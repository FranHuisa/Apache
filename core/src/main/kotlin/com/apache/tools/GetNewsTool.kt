package com.apache.tools

import java.time.Duration
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component

/**
 * Tool de solo lectura con las noticias de hoy: titulares de España, una
 * sección (deportes, economía...) o noticias recientes de un tema concreto.
 * Usa el RSS público de Google Noticias, sin API key (ver [NewsFeed]).
 */
@Component
class GetNewsTool : Tool {

    override val name = "getNews"

    override val description =
        "Da las noticias de hoy: los titulares de España, una sección (deportes, economía, tecnología, " +
            "ciencia, salud, internacional, entretenimiento) o noticias recientes sobre un tema concreto " +
            "(un equipo, una persona, una ciudad...). Úsala cuando pidan noticias, titulares o qué ha pasado. " +
            "Para preguntas concretas que no son noticias, usa webSearch."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "topic" to mapOf(
                "type" to "string",
                "description" to "Sección o tema, ej: 'deportes', 'tecnología', 'Real Madrid', 'Almería'. " +
                    "Vacío = titulares generales."
            ),
            "count" to mapOf(
                "type" to "integer",
                "description" to "Cuántas noticias (1-10). Por defecto 5."
            )
        )
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(15))
        .callTimeout(Duration.ofSeconds(20))
        .build()

    override fun execute(args: Map<String, Any?>): String {
        val topic = (args["topic"] as? String)?.trim()?.ifBlank { null }
        val count = ((args["count"] as? Number)?.toInt() ?: (args["count"] as? String)?.toIntOrNull() ?: 5).coerceIn(1, 10)

        val xml = runCatching { fetch(NewsFeed.url(topic)) }.recoverCatching { fetch(NewsFeed.url(topic)) }.getOrNull()
            ?: return "No he podido conectar con Google Noticias. Si es importante, usa webSearch."

        val items = NewsFeed.pick(NewsFeed.parse(xml), count, newestFirst = NewsFeed.isSearch(topic))
        return NewsFeed.format(items, topic)
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "Apache/0.2 (asistente personal)").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }
}
