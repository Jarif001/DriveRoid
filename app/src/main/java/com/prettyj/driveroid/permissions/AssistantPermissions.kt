package com.prettyj.driveroid.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Single place that answers "may DriveRoid use the microphone and show its notification?".
 *
 * Permissions used by the assistant:
 *  - [Manifest.permission.RECORD_AUDIO]: mandatory, the microphone is the whole point.
 *  - [Manifest.permission.POST_NOTIFICATIONS]: required from Android 13 (API 33) onwards so that
 *    the persistent "listening" notification can be displayed. When it is denied the assistant
 *    still runs, the notification is simply hidden by the system.
 *  - `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` are install-time permissions, so they
 *    are declared in the manifest and never requested at runtime.
 *
 * No other permission is needed in this phase (no contacts, no phone state, no audio routing).
 */
object AssistantPermissions {

    /** Runtime permissions that are still missing, in the order they should be requested. */
    fun missingRuntimePermissions(context: Context): List<String> = buildList {
        if (!hasMicrophoneAccess(context)) add(Manifest.permission.RECORD_AUDIO)
        if (needsNotificationPermission() && !hasNotificationAccess(context)) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun hasMicrophoneAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** A device without microphone hardware can never run the assistant. */
    fun hasMicrophoneHardware(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    /** `true` when this Android version requires the runtime notification permission. */
    fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasNotificationAccess(context: Context): Boolean =
        !needsNotificationPermission() || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    /** Notification settings of DriveRoid, used when the notification permission is missing. */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** DriveRoid's own settings page, where a denied microphone permission can be re-enabled. */
    fun applicationSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
}
