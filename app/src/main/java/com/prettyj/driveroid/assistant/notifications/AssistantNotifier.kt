package com.prettyj.driveroid.assistant.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.prettyj.driveroid.MainActivity
import com.prettyj.driveroid.R
import com.prettyj.driveroid.assistant.AssistantState
import com.prettyj.driveroid.assistant.VoiceAssistantService
import com.prettyj.driveroid.util.DriveroidLog

/**
 * Builds and updates the persistent notification that tells the user the assistant is listening.
 *
 * The notification *is* the foreground service contract: the service may not stay in the foreground
 * without it, and the user must always be able to see and stop the microphone usage from the
 * notification shade. It is therefore ongoing, silent and public (also visible on the lock screen).
 */
class AssistantNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    /** Creates the channel once per service start; the platform ignores a duplicate creation. */
    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            // LOW keeps the notification (and the microphone indicator) visible without ever
            // playing a sound or vibrating.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
        DriveroidLog.d(TAG, "Notification channel ready: $CHANNEL_ID")
    }

    /** The notification the service passes to `startForeground`. */
    fun build(state: AssistantState): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            VoiceAssistantService.stopIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(context.getString(R.string.notification_title_listening))
            .setContentText(context.getString(textRes(state)))
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_stop,
                context.getString(R.string.notification_action_stop),
                stopIntent,
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Refreshes the notification text while the service stays in the foreground (for example
     * `STARTING` -> `LISTENING_FOR_WAKE_WORD`).
     */
    fun update(state: AssistantState) {
        // The runtime permission is checked right here (instead of through AssistantPermissions) so
        // that the guard is also visible to lint: notify() needs POST_NOTIFICATIONS on Android 13+.
        val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!canPostNotifications) {
            // The assistant keeps running; the system just keeps the notification hidden.
            DriveroidLog.w(TAG, "POST_NOTIFICATIONS not granted; skipping notification update")
            return
        }
        notificationManager.notify(NOTIFICATION_ID, build(state))
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun textRes(state: AssistantState): Int = when (state) {
        AssistantState.STARTING -> R.string.notification_text_starting
        AssistantState.ERROR -> R.string.notification_text_error
        else -> R.string.notification_text_active
    }

    companion object {
        private const val TAG = "AssistantNotifier"

        /** Exactly one notification belongs to the assistant, which keeps updates idempotent. */
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "driveroid_voice_assistant"

        private const val REQUEST_CODE_OPEN = 100
        private const val REQUEST_CODE_STOP = 101
    }
}
