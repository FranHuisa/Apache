package com.apache.mobile.data

import java.text.Normalizer

/** Rutinas por voz: una frase que dispara varios pasos. */
class RoutineStore(private val db: ApacheDatabase) {

    /** Crea o sustituye (misma frase) una rutina. */
    fun save(trigger: String, steps: List<String>): Routine {
        val phrase = trigger.trim().trim('"', '«', '»', '.').trim()
        require(phrase.isNotEmpty()) { "La rutina necesita una frase." }
        val cleanSteps = steps.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleanSteps.isNotEmpty()) { "La rutina necesita al menos un paso." }

        findExact(phrase)?.let { delete(it.id) }
        val id = db.writableDatabase.insert(
            "routine", null,
            contentValues("trigger_phrase" to phrase, "steps" to cleanSteps.joinToString("\n"), "created_at" to ApacheDatabase.now())
        )
        return Routine(id, phrase, cleanSteps, null)
    }

    fun all(): List<Routine> =
        db.readableDatabase.rawQuery("SELECT * FROM routine ORDER BY trigger_phrase", null).mapRows { it.toRoutine() }

    fun delete(id: Long): Boolean = db.writableDatabase.delete("routine", "id = ?", arrayOf(id.toString())) > 0

    fun markRun(id: Long) {
        db.writableDatabase.update("routine", contentValues("last_run" to ApacheDatabase.now()), "id = ?", arrayOf(id.toString()))
    }

    private fun findExact(phrase: String): Routine? = all().firstOrNull { key(it.trigger) == key(phrase) }

    /** Busca por frase aproximada (para borrar "la de dormir"). */
    fun find(text: String): Routine? {
        val wanted = key(text)
        return all().firstOrNull { key(it.trigger) == wanted }
            ?: all().firstOrNull { key(it.trigger).contains(wanted) || wanted.contains(key(it.trigger)) }
    }

    /**
     * ¿Lo que ha dicho el usuario es la frase de una rutina? Coincide si es
     * igual o casi (con "Apache," delante, signos o un "ya" de más).
     */
    fun match(message: String): Routine? {
        val said = key(message).removePrefix("apache").removePrefix("oye apache").trim()
        if (said.isEmpty() || said.length > 80) return null
        return all().firstOrNull { routine ->
            val phrase = key(routine.trigger)
            said == phrase || said.removePrefix("ya ").trim() == phrase.removePrefix("ya ").trim()
        }
    }

    private fun android.database.Cursor.toRoutine() = Routine(
        id = long("id"),
        trigger = string("trigger_phrase"),
        steps = string("steps").split("\n").filter { it.isNotBlank() },
        lastRun = stringOrNull("last_run")
    )

    companion object {
        fun key(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
                .lowercase().replace(Regex("[^a-z0-9ñ ]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
