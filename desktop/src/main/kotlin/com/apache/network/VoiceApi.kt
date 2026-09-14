package com.apache.network

import com.apache.model.VoiceSpeakRequest
import com.apache.model.VoiceSpeechResponse
import com.apache.model.VoiceTranscriptionResponse
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Envía una grabación al Apache Core para convertirla en texto.
 *
 * Desktop -> HTTP POST -> Core -> SpeechToTextClient -> Gemini
 */
fun transcribeAudio(audioData: ByteArray): VoiceTranscriptionResponse {

    val audioBody = audioData.toRequestBody("audio/L16;rate=16000".toMediaType())

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("audio", "recording.pcm", audioBody)
        .build()

    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/voice/transcribe")
        .post(requestBody)
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("El Core respondió con HTTP ${response.code}")
        }

        val responseBody =
            response.body?.string() ?: throw Exception("El Core no devolvió ninguna transcripción.")

        return objectMapper.readValue(responseBody)
    }
}

/**
 * Solicita al Core que convierta un texto en audio (Text-to-Speech).
 *
 * Desktop -> HTTP POST -> Core -> TextToSpeechClient -> Gemini
 */
fun synthesizeSpeech(text: String): VoiceSpeechResponse {

    val json = objectMapper.writeValueAsString(VoiceSpeakRequest(text = text))

    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/voice/speak")
        .post(json.toRequestBody(jsonMediaType))
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("El Core respondió con HTTP ${response.code}")
        }

        val responseBody = response.body?.string() ?: throw Exception("El Core no devolvió ningún audio.")

        return objectMapper.readValue(responseBody)
    }
}
