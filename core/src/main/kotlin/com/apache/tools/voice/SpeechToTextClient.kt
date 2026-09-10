package com.apache.voice

import com.fasterxml.jackson.databind.ObjectMapper

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import java.time.Duration
import java.util.Base64

/**
 * Cliente para convertir audio en texto mediante la API REST de Gemini.
 *
 * Recibe audio PCM 16-bit, 16 kHz y mono procedente del micrófono
 * y solicita a Gemini que transcriba únicamente lo que se ha dicho.
 */
@Component
class SpeechToTextClient(
    @Value("\${apache.gemini.api-key}")
    private val apiKey: String,

    @Value("\${apache.gemini.model:gemini-2.0-flash}")
    private val model: String
) {

    private val http =
        OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(30))
            .build()

    private val mapper = ObjectMapper() 

    private val baseUrl
        get() =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    /**
     * Transcribe un audio PCM 16-bit, 16 kHz y mono.
     *
     * @param audioData audio PCM sin cabecera WAV
     * @return texto reconocido por Gemini
     */
    fun transcribe(audioData: ByteArray): String {

        if (audioData.isEmpty()) {
            return ""
        }

        val audioBase64 =
            Base64.getEncoder().encodeToString(audioData)

        val body =
            mapOf(
                "contents" to
                    listOf(
                        mapOf(
                            "role" to "user",
                            "parts" to
                                listOf(
                                    mapOf(
                                        "text" to
                                            "Transcribe exactamente lo que se dice en este audio. " +
                                                    "Devuelve únicamente el texto transcrito, sin explicaciones."
                                    ),
                                    mapOf(
                                        "inline_data" to
                                            mapOf(
                                                "mime_type" to "audio/L16;rate=16000",
                                                "data" to audioBase64
                                            )
                                    )
                                )
                        )
                    )
            )

        val request =
            Request.Builder()
                .url(baseUrl)
                .post(
                    mapper.writeValueAsString(body)
                        .toRequestBody(
                            "application/json".toMediaType()
                        )
                )
                .build()

        http.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Error llamando a Gemini para transcribir: " +
                            "${response.code} ${response.body?.string()}"
                )
            }

            val json =
                mapper.readTree(
                    response.body?.string()
                )

            return json["candidates"]
                ?.get(0)
                ?.get("content")
                ?.get("parts")
                ?.get(0)
                ?.get("text")
                ?.asText()
                ?.trim()
                ?: ""
        }
    }
}