package com.apache.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.model.ChatMessage
import com.apache.model.ChatResponse
import com.apache.model.ConversationMessageDto
import com.apache.network.confirmActionInCore
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
private const val DAILY_SUMMARY_PROMPT =
    "(Mensaje automático al abrir Apache por primera vez hoy, no lo menciones.) " +
        "Dame mi resumen de hoy: salúdame por mi nombre si lo sabes, dime qué tengo hoy en el " +
        "calendario y el horario, mis recordatorios y tareas pendientes, y el tiempo de hoy si " +
        "sabes en qué ciudad estoy. Usa las herramientas que necesites. Sé breve: una lista corta " +
        "y, si no tengo nada, dilo en una frase."

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

    /**
     * Muestra una respuesta del Core. Si la acción necesita confirmación, el
     * mensaje lleva su confirmationId para que la burbuja dibuje los botones.
     */
    private fun appendResponse(response: ChatResponse, reply: String) {
        if (response.needsConfirmation && response.confirmationId != null) {
            appendMessage(ChatMessage(reply, false, confirmationId = response.confirmationId))
        } else {
            appendMessage(ChatMessage(reply, false, response.images))
        }
    }

    /**
     * Responde a una acción pendiente (botones Sí / No del chat). Los botones
     * desaparecen y se muestra lo que haya respondido Apache tras ejecutarla
     * o cancelarla.
     */
    fun confirm(confirmationId: String, approved: Boolean) {
        if (isLoading) return

        // Quitamos los botones del mensaje y dejamos constancia de la decisión.
        messages = messages.map { message ->
            if (message.confirmationId == confirmationId) {
                message.copy(
                    text = message.text + if (approved) "\n\n✔ Confirmado" else "\n\n✖ Cancelado",
                    confirmationId = null
                )
            } else {
                message
            }
        }

        scope.launch {
            isLoading = true
            try {
                val response = withContext(Dispatchers.IO) { confirmActionInCore(confirmationId, approved) }
                val reply = response.reply ?: response.warning ?: "Hecho."
                appendResponse(response, reply)
            } catch (e: Exception) {
                appendMessage(ChatMessage("No se ha podido enviar la confirmación: ${e.message}", false))
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Resumen del día: la primera vez que se abre Apache cada día, le pide a
     * Apache un resumen (horario, recordatorios, tareas y tiempo). La petición
     * no se muestra en el chat, solo la respuesta.
     */
    suspend fun showDailySummaryIfNeeded() {
        if (!SessionStore.shouldShowDailySummary() || isLoading) return

        val reply = runTurn(DAILY_SUMMARY_PROMPT, speak = false, showUserMessage = false)

        // Si el Core no respondía, se volverá a intentar en el próximo arranque.
        if (reply != null) SessionStore.markDailySummaryShown()
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
        showUserMessage: Boolean = true,
        // Debe ser el último parámetro: el modo voz lo pasa como lambda final.
        onReply: (suspend (String) -> Unit)? = null
    ): String? {
        // Los mensajes automáticos (p. ej. el resumen del día) no se muestran como si
        // los hubiera escrito el usuario.
        if (showUserMessage) appendMessage(ChatMessage(text, true))
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
            appendResponse(response, reply)

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
