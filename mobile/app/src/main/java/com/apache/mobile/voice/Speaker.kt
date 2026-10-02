package com.apache.mobile.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Lee las respuestas en voz alta con el motor de voz del propio Android
 * (gratis y sin conexión; no gasta llamadas a Gemini).
 *
 * [speak] puede avisar al terminar de hablar: lo usa el modo conversación
 * para volver a escuchar justo después.
 */
class Speaker(context: Context) {

    private var ready = false
    private val main = Handler(Looper.getMainLooper())
    private var onDone: (() -> Unit)? = null

    /** true mientras está hablando. */
    var isSpeaking = false
        private set

    // lateinit: el callback de inicialización necesita referirse al propio motor.
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Locale.forLanguageTag("es-ES")
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) = finished()

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finished()

                    override fun onError(utteranceId: String?, errorCode: Int) = finished()
                })
            }
        }
    }

    private fun finished() {
        main.post {
            isSpeaking = false
            val callback = onDone
            onDone = null
            callback?.invoke()
        }
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        val clean = text.replace(Regex("https?://\\S+"), "").replace(Regex("[*#_`]"), "").trim()
        if (!ready || clean.isBlank()) {
            onFinished?.let { main.post(it) }
            return
        }
        onDone = onFinished
        isSpeaking = true
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "apache-${System.nanoTime()}")
    }

    /** Calla sin avisar a nadie (se descarta el "al terminar"). */
    fun stop() {
        onDone = null
        isSpeaking = false
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
