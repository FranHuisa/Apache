package com.apache.memory

import com.apache.ai.GeminiTurn
import org.springframework.stereotype.Service

/**
 * Capa de servicio encargada de gestionar la memoria conversacional
 * de Apache.
 *
 * El Service contiene la lógica de negocio y utiliza MemoryRepository
 * para acceder a la base de datos.
 *
 * Apache no debería acceder directamente al Repository desde el Agent.
 */
@Service
class MemoryService(
    private val memoryRepository: MemoryRepository
) {

    /**
     * Obtiene una conversación existente o crea una nueva.
     *
     * Si conversationId es null, se genera automáticamente
     * un identificador para la nueva conversación.
     */
    fun getOrCreateConversation(
        userId: Long,
        conversationId: Long?
    ): Long {

        require(userId > 0) {
            "El identificador del usuario no es válido."
        }

        return memoryRepository.getOrCreateConversation(
            userId = userId,
            conversationId = conversationId
        )
    }

    /**
     * Añade un mensaje al historial de una conversación.
     */
    fun appendMessage(
        conversationId: Long,
        role: String,
        content: String
    ) {

        require(conversationId > 0) {
            "El identificador de la conversación no es válido."
        }

        require(role.isNotBlank()) {
            "El rol del mensaje no puede estar vacío."
        }

        require(content.isNotBlank()) {
            "El contenido del mensaje no puede estar vacío."
        }

        memoryRepository.appendMessage(
            conversationId = conversationId,
            role = role,
            content = content
        )
    }

    /**
     * Recupera el historial completo de una conversación.
     */
    fun getHistory(conversationId: Long): List<GeminiTurn> {

        require(conversationId > 0) {
            "El identificador de la conversación no es válido."
        }

        return memoryRepository.getHistory(conversationId)
    }
}