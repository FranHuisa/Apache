package com.apache.security

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Una acción de una tool que quedó "en pausa" esperando que el usuario la
 * confirme o la rechace desde la UI (ej. "Apache, elimina esta carpeta" ->
 * "Esta acción puede eliminar archivos. ¿Quieres que continúe?").
 */
data class PendingConfirmation(

    val id: String = UUID.randomUUID().toString(),

    val conversationId: Long,

    val toolName: String,

    val args: Map<String, Any?>,

    val humanReadableWarning: String
)

/**
 * Almacén en memoria de confirmaciones pendientes.
 *
 * Para el prototipo basta con memoria (ConcurrentHashMap): si Apache se
 * reinicia, las confirmaciones pendientes se pierden, lo cual es un
 * comportamiento razonable de seguridad (mejor perder una acción pendiente
 * que ejecutarla "a ciegas" tras un reinicio). Si en el futuro hiciera
 * falta persistirlas, se movería a la base de datos igual que Conversation/Message.
 */
object PendingConfirmationStore {

    private val store = ConcurrentHashMap<String, PendingConfirmation>()

    fun add(confirmation: PendingConfirmation): PendingConfirmation {
        store[confirmation.id] = confirmation
        return confirmation
    }

    fun take(id: String): PendingConfirmation? = store.remove(id)
}