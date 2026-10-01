package com.apache.agent

/**
 * Imagen que Apache muestra en el chat junto a su respuesta.
 *
 * @param url dirección de la imagen (normalmente una miniatura) que descarga el Desktop
 * @param title título o descripción corta, usado como pie de foto
 * @param sourceUrl página de origen, para abrirla al hacer clic (opcional)
 */
data class ChatImage(
    val url: String,
    val title: String = "",
    val sourceUrl: String? = null
) {

    /**
     * Formato de una línea dentro del resultado de una tool:
     * `IMAGE|url|título|urlOrigen`. Se eliminan los `|` y saltos de línea del
     * título para no romper el formato.
     */
    fun toToolLine(): String {
        val safeTitle = title.replace('|', ' ').replace('\n', ' ').trim()
        return "$IMAGE_PREFIX$url|$safeTitle|${sourceUrl.orEmpty()}"
    }

    companion object {
        const val IMAGE_PREFIX = "IMAGE|"

        /**
         * Extrae las imágenes que una tool haya incluido en su resultado.
         *
         * Así el Agent no necesita saber qué tool concreta las ha generado:
         * cualquier tool futura puede devolver imágenes con el mismo formato.
         */
        fun parseFromToolOutput(output: String): List<ChatImage> =
            output.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith(IMAGE_PREFIX) }
                .mapNotNull { line ->
                    val parts = line.removePrefix(IMAGE_PREFIX).split('|')
                    val url = parts.getOrNull(0)?.trim()

                    if (url.isNullOrBlank() || !url.startsWith("http")) {
                        null
                    } else {
                        ChatImage(
                            url = url,
                            title = parts.getOrNull(1)?.trim().orEmpty(),
                            sourceUrl = parts.getOrNull(2)?.trim()?.ifBlank { null }
                        )
                    }
                }
                .toList()
    }
}
