package com.apache.ai

/**
 * Archivo que el usuario adjunta a un mensaje (imagen, captura de pantalla,
 * PDF o texto). Se envía a Gemini como `inline_data` junto al mensaje.
 *
 * No se guarda en MySQL: en el historial solo queda el nombre del archivo,
 * así que Gemini lo "ve" en el turno en que se envía.
 *
 * @param data contenido en Base64
 */
data class GeminiAttachment(
    val name: String,
    val mimeType: String,
    val data: String
)
