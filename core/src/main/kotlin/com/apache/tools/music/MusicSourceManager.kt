package com.apache.tools.music

import org.springframework.stereotype.Component

/**
 * Elige, de entre todas las [MusicSource] registradas en Spring, cuál debe controlar
 * [com.apache.tools.MusicControlTool] en cada momento.
 *
 * Igual que [com.apache.tools.ToolRegistry] con las tools, Spring inyecta aquí
 * automáticamente todas las clases que implementen [MusicSource] y estén anotadas con
 * @Component. Esto significa que añadir Spotify, un reproductor local o YouTube Music
 * en el futuro (Fase 5 del roadmap) no requiere tocar esta clase ni MusicControlTool:
 * basta con crear la clase nueva.
 *
 * Política de selección (deliberadamente simple para el prototipo): de entre las fuentes
 * disponibles ([MusicSource.isAvailable] == true), se elige la de mayor [MusicSource.priority].
 * [NoOpMusicSource] siempre está disponible con la prioridad mínima, así que siempre hay
 * una fuente "activa", aunque sea la vacía.
 */
@Component
class MusicSourceManager(private val sources: List<MusicSource>) {

    /** Fuente de música que debe recibir el próximo comando. */
    fun activeSource(): MusicSource =
        sources.filter { it.isAvailable() }
            .maxByOrNull { it.priority }
            ?: sources.first { it.id == "none" }

    /** Todas las fuentes registradas, disponibles o no (útil para debug/logging). */
    fun all(): List<MusicSource> = sources
}
