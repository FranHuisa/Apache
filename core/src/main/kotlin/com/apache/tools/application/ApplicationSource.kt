package com.apache.tools.application

/**
 * Interfaz para las fuentes que permiten controlar aplicaciones.
 *
 * La tool no conoce cómo se abre o cierra una aplicación concreta.
 * Esta interfaz permite separar la lógica de Apache de la implementación
 * específica del sistema operativo.
 */
interface ApplicationSource {

    val id: String

    val displayName: String

    val priority: Int

    fun isAvailable(): Boolean

    fun open(application: String): Boolean

    fun close(application: String): Boolean

    fun isRunning(application: String): Boolean
}