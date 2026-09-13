package com.apache.database.service

import com.apache.database.repository.NotificationRecord
import com.apache.database.repository.NotificationRepository
import org.springframework.stereotype.Service

/** Tipos de notificación que genera Apache. */
object NotificationType {
    const val REMINDER = "reminder"
    const val SYSTEM = "system"
}

/** Capa de servicio de notificaciones. */
@Service
class NotificationService(
    private val notificationRepository: NotificationRepository
) {

    fun create(
        userId: Long,
        title: String,
        message: String,
        type: String = NotificationType.SYSTEM
    ): NotificationRecord {

        require(title.isNotBlank()) {
            "El título de la notificación no puede estar vacío."
        }

        val id = notificationRepository.create(
            userId = userId,
            type = type,
            title = title,
            message = message
        )

        return notificationRepository.findByUser(userId).first { it.id == id }
    }

    fun listNotifications(userId: Long, onlyUnread: Boolean = false): List<NotificationRecord> =
        notificationRepository.findByUser(userId, onlyUnread)

    /** Histórico de notificaciones ya leídas de un usuario. */
    fun listRead(userId: Long): List<NotificationRecord> =
        notificationRepository.findReadByUser(userId)

    fun markRead(notificationId: Long): Boolean =
        notificationRepository.markRead(notificationId)

    fun markAllRead(userId: Long): Int =
        notificationRepository.markAllRead(userId)
}
