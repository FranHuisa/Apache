package com.apache.tools

import com.apache.agent.ChatImage
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Últimas imágenes que Apache ha enseñado en el chat, para que el usuario
 * pueda decir "guarda la segunda". Apache es de un solo usuario, así que basta
 * con recordar la última respuesta que tuvo imágenes.
 */
@Component
class ShownImagesStore {

    @Volatile
    var lastShown: List<ChatImage> = emptyList()
        private set

    fun remember(images: List<ChatImage>) {
        if (images.isNotEmpty()) lastShown = images
    }
}

/**
 * Guarda imágenes en el ordenador SOLO cuando el usuario lo pide (clic en la
 * imagen o "guarda la 2"). Por defecto las imágenes del chat no se guardan en
 * ningún sitio.
 *
 * Carpeta: Imágenes del usuario (también en OneDrive / "Imágenes") + "Apache".
 */
@Component
class ImageSaver {

    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(30)).build()

    /** Descarga la imagen y la guarda. Devuelve el archivo creado. */
    fun save(image: ChatImage): File {
        val (bytes, extension) = downloadBest(image.url)

        val folder = File(
            UserFolders.resolve("imagenes").firstOrNull()
                ?: File(System.getProperty("user.home"), "Pictures"),
            "Apache"
        )
        folder.mkdirs()

        val baseName = image.title
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
            .ifBlank { "imagen-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) }

        var file = File(folder, "$baseName.$extension")
        var copy = 2
        while (file.exists()) {
            file = File(folder, "$baseName ($copy).$extension")
            copy++
        }

        file.writeBytes(bytes)
        return file
    }

    /**
     * En el chat se muestran miniaturas de 640 px. Si la imagen es de
     * Wikimedia, se intenta bajar una versión de 1600 px; si no existe (la
     * original es más pequeña), se usa la miniatura.
     */
    private fun downloadBest(url: String): Pair<ByteArray, String> {
        val larger = url.replace(Regex("/(\\d+)px-"), "/1600px-")
        if (larger != url) {
            runCatching { return download(larger) }
        }
        return download(url)
    }

    private fun download(url: String): Pair<ByteArray, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SearchImagesTool.USER_AGENT)
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

            val bytes = response.body?.bytes() ?: throw IllegalStateException("Imagen vacía")
            val type = response.header("Content-Type").orEmpty()

            val extension = when {
                "png" in type -> "png"
                "webp" in type -> "webp"
                "gif" in type -> "gif"
                else -> "jpg"
            }
            return bytes to extension
        }
    }
}

/** Tool para guardar una o varias de las imágenes que Apache acaba de enseñar. */
@Component
class SaveImageTool(
    private val store: ShownImagesStore,
    private val saver: ImageSaver
) : Tool {

    override val name = "saveImage"

    override val description =
        "Guarda en el ordenador del usuario imágenes que Apache acaba de enseñar en el chat. " +
            "Úsalo cuando diga 'guarda la segunda', 'coge la 3', 'me quedo con la primera', " +
            "'guárdalas todas'... Las posiciones empiezan en 1 y se refieren a la última " +
            "respuesta con imágenes. Nunca guardes imágenes si el usuario no lo pide."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val requiresGeminiResponse = false

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "positions" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "integer"),
                "description" to "Posiciones a guardar (1, 2 o 3). Vacío o ausente si quiere todas."
            ),
            "all" to mapOf(
                "type" to "boolean",
                "description" to "true para guardar todas las de la última respuesta."
            )
        )
    )

    override fun execute(args: Map<String, Any?>): String {
        val shown = store.lastShown
        if (shown.isEmpty()) return "No he enseñado ninguna imagen todavía."

        val positions = (args["positions"] as? List<*>).orEmpty()
            .mapNotNull { (it as? Number)?.toInt() ?: (it as? String)?.toIntOrNull() }
            .distinct()

        val selected = if (args["all"] == true || positions.isEmpty()) {
            shown
        } else {
            val invalid = positions.filter { it !in 1..shown.size }
            if (invalid.isNotEmpty()) {
                return "Solo he enseñado ${shown.size} imagen(es); no existe la ${invalid.first()}."
            }
            positions.map { shown[it - 1] }
        }

        val saved = mutableListOf<File>()
        val failed = mutableListOf<String>()

        selected.forEach { image ->
            try {
                saved.add(saver.save(image))
            } catch (e: Exception) {
                failed.add(image.title.ifBlank { "imagen" })
            }
        }

        return buildString {
            when (saved.size) {
                0 -> append("No he podido guardar la imagen.")
                1 -> append("Guardada en ${saved.first().absolutePath}")
                else -> append("Guardadas ${saved.size} imágenes en ${saved.first().parentFile.absolutePath}")
            }
            if (failed.isNotEmpty() && saved.isNotEmpty()) append(" (no se pudo guardar: ${failed.joinToString()})")
        }
    }
}

/** Petición del Desktop al hacer clic en una imagen del chat. */
data class SaveImageRequest(val url: String, val title: String = "", val sourceUrl: String? = null)

/** POST /api/images/save: guarda la imagen en la que el usuario ha hecho clic. */
@RestController
@RequestMapping("/api/images")
class ImageController(private val saver: ImageSaver) {

    @PostMapping("/save")
    fun save(@RequestBody request: SaveImageRequest): Map<String, String?> =
        try {
            val file = saver.save(ChatImage(request.url, request.title, request.sourceUrl))
            mapOf("path" to file.absolutePath, "error" to null)
        } catch (e: Exception) {
            mapOf("path" to null, "error" to (e.message ?: "No se ha podido guardar la imagen."))
        }
}
