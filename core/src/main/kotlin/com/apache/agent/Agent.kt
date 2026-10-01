package com.apache.agent

import com.apache.ai.GeminiClient
import com.apache.ai.GeminiResult
import com.apache.database.service.UserMemoryService
import com.apache.memory.MemoryService
import com.apache.security.PendingConfirmation
import com.apache.security.PendingConfirmationStore
import com.apache.security.PermissionDecision
import com.apache.security.PermissionManager
import com.apache.tools.ToolRegistry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.stereotype.Component

/**
 * Resultado que el Agent devuelve al controlador HTTP, para que este a su vez decida qué mostrarle
 * al usuario en la UI.
 */
sealed class AgentResult {

    /**
     * Respuesta final en texto, lista para mostrar, con las imágenes (0-3)
     * que alguna tool haya encontrado durante el turno.
     */
    data class Reply(
        val text: String,
        val images: List<ChatImage> = emptyList()
    ) : AgentResult()

    /** Hay una acción de riesgo esperando confirmación explícita del usuario. */
    data class NeedsConfirmation(
        val confirmationId: String,
        val warning: String
    ) : AgentResult()
}

/** Máximo de imágenes que Apache muestra en una misma respuesta. */
private const val MAX_IMAGES_PER_REPLY = 3

private const val SYSTEM_INSTRUCTION =
    """
Eres Apache, un asistente de IA personal instalado en el ordenador del usuario.

Eres útil, directo y honesto. Cuando necesites realizar una acción sobre el
sistema (abrir una app, consultar información, etc.), usa las herramientas
disponibles en lugar de inventar la respuesta. Responde siempre en español.

Cuando el usuario solicite varias acciones, realiza todas las acciones necesarias
y no te limites únicamente a la primera.

Puedes enseñar imágenes en el chat con la herramienta searchImages. Úsala cuando
el usuario quiera ver algo o cuando una imagen ayude de verdad a la respuesta.
Muestra 1 imagen para una cosa concreta y 2 o 3 cuando pida varias, una
comparación o variedad; nunca más de 3. Las imágenes aparecen solas debajo de tu
mensaje: no escribas sus enlaces en el texto.

Si el usuario pide poner, buscar o reproducir una canción, artista o álbum
concreto, usa playSong (en YouTube salvo que pida Spotify). Para pausar, pasar
de canción o cambiar el volumen de lo que ya suena, usa musicControl.

Si te preguntan algo actual (noticias, resultados, precios, estrenos, horarios)
o algo que no sepas con seguridad, búscalo con webSearch en vez de inventarlo.

Tienes memoria permanente. Cuando el usuario diga "recuerda que..." o te cuente
algo personal que seguirá siendo cierto (su nombre, gustos, alergias, rutinas,
trabajo, personas importantes, cómo prefiere que le hables), guárdalo con
rememberFact sin pedir permiso y díselo en una frase corta. Si te pide que
olvides algo, usa forgetFact. Usa lo que sabes del usuario con naturalidad, sin
recitarlo.

Cuando el usuario quiera organizar su día o hacer un horario, consulta primero
lo que ya tiene ese día con getCalendarEvents y después crea todos los bloques de
una vez con planDaySchedule. Si te falta información importante (a qué hora
empieza o termina el día, cuánto dura cada cosa), propón tú unos horarios
razonables en vez de preguntar demasiado, y al final resume el horario en una
lista breve.
"""

private val CURRENT_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-ES"))

/**
 * Instrucción de sistema de cada turno: la fija más la fecha y hora actuales,
 * para que Gemini sepa qué día es "hoy" o "mañana" al organizar horarios
 * sin tener que llamar antes a getCurrentTime.
 */
private fun systemInstructionForNow(userMemory: String): String =
    SYSTEM_INSTRUCTION +
        "\nFecha y hora actual del usuario: ${LocalDateTime.now().format(CURRENT_TIME_FORMAT)} " +
        "(${LocalDate.now()})." +
        if (userMemory.isBlank()) {
            "\nTodavía no has guardado ningún dato del usuario en tu memoria."
        } else {
            "\n\nLo que recuerdas del usuario (memoria permanente):\n$userMemory"
        }

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

    private val memoryService: MemoryService,

    private val userMemoryService: UserMemoryService

) {

    /**
     * Datos de la memoria permanente para la instrucción de sistema. Si MySQL
     * falla aquí, Apache sigue respondiendo (solo que sin esos datos).
     */
    private fun loadUserMemory(): String =
        try {
            userMemoryService.contextForPrompt(defaultUserId)
        } catch (e: Exception) {
            println("No se ha podido leer la memoria del usuario: ${e.message}")
            ""
        }

    /**
     * Apache 0.1 utiliza inicialmente el usuario `default`.
     *
     * Ver [com.apache.ApacheDefaults.DEFAULT_USER_ID].
     */
    private val defaultUserId = com.apache.ApacheDefaults.DEFAULT_USER_ID

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

        val result = runTurn(convId, mutableListOf())

        return convId to result
    }

    /**
     * Ejecuta un turno de conversación con Gemini a partir del historial actual guardado en
     * memoria. Es una función separada de handleMessage porque se vuelve a invocar
     * recursivamente tras ejecutar una tool y también tras confirmar una acción pendiente.
     */
    private fun runTurn(
        conversationId: Long,
        images: MutableList<ChatImage>
    ): AgentResult {

        val history = memoryService.getHistory(conversationId)

        return when (
            val geminiResult =
                geminiClient.sendMessage(
                    history,
                    systemInstructionForNow(loadUserMemory())
                )
        ) {

            is GeminiResult.TextResponse -> {

                memoryService.appendMessage(
                    conversationId,
                    "model",
                    geminiResult.text
                )

                AgentResult.Reply(geminiResult.text, images.toList())
            }

            is GeminiResult.FunctionCalls -> {

                handleFunctionCalls(
                    conversationId,
                    geminiResult.calls,
                    images
                )
            }
        }
    }

    private fun handleFunctionCalls(
        conversationId: Long,
        calls: List<GeminiResult.FunctionCall>,
        images: MutableList<ChatImage>
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

                    collectImages(output, images)

                    memoryService.appendMessage(
                        conversationId,
                        "function",
                        "${tool.name}\n$output\n${call.id ?: ""}"
                    )

                    if (!tool.requiresGeminiResponse) {

                        outputs.add(output)

                    } else {

                        return runTurn(conversationId, images)
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
                                    "Esta acción (${tool.name}) puede tener efectos importantes. ¿Quieres que continúe?",
                                callId = call.id
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

            return AgentResult.Reply(finalOutput, images.toList())
        }

        return runTurn(conversationId, images)
    }

    /**
     * Añade a [images] las imágenes que la tool haya devuelto en su resultado
     * (líneas `IMAGE|...`, ver [ChatImage]), sin repetir y con un máximo de 3
     * por respuesta.
     */
    private fun collectImages(output: String, images: MutableList<ChatImage>) {
        ChatImage.parseFromToolOutput(output).forEach { image ->
            if (images.size < MAX_IMAGES_PER_REPLY && images.none { it.url == image.url }) {
                images.add(image)
            }
        }
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
                // Formato "nombre\nresultado\nid": sin el último salto de línea,
                // GeminiClient no sabría separar el resultado del id y lo perdería.
                "${pending.toolName}\nEl usuario canceló la acción.\n${pending.callId ?: ""}"
            )

            return runTurn(pending.conversationId, mutableListOf())
        }

        val tool =
            toolRegistry.findByName(pending.toolName)
                ?: return AgentResult.Reply(
                    "La herramienta '${pending.toolName}' ya no existe."
                )

        val output = tool.execute(pending.args)

        val images = mutableListOf<ChatImage>()
        collectImages(output, images)

        memoryService.appendMessage(
            pending.conversationId,
            "function",
            "${tool.name}\n$output\n${pending.callId ?: ""}"
        )

        return runTurn(pending.conversationId, images)
    }
}