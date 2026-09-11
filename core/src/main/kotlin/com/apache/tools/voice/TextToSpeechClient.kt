package com.apache.voice

import com.fasterxml.jackson.databind.ObjectMapper

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import java.time.Duration

/**
 * Resultado de sintetizar voz a partir de texto.
 *
 * El audio viene en PCM 16-bit sin cabecera, tal y como lo devuelve
 * Gemini. [sampleRate] indica la frecuencia de muestreo necesaria para
 * reproducirlo correctamente en el cliente.
 */
data class SpeechResult(
    val audioBase64: String,
    val sampleRate: Int
)

/**
 * Cliente para convertir texto en voz mediante la API REST de Gemini.
 *
 * Recibe el texto de la respuesta del Agent y solicita a Gemini que
 * genere el audio correspondiente (Text-to-Speech nativo de Gemini).
 */
@Component
class TextToSpeechClient(
    @Value("\${apache.gemini.api-key}")
    private val apiKey: String,

    @Value("\${apache.gemini.voice.tts-model:gemini-2.5-flash-preview-tts}")
    private val model: String,

    @Value("\${apache.gemini.voice.tts-voice-name:Kore}")
    private val voiceName: String,

    @Value("\${apache.gemini.voice.tts-sample-rate:24000}")
    private val sampleRate: Int
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
     * Convierte un texto en audio hablado.
     *
     * @param text texto de la respuesta que Apache debe decir en voz alta
     * @return audio PCM 16-bit sin cabecera, en base64, junto con su sampleRate
     */
    fun synthesize(text: String): SpeechResult {

        if (text.isBlank()) {
            return SpeechResult(audioBase64 = "", sampleRate = sampleRate)
        }

        val body =
            mapOf(
                "contents" to
                    listOf(
                        mapOf(
                            "role" to "user",
                            "parts" to listOf(mapOf("text" to text))
                        )
                    ),
                "generationConfig" to
                    mapOf(
                        "responseModalities" to listOf("AUDIO"),
                        "speechConfig" to
                            mapOf(
                                "voiceConfig" to
                                    mapOf(
                                        "prebuiltVoiceConfig" to
                                            mapOf("voiceName" to voiceName)
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
                    "Error llamando a Gemini para generar voz: " +
                            "${response.code} ${response.body?.string()}"
                )
            }

            val json =
                mapper.readTree(
                    response.body?.string()
                )

            val audioBase64 =
                json["candidates"]
                    ?.get(0)
                    ?.get("content")
                    ?.get("parts")
                    ?.get(0)
                    ?.get("inlineData")
                    ?.get("data")
                    ?.asText()
                    ?: throw IllegalStateException(
                        "Gemini no ha devuelto audio para el texto proporcionado."
                    )

            return SpeechResult(audioBase64 = audioBase64, sampleRate = sampleRate)
        }
    }
}
