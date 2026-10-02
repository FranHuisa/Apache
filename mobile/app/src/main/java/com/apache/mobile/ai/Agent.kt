package com.apache.mobile.ai

import com.apache.mobile.data.ChatImage
import com.apache.mobile.data.ConversationStore
import com.apache.mobile.data.MemoryStore
import com.apache.mobile.data.Settings
import com.apache.mobile.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Archivo adjunto a un mensaje (foto de la galería, PDF...), en Base64. */
data class Attachment(val name: String, val mimeType: String, val base64: String)

/** Resultado de un turno: texto, imágenes (0-3) y la conversación en la que se guardó. */
data class AgentReply(val text: String, val images: List<ChatImage>, val conversationId: Long)

/**
 * Orquestador de Apache Móvil: el equivalente al Agent del Core de escritorio.
 *
 * En cada turno:
 *  1. Guarda el mensaje del usuario y manda el historial a Gemini con el
 *     catálogo de herramientas.
 *  2. Si Gemini pide herramientas, las ejecuta TODAS y le devuelve todas las
 *     respuestas juntas (como pide la API), y repite.
 *  3. Cuando Gemini responde con texto, lo guarda y lo devuelve.
 *
 * Todo el historial se guarda con el `content` exacto de Gemini (incluidas las
 * firmas de las llamadas a herramientas), así nunca se "rompe" la conversación.
 */
class Agent(
    private val gemini: GeminiClient,
    private val tools: ToolRegistry,
    private val conversations: ConversationStore,
    private val memory: MemoryStore,
    private val settings: Settings
) {

    suspend fun send(
        conversationId: Long?,
        text: String,
        attachments: List<Attachment> = emptyList(),
        fromVoice: Boolean = false,
        hideUserMessage: Boolean = false
    ): AgentReply = withContext(Dispatchers.IO) {

        val convId = conversationId?.takeIf { conversations.exists(it) } ?: conversations.create()

        val history = trimHistory(conversations.geminiContents(convId))

        // Lo que se envía a Gemini lleva los adjuntos; lo que se guarda, solo su nombre.
        val userText = text.ifBlank { "¿Qué ves en esto?" }
        // Si es la frase de una rutina, a Gemini le llegan sus pasos (al chat, lo que dijo).
        val routine = if (attachments.isEmpty()) com.apache.mobile.ApacheApp.get().routines.match(text) else null
        val geminiText = if (routine == null) userText else {
            com.apache.mobile.ApacheApp.get().routines.markRun(routine.id)
            "$userText\n\n(Es mi rutina «${routine.trigger}». Haz todos estos pasos con las herramientas, en orden, " +
                "y al final resúmelo en una o dos frases:\n" +
                routine.steps.mapIndexed { i, step -> "${i + 1}. $step" }.joinToString("\n") + ")"
        }
        val userContent = userContent(geminiText, attachments)
        val storedText = if (attachments.isEmpty()) userText else "$userText\n[Adjuntos: ${attachments.joinToString { it.name }}]"
        val storedContent = userContent(storedText, emptyList())

        conversations.addMessage(
            convId,
            storedContent,
            displayText = when {
                hideUserMessage -> null
                attachments.isEmpty() -> userText
                else -> "$userText\n📎 ${attachments.joinToString { it.name }}"
            }
        )

        val working = JSONArray()
        history.forEach { working.put(it) }
        working.put(userContent)

        val images = mutableListOf<ChatImage>()
        val app = com.apache.mobile.ApacheApp.get()
        val routineList = app.routines.all()
        val instruction = Prompt.systemInstruction(
            memory.contextForPrompt(), fromVoice,
            routineList.joinToString("\n") { r -> "- «${r.trigger}» → ${r.steps.joinToString("; ")}" }
        )

        repeat(MAX_STEPS) {
            val modelContent = gemini.generate(working, instruction, tools.declarations())
            working.put(modelContent)

            val calls = functionCalls(modelContent)

            if (calls.isEmpty()) {
                val reply = textOf(modelContent).ifBlank { "Hecho." }
                conversations.addMessage(convId, modelContent, displayText = reply, images = images.toList())
                return@withContext AgentReply(reply, images.toList(), convId)
            }

            conversations.addMessage(convId, modelContent)

            // Se ejecutan todas las herramientas pedidas en este paso.
            val responseParts = JSONArray()
            val directOutputs = mutableListOf<String>()
            var allDirect = true

            for (call in calls) {
                val name = call.optString("name")
                val args = call.optJSONObject("args") ?: JSONObject()
                val tool = tools.find(name)

                val output = if (tool == null) {
                    allDirect = false
                    "La herramienta '$name' no existe."
                } else {
                    if (!tool.direct) allDirect = false
                    try {
                        tool.execute(args)
                    } catch (e: Exception) {
                        allDirect = false
                        "Error al ejecutar $name: ${e.message}"
                    }
                }

                ToolOutput.images(output).forEach { image ->
                    if (images.size < MAX_IMAGES && images.none { it.url == image.url }) images.add(image)
                }
                directOutputs.add(ToolOutput.textWithoutImages(output))

                val functionResponse = JSONObject()
                    .put("name", name)
                    .put("response", JSONObject().put("result", output))
                call.optString("id").takeIf { it.isNotBlank() }?.let { functionResponse.put("id", it) }
                responseParts.put(JSONObject().put("functionResponse", functionResponse))
            }

            val responseContent = JSONObject().put("role", "user").put("parts", responseParts)
            working.put(responseContent)
            conversations.addMessage(convId, responseContent)

            // Acciones simples (abrir una app, poner una canción, una alarma): su
            // resultado ya es la respuesta, sin otra vuelta a Gemini.
            if (allDirect) {
                val reply = directOutputs.joinToString("\n").ifBlank { "Hecho." }
                val replyContent = JSONObject().put("role", "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", reply)))
                conversations.addMessage(convId, replyContent, displayText = reply, images = images.toList())
                return@withContext AgentReply(reply, images.toList(), convId)
            }
        }

        val fallback = "He hecho varias cosas pero no he terminado de responder. ¿Me lo repites?"
        conversations.addMessage(
            convId,
            JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", fallback))),
            displayText = fallback
        )
        AgentReply(fallback, images.toList(), convId)
    }

    private fun userContent(text: String, attachments: List<Attachment>): JSONObject {
        val parts = JSONArray().put(JSONObject().put("text", text))
        attachments.forEach {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", it.mimeType).put("data", it.base64)
                )
            )
        }
        return JSONObject().put("role", "user").put("parts", parts)
    }

    private fun functionCalls(content: JSONObject): List<JSONObject> {
        val parts = content.optJSONArray("parts") ?: return emptyList()
        return (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optJSONObject("functionCall") }
    }

    private fun textOf(content: JSONObject): String {
        val parts = content.optJSONArray("parts") ?: return ""
        return (0 until parts.length())
            .mapNotNull { i -> parts.optJSONObject(i)?.takeIf { !it.optBoolean("thought") }?.optString("text") }
            .joinToString("")
            .trim()
    }

    /**
     * Se envían como mucho los últimos [MAX_HISTORY] contenidos para no gastar
     * tokens sin fin, cortando siempre en un mensaje escrito por el usuario
     * (nunca entre una llamada a herramienta y su respuesta).
     */
    private fun trimHistory(history: List<JSONObject>): List<JSONObject> {
        if (history.size <= MAX_HISTORY) return history

        var start = history.size - MAX_HISTORY
        while (start < history.size && !isUserText(history[start])) start++
        return history.subList(start, history.size)
    }

    private fun isUserText(content: JSONObject): Boolean {
        if (content.optString("role") != "user") return false
        val first = content.optJSONArray("parts")?.optJSONObject(0) ?: return false
        return first.has("text")
    }

    private companion object {
        const val MAX_STEPS = 10
        const val MAX_IMAGES = 3
        const val MAX_HISTORY = 40
    }
}
