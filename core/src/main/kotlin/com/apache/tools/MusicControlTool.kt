package com.apache.tools

import com.apache.tools.music.MusicSource
import com.apache.tools.music.MusicSourceManager
import org.springframework.stereotype.Component

/**
 * Tool que controla la reproducción multimedia desde Apache: play/pausa, siguiente/anterior,
 * volumen, y consultar qué se está reproduciendo.
 *
 * Riesgo REVERSIBLE (como [OpenApplicationTool]): las acciones tienen un efecto real
 * (cambian lo que suena o el volumen), pero son benignas y fácilmente deshacibles, así que
 * se ejecutan directamente sin pedir confirmación, quedando registradas en el log del agente.
 *
 * IMPORTANTE (diseño): esta tool NO sabe hablar con Spotify, YouTube Music ni ningún
 * reproductor en concreto. Solo conoce la interfaz [MusicSource] y
 * delega en [MusicSourceManager] para saber a qué fuente mandar cada comando. Así, cuando en
 * la Fase 5 del roadmap se añadan integraciones reales, esta clase no necesita cambiar:
 * basta con registrar una nueva MusicSource (ver NoOpMusicSource para un ejemplo mínimo).
 */
@Component
class MusicControlTool(private val sourceManager: MusicSourceManager) : Tool {

    override val name = "musicControl"

    override val description =
        "Controla la reproducción de música/multimedia: reproducir, pausar, siguiente, " +
            "anterior, subir/bajar volumen, fijar un volumen concreto, o consultar qué se " +
            "está reproduciendo ahora mismo."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "description" to "Acción a realizar sobre la reproducción actual.",
                "enum" to listOf(
                    "play",
                    "pause",
                    "playPause",
                    "next",
                    "previous",
                    "volumeUp",
                    "volumeDown",
                    "setVolume",
                    "status"
                )
            ),
            "volume" to mapOf(
                "type" to "integer",
                "description" to
                    "Volumen exacto de 0 a 100. Solo se usa (y es obligatorio) cuando action es 'setVolume'."
            )
        ),
        "required" to listOf("action")
    )

    override fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.trim()
            ?: return "No se ha especificado ninguna acción."

        val source = sourceManager.activeSource()

        // Si la única fuente disponible es la vacía (NoOpMusicSource), avisamos con un mensaje
        // claro en vez de intentar ejecutar el comando y devolver un "false" sin contexto.
        if (source.id == "none" && action != "status") {
            return "No hay ninguna fuente de música conectada todavía (Spotify, local, YouTube Music...). " +
                "No puedo ${describeAction(action)}."
        }

        return when (action) {
            "play" -> resultOf(source.play(), "He reanudado la reproducción en ${source.displayName}.", action)
            "pause" -> resultOf(source.pause(), "He pausado la reproducción en ${source.displayName}.", action)
            "playPause" -> resultOf(source.playPause(), "Cambiado el estado de reproducción en ${source.displayName}.", action)
            "next" -> resultOf(source.next(), "Saltado a la siguiente canción en ${source.displayName}.", action)
            "previous" -> resultOf(source.previous(), "Vuelto a la canción anterior en ${source.displayName}.", action)
            "volumeUp" -> resultOf(source.volumeUp(), "He subido el volumen en ${source.displayName}.", action)
            "volumeDown" -> resultOf(source.volumeDown(), "He bajado el volumen en ${source.displayName}.", action)
            "setVolume" -> {
                val volume = (args["volume"] as? Number)?.toInt()
                    ?: (args["volume"] as? String)?.toIntOrNull()
                    ?: return "Para fijar el volumen necesito un valor numérico entre 0 y 100."

                val clamped = volume.coerceIn(0, 100)
                resultOf(source.setVolume(clamped), "He puesto el volumen a $clamped% en ${source.displayName}.", action)
            }
            "status" -> describeStatus(source)
            else -> "Acción '$action' no reconocida. Acciones disponibles: " +
                "play, pause, playPause, next, previous, volumeUp, volumeDown, setVolume, status."
        }
    }

    private fun describeStatus(source: MusicSource): String {
        if (source.id == "none") {
            return "No hay ninguna fuente de música conectada ahora mismo."
        }

        val status = source.getStatus()

        if (!status.isPlaying && status.title == null) {
            return "${source.displayName}: no se está reproduciendo nada ahora mismo."
        }

        val trackText = listOfNotNull(status.title, status.artist?.let { "de $it" })
            .joinToString(" ")
            .ifBlank { "algo sin título/artista disponible" }

        val stateText = if (status.isPlaying) "reproduciendo" else "en pausa"
        val volumeText = status.volumePercent?.let { " · volumen $it%" } ?: ""

        return "${source.displayName}: $stateText $trackText$volumeText."
    }

    private fun resultOf(success: Boolean, successMessage: String, action: String): String =
        if (success) successMessage else "No he podido ${describeAction(action)}."

    private fun describeAction(action: String): String = when (action) {
        "play" -> "reanudar la reproducción"
        "pause" -> "pausar la reproducción"
        "playPause" -> "cambiar el estado de reproducción"
        "next" -> "saltar a la siguiente canción"
        "previous" -> "volver a la canción anterior"
        "volumeUp" -> "subir el volumen"
        "volumeDown" -> "bajar el volumen"
        "setVolume" -> "fijar el volumen"
        else -> "realizar esa acción"
    }
}
