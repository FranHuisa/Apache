package com.apache.api.dto

/**
 * Respuesta que devuelve Apache a la aplicación de escritorio.
 *
 * conversationId permite al cliente mantener la conversación actual
 * y enviarlo de nuevo en los siguientes mensajes.
 *
 * Es nullable porque /api/chat/confirm no genera ni conoce una
 * conversación nueva: el cliente ya la tiene guardada de la llamada
 * anterior a /api/chat.
 */
data class ChatResponse(
    val conversationId: Long? = null,
    val reply: String? = null,
    val needsConfirmation: Boolean = false,
    val confirmationId: String? = null,
    val warning: String? = null,
    /** Imágenes (0-3) que el Desktop dibuja debajo de la respuesta. */
    val images: List<ChatImageDto> = emptyList()
)

/** Imagen que acompaña a una respuesta de Apache. */
data class ChatImageDto(
    val url: String,
    val title: String = "",
    val sourceUrl: String? = null
)