package com.apache.mobile.data

import java.time.LocalDateTime

/** Recordatorios. El aviso lo programa [com.apache.mobile.reminders.AlarmScheduler]. */
class ReminderStore(private val db: ApacheDatabase) {

    fun create(title: String, triggerAt: LocalDateTime): Reminder {
        require(title.isNotBlank()) { "El recordatorio necesita un texto." }
        val id = db.writableDatabase.insert(
            "reminder", null,
            contentValues("title" to title.trim(), "trigger_at" to triggerAt.withNano(0).toString())
        )
        return find(id) ?: error("No se ha podido guardar el recordatorio.")
    }

    fun find(id: Long): Reminder? =
        db.readableDatabase.rawQuery("SELECT * FROM reminder WHERE id = ?", arrayOf(id.toString()))
            .mapRows { it.toReminder() }.firstOrNull()

    fun pending(): List<Reminder> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM reminder WHERE status = 'pending' ORDER BY trigger_at", null
        ).mapRows { it.toReminder() }

    fun setStatus(id: Long, status: String): Boolean =
        db.writableDatabase.update("reminder", contentValues("status" to status), "id = ?", arrayOf(id.toString())) > 0

    private fun android.database.Cursor.toReminder() = Reminder(
        id = long("id"),
        title = string("title"),
        triggerAt = LocalDateTime.parse(string("trigger_at")),
        status = string("status")
    )
}
