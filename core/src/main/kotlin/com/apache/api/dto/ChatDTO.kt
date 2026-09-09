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