package com.apache.memory

import com.apache.ai.GeminiTurn
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

/**
 * Repositorio de memoria del agente: guarda y recupera el historial de
 * conversaciones. Es la capa que traduce entre las tablas de Exposed
 * (Tables.kt) y los tipos que usa el resto del agente (GeminiTurn).
 *
 * Todas las operaciones van envueltas en `transaction { }`, que es como
 * Exposed abre/cierra una transacción JDBC contra la base de datos
 * configurada en DatabaseFactory.
 */
@Repository
class MemoryRepository {

    /** Crea una conversación nueva con un id aleatorio si no se especifica una existente. */
    fun getOrCreateConversation(conversationId: String?): String = transaction {
        val id = conversationId ?: UUID.randomUUID().toString()
        val exists = Conversations.select { Conversations.id eq id }.count() > 0
        if (!exists) {
            Conversations.insert {
                it[Conversations.id] = id
                it[createdAt] = LocalDateTime.now()
            }
        }
        id
    }

    /** Añade un turno (mensaje) al historial de una conversación. */
    fun appendMessage(conversationId: String, role: String, content: String) = transaction {
        Messages.insert {
            it[Messages.conversationId] = conversationId
            it[Messages.role] = role
            it[Messages.content] = content
            it[createdAt] = LocalDateTime.now()
        }
        Unit
    }

    /**
     * Recupera el historial completo de una conversación, ordenado
     * cronológicamente, y lo convierte al formato que espera GeminiClient.
     */
    fun getHistory(conversationId: String): List<GeminiTurn> = transaction {
        Messages
            .select { Messages.conversationId eq conversationId }
            .orderBy(Messages.id, SortOrder.ASC)
            .map { GeminiTurn(role = it[Messages.role], text = it[Messages.content]) }
    }
}