package com.apache.agent

import com.apache.ai.GeminiClient
import com.apache.ai.GeminiResult
import com.apache.memory.MemoryService
import com.apache.security.PendingConfirmation
import com.apache.security.PendingConfirmationStore
import com.apache.security.PermissionDecision
import com.apache.security.PermissionManager
import com.apache.tools.ToolRegistry
import org.springframework.stereotype.Component

/**
 * Resultado que el Agent devuelve al controlador HTTP, para que este a su vez decida qué mostrarle
 * al usuario en la UI.
 */
sealed class AgentResult {

    /** Respuesta final en texto, lista para mostrar. */
    data class Reply(val text: String) : AgentResult()

    /** Hay una acción de riesgo esperando confirmación explícita del usuario. */
    data class NeedsConfirmation(
        val confirmationId: String,
        val warning: String
    ) : AgentResult()
}

private const val SYSTEM_INSTRUCTION =
    """
Eres Apache, un asistente de IA personal instalado en el ordenador del usuario.

Eres útil, directo y honesto. Cuando necesites realizar una acción sobre el
sistema (abrir una app, consultar información, etc.), usa las herramientas
disponibles en lugar de inventar la respuesta. Responde siempre en español.

Cuando el usuario solicite varias acciones, realiza todas las acciones necesarias
y no te limites únicamente a la primera.
"""

/**
 * Es el "Agent" del diagrama de arquitectura: el orquestador que conecta todas las piezas en cada
 * turno de conversación.
 *
 * Flujo de handleMessage():
 * 1. Recupera (o crea) la conversación y su historial en memoria.
 * 2. Añade el mensaje del usuario al historial y se lo manda a Gemini.
 * 3. Si Gemini responde con texto -> se guarda y se devuelve tal cual.
 * 4. Si Gemini responde con una o varias function calls:
 *    a. Se busca cada tool en el ToolRegistry.
 *    b. Se consulta al PermissionManager qué hacer según su RiskLevel.
 *    c. Si se puede ejecutar directo -> se ejecuta la acción.
 *    d. Si requiere confirmación -> se guarda la acción en
 *       PendingConfirmationStore y se pide al usuario que confirme.
 */
@Component
class Agent(

    private val geminiClient: GeminiClient,

    private val toolRegistry: ToolRegistry,

    private val permissionManager: PermissionManager,

    private val memoryService: MemoryService

) {

    /**
     * Apache 0.1 utiliza inicialmente el usuario `default`.
     *
     * Este usuario se creó durante la configuración inicial de MySQL
     * y actualmente corresponde al id 1.
     *
     * Más adelante este valor dejará de estar fijado cuando Apache
     * tenga gestión de usuarios y sesiones.
     */
    private val defaultUserId = 1L

    fun handleMessage(
        conversationId: Long?,
        userMessage: String
    ): Pair<Long, AgentResult> {

        val convId = memoryService.getOrCreateConversation(
            userId = defaultUserId,
            conversationId = conversationId
        )

        memoryService.appendMessage(
            convId,
            "user",
            userMessage
        )

        val result = runTurn(convId)

        return convId to result
    }

    /**
     * Ejecuta un turno de conversación con Gemini a partir del historial actual guardado en
     * memoria. Es una función separada de handleMessage porque se vuelve a invocar
     * recursivamente tras ejecutar una tool y también tras confirmar una acción pendiente.
     */
    private fun runTurn(conversationId: Long): AgentResult {

        val history = memoryService.getHistory(conversationId)

        return when (
            val geminiResult =
                geminiClient.sendMessage(
                    history,
                    SYSTEM_INSTRUCTION
                )
        ) {

            is GeminiResult.TextResponse -> {

                memoryService.appendMessage(
                    conversationId,
                    "model",
                    geminiResult.text
                )

                AgentResult.Reply(geminiResult.text)
            }

            is GeminiResult.FunctionCalls -> {

                handleFunctionCalls(
                    conversationId,
                    geminiResult.calls
                )
            }
        }
    }

    private fun handleFunctionCalls(
        conversationId: Long,
        calls: List<GeminiResult.FunctionCall>
    ): AgentResult {

        val outputs = mutableListOf<String>()

        for (call in calls) {

            val tool =
                toolRegistry.findByName(call.toolName)
                    ?: return AgentResult.Reply(
                        "Gemini pidió usar la herramienta '${call.toolName}', que no existe."
                    )

            memoryService.appendMessage(
                conversationId,
                "model",
                "FUNCTION_CALL\n${call.toolName}\n${call.args}\n${call.thoughtSignature ?: ""}\n${call.id ?: ""}"
            )

            when (permissionManager.decide(tool.riskLevel)) {

                PermissionDecision.EXECUTE_DIRECT -> {

                    val output = tool.execute(call.args)

                    memoryService.appendMessage(
                        conversationId,
                        "function",
                        "${tool.name}\n$output\n${call.id ?: ""}"
                    )

                    if (!tool.requiresGeminiResponse) {

                        outputs.add(output)

                    } else {

                        return runTurn(conversationId)
                    }
                }

                PermissionDecision.REQUIRES_CONFIRMATION -> {

                    val pending =
                        PendingConfirmationStore.add(
                            PendingConfirmation(
                                conversationId = conversationId,
                                toolName = tool.name,
                                args = call.args,
                                humanReadableWarning =
                                    "Esta acción (${tool.name}) puede tener efectos importantes. ¿Quieres que continúe?"
                            )
                        )

                    return AgentResult.NeedsConfirmation(
                        pending.id,
                        pending.humanReadableWarning
                    )
                }

                PermissionDecision.DENIED -> {

                    outputs.add(
                        "No tienes permiso para ejecutar la acción '${tool.name}'."
                    )
                }
            }
        }

        if (outputs.isNotEmpty()) {

            val finalOutput =
                outputs.joinToString("\n")

            memoryService.appendMessage(
                conversationId,
                "model",
                finalOutput
            )

            return AgentResult.Reply(finalOutput)
        }

        return runTurn(conversationId)
    }

    /**
     * Se llama cuando el usuario confirma (o rechaza) una acción pendiente desde la UI. Si
     * confirma, ejecuta la tool y continúa el turno como si se hubiera ejecutado directamente.
     */
    fun confirmPendingAction(
        confirmationId: String,
        approved: Boolean
    ): AgentResult {

        val pending =
            PendingConfirmationStore.take(confirmationId)
                ?: return AgentResult.Reply(
                    "Esa confirmación ya no es válida (puede que haya expirado)."
                )

        if (!approved) {

            memoryService.appendMessage(
                pending.conversationId,
                "function",
                "${pending.toolName}\nEl usuario canceló la acción."
            )

            return runTurn(pending.conversationId)
        }

        val tool =
            toolRegistry.findByName(pending.toolName)
                ?: return AgentResult.Reply(
                    "La herramienta '${pending.toolName}' ya no existe."
                )

        val output = tool.execute(pending.args)

        memoryService.appendMessage(
            pending.conversationId,
            "function",
            "${tool.name}\n$output"
        )

        return runTurn(pending.conversationId)
    }
}