package com.apache.tools

import java.net.URLEncoder
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Una noticia de Google Noticias. */
data class NewsItem(val title: String, val source: String, val published: ZonedDateTime?, val link: String)

/**
 * Noticias de Google Noticias (RSS público, sin API key), en español de España.
 * Solo usa la librería estándar: el mismo archivo está en Apache de escritorio.
 */
object NewsFeed {

    private const val BASE = "https://news.google.com/rss"
    private const val LOCALE = "hl=es&gl=ES&ceid=ES:es"

    private val GENERAL = setOf("", "general", "portada", "titulares", "hoy", "últimas", "ultimas", "actualidad", "noticias")

    /** Secciones fijas de Google Noticias. */
    private val SECTIONS = mapOf(
        "mundo" to "WORLD", "internacional" to "WORLD",
        "españa" to "NATION", "espana" to "NATION", "nacional" to "NATION",
        "economía" to "BUSINESS", "economia" to "BUSINESS", "negocios" to "BUSINESS",
        "tecnología" to "TECHNOLOGY", "tecnologia" to "TECHNOLOGY",
        "entretenimiento" to "ENTERTAINMENT", "famosos" to "ENTERTAINMENT",
        "deportes" to "SPORTS", "deporte" to "SPORTS",
        "ciencia" to "SCIENCE",
        "salud" to "HEALTH"
    )

    /** URL del feed: portada, una sección o una búsqueda de los últimos días. */
    fun url(topic: String?): String {
        val clean = topic?.trim().orEmpty()
        val key = clean.lowercase()
        if (key in GENERAL) return "$BASE?$LOCALE"
        SECTIONS[key]?.let { return "$BASE/headlines/section/topic/$it?$LOCALE" }
        return "$BASE/search?q=${URLEncoder.encode("$clean when:3d", "UTF-8")}&$LOCALE"
    }

    fun isSearch(topic: String?): Boolean {
        val key = topic?.trim().orEmpty().lowercase()
        return key !in GENERAL && key !in SECTIONS
    }

    private val ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)

    private fun tag(xml: String, name: String): String? =
        Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)

    /** Lee los <item> del RSS. Quita la fuente del final del título ("Titular - El País"). */
    fun parse(xml: String): List<NewsItem> = ITEM.findAll(xml).mapNotNull { match ->
        val body = match.groupValues[1]
        val rawTitle = tag(body, "title")?.let(::clean) ?: return@mapNotNull null
        val source = tag(body, "source")?.let(::clean).orEmpty()
        val title = if (source.isNotBlank() && rawTitle.endsWith(" - $source")) {
            rawTitle.removeSuffix(" - $source").trim()
        } else rawTitle
        val published = tag(body, "pubDate")?.let {
            runCatching { ZonedDateTime.parse(clean(it), DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull()
        }
        NewsItem(title, source, published, tag(body, "link")?.let(::clean).orEmpty())
    }.filter { it.title.isNotBlank() }.toList()

    /**
     * Elige las [count] noticias a mostrar: sin repetidas y, en búsquedas,
     * las más recientes primero (la portada ya viene ordenada por relevancia).
     */
    fun pick(items: List<NewsItem>, count: Int, newestFirst: Boolean): List<NewsItem> {
        val seen = HashSet<String>()
        val unique = items.filter { seen.add(it.title.lowercase().filter(Char::isLetterOrDigit).take(60)) }
        val ordered = if (newestFirst) unique.sortedByDescending { it.published?.toEpochSecond() ?: 0L } else unique
        return ordered.take(count)
    }

    /** Texto para Gemini: titular, fuente y hace cuánto. */
    fun format(items: List<NewsItem>, topic: String?, now: ZonedDateTime = ZonedDateTime.now()): String {
        val about = topic?.trim()?.takeIf { it.lowercase() !in GENERAL }
        if (items.isEmpty()) {
            return if (about != null) "No he encontrado noticias recientes sobre «$about»."
            else "No he podido leer los titulares ahora mismo."
        }
        return buildString {
            appendLine(if (about != null) "Noticias recientes sobre «$about» (Google Noticias):" else "Titulares de hoy en España (Google Noticias):")
            items.forEachIndexed { index, item ->
                val details = listOfNotNull(item.source.ifBlank { null }, item.published?.let { ago(it, now) })
                append("${index + 1}. ${item.title}")
                if (details.isNotEmpty()) append(" — ${details.joinToString(", ")}")
                appendLine()
            }
            append("Resume los titulares en pocas líneas y di de qué medio es cada uno.")
        }
    }

    fun ago(time: ZonedDateTime, now: ZonedDateTime): String {
        val minutes = Duration.between(time, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 1 -> "ahora mismo"
            minutes < 60 -> "hace $minutes min"
            minutes < 24 * 60 -> "hace ${minutes / 60} h"
            minutes < 48 * 60 -> "ayer"
            else -> "hace ${minutes / (24 * 60)} días"
        }
    }

    private fun clean(text: String): String {
        var out = text.trim()
        if (out.startsWith("<![CDATA[")) out = out.removePrefix("<![CDATA[").removeSuffix("]]>")
        out = Regex("&#(x?)([0-9a-fA-F]+);").replace(out) { m ->
            val code = if (m.groupValues[1].isNotEmpty()) m.groupValues[2].toIntOrNull(16) else m.groupValues[2].toIntOrNull()
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
        return out.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&").trim()
    }
}
