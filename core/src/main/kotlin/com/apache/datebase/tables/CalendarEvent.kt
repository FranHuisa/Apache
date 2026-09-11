package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Evento perteneciente a un calendario.
 *
 * Un evento representa algo que ocurre en una fecha y hora concreta,
 * como una reunión, una cita o cualquier otro compromiso.
 */
object CalendarEvents : Table("calendar_event") {

    val id = long("id").autoIncrement()

    val calendarId = long("calendar_id")

    val title = varchar("title", 255)

    val description = text("description").nullable()

    val startAt = datetime("start_at")

    val endAt = datetime("end_at").nullable()

    val location = varchar("location", 255).nullable()

    val allDay = bool("all_day").default(false)

    val status = varchar("status", 30)

    val recurrenceRule = text("recurrence_rule").nullable()

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}