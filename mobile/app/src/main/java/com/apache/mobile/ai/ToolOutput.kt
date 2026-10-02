package com.apache.mobile.ai

import com.apache.mobile.data.ChatImage

/**
 * Formato con el que una herramienta devuelve imágenes dentro de su texto:
 * una línea `IMAGE|url|título|origen` por imagen (igual que en escritorio).
 * El Agent las recoge y el chat las dibuja debajo de la respuesta.
 */
object ToolOutput {

    private const val PREFIX = "IMAGE|"

    fun imageLine(image: ChatImage): String {
        val title = image.title.replace('|', ' ').replace('\n', ' ').trim()
        return "$PREFIX${image.url}|$title|${image.sourceUrl.orEmpty()}"
    }

    fun images(output: String): List<ChatImage> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith(PREFIX) }
            .mapNotNull { line ->
                val parts = line.removePrefix(PREFIX).split('|')
                val url = parts.getOrNull(0)?.trim()
                if (url.isNullOrBlank() || !url.startsWith("http")) {
                    null
                } else {
                    ChatImage(url, parts.getOrNull(1)?.trim().orEmpty(), parts.getOrNull(2)?.trim()?.ifBlank { null })
                }
            }
            .toList()

    /** El texto de la herramienta sin las líneas de imágenes (para respuestas directas). */
    fun textWithoutImages(output: String): String =
        output.lineSequence().filterNot { it.trim().startsWith(PREFIX) }.joinToString("\n").trim()
}
