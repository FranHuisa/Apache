package com.apache.voice

import com.fasterxml.jackson.databind.ObjectMapper

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    private val sampleRate = 16_000
    private val bitsPerSample = 16
    private val channels = 1

    /**
     * Transcribe un audio PCM 16-bit, 16 kHz y mono.
     *
     * IMPORTANTE: el mime type "audio/L16" (PCM crudo) no está entre los formatos que la API
     * pública de Gemini (generativelanguage.googleapis.com) documenta como soportados para
     * audio inline (solo WAV, MP3, AIFF, AAC, OGG y FLAC: https://ai.google.dev/gemini-api/docs/audio).
     * Mandar PCM crudo con ese mime type provoca que la transcripción falle o venga vacía de
     * forma intermitente, lo cual rompía silenciosamente la detección de la wake word. Por eso
     * envolvemos el PCM en un contenedor WAV mínimo antes de mandarlo, y usamos "audio/wav".
     *
     * @param audioData audio PCM sin cabecera WAV
     * @return texto reconocido por Gemini
     */
    fun transcribe(audioData: ByteArray): String {

        if (audioData.isEmpty()) {
            return ""
        }

        val audioBase64 =
            Base64.getEncoder().encodeToString(wrapInWav(audioData))

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
                                                "mime_type" to "audio/wav",
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

    /**
     * Añade una cabecera RIFF/WAV de 44 bytes delante del PCM crudo (16-bit, 16 kHz, mono),
     * sin recodificar ni copiar más datos de los necesarios. Es el formato mínimo que
     * cualquier lector de WAV (incluido Gemini) reconoce.
     */
    private fun wrapInWav(pcm: ByteArray): ByteArray {

        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)

        val out = ByteArrayOutputStream(44 + pcm.size)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size) // tamaño total del fichero - 8
        header.put("WAVE".toByteArray(Charsets.US_ASCII))

        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // tamaño del bloque fmt
        header.putShort(1) // PCM sin comprimir
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())

        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)

        out.write(header.array())
        out.write(pcm)

        return out.toByteArray()
    }
}