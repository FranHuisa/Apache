package com.apache.database.repository

import com.apache.database.tables.Reminders
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository

/**
 * Estados posibles de un recordatorio. Se guardan como texto en la
 * columna `status` de la tabla `reminder` (ver [Reminders]).
 */
object ReminderStatus {
    /** Todavía no ha llegado su `triggerAt`. */
    const val PENDING = "pending"

    /** Ya se ejecutó: el [com.apache.reminders.ReminderScheduler] generó su notificación. */
    const val TRIGGERED = "triggered"

    /** Cancelado manualmente antes de dispararse. */
    const val CANCELLED = "cancelled"
}

/** Representación de un recordatorio obtenida desde la base de datos. */
data class ReminderRecord(
    val id: Long,
    val userId: Long,
    val eventId: Long?,
    val title: String,
    val message: String?,
    val triggerAt: LocalDateTime,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/** Repository encargado de acceder a la tabla `reminder`. */
@Repository
class ReminderRepository {

    fun create(
        userId: Long,
        title: String,
        message: String?,
        triggerAt: LocalDateTime,
        eventId: Long? = null
    ): Long = transaction {

        val now = LocalDateTime.now()

        Reminders.insert {
            it[Reminders.userId] = userId
            it[Reminders.eventId] = eventId
            it[Reminders.title] = title
            it[Reminders.message] = message
            it[Reminders.triggerAt] = triggerAt
            it[Reminders.status] = ReminderStatus.PENDING
            it[Reminders.createdAt] = now
            it[Reminders.updatedAt] = now
        } get Reminders.id
    }

    fun findByUser(userId: Long, includeFinished: Boolean = false): List<ReminderRecord> = transaction {

        Reminders
            .selectAll()
            .where {
                if (includeFinished) {
                    Reminders.userId eq userId
                } else {
                    (Reminders.userId eq userId) and (Reminders.status eq ReminderStatus.PENDING)
                }
            }
            .orderBy(Reminders.triggerAt, SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun findById(reminderId: Long): ReminderRecord? = transaction {
        Reminders
            .selectAll()
            .where { Reminders.id eq reminderId }
            .singleOrNull()
            ?.toRecord()
    }

    /**
     * Recordatorios cuyo `triggerAt` ya ha pasado y siguen en estado PENDING.
     *
     * Usado por [com.apache.reminders.ReminderScheduler] en cada "tick" para
     * saber cuáles tiene que disparar en forma de notificación.
     */
    fun findDuePending(now: LocalDateTime): List<ReminderRecord> = transaction {
        Reminders
            .selectAll()
            .where {
                (Reminders.status eq ReminderStatus.PENDING) and
                    (Reminders.triggerAt lessEq now)
            }
            .orderBy(Reminders.triggerAt, SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun markTriggered(reminderId: Long): Boolean = transaction {
        Reminders.update({ Reminders.id eq reminderId }) {
            it[Reminders.status] = ReminderStatus.TRIGGERED
            it[Reminders.updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun cancel(reminderId: Long): Boolean = transaction {
        Reminders.update({ Reminders.id eq reminderId }) {
            it[Reminders.status] = ReminderStatus.CANCELLED
            it[Reminders.updatedAt] = LocalDateTime.now()
        } > 0
    }

    private fun ResultRow.toRecord() = ReminderRecord(
        id = this[Reminders.id],
        userId = this[Reminders.userId],
        eventId = this[Reminders.eventId],
        title = this[Reminders.title],
        message = this[Reminders.message],
        triggerAt = this[Reminders.triggerAt],
        status = this[Reminders.status],
        createdAt = this[Reminders.createdAt],
        updatedAt = this[Reminders.updatedAt]
    )
}
