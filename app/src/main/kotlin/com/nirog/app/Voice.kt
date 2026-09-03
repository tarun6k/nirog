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

    /** True when this device can hear at all (ASR engine present). */
    fun canListen(): Boolean

    /** Streams partial transcripts; onFinal(null) = error or nothing heard. */
    fun startListening(onPartial: (String) -> Unit, onFinal: (String?) -> Unit)

    /** Stop and deliver the final transcript. */
    fun stopListening()

    /** Abandon without a result. */
    fun cancelListening()

    fun shutdown()
}

class AndroidTtsVoice(private val context: Context) : VoiceProvider, TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var recognizer: android.speech.SpeechRecognizer? = null

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

    override fun canListen(): Boolean =
        android.speech.SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening(onPartial: (String) -> Unit, onFinal: (String?) -> Unit) {
        if (!canListen()) return onFinal(null)
        cancelListening()
        val r = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                partialResults
                    ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onPartial)
            }

            override fun onResults(results: android.os.Bundle?) {
                onFinal(
                    results
                        ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull(),
                )
            }

            override fun onError(error: Int) = onFinal(null)
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        r.startListening(
            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            },
        )
    }

    override fun stopListening() {
        recognizer?.stopListening()
    }

    override fun cancelListening() {
        recognizer?.destroy()
        recognizer = null
    }

    override fun shutdown() {
        cancelListening()
        tts.shutdown()
    }
}

/**
 * Bhashini/ULCA implementation slot. Requires ULCA registration + API key +
 * pipeline config — do not invent endpoints; wire when credentials exist.
 * ASR joins the interface at the same time (the voice Q&A feature needs it).
 */
fun createVoice(context: Context): VoiceProvider = AndroidTtsVoice(context)
