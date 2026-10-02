package com.apache.tools

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component

/** Ubicación aproximada del PC. */
data class ApproxLocation(val city: String, val region: String, val country: String, val latitude: Double, val longitude: Double) {
    val displayName: String
        get() = listOf(city, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/**
 * Ubicación aproximada del ordenador por su IP pública (el PC no tiene GPS).
 * Suele acertar la ciudad o una cercana. Se guarda una hora en memoria.
 */
@Component
class IpLocation {

    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(8)).build()
    private val mapper = ObjectMapper()

    @Volatile
    private var cached: Pair<Instant, ApproxLocation>? = null

    fun current(): ApproxLocation? {
        cached?.let { (at, location) -> if (at.isAfter(Instant.now().minusSeconds(3600))) return location }
        val location = runCatching { ipWhoIs() }.getOrNull() ?: runCatching { ipApiCo() }.getOrNull()
        if (location != null) cached = Instant.now() to location
        return location
    }

    private fun get(url: String) = http.newCall(
        Request.Builder().url(url).header("User-Agent", "Apache/0.2 (asistente personal)").build()
    ).execute().use { response ->
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
        mapper.readTree(response.body?.string().orEmpty())
    }

    private fun ipWhoIs(): ApproxLocation? {
        val json = get("https://ipwho.is/?lang=es")
        if (json["success"]?.asBoolean(false) != true) return null
        return ApproxLocation(
            json["city"]?.asText().orEmpty(), json["region"]?.asText().orEmpty(), json["country"]?.asText().orEmpty(),
            json["latitude"].asDouble(), json["longitude"].asDouble()
        )
    }

    private fun ipApiCo(): ApproxLocation? {
        val json = get("https://ipapi.co/json/")
        if (json["error"]?.asBoolean(false) == true || json["latitude"] == null) return null
        return ApproxLocation(
            json["city"]?.asText().orEmpty(), json["region"]?.asText().orEmpty(), json["country_name"]?.asText().orEmpty(),
            json["latitude"].asDouble(), json["longitude"].asDouble()
        )
    }
}

@Component
class GetMyLocationTool(private val ipLocation: IpLocation) : Tool {
    override val name = "getMyLocation"
    override val description =
        "Dice dónde está el usuario de forma aproximada (ciudad), a partir de la conexión a internet del PC. " +
            "Para '¿dónde estoy?' o antes de buscar algo 'cerca de mí' (luego webSearch con el nombre de la ciudad). " +
            "Si el usuario tiene su ciudad en la memoria, esa manda."
    override val riskLevel = RiskLevel.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf("type" to "object", "properties" to emptyMap<String, Any?>())

    override fun execute(args: Map<String, Any?>): String {
        val location = ipLocation.current() ?: return "No he podido averiguar dónde está (sin conexión). Pregúntaselo."
        return "Según la conexión a internet, el usuario está en o cerca de ${location.displayName} (es aproximado)."
    }
}

@Component
class ReadWebPageTool : Tool {
    override val name = "readWebPage"
    override val description =
        "Lee el texto de una página web a partir de su enlace (http/https). Úsala cuando el usuario pegue un " +
            "enlace y pida resumirlo o preguntar algo sobre él."
    override val riskLevel = RiskLevel.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf("url" to mapOf("type" to "string", "description" to "Enlace completo de la página.")),
        "required" to listOf("url")
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(20))
        .callTimeout(Duration.ofSeconds(25))
        .build()

    override fun execute(args: Map<String, Any?>): String {
        val url = (args["url"] as? String)?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return "El enlace no es válido."
        val html = try {
            http.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Apache/0.2")
                    .header("Accept-Language", "es-ES,es;q=0.9")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return "No he podido abrir la página (HTTP ${response.code})."
                response.body?.string().orEmpty()
            }
        } catch (e: Exception) {
            return "No he podido abrir la página (${e.message})."
        }
        val text = WebText.extract(html)
        return if (text.isBlank()) "La página no tiene texto que se pueda leer (puede que necesite iniciar sesión)."
        else "Texto de la página ($url):\n$text"
    }
}
