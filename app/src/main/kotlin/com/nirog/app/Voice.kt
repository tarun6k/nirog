package com.nirog.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voice output/input behind one interface. Bhashini (ULCA) is the target
 * provider for TTS+ASR in Indian languages; it needs registered API keys, so
 * until those are provisioned the Android platform TTS is the implementation.
 * Callers never know which provider answered.
 */
interface VoiceProvider {
    fun speak(text: String)
    fun shutdown()
}

class AndroidTtsVoice(context: Context) : VoiceProvider, TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            ready = true
        }
    }

    override fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nirog")
    }

    override fun shutdown() = tts.shutdown()
}

/**
 * Bhashini/ULCA implementation slot. Requires ULCA registration + API key +
 * pipeline config — do not invent endpoints; wire when credentials exist.
 * ASR joins the interface at the same time (the voice Q&A feature needs it).
 */
fun createVoice(context: Context): VoiceProvider = AndroidTtsVoice(context)
