package com.prettyj.driveroid

import android.Manifest
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.prettyj.driveroid.assistant.AssistantError
import com.prettyj.driveroid.assistant.AssistantSnapshot
import com.prettyj.driveroid.assistant.AssistantState
import com.prettyj.driveroid.assistant.AssistantStateRepository
import com.prettyj.driveroid.assistant.VoiceAssistantService
import com.prettyj.driveroid.databinding.ActivityMainBinding
import com.prettyj.driveroid.permissions.AssistantPermissions
import com.prettyj.driveroid.util.DriveroidLog
import kotlinx.coroutines.launch

/**
 * The only screen of DriveRoid: app name, assistant status and a single primary action
 * (START LISTENING / STOP LISTENING).
 *
 * The Activity never performs voice processing and never owns the assistant. It
 *  1. validates the runtime permissions,
 *  2. asks [VoiceAssistantService] to start or stop,
 *  3. renders whatever [AssistantStateRepository] publishes.
 *
 * Because of step 3 the same code path covers a freshly created Activity, one that was recreated
 * after a configuration change and one that was reopened while the assistant keeps running: the UI
 * follows the service, never the other way round. Nothing starts the assistant in `onCreate`, so
 * opening the app can never begin listening on its own.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Permission results of the START LISTENING flow. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> onPermissionsResult(grants) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        binding.buttonStartStop.setOnClickListener { onPrimaryActionClicked() }
        observeAssistantState()
        DriveroidLog.i(
            TAG,
            "Home screen created; assistant is ${AssistantStateRepository.current.state}",
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handleActivityDestruction()
    }

    /**
     * Keeps the content inside the system bars. The root view of the layout carries no padding of
     * its own, so the window insets and the layout paddings cannot conflict.
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Renders the assistant state now and for as long as the Activity is started.
     *
     * The synchronous first render is what makes Activity recreation safe: a recreated Activity
     * immediately shows the live state (STOP LISTENING while the assistant runs) without requesting
     * a start, and it shows `STOPPED` when the process was recreated after the assistant ended.
     */
    private fun observeAssistantState() {
        render(AssistantStateRepository.current)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AssistantStateRepository.state.collect { snapshot -> render(snapshot) }
            }
        }
    }

    private fun onPrimaryActionClicked() {
        when {
            // START cannot be pressed twice: the service is starting up and rejects extra commands.
            AssistantStateRepository.current.state == AssistantState.STARTING -> {
                DriveroidLog.d(TAG, "START ignored: assistant is already starting")
            }

            AssistantStateRepository.current.isSessionActive -> {
                DriveroidLog.i(TAG, "STOP LISTENING pressed")
                VoiceAssistantService.stop(this)
            }

            else -> {
                DriveroidLog.i(TAG, "START LISTENING pressed")
                requestPermissionsAndStart()
            }
        }
    }

    /**
     * The user chose to start the assistant, so this is the moment to ask for what is missing:
     * the microphone (mandatory) and, from Android 13, the notification permission (nice to have).
     */
    private fun requestPermissionsAndStart() {
        if (!AssistantPermissions.hasMicrophoneHardware(this)) {
            DriveroidLog.e(TAG, "Device has no microphone")
            AssistantStateRepository.update(
                AssistantState.ERROR,
                AssistantError.MICROPHONE_UNAVAILABLE,
            )
            return
        }

        val missing = AssistantPermissions.missingRuntimePermissions(this)
        if (missing.isNotEmpty()) {
            DriveroidLog.i(TAG, "Requesting permissions: $missing")
            permissionLauncher.launch(missing.toTypedArray())
            return
        }

        VoiceAssistantService.start(this)
    }

    private fun onPermissionsResult(grants: Map<String, Boolean>) {
        // Re-check through the permission helper instead of trusting the map: the map only contains
        // the permissions that were requested, and a permission can already have been granted.
        if (!AssistantPermissions.hasMicrophoneAccess(this)) {
            DriveroidLog.e(TAG, "Microphone permission denied; assistant not started")
            AssistantStateRepository.update(AssistantState.ERROR, AssistantError.PERMISSION_DENIED)
            showMicrophoneDeniedHint()
            return
        }

        if (grants[Manifest.permission.POST_NOTIFICATIONS] == false) {
            // Not fatal: the assistant runs, the system just hides the notification.
            DriveroidLog.w(TAG, "Notification permission denied; notification may stay hidden")
            showNotificationsDisabledHint()
        }

        VoiceAssistantService.start(this)
    }

    private fun showMicrophoneDeniedHint() {
        Snackbar.make(
            binding.buttonStartStop,
            R.string.snackbar_microphone_required,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.action_open_settings) {
            startActivity(AssistantPermissions.applicationSettingsIntent(this))
        }.show()
    }

    private fun showNotificationsDisabledHint() {
        Snackbar.make(
            binding.buttonStartStop,
            R.string.snackbar_notifications_disabled,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.action_open_settings) {
            startActivity(AssistantPermissions.notificationSettingsIntent(this))
        }.show()
    }

    /**
     * Single place that turns an [AssistantSnapshot] into what the driver sees: the primary action,
     * the status dot and the status text. Called once for the initial render and again for every
     * state change.
     */
    private fun render(snapshot: AssistantSnapshot) {
        val sessionActive = snapshot.isSessionActive

        // Primary action: START LISTENING (brand colour) while idle, STOP LISTENING (red) while the
        // assistant is running.
        binding.buttonStartStop.setText(
            if (sessionActive) R.string.action_stop_listening else R.string.action_start_listening
        )
        binding.buttonStartStop.setIconResource(
            if (sessionActive) R.drawable.ic_stop else R.drawable.ic_mic
        )
        val background = color(if (sessionActive) R.color.driveroid_stop else R.color.driveroid_primary)
        val foreground = color(
            if (sessionActive) R.color.driveroid_on_stop else R.color.driveroid_on_primary
        )
        binding.buttonStartStop.backgroundTintList = ColorStateList.valueOf(background)
        binding.buttonStartStop.setTextColor(foreground)
        binding.buttonStartStop.iconTint = ColorStateList.valueOf(foreground)

        // Status: dot colour, label and one line of explanation.
        binding.textStatus.setText(statusLabelRes(snapshot.state))
        binding.textStatusDetail.setText(detailRes(snapshot))

        // Latest thing the assistant heard, if anything; keeps the user's command visible while the
        // pipeline works on it.
        val transcript = snapshot.transcript
        if (transcript.isNullOrBlank()) {
            binding.textTranscript.isVisible = false
        } else {
            binding.textTranscript.text = getString(R.string.detail_heard, transcript)
            binding.textTranscript.isVisible = true
        }

        ViewCompat.setBackgroundTintList(
            binding.viewStatusDot,
            ColorStateList.valueOf(color(statusColorRes(snapshot.state))),
        )
    }

    @StringRes
    private fun statusLabelRes(state: AssistantState): Int = when (state) {
        AssistantState.STOPPED -> R.string.status_not_listening
        AssistantState.STARTING -> R.string.status_starting
        AssistantState.LISTENING_FOR_WAKE_WORD,
        AssistantState.LISTENING_FOR_COMMAND -> R.string.status_listening

        AssistantState.PROCESSING -> R.string.status_processing
        AssistantState.WAITING_FOR_RESPONSE -> R.string.status_waiting_for_response
        AssistantState.CALLING -> R.string.status_calling
        AssistantState.ERROR -> R.string.status_error
    }

    @StringRes
    private fun detailRes(snapshot: AssistantSnapshot): Int = when (snapshot.state) {
        AssistantState.ERROR -> snapshot.error?.let(::errorDetailRes) ?: R.string.detail_error_unknown
        AssistantState.STOPPED -> R.string.detail_stopped
        AssistantState.STARTING -> R.string.detail_starting
        AssistantState.LISTENING_FOR_WAKE_WORD -> R.string.detail_listening_wake_word
        AssistantState.LISTENING_FOR_COMMAND -> R.string.detail_listening_command

        AssistantState.PROCESSING -> R.string.detail_processing
        AssistantState.WAITING_FOR_RESPONSE -> R.string.detail_waiting_for_response
        AssistantState.CALLING -> R.string.detail_calling
    }

    @StringRes
    private fun errorDetailRes(error: AssistantError): Int = when (error) {
        AssistantError.PERMISSION_DENIED -> R.string.detail_error_permission_denied
        AssistantError.MICROPHONE_UNAVAILABLE -> R.string.detail_error_microphone_unavailable
        AssistantError.SERVICE_START_FAILED -> R.string.detail_error_service_start_failed
        AssistantError.PIPELINE_START_FAILED -> R.string.detail_error_pipeline_start_failed
        AssistantError.RECOGNITION_UNAVAILABLE -> R.string.detail_error_recognition_unavailable
    }

    @ColorRes
    private fun statusColorRes(state: AssistantState): Int = when (state) {
        AssistantState.STOPPED -> R.color.driveroid_status_idle
        AssistantState.STARTING -> R.color.driveroid_status_pending
        AssistantState.ERROR -> R.color.driveroid_status_error
        else -> R.color.driveroid_status_active
    }

    private fun color(@ColorRes colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

    /**
     * The lifecycle decision that `onDestroy()` alone cannot make.
     *
     * - Configuration change (rotation, dark mode, font size, ...): the Activity is recreated but the
     *   user did not close DriveRoid, so the assistant keeps running.
     * - `isFinishing` (Back, `finish()`, `finishAndRemoveTask()`): the user closed the app, so V1 ends
     *   the assistant instead of listening forever.
     * - Neither: the system destroyed the Activity to reclaim memory while the task is still in
     *   Recents. The assistant must survive that and re-attach on the next launch, which is exactly
     *   why `onDestroy()` on its own is not treated as "the app was closed".
     *
     * Removing the app from Recents does not necessarily reach this method at all; that case is
     * handled by [VoiceAssistantService.onTaskRemoved].
     */
    private fun handleActivityDestruction() {
        when {
            isChangingConfigurations -> DriveroidLog.i(
                TAG,
                "Configuration change: assistant keeps running",
            )

            isFinishing -> {
                DriveroidLog.i(TAG, "DriveRoid closed by the user: ending the assistant")
                VoiceAssistantService.stop(this)
            }

            else -> DriveroidLog.i(TAG, "Activity destroyed by the system: assistant keeps running")
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
