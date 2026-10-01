package com.apache.network

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.skia.Image

/**
 * Caché en memoria de las imágenes ya descargadas, para no volver a pedirlas
 * cada vez que la burbuja del chat se recompone o se hace scroll.
 */
private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

/**
 * Descarga una imagen de internet y la convierte en un [ImageBitmap] de Compose.
 *
 * Es bloqueante (red), así que debe llamarse siempre desde Dispatchers.IO.
 */
fun downloadImageBitmap(url: String): ImageBitmap {
    imageCache[url]?.let { return it }

    val request = Request.Builder()
        .url(url)
        // Wikimedia rechaza peticiones sin un User-Agent identificable.
        .header("User-Agent", "ApacheDesktop/0.1 (asistente personal de escritorio)")
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val bytes = response.body?.bytes() ?: throw Exception("Imagen vacía")
        val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()

        imageCache[url] = bitmap
        return bitmap
    }
}

/**
 * Pide al Core que guarde una imagen del chat en la carpeta Imágenes/Apache.
 * Devuelve la ruta del archivo creado. Bloqueante: llamar desde Dispatchers.IO.
 */
fun saveImageInCore(image: com.apache.model.ChatImage): String {
    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/images/save")
        .post(
            objectMapper.writeValueAsString(image)
                .toRequestBody(jsonMediaType)
        )
        .build()

    httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw Exception("El Core respondió con HTTP ${response.code}")

        val result = objectMapper.readTree(body)
        val path = result.path("path").asText("")
        if (path.isBlank()) throw Exception(result.path("error").asText("No se ha podido guardar la imagen."))
        return path
    }
}
