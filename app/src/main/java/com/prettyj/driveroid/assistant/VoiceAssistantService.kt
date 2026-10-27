package com.prettyj.driveroid.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.prettyj.driveroid.assistant.notifications.AssistantNotifier
import com.prettyj.driveroid.permissions.AssistantPermissions
import com.prettyj.driveroid.util.DriveroidLog

/**
 * The foreground service that keeps the voice assistant alive while the UI is not visible.
 *
 * Responsibilities:
 *  - own the single [VoiceAssistantController] and therefore the whole voice pipeline,
 *  - enter and leave the foreground with the `microphone` service type,
 *  - show the persistent "listening" notification,
 *  - decide when the assistant *ends* (stop command, user removed the task).
 *
 * What it deliberately does not do: no UI, no permission dialogs, no recognition logic, no
 * `Activity` references. The UI reaches the assistant state through
 * [AssistantStateRepository] instead of binding, which keeps the Activity free of
 * `ServiceConnection` lifecycles.
 */
class VoiceAssistantService : Service() {

    private lateinit var controller: VoiceAssistantController
    private lateinit var notifier: AssistantNotifier
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        notifier = AssistantNotifier(this).apply { ensureChannel() }
        // One controller per service instance; creating it is cheap, it acquires nothing until the
        // assistant is actually started. The callback lets a fatal mid-session failure (for example
        // a revoked microphone permission) tear this service down completely.
        controller = VoiceAssistantController(this, onFatalError = ::handleFatalError)
        DriveroidLog.i(TAG, "Service created on ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
    }

    /**
     * A fatal pipeline error means the assistant can no longer work, so the foreground service (and
     * therefore its microphone notification) must disappear with it. Called synchronously by the
     * controller; every step here is idempotent, so a duplicate call is harmless.
     */
    private fun handleFatalError(error: AssistantError) {
        DriveroidLog.e(TAG, "Fatal assistant error ${error.name}; stopping the service")
        releaseServiceResources()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> {
                DriveroidLog.i(TAG, "Stop command received")
                stopAssistant()
            }
            null -> {
                // START_NOT_STICKY means the system never re-delivers an intent after killing the
                // service, so this can only be an explicit null-intent start. Never resume
                // listening silently in that case.
                DriveroidLog.w(TAG, "Null intent: refusing to start the assistant")
                stopSelf()
            }
            else -> DriveroidLog.w(TAG, "Ignoring unknown action '${intent.action}'")
        }
        // If the platform kills the process, the assistant ends instead of silently coming back.
        return START_NOT_STICKY
    }

    /**
     * No binding: the UI observes [AssistantStateRepository], which works for a running as well as
     * for a freshly created Activity and cannot leak a connection.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * V1 behaviour: when the user dismisses the app from Recents, the assistant ends instead of
     * listening forever. Pressing Home, switching apps or locking the phone does not remove the
     * task, so the assistant keeps running in those cases.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        DriveroidLog.i(TAG, "Task removed by the user: ending the assistant")
        stopAssistant()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        DriveroidLog.i(TAG, "Service destroying")
        // Defensive cleanup: every normal path already released the pipeline, but a system-initiated
        // stop must not leave a recognition resource behind either (stop() is idempotent).
        controller.stop()
        leaveForeground()
        super.onDestroy()
        DriveroidLog.i(TAG, "Service destroyed")
    }

    private fun handleStart() {
        if (controller.isRunning) {
            // A reopened Activity or a double tap must not create a second assistant: the service
            // is a singleton, so the repeated start is absorbed here.
            DriveroidLog.i(TAG, "Start ignored: assistant already running (single instance)")
            refreshNotification(AssistantStateRepository.current.state)
            return
        }
        if (!AssistantPermissions.hasMicrophoneAccess(this)) {
            // Pre-flight validation in the Activity normally prevents this; this is the safety net.
            DriveroidLog.e(TAG, "Cannot start: RECORD_AUDIO permission is not granted")
            controller.reportFailure(AssistantError.PERMISSION_DENIED)
            releaseServiceResources()
            return
        }
        if (!enterForeground()) return
        if (!controller.start()) {
            DriveroidLog.e(TAG, "Assistant pipeline could not be started")
            releaseServiceResources()
            return
        }
        refreshNotification(AssistantStateRepository.current.state)
    }

    /**
     * Enters the foreground with the `microphone` service type. This must happen within a few
     * seconds of the start request, which is why it is the first thing done after the permission
     * check.
     *
     * @return `true` when the assistant is in the foreground; `false` when the service already
     *  reported the failure and cleaned itself up.
     */
    private fun enterForeground(): Boolean {
        return try {
            ServiceCompat.startForeground(
                this,
                AssistantNotifier.NOTIFICATION_ID,
                notifier.build(AssistantState.STARTING),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            isForeground = true
            DriveroidLog.i(TAG, "Foreground service active (type=microphone)")
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (background start restriction),
            // SecurityException (microphone permission revoked, missing FGS type permission) ...
            DriveroidLog.e(TAG, "Could not enter the foreground", e)
            controller.reportFailure(AssistantError.SERVICE_START_FAILED, e)
            releaseServiceResources()
            false
        }
    }

    /** Full stop: release the pipeline, drop the notification and destroy the service. */
    private fun stopAssistant() {
        controller.stop()
        releaseServiceResources()
    }

    private fun releaseServiceResources() {
        leaveForeground()
        // stopSelf() (not stopSelf(startId)) is intentional: the assistant must never outlive an
        // explicit stop request, not even when several commands were queued back to back.
        stopSelf()
    }

    private fun leaveForeground() {
        if (isForeground) {
            isForeground = false
            // STOP_FOREGROUND_REMOVE also removes the notification, so no microphone indicator
            // stays behind after the assistant stops.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            DriveroidLog.i(TAG, "Left the foreground; notification removed")
            return
        }
        notifier.cancel()
    }

    private fun refreshNotification(state: AssistantState) {
        if (!isForeground) {
            DriveroidLog.d(TAG, "No foreground notification to refresh")
            return
        }
        notifier.update(state)
    }

    companion object {
        private const val TAG = "VoiceAssistantService"

        /** Asked the service to prepare the pipeline and go to the foreground. */
        const val ACTION_START = "com.prettyj.driveroid.action.START_ASSISTANT"

        /** Asked the service to stop the assistant and clean up. */
        const val ACTION_STOP = "com.prettyj.driveroid.action.STOP_ASSISTANT"

        /** Intent that stops the assistant; also used by the notification action. */
        fun stopIntent(context: Context): Intent =
            Intent(context, VoiceAssistantService::class.java).setAction(ACTION_STOP)

        /**
         * Starts the assistant. Idempotent: a second call while the assistant runs only refreshes
         * the notification, because the service survives and the controller ignores the extra start.
         */
        fun start(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
                DriveroidLog.i(TAG, "Assistant start requested")
            } catch (e: RuntimeException) {
                // For example ForegroundServiceStartNotAllowedException when the app is not in a
                // state that allows starting a foreground service.
                DriveroidLog.e(TAG, "Assistant service could not be started", e)
                AssistantStateRepository.update(
                    AssistantState.ERROR,
                    AssistantError.SERVICE_START_FAILED,
                )
            }
        }

        /**
         * Stops the assistant. Idempotent: when it is not running, nothing happens.
         *
         * The running service is asked through [ACTION_STOP] so that it can release its resources in
         * order (pipeline first, then notification, then itself). `startService` is safe here because
         * the background restriction only applies while the service is *not* running yet; if the
         * command cannot be delivered, the service is stopped directly instead.
         */
        fun stop(context: Context) {
            if (AssistantStateRepository.current.isSessionActive) {
                try {
                    context.startService(stopIntent(context))
                    return
                } catch (e: RuntimeException) {
                    DriveroidLog.w(TAG, "Stop command could not be delivered; stopping directly", e)
                }
            }
            context.stopService(Intent(context, VoiceAssistantService::class.java))
        }
    }
}
