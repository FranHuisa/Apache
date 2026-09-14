package com.apache.database.repository

import com.apache.database.tables.CalendarEvents
import com.apache.database.tables.Calendars

import java.time.LocalDateTime

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

import org.springframework.stereotype.Repository

/**
 * Representación de un evento de calendario obtenida desde la base de datos.
 *
 * No es una tabla Exposed: es el objeto que usamos para transportar los datos.
 */
data class CalendarEventRecord(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val description: String?,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime?,
    val location: String?,
    val allDay: Boolean,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * Repository encargado de acceder a las tablas `calendar` y `calendar_event`.
 *
 * Como Apache 0.1 no tiene todavía gestión de múltiples calendarios desde la
 * interfaz, cada usuario tiene un único calendario "principal" que se crea
 * automáticamente la primera vez que hace falta (mismo patrón que
 * `MemoryRepository.getOrCreateConversation`).
 */
@Repository
class CalendarRepository {

    /** Obtiene el calendario principal del usuario, o lo crea si todavía no existe. */
    fun getOrCreateDefaultCalendar(userId: Long): Long = transaction {

        val existing = Calendars
            .selectAll()
            .where {
                (Calendars.userId eq userId) and
                    (Calendars.active eq true)
            }
            .orderBy(Calendars.id, SortOrder.ASC)
            .firstOrNull()

        if (existing != null) {
            return@transaction existing[Calendars.id]
        }

        val now = LocalDateTime.now()

        Calendars.insert {
            it[Calendars.userId] = userId
            it[Calendars.name] = "Principal"
            it[Calendars.timezone] = "Europe/Madrid"
            it[Calendars.active] = true
            it[Calendars.createdAt] = now
            it[Calendars.updatedAt] = now
        } get Calendars.id
    }

    fun createEvent(
        calendarId: Long,
        title: String,
        description: String?,
        startAt: LocalDateTime,
        endAt: LocalDateTime?,
        location: String?,
        allDay: Boolean
    ): Long = transaction {

        val now = LocalDateTime.now()

        CalendarEvents.insert {
            it[CalendarEvents.calendarId] = calendarId
            it[CalendarEvents.title] = title
            it[CalendarEvents.description] = description
            it[CalendarEvents.startAt] = startAt
            it[CalendarEvents.endAt] = endAt
            it[CalendarEvents.location] = location
            it[CalendarEvents.allDay] = allDay
            it[CalendarEvents.status] = "confirmed"
            it[CalendarEvents.createdAt] = now
            it[CalendarEvents.updatedAt] = now
        } get CalendarEvents.id
    }

    /**
     * Eventos de un calendario cuyo `startAt` cae dentro de [from]..[to],
     * ordenados cronológicamente.
     */
    fun findEventsBetween(
        calendarId: Long,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<CalendarEventRecord> = transaction {

        CalendarEvents
            .selectAll()
            .where {
                (CalendarEvents.calendarId eq calendarId) and
                    (CalendarEvents.startAt greaterEq from) and
                    (CalendarEvents.startAt lessEq to) and
                    (CalendarEvents.status neq "cancelled")
            }
            .orderBy(CalendarEvents.startAt, SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun findEventById(eventId: Long): CalendarEventRecord? = transaction {

        CalendarEvents
            .selectAll()
            .where {
                CalendarEvents.id eq eventId
            }
            .singleOrNull()
            ?.toRecord()
    }

    /**
     * Actualiza solo los campos no nulos que se pasen;
     * el resto se deja como estaba.
     */
    fun updateEvent(
        eventId: Long,
        title: String?,
        description: String?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        location: String?
    ): Boolean = transaction {

        val current = CalendarEvents
            .selectAll()
            .where {
                CalendarEvents.id eq eventId
            }
            .singleOrNull()
            ?: return@transaction false

        CalendarEvents.update({
            CalendarEvents.id eq eventId
        }) {
            it[CalendarEvents.title] =
                title ?: current[CalendarEvents.title]

            it[CalendarEvents.description] =
                description ?: current[CalendarEvents.description]

            it[CalendarEvents.startAt] =
                startAt ?: current[CalendarEvents.startAt]

            it[CalendarEvents.endAt] =
                endAt ?: current[CalendarEvents.endAt]

            it[CalendarEvents.location] =
                location ?: current[CalendarEvents.location]

            it[CalendarEvents.updatedAt] =
                LocalDateTime.now()
        } > 0
    }

    /**
     * Cancela un evento (borrado lógico) en lugar de eliminar la fila.
     */
    fun cancelEvent(eventId: Long): Boolean = transaction {

        CalendarEvents.update({
            CalendarEvents.id eq eventId
        }) {
            it[CalendarEvents.status] = "cancelled"
            it[CalendarEvents.updatedAt] = LocalDateTime.now()
        } > 0
    }

    /**
     * Marca un evento como completado.
     */
    fun completeEvent(eventId: Long): Boolean = transaction {

        CalendarEvents.update({
            CalendarEvents.id eq eventId
        }) {
            it[CalendarEvents.status] = "completed"
            it[CalendarEvents.updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun deleteEvent(eventId: Long): Boolean = transaction {

        CalendarEvents.deleteWhere(
            op = {
                it.run {
                    CalendarEvents.id eq eventId
                }
            }
        ) > 0
    }

    private fun ResultRow.toRecord() = CalendarEventRecord(
        id = this[CalendarEvents.id],
        calendarId = this[CalendarEvents.calendarId],
        title = this[CalendarEvents.title],
        description = this[CalendarEvents.description],
        startAt = this[CalendarEvents.startAt],
        endAt = this[CalendarEvents.endAt],
        location = this[CalendarEvents.location],
        allDay = this[CalendarEvents.allDay],
        status = this[CalendarEvents.status],
        createdAt = this[CalendarEvents.createdAt],
        updatedAt = this[CalendarEvents.updatedAt]
    )
}
