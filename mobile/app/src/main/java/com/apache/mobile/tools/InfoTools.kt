package com.apache.mobile.tools

import com.apache.mobile.ai.GeminiClient
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Tiempo actual y previsión (Open-Meteo, sin API key). Igual que en escritorio. */
class GetWeatherTool : Tool {

    override val name = "getWeather"

    override val description =
        "Consulta el tiempo actual de una ciudad y, opcionalmente, la previsión de los próximos días. " +
            "Si el usuario no dice la ciudad, usa la que tengas en su memoria."

    override val parameters = Schema.obj(
        "location" to Schema.string("Ciudad, ej: 'Madrid', 'Almería'."),
        "days" to Schema.integer("Días de previsión incluyendo hoy (1-7). Por defecto 1."),
        required = listOf("location")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val location = args.str("location") ?: return@withContext "¿De qué ciudad?"
        val days = (args.int("days") ?: 1).coerceIn(1, 7)

        val geo = Http.getJson(
            "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(location, "UTF-8")}&count=1&language=es"
        ).optJSONArray("results")?.optJSONObject(0)
            ?: return@withContext "No he encontrado ninguna localización llamada '$location'."

        val place = listOf(geo.optString("name"), geo.optString("admin1"), geo.optString("country"))
            .filter { it.isNotBlank() }.distinct().joinToString(", ")

        val json = Http.getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=${geo.getDouble("latitude")}" +
                "&longitude=${geo.getDouble("longitude")}&current_weather=true" +
                "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&forecast_days=$days&timezone=auto"
        )

        val current = json.getJSONObject("current_weather")
        buildString {
            appendLine("Tiempo en $place:")
            appendLine(
                "Ahora: ${describe(current.optInt("weathercode"))}, ${temp(current.optDouble("temperature"))}, " +
                    "viento ${current.optDouble("windspeed").toInt()} km/h."
            )
            if (days > 1) {
                val daily = json.getJSONObject("daily")
                val dates = daily.getJSONArray("time")
                for (i in 0 until dates.length()) {
                    val rain = daily.optJSONArray("precipitation_probability_max")?.optInt(i, -1) ?: -1
                    appendLine(
                        "- ${dates.getString(i)}: ${describe(daily.getJSONArray("weathercode").optInt(i))}, " +
                            "mín ${temp(daily.getJSONArray("temperature_2m_min").optDouble(i))} / " +
                            "máx ${temp(daily.getJSONArray("temperature_2m_max").optDouble(i))}" +
                            (if (rain >= 0) ", lluvia $rain%" else "")
                    )
                }
            }
        }.trim()
    }

    private fun temp(value: Double) = if (value.isNaN()) "N/D" else "${Math.round(value)} °C"

    private fun describe(code: Int): String = when (code) {
        0 -> "despejado"
        1 -> "mayormente despejado"
        2 -> "parcialmente nublado"
        3 -> "nublado"
        45, 48 -> "niebla"
        51, 53, 55 -> "llovizna"
        56, 57 -> "llovizna helada"
        61, 63, 65 -> "lluvia"
        66, 67 -> "lluvia helada"
        71, 73, 75, 77 -> "nieve"
        80, 81, 82 -> "chubascos"
        85, 86 -> "chubascos de nieve"
        95, 96, 99 -> "tormenta"
        else -> "tiempo variable"
    }
}

/** Búsqueda en internet con Google, a través de Gemini (misma API key). */
class WebSearchTool(private val gemini: GeminiClient) : Tool {

    override val name = "webSearch"

    override val description =
        "Busca información actual en internet (Google): noticias, resultados, precios, horarios, " +
            "estrenos o cualquier dato que pueda haber cambiado o que no sepas seguro. No lo uses " +
            "para el tiempo (usa getWeather)."

    override val parameters = Schema.obj(
        "query" to Schema.string("Qué buscar, como una pregunta completa."),
        required = listOf("query")
    )

    override suspend fun execute(args: JSONObject): String {
        val query = args.str("query") ?: return "No se ha indicado qué buscar."

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", "Busca en internet y responde en español, con datos concretos y fechas: $query")
                        )
                    )
                )
            )
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val response = gemini.generateRaw(body)
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: return "La búsqueda no ha devuelto resultados."

        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val answer = (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }.trim()
        if (answer.isBlank()) return "La búsqueda no ha devuelto resultados para '$query'."

        val chunks = candidate.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: JSONArray()
        val sources = (0 until chunks.length())
            .mapNotNull { chunks.optJSONObject(it)?.optJSONObject("web")?.optString("title")?.ifBlank { null } }
            .distinct()
            .take(5)

        return buildString {
            appendLine("Resultado de la búsqueda:")
            appendLine(answer)
            if (sources.isNotEmpty()) appendLine("Fuentes: ${sources.joinToString()}")
        }.trim()
    }
}
