package com.prettyj.driveroid.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import com.prettyj.driveroid.util.DriveroidLog

/**
 * Speaks confirmation announcements using Android's [TextToSpeech] API.
 *
 * Uses the default system voice. All methods must be called from the main thread.
 */
class SpeechSynthesizer(private val context: Context) {

    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            DriveroidLog.i(TAG, "TTS initialized; isReady=true")
        } else {
            DriveroidLog.e(TAG, "TTS initialization failed with status $status")
        }
    }

    private var isReady = false

    /** Speaks [text]. Queues are flushed so only the latest announcement is heard. */
    fun speak(text: String) {
        if (!isReady) {
            DriveroidLog.w(TAG, "TTS not ready, skipping: $text")
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DriveroidTTS")
        DriveroidLog.d(TAG, "Spoke: $text")
    }

    fun shutdown() {
        tts.shutdown()
        DriveroidLog.d(TAG, "TTS shut down")
    }

    companion object {
        private const val TAG = "SpeechSynthesizer"
    }
}
