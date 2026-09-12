package com.apache.api.dto

/**
 * Petición para confirmar (o rechazar) una acción pendiente.
 *
 * confirmationId es el identificador en memoria generado por
 * PendingConfirmationStore (UUID como String), no el id de la
 * conversación, por lo que no participa en la migración a Long.
 */
data class ConfirmRequest(
    val confirmationId: String,
    val approved: Boolean
)
