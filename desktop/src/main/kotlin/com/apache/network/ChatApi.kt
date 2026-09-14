package com.apache.network

import com.apache.model.ChatRequest
import com.apache.model.ChatResponse
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Envía un mensaje al Apache Core.
 *
 * Desktop -> HTTP POST -> Core -> Agent -> Gemini
 *
 * Esta función es bloqueante (red), así que debe ejecutarse siempre desde
 * Dispatchers.IO, nunca desde el hilo de la interfaz.
 */
fun sendMessageToCore(conversationId: Long?, message: String): ChatResponse {

    val json = objectMapper.writeValueAsString(
        ChatRequest(conversationId = conversationId, message = message)
    )

    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/chat")
        .post(json.toRequestBody(jsonMediaType))
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("El Core respondió con HTTP ${response.code}")
        }

        val responseBody =
            response.body?.string() ?: throw Exception("El Core no devolvió ninguna respuesta.")

        return objectMapper.readValue(responseBody)
    }
}
