package com.apache.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.model.ChatMessage
import com.apache.model.ConversationMessageDto
import com.apache.network.sendMessageToCore
import com.apache.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controla el estado y la lógica de la conversación con Apache: mensajes,
 * `conversationId` persistido, y el envío/recepción de turnos al Core.
 *
 * La usan tanto el chat de texto como el modo voz, para que ambos compartan
 * el mismo historial y el mismo `conversationId` (antes esto estaba duplicado
 * entre `processMessage` y `runVoiceTurn`).
 */
class ChatController(private val scope: CoroutineScope) {

    var messages by mutableStateOf(
        listOf(ChatMessage("Hola. Soy Apache. ¿En qué puedo ayudarte?", false))
    )
        private set

    var conversationId by mutableStateOf(SessionStore.loadConversationId())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showThinking by mutableStateOf(false)
        private set

    private fun appendMessage(message: ChatMessage) {
        messages = messages + message
    }

    /**
     * Retoma una conversación pasada (desde la sección Memoria): el chat
     * muestra sus mensajes y los siguientes se añaden a esa conversación.
     */
    fun openConversation(id: Long, history: List<ConversationMessageDto>) {
        if (isLoading) return

        conversationId = id
        SessionStore.saveConversationId(id)
        messages = listOf(ChatMessage("Conversación retomada. Sigue donde lo dejaste.", false)) +
            history.map { ChatMessage(it.text, it.role == "user") }
    }

    /** Empieza una conversación nueva (Apache sigue recordando la memoria permanente). */
    fun startNewConversation() {
        if (isLoading) return

        conversationId = null
        SessionStore.clearConversationId()
        messages = listOf(ChatMessage("Nueva conversación. ¿En qué puedo ayudarte?", false))
    }

    /** Añade un mensaje "de Apache" sin pasar por el Core (avisos del modo escucha, errores locales...). */
    fun appendSystemMessage(text: String) {
        appendMessage(ChatMessage(text, false))
    }

    /**
     * Envía un mensaje de texto normal (botón "Enviar" o Enter del campo de entrada).
     * No reproduce la respuesta por voz.
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || isLoading) return
        scope.launch { runTurn(text, speak = false) }
    }

    /**
     * Ejecuta un turno completo de conversación: añade el mensaje del usuario,
     * llama al Core, muestra la respuesta y, si [speak] es true, invoca [onReply]
     * con el texto para que quien la llame decida cómo reproducirla (TTS).
     *
     * Es una función suspend para poder encadenarse directamente desde el modo
     * escucha (voz) sin duplicar la lógica de red.
     *
     * Devuelve el texto de la respuesta (o null si no se pudo hablar con el
     * Core), para que otras pantallas como Horario puedan mostrarlo.
     */
    suspend fun runTurn(
        text: String,
        speak: Boolean,
        onReply: (suspend (String) -> Unit)? = null
    ): String? {
        appendMessage(ChatMessage(text, true))
        isLoading = true
        showThinking = false

        val thinkingJob = scope.launch {
            delay(400)
            if (isLoading) showThinking = true
        }

        try {
            val response = withContext(Dispatchers.IO) { sendMessageToCore(conversationId, text) }
            val reply = response.reply ?: response.warning ?: "Apache no devolvió una respuesta."

            conversationId = response.conversationId
            SessionStore.saveConversationId(response.conversationId)
            appendMessage(ChatMessage(reply, false, response.images))

            if (speak) {
                onReply?.invoke(reply)
            }

            return reply
        } catch (e: Exception) {
            appendMessage(ChatMessage("No puedo conectar con Apache Core: ${e.message}", false))
            return null
        } finally {
            thinkingJob.cancel()
            isLoading = false
            showThinking = false
        }
    }
}
