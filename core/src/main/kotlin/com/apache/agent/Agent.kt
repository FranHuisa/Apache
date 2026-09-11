package com.apache.agent

import com.apache.ai.GeminiClient
import com.apache.ai.GeminiResult
import com.apache.memory.MemoryRepository
import com.apache.memory.UserMemoryRepository
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

private const val BASE_SYSTEM_INSTRUCTION =
    """
Eres Apache, un asistente de IA personal instalado en el ordenador del usuario.

Eres útil, directo y honesto. Cuando necesites realizar una acción sobre el
sistema (abrir una app, consultar información, etc.), usa las herramientas
disponibles en lugar de inventar la respuesta. Responde siempre en español.

Cuando el usuario solicite varias acciones, realiza todas las acciones necesarias
y no te limites únicamente a la primera.

Tienes memoria a largo plazo sobre el usuario (ver más abajo, si hay algo guardado).
Utilízala con naturalidad, como lo haría alguien que conoce a la persona con la que
habla, sin repetir literalmente lo que sabes de ella salvo que venga a cuento.
Cuando el usuario te cuente algo sobre sí mismo que tenga sentido recordar en el
futuro (su nombre, gustos, preferencias, rutinas...), usa la herramienta
rememberFact para guardarlo. Si te pide olvidar algo que le habías guardado, usa
forgetFact.
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
    private val memoryRepository: MemoryRepository,
    private val userMemoryRepository: UserMemoryRepository
) {

    /**
     * Construye el system instruction de este turno: la parte fija de Apache más, si hay algo
     * guardado, un bloque con los recuerdos a largo plazo sobre el usuario (ver
     * [UserMemoryRepository]). Se reconstruye en cada turno (no se cachea) porque rememberFact/
     * forgetFact pueden cambiar la lista en mitad de una conversación.
     */
    private fun buildSystemInstruction(): String {
        val memories = userMemoryRepository.getAll()

        if (memories.isEmpty()) {
            return BASE_SYSTEM_INSTRUCTION
        }

        val memoriesBlock = memories.joinToString("\n") { "- ${it.content}" }

        return BASE_SYSTEM_INSTRUCTION + "\n\nDatos que recuerdas sobre el usuario:\n$memoriesBlock\n"
    }

    fun handleMessage(
        conversationId: String?,
        userMessage: String
    ): Pair<String, AgentResult> {

        val convId = memoryRepository.getOrCreateConversation(conversationId)

        memoryRepository.appendMessage(
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
    private fun runTurn(conversationId: String): AgentResult {

        val history = memoryRepository.getHistory(conversationId)

        return when (
            val geminiResult =
                geminiClient.sendMessage(
                    history,
                    buildSystemInstruction()
                )
        ) {

            is GeminiResult.TextResponse -> {

                memoryRepository.appendMessage(
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
        conversationId: String,
        calls: List<GeminiResult.FunctionCall>
    ): AgentResult {

        val outputs = mutableListOf<String>()

        for (call in calls) {

            val tool =
                toolRegistry.findByName(call.toolName)
                    ?: return AgentResult.Reply(
                        "Gemini pidió usar la herramienta '${call.toolName}', que no existe."
                    )

            memoryRepository.appendMessage(
                conversationId,
                "model",
                "FUNCTION_CALL\n${call.toolName}\n${call.args}\n${call.thoughtSignature ?: ""}\n${call.id ?: ""}"
            )

            when (permissionManager.decide(tool.riskLevel)) {

                PermissionDecision.EXECUTE_DIRECT -> {

                    val output = tool.execute(call.args)

                    memoryRepository.appendMessage(
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

            memoryRepository.appendMessage(
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

            memoryRepository.appendMessage(
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

        memoryRepository.appendMessage(
            pending.conversationId,
            "function",
            "${tool.name}\n$output"
        )

        return runTurn(pending.conversationId)
    }
}