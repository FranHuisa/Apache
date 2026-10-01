package com.apache.tools

import com.apache.tools.music.SpotifyClient
import org.springframework.stereotype.Component

/** Abre el navegador para conectar la cuenta de Spotify del usuario (una sola vez). */
@Component
class ConnectSpotifyTool(private val spotify: SpotifyClient) : Tool {

    override val name = "connectSpotify"

    override val description =
        "Conecta la cuenta de Spotify del usuario con Apache para poder poner canciones concretas " +
            "en Spotify. Úsalo cuando el usuario pida conectar, vincular o iniciar sesión en Spotify."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val requiresGeminiResponse = false

    override val parametersSchema: Map<String, Any?> = mapOf("type" to "object", "properties" to emptyMap<String, Any>())

    override fun execute(args: Map<String, Any?>): String {
        if (!spotify.isEnabled) {
            return "Spotify todavía no está configurado. Hay que crear una app gratuita en " +
                "developer.spotify.com con la Redirect URI http://127.0.0.1:8080/api/spotify/callback " +
                "y poner su Client ID en la variable de entorno SPOTIFY_CLIENT_ID. Los pasos están en la Ayuda."
        }

        if (spotify.isConnected) {
            return "Spotify ya está conectado. Pídeme cualquier canción."
        }

        return if (SystemOpener.open("http://127.0.0.1:8080/api/spotify/login")) {
            "Te he abierto Spotify en el navegador: acepta los permisos y listo."
        } else {
            "Abre http://127.0.0.1:8080/api/spotify/login en el navegador para conectar Spotify."
        }
    }
}
