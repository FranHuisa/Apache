package com.apache.database.repository

import com.apache.database.tables.Conversations
import com.apache.database.tables.Memory
import com.apache.database.tables.Messages
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository

/** Un dato que Apache recuerda del usuario (tabla `memory`). */
data class MemoryRecord(
    val id: Long,
    val type: String,
    val key: String,
    val value: String,
    val importance: Double,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/** Resumen de una conversación pasada, para listarla en la sección Memoria. */
data class ConversationSummary(
    val id: Long,
    val title: String,
    val messageCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/** Mensaje visible de una conversación (sin llamadas internas a herramientas). */
data class ConversationMessage(
    val role: String,
    val text: String,
    val createdAt: LocalDateTime
)

/**
 * Acceso a la memoria a largo plazo de Apache (tabla `memory`) y a la
 * consulta del historial de conversaciones para la interfaz.
 *
 * No confundir con [com.apache.memory.MemoryRepository], que es la memoria
 * de cada conversación (los turnos que se le envían a Gemini).
 */
@Repository
class UserMemoryRepository {

    fun findAll(userId: Long): List<MemoryRecord> = transaction {
        Memory
            .selectAll()
            .where { Memory.userId eq userId }
            .orderBy(Memory.importance to SortOrder.DESC, Memory.updatedAt to SortOrder.DESC)
            .filter { row -> row[Memory.expiresAt]?.isAfter(LocalDateTime.now()) ?: true }
            .map { it.toMemoryRecord() }
    }

    fun findById(memoryId: Long): MemoryRecord? = transaction {
        Memory.selectAll().where { Memory.id eq memoryId }.firstOrNull()?.toMemoryRecord()
    }

    /** Busca un dato por su clave, sin distinguir mayúsculas (para no duplicar "nombre" y "Nombre"). */
    fun findByKey(userId: Long, key: String): MemoryRecord? = transaction {
        Memory
            .selectAll()
            .where { Memory.userId eq userId }
            .firstOrNull { it[Memory.key].equals(key, ignoreCase = true) }
            ?.toMemoryRecord()
    }

    fun create(userId: Long, type: String, key: String, value: String, importance: Double): Long = transaction {
        val now = LocalDateTime.now()

        Memory.insert {
            it[Memory.userId] = userId
            it[Memory.type] = type
            it[Memory.key] = key
            it[Memory.value] = value
            it[Memory.importance] = importance
            it[Memory.confidence] = 1.0
            it[Memory.createdAt] = now
            it[Memory.updatedAt] = now
        } get Memory.id
    }

    fun update(memoryId: Long, type: String?, key: String?, value: String?, importance: Double?): Boolean = transaction {
        Memory.update({ Memory.id eq memoryId }) {
            if (type != null) it[Memory.type] = type
            if (key != null) it[Memory.key] = key
            if (value != null) it[Memory.value] = value
            if (importance != null) it[Memory.importance] = importance
            it[Memory.updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun delete(memoryId: Long): Boolean = transaction {
        Memory.deleteWhere { Memory.id eq memoryId } > 0
    }

    // --- Conversaciones (solo lectura) ---

    /** Conversaciones del usuario, las más recientes primero, con un título sacado del primer mensaje. */
    fun findConversations(userId: Long, limit: Int): List<ConversationSummary> = transaction {
        Conversations
            .selectAll()
            .where { Conversations.userId eq userId }
            .orderBy(Conversations.updatedAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                val conversationId = row[Conversations.id]

                val userMessages = Messages
                    .selectAll()
                    .where { (Messages.conversationId eq conversationId) and (Messages.role eq "user") }
                    .orderBy(Messages.id, SortOrder.ASC)
                    .map { it[Messages.content] }

                ConversationSummary(
                    id = conversationId,
                    title = row[Conversations.title]
                        ?: userMessages.firstOrNull()?.take(80)
                        ?: "Conversación sin mensajes",
                    messageCount = userMessages.size,
                    createdAt = row[Conversations.createdAt],
                    updatedAt = row[Conversations.updatedAt]
                )
            }
            .filter { it.messageCount > 0 }
    }

    /**
     * Mensajes de una conversación tal y como los vio el usuario: se quitan
     * las llamadas a herramientas (`FUNCTION_CALL`) y sus resultados (`function`).
     */
    fun findConversationMessages(conversationId: Long): List<ConversationMessage> = transaction {
        Messages
            .selectAll()
            .where { Messages.conversationId eq conversationId }
            .orderBy(Messages.id, SortOrder.ASC)
            .mapNotNull { row ->
                val role = row[Messages.role]
                val content = row[Messages.content]

                when {
                    role == "user" -> ConversationMessage("user", content, row[Messages.createdAt])
                    role == "model" && !content.startsWith("FUNCTION_CALL\n") ->
                        ConversationMessage("model", content, row[Messages.createdAt])
                    else -> null
                }
            }
    }

    private fun ResultRow.toMemoryRecord() = MemoryRecord(
        id = this[Memory.id],
        type = this[Memory.type],
        key = this[Memory.key],
        value = this[Memory.value],
        importance = this[Memory.importance],
        createdAt = this[Memory.createdAt],
        updatedAt = this[Memory.updatedAt]
    )
}
