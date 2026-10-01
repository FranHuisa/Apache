package com.apache.api

import com.apache.tools.music.SpotifyClient
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Conexión con la cuenta de Spotify (ver [SpotifyClient]).
 *
 *  GET /api/spotify/status   -> {enabled, connected}
 *  GET /api/spotify/login    -> redirige a Spotify para dar permiso
 *  GET /api/spotify/callback -> Spotify vuelve aquí con el código
 */
@RestController
@RequestMapping("/api/spotify")
class SpotifyController(private val spotify: SpotifyClient) {

    @GetMapping("/status")
    fun status(): Map<String, Boolean> =
        mapOf("enabled" to spotify.isEnabled, "connected" to spotify.isConnected)

    @GetMapping("/login")
    fun login(): ResponseEntity<String> {
        if (!spotify.isEnabled) {
            return page(HttpStatus.BAD_REQUEST, "Spotify no está configurado", NOT_CONFIGURED)
        }

        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, spotify.buildLoginUrl())
            .build()
    }

    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?
    ): ResponseEntity<String> {
        if (error != null || code == null || state == null) {
            return page(HttpStatus.BAD_REQUEST, "No se ha conectado Spotify", "Spotify no ha dado permiso ($error).")
        }

        val failure = spotify.completeLogin(code, state)
            ?: return page(HttpStatus.OK, "Spotify conectado", "Ya puedes cerrar esta pestaña y pedirle canciones a Apache.")

        return page(HttpStatus.BAD_REQUEST, "No se ha conectado Spotify", failure)
    }

    private fun page(status: HttpStatus, title: String, message: String): ResponseEntity<String> {
        val html = """
            <!doctype html><html lang="es"><head><meta charset="utf-8"><title>Apache · $title</title></head>
            <body style="background:#121212;color:#fff;font-family:Segoe UI,sans-serif;display:flex;
                         align-items:center;justify-content:center;height:100vh;margin:0">
              <div style="max-width:520px;padding:32px;border-radius:16px;background:#1D2923">
                <h2 style="color:#1DB954;margin-top:0">$title</h2><p>${escape(message)}</p>
              </div>
            </body></html>
        """.trimIndent()

        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html)
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private companion object {
        const val NOT_CONFIGURED =
            "Crea una app en developer.spotify.com, añade como Redirect URI " +
                "http://127.0.0.1:8080/api/spotify/callback y pon su Client ID en la variable de " +
                "entorno SPOTIFY_CLIENT_ID antes de arrancar Apache."
    }
}
