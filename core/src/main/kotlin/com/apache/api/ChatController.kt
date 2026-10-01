package com.apache.api

import com.apache.agent.Agent
import com.apache.agent.AgentResult
import com.apache.ai.GeminiAttachment
import com.apache.api.dto.ChatRequest
import com.apache.api.dto.ChatImageDto
import com.apache.api.dto.ChatResponse
import com.apache.api.dto.ConfirmRequest
import org.springframework.web.bind.annotation.*

/**
 * Única puerta de entrada HTTP entre la app de escritorio (o, en el futuro,
 * la app Android) y el core. Deliberadamente muy fina: toda la lógica vive
 * en Agent; este controlador solo traduce entre DTOs de red y AgentResult.
 *
 * Endpoints:
 *  POST /api/chat         -> enviar un mensaje nuevo del usuario
 *  POST /api/chat/confirm -> confirmar o rechazar una acción pendiente
 */
@RestController
@RequestMapping("/api/chat")
class ChatController(private val agent: Agent) {

    @PostMapping
    fun chat(@RequestBody request: ChatRequest): ChatResponse =
        try {
            val (conversationId, result) =
                agent.handleMessage(
                    request.conversationId,
                    request.message,
                    request.attachments.map { GeminiAttachment(it.name, it.mimeType, it.data) },
                    request.fromVoice
                )

            toResponse(conversationId, result)
        } catch (e: Exception) {
            // En vez de un error 500 (que el Desktop mostraba como "No puedo conectar..."),
            // se devuelve un mensaje claro. La conversación se mantiene.
            println("Error en el turno de chat: ${e.javaClass.simpleName}: ${e.message}")
            ChatResponse(conversationId = request.conversationId, reply = friendlyError(e))
        }

    @PostMapping("/confirm")
    fun confirm(@RequestBody request: ConfirmRequest): ChatResponse {
        val result = try {
            agent.confirmPendingAction(request.confirmationId, request.approved)
        } catch (e: Exception) {
            return ChatResponse(reply = friendlyError(e))
        }
        // conversationId no viaja en la respuesta de confirm porque el cliente
        // ya lo conoce de la llamada anterior.
        return toResponse(conversationId = null, result = result)
    }

    /** Mensaje para el usuario según el tipo de fallo (Gemini lento, saturado u otro). */
    private fun friendlyError(e: Exception): String {
        val text = (e.message ?: "").lowercase()
        return when {
            e is java.io.InterruptedIOException || "timeout" in text ->
                "Gemini está tardando demasiado en responder ahora mismo. Inténtalo de nuevo en un momento."
            "503" in text || "429" in text || "high demand" in text ->
                "Gemini está saturado ahora mismo. Inténtalo de nuevo en unos segundos."
            else -> "Algo ha fallado al procesar tu mensaje: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun toResponse(conversationId: Long?, result: AgentResult): ChatResponse = when (result) {
        is AgentResult.Reply -> ChatResponse(
            conversationId = conversationId,
            reply = result.text,
            images = result.images.map { ChatImageDto(it.url, it.title, it.sourceUrl) }
        )
        is AgentResult.NeedsConfirmation -> ChatResponse(
            conversationId = conversationId,
            needsConfirmation = true,
            confirmationId = result.confirmationId,
            warning = result.warning
        )
    }
}