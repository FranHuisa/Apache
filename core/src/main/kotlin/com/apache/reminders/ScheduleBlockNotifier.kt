package com.apache.reminders

import com.apache.ApacheDefaults
import com.apache.database.service.CalendarService
import com.apache.database.service.NotificationService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Avisa cuando empieza un bloque del Horario (o cualquier evento del
 * calendario): crea una notificación que el Desktop muestra como aviso de
 * Windows, igual que los recordatorios.
 *
 * Cada 30 segundos mira qué eventos han empezado desde la comprobación
 * anterior. Al arrancar el Core se empieza a contar desde ese momento, así
 * que no se avisa de eventos que ya habían empezado antes.
 */
@Component
class ScheduleBlockNotifier(
    private val calendarService: CalendarService,
    private val notificationService: NotificationService
) {

    private val logger = LoggerFactory.getLogger(ScheduleBlockNotifier::class.java)

    private var lastCheck: LocalDateTime = LocalDateTime.now()

    /** Eventos ya avisados (id → inicio), para no repetir el aviso en el límite entre dos comprobaciones. */
    private val notified = mutableMapOf<Long, LocalDateTime>()

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    fun notifyStartingBlocks() {
        val now = LocalDateTime.now()

        try {
            val starting = calendarService
                .getEvents(ApacheDefaults.DEFAULT_USER_ID, lastCheck, now)
                .filter { it.status != "completed" && it.status != "cancelled" }
                .filter { notified[it.id] != it.startAt }

            starting.forEach { event ->
                val range = buildString {
                    append(event.startAt.format(HOUR))
                    event.endAt?.let { append(" – ${it.format(HOUR)}") }
                }
                val details = listOfNotNull(
                    range,
                    event.location?.takeIf { it.isNotBlank() },
                    event.description?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")

                notificationService.create(
                    userId = ApacheDefaults.DEFAULT_USER_ID,
                    title = "Empieza: ${event.title}",
                    message = details,
                    type = "schedule"
                )
                notified[event.id] = event.startAt
            }

            if (starting.isNotEmpty()) {
                logger.info("Avisados ${starting.size} bloque(s) del horario: ${starting.map { it.title }}")
            }

            // Limpieza: no hace falta recordar eventos de hace más de un día.
            notified.entries.removeIf { it.value.isBefore(now.minusDays(1)) }
            lastCheck = now
        } catch (e: Exception) {
            // Si MySQL falla, no avanzamos lastCheck: se reintentará en el siguiente tick.
            logger.error("Error comprobando bloques del horario: ${e.message}", e)
        }
    }

    private companion object {
        val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
