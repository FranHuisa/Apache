package com.apache.api

import com.apache.voice.SpeechToTextClient

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController

/**
 * Expone el endpoint utilizado por la aplicación de escritorio
 * para convertir grabaciones de audio en texto.
 */
@RestController
@RequestMapping("/api/voice")
class VoiceController(
    private val speechToTextClient: SpeechToTextClient
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
}

/**
 * Respuesta del endpoint de transcripción.
 */
data class VoiceTranscriptionResponse(
    val text: String
)