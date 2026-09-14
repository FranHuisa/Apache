package com.apache.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.model.ChatMessage
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
     */
    suspend fun runTurn(text: String, speak: Boolean, onReply: (suspend (String) -> Unit)? = null) {
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
            appendMessage(ChatMessage(reply, false))

            if (speak) {
                onReply?.invoke(reply)
            }
        } catch (e: Exception) {
            appendMessage(ChatMessage("No puedo conectar con Apache Core: ${e.message}", false))
        } finally {
            thinkingJob.cancel()
            isLoading = false
            showThinking = false
        }
    }
}
