package com.apache.network

import com.apache.model.ConversationMessageDto
import com.apache.model.ConversationSummaryDto
import com.apache.model.MemoryItemDto
import com.apache.model.MemoryRequestDto
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/*
 * Llamadas a /api/memory del Core. Todas son bloqueantes (red): llamarlas
 * siempre desde Dispatchers.IO.
 */

fun fetchMemories(): List<MemoryItemDto> =
    executeMemoryRequest(Request.Builder().url("$CORE_BASE_URL/api/memory").get().build())

fun createMemory(request: MemoryRequestDto): MemoryItemDto =
    executeMemoryRequest(
        Request.Builder()
            .url("$CORE_BASE_URL/api/memory")
            .post(objectMapper.writeValueAsString(request).toRequestBody(jsonMediaType))
            .build()
    )

fun updateMemory(id: Long, request: MemoryRequestDto): MemoryItemDto =
    executeMemoryRequest(
        Request.Builder()
            .url("$CORE_BASE_URL/api/memory/$id")
            .put(objectMapper.writeValueAsString(request).toRequestBody(jsonMediaType))
            .build()
    )

fun deleteMemory(id: Long) {
    val request = Request.Builder().url("$CORE_BASE_URL/api/memory/$id").delete().build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw RuntimeException("Error ${response.code} al borrar el dato.")
        }
    }
}

fun fetchConversations(): List<ConversationSummaryDto> =
    executeMemoryRequest(Request.Builder().url("$CORE_BASE_URL/api/memory/conversations").get().build())

fun fetchConversationMessages(conversationId: Long): List<ConversationMessageDto> =
    executeMemoryRequest(
        Request.Builder().url("$CORE_BASE_URL/api/memory/conversations/$conversationId").get().build()
    )

private inline fun <reified T> executeMemoryRequest(request: Request): T {
    httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException("Error ${response.code}: ${bodyText.ifBlank { "Sin respuesta del Core." }}")
        }

        return objectMapper.readValue(bodyText)
    }
}
