package com.apache.api.dto

/**
 * Datos que recibe Apache cuando el usuario envía un mensaje.
 *
 * conversationId es null cuando se inicia una conversación nueva.
 * En ese caso, el Agent crea automáticamente una conversación.
 */
data class ChatRequest(
    val conversationId: Long? = null,
    val message: String
)