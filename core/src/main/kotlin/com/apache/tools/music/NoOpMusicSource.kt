package com.apache.tools.music

import org.springframework.stereotype.Component

/**
 * Fuente "vacía" que actúa como fallback mientras no haya ninguna integración real
 * (Spotify, local, YouTube Music...) conectada. Existe para que [MusicSourceManager]
 * siempre tenga al menos una fuente disponible y [com.apache.tools.MusicControlTool]
 * pueda dar una respuesta clara en vez de fallar o devolver null.
 *
 * Prioridad mínima a propósito: en cuanto se registre cualquier fuente real que
 * devuelva `isAvailable() == true`, [MusicSourceManager] la elegirá a ella en su lugar.
 */
@Component
class NoOpMusicSource : MusicSource {
    override val id = "none"
    override val displayName = "ninguna fuente"
    override val priority = Int.MIN_VALUE

    // Siempre disponible: es el último recurso cuando no hay nada más.
    override fun isAvailable() = true

    override fun play() = false
    override fun pause() = false
    override fun playPause() = false
    override fun next() = false
    override fun previous() = false
    override fun volumeUp() = false
    override fun volumeDown() = false
    override fun setVolume(percent: Int) = false

    override fun getStatus() = PlaybackStatus(isPlaying = false)
}
