package com.apache.model

// Imagen que Apache muestra debajo de una de sus respuestas.
data class ChatImage(
    val url: String,
    val title: String = "",
    val sourceUrl: String? = null
)

// Representa un mensaje que aparece en el chat.
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val images: List<ChatImage> = emptyList(),
    // Si no es null, el mensaje es una acción pendiente de confirmar (botones Sí / No).
    val confirmationId: String? = null
)

// DTO para confirmar o rechazar una acción pendiente.
data class ConfirmRequest(val confirmationId: String, val approved: Boolean)

// DTO que enviamos al Core.
data class ChatRequest(val conversationId: Long? = null, val message: String)

// DTO que recibimos del Core.
data class ChatResponse(
    val conversationId: Long? = null,
    val reply: String? = null,
    val needsConfirmation: Boolean = false,
    val confirmationId: String? = null,
    val warning: String? = null,
    val images: List<ChatImage> = emptyList()
)
