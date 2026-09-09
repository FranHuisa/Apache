package com.apache.agent

import com.apache.ai.GeminiClient
import com.apache.ai.GeminiResult
import com.apache.memory.MemoryRepository
import com.apache.security.PendingConfirmation
import com.apache.security.PendingConfirmationStore
import com.apache.security.PermissionDecision
import com.apache.security.PermissionManager
import com.apache.tools.ToolRegistry
import org.springframework.stereotype.Component

/**
 * Resultado que el Agent devuelve al controlador HTTP, para que este a su
 * vez decida qué mostrarle al usuario en la UI.
 */
sealed class AgentResult {
    /** Respuesta final en texto, lista para mostrar. */
    data class Reply(val text: String) : AgentResult()

    /** Hay una acción de riesgo esperando confirmación explícita del usuario. */
    data class NeedsConfirmation(val confirmationId: String, val warning: String) : AgentResult()
}

private const val SYSTEM_INSTRUCTION = """
Eres Apache, un asistente de IA personal instalado en el ordenador del usuario.
Eres útil, directo y honesto. Cuando necesites realizar una acción sobre el
sistema (abrir una app, consultar información, etc.), usa las herramientas
disponibles en lugar de inventar la respuesta. Responde siempre en español.
"""

/**
 * Es el "Agent" del diagrama de arquitectura: el orquestador que conecta
 * todas las piezas en cada turno de conversación.
 *
 * Flujo de handleMessage():
 *  1. Recupera (o crea) la conversación y su historial en memoria.
 *  2. Añade el mensaje del usuario al historial y se lo manda a Gemini.
 *  3. Si Gemini responde con texto -> se guarda y se devuelve tal cual.
 *  4. Si Gemini responde con una function call:
 *     a. Se busca la tool en el ToolRegistry.
 *     b. Se consulta al PermissionManager qué hacer según su RiskLevel.
 *     c. Si se puede ejecutar directo -> se ejecuta, se manda el resultado
 *        de vuelta a Gemini para que redacte la respuesta final al usuario.
 *     d. Si requiere confirmación -> se guarda la acción en
 *        PendingConfirmationStore y se le pide al usuario que confirme,
 *        SIN ejecutar nada todavía.
 */
@Component
class Agent(
    private val geminiClient: GeminiClient,
    private val toolRegistry: ToolRegistry,
    private val permissionManager: PermissionManager,
    private val memoryRepository: MemoryRepository
) {

    fun handleMessage(conversationId: String?, userMessage: String): Pair<String, AgentResult> {
        val convId = memoryRepository.getOrCreateConversation(conversationId)
        memoryRepository.appendMessage(convId, "user", userMessage)

        val result = runTurn(convId)
        return convId to result
    }

    /**
     * Ejecuta un turno de conversación con Gemini a partir del historial
     * actual guardado en memoria. Es una función separada de handleMessage
     * porque se vuelve a invocar recursivamente tras ejecutar una tool
     * (Gemini necesita "ver" el resultado de la función para redactar la
     * respuesta final) y también tras confirmar una acción pendiente.
     */
    private fun runTurn(conversationId: String): AgentResult {
        val history = memoryRepository.getHistory(conversationId)

        return when (val geminiResult = geminiClient.sendMessage(history, SYSTEM_INSTRUCTION)) {
            is GeminiResult.TextResponse -> {
                memoryRepository.appendMessage(conversationId, "model", geminiResult.text)
                AgentResult.Reply(geminiResult.text)
            }

            is GeminiResult.FunctionCall -> handleFunctionCall(conversationId, geminiResult)
        }
    }

    private fun handleFunctionCall(conversationId: String, call: GeminiResult.FunctionCall): AgentResult {
        val tool = toolRegistry.findByName(call.toolName)
            ?: return AgentResult.Reply("Gemini pidió usar la herramienta '${call.toolName}', que no existe.")

        return when (permissionManager.decide(tool.riskLevel)) {
            PermissionDecision.EXECUTE_DIRECT -> {
                val output = tool.execute(call.args)
                // El resultado de la función se guarda como un turno "function" en el
                // historial, para que en la siguiente llamada Gemini pueda usarlo y
                // redactar la respuesta definitiva en lenguaje natural.
                memoryRepository.appendMessage(conversationId, "function", output)
                runTurn(conversationId)
            }

            PermissionDecision.REQUIRES_CONFIRMATION -> {
                val pending = PendingConfirmationStore.add(
                    PendingConfirmation(
                        conversationId = conversationId,
                        toolName = tool.name,
                        args = call.args,
                        humanReadableWarning = "Esta acción (${tool.name}) puede tener efectos importantes. ¿Quieres que continúe?"
                    )
                )
                AgentResult.NeedsConfirmation(pending.id, pending.humanReadableWarning)
            }

            PermissionDecision.DENIED ->
                AgentResult.Reply("No tienes permiso para ejecutar la acción '${tool.name}'.")
        }
    }

    /**
     * Se llama cuando el usuario confirma (o rechaza) una acción pendiente
     * desde la UI. Si confirma, ejecuta la tool y continúa el turno como si
     * se hubiera ejecutado directamente.
     */
    fun confirmPendingAction(confirmationId: String, approved: Boolean): AgentResult {
        val pending = PendingConfirmationStore.take(confirmationId)
            ?: return AgentResult.Reply("Esa confirmación ya no es válida (puede que haya expirado).")

        if (!approved) {
            memoryRepository.appendMessage(pending.conversationId, "function", "El usuario canceló la acción.")
            return runTurn(pending.conversationId)
        }

        val tool = toolRegistry.findByName(pending.toolName)
            ?: return AgentResult.Reply("La herramienta '${pending.toolName}' ya no existe.")

        val output = tool.execute(pending.args)
        memoryRepository.appendMessage(pending.conversationId, "function", output)
        return runTurn(pending.conversationId)
    }
}