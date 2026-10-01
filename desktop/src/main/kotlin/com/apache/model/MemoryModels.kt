package com.apache.model

// Dato que Apache recuerda del usuario (sección Memoria).
data class MemoryItemDto(
    val id: Long,
    val key: String,
    val value: String,
    val type: String,
    val importance: Double = 0.5,
    val updatedAt: String = ""
)

// Petición para crear o editar un dato de memoria.
data class MemoryRequestDto(
    val key: String? = null,
    val value: String? = null,
    val type: String? = null,
    val importance: Double? = null
)

// Conversación pasada, tal y como se lista en Memoria.
data class ConversationSummaryDto(
    val id: Long,
    val title: String,
    val messageCount: Int,
    val createdAt: String,
    val updatedAt: String
)

// Mensaje visible de una conversación pasada ("user" o "model").
data class ConversationMessageDto(
    val role: String,
    val text: String,
    val createdAt: String
)
