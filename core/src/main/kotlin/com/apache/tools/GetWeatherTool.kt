package com.apache.tools

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component

/**
 * Tool de solo lectura que consulta el tiempo (actual y/o previsión) de una
 * localización cualquiera.
 *
 * Usa la API pública de Open-Meteo (https://open-meteo.com) porque no
 * requiere API key: para un prototipo evita tener que gestionar credenciales
 * y variables de entorno adicionales (a diferencia de Gemini, ver
 * application.yml). Si en el futuro se necesita más precisión/cobertura, se
 * puede sustituir por otro proveedor (AEMET, OpenWeather, etc.) sin tocar el
 * contrato de la tool: solo cambiaría la implementación de [fetchForecast] y
 * [geocode].
 *
 * El flujo es en dos pasos, como exige Open-Meteo:
 * 1. Geocoding: convierte el nombre de la localización ("Madrid", "Almería")
 *    en coordenadas (lat/lon) mediante su API de geocoding.
 * 2. Forecast: con esas coordenadas, pide el tiempo actual y, si se solicita,
 *    la previsión diaria de los próximos días.
 */
@Component
class GetWeatherTool : Tool {

    override val name = "getWeather"

    override val description =
        "Consulta el tiempo actual de una ciudad/localización y, opcionalmente, la previsión " +
            "meteorológica de los próximos días. Útil cuando el usuario pregunta qué tiempo hace, " +
            "si va a llover, la temperatura, o cómo estará el tiempo mañana o en los próximos días."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "location" to mapOf(
                "type" to "string",
                "description" to "Ciudad o localización a consultar, ej: 'Madrid', 'Almería', 'Nueva York'."
            ),
            "days" to mapOf(
                "type" to "integer",
                "description" to
                    "Número de días de previsión a futuro, incluyendo hoy. 1 = solo el tiempo actual " +
                        "de hoy. Hasta 7 días. Si no se especifica, se asume 1 (solo el tiempo actual)."
            )
        ),
        "required" to listOf("location")
    )

    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(10)).build()
    private val mapper = ObjectMapper()

    override fun execute(args: Map<String, Any?>): String {
        val location = (args["location"] as? String)?.trim()
            ?: return "No se ha especificado ninguna localización."

        if (location.isBlank()) {
            return "No se ha especificado ninguna localización."
        }

        // Gemini puede mandar "days" como Int, Double o String según cómo lo serialice; lo
        // normalizamos aceptando cualquiera de esas formas y lo acotamos a un rango razonable.
        val requestedDays = (args["days"] as? Number)?.toInt()
            ?: (args["days"] as? String)?.toIntOrNull()
            ?: 1
        val days = requestedDays.coerceIn(1, 7)

        val place = try {
            geocode(location)
        } catch (e: Exception) {
            return "No he podido buscar la localización '$location': ${e.message}"
        } ?: return "No he encontrado ninguna localización llamada '$location'."

        val forecast = try {
            fetchForecast(place.latitude, place.longitude, days)
        } catch (e: Exception) {
            return "No he podido obtener el tiempo para ${place.displayName}: ${e.message}"
        }

        return formatResponse(place, forecast, days)
    }

    // --- Geocoding ---------------------------------------------------------

    private data class Place(
        val displayName: String,
        val latitude: Double,
        val longitude: Double
    )

    private fun geocode(location: String): Place? {
        val encoded = URLEncoder.encode(location, StandardCharsets.UTF_8)
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=es"

        val json = get(url)
        val results = json["results"] ?: return null
        if (!results.isArray || results.isEmpty) return null

        val first = results[0]
        val name = first["name"]?.asText() ?: location
        val admin = first["admin1"]?.asText()
        val country = first["country"]?.asText()

        val displayName = listOfNotNull(name, admin, country).distinct().joinToString(", ")

        return Place(
            displayName = displayName,
            latitude = first["latitude"].asDouble(),
            longitude = first["longitude"].asDouble()
        )
    }

    // --- Forecast ------------------------------------------------------------

    private data class DailyForecast(
        val date: String,
        val weatherCode: Int,
        val tempMax: Double,
        val tempMin: Double,
        val precipitationProbability: Int?
    )

    private data class Forecast(
        val currentTemperature: Double,
        val currentWeatherCode: Int,
        val currentWindSpeed: Double,
        val daily: List<DailyForecast>
    )

    private fun fetchForecast(latitude: Double, longitude: Double, days: Int): Forecast {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current_weather=true" +
            "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=$days" +
            "&timezone=auto"

        val json = get(url)

        val current = json["current_weather"]
        val daily = json["daily"]

        val dates = daily["time"].map { it.asText() }
        val codes = daily["weathercode"].map { it.asInt() }
        val maxTemps = daily["temperature_2m_max"].map { it.asDouble() }
        val minTemps = daily["temperature_2m_min"].map { it.asDouble() }
        val precipitationProbabilities = daily["precipitation_probability_max"]?.map { it.asInt() }

        val dailyForecasts = dates.indices.map { i ->
            DailyForecast(
                date = dates[i],
                weatherCode = codes.getOrElse(i) { -1 },
                tempMax = maxTemps.getOrElse(i) { Double.NaN },
                tempMin = minTemps.getOrElse(i) { Double.NaN },
                precipitationProbability = precipitationProbabilities?.getOrNull(i)
            )
        }

        return Forecast(
            currentTemperature = current["temperature"].asDouble(),
            currentWeatherCode = current["weathercode"].asInt(),
            currentWindSpeed = current["windspeed"].asDouble(),
            daily = dailyForecasts
        )
    }

    private fun get(url: String): JsonNode {
        val request = Request.Builder().url(url).build()

        http.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IllegalStateException("Error ${response.code} llamando a la API del tiempo.")
            }

            return mapper.readTree(bodyText)
        }
    }

    // --- Presentación --------------------------------------------------------

    /**
     * Construye un texto claro y resumido para que Gemini lo use al componer su respuesta
     * final al usuario. No se le devuelve el JSON crudo: Gemini funciona mejor (y consume
     * menos tokens) con un resumen ya legible en lenguaje natural.
     */
    private fun formatResponse(place: Place, forecast: Forecast, days: Int): String {
        return buildString {
            appendLine("Tiempo en ${place.displayName}:")
            appendLine(
                "Ahora mismo: ${describeWeatherCode(forecast.currentWeatherCode)}, " +
                    "${formatTemp(forecast.currentTemperature)} · " +
                    "viento ${formatNumber(forecast.currentWindSpeed)} km/h."
            )

            if (days > 1 && forecast.daily.isNotEmpty()) {
                appendLine()
                appendLine("Previsión próximos días:")
                forecast.daily.forEach { day ->
                    val precipitationText = day.precipitationProbability
                        ?.let { " · prob. lluvia $it%" }
                        ?: ""

                    appendLine(
                        "- ${day.date}: ${describeWeatherCode(day.weatherCode)}, " +
                            "min ${formatTemp(day.tempMin)} / máx ${formatTemp(day.tempMax)}$precipitationText"
                    )
                }
            }
        }.trim()
    }

    private fun formatTemp(value: Double) = "${formatNumber(value)}°C"

    private fun formatNumber(value: Double): String =
        if (value.isNaN()) "N/D" else if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    /**
     * Traduce los códigos de tiempo estándar WMO (los que usa Open-Meteo) a una descripción
     * corta en español. La tabla completa está documentada en:
     * https://open-meteo.com/en/docs -> "WMO Weather interpretation codes"
     */
    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "cielo despejado"
        1 -> "mayormente despejado"
        2 -> "parcialmente nublado"
        3 -> "nublado"
        45, 48 -> "niebla"
        51, 53, 55 -> "llovizna"
        56, 57 -> "llovizna helada"
        61, 63, 65 -> "lluvia"
        66, 67 -> "lluvia helada"
        71, 73, 75 -> "nieve"
        77 -> "granos de nieve"
        80, 81, 82 -> "chubascos"
        85, 86 -> "chubascos de nieve"
        95 -> "tormenta"
        96, 99 -> "tormenta con granizo"
        else -> "condiciones desconocidas"
    }
}
