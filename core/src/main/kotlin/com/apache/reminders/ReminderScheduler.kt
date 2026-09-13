package com.apache.reminders

import com.apache.database.service.ReminderService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Comprueba periódicamente si hay recordatorios que ya han llegado a su `triggerAt` y, si es así,
 * delega en [ReminderService] para convertirlos en notificaciones.
 *
 * La comprobación cada 30 segundos es más que suficiente para un asistente personal de un único
 * usuario: no necesitamos precisión de segundos, y así evitamos abrir transacciones contra MySQL
 * constantemente.
 */
@Component
class ReminderScheduler(private val reminderService: ReminderService) {

    private val logger = LoggerFactory.getLogger(ReminderScheduler::class.java)

    @Scheduled(fixedDelay = 30_000)
    fun checkDueReminders() {
        try {
            val triggered = reminderService.processDueReminders()

            logger.info("Scheduler de recordatorios ejecutado. Encontrados: ${triggered.size}")

            if (triggered.isNotEmpty()) {
                logger.info(
                        "Disparados ${triggered.size} recordatorio(s): ${triggered.map { it.title }}"
                )
            }
        } catch (e: Exception) {
            // Un fallo puntual (ej. MySQL momentáneamente caído) no debe tumbar
            // el scheduler: se reintentará automáticamente en el siguiente tick.
            logger.error("Error comprobando recordatorios pendientes: ${e.message}", e)
        }
    }
}
