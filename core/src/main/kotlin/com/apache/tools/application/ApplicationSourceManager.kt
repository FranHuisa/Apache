package com.apache.tools.application

import org.springframework.stereotype.Component

/**
 * Gestiona las fuentes disponibles para el control de aplicaciones.
 *
 * Selecciona automáticamente la fuente disponible con mayor prioridad.
 */
@Component
class ApplicationSourceManager(
    private val sources: List<ApplicationSource>
) {

    fun activeSource(): ApplicationSource {
        return sources
            .filter { it.isAvailable() }
            .maxByOrNull { it.priority }
            ?: NoOpApplicationSource()
    }
}