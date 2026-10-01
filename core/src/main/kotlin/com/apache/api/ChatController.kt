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
    fun chat(@RequestBody request: ChatRequest): ChatResponse {

        val (conversationId, result) =
            agent.handleMessage(
                request.conversationId,
                request.message,
                request.attachments.map { GeminiAttachment(it.name, it.mimeType, it.data) },
                request.fromVoice
            )

        return toResponse(conversationId, result)
    }

    @PostMapping("/confirm")
    fun confirm(@RequestBody request: ConfirmRequest): ChatResponse {
        val result = agent.confirmPendingAction(request.confirmationId, request.approved)
        // conversationId no viaja en la respuesta de confirm porque el cliente
        // ya lo conoce de la llamada anterior.
        return toResponse(conversationId = null, result = result)
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