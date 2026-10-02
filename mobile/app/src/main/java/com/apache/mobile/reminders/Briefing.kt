package com.apache.mobile.reminders

import com.apache.mobile.ApacheApp
import com.apache.mobile.tools.GeoLookup
import com.apache.mobile.tools.GetWeatherTool
import com.apache.mobile.tools.Http
import com.apache.mobile.tools.NewsFeed
import com.apache.mobile.tools.WeatherService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Resumen de buenos días listo para enseñar: título corto y texto. */
data class BriefingResult(val title: String, val text: String)

/**
 * Resumen de buenos días: agenda, tiempo, recordatorios, listas y titulares.
 * No usa Gemini (así llega aunque falle la API key o no haya cuota): junta los
 * datos directamente. El tiempo y las noticias se piden a la vez y, si tardan
 * demasiado, se omiten.
 */
object Briefing {

    private val HOUR = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun build(app: ApacheApp): BriefingResult = coroutineScope {
        val weatherJob = async {
            withTimeoutOrNull(15_000) {
                runCatching {
                    val city = withContext(Dispatchers.IO) { GetWeatherTool().homeCity() }
                    val saved = app.settings.lastLocation
                    when {
                        // En segundo plano no se puede pedir el GPS: última ubicación guardada.
                        saved != null -> WeatherService.reportAt(saved.latitude, saved.longitude, saved.place, 1)
                        city != null -> WeatherService.report(city, 1)
                        else -> null
                    }
                }.getOrNull()
            }
        }
        val newsJob = async {
            withTimeoutOrNull(12_000) {
                runCatching {
                    withContext(Dispatchers.IO) { NewsFeed.pick(NewsFeed.parse(Http.getText(NewsFeed.url(null))), 3, false) }
                }.getOrNull()
            }
        }

        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val (name, events, reminders, lists) = withContext(Dispatchers.IO) {
            Quad(
                app.memory.findByKey("nombre")?.value,
                app.events.onDay(today).filter { it.status == "confirmed" && (it.endAt ?: it.startAt).isAfter(now.minusMinutes(1)) },
                app.reminders.pending().filter { it.triggerAt.toLocalDate() == today },
                app.tasks.lists().filter { it.pending > 0 }
            )
        }

        val weather = weatherJob.await()
        val news = newsJob.await().orEmpty()

        val greeting = when (LocalTime.now().hour) {
            in 6..13 -> "Buenos días"
            in 14..20 -> "Buenas tardes"
            else -> "Buenas noches"
        } + (name?.let { ", $it" } ?: "")

        val weatherLine = weather?.let { report ->
            val (sky, emoji) = GeoLookup.describe(report.code, report.isDay)
            val todayForecast = report.days.firstOrNull()
            val range = todayForecast?.let { " (${Math.round(it.min)}–${Math.round(it.max)} °C" + (it.rain?.let { r -> ", lluvia $r%" } ?: "") + ")" }.orEmpty()
            "$emoji ${report.description ?: sky}, ${Math.round(report.temperature)} °C$range en ${report.place.substringBefore(',')}"
        }

        val text = buildString {
            weatherLine?.let { appendLine(it) }
            if (events.isEmpty()) appendLine("📅 Hoy no tienes nada en la agenda.")
            else {
                appendLine("📅 Hoy:")
                events.take(5).forEach { appendLine("  ${it.startAt.format(HOUR)} ${it.title}") }
                if (events.size > 5) appendLine("  y ${events.size - 5} más")
            }
            if (reminders.isNotEmpty()) appendLine("⏰ " + reminders.joinToString { "${it.title} (${it.triggerAt.format(HOUR)})" })
            if (lists.isNotEmpty()) appendLine("📝 Pendiente: " + lists.joinToString { "${it.name} (${it.pending})" })
            if (news.isNotEmpty()) {
                appendLine("📰 Titulares:")
                news.forEach { appendLine("  • ${it.title}" + (if (it.source.isNotBlank()) " (${it.source})" else "")) }
            }
        }.trim()

        BriefingResult(greeting + (weather?.let { " · ${Math.round(it.temperature)} °C" } ?: ""), text)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
