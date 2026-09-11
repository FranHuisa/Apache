package com.apache.api.dto

/** Petición entrante desde la app de escritorio para enviar un mensaje. */
data class ChatRequest(
    val conversationId: String? = null,
    val message: String
)

/**
 * Respuesta que recibe la app de escritorio. Solo uno de los dos casos
 * relevantes está "activo" a la vez:
 *  - reply != null                -> mostrar el texto normalmente
 *  - needsConfirmation == true    -> mostrar un diálogo de confirmación
 *    con `warning`, y guardar `confirmationId` para la siguiente llamada
 *    a /api/chat/confirm.
 */
data class ChatResponse(
    val conversationId: String,
    val reply: String? = null,
    val needsConfirmation: Boolean = false,
    val confirmationId: String? = null,
    val warning: String? = null
)

/** Petición para confirmar (o rechazar) una acción pendiente. */
data class ConfirmRequest(
    val confirmationId: String,
    val approved: Boolean
)

/** Un turno del historial de una conversación, ya filtrado para mostrarlo en la UI. */
data class ChatHistoryMessage(
    val role: String,
    val content: String
)

/**
 * Respuesta del endpoint de historial. `exists = false` indica que ese conversationId no existe
 * en el Core (ej. porque se borró la base de datos): el cliente debe empezar una conversación
 * nueva en vez de intentar seguir usando ese id.
 */
data class ChatHistoryResponse(
    val conversationId: String,
    val exists: Boolean,
    val messages: List<ChatHistoryMessage>
)