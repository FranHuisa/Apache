package com.apache.ai

import com.apache.tools.ToolRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Resultado de una llamada a Gemini. Puede ser texto normal para mostrar al usuario, o una petición
 * de function calling que el [Agent] debe resolver.
 */
sealed class GeminiResult {

        data class TextResponse(val text: String) : GeminiResult()

        data class FunctionCall(
                val toolName: String,
                val args: Map<String, Any?>,
                val thoughtSignature: String? = null,
                val id: String? = null
        ) : GeminiResult()
}

/**
 * Cliente ligero para la API REST de Gemini (`generateContent`). No usa el SDK oficial de Google a
 * propósito: para un prototipo, una llamada HTTP directa con OkHttp es más fácil de depurar y no
 * añade una dependencia pesada. Si el proyecto crece, se puede sustituir por el SDK sin tocar el
 * resto del agente (Agent.kt solo conoce GeminiResult).
 */
@Component
class GeminiClient(
        @Value("\${apache.gemini.api-key}") private val apiKey: String,
        @Value("\${apache.gemini.model:gemini-2.0-flash}") private val model: String,
        private val toolRegistry: ToolRegistry
) {

        private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(30)).build()

        private val mapper = ObjectMapper()

        private val baseUrl
                get() =
                        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        /**
         * Envía el historial de conversación + el catálogo de tools a Gemini y decide si la
         * respuesta es texto para el usuario o una llamada a función.
         *
         * @param history turnos previos de la conversación (para dar contexto)
         * @param systemInstruction instrucción de sistema (personalidad, reglas de Apache)
         */
        fun sendMessage(history: List<GeminiTurn>, systemInstruction: String): GeminiResult {

                val contents = mutableListOf<Map<String, Any?>>()

                history.forEach { turn ->
                        when (turn.role) {
                                "user" -> {

                                        contents.add(
                                                mapOf(
                                                        "role" to "user",
                                                        "parts" to
                                                                listOf(mapOf("text" to turn.text))
                                                )
                                        )
                                }
                                "model" -> {

                                        if (turn.text.startsWith("FUNCTION_CALL\n")) {

                                                val lines = turn.text.split("\n", limit = 5)
                                                val toolName = lines.getOrNull(1) ?: ""
                                                val argsText = lines.getOrNull(2) ?: "{}"
                                                val id = lines.getOrNull(4)

                                                val thoughtSignature = lines.getOrNull(3)
                                                val args = parseFunctionArguments(argsText)

                                                contents.add(
                                                        mapOf(
                                                                "role" to "model",
                                                                "parts" to
                                                                        listOf(
                                                                                buildMap {
                                                                                        put(
                                                                                                "functionCall",
                                                                                                buildMap {
                                                                                                        put(
                                                                                                                "name",
                                                                                                                toolName
                                                                                                        )
                                                                                                        put(
                                                                                                                "args",
                                                                                                                args
                                                                                                        )

                                                                                                        if (!id.isNullOrBlank()
                                                                                                        ) {
                                                                                                                put(
                                                                                                                        "id",
                                                                                                                        id
                                                                                                                )
                                                                                                        }
                                                                                                }
                                                                                        )

                                                                                        if (!thoughtSignature
                                                                                                        .isNullOrBlank()
                                                                                        ) {
                                                                                                put(
                                                                                                        "thoughtSignature",
                                                                                                        thoughtSignature
                                                                                                )
                                                                                        }
                                                                                }
                                                                        )
                                                        )
                                                )
                                        } else {

                                                contents.add(
                                                        mapOf(
                                                                "role" to "model",
                                                                "parts" to
                                                                        listOf(
                                                                                mapOf(
                                                                                        "text" to
                                                                                                turn.text
                                                                                )
                                                                        )
                                                        )
                                                )
                                        }
                                }
                                "function" -> {

                                        val firstSeparator = turn.text.indexOf("\n")
                                        val lastSeparator = turn.text.lastIndexOf("\n")

                                        val toolName =
                                                if (firstSeparator >= 0) {
                                                        turn.text.substring(0, firstSeparator)
                                                } else {
                                                        turn.text
                                                }

                                        val output =
                                                if (firstSeparator >= 0 &&
                                                                lastSeparator > firstSeparator
                                                ) {
                                                        turn.text.substring(
                                                                firstSeparator + 1,
                                                                lastSeparator
                                                        )
                                                } else {
                                                        ""
                                                }

                                        val id =
                                                if (lastSeparator > firstSeparator) {
                                                        turn.text.substring(lastSeparator + 1)
                                                } else {
                                                        null
                                                }

                                        contents.add(
                                                mapOf(
                                                        "role" to "user",
                                                        "parts" to
                                                                listOf(
                                                                        mapOf(
                                                                                "functionResponse" to
                                                                                        buildMap {
                                                                                                put(
                                                                                                        "name",
                                                                                                        toolName
                                                                                                )

                                                                                                if (!id.isNullOrBlank()
                                                                                                ) {
                                                                                                        put(
                                                                                                                "id",
                                                                                                                id
                                                                                                        )
                                                                                                }

                                                                                                put(
                                                                                                        "response",
                                                                                                        mapOf(
                                                                                                                "result" to
                                                                                                                        output
                                                                                                        )
                                                                                                )
                                                                                        }
                                                                        )
                                                                )
                                                )
                                        )
                                }
                        }
                }

                val body =
                        mapOf(
                                "system_instruction" to
                                        mapOf(
                                                "parts" to
                                                        listOf(mapOf("text" to systemInstruction))
                                        ),
                                "contents" to contents,
                                "tools" to
                                        listOf(
                                                mapOf(
                                                        "function_declarations" to
                                                                toolRegistry
                                                                        .toGeminiFunctionDeclarations()
                                                )
                                        )
                        )

                val request =
                        Request.Builder()
                                .url(baseUrl)
                                .post(
                                        mapper.writeValueAsString(body)
                                                .toRequestBody("application/json".toMediaType())
                                )
                                .build()

                http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {

                                throw IllegalStateException(
                                        "Error llamando a Gemini: ${response.code} ${response.body?.string()}"
                                )
                        }

                        val json = mapper.readTree(response.body?.string())

                        val part = json["candidates"][0]["content"]["parts"][0]

                        // Gemini devuelve o bien "text", o bien "functionCall" en la parte de la
                        // respuesta

                        return if (part.has("functionCall")) {

                                val fc = part["functionCall"]

                                val argsNode = fc["args"]

                                val args: Map<String, Any?> =
                                        mapper.convertValue(argsNode, Map::class.java) as
                                                Map<String, Any?>

                                GeminiResult.FunctionCall(
                                        toolName = fc["name"].asText(),
                                        args = args,
                                        thoughtSignature = part["thoughtSignature"]?.asText(),
                                        id = fc["id"]?.asText()
                                )
                        } else {

                                GeminiResult.TextResponse(text = part["text"].asText())
                        }
                }
        }

        /**
         * Convierte los argumentos guardados por Agent en un mapa que pueda enviarse como objeto
         * JSON dentro de un functionCall de Gemini.
         *
         * Las tools que no necesitan argumentos suelen llegar como "{}". Para formatos de
         * argumentos más complejos, intentamos primero interpretarlos como JSON y, si no es
         * posible, usamos un mapa vacío.
         */
        private fun parseFunctionArguments(argsText: String): Map<String, Any?> {

                if (argsText.isBlank() || argsText == "{}") {
                        return emptyMap()
                }

                return try {

                        mapper.readValue(argsText, Map::class.java) as Map<String, Any?>
                } catch (_: Exception) {

                        emptyMap()
                }
        }
}
