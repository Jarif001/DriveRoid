package com.prettyj.driveroid.assistant

/**
 * Reasons the assistant can fail to run.
 *
 * Keeping them as a closed set instead of raw exception messages lets the UI show localised text
 * while the logs stay comparable between builds and devices.
 */
enum class AssistantError {

    /** Microphone access was not granted, so the assistant cannot start. */
    PERMISSION_DENIED,

    /** The device has no microphone at all. */
    MICROPHONE_UNAVAILABLE,

    /** The foreground service could not be started (platform restriction or system error). */
    SERVICE_START_FAILED,

    /** The voice pipeline (wake word / recognition stage) could not be prepared. */
    PIPELINE_START_FAILED,

    /** There is no speech recognition service on this device, so nothing could ever be understood. */
    RECOGNITION_UNAVAILABLE,
}
