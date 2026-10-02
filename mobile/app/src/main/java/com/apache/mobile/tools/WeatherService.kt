package com.apache.mobile.tools

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tiempo actual resumido para la pantalla de Inicio. */
data class CurrentWeather(val place: String, val temperature: Int, val description: String, val emoji: String)

/** Consulta rápida del tiempo actual (Open-Meteo, sin API key). */
object WeatherService {

    suspend fun current(city: String): CurrentWeather? = withContext(Dispatchers.IO) {
        runCatching {
            val geo = Http.getJson(
                "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city, "UTF-8")}&count=1&language=es"
            ).optJSONArray("results")?.optJSONObject(0) ?: return@runCatching null

            val current = Http.getJson(
                "https://api.open-meteo.com/v1/forecast?latitude=${geo.getDouble("latitude")}" +
                    "&longitude=${geo.getDouble("longitude")}&current_weather=true&timezone=auto"
            ).getJSONObject("current_weather")

            val code = current.optInt("weathercode")
            val (description, emoji) = describe(code, current.optInt("is_day", 1) == 1)
            CurrentWeather(geo.optString("name", city), Math.round(current.optDouble("temperature")).toInt(), description, emoji)
        }.getOrNull()
    }

    private fun describe(code: Int, day: Boolean): Pair<String, String> = when (code) {
        0 -> "Despejado" to if (day) "☀️" else "🌙"
        1, 2 -> "Poco nuboso" to if (day) "🌤️" else "☁️"
        3 -> "Nublado" to "☁️"
        45, 48 -> "Niebla" to "🌫️"
        in 51..57 -> "Llovizna" to "🌦️"
        in 61..67, in 80..82 -> "Lluvia" to "🌧️"
        in 71..77, 85, 86 -> "Nieve" to "❄️"
        in 95..99 -> "Tormenta" to "⛈️"
        else -> "Variable" to "🌡️"
    }
}
