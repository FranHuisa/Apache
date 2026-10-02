package com.apache.mobile.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Lee las respuestas en voz alta con el motor de voz del propio Android
 * (gratis y sin conexión; no gasta llamadas a Gemini).
 */
class Speaker(context: Context) {

    private var ready = false

    // lateinit: el callback de inicialización necesita referirse al propio motor.
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts.language = Locale.forLanguageTag("es-ES")
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        // Sin URLs ni símbolos que el motor leería letra a letra.
        val clean = text.replace(Regex("https?://\\S+"), "").replace(Regex("[*#_`]"), "")
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "apache-reply")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
