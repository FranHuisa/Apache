package com.apache.tools

import com.apache.tools.music.MusicSourceManager
import com.apache.tools.music.SpotifyClient
import com.apache.tools.music.SpotifyPlayResult
import com.apache.tools.music.YouTubeSongSearch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.springframework.stereotype.Component

/**
 * Tool que busca una canción concreta y la pone.
 *
 * - YouTube (por defecto): busca la canción, coge el primer resultado y lo abre
 *   en el navegador ya reproduciéndose. No necesita API key ni cuenta.
 * - Spotify: Spotify no permite reproducir una canción concreta sin su API
 *   (OAuth), así que solo se abre la búsqueda en la app de escritorio.
 *
 * Antes de abrir la canción se pausa lo que esté sonando (vía [MusicSourceManager])
 * para que no suenen dos cosas a la vez. Una vez abierta, el navegador aparece
 * como sesión multimedia de Windows, así que "pausa" o "siguiente" siguen
 * funcionando con [MusicControlTool].
 */
@Component
class PlaySongTool(
    private val sourceManager: MusicSourceManager,
    private val spotify: SpotifyClient
) : Tool {

    override val name = "playSong"

    override val description =
        "Busca y pone una canción, artista, álbum o playlist concreta. Úsalo cuando el usuario " +
            "diga 'pon...', 'busca la canción...', 'reproduce...', 'quiero escuchar...' con algo " +
            "concreto. Para controlar lo que ya está sonando (pausa, siguiente, volumen) usa " +
            "musicControl en su lugar."

    override val riskLevel = RiskLevel.REVERSIBLE

    // La respuesta ya es clara ("Poniendo X en YouTube"): no hace falta otra vuelta a Gemini.
    override val requiresGeminiResponse = false

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to
                    "Canción a buscar, idealmente 'título artista', ej: 'Bohemian Rhapsody Queen'."
            ),
            "platform" to mapOf(
                "type" to "string",
                "description" to
                    "Dónde ponerla. Si no se indica, se usa Spotify cuando está conectado y, si no, " +
                        "YouTube. Indícalo solo si el usuario lo pide expresamente.",
                "enum" to listOf("youtube", "spotify")
            )
        ),
        "required" to listOf("query")
    )

    override fun execute(args: Map<String, Any?>): String {
        val query = (args["query"] as? String)?.trim()
        if (query.isNullOrBlank()) {
            return "¿Qué canción quieres que ponga?"
        }

        val platform = (args["platform"] as? String)?.trim()?.lowercase()
            ?: if (spotify.isConnected) "spotify" else "youtube"

        if (platform != "spotify") return playOnYouTube(query)

        // Spotify con la API conectada: pone la canción directamente.
        if (spotify.isConnected) {
            return when (val result = spotify.play(query)) {
                is SpotifyPlayResult.Playing ->
                    "Poniendo «${result.track.name}» de ${result.track.artists} en Spotify."
                is SpotifyPlayResult.Failed ->
                    result.reason + " ¿Quieres que la ponga en YouTube?"
            }
        }

        return openInSpotify(query)
    }

    private fun playOnYouTube(query: String): String {
        val song = try {
            YouTubeSongSearch.findFirst(query)
        } catch (_: Exception) {
            null
        }

        pauseCurrentPlayback()

        if (song == null) {
            // Si no se ha podido leer el resultado, al menos abrimos la búsqueda.
            return if (openUrl(YouTubeSongSearch.searchUrl(query))) {
                "No he podido elegir el vídeo automáticamente, así que te he abierto la búsqueda " +
                    "de «$query» en YouTube."
            } else {
                "No he podido abrir el navegador para buscar «$query»."
            }
        }

        return if (openUrl(song.watchUrl)) {
            "Poniendo «${song.title ?: query}» en YouTube."
        } else {
            "He encontrado «${song.title ?: query}», pero no he podido abrir el navegador."
        }
    }

    private fun openInSpotify(query: String): String {
        // Spotify usa %20 para los espacios en sus URIs, no '+'.
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20")

        return if (openUrl("spotify:search:$encoded")) {
            "He abierto la búsqueda de «$query» en Spotify; dale tú a reproducir. Si conectas tu " +
                "cuenta («conecta mi Spotify»), la pondré yo directamente."
        } else {
            "No he podido abrir Spotify. ¿Está instalado? Si quieres, la pongo en YouTube."
        }
    }

    /** Pausa lo que esté sonando ahora mismo (Spotify, otro vídeo...), si hay algo. */
    private fun pauseCurrentPlayback() {
        try {
            val source = sourceManager.activeSource()
            if (source.id != "none" && source.getStatus().isPlaying) {
                source.pause()
            }
        } catch (_: Exception) {
            // No es grave: simplemente sonarán las dos cosas.
        }
    }

    private fun openUrl(url: String): Boolean = SystemOpener.open(url)
}
