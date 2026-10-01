package com.apache.tools.music

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import okhttp3.OkHttpClient
import okhttp3.Request

/** Resultado de buscar una canción: vídeo de YouTube listo para abrir. */
data class SongResult(
    val videoId: String,
    val title: String?
) {
    val watchUrl: String
        get() = "https://www.youtube.com/watch?v=$videoId&autoplay=1"
}

/**
 * Busca canciones en YouTube sin API key, leyendo la página de resultados
 * de búsqueda (la misma que ve cualquier navegador).
 *
 * Es deliberadamente simple: si YouTube cambia el formato de la página, la
 * búsqueda devolverá null y [com.apache.tools.PlaySongTool] abrirá la página
 * de resultados para que el usuario elija, en lugar de fallar.
 */
object YouTubeSongSearch {

    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(10)).build()

    /** Cada resultado normal empieza por `"videoRenderer":{"videoId":"<11 caracteres>"`. */
    private val VIDEO_REGEX = Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    /** Título del vídeo dentro del mismo bloque: `"title":{"runs":[{"text":"..."}]`. */
    private val TITLE_REGEX = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")

    fun searchUrl(query: String): String =
        "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)

    /** Devuelve el primer vídeo de la búsqueda, o null si no se ha podido encontrar. */
    fun findFirst(query: String): SongResult? {
        val request = Request.Builder()
            .url(searchUrl(query))
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
            )
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            // En Europa YouTube redirige a la página de consentimiento de cookies si no
            // hay ninguna; estas cookies indican que ya se ha respondido.
            .header("Cookie", "CONSENT=YES+cb; SOCS=CAI")
            .get()
            .build()

        val html = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }

        return parseFirstResult(html)
    }

    /** Separado de [findFirst] para poder probarlo sin red. */
    fun parseFirstResult(html: String): SongResult? {
        val match = VIDEO_REGEX.find(html) ?: return null
        val videoId = match.groupValues[1]

        // Buscamos el título solo cerca del resultado, para no coger el de otro vídeo.
        val window = html.substring(match.range.last, minOf(html.length, match.range.last + 4000))
        val title = TITLE_REGEX.find(window)?.groupValues?.get(1)?.let(::unescapeJson)

        return SongResult(videoId, title)
    }

    private fun unescapeJson(text: String): String =
        text.replace("\\u0026", "&")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
}
