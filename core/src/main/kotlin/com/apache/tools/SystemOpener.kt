package com.apache.tools

import java.io.File

/**
 * Abre URLs, URIs (spotify:...) y archivos con el programa predeterminado del
 * sistema.
 *
 * No se usa java.awt.Desktop porque Spring Boot arranca en modo headless.
 * En Windows se usa `rundll32 url.dll,FileProtocolHandler`, que no pasa por
 * cmd y por tanto no tiene problemas con los '&' de una URL ni con espacios.
 */
object SystemOpener {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val isMac = System.getProperty("os.name").lowercase().contains("mac")

    fun open(target: String): Boolean {
        val command = when {
            isWindows -> listOf("rundll32", "url.dll,FileProtocolHandler", target)
            isMac -> listOf("open", target)
            else -> listOf("xdg-open", target)
        }
        return start(command)
    }

    /** Abre el explorador de archivos con el archivo seleccionado. */
    fun showInFolder(file: File): Boolean = when {
        isWindows -> start(listOf("explorer.exe", "/select,", file.absolutePath))
        isMac -> start(listOf("open", "-R", file.absolutePath))
        else -> open(file.parentFile?.absolutePath ?: file.absolutePath)
    }

    private fun start(command: List<String>): Boolean =
        try {
            ProcessBuilder(command).start()
            true
        } catch (_: Exception) {
            false
        }
}
