package com.apache.tools

import java.text.Normalizer

/** Un resultado del buscador de lugares de Open-Meteo. */
data class GeoCandidate(
    val name: String,
    val admin1: String,
    val admin2: String,
    val country: String,
    val countryCode: String,
    val population: Long,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String
        get() = listOf(name, admin1, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/**
 * Decide qué buscar y con qué lugar quedarse para el tiempo. Antes se buscaba
 * el texto tal cual y se cogía el primer resultado, y por eso fallaba con
 * "Madrid, España", "el tiempo en Sevilla" o cogía Córdoba (Argentina).
 * Solo usa la librería estándar: el mismo archivo está en Apache de escritorio.
 */
object GeoLookup {

    private const val HOME_COUNTRY = "ES"

    private val PREFIXES = listOf("el tiempo en ", "tiempo en ", "clima en ", "en ", "la ciudad de ", "ciudad de ")

    /** Separa "Vélez-Málaga, Málaga" en (nombre, pista) y limpia lo que sobra. */
    fun split(query: String): Pair<String, String> {
        var text = query.trim().trimEnd('.', '?', '!', ' ')
        PREFIXES.forEach { if (text.lowercase().startsWith(it)) text = text.substring(it.length) }
        val paren = Regex("^(.*?)\\s*\\((.*?)\\)\\s*$").find(text)
        if (paren != null) return paren.groupValues[1].trim() to paren.groupValues[2].trim()
        val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return (parts.firstOrNull() ?: text) to parts.drop(1).joinToString(" ")
    }

    /** Textos a probar en el buscador, de más a menos exacto. */
    fun searchTerms(query: String): List<String> {
        val (name, _) = split(query)
        return listOf(name, stripAccents(name), name.replace('-', ' '))
            .map { it.trim() }.filter { it.length >= 2 }.distinct()
    }

    /**
     * Elige el lugar: el que encaje con la pista ("Málaga", "Argentina"); si no
     * hay pista, mejor uno de España salvo que el de fuera sea muchísimo mayor.
     */
    fun choose(candidates: List<GeoCandidate>, query: String): GeoCandidate? {
        if (candidates.isEmpty()) return null
        val (_, hint) = split(query)
        if (hint.isNotBlank()) {
            val wanted = normalize(hint)
            candidates.firstOrNull { c ->
                listOf(c.admin1, c.admin2, c.country, c.countryCode).any {
                    it.isNotBlank() && (normalize(it).contains(wanted) || wanted.contains(normalize(it)))
                }
            }?.let { return it }
        }
        val top = candidates.first()
        if (top.countryCode.equals(HOME_COUNTRY, ignoreCase = true)) return top
        val home = candidates.firstOrNull { it.countryCode.equals(HOME_COUNTRY, ignoreCase = true) }
        return if (home != null && (top.population == 0L || home.population * 20 >= top.population)) home else top
    }

    fun normalize(text: String): String = stripAccents(text).lowercase().trim()

    fun stripAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Códigos del tiempo (WMO) en español, con emoji. */
    fun describe(code: Int, day: Boolean = true): Pair<String, String> = when (code) {
        0 -> "despejado" to if (day) "☀️" else "🌙"
        1 -> "mayormente despejado" to if (day) "🌤️" else "🌙"
        2 -> "parcialmente nublado" to "⛅"
        3 -> "nublado" to "☁️"
        45, 48 -> "niebla" to "🌫️"
        51, 53, 55 -> "llovizna" to "🌦️"
        56, 57 -> "llovizna helada" to "🌧️"
        61, 63 -> "lluvia" to "🌧️"
        65 -> "lluvia fuerte" to "🌧️"
        66, 67 -> "lluvia helada" to "🌧️"
        71, 73, 75, 77 -> "nieve" to "❄️"
        80, 81 -> "chubascos" to "🌦️"
        82 -> "chubascos fuertes" to "⛈️"
        85, 86 -> "chubascos de nieve" to "🌨️"
        95 -> "tormenta" to "⛈️"
        96, 99 -> "tormenta con granizo" to "⛈️"
        else -> "variable" to "🌡️"
    }
}
