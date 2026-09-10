package com.apache.tools.music

/**
 * Estado de reproducción de una [MusicSource] en un momento dado.
 *
 * Se mantiene deliberadamente simple y agnóstico de la fuente concreta:
 * cualquier reproductor (Spotify, un reproductor local, YouTube Music...)
 * tiene que poder rellenar estos campos, aunque algunos queden a null si
 * esa fuente no los soporta.
 */
data class PlaybackStatus(
    val isPlaying: Boolean,
    val title: String? = null,
    val artist: String? = null,
    /** Volumen actual, de 0 a 100. Null si la fuente no expone volumen (ej. YouTube web). */
    val volumePercent: Int? = null
)

/**
 * Contrato común para cualquier "fuente" de reproducción multimedia que
 * [com.apache.tools.MusicControlTool] pueda controlar.
 *
 * La idea es la misma que con [com.apache.tools.Tool]: separar el contrato
 * (qué acciones existen) de la implementación concreta (cómo se hacen esas
 * acciones en Spotify, en un reproductor local o en YouTube Music), para que
 * la tool y el agente no tengan que saber nada de esos detalles.
 *
 * De momento el proyecto no tiene ninguna integración real todavía (Fase 5
 * del roadmap); [NoOpMusicSource] es la única implementación y sirve de
 * placeholder/fallback. Añadir una integración real (ej. SpotifyMusicSource)
 * consiste en:
 *
 * 1. Implementar esta interfaz hablando con la API/SDK correspondiente.
 * 2. Anotarla con @Component y darle una [priority] mayor que la de
 *    NoOpMusicSource.
 * 3. [MusicSourceManager] la recogerá automáticamente y pasará a usarla
 *    como fuente activa cuando [isAvailable] devuelva true.
 *
 * No hay que tocar MusicControlTool ni el Agent para nada de esto.
 */
interface MusicSource {

    /** Identificador corto y estable de la fuente, ej. "spotify", "local", "youtube_music". */
    val id: String

    /** Nombre legible de la fuente, para mostrar en las respuestas al usuario. */
    val displayName: String

    /**
     * Prioridad de la fuente cuando hay varias disponibles a la vez: a mayor número, más
     * prioridad. [NoOpMusicSource] usa la prioridad más baja posible para actuar solo como
     * último recurso cuando ninguna fuente real está disponible.
     */
    val priority: Int

    /**
     * Indica si esta fuente está lista para recibir comandos ahora mismo (ej. sesión de
     * Spotify autenticada y activa, reproductor local abierto, etc.). [MusicSourceManager]
     * usa esto para elegir qué fuente controlar en cada momento.
     */
    fun isAvailable(): Boolean

    fun play(): Boolean
    fun pause(): Boolean

    /** Alterna entre reproducir y pausar según el estado actual. */
    fun playPause(): Boolean

    fun next(): Boolean
    fun previous(): Boolean

    /** Sube el volumen un paso "razonable" (definido por cada fuente, ej. 10%). */
    fun volumeUp(): Boolean

    /** Baja el volumen un paso "razonable" (definido por cada fuente, ej. 10%). */
    fun volumeDown(): Boolean

    /** Fija el volumen a un porcentaje exacto (0-100). */
    fun setVolume(percent: Int): Boolean

    /** Consulta qué se está reproduciendo (o si no hay nada) ahora mismo. */
    fun getStatus(): PlaybackStatus
}
