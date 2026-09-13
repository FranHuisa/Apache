package com.apache.database.repository

import com.apache.database.tables.Notifications
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository

/** Representación de una notificación obtenida desde la base de datos. */
data class NotificationRecord(
    val id: Long,
    val userId: Long,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val createdAt: LocalDateTime,
    val readAt: LocalDateTime?
)

/** Repository encargado de acceder a la tabla `notification`. */
@Repository
class NotificationRepository {

    fun create(
        userId: Long,
        type: String,
        title: String,
        message: String
    ): Long = transaction {

        Notifications.insert {
            it[Notifications.userId] = userId
            it[Notifications.type] = type
            it[Notifications.title] = title
            it[Notifications.message] = message
            it[Notifications.read] = false
            it[Notifications.createdAt] = LocalDateTime.now()
        } get Notifications.id
    }

    /** Notificaciones de un usuario, las más recientes primero. */
    fun findByUser(userId: Long, onlyUnread: Boolean = false): List<NotificationRecord> = transaction {

        Notifications
            .selectAll()
            .where {
                if (onlyUnread) {
                    (Notifications.userId eq userId) and (Notifications.read eq false)
                } else {
                    Notifications.userId eq userId
                }
            }
            .orderBy(Notifications.createdAt, SortOrder.DESC)
            .map { it.toRecord() }
    }

    fun markRead(notificationId: Long): Boolean = transaction {
        Notifications.update({ Notifications.id eq notificationId }) {
            it[Notifications.read] = true
            it[Notifications.readAt] = LocalDateTime.now()
        } > 0
    }

    /** Marca como leídas todas las notificaciones pendientes de un usuario. Devuelve cuántas afectó. */
    fun markAllRead(userId: Long): Int = transaction {
        Notifications.update({ (Notifications.userId eq userId) and (Notifications.read eq false) }) {
            it[Notifications.read] = true
            it[Notifications.readAt] = LocalDateTime.now()
        }
    }

    /**
     * Notificaciones ya leídas de un usuario (histórico), las más recientes primero.
     *
     * Se mantienen en la tabla en vez de borrarse físicamente: `readAt` solo
     * tiene sentido si conservamos la fila, y así el usuario puede consultar
     * más adelante qué recordatorios se han disparado.
     */
    fun findReadByUser(userId: Long): List<NotificationRecord> = transaction {
        Notifications
            .selectAll()
            .where { (Notifications.userId eq userId) and (Notifications.read eq true) }
            .orderBy(Notifications.createdAt, SortOrder.DESC)
            .map { it.toRecord() }
    }

    private fun ResultRow.toRecord() = NotificationRecord(
        id = this[Notifications.id],
        userId = this[Notifications.userId],
        type = this[Notifications.type],
        title = this[Notifications.title],
        message = this[Notifications.message],
        read = this[Notifications.read],
        createdAt = this[Notifications.createdAt],
        readAt = this[Notifications.readAt]
    )
}
