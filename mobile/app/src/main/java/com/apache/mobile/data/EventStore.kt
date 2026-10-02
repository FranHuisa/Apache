package com.apache.mobile.data

import java.time.LocalDate
import java.time.LocalDateTime

/** Calendario y bloques del horario (un bloque es un evento con hora de fin). */
class EventStore(private val db: ApacheDatabase) {

    /** Eventos no cancelados que empiezan entre [from] y [to], ordenados. */
    fun between(from: LocalDateTime, to: LocalDateTime): List<CalendarEvent> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM event WHERE start_at >= ? AND start_at <= ? AND status != 'cancelled' ORDER BY start_at",
            arrayOf(from.withNano(0).toString(), to.withNano(0).toString())
        ).mapRows { it.toEvent() }

    fun onDay(date: LocalDate): List<CalendarEvent> = between(date.atStartOfDay(), date.atTime(23, 59, 59))

    fun find(id: Long): CalendarEvent? =
        db.readableDatabase.rawQuery("SELECT * FROM event WHERE id = ?", arrayOf(id.toString()))
            .mapRows { it.toEvent() }.firstOrNull()

    fun create(
        title: String,
        startAt: LocalDateTime,
        endAt: LocalDateTime? = null,
        description: String? = null,
        location: String? = null
    ): CalendarEvent {
        require(title.isNotBlank()) { "El evento necesita un título." }
        if (endAt != null) require(!endAt.isBefore(startAt)) { "La hora de fin no puede ser anterior a la de inicio." }

        val id = db.writableDatabase.insert(
            "event", null,
            contentValues(
                "title" to title.trim(),
                "description" to description?.trim()?.ifBlank { null },
                "location" to location?.trim()?.ifBlank { null },
                "start_at" to startAt.withNano(0).toString(),
                "end_at" to endAt?.withNano(0)?.toString()
            )
        )
        return find(id) ?: error("No se ha podido guardar el evento.")
    }

    fun update(
        id: Long,
        title: String? = null,
        startAt: LocalDateTime? = null,
        endAt: LocalDateTime? = null,
        description: String? = null,
        location: String? = null
    ): CalendarEvent? {
        val values = contentValues()
        title?.let { values.put("title", it.trim()) }
        startAt?.let { values.put("start_at", it.withNano(0).toString()) }
        endAt?.let { values.put("end_at", it.withNano(0).toString()) }
        description?.let { values.put("description", it.trim()) }
        location?.let { values.put("location", it.trim()) }
        if (values.size() > 0) db.writableDatabase.update("event", values, "id = ?", arrayOf(id.toString()))
        return find(id)
    }

    fun setStatus(id: Long, status: String): Boolean =
        db.writableDatabase.update("event", contentValues("status" to status), "id = ?", arrayOf(id.toString())) > 0

    private fun android.database.Cursor.toEvent() = CalendarEvent(
        id = long("id"),
        title = string("title"),
        description = stringOrNull("description"),
        location = stringOrNull("location"),
        startAt = LocalDateTime.parse(string("start_at")),
        endAt = stringOrNull("end_at")?.let { LocalDateTime.parse(it) },
        status = string("status")
    )
}
