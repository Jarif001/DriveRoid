package com.prettyj.driveroid.assistant.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.prettyj.driveroid.util.DriveroidLog

/**
 * Owns the platform recognition resource ([SpeechRecognizer]) of the assistant and turns it into a
 * small event stream for the voice pipeline.
 *
 * Resource rule: [acquire] creates exactly one instance and [release] destroys it again. The service
 * releases it on every stop path, which is what guarantees that "STOP LISTENING" does not leave a
 * recognition resource (and therefore the microphone) behind.
 *
 * Engine rule: [acquire] picks the recogniser per device - Android 12+ phones (for example a
 * Galaxy S25 Ultra) use the on-device recogniser so commands are understood without a network,
 * while older phones (for example an Android 11 device) use the system default recogniser.
 *
 * Session rule: [startListening] begins one utterance session that ends with exactly one
 * [SpeechEventListener.onResult] or [SpeechEventListener.onError]. Callbacks that arrive after an
 * intentional [cancelListening] or [release] are dropped, so the pipeline can restart immediately
 * without ever seeing a stale platform event.
 *
 * All methods and callbacks run on the main thread, because [SpeechRecognizer] requires a `Looper`
 * with a message queue.
 */
class SpeechRecognizerManager(context: Context) {

    private val appContext = context.applicationContext

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    /** Event sink; set once by [com.prettyj.driveroid.assistant.VoiceAssistantController]. */
    var listener: SpeechEventListener? = null

    /** `true` while the recognition resource is held. */
    val isAcquired: Boolean
        get() = recognizer != null

    /** `true` while an utterance session is running. */
    val isListening: Boolean
        get() = listening

    /**
     * Prefer the platform's on-device recogniser when the phone offers one (Android 12+, for
     * example a Galaxy S25 Ultra): commands are then understood **without an internet connection**,
     * which is what a driving assistant needs. Phones without an on-device model (for example an
     * Android 11 device) keep using the system default recogniser, so nothing changes there.
     *
     * Set to `false` to always use the device default recogniser (usually cloud-backed).
     */
    private val preferOnDeviceEngine = true

    /**
     * Creates the recognition resource and picks the engine for this device.
     *
     * @return `false` when this device cannot recognise speech at all. The caller must treat that
     *  as a fatal pipeline failure, because without an engine no command can ever be understood.
     */
    fun acquire(): Boolean {
        if (recognizer != null) {
            DriveroidLog.d(TAG, "acquire() ignored: recognition resource already held")
            return true
        }
        val onDeviceAvailable = onDeviceRecognitionAvailable()
        val defaultAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        // Prefer the on-device engine when asked for it. If the device default is missing (or hidden
        // by package visibility) but an on-device model exists, use the on-device engine anyway
        // instead of failing outright.
        val useOnDevice = (preferOnDeviceEngine && onDeviceAvailable) ||
            (!defaultAvailable && onDeviceAvailable)
        if (!useOnDevice && !defaultAvailable) {
            // Diagnostic aid: distinguish "device has no recogniser" from "package visibility hid
            // the recogniser because the <queries> block is missing".
            DriveroidLog.w(
                TAG,
                "No service answers android.speech.RecognitionService: the device has no " +
                    "recogniser, or the <queries> block in the manifest is missing",
            )
            return false
        }
        return try {
            recognizer = createRecognizer(useOnDevice).also {
                it.setRecognitionListener(platformListener)
            }
            DriveroidLog.i(
                TAG,
                "Recognition resource acquired (engine=${if (useOnDevice) "ON_DEVICE" else "DEFAULT"})",
            )
            true
        } catch (e: Exception) {
            DriveroidLog.e(TAG, "Could not create the recognition resource", e)
            recognizer = null
            false
        }
    }

    private fun createRecognizer(onDevice: Boolean): SpeechRecognizer = if (onDevice) {
        try {
            // The SDK check is belt and braces: `onDevice` is only ever true on API 31+, but stating
            // it here keeps lint (and any future refactor) safe.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (e: UnsupportedOperationException) {
            // Availability can change between the check and the call: keep the assistant alive by
            // falling back to the device default instead of losing the whole session.
            DriveroidLog.w(TAG, "On-device recogniser refused; falling back to the device default", e)
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
    } else {
        SpeechRecognizer.createSpeechRecognizer(appContext)
    }

    /**
     * `true` when Android exposes an on-device recogniser. Guarded for API 31+ because
     * `isOnDeviceRecognitionAvailable()` was added in Android 12 (and lint checks that guard).
     */
    private fun onDeviceRecognitionAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    /**
     * Starts one utterance session.
     *
     * @return `true` when a session is running (or was already running before this call).
     */
    fun startListening(): Boolean {
        val current = recognizer ?: return false
        if (listening) {
            DriveroidLog.d(TAG, "startListening() ignored: a session is already running")
            return true
        }
        return try {
            current.startListening(buildRecognitionIntent())
            listening = true
            DriveroidLog.d(TAG, "Recognition session started")
            true
        } catch (e: Exception) {
            listening = false
            DriveroidLog.e(TAG, "startListening() failed", e)
            false
        }
    }

    /**
     * Stops the running session without destroying the resource. Safe to call at any time; no
     * callback of the cancelled session is forwarded afterwards.
     */
    fun cancelListening() {
        val current = recognizer ?: return
        listening = false
        try {
            current.cancel()
        } catch (e: Exception) {
            DriveroidLog.w(TAG, "cancel() failed", e)
        }
        DriveroidLog.d(TAG, "Recognition session cancelled")
    }

    /** Destroys the recognition resource. Safe to call repeatedly. */
    fun release() {
        val current = recognizer ?: return
        recognizer = null
        listening = false
        try {
            current.cancel()
        } catch (e: Exception) {
            DriveroidLog.w(TAG, "cancel() during release failed", e)
        }
        try {
            current.destroy()
            DriveroidLog.i(TAG, "Recognition resource released")
        } catch (e: Exception) {
            DriveroidLog.w(TAG, "Recognition resource could not be destroyed cleanly", e)
        }
    }

    private fun buildRecognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // Interim hypotheses let the UI show what is being heard while the user is talking.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private val platformListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            DriveroidLog.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            DriveroidLog.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            if (!listening) {
                DriveroidLog.d(TAG, "Dropping stale error $error (session already ended)")
                return
            }
            listening = false
            val kind = errorKind(error)
            DriveroidLog.w(TAG, "Recognition session failed: code=$error kind=$kind")
            listener?.onError(kind)
        }

        override fun onResults(results: Bundle?) {
            if (!listening) {
                DriveroidLog.d(TAG, "Dropping stale results (session already ended)")
                return
            }
            listening = false
            val text = topResult(results)
            if (text == null) {
                DriveroidLog.d(TAG, "Recognition returned no text")
                listener?.onError(SpeechErrorKind.NO_MATCH)
            } else {
                DriveroidLog.i(TAG, "Recognized: \"$text\"")
                listener?.onResult(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!listening) return
            topResult(partialResults)?.let { listener?.onPartialResult(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /** First transcript of a result bundle, trimmed; `null` when the bundle carries no text. */
    private fun topResult(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Maps platform error codes to [SpeechErrorKind], using only constants available since API 8. */
    private fun errorKind(error: Int): SpeechErrorKind = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechErrorKind.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorKind.TIMEOUT
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechErrorKind.BUSY
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> SpeechErrorKind.NETWORK

        SpeechRecognizer.ERROR_AUDIO -> SpeechErrorKind.AUDIO
        SpeechRecognizer.ERROR_CLIENT -> SpeechErrorKind.CLIENT
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorKind.PERMISSION
        else -> SpeechErrorKind.UNKNOWN
    }

    private companion object {
        const val TAG = "SpeechRecognizerManager"
    }
}
