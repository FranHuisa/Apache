package com.apache.api

import com.apache.ApacheDefaults
import com.apache.database.repository.NotificationRecord
import com.apache.database.service.NotificationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Expone las notificaciones generadas por Apache (hoy, sobre todo, las que
 * genera [com.apache.reminders.ReminderScheduler] al disparar un recordatorio)
 * para que la app de escritorio pueda consultarlas mediante *polling* y
 * mostrarlas al usuario.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    /** Lista las notificaciones del usuario. Por defecto, solo las no leídas. */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "true") onlyUnread: Boolean
    ): List<NotificationResponse> =
        notificationService
            .listNotifications(ApacheDefaults.DEFAULT_USER_ID, onlyUnread)
            .map { it.toResponse() }

    /**
     * Histórico de notificaciones ya leídas (por ejemplo, para un panel de
     * "notificaciones anteriores" en el Desktop). No hay borrado físico: ver
     * [com.apache.database.repository.NotificationRepository.findReadByUser].
     */
    @GetMapping("/read")
    fun listRead(): List<NotificationResponse> =
        notificationService
            .listRead(ApacheDefaults.DEFAULT_USER_ID)
            .map { it.toResponse() }

    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: Long): MarkReadResponse =
        MarkReadResponse(success = notificationService.markRead(id))

    @PostMapping("/read-all")
    fun markAllRead(): MarkReadResponse =
        MarkReadResponse(success = true, count = notificationService.markAllRead(ApacheDefaults.DEFAULT_USER_ID))
}

private fun NotificationRecord.toResponse() = NotificationResponse(
    id = id,
    type = type,
    title = title,
    message = message,
    read = read,
    createdAt = createdAt.toString()
)

data class NotificationResponse(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val createdAt: String
)

data class MarkReadResponse(
    val success: Boolean,
    val count: Int = if (success) 1 else 0
)
