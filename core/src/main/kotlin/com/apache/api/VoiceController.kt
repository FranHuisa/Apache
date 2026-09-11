package com.apache.api

import com.apache.voice.SpeechToTextClient
import com.apache.voice.TextToSpeechClient

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController

/**
 * Expone los endpoints utilizados por la aplicación de escritorio
 * para convertir grabaciones de audio en texto (Speech-to-Text) y
 * para convertir el texto de las respuestas de Apache en audio
 * (Text-to-Speech).
 */
@RestController
@RequestMapping("/api/voice")
class VoiceController(
    private val speechToTextClient: SpeechToTextClient,
    private val textToSpeechClient: TextToSpeechClient
) {

    @PostMapping(
        "/transcribe",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun transcribe(
        @RequestPart("audio") audio: ByteArray
    ): VoiceTranscriptionResponse {

        val text = speechToTextClient.transcribe(audio)

        return VoiceTranscriptionResponse(
            text = text
        )
    }

    @PostMapping("/speak")
    fun speak(
        @RequestBody request: VoiceSpeakRequest
    ): VoiceSpeechResponse {

        val speech = textToSpeechClient.synthesize(request.text)

        return VoiceSpeechResponse(
            audio = speech.audioBase64,
            sampleRate = speech.sampleRate
        )
    }
}

/**
 * Respuesta del endpoint de transcripción.
 */
data class VoiceTranscriptionResponse(
    val text: String
)

/**
 * Petición del endpoint de síntesis de voz.
 */
data class VoiceSpeakRequest(
    val text: String
)

/**
 * Respuesta del endpoint de síntesis de voz.
 */
data class VoiceSpeechResponse(
    val audio: String,
    val sampleRate: Int
)