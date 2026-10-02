package com.apache.mobile.tools

import com.apache.mobile.ApacheApp
import com.apache.mobile.ai.GeminiClient
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiempo actual y previsión. Si no se dice la ciudad, usa la de la memoria.
 * Busca el sitio con cuidado (GeoLookup) y, si Open-Meteo falla, tira de wttr.in.
 */
class GetWeatherTool : Tool {

    override val name = "getWeather"

    override val description =
        "Consulta el tiempo: ahora, hoy, mañana o los próximos días (temperatura, lluvia, viento). " +
            "Úsala siempre que pregunten por el tiempo, la lluvia, si hace frío o si llevar paraguas. " +
            "Si el usuario no dice la ciudad, déjala vacía: se usa dónde está el móvil (o su ciudad guardada)."

    override val parameters = Schema.obj(
        "location" to Schema.string("Ciudad o pueblo, con provincia o país si hay duda, ej: 'Córdoba', 'Mérida, Badajoz'. Vacío = donde está ahora."),
        "days" to Schema.integer("Días de previsión incluyendo hoy (1-7). Por defecto 2 (hoy y mañana).")
    )

    override suspend fun execute(args: JSONObject): String {
        val days = (args.int("days") ?: 2).coerceIn(1, 7)
        val location = args.str("location")

        if (location == null) {
            // Sin ciudad: donde está el móvil y, si no, la ciudad de su memoria.
            val report = runCatching { WeatherService.home(days, homeCity()) }.getOrNull()
                ?: return "No sé dónde estás: no tengo permiso de ubicación ni su ciudad en la memoria. " +
                    "Pregúntale al usuario dónde vive y guárdalo con rememberFact (clave 'ciudad')."
            return WeatherService.format(report, days)
        }

        val report = try {
            WeatherService.report(location, days)
        } catch (e: Exception) {
            null
        } ?: return "No he podido consultar el tiempo de '$location' (no encuentro el sitio o no hay conexión). " +
            "Si el nombre es raro, pide al usuario la provincia o una ciudad cercana."

        return WeatherService.format(report, days)
    }

    fun homeCity(): String? = runCatching {
        val memory = ApacheApp.get().memory
        listOf("ciudad", "ubicación", "ubicacion", "vivo en", "localidad", "pueblo")
            .firstNotNullOfOrNull { memory.findByKey(it)?.value?.takeIf(String::isNotBlank) }
    }.getOrNull()
}

/** Titulares y noticias recientes (Google Noticias, sin API key). */
class GetNewsTool : Tool {

    override val name = "getNews"

    override val description =
        "Da las noticias de hoy: los titulares de España, una sección (deportes, economía, tecnología, " +
            "ciencia, salud, internacional, entretenimiento) o noticias recientes sobre un tema concreto " +
            "(un equipo, una persona, una ciudad...). Úsala cuando pidan noticias, titulares o qué ha pasado. " +
            "Para preguntas concretas que no son noticias, usa webSearch."

    override val parameters = Schema.obj(
        "topic" to Schema.string("Sección o tema, ej: 'deportes', 'tecnología', 'Real Madrid', 'Almería'. Vacío = titulares generales."),
        "count" to Schema.integer("Cuántas noticias (1-10). Por defecto 5.")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val topic = args.str("topic")
        val count = (args.int("count") ?: 5).coerceIn(1, 10)
        val xml = try {
            Http.getText(NewsFeed.url(topic))
        } catch (e: Exception) {
            return@withContext "No he podido conectar con Google Noticias. Si es importante, usa webSearch."
        }
        val items = NewsFeed.pick(NewsFeed.parse(xml), count, newestFirst = NewsFeed.isSearch(topic))
        NewsFeed.format(items, topic)
    }
}

/** Búsqueda en internet con Google, a través de Gemini (misma API key). */
class WebSearchTool(private val gemini: GeminiClient) : Tool {

    override val name = "webSearch"

    override val description =
        "Busca información actual en internet (Google): resultados, precios, horarios, " +
            "estrenos o cualquier dato que pueda haber cambiado o que no sepas seguro. No lo uses " +
            "para el tiempo (usa getWeather) ni para titulares (usa getNews)."

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

/** Dónde está el usuario ahora (GPS o red del móvil). */
class GetMyLocationTool : Tool {

    override val name = "getMyLocation"

    override val description =
        "Dice dónde está el usuario ahora (pueblo o ciudad y coordenadas) según el móvil. Úsala para " +
            "preguntas como '¿dónde estoy?' o antes de buscar algo 'cerca de mí' (luego usa webSearch " +
            "con el nombre del sitio, ej: 'farmacias de guardia en Almería')."

    override val parameters = Schema.obj()

    override suspend fun execute(args: JSONObject): String {
        val location = DeviceLocation.current()
            ?: return "No sé dónde está: el móvil no da la ubicación (permiso denegado o ubicación apagada). " +
                "Pídele que active la ubicación o que te diga dónde está."
        val coords = "%.4f, %.4f".format(java.util.Locale.US, location.latitude, location.longitude)
        return if (location.place != null) "El usuario está en ${location.place} (coordenadas $coords)."
        else "El usuario está en las coordenadas $coords (no sé el nombre del sitio)."
    }
}

/** Lee el texto de una página web (para "resúmeme este enlace"). */
class ReadWebPageTool : Tool {

    override val name = "readWebPage"

    override val description =
        "Lee el texto de una página web a partir de su enlace (http/https). Úsala cuando el usuario " +
            "comparta o pegue un enlace y pida resumirlo o preguntar algo sobre él."

    override val parameters = Schema.obj(
        "url" to Schema.string("Enlace completo de la página."),
        required = listOf("url")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val url = args.str("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return@withContext "El enlace no es válido."
        val html = try {
            Http.getText(url)
        } catch (e: Exception) {
            return@withContext "No he podido abrir la página (${e.message})."
        }
        val text = WebText.extract(html)
        if (text.isBlank()) "La página no tiene texto que se pueda leer (puede que necesite iniciar sesión)."
        else "Texto de la página ($url):\n$text"
    }
}
