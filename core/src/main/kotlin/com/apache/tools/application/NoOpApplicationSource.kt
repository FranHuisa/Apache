package com.apache.tools.application

/**
 * Fuente vacía utilizada como fallback cuando no existe ninguna fuente
 * disponible para controlar aplicaciones.
 */
class NoOpApplicationSource : ApplicationSource {

    override val id = "none"

    override val displayName = "No disponible"

    override val priority = 0

    override fun isAvailable(): Boolean {
        return true
    }

    override fun open(application: String): Boolean {
        return false
    }

    override fun close(application: String): Boolean {
        return false
    }

    override fun isRunning(application: String): Boolean {
        return false
    }
}