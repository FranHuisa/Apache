package com.apache.api.dto

/**
 * Respuesta que devuelve Apache a la aplicación de escritorio.
 *
 * conversationId permite al cliente mantener la conversación actual
 * y enviarlo de nuevo en los siguientes mensajes.
 */
data class ChatResponse(
    val conversationId: Long,
    val reply: String? = null,
    val needsConfirmation: Boolean = false,
    val confirmationId: String? = null,
    val warning: String? = null
)