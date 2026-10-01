package com.apache.util

import com.apache.model.ChatAttachment
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.imageio.ImageIO

/** Tamaño máximo de un adjunto (Gemini admite hasta ~20 MB por petición en total). */
const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

/** Frases que significan "mira mi pantalla": con ellas se hace la captura automáticamente. */
val screenRequestRegex = Regex(
    "(qu[eé] (hay|tengo|ves|sale|aparece) en (mi |la )?pantalla|mira (mi |la )?pantalla|" +
        "lee (mi |la )?pantalla|captura (de )?(mi |la )?pantalla|qu[eé] estoy viendo)",
    RegexOption.IGNORE_CASE
)

/**
 * Abre el selector de archivos de Windows y devuelve los archivos elegidos.
 * Debe llamarse desde el hilo de la interfaz (el diálogo es modal).
 */
fun chooseFiles(): List<File> {
    val dialog = FileDialog(null as Frame?, "Adjuntar a Apache", FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.isVisible = true
    return dialog.files?.toList().orEmpty()
}

/**
 * Convierte un archivo en adjunto. Devuelve null y un motivo si no se puede
 * (tipo no soportado o demasiado grande).
 */
fun fileToAttachment(file: File): Pair<ChatAttachment?, String?> {
    val mimeType = mimeTypeFor(file)
        ?: return null to "«${file.name}»: tipo de archivo no soportado (imágenes, PDF o texto)."

    if (file.length() > MAX_ATTACHMENT_BYTES) {
        return null to "«${file.name}» es demasiado grande (máximo 10 MB)."
    }

    val data = Base64.getEncoder().encodeToString(file.readBytes())
    return ChatAttachment(file.name, mimeType, data) to null
}

private fun mimeTypeFor(file: File): String? = when (file.extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "pdf" -> "application/pdf"
    "txt", "md", "log", "csv", "kt", "java", "py", "js", "json", "xml", "yml", "yaml", "html", "css", "sql" ->
        "text/plain"
    else -> null
}

/**
 * Captura todas las pantallas como JPEG, reducida a 1600 px de ancho como
 * máximo (suficiente para que Gemini la lea y mucho más ligera).
 * Bloqueante: llamar desde Dispatchers.IO.
 */
fun captureScreenAttachment(): ChatAttachment {
    val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { it.defaultConfiguration.bounds }
        .fold(Rectangle()) { acc, rect -> acc.union(rect) }

    val capture = Robot().createScreenCapture(bounds)
    val image = scaleDown(capture, maxWidth = 1600)

    val bytes = ByteArrayOutputStream().use { output ->
        ImageIO.write(image, "jpg", output)
        output.toByteArray()
    }

    val name = "pantalla-${LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))}.jpg"
    return ChatAttachment(name, "image/jpeg", Base64.getEncoder().encodeToString(bytes))
}

private fun scaleDown(image: BufferedImage, maxWidth: Int): BufferedImage {
    if (image.width <= maxWidth) return image

    val height = image.height * maxWidth / image.width
    val scaled = BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_RGB)
    val graphics = scaled.createGraphics()
    graphics.drawImage(image.getScaledInstance(maxWidth, height, Image.SCALE_SMOOTH), 0, 0, null)
    graphics.dispose()
    return scaled
}
