package com.apache.tools

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Tool de solo lectura que busca información actual en internet.
 *
 * Usa la búsqueda de Google integrada en Gemini ("grounding" con la tool
 * `google_search`) en una llamada aparte: la conversación principal sigue
 * usando solo function calling, y esta tool devuelve un resumen con sus
 * fuentes para que Gemini redacte la respuesta final.
 *
 * No necesita ninguna API key adicional: usa la misma de Gemini.
 */
@Component
class WebSearchTool(
    @Value("\${apache.gemini.api-key}") private val apiKey: String,
    @Value("\${apache.gemini.model:gemini-2.0-flash}") private val model: String
) : Tool {

    override val name = "webSearch"

    override val description =
        "Busca información actual en internet (Google). Úsalo para noticias, resultados " +
            "deportivos, precios, horarios, estrenos, datos que cambian con el tiempo o cualquier " +
            "cosa que no sepas con seguridad o que pueda haber cambiado recientemente. No lo uses " +
            "para el tiempo (usa getWeather) ni para cosas del ordenador del usuario."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "Qué buscar, como una pregunta completa, ej: '¿Quién ganó ayer el Real Madrid - Barça?'."
            )
        ),
        "required" to listOf("query")
    )

    private val http = OkHttpClient.Builder()
        .readTimeout(Duration.ofSeconds(40))
        .callTimeout(Duration.ofSeconds(45))
        .build()
    private val mapper = ObjectMapper()

    override fun execute(args: Map<String, Any?>): String {
        val query = (args["query"] as? String)?.trim()
        if (query.isNullOrBlank()) {
            return "No se ha indicado qué buscar."
        }

        val body = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf(
                            "text" to "Busca en internet y responde en español, con datos concretos " +
                                "y fechas si las hay: $query"
                        )
                    )
                )
            ),
            "tools" to listOf(mapOf("google_search" to emptyMap<String, Any>()))
        )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .post(mapper.writeValueAsString(body).toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return "No se ha podido buscar en internet (HTTP ${response.code})."
                }

                val json = mapper.readTree(text)
                val candidate = json.path("candidates").path(0)

                val answer = candidate.path("content").path("parts")
                    .mapNotNull { part -> part.path("text").asText(null) }
                    .joinToString("")
                    .trim()

                if (answer.isBlank()) {
                    return "La búsqueda no ha devuelto resultados para '$query'."
                }

                val sources = candidate.path("groundingMetadata").path("groundingChunks")
                    .mapNotNull { chunk ->
                        val web = chunk.path("web")
                        val title = web.path("title").asText(null)
                        val uri = web.path("uri").asText(null)
                        if (title == null && uri == null) null else "- ${title ?: uri}"
                    }
                    .distinct()
                    .take(5)

                buildString {
                    appendLine("Resultado de la búsqueda en internet:")
                    appendLine(answer)
                    if (sources.isNotEmpty()) {
                        appendLine()
                        appendLine("Fuentes (menciona las más relevantes por su nombre si aporta):")
                        sources.forEach { appendLine(it) }
                    }
                }.trimEnd()
            }
        } catch (e: Exception) {
            "No se ha podido buscar en internet: ${e.message}"
        }
    }
}
