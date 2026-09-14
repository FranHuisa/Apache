package com.apache.model

// Representa un mensaje que aparece en el chat.
data class ChatMessage(val text: String, val isUser: Boolean)

// DTO que enviamos al Core.
data class ChatRequest(val conversationId: Long? = null, val message: String)

// DTO que recibimos del Core.
data class ChatResponse(
    val conversationId: Long? = null,
    val reply: String? = null,
    val needsConfirmation: Boolean = false,
    val confirmationId: String? = null,
    val warning: String? = null
)
