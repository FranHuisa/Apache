package com.apache.api.dto

/**
 * Datos que recibe Apache cuando el usuario envía un mensaje.
 *
 * conversationId es null cuando se inicia una conversación nueva.
 * En ese caso, el Agent crea automáticamente una conversación.
 */
data class ChatRequest(
    val conversationId: Long? = null,
    val message: String,
    /** Imágenes, capturas o archivos adjuntos al mensaje (opcional). */
    val attachments: List<ChatAttachmentDto> = emptyList(),
    /** true si el mensaje viene del micrófono: la respuesta se leerá en voz alta. */
    val fromVoice: Boolean = false
)

/** Archivo adjunto a un mensaje: nombre, tipo MIME y contenido en Base64. */
data class ChatAttachmentDto(
    val name: String,
    val mimeType: String,
    val data: String
)