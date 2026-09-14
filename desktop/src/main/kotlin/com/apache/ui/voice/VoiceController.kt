package com.apache.ui.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.audio.AudioPlayer
import com.apache.audio.MicrophoneRecorder
import com.apache.network.synthesizeSpeech
import com.apache.network.transcribeAudio
import com.apache.util.stopListeningRegex
import com.apache.util.wakeWordRegex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Controla la grabación puntual por micrófono, el modo escucha continua
 * (wake word "Apache") y la reproducción de las respuestas por voz (TTS).
 *
 * No conoce el chat directamente: cada orden reconocida (por micrófono o por
 * el modo escucha) se delega a [onCommand], y los avisos que no pasan por el
 * Core (p. ej. "no te he entendido") se delegan a [onSystemMessage]. Así este
 * controller se puede probar o reutilizar sin arrastrar el estado del chat.
 */
class VoiceController(
    private val scope: CoroutineScope,
    private val onCommand: suspend (text: String, speak: Boolean) -> Unit,
    private val onSystemMessage: (String) -> Unit
) {

    private val microphoneRecorder = MicrophoneRecorder()
    private val audioPlayer = AudioPlayer()

    var isRecording by mutableStateOf(false)
        private set

    var listenModeEnabled by mutableStateOf(false)
        private set

    // El texto de la respuesta se sigue mostrando igual; silenciar solo omite el audio.
    var isMuted by mutableStateOf(false)

    fun toggleMute() {
        isMuted = !isMuted
    }

    /**
     * Convierte un texto en audio y lo reproduce por los altavoces.
     * Fallo silencioso: si la síntesis de voz falla, la respuesta de texto
     * ya se ha mostrado igualmente, así que no interrumpe la conversación.
     */
    fun speak(text: String) {
        if (text.isBlank() || isMuted) return

        try {
            val speech = synthesizeSpeech(text)
            if (speech.audio.isNotBlank()) {
                val audioBytes = Base64.getDecoder().decode(speech.audio)
                audioPlayer.play(audioBytes, speech.sampleRate)
            }
        } catch (_: Exception) {
            // Fallo silencioso, ver kdoc de la función.
        }
    }

    /** Inicia o detiene una grabación puntual (botón "Micrófono"). */
    fun toggleRecording() {
        if (isRecording) {
            scope.launch(Dispatchers.IO) {
                try {
                    val audio = microphoneRecorder.stop()
                    withContext(Dispatchers.Main) { isRecording = false }
                    if (audio.isNotEmpty()) processRecordedAudio(audio)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isRecording = false
                        onSystemMessage("No se ha podido detener la grabación: ${e.message}")
                    }
                }
            }
            return
        }

        isRecording = true

        scope.launch(Dispatchers.IO) {
            try {
                microphoneRecorder.start { audio ->
                    scope.launch(Dispatchers.Main) { isRecording = false }
                    scope.launch(Dispatchers.IO) { processRecordedAudio(audio) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isRecording = false
                    onSystemMessage("No se ha podido acceder al micrófono: ${e.message}")
                }
            }
        }
    }

    private suspend fun processRecordedAudio(audio: ByteArray) {
        try {
            val text = transcribeAudio(audio).text.trim()
            if (text.isNotBlank()) {
                onCommand(text, true)
            } else {
                withContext(Dispatchers.Main) {
                    onSystemMessage("No he podido reconocer lo que has dicho.")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onSystemMessage("No se ha podido transcribir el audio: ${e.message}")
            }
        }
    }

    /** Activa o desactiva el modo escucha continua (wake word "Apache"). */
    fun toggleListenMode() {
        listenModeEnabled = !listenModeEnabled

        if (listenModeEnabled) {
            startPassiveListening()
        } else {
            scope.launch(Dispatchers.IO) { microphoneRecorder.stop() }
        }
    }

    /** Desactiva la escucha mediante una orden local, sin enviarla al Agent. */
    private suspend fun stopListeningByVoice() {
        withContext(Dispatchers.Main) {
            listenModeEnabled = false
            onSystemMessage("Modo escucha desactivado.")
        }
        speak("Modo escucha desactivado.")
    }

    /**
     * Escucha en segundo plano hasta detectar la palabra de activación "Apache".
     * Cada captura termina tras un segundo de silencio, se transcribe y se
     * comprueba si contiene "Apache".
     */
    private fun startPassiveListening(awaitingCommand: Boolean = false) {
        if (!listenModeEnabled) return

        microphoneRecorder.start { audio ->
            scope.launch(Dispatchers.IO) {
                if (audio.isEmpty()) {
                    if (listenModeEnabled) startPassiveListening(awaitingCommand)
                    return@launch
                }

                try {
                    val transcript = transcribeAudio(audio).text.trim()

                    if (awaitingCommand) {
                        if (transcript.isNotBlank()) {
                            if (stopListeningRegex.matches(transcript)) {
                                stopListeningByVoice()
                            } else {
                                onCommand(transcript, true)
                            }
                        }
                        if (listenModeEnabled) startPassiveListening()
                    } else {
                        val match = wakeWordRegex.find(transcript)

                        if (match == null) {
                            if (listenModeEnabled) startPassiveListening()
                            return@launch
                        }

                        val command = transcript
                            .substring(match.range.last + 1)
                            .trim()
                            .trimStart(',', '.', ':', ';', '-')
                            .trim()

                        when {
                            stopListeningRegex.matches(transcript) -> stopListeningByVoice()
                            command.isNotBlank() -> {
                                onCommand(command, true)
                                if (listenModeEnabled) startPassiveListening()
                            }
                            else -> startPassiveListening(awaitingCommand = true)
                        }
                    }
                } catch (_: Exception) {
                    if (listenModeEnabled) startPassiveListening(awaitingCommand)
                }
            }
        }
    }
}
