package com.apache.database.service

import com.apache.database.repository.ReminderRecord
import com.apache.database.repository.ReminderRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Capa de servicio de recordatorios. */
@Service
class ReminderService(
    private val reminderRepository: ReminderRepository,
    private val notificationService: NotificationService
) {

    private val logger = LoggerFactory.getLogger(ReminderService::class.java)

    fun createReminder(
        userId: Long,
        title: String,
        message: String? = null,
        triggerAt: LocalDateTime,
        eventId: Long? = null
    ): ReminderRecord {

        require(title.isNotBlank()) {
            "El título del recordatorio no puede estar vacío."
        }

        require(triggerAt.isAfter(LocalDateTime.now())) {
            "La fecha del recordatorio debe ser futura."
        }

        val id = reminderRepository.create(
            userId = userId,
            title = title,
            message = message,
            triggerAt = triggerAt,
            eventId = eventId
        )

        return reminderRepository.findById(id)
            ?: error("No se ha podido recuperar el recordatorio recién creado (id=$id).")
    }

    fun listReminders(userId: Long, includeFinished: Boolean = false): List<ReminderRecord> =
        reminderRepository.findByUser(userId, includeFinished)

    fun cancelReminder(reminderId: Long): Boolean =
        reminderRepository.cancel(reminderId)

    /**
     * Busca todos los recordatorios pendientes cuya hora ya ha llegado, genera
     * una notificación por cada uno y los marca como disparados.
     *
     * Llamado periódicamente por [com.apache.reminders.ReminderScheduler]. Vive
     * aquí (y no en el scheduler) para poder probarlo sin depender de Spring
     * `@Scheduled` ni de temporizadores reales.
     *
     * @return los recordatorios que se han disparado en esta pasada.
     */
    fun processDueReminders(now: LocalDateTime = LocalDateTime.now()): List<ReminderRecord> {

        val due = reminderRepository.findDuePending(now)

        due.forEach { reminder ->
            try {
                notificationService.create(
                    userId = reminder.userId,
                    title = reminder.title,
                    message = reminder.message ?: "Recordatorio: ${reminder.title}",
                    type = NotificationType.REMINDER
                )

                reminderRepository.markTriggered(reminder.id)
            } catch (e: Exception) {
                // Si falla uno, seguimos con el resto: un recordatorio roto no debe
                // bloquear la notificación de los demás. Se reintentará en el
                // siguiente "tick" porque no se ha marcado como triggered.
                logger.warn("No se pudo procesar el recordatorio id=${reminder.id}: ${e.message}")
            }
        }

        return due
    }
}
