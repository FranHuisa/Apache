package com.apache.database.service

import com.apache.database.repository.ConversationMessage
import com.apache.database.repository.ConversationSummary
import com.apache.database.repository.MemoryRecord
import com.apache.database.repository.UserMemoryRepository
import org.springframework.stereotype.Service

/**
 * Memoria a largo plazo de Apache: datos sobre el usuario (nombre, gustos,
 * rutinas...) que se recuerdan entre conversaciones y se le pasan a Gemini
 * en cada turno.
 */
@Service
class UserMemoryService(private val repository: UserMemoryRepository) {

    fun list(userId: Long): List<MemoryRecord> = repository.findAll(userId)

    /**
     * Guarda un dato. Si ya existe uno con la misma clave, se actualiza en vez
     * de duplicarlo (p. ej. "ciudad: Madrid" → "ciudad: Almería").
     *
     * @return el dato guardado y si era nuevo (true) o una actualización (false)
     */
    fun remember(
        userId: Long,
        key: String,
        value: String,
        type: String = DEFAULT_TYPE,
        importance: Double = DEFAULT_IMPORTANCE
    ): Pair<MemoryRecord, Boolean> {

        val cleanKey = key.trim().take(255)
        val cleanValue = value.trim()

        require(cleanKey.isNotBlank()) { "El dato a recordar necesita un nombre." }
        require(cleanValue.isNotBlank()) { "El dato a recordar no puede estar vacío." }

        val cleanType = normalizeType(type)
        val cleanImportance = importance.coerceIn(0.0, 1.0)

        val existing = repository.findByKey(userId, cleanKey)

        return if (existing != null) {
            repository.update(existing.id, cleanType, cleanKey, cleanValue, cleanImportance)
            (repository.findById(existing.id) ?: existing) to false
        } else {
            val id = repository.create(userId, cleanType, cleanKey, cleanValue, cleanImportance)
            (repository.findById(id) ?: error("No se ha podido recuperar el dato guardado (id=$id).")) to true
        }
    }

    fun update(memoryId: Long, key: String?, value: String?, type: String?, importance: Double?): MemoryRecord? {
        require(key == null || key.isNotBlank()) { "El nombre del dato no puede estar vacío." }
        require(value == null || value.isNotBlank()) { "El valor del dato no puede estar vacío." }

        repository.update(
            memoryId,
            type?.let(::normalizeType),
            key?.trim(),
            value?.trim(),
            importance?.coerceIn(0.0, 1.0)
        )
        return repository.findById(memoryId)
    }

    fun forget(memoryId: Long): Boolean = repository.delete(memoryId)

    /** Olvida un dato por su clave (lo usa la tool forgetFact, que no conoce ids). */
    fun forgetByKey(userId: Long, key: String): MemoryRecord? {
        val existing = repository.findByKey(userId, key.trim()) ?: return null
        repository.delete(existing.id)
        return existing
    }

    /**
     * Texto con lo que Apache sabe del usuario, para añadirlo a la instrucción
     * de sistema. Se limita a los [limit] datos más importantes para no
     * inflar cada petición a Gemini.
     */
    fun contextForPrompt(userId: Long, limit: Int = 60): String {
        val memories = list(userId).take(limit)
        if (memories.isEmpty()) return ""

        return memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
    }

    fun conversations(userId: Long, limit: Int = 50): List<ConversationSummary> =
        repository.findConversations(userId, limit)

    fun conversationMessages(conversationId: Long): List<ConversationMessage> =
        repository.findConversationMessages(conversationId)

    companion object {
        const val DEFAULT_TYPE = "otro"
        const val DEFAULT_IMPORTANCE = 0.5

        /** Categorías que entiende la interfaz. Cualquier otra se guarda como "otro". */
        val TYPES = listOf("personal", "preferencia", "rutina", "trabajo", "salud", "otro")

        fun normalizeType(type: String): String {
            val clean = type.trim().lowercase()
            return if (clean in TYPES) clean else DEFAULT_TYPE
        }
    }
}
