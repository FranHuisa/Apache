package com.apache.mobile.tools

/**
 * Saca el texto legible de una página HTML: título, y párrafos sin menús,
 * scripts ni estilos. Solo usa la librería estándar: el mismo archivo está en
 * Apache de escritorio.
 */
object WebText {

    private const val MAX_CHARS = 12_000

    fun extract(html: String, maxChars: Int = MAX_CHARS): String {
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let(::decode)?.trim().orEmpty()

        // Si hay <article> o <main>, el contenido de verdad suele estar ahí.
        val body = listOf("article", "main").firstNotNullOfOrNull { tag ->
            Regex("<$tag[^>]*>(.*?)</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.get(1)?.takeIf { it.length > 500 }
        } ?: html

        var text = body
        listOf("script", "style", "noscript", "svg", "nav", "header", "footer", "aside", "form", "iframe").forEach { tag ->
            text = text.replace(Regex("<$tag[^>]*>.*?</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        }
        text = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
            .replace(Regex("<[^>]+>"), " ")
        text = decode(text)
            .lines()
            .map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .filter { it.length > 1 }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")

        val full = if (title.isNotBlank() && !text.startsWith(title)) "$title\n\n$text" else text
        return if (full.length > maxChars) full.take(maxChars) + "\n[…texto recortado]" else full
    }

    private fun decode(text: String): String =
        Regex("&#(x?)([0-9a-fA-F]+);").replace(text) { m ->
            val code = if (m.groupValues[1].isNotEmpty()) m.groupValues[2].toIntOrNull(16) else m.groupValues[2].toIntOrNull()
            code?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: " "
        }.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
