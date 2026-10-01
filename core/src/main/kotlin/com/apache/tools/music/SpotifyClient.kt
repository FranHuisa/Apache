package com.apache.tools.music

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Properties
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Canción encontrada en Spotify. */
data class SpotifyTrack(val uri: String, val name: String, val artists: String)

/** Resultado de intentar reproducir en Spotify. */
sealed class SpotifyPlayResult {
    data class Playing(val track: SpotifyTrack) : SpotifyPlayResult()
    data class Failed(val reason: String) : SpotifyPlayResult()
}

/**
 * Cliente de la API Web de Spotify para poner canciones concretas.
 *
 * Es opcional: solo se activa si `apache.spotify.client-id` tiene valor
 * (variable de entorno SPOTIFY_CLIENT_ID). Usa el flujo OAuth "Authorization
 * Code con PKCE", que no necesita client secret:
 *
 *  1. El usuario abre /api/spotify/login (tool connectSpotify) y acepta en Spotify.
 *  2. Spotify redirige a /api/spotify/callback con un código.
 *  3. Se cambia el código por tokens y el refresh token se guarda en
 *     ~/.apache/spotify.properties para no tener que volver a iniciar sesión.
 *
 * Reproducir canciones con la API requiere una cuenta Spotify Premium.
 */
@Component
class SpotifyClient(
    @Value("\${apache.spotify.client-id:}") private val clientId: String,
    @Value("\${apache.spotify.redirect-uri:http://127.0.0.1:8080/api/spotify/callback}")
    private val redirectUri: String
) {

    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(15)).build()
    private val mapper = ObjectMapper()

    private val tokenFile = File(File(System.getProperty("user.home"), ".apache"), "spotify.properties")

    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenExpiresAt: Instant = Instant.EPOCH

    /** Inicios de sesión en curso: state -> code_verifier. */
    private val pendingLogins = mutableMapOf<String, String>()

    /** true si hay un Client ID configurado. */
    val isEnabled: Boolean
        get() = clientId.isNotBlank()

    /** true si además el usuario ya ha conectado su cuenta. */
    val isConnected: Boolean
        get() = isEnabled && loadRefreshToken() != null

    // --- Inicio de sesión (PKCE) ---

    /** URL de Spotify a la que hay que mandar al usuario para que dé permiso. */
    @Synchronized
    fun buildLoginUrl(): String {
        check(isEnabled) { "Spotify no está configurado (falta SPOTIFY_CLIENT_ID)." }

        val verifier = randomString(64)
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = randomString(16)

        pendingLogins[state] = verifier

        return "https://accounts.spotify.com/authorize" +
            "?client_id=${encode(clientId)}" +
            "&response_type=code" +
            "&redirect_uri=${encode(redirectUri)}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$challenge" +
            "&state=$state" +
            "&scope=${encode(SCOPES)}"
    }

    /** Cambia el código de Spotify por tokens. Devuelve null si todo fue bien, o el error. */
    @Synchronized
    fun completeLogin(code: String, state: String): String? {
        val verifier = pendingLogins.remove(state)
            ?: return "El inicio de sesión ha caducado. Vuelve a pedirle a Apache que conecte Spotify."

        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()

        return try {
            val json = postToken(form)
            saveTokens(json)
            null
        } catch (e: Exception) {
            "Spotify ha rechazado el inicio de sesión: ${e.message}"
        }
    }

    // --- Reproducción ---

    fun searchTrack(query: String): SpotifyTrack? {
        val json = api("GET", "/v1/search?type=track&limit=1&q=${encode(query)}")
        val item = json?.path("tracks")?.path("items")?.path(0) ?: return null
        val uri = item.path("uri").asText(null) ?: return null

        return SpotifyTrack(
            uri = uri,
            name = item.path("name").asText(""),
            artists = item.path("artists").mapNotNull { it.path("name").asText(null) }.joinToString(", ")
        )
    }

    /** Busca la canción y la pone en el dispositivo de Spotify activo (o en el primero disponible). */
    fun play(query: String): SpotifyPlayResult {
        if (!isConnected) return SpotifyPlayResult.Failed("Spotify no está conectado.")

        val track = try {
            searchTrack(query)
        } catch (e: Exception) {
            return SpotifyPlayResult.Failed("No se ha podido buscar en Spotify: ${e.message}")
        } ?: return SpotifyPlayResult.Failed("No he encontrado «$query» en Spotify.")

        return try {
            var deviceId = findDeviceId()

            // Si no hay ninguna app de Spotify abierta, abrimos la de escritorio y esperamos.
            if (deviceId == null) {
                com.apache.tools.SystemOpener.open("spotify:")
                repeat(8) {
                    if (deviceId == null) {
                        Thread.sleep(1000)
                        deviceId = findDeviceId()
                    }
                }
            }

            val id = deviceId
                ?: return SpotifyPlayResult.Failed("No hay ningún dispositivo de Spotify disponible. Abre Spotify y vuelve a intentarlo.")

            val body = mapper.writeValueAsString(mapOf("uris" to listOf(track.uri)))
            val response = rawApi("PUT", "/v1/me/player/play?device_id=${encode(id)}", body)

            when (response.first) {
                in 200..299 -> SpotifyPlayResult.Playing(track)
                403 -> SpotifyPlayResult.Failed("Spotify solo permite elegir canciones desde otras apps con una cuenta Premium.")
                else -> SpotifyPlayResult.Failed("Spotify respondió con un error (${response.first}).")
            }
        } catch (e: Exception) {
            SpotifyPlayResult.Failed("No se ha podido reproducir en Spotify: ${e.message}")
        }
    }

    private fun findDeviceId(): String? {
        val devices = api("GET", "/v1/me/player/devices")?.path("devices") ?: return null
        val list = devices.toList()
        val chosen = list.firstOrNull { it.path("is_active").asText("false") == "true" }
            ?: list.firstOrNull { it.path("type").asText("").equals("Computer", ignoreCase = true) }
            ?: list.firstOrNull()
        return chosen?.path("id")?.asText(null)
    }

    // --- HTTP ---

    private fun api(method: String, path: String): JsonNode? {
        val (code, body) = rawApi(method, path, null)
        if (code !in 200..299 || body.isBlank()) return null
        return mapper.readTree(body)
    }

    private fun rawApi(method: String, path: String, jsonBody: String?): Pair<Int, String> {
        val token = validAccessToken() ?: throw IllegalStateException("Spotify no está conectado.")

        val requestBody = jsonBody?.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.spotify.com$path")
            .header("Authorization", "Bearer $token")
            .method(method, if (method == "GET") null else (requestBody ?: "".toRequestBody(null)))
            .build()

        http.newCall(request).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    @Synchronized
    private fun validAccessToken(): String? {
        val current = accessToken
        if (current != null && Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(60))) {
            return current
        }

        val refreshToken = loadRefreshToken() ?: return null

        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .build()

        saveTokens(postToken(form))
        return accessToken
    }

    private fun postToken(form: FormBody): JsonNode {
        val request = Request.Builder().url("https://accounts.spotify.com/api/token").post(form).build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} $text")
            return mapper.readTree(text)
        }
    }

    private fun saveTokens(json: JsonNode) {
        accessToken = json.path("access_token").asText(null)
        accessTokenExpiresAt = Instant.now().plusSeconds(json.path("expires_in").asInt().toLong())

        // Spotify puede devolver un refresh token nuevo; si no lo hace, se conserva el anterior.
        json.path("refresh_token").asText(null)?.let { refresh ->
            tokenFile.parentFile.mkdirs()
            val properties = Properties()
            properties.setProperty("refreshToken", refresh)
            tokenFile.outputStream().use { properties.store(it, "Apache - sesión de Spotify (no compartir)") }
        }
    }

    private fun loadRefreshToken(): String? =
        try {
            if (!tokenFile.exists()) {
                null
            } else {
                val properties = Properties()
                tokenFile.inputStream().use { properties.load(it) }
                properties.getProperty("refreshToken")?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun randomString(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..length).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private companion object {
        const val SCOPES = "user-modify-playback-state user-read-playback-state"
    }
}
