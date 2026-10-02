package com.apache.mobile.tools

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.apache.mobile.ai.GeminiClient
import com.apache.mobile.ai.ToolOutput
import com.apache.mobile.data.ChatImage
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Últimas imágenes enseñadas, para "guarda la 2", y las recientes, para no repetirlas. */
object ShownImages {
    @Volatile
    var last: List<ChatImage> = emptyList()

    private val recent = ArrayDeque<String>()

    fun remember(images: List<ChatImage>) {
        if (images.isEmpty()) return
        last = images
        synchronized(recent) {
            images.forEach { recent.addLast(it.url) }
            while (recent.size > 60) recent.removeFirst()
        }
    }

    fun wasRecentlyShown(url: String): Boolean = synchronized(recent) { url in recent }
}

/**
 * Busca 1-3 imágenes y las verifica con Gemini (visión) antes de enseñarlas,
 * igual que Apache de escritorio: candidatas de Wikimedia Commons + Openverse,
 * ordenadas por coincidencia con el título, sin repetir las ya enseñadas.
 */
class SearchImagesTool(private val gemini: GeminiClient) : Tool {

    override val name = "searchImages"

    override val description =
        "Busca imágenes reales y las muestra en el chat debajo de tu respuesta. Úsalo cuando el " +
            "usuario quiera ver algo o una imagen ayude de verdad. 1 imagen para algo concreto, " +
            "2-3 para varias o comparar. Si dice que no eran las correctas, vuelve a llamarla con " +
            "una búsqueda más precisa (las ya enseñadas se descartan solas)."

    override val parameters = Schema.obj(
        "query" to Schema.string("Qué buscar, en inglés y con las palabras clave que lo distinguen, ej: 'black cat'."),
        "description" to Schema.string("Qué debe verse en la imagen, en español y con detalle, ej: 'un gato completamente negro'."),
        "count" to Schema.integer("Número de imágenes, de 1 a 3."),
        required = listOf("query", "count")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val query = args.str("query") ?: return@withContext "No se ha indicado qué buscar."
        val wanted = args.str("description") ?: query
        val count = (args.int("count") ?: 1).coerceIn(1, 3)

        val candidates = (runCatching { wikimedia(query) }.getOrDefault(emptyList()) +
            runCatching { openverse(query) }.getOrDefault(emptyList()))
            .distinctBy { it.url }

        if (candidates.isEmpty()) return@withContext "No se han encontrado imágenes de '$query'. Díselo en una frase."

        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
        val ranked = candidates.sortedWith(
            compareBy<ChatImage> { ShownImages.wasRecentlyShown(it.url) }
                .thenByDescending { image -> terms.count { image.title.lowercase().contains(it) } }
        )

        val verified = verify(ranked.take(8), wanted)
        val chosen = when {
            verified == null -> ranked.take(count)
            else -> verified.take(count)
        }

        if (chosen.isEmpty()) {
            return@withContext "Ninguna imagen encontrada muestra de verdad $wanted. Díselo en una frase y propón buscarlo de otra forma."
        }

        ShownImages.remember(chosen)

        buildString {
            appendLine("Se mostrarán ${chosen.size} imagen(es) debajo de tu respuesta. No copies las URLs; una frase corta como mucho.")
            chosen.forEach { appendLine(ToolOutput.imageLine(it)) }
        }.trim()
    }

    private fun wikimedia(query: String): List<ChatImage> {
        val search = URLEncoder.encode("$query filetype:bitmap", "UTF-8")
        val json = Http.getJson(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
                "&gsrnamespace=6&gsrlimit=12&gsrsearch=$search&prop=imageinfo&iiprop=url|mime&iiurlwidth=640"
        )
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()

        return pages.keys().asSequence()
            .mapNotNull { pages.optJSONObject(it) }
            .sortedBy { it.optInt("index", Int.MAX_VALUE) }
            .mapNotNull { page ->
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
                if (info.optString("mime") !in setOf("image/jpeg", "image/png", "image/webp")) return@mapNotNull null
                val url = info.optString("thumburl").ifBlank { info.optString("url") }.ifBlank { return@mapNotNull null }
                ChatImage(
                    url = url,
                    title = page.optString("title").removePrefix("File:").substringBeforeLast('.'),
                    sourceUrl = info.optString("descriptionurl").ifBlank { null }
                )
            }
            .toList()
    }

    private fun openverse(query: String): List<ChatImage> {
        val results = Http.getJson(
            "https://api.openverse.org/v1/images/?q=${URLEncoder.encode(query, "UTF-8")}&page_size=12&mature=false"
        ).optJSONArray("results") ?: return emptyList()

        return (0 until results.length()).mapNotNull { i ->
            val item = results.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("thumbnail").ifBlank { item.optString("url") }.ifBlank { return@mapNotNull null }
            ChatImage(url, item.optString("title"), item.optString("foreign_landing_url").ifBlank { null })
        }
    }

    /** Gemini mira las candidatas y dice cuáles valen. null = no se pudo verificar. */
    private suspend fun verify(candidates: List<ChatImage>, wanted: String): List<ChatImage>? {
        val downloads = coroutineScope {
            candidates.map { image ->
                async(Dispatchers.IO) { runCatching { image to Http.getBytes(image.url, maxBytes = 1_500_000) }.getOrNull() }
            }.awaitAll().filterNotNull()
        }
        if (downloads.isEmpty()) return null

        val parts = JSONArray().put(
            JSONObject().put(
                "text",
                "Te paso ${downloads.size} imágenes numeradas de 0 a ${downloads.size - 1}, en este orden. " +
                    "¿Cuáles muestran claramente esto: \"$wanted\"? Sé estricto con los detalles. Responde " +
                    "SOLO con los números válidos separados por comas, de mejor a peor, o con NINGUNA."
            )
        )
        downloads.forEach { (_, download) ->
            val (bytes, type) = download
            val mime = if (type.startsWith("image/")) type else "image/jpeg"
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", mime).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                )
            )
        }

        return try {
            val content = gemini.generate(
                JSONArray().put(JSONObject().put("role", "user").put("parts", parts)),
                temperature = 0.0
            )
            val answerParts = content.optJSONArray("parts") ?: JSONArray()
            val answer = (0 until answerParts.length()).joinToString("") { answerParts.optJSONObject(it)?.optString("text").orEmpty() }

            if (answer.uppercase().contains("NINGUNA")) {
                emptyList()
            } else {
                Regex("\\d+").findAll(answer)
                    .mapNotNull { it.value.toIntOrNull() }
                    .filter { it in downloads.indices }
                    .distinct()
                    .map { downloads[it].first }
                    .toList()
                    .ifEmpty { null }
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Guarda imágenes del chat en la galería (Imágenes/Apache), solo si el usuario lo pide. */
object ImageSaver {

    /** Devuelve una descripción de dónde se ha guardado. Bloqueante: llamar desde Dispatchers.IO. */
    fun save(context: Context, image: ChatImage): String {
        // En Wikimedia se intenta una versión más grande que la miniatura del chat.
        val larger = image.url.replace(Regex("/(\\d+)px-"), "/1600px-")
        val (bytes, type) = if (larger != image.url) {
            runCatching { Http.getBytes(larger) }.getOrElse { Http.getBytes(image.url) }
        } else {
            Http.getBytes(image.url)
        }

        val extension = when {
            "png" in type -> "png"
            "webp" in type -> "webp"
            else -> "jpg"
        }
        val mime = if (type.startsWith("image/")) type else "image/jpeg"
        val name = image.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60)
            .ifBlank { "apache-${System.currentTimeMillis()}" } + ".$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Apache")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("No se ha podido crear la imagen en la galería.")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("No se ha podido escribir la imagen.")
            return "Galería › Imágenes › Apache › $name"
        }

        // Android 8-9: carpeta de imágenes propia de la app (sin pedir permisos de almacenamiento).
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Apache").apply { mkdirs() }
        File(folder, name).writeBytes(bytes)
        return "${folder.absolutePath}/$name"
    }
}

/** "Guarda la 2", "coge la tercera", "guárdalas todas". */
class SaveImageTool(private val context: Context) : Tool {

    override val name = "saveImage"

    override val description =
        "Guarda en la galería imágenes que Apache acaba de enseñar. Úsalo solo cuando el usuario " +
            "lo pida ('guarda la segunda', 'coge la 3', 'guárdalas todas'). Las posiciones empiezan " +
            "en 1 y se refieren a la última respuesta con imágenes."

    override val direct = true

    override val parameters = Schema.obj(
        "positions" to Schema.array("Posiciones a guardar (1-3). Vacío si quiere todas.", Schema.integer("Posición")),
        "all" to Schema.boolean("true para guardar todas.")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val shown = ShownImages.last
        if (shown.isEmpty()) return@withContext "No he enseñado ninguna imagen todavía."

        val positionsJson = args.optJSONArray("positions") ?: JSONArray()
        val positions = (0 until positionsJson.length()).mapNotNull { positionsJson.optInt(it, -1).takeIf { p -> p > 0 } }

        val selected = if (args.optBoolean("all") || positions.isEmpty()) {
            shown
        } else {
            positions.firstOrNull { it > shown.size }?.let {
                return@withContext "Solo he enseñado ${shown.size} imagen(es); no existe la $it."
            }
            positions.distinct().map { shown[it - 1] }
        }

        val saved = selected.mapNotNull { runCatching { ImageSaver.save(context, it) }.getOrNull() }
        when {
            saved.isEmpty() -> "No he podido guardar la imagen."
            saved.size == 1 -> "Guardada en ${saved.first()}."
            else -> "Guardadas ${saved.size} imágenes en Galería › Imágenes › Apache."
        }
    }
}
