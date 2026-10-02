package com.apache.tools

import com.apache.ApacheDefaults
import com.apache.database.service.UserMemoryService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component

/**
 * Tool de solo lectura que consulta el tiempo (actual y previsión).
 *
 * Fallaba mucho porque buscaba el texto tal cual ("Madrid, España" no da
 * resultados) y se quedaba con el primero (Córdoba de Argentina). Ahora:
 * - [GeoLookup] limpia el nombre, prueba variantes y elige bien el sitio
 *   (la provincia/país si se indica; si no, mejor uno de España).
 * - Cada petición se reintenta una vez y los timeouts son más largos.
 * - Si Open-Meteo falla, se usa wttr.in como respaldo.
 * - Sin ciudad, usa la de la memoria del usuario.
 */
@Component
class GetWeatherTool(
    private val memoryService: UserMemoryService
) : Tool {

    override val name = "getWeather"

    override val description =
        "Consulta el tiempo: ahora, hoy, mañana o los próximos días (temperatura, lluvia, viento). " +
            "Úsala siempre que pregunten por el tiempo, la lluvia, si hace frío o si llevar paraguas. " +
            "Si el usuario no dice la ciudad, déjala vacía y se usará la de su memoria."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "location" to mapOf(
                "type" to "string",
                "description" to "Ciudad o pueblo, con provincia o país si hay duda, ej: 'Córdoba', " +
                    "'Mérida, Badajoz'. Vacío = la ciudad del usuario."
            ),
            "days" to mapOf(
                "type" to "integer",
                "description" to "Días de previsión incluyendo hoy (1-7). Por defecto 2 (hoy y mañana)."
            )
        )
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(15))
        .callTimeout(Duration.ofSeconds(20))
        .build()
    private val mapper = ObjectMapper()
    private val es = Locale.forLanguageTag("es-ES")

    private data class Day(val date: LocalDate, val code: Int, val min: Double, val max: Double, val rain: Int?)

    private data class Report(
        val place: String,
        val temperature: Double,
        val feelsLike: Double?,
        val humidity: Int?,
        val wind: Double?,
        val description: String,
        val days: List<Day>,
        val source: String
    )

    override fun execute(args: Map<String, Any?>): String {
        val location = (args["location"] as? String)?.trim()?.ifBlank { null } ?: homeCity()
            ?: return "No sé de qué ciudad. Pregúntale al usuario dónde vive y guárdalo con rememberFact (clave 'ciudad')."

        val days = ((args["days"] as? Number)?.toInt() ?: (args["days"] as? String)?.toIntOrNull() ?: 2).coerceIn(1, 7)

        val report = runCatching { openMeteo(location, days) }.getOrNull()
            ?: runCatching { wttr(location, days) }.getOrNull()
            ?: return "No he podido consultar el tiempo de '$location' (no encuentro el sitio o no hay conexión). " +
                "Si el nombre es raro, pide al usuario la provincia o una ciudad cercana."

        return format(report, days)
    }

    private fun homeCity(): String? = runCatching {
        val facts = memoryService.list(ApacheDefaults.DEFAULT_USER_ID)
        listOf("ciudad", "ubicación", "ubicacion", "vivo en", "localidad", "pueblo").firstNotNullOfOrNull { key ->
            facts.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.takeIf(String::isNotBlank)
        }
    }.getOrNull()

    // --- Open-Meteo ----------------------------------------------------------

    private fun openMeteo(query: String, days: Int): Report? {
        var candidates = emptyList<GeoCandidate>()
        for (term in GeoLookup.searchTerms(query)) {
            val results = get(
                "https://geocoding-api.open-meteo.com/v1/search?name=${encode(term)}&count=10&language=es&format=json"
            )["results"]
            if (results == null || !results.isArray || results.isEmpty) continue
            candidates = results.map { r ->
                GeoCandidate(
                    r.text("name"), r.text("admin1"), r.text("admin2"), r.text("country"), r.text("country_code"),
                    r["population"]?.asLong() ?: 0L, r["latitude"].asDouble(), r["longitude"].asDouble()
                )
            }
            break
        }
        val place = GeoLookup.choose(candidates, query) ?: return null

        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&forecast_days=${days.coerceIn(2, 7)}&timezone=auto"
        )
        val current = json["current"]
        val daily = json["daily"]
        val dates = daily["time"].map { it.asText() }
        val list = dates.indices.map { i ->
            Day(
                LocalDate.parse(dates[i]),
                daily["weather_code"]?.get(i)?.asInt(-1) ?: -1,
                daily["temperature_2m_min"]?.get(i)?.asDouble(Double.NaN) ?: Double.NaN,
                daily["temperature_2m_max"]?.get(i)?.asDouble(Double.NaN) ?: Double.NaN,
                daily["precipitation_probability_max"]?.get(i)?.takeUnless { it.isNull }?.asInt()
            )
        }
        val code = current["weather_code"]?.asInt(-1) ?: -1
        return Report(
            place = place.displayName,
            temperature = current["temperature_2m"].asDouble(),
            feelsLike = current["apparent_temperature"]?.asDouble(),
            humidity = current["relative_humidity_2m"]?.asInt(),
            wind = current["wind_speed_10m"]?.asDouble(),
            description = GeoLookup.describe(code, (current["is_day"]?.asInt(1) ?: 1) == 1).first,
            days = list,
            source = "Open-Meteo"
        )
    }

    // --- Respaldo: wttr.in ---------------------------------------------------

    private fun wttr(query: String, days: Int): Report {
        val (name, hint) = GeoLookup.split(query)
        val term = listOf(name, hint).filter { it.isNotBlank() }.joinToString(",")
        val json = get("https://wttr.in/${encode(term).replace("+", "%20")}?format=j1&lang=es")
        val now = json["current_condition"][0]
        val area = json["nearest_area"]?.get(0)
        val areaName = area?.get("areaName")?.get(0)?.text("value").orEmpty()
        val country = area?.get("country")?.get(0)?.text("value").orEmpty()
        val description = (now["lang_es"]?.get(0)?.text("value")?.ifBlank { null }
            ?: now["weatherDesc"]?.get(0)?.text("value").orEmpty()).lowercase(es)
        val list = (json["weather"]?.toList() ?: emptyList()).take(days.coerceIn(2, 3)).map { day ->
            val rain = day["hourly"]?.maxOfOrNull { it.text("chanceofrain").toIntOrNull() ?: 0 }
            Day(
                LocalDate.parse(day.text("date")), -1,
                day.text("mintempC").toDoubleOrNull() ?: Double.NaN,
                day.text("maxtempC").toDoubleOrNull() ?: Double.NaN,
                rain
            )
        }
        return Report(
            place = listOf(areaName.ifBlank { name }, country).filter { it.isNotBlank() }.joinToString(", "),
            temperature = now.text("temp_C").toDouble(),
            feelsLike = now.text("FeelsLikeC").toDoubleOrNull(),
            humidity = now.text("humidity").toIntOrNull(),
            wind = now.text("windspeedKmph").toDoubleOrNull(),
            description = description.ifBlank { "variable" },
            days = list,
            source = "wttr.in"
        )
    }

    // --- Utilidades ------------------------------------------------------------

    private fun JsonNode.text(field: String): String = this[field]?.asText().orEmpty()

    private fun encode(text: String) = URLEncoder.encode(text, StandardCharsets.UTF_8)

    /** GET con un reintento. */
    private fun get(url: String): JsonNode {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                val request = Request.Builder().url(url).header("User-Agent", "Apache/0.2 (asistente personal)").build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("Error ${response.code} de la API del tiempo.")
                    return mapper.readTree(body)
                }
            } catch (e: Exception) {
                last = e
                if (attempt == 0) Thread.sleep(800)
            }
        }
        throw last ?: IllegalStateException("Sin respuesta")
    }

    /** Texto para Gemini. Siempre incluye hoy y mañana. */
    private fun format(report: Report, days: Int): String = buildString {
        fun t(value: Double) = if (value.isNaN()) "N/D" else "${Math.round(value)} °C"
        appendLine("Tiempo en ${report.place}:")
        append("Ahora: ${report.description}, ${t(report.temperature)}")
        report.feelsLike?.let { if (Math.round(it) != Math.round(report.temperature)) append(" (sensación ${t(it)})") }
        report.humidity?.let { append(", humedad $it%") }
        report.wind?.let { append(", viento ${Math.round(it)} km/h") }
        appendLine(".")
        val today = LocalDate.now()
        report.days.take(maxOf(days, 2)).forEach { day ->
            val label = when (day.date) {
                today -> "Hoy"
                today.plusDays(1) -> "Mañana"
                else -> day.date.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.titlecase(es) } +
                    " ${day.date.dayOfMonth}"
            }
            append("- $label: ")
            if (day.code >= 0) append("${GeoLookup.describe(day.code).first}, ")
            append("mín ${t(day.min)} / máx ${t(day.max)}")
            day.rain?.let { append(", lluvia $it%") }
            appendLine()
        }
        append("(Fuente: ${report.source})")
    }
}
