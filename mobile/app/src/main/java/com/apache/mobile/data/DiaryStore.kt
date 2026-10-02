package com.apache.mobile.data

import java.text.Normalizer
import java.time.LocalDate

/** Diario: una entrada por día con lo que el usuario cuenta y su ánimo. */
class DiaryStore(private val db: ApacheDatabase) {

    /** Guarda la entrada del día; si ya había, añade lo nuevo al final. */
    fun save(day: LocalDate, text: String, mood: String?, append: Boolean = true): DiaryEntry {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "La entrada del diario está vacía." }
        val existing = find(day)
        val finalText = if (append && existing != null && !existing.text.contains(clean)) "${existing.text}\n$clean" else clean
        db.writableDatabase.insertWithOnConflict(
            "diary", null,
            contentValues("day" to day.toString(), "text" to finalText, "mood" to (mood ?: existing?.mood), "updated_at" to ApacheDatabase.now()),
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        return DiaryEntry(day, finalText, mood ?: existing?.mood)
    }

    fun find(day: LocalDate): DiaryEntry? =
        db.readableDatabase.rawQuery("SELECT * FROM diary WHERE day = ?", arrayOf(day.toString())).mapRows { it.toEntry() }.firstOrNull()

    fun between(from: LocalDate, to: LocalDate): List<DiaryEntry> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM diary WHERE day >= ? AND day <= ? ORDER BY day", arrayOf(from.toString(), to.toString())
        ).mapRows { it.toEntry() }

    fun recent(limit: Int = 60): List<DiaryEntry> =
        db.readableDatabase.rawQuery("SELECT * FROM diary ORDER BY day DESC LIMIT $limit", null).mapRows { it.toEntry() }

    /** Entradas que mencionan alguna de las palabras (sin tildes ni mayúsculas). */
    fun search(words: String, limit: Int = 20): List<DiaryEntry> {
        val terms = key(words).split(' ').filter { it.length >= 3 }
        if (terms.isEmpty()) return emptyList()
        return recent(1000).filter { entry -> terms.any { key(entry.text).contains(it) } }.take(limit)
    }

    fun delete(day: LocalDate): Boolean = db.writableDatabase.delete("diary", "day = ?", arrayOf(day.toString())) > 0

    private fun android.database.Cursor.toEntry() = DiaryEntry(
        day = LocalDate.parse(string("day")),
        text = string("text"),
        mood = stringOrNull("mood")
    )

    private fun key(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
}
