package com.apache.mobile.tools

import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Tiempo actual resumido para la pantalla de Inicio. */
data class CurrentWeather(val place: String, val temperature: Int, val description: String, val emoji: String)

/** Un día de previsión. */
data class DayForecast(val date: LocalDate, val code: Int, val min: Double, val max: Double, val rain: Int?, val description: String? = null)

/** Informe completo: lo usa la herramienta getWeather. */
data class WeatherReport(
    val place: String,
    val temperature: Double,
    val feelsLike: Double?,
    val humidity: Int?,
    val wind: Double?,
    val code: Int,
    val isDay: Boolean,
    val description: String?,
    val days: List<DayForecast>,
    val source: String
)

/**
 * El tiempo, sin API key. Primero Open-Meteo; si no encuentra el sitio o no
 * responde, wttr.in como respaldo. Cada consulta se reintenta una vez.
 */
object WeatherService {

    private val ES = Locale.forLanguageTag("es-ES")

    /** Para Inicio: con [city] null usa la ubicación del móvil. */
    suspend fun current(city: String?): CurrentWeather? = runCatching {
        val report = (if (city != null) report(city, 1) else home(1, null)) ?: return null
        val (description, emoji) = GeoLookup.describe(report.code, report.isDay)
        CurrentWeather(
            report.place.substringBefore(','),
            Math.round(report.temperature).toInt(),
            (report.description ?: description).replaceFirstChar { it.titlecase(ES) },
            emoji
        )
    }.getOrNull()

    /** null si no se encuentra el sitio en ningún servicio. */
    suspend fun report(query: String, days: Int): WeatherReport? = withContext(Dispatchers.IO) {
        val fromOpenMeteo = runCatching { openMeteo(query, days) }.getOrNull()
        fromOpenMeteo ?: runCatching { wttr(query, days) }.getOrNull()
    }

    private suspend fun getJson(url: String): JSONObject {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                return Http.getJson(url)
            } catch (e: Exception) {
                last = e
                if (attempt == 0) delay(800)
            }
        }
        throw last ?: IllegalStateException("Sin respuesta")
    }

    /** Coordenadas de un sitio por su nombre (o null). */
    suspend fun locate(query: String): GeoCandidate? = withContext(Dispatchers.IO) {
        runCatching { geocode(query) }.getOrNull()
    }

    /**
     * Probabilidad de lluvia hora a hora (próximas 24 h) en unas coordenadas,
     * para los avisos proactivos.
     */
    suspend fun hourlyRain(latitude: Double, longitude: Double): List<Pair<java.time.LocalDateTime, Int>> =
        withContext(Dispatchers.IO) {
            val hourly = getJson(
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&hourly=precipitation_probability&forecast_days=2&timezone=auto"
            ).getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val rain = hourly.getJSONArray("precipitation_probability")
            (0 until times.length()).mapNotNull { i ->
                if (rain.isNull(i)) null else java.time.LocalDateTime.parse(times.getString(i)) to rain.optInt(i)
            }
        }

    private suspend fun geocode(query: String): GeoCandidate? {
        var candidates = emptyList<GeoCandidate>()
        for (term in GeoLookup.searchTerms(query)) {
            val results = getJson(
                "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(term, "UTF-8")}&count=10&language=es&format=json"
            ).optJSONArray("results") ?: continue
            candidates = (0 until results.length()).map { i ->
                val r = results.getJSONObject(i)
                GeoCandidate(
                    r.optString("name"), r.optString("admin1"), r.optString("admin2"), r.optString("country"),
                    r.optString("country_code"), r.optLong("population", 0L), r.getDouble("latitude"), r.getDouble("longitude")
                )
            }
            if (candidates.isNotEmpty()) break
        }
        return GeoLookup.choose(candidates, query)
    }

    private suspend fun openMeteo(query: String, days: Int): WeatherReport? {
        val place = geocode(query) ?: return null
        return forecast(place.latitude, place.longitude, place.displayName, days)
    }

    /** Tiempo en unas coordenadas (la ubicación del móvil). */
    suspend fun reportAt(latitude: Double, longitude: Double, place: String?, days: Int): WeatherReport? =
        withContext(Dispatchers.IO) {
            runCatching { forecast(latitude, longitude, place ?: "tu ubicación", days) }.getOrNull()
                ?: place?.let { runCatching { wttr(it, days) }.getOrNull() }
        }

    /** Tiempo del sitio del usuario: ubicación del móvil y, si no, la ciudad de su memoria. */
    suspend fun home(days: Int, homeCity: String?): WeatherReport? {
        val location = DeviceLocation.current()
        if (location != null) reportAt(location.latitude, location.longitude, location.place, days)?.let { return it }
        return homeCity?.let { report(it, days) }
    }

    private suspend fun forecast(latitude: Double, longitude: Double, placeName: String, days: Int): WeatherReport {
        val json = getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&forecast_days=${days.coerceIn(2, 7)}&timezone=auto"
        )
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val list = (0 until dates.length()).map { i ->
            DayForecast(
                LocalDate.parse(dates.getString(i)),
                daily.getJSONArray("weather_code").optInt(i, -1),
                daily.getJSONArray("temperature_2m_min").optDouble(i),
                daily.getJSONArray("temperature_2m_max").optDouble(i),
                daily.optJSONArray("precipitation_probability_max")?.let { if (it.isNull(i)) null else it.optInt(i) }
            )
        }
        return WeatherReport(
            place = placeName,
            temperature = current.getDouble("temperature_2m"),
            feelsLike = current.optDouble("apparent_temperature").takeUnless { it.isNaN() },
            humidity = current.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
            wind = current.optDouble("wind_speed_10m").takeUnless { it.isNaN() },
            code = current.optInt("weather_code", -1),
            isDay = current.optInt("is_day", 1) == 1,
            description = null,
            days = list,
            source = "Open-Meteo"
        )
    }

    /** Respaldo: wttr.in entiende casi cualquier nombre de sitio. */
    private suspend fun wttr(query: String, days: Int): WeatherReport? {
        val (name, hint) = GeoLookup.split(query)
        val term = listOf(name, hint).filter { it.isNotBlank() }.joinToString(",")
        val json = getJson("https://wttr.in/${URLEncoder.encode(term, "UTF-8").replace("+", "%20")}?format=j1&lang=es")
        val now = json.getJSONArray("current_condition").getJSONObject(0)
        val area = json.optJSONArray("nearest_area")?.optJSONObject(0)
        val areaName = area?.optJSONArray("areaName")?.optJSONObject(0)?.optString("value").orEmpty()
        val country = area?.optJSONArray("country")?.optJSONObject(0)?.optString("value").orEmpty()
        val description = now.optJSONArray("lang_es")?.optJSONObject(0)?.optString("value")
            ?: now.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value")
        val weather = json.optJSONArray("weather")
        val list = (0 until (weather?.length() ?: 0)).take(days.coerceIn(2, 3)).map { i ->
            val day = weather!!.getJSONObject(i)
            val hourly = day.optJSONArray("hourly")
            val rain = (0 until (hourly?.length() ?: 0)).maxOfOrNull { hourly!!.getJSONObject(it).optInt("chanceofrain", 0) }
            DayForecast(LocalDate.parse(day.getString("date")), -1, day.optDouble("mintempC"), day.optDouble("maxtempC"), rain)
        }
        return WeatherReport(
            place = listOf(areaName.ifBlank { name }, country).filter { it.isNotBlank() }.joinToString(", "),
            temperature = now.getString("temp_C").toDouble(),
            feelsLike = now.optString("FeelsLikeC").toDoubleOrNull(),
            humidity = now.optString("humidity").toIntOrNull(),
            wind = now.optString("windspeedKmph").toDoubleOrNull(),
            code = -1,
            isDay = true,
            description = description?.lowercase(ES),
            days = list,
            source = "wttr.in"
        )
    }

    /** Texto para Gemini. Siempre incluye hoy y mañana, aunque pida solo hoy. */
    fun format(report: WeatherReport, days: Int): String = buildString {
        fun t(value: Double) = if (value.isNaN()) "N/D" else "${Math.round(value)} °C"
        val now = report.description ?: GeoLookup.describe(report.code, report.isDay).first
        appendLine("Tiempo en ${report.place}:")
        append("Ahora: $now, ${t(report.temperature)}")
        report.feelsLike?.let { if (Math.round(it) != Math.round(report.temperature)) append(" (sensación ${t(it)})") }
        report.humidity?.let { append(", humedad $it%") }
        report.wind?.let { append(", viento ${Math.round(it)} km/h") }
        appendLine(".")
        val today = LocalDate.now()
        report.days.take(maxOf(days, 2)).forEach { day ->
            val label = when (day.date) {
                today -> "Hoy"
                today.plusDays(1) -> "Mañana"
                else -> day.date.dayOfWeek.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.titlecase(ES) } + " ${day.date.dayOfMonth}"
            }
            val sky = day.description ?: if (day.code >= 0) GeoLookup.describe(day.code).first else null
            append("- $label: ")
            if (sky != null) append("$sky, ")
            append("mín ${t(day.min)} / máx ${t(day.max)}")
            day.rain?.let { append(", lluvia $it%") }
            appendLine()
        }
        append("(Fuente: ${report.source})")
    }
}
