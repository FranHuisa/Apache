package com.apache.mobile.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.provider.AlarmClock
import com.apache.mobile.ApacheApp
import com.apache.mobile.tools.GetWeatherTool
import com.apache.mobile.tools.WeatherService
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Un aviso proactivo listo para enseñar. */
data class ProactiveNotice(
    val key: String,
    val title: String,
    val text: String,
    val alarmAt: LocalDateTime? = null
)

/**
 * Apache proactivo: cada ~2 horas cruza el tiempo, la agenda, las alarmas y
 * las listas y avisa si hay algo que merece la pena. Sin Gemini (gratis y
 * fiable), sin repetir avisos y nunca de noche.
 *
 * Avisos:
 *  - Va a llover cerca de la hora de un evento de las próximas 12 h.
 *  - Va a llover pronto (si no hay eventos), una vez al día.
 *  - Un evento con lugar empieza en ~1 h.
 *  - Mañana empiezas temprano y no hay ninguna alarma puesta (por la noche),
 *    con un botón para ponerla.
 *  - Llevas días con cosas en la lista de la compra.
 */
object Proactive {

    private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
    private const val RAIN_THRESHOLD = 60
    private const val QUIET_START = 23
    private const val QUIET_END = 8

    suspend fun check(app: ApacheApp, now: LocalDateTime = LocalDateTime.now()) {
        if (!app.settings.proactiveEnabled) return
        if (now.hour >= QUIET_START || now.hour < QUIET_END) return

        val notices = collect(app, now)
        // Como mucho dos avisos por comprobación, los más importantes primero.
        notices.filter { !app.settings.proactiveAlreadySent(it.key) }.take(2).forEachIndexed { index, notice ->
            app.settings.markProactiveSent(notice.key)
            Notifications.show(
                app, 3_200_000 + (notice.key.hashCode() and 0xFFFF) + index,
                Notifications.CHANNEL_PROACTIVE, notice.title, notice.text,
                action = notice.alarmAt?.let { alarmAction(app, it) }
            )
        }
    }

    /** Todos los avisos posibles ahora mismo (sin filtrar los ya enviados). */
    suspend fun collect(app: ApacheApp, now: LocalDateTime = LocalDateTime.now()): List<ProactiveNotice> {
        val today = now.toLocalDate()
        val notices = mutableListOf<ProactiveNotice>()

        val upcoming = withContext(Dispatchers.IO) {
            app.events.between(now, now.plusHours(12)).filter { it.status == "confirmed" }
        }

        // 1) Lluvia: con eventos, cerca de su hora; sin eventos, en las próximas 3 h.
        val rain = withTimeoutOrNull(15_000) { runCatching { rainForecast(app) }.getOrNull() }.orEmpty()
        if (rain.isNotEmpty()) {
            upcoming.forEach { event ->
                val around = rain.filter { (time, _) -> !time.isBefore(event.startAt.minusHours(1)) && !time.isAfter(event.startAt.plusHours(1)) }
                val worst = around.maxByOrNull { it.second }
                if (worst != null && worst.second >= RAIN_THRESHOLD) {
                    notices += ProactiveNotice(
                        "$today|rain|${event.id}",
                        "Coge paraguas ☂️",
                        "Va a llover sobre las ${worst.first.format(HOUR)} (${worst.second}%) y tienes «${event.title}» a las ${event.startAt.format(HOUR)}."
                    )
                }
            }
            if (upcoming.isEmpty() && now.hour in 8..20) {
                val soon = rain.filter { (time, _) -> time.isAfter(now) && time.isBefore(now.plusHours(3)) }.maxByOrNull { it.second }
                if (soon != null && soon.second >= 70) {
                    notices += ProactiveNotice(
                        "$today|rain",
                        "Va a llover ☂️",
                        "Lluvia sobre las ${soon.first.format(HOUR)} (${soon.second}%). Si vas a salir, coge paraguas."
                    )
                }
            }
        }

        // 2) Evento con lugar que empieza en ~1 h.
        upcoming.filter { it.location != null }.forEach { event ->
            val minutes = Duration.between(now, event.startAt).toMinutes()
            if (minutes in 40..100) {
                notices += ProactiveNotice(
                    "$today|soon|${event.id}",
                    "En ${if (minutes < 70) "una hora" else "hora y media"}: ${event.title}",
                    "A las ${event.startAt.format(HOUR)} en ${event.location}. Ve mirando cuándo salir."
                )
            }
        }

        // 3) Por la noche: mañana se empieza temprano y no hay alarma.
        if (now.hour in 20..22) {
            val tomorrow = today.plusDays(1)
            val first = withContext(Dispatchers.IO) {
                app.events.onDay(tomorrow).filter { it.status == "confirmed" }.minByOrNull { it.startAt }
            }
            if (first != null && first.startAt.hour < 10) {
                val nextAlarm = nextSystemAlarm(app)
                val hasAlarm = nextAlarm != null && nextAlarm.isBefore(first.startAt) && nextAlarm.isAfter(now)
                if (!hasAlarm) {
                    val suggested = first.startAt.minusHours(1)
                    notices += ProactiveNotice(
                        "$today|alarm",
                        "Mañana empiezas a las ${first.startAt.format(HOUR)} ⏰",
                        "Tienes «${first.title}» y no veo ninguna alarma puesta.",
                        alarmAt = suggested
                    )
                }
            }
        }

        // 4) La compra lleva días esperando (como mucho cada 3 días).
        val shopping = withContext(Dispatchers.IO) { app.tasks.pendingSince("compra") }
        if (shopping.isNotEmpty()) {
            val days = Duration.between(shopping.first().second, now).toDays()
            val recentlySent = (0L..2L).any { app.settings.proactiveAlreadySent("${today.minusDays(it)}|shopping") }
            if (days >= 3 && !recentlySent && now.hour in 10..20) {
                val names = shopping.take(4).joinToString(", ") { it.first.title } + if (shopping.size > 4) "…" else ""
                notices += ProactiveNotice(
                    "$today|shopping",
                    "La compra te espera 🛒",
                    "Llevas $days días con ${shopping.size} cosas apuntadas: $names."
                )
            }
        }

        return notices
    }

    /** Lluvia hora a hora donde está el usuario (última ubicación o su ciudad). */
    private suspend fun rainForecast(app: ApacheApp): List<Pair<LocalDateTime, Int>> {
        val saved = app.settings.lastLocation
        if (saved != null) return WeatherService.hourlyRain(saved.latitude, saved.longitude)
        val city = withContext(Dispatchers.IO) { GetWeatherTool().homeCity() } ?: return emptyList()
        val place = WeatherService.locate(city) ?: return emptyList()
        return WeatherService.hourlyRain(place.latitude, place.longitude)
    }

    /** Próxima alarma del reloj del sistema (la que el usuario tenga puesta). */
    private fun nextSystemAlarm(app: ApacheApp): LocalDateTime? = runCatching {
        val info = app.getSystemService(AlarmManager::class.java)?.nextAlarmClock ?: return null
        LocalDateTime.ofInstant(Instant.ofEpochMilli(info.triggerTime), ZoneId.systemDefault())
    }.getOrNull()

    /** Botón "Poner alarma a las 7:00" (abre el reloj con la alarma ya puesta). */
    private fun alarmAction(app: ApacheApp, at: LocalDateTime): Notifications.Action {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, at.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, at.minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Apache")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(app, 4_000_000, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notifications.Action("Poner alarma a las ${at.format(HOUR)}", pending)
    }
}
