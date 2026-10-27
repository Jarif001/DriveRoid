package com.prettyj.driveroid.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.prettyj.driveroid.assistant.speech.SpeechErrorKind
import com.prettyj.driveroid.assistant.speech.SpeechEventListener
import com.prettyj.driveroid.assistant.speech.SpeechRecognizerManager
import com.prettyj.driveroid.assistant.speech.SpeechRetryPolicy
import com.prettyj.driveroid.assistant.wakeword.PhraseWakeWordDetector
import com.prettyj.driveroid.assistant.wakeword.WakeWordDetector
import com.prettyj.driveroid.util.DriveroidLog

/**
 * Owns the voice pipeline and its state machine:
 *
 * ```
 * LISTENING_FOR_WAKE_WORD --wake word--> LISTENING_FOR_COMMAND --utterance--> PROCESSING
 *          ^                                  |                                  |
 *          +----------------------------------+----------------------------------+
 * ```
 *
 * The recogniser runs as a continuous chain of sessions. Every transcript is offered to the
 * wake-word detector, and only utterances containing "Hey DriveRoid" advance the machine; the text
 * after the wake word is the command. [AssistantState.PROCESSING] acknowledges the command and the
 * assistant then returns to waiting for the wake word.
 *
 * Failure handling: silence ([SpeechErrorKind.NO_MATCH] / [SpeechErrorKind.TIMEOUT]) restarts
 * immediately without growing the back-off, recoverable failures are retried with
 * [SpeechRetryPolicy], a stuck session is caught by a watchdog, and a lost microphone permission
 * stops the assistant for good through [onFatalError].
 *
 * The controller is the only component that changes the pipeline state, and it publishes every
 * transition through [AssistantStateRepository]. The service owns its lifetime (one controller per
 * service instance), the foreground notification and the permission checks; the UI never talks to
 * the controller directly.
 *
 * All methods must be called from the main thread, which is also where recogniser callbacks arrive.
 */
class VoiceAssistantController(
    context: Context,
    private val wakeWordDetector: WakeWordDetector = PhraseWakeWordDetector(),
    /** Invoked once when the pipeline fails for good, so the service can tear itself down. */
    private val onFatalError: (AssistantError) -> Unit = {},
) {

    /** What the current recognition session is waiting for. */
    private enum class Session { WAKE_WORD, COMMAND }

    private val speechRecognizerManager = SpeechRecognizerManager(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var session = Session.WAKE_WORD

    /** Consecutive recoverable failures; reset whenever an utterance is recognised successfully. */
    private var failures = 0

    /**
     * Consecutive failures that mean the engine itself cannot be reached (client errors, stalled
     * sessions). Reset by any normal callback, because those prove the engine answered.
     */
    private var engineFailures = 0

    private var pendingRestart: Runnable? = null
    private var pendingWatchdog: Runnable? = null

    /** `true` while the pipeline is prepared and the assistant is supposed to be running. */
    var isRunning: Boolean = false
        private set

    /** Receives every recogniser event of the current session; stale events are already filtered. */
    private val speechListener = object : SpeechEventListener {
        override fun onPartialResult(text: String) {
            if (!isRunning) return
            AssistantStateRepository.publishTranscript(text)
        }

        override fun onResult(text: String) {
            if (!isRunning) return
            cancelWatchdog()
            failures = 0
            engineFailures = 0
            AssistantStateRepository.publishTranscript(text)
            when (session) {
                Session.WAKE_WORD -> onWakeWordUtterance(text)
                Session.COMMAND -> onCommandUtterance(text)
            }
        }

        override fun onError(error: SpeechErrorKind) {
            if (!isRunning) return
            cancelWatchdog()
            onRecognitionError(error)
        }
    }

    init {
        speechRecognizerManager.listener = speechListener
    }

    /**
     * Prepares the pipeline and moves the assistant to [AssistantState.LISTENING_FOR_WAKE_WORD].
     *
     * @return `true` when the assistant is armed. `false` means the controller already reported a
     *  failure; the caller must tear the service down.
     */
    @MainThread
    fun start(): Boolean {
        if (isRunning) {
            // Guards against a second start request from a reopened Activity, a double tap or a
            // duplicate service command: only one pipeline ever exists.
            DriveroidLog.d(TAG, "start() ignored: assistant already running")
            return true
        }
        AssistantStateRepository.update(AssistantState.STARTING)
        if (!preparePipeline()) return false

        isRunning = true
        session = Session.WAKE_WORD
        failures = 0
        AssistantStateRepository.update(AssistantState.LISTENING_FOR_WAKE_WORD)
        beginListening("assistant armed")
        DriveroidLog.i(TAG, "Assistant armed")
        return true
    }

    /** Releases the pipeline resources and reports [AssistantState.STOPPED]. Idempotent. */
    @MainThread
    fun stop() {
        if (!isRunning) {
            DriveroidLog.d(TAG, "stop() ignored: assistant is not running")
            return
        }
        isRunning = false
        session = Session.WAKE_WORD
        cancelPendingRestart()
        cancelWatchdog()
        releasePipeline()
        if (AssistantStateRepository.current.state != AssistantState.ERROR) {
            AssistantStateRepository.update(AssistantState.STOPPED)
        }
        AssistantStateRepository.publishTranscript(null)
        DriveroidLog.i(TAG, "Assistant stopped, pipeline resources released")
    }

    /**
     * Reports a failure that prevents the assistant from running: releases the pipeline, leaves the
     * error visible for the UI and notifies [onFatalError] so the service can stop itself.
     */
    @MainThread
    fun reportFailure(error: AssistantError, throwable: Throwable? = null) {
        DriveroidLog.e(TAG, "Assistant failure: ${error.name}", throwable)
        isRunning = false
        cancelPendingRestart()
        cancelWatchdog()
        releasePipeline()
        AssistantStateRepository.update(AssistantState.ERROR, error)
        onFatalError(error)
    }

    private fun preparePipeline(): Boolean {
        return try {
            if (!speechRecognizerManager.acquire()) {
                DriveroidLog.e(TAG, "No speech recognition service on this device")
                reportFailure(AssistantError.RECOGNITION_UNAVAILABLE)
                false
            } else {
                wakeWordDetector.arm()
                true
            }
        } catch (e: Exception) {
            DriveroidLog.e(TAG, "Could not prepare the voice pipeline", e)
            reportFailure(AssistantError.PIPELINE_START_FAILED, e)
            false
        }
    }

    private fun releasePipeline() {
        wakeWordDetector.disarm()
        speechRecognizerManager.release()
    }

    // --- state machine -------------------------------------------------------

    private fun onWakeWordUtterance(text: String) {
        val remainder = wakeWordDetector.matchWakeWord(text)
        if (remainder == null) {
            DriveroidLog.d(TAG, "Ignoring utterance without wake word: \"$text\"")
            scheduleRestart(FAST_RESTART_MILLIS, "no wake word")
            return
        }
        DriveroidLog.i(TAG, "Wake word detected in \"$text\"")
        if (remainder.isEmpty()) {
            session = Session.COMMAND
            AssistantStateRepository.update(AssistantState.LISTENING_FOR_COMMAND)
            scheduleRestart(FAST_RESTART_MILLIS, "waiting for the command")
            return
        }
        // Wake word and command spoken in one breath: take it as the command.
        onCommandUtterance(text)
    }

    private fun onCommandUtterance(text: String) {
        session = Session.COMMAND
        AssistantStateRepository.update(AssistantState.PROCESSING)
        DriveroidLog.i(TAG, "Command captured: \"$text\"")
        // Phase 3 hands this transcript to CommandParser -> ContactManager -> CallManager; until
        // then the assistant acknowledges the captured command and goes back to the wake word.
        scheduleRestart(PROCESSING_HOLD_MILLIS, "command acknowledged") {
            session = Session.WAKE_WORD
            AssistantStateRepository.update(AssistantState.LISTENING_FOR_WAKE_WORD)
        }
    }

    private fun onRecognitionError(error: SpeechErrorKind) {
        when (error) {
            SpeechErrorKind.PERMISSION -> {
                DriveroidLog.e(TAG, "Microphone permission lost while listening")
                reportFailure(AssistantError.PERMISSION_DENIED)
            }

            SpeechErrorKind.CLIENT -> {
                // "Not connected to the recognition service": the engine cannot be reached. Retry
                // briefly, then stop with a clear error instead of spinning forever.
                engineFailures++
                speechRecognizerManager.cancelListening()
                if (engineFailures >= ENGINE_UNRESPONSIVE_LIMIT) {
                    DriveroidLog.e(
                        TAG,
                        "Recognition engine still unreachable after $engineFailures attempts",
                    )
                    reportFailure(AssistantError.RECOGNITION_UNAVAILABLE)
                    return
                }
                failures++
                val delay = SpeechRetryPolicy.delayMillisFor(failures)
                DriveroidLog.w(
                    TAG,
                    "Recognition engine not responding (client error " +
                        "$engineFailures/$ENGINE_UNRESPONSIVE_LIMIT); retrying in ${delay}ms",
                )
                scheduleRestart(delay, "retry after CLIENT")
            }

            SpeechErrorKind.NO_MATCH, SpeechErrorKind.TIMEOUT -> {
                // Silence is normal, and this callback also proves the engine is alive again.
                engineFailures = 0
                if (session == Session.COMMAND) {
                    DriveroidLog.i(TAG, "No command heard; waiting for the wake word again")
                    session = Session.WAKE_WORD
                    AssistantStateRepository.update(AssistantState.LISTENING_FOR_WAKE_WORD)
                }
                scheduleRestart(FAST_RESTART_MILLIS, "session ended with $error")
            }

            else -> {
                // The engine answered with an error, so the "unreachable" streak is over.
                engineFailures = 0
                failures++
                val delay = SpeechRetryPolicy.delayMillisFor(failures)
                if (error == SpeechErrorKind.BUSY) {
                    // A recogniser that reports busy is usually stuck; cancel it before retrying.
                    speechRecognizerManager.cancelListening()
                }
                DriveroidLog.w(
                    TAG,
                    "Recognition error $error; retrying in ${delay}ms (failure #$failures)",
                )
                scheduleRestart(delay, "retry after $error")
            }
        }
    }

    /** Starts one recognition session and puts it under supervision. */
    private fun beginListening(reason: String) {
        if (!isRunning) return
        cancelWatchdog()
        if (!speechRecognizerManager.startListening()) {
            failures++
            val delay = SpeechRetryPolicy.delayMillisFor(failures)
            DriveroidLog.w(TAG, "Could not start a session ($reason); retrying in ${delay}ms")
            scheduleRestart(delay, "start failed")
            return
        }
        scheduleWatchdog()
        DriveroidLog.d(TAG, "Listening ($reason), session=$session")
    }

    private fun scheduleRestart(
        delayMillis: Long,
        reason: String,
        beforeListening: (() -> Unit)? = null,
    ) {
        if (!isRunning) return
        cancelPendingRestart()
        val task = Runnable {
            pendingRestart = null
            if (!isRunning) return@Runnable
            beforeListening?.invoke()
            beginListening(reason)
        }
        pendingRestart = task
        mainHandler.postDelayed(task, delayMillis)
    }

    /** Restarts a session that never produced any callback, so the assistant cannot go silently deaf. */
    private fun scheduleWatchdog() {
        cancelWatchdog()
        val task = Runnable {
            pendingWatchdog = null
            if (!isRunning) return@Runnable
            DriveroidLog.w(
                TAG,
                "No recogniser callback in ${WATCHDOG_MILLIS}ms; restarting the session",
            )
            speechRecognizerManager.cancelListening()
            engineFailures++
            if (engineFailures >= ENGINE_UNRESPONSIVE_LIMIT) {
                DriveroidLog.e(
                    TAG,
                    "Recognition engine produced nothing after $engineFailures stalled sessions",
                )
                reportFailure(AssistantError.RECOGNITION_UNAVAILABLE)
                return@Runnable
            }
            failures++
            scheduleRestart(SpeechRetryPolicy.delayMillisFor(failures), "watchdog timeout")
        }
        pendingWatchdog = task
        mainHandler.postDelayed(task, WATCHDOG_MILLIS)
    }

    private fun cancelPendingRestart() {
        pendingRestart?.let(mainHandler::removeCallbacks)
        pendingRestart = null
    }

    private fun cancelWatchdog() {
        pendingWatchdog?.let(mainHandler::removeCallbacks)
        pendingWatchdog = null
    }

    private companion object {
        const val TAG = "VoiceAssistantController"

        /** Gap between two sessions that follow each other normally (result -> next session). */
        const val FAST_RESTART_MILLIS = 200L

        /** How long [AssistantState.PROCESSING] is shown so the user sees their command was taken. */
        const val PROCESSING_HOLD_MILLIS = 1_200L

        /** No recogniser callback at all within this window means the session is stuck. */
        const val WATCHDOG_MILLIS = 30_000L

        /** Consecutive client errors / stalled sessions that prove the engine cannot be reached. */
        const val ENGINE_UNRESPONSIVE_LIMIT = 5
    }
}
