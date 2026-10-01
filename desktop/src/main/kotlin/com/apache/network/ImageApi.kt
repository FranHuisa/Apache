package com.apache.network

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Request
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
