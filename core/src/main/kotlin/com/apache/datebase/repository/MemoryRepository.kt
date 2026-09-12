package com.apache.memory

import com.apache.ai.GeminiTurn
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Repositorio de memoria conversacional del agente.
 *
 * Esta clase contiene únicamente las operaciones relacionadas
 * con la persistencia de conversaciones y mensajes.
 *
 * Todas las operaciones van envueltas en `transaction { }`, que es como
 * Exposed abre y cierra una transacción JDBC contra la base de datos
 * configurada en DatabaseFactory.
 */
@Repository
class MemoryRepository {

    /**
     * Crea una conversación nueva para el usuario.
     *
     * El identificador de la conversación lo genera automáticamente MySQL.
     */
    fun createConversation(userId: Long): Long = transaction {

        val now = LocalDateTime.now()

        Conversations.insert {
            it[Conversations.userId] = userId
            it[Conversations.createdAt] = now
            it[Conversations.updatedAt] = now
        } get Conversations.id
    }

    /**
     * Comprueba si una conversación existe y pertenece al usuario indicado.
     *
     * Se busca primero por el identificador y después se comprueba
     * que el usuario asociado sea el correcto.
     */
    fun conversationExists(
        userId: Long,
        conversationId: Long
    ): Boolean = transaction {

        Conversations
            .select { Conversations.id eq conversationId }
            .any {
                it[Conversations.userId] == userId
            }
    }

    /**
     * Obtiene una conversación existente o crea una nueva.
     *
     * Si conversationId es null, se genera automáticamente
     * una nueva conversación.
     */
    fun getOrCreateConversation(
        userId: Long,
        conversationId: Long?
    ): Long {

        if (
            conversationId != null &&
            conversationExists(userId, conversationId)
        ) {
            return conversationId
        }

        return createConversation(userId)
    }

    /**
     * Añade un turno al historial de una conversación.
     */
    fun appendMessage(
        conversationId: Long,
        role: String,
        content: String
    ) = transaction {

        val now = LocalDateTime.now()

        Messages.insert {
            it[Messages.conversationId] = conversationId
            it[Messages.role] = role
            it[Messages.content] = content
            it[Messages.createdAt] = now
            it[Messages.updatedAt] = now
        }

        /*
         * Actualizamos la fecha de modificación de la conversación
         * cada vez que recibe un nuevo mensaje.
         */
        Conversations.update(
            { Conversations.id eq conversationId }
        ) {
            it[Conversations.updatedAt] = now
        }

        Unit
    }

    /**
     * Recupera el historial completo de una conversación, ordenado
     * cronológicamente, y lo convierte al formato que espera GeminiClient.
     */
    fun getHistory(
        conversationId: Long
    ): List<GeminiTurn> = transaction {

        Messages
            .select { Messages.conversationId eq conversationId }
            .orderBy(Messages.id, SortOrder.ASC)
            .map {
                GeminiTurn(
                    role = it[Messages.role],
                    text = it[Messages.content]
                )
            }
    }
}