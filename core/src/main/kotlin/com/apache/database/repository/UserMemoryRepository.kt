package com.apache.database.repository

import com.apache.database.tables.Conversations
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
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.springframework.stereotype.Repository

/**
 * Tabla `user_memory`: memoria permanente de Apache.
 *
 * No se usa la tabla `memory` del esquema inicial porque en las instalaciones
 * existentes tiene otras columnas. Esta tabla la crea DatabaseFactory al
 * arrancar si no existe. Las columnas evitan las palabras reservadas de
 * MySQL `key` y `value`.
 */
object UserMemories : Table("user_memory") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val type = varchar("type", 50)
    val key = varchar("memory_key", 255)
    val value = text("memory_value")
    val importance = double("importance")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

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
        UserMemories
            .selectAll()
            .where { UserMemories.userId eq userId }
            .orderBy(UserMemories.importance to SortOrder.DESC, UserMemories.updatedAt to SortOrder.DESC)
            .map { it.toMemoryRecord() }
    }

    fun findById(memoryId: Long): MemoryRecord? = transaction {
        UserMemories.selectAll().where { UserMemories.id eq memoryId }.firstOrNull()?.toMemoryRecord()
    }

    /** Busca un dato por su clave, sin distinguir mayúsculas (para no duplicar "nombre" y "Nombre"). */
    fun findByKey(userId: Long, key: String): MemoryRecord? = transaction {
        UserMemories
            .selectAll()
            .where { UserMemories.userId eq userId }
            .firstOrNull { it[UserMemories.key].equals(key, ignoreCase = true) }
            ?.toMemoryRecord()
    }

    fun create(userId: Long, type: String, key: String, value: String, importance: Double): Long = transaction {
        val now = LocalDateTime.now()

        UserMemories.insert {
            it[UserMemories.userId] = userId
            it[UserMemories.type] = type
            it[UserMemories.key] = key
            it[UserMemories.value] = value
            it[UserMemories.importance] = importance
            it[UserMemories.createdAt] = now
            it[UserMemories.updatedAt] = now
        } get UserMemories.id
    }

    fun update(memoryId: Long, type: String?, key: String?, value: String?, importance: Double?): Boolean = transaction {
        UserMemories.update({ UserMemories.id eq memoryId }) {
            if (type != null) it[UserMemories.type] = type
            if (key != null) it[UserMemories.key] = key
            if (value != null) it[UserMemories.value] = value
            if (importance != null) it[UserMemories.importance] = importance
            it[UserMemories.updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun delete(memoryId: Long): Boolean = transaction {
        UserMemories.deleteWhere { UserMemories.id eq memoryId } > 0
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
        id = this[UserMemories.id],
        type = this[UserMemories.type],
        key = this[UserMemories.key],
        value = this[UserMemories.value],
        importance = this[UserMemories.importance],
        createdAt = this[UserMemories.createdAt],
        updatedAt = this[UserMemories.updatedAt]
    )
}
