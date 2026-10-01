package com.apache.tools

import com.apache.agent.ChatImage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component

/**
 * Tool de solo lectura que busca entre 1 y 3 imágenes para mostrarlas en el
 * chat junto a la respuesta de Apache.
 *
 * Igual que [GetWeatherTool], usa servicios públicos que no requieren API key:
 *  1. Wikimedia Commons (fuente principal, imágenes libres y fiables).
 *  2. Openverse (alternativa si Commons no devuelve nada).
 *
 * La tool NO descarga las imágenes: solo devuelve sus URLs. El Agent las
 * extrae del resultado (ver [ChatImage.parseFromToolOutput]) y las envía al
 * Desktop dentro de `ChatResponse.images`; es el Desktop quien las descarga
 * y las dibuja debajo del mensaje.
 */
@Component
class SearchImagesTool : Tool {

    override val name = "searchImages"

    override val description =
        "Busca imágenes reales en internet y las muestra al usuario en el chat, debajo de tu " +
            "respuesta. Úsalo cuando el usuario pida ver, enseñar o mostrar algo (\"enséñame un " +
            "ajolote\", \"cómo es la Torre Eiffel\", \"fotos de...\") o cuando una imagen ayude " +
            "claramente a la explicación (animales, lugares, monumentos, objetos, obras de arte). " +
            "Elige cuántas mostrar: 1 si es una sola cosa concreta, 2 o 3 si el usuario pide " +
            "varias, una comparación o variedad. Nunca más de 3."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to
                    "Qué buscar, preferiblemente en inglés y con pocas palabras para obtener " +
                        "mejores resultados, ej: 'axolotl', 'Eiffel Tower at night'."
            ),
            "count" to mapOf(
                "type" to "integer",
                "description" to "Número de imágenes a mostrar, de 1 a 3."
            )
        ),
        "required" to listOf("query", "count")
    )

    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(10)).build()
    private val mapper = ObjectMapper()

    override fun execute(args: Map<String, Any?>): String {
        val query = (args["query"] as? String)?.trim()
        if (query.isNullOrBlank()) {
            return "No se ha indicado qué imágenes buscar."
        }

        // Gemini puede mandar el número como Int, Double o String.
        val count = ((args["count"] as? Number)?.toInt()
            ?: (args["count"] as? String)?.toIntOrNull()
            ?: 1).coerceIn(1, MAX_IMAGES)

        val images = try {
            searchWikimedia(query, count).ifEmpty { searchOpenverse(query, count) }
        } catch (e: Exception) {
            try {
                searchOpenverse(query, count)
            } catch (e2: Exception) {
                return "No se han podido buscar imágenes de '$query': ${e2.message ?: e.message}"
            }
        }

        if (images.isEmpty()) {
            return "No se han encontrado imágenes de '$query'. Díselo al usuario."
        }

        // Las líneas IMAGE|... las consume el Agent; el texto de arriba es para Gemini.
        return buildString {
            appendLine(
                "Se han encontrado ${images.size} imagen(es) de '$query'. Se mostrarán " +
                    "automáticamente al usuario debajo de tu respuesta: no copies las URLs en el " +
                    "texto, solo comenta brevemente lo que se ve si aporta algo."
            )
            images.forEach { appendLine(it.toToolLine()) }
        }.trimEnd()
    }

    /**
     * Busca en Wikimedia Commons archivos de tipo imagen (namespace 6) y pide
     * una miniatura de [THUMB_WIDTH] px, que es la que se muestra en el chat.
     */
    private fun searchWikimedia(query: String, count: Int): List<ChatImage> {
        val search = URLEncoder.encode("$query filetype:bitmap", StandardCharsets.UTF_8)

        val url =
            "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                "&generator=search&gsrnamespace=6&gsrlimit=${count * 4}&gsrsearch=$search" +
                "&prop=imageinfo&iiprop=url|mime&iiurlwidth=$THUMB_WIDTH"

        val json = getJson(url)
        val pages = json["query"]?.get("pages") ?: return emptyList()

        return pages.elements().asSequence()
            .sortedBy { it["index"]?.asInt() ?: Int.MAX_VALUE }
            .mapNotNull { page ->
                val info = page["imageinfo"]?.get(0) ?: return@mapNotNull null
                val mime = info["mime"]?.asText().orEmpty()
                if (mime !in SUPPORTED_MIME_TYPES) return@mapNotNull null

                val imageUrl = info["thumburl"]?.asText() ?: info["url"]?.asText()
                    ?: return@mapNotNull null

                ChatImage(
                    url = imageUrl,
                    title = page["title"]?.asText().orEmpty()
                        .removePrefix("File:")
                        .substringBeforeLast('.'),
                    sourceUrl = info["descriptionurl"]?.asText()
                )
            }
            .distinctBy { it.url }
            .take(count)
            .toList()
    }

    /** Alternativa: Openverse (imágenes con licencia abierta de Flickr, museos, etc.). */
    private fun searchOpenverse(query: String, count: Int): List<ChatImage> {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val json = getJson("https://api.openverse.org/v1/images/?q=$q&page_size=${count * 2}&mature=false")

        return json["results"]?.elements()?.asSequence().orEmpty()
            .mapNotNull { result ->
                val imageUrl = result["thumbnail"]?.asText() ?: result["url"]?.asText()
                    ?: return@mapNotNull null

                ChatImage(
                    url = imageUrl,
                    title = result["title"]?.asText().orEmpty(),
                    sourceUrl = result["foreign_landing_url"]?.asText()
                )
            }
            .distinctBy { it.url }
            .take(count)
            .toList()
    }

    private fun getJson(url: String): JsonNode {
        val request = Request.Builder()
            .url(url)
            // Wikimedia exige un User-Agent identificable.
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            return mapper.readTree(response.body?.string().orEmpty())
        }
    }

    companion object {
        const val MAX_IMAGES = 3
        private const val THUMB_WIDTH = 640
        const val USER_AGENT = "ApacheDesktop/0.1 (asistente personal de escritorio)"

        private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }
}
