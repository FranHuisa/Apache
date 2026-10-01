package com.apache.tools

import com.apache.agent.ChatImage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Tool de solo lectura que busca entre 1 y 3 imágenes para mostrarlas en el
 * chat junto a la respuesta de Apache.
 *
 * Cómo elige las imágenes (para no enseñar "gatos atigrados" cuando se piden
 * "gatos negros"):
 *  1. Reúne candidatas de Wikimedia Commons y de Openverse (sin API key).
 *  2. Las ordena por cuántas palabras de la búsqueda aparecen en su título y
 *     quita las que ya se enseñaron hace poco (para no repetir si el usuario
 *     dice "esas no").
 *  3. Descarga las mejores y se las enseña a Gemini (visión), que dice cuáles
 *     muestran de verdad lo pedido. Solo se enseñan esas.
 *  Si la verificación falla (red, Gemini caído), se usa el orden del paso 2.
 *
 * La tool no envía las imágenes al Desktop: devuelve sus URLs en líneas
 * `IMAGE|...` (ver [ChatImage]) y el Desktop las descarga y las dibuja.
 */
@Component
class SearchImagesTool(
    @Value("\${apache.gemini.api-key}") private val apiKey: String,
    @Value("\${apache.gemini.model:gemini-2.0-flash}") private val model: String
) : Tool {

    override val name = "searchImages"

    override val description =
        "Busca imágenes reales en internet y las muestra al usuario en el chat, debajo de tu " +
            "respuesta. Úsalo cuando el usuario pida ver, enseñar o mostrar algo (\"enséñame un " +
            "ajolote\", \"cómo es la Torre Eiffel\", \"fotos de...\") o cuando una imagen ayude " +
            "claramente a la explicación. Elige cuántas mostrar: 1 si es una sola cosa concreta, " +
            "2 o 3 si pide varias, una comparación o variedad. Nunca más de 3. Si el usuario dice " +
            "que las imágenes no eran correctas, vuelve a llamarla con una búsqueda más precisa: " +
            "las ya mostradas se descartan solas."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to
                    "Qué buscar, en inglés y con las palabras clave que lo distinguen (color, " +
                        "raza, lugar...), ej: 'black cat', 'Eiffel Tower at night', 'axolotl'."
            ),
            "description" to mapOf(
                "type" to "string",
                "description" to
                    "Qué tiene que verse en la imagen, en español y con detalle, para comprobar " +
                        "que es correcta. Ej: 'un gato completamente negro'."
            ),
            "count" to mapOf(
                "type" to "integer",
                "description" to "Número de imágenes a mostrar, de 1 a 3."
            )
        ),
        "required" to listOf("query", "count")
    )

    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(15)).build()
    private val geminiHttp = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(40)).build()
    private val mapper = ObjectMapper()

    /** URLs enseñadas recientemente (las últimas [RECENT_LIMIT]) para no repetirlas. */
    private val recentlyShown = ArrayDeque<String>()

    override fun execute(args: Map<String, Any?>): String {
        val query = (args["query"] as? String)?.trim()
        if (query.isNullOrBlank()) {
            return "No se ha indicado qué imágenes buscar."
        }

        val wanted = (args["description"] as? String)?.trim()?.ifBlank { null } ?: query

        // Gemini puede mandar el número como Int, Double o String.
        val count = ((args["count"] as? Number)?.toInt()
            ?: (args["count"] as? String)?.toIntOrNull()
            ?: 1).coerceIn(1, MAX_IMAGES)

        // 1. Candidatas de las dos fuentes (si una falla, seguimos con la otra).
        val candidates = (safe { searchWikimedia(query, CANDIDATES) } + safe { searchOpenverse(query, CANDIDATES) })
            .distinctBy { it.url }

        if (candidates.isEmpty()) {
            return "No se han encontrado imágenes de '$query'. Díselo al usuario en una frase."
        }

        // 2. Primero las que tienen las palabras de la búsqueda en el título; las ya
        //    enseñadas, al final (solo se usarán si no queda nada más).
        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
        val shown = synchronized(recentlyShown) { recentlyShown.toSet() }

        val ranked = candidates
            .sortedWith(
                compareBy<ChatImage> { it.url in shown }
                    .thenByDescending { image -> terms.count { image.title.lowercase().contains(it) } }
            )

        // 3. Verificación visual con Gemini.
        val verified = verifyWithGemini(ranked.take(VERIFY_LIMIT), wanted)

        val chosen = when {
            verified == null -> ranked.take(count) // No se pudo verificar: mejor orden disponible.
            verified.isEmpty() -> emptyList()
            else -> verified.take(count)
        }

        if (chosen.isEmpty()) {
            return "He buscado imágenes de '$query' pero ninguna muestra de verdad $wanted. " +
                "Díselo al usuario en una frase y propón buscarlo de otra forma."
        }

        synchronized(recentlyShown) {
            chosen.forEach { recentlyShown.addLast(it.url) }
            while (recentlyShown.size > RECENT_LIMIT) recentlyShown.removeFirst()
        }

        // Las líneas IMAGE|... las consume el Agent; el texto de arriba es para Gemini.
        return buildString {
            appendLine(
                "Se mostrarán ${chosen.size} imagen(es) de '$query' debajo de tu respuesta. " +
                    "No copies las URLs; acompáñalas con una frase corta como mucho."
            )
            chosen.forEach { appendLine(it.toToolLine()) }
        }.trimEnd()
    }

    // ---------------------------------------------------------------------
    // Fuentes
    // ---------------------------------------------------------------------

    /**
     * Busca en Wikimedia Commons archivos de tipo imagen (namespace 6) y pide
     * una miniatura de [THUMB_WIDTH] px, que es la que se muestra en el chat.
     */
    private fun searchWikimedia(query: String, limit: Int): List<ChatImage> {
        val search = URLEncoder.encode("$query filetype:bitmap", StandardCharsets.UTF_8)

        val url =
            "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                "&generator=search&gsrnamespace=6&gsrlimit=$limit&gsrsearch=$search" +
                "&prop=imageinfo&iiprop=url|mime&iiurlwidth=$THUMB_WIDTH"

        val pages = getJson(url)["query"]?.get("pages") ?: return emptyList()

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
            .toList()
    }

    /** Openverse: imágenes con licencia abierta de Flickr, museos, etc. */
    private fun searchOpenverse(query: String, limit: Int): List<ChatImage> {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val json = getJson("https://api.openverse.org/v1/images/?q=$q&page_size=$limit&mature=false")

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
            .toList()
    }

    // ---------------------------------------------------------------------
    // Verificación visual
    // ---------------------------------------------------------------------

    /**
     * Enseña las candidatas a Gemini y devuelve las que muestran [wanted], de
     * mejor a peor. Devuelve null si no se ha podido verificar (y entonces se
     * usa el orden por título), o una lista vacía si ninguna vale.
     */
    private fun verifyWithGemini(candidates: List<ChatImage>, wanted: String): List<ChatImage>? {
        if (candidates.isEmpty()) return emptyList()

        // Descarga en paralelo; las que fallen simplemente no se verifican.
        val downloads = candidates
            .map { image -> CompletableFuture.supplyAsync { image to download(image.url) } }
            .map { it.join() }
            .filter { it.second != null }

        if (downloads.isEmpty()) return null

        val parts = mutableListOf<Map<String, Any?>>(
            mapOf(
                "text" to "Te paso ${downloads.size} imágenes numeradas de 0 a ${downloads.size - 1}, " +
                    "en este orden. ¿Cuáles muestran claramente esto: \"$wanted\"? Sé estricto con " +
                    "los detalles (color, raza, tipo, lugar...). Responde SOLO con los números " +
                    "válidos separados por comas, de la mejor a la peor, o con la palabra NINGUNA."
            )
        )
        downloads.forEach { (_, image) ->
            val (mimeType, bytes) = image!!
            parts.add(
                mapOf("inline_data" to mapOf("mime_type" to mimeType, "data" to Base64.getEncoder().encodeToString(bytes)))
            )
        }

        val body = mapOf(
            "contents" to listOf(mapOf("role" to "user", "parts" to parts)),
            "generationConfig" to mapOf("temperature" to 0)
        )

        return try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(mapper.writeValueAsString(body).toRequestBody("application/json".toMediaType()))
                .build()

            val answer = geminiHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                mapper.readTree(response.body?.string().orEmpty())
                    .path("candidates").path(0).path("content").path("parts")
                    .mapNotNull { it.path("text").asText(null) }
                    .joinToString("")
                    .trim()
            }

            if (answer.uppercase().contains("NINGUNA")) return emptyList()

            Regex("\\d+").findAll(answer)
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in downloads.indices }
                .distinct()
                .map { downloads[it].first }
                .toList()
                .ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /** Descarga una miniatura. Devuelve (tipo MIME, bytes) o null si falla o pesa demasiado. */
    private fun download(url: String): Pair<String, ByteArray>? =
        try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.size > MAX_VERIFY_BYTES) return null

                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
                    ?.takeIf { it.startsWith("image/") }
                    ?: "image/jpeg"
                mime to bytes
            }
        } catch (_: Exception) {
            null
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

    private inline fun safe(block: () -> List<ChatImage>): List<ChatImage> =
        try {
            block()
        } catch (_: Exception) {
            emptyList()
        }

    companion object {
        const val MAX_IMAGES = 3
        private const val THUMB_WIDTH = 640

        /** Candidatas que se piden a cada fuente. */
        private const val CANDIDATES = 12

        /** Cuántas de las mejores candidatas se le enseñan a Gemini para verificar. */
        private const val VERIFY_LIMIT = 8

        private const val MAX_VERIFY_BYTES = 1_500_000
        private const val RECENT_LIMIT = 60
        const val USER_AGENT = "ApacheDesktop/0.1 (asistente personal de escritorio)"

        private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
