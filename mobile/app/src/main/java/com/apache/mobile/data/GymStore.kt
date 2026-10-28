package com.apache.mobile.data

import java.text.Normalizer
import java.time.LocalDate

/**
 * Gimnasio: rutinas (con ejercicios, series, repeticiones y peso) y el
 * registro de lo entrenado cada día, para ver el progreso.
 */
class GymStore(private val db: ApacheDatabase) {

    /** Datos de un ejercicio al crear o cambiar una rutina. */
    data class ExerciseInput(
        val name: String,
        val sets: Int = 3,
        val reps: String = "10",
        val weight: Double? = null,
        val restSeconds: Int? = null,
        val notes: String? = null
    )

    // --- Rutinas ---

    /** Crea una rutina; si ya hay una con el mismo nombre, la sustituye. */
    fun saveRoutine(name: String, day: String?, exercises: List<ExerciseInput>): GymRoutine {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "La rutina necesita un nombre." }
        require(exercises.isNotEmpty()) { "La rutina necesita al menos un ejercicio." }

        val existing = findRoutine(clean)
        val writable = db.writableDatabase
        writable.beginTransaction()
        try {
            val id = if (existing != null) {
                writable.update("gym_routine", contentValues("name" to clean, "day" to day?.trim()), "id = ?", arrayOf(existing.id.toString()))
                writable.delete("gym_exercise", "routine_id = ?", arrayOf(existing.id.toString()))
                existing.id
            } else {
                val position = routines().size
                writable.insert(
                    "gym_routine", null,
                    contentValues("name" to clean, "day" to day?.trim(), "position" to position, "created_at" to ApacheDatabase.now())
                )
            }
            exercises.forEachIndexed { index, exercise -> insertExercise(id, index, exercise) }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
        return findRoutine(clean) ?: error("No se ha podido guardar la rutina.")
    }

    private fun insertExercise(routineId: Long, position: Int, e: ExerciseInput): Long =
        db.writableDatabase.insert(
            "gym_exercise", null,
            contentValues(
                "routine_id" to routineId, "position" to position, "name" to e.name.trim(),
                "sets" to e.sets.coerceIn(1, 20), "reps" to e.reps.trim().ifEmpty { "10" },
                "weight" to e.weight, "rest_seconds" to e.restSeconds, "notes" to e.notes?.trim()
            )
        )

    fun routines(): List<GymRoutine> {
        val list = db.readableDatabase.rawQuery("SELECT * FROM gym_routine ORDER BY position, id", null)
            .mapRows { Triple(it.long("id"), it.string("name"), it.stringOrNull("day")) }
        return list.map { (id, name, day) -> GymRoutine(id, name, day, exercises(id)) }
    }

    private fun exercises(routineId: Long): List<GymExercise> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM gym_exercise WHERE routine_id = ? ORDER BY position, id", arrayOf(routineId.toString())
        ).mapRows { it.toExercise() }

    /** Busca una rutina por nombre o por su día ("lunes", "la de pierna"). */
    fun findRoutine(text: String): GymRoutine? {
        val wanted = key(text).removePrefix("la de ").removePrefix("rutina de ").removePrefix("rutina ").trim()
        if (wanted.isEmpty()) return null
        val all = routines()
        return all.firstOrNull { key(it.name) == wanted }
            ?: all.firstOrNull { it.day != null && key(it.day) == wanted }
            ?: all.firstOrNull { key(it.name).contains(wanted) || wanted.contains(key(it.name)) }
            ?: all.firstOrNull { it.day != null && (key(it.day).contains(wanted) || wanted.contains(key(it.day))) }
    }

    /** La rutina que toca hoy según su día (si alguna lo tiene). */
    fun routineForToday(): GymRoutine? {
        val today = key(java.time.LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-ES")))
        return routines().firstOrNull { it.day != null && key(it.day).contains(today) }
    }

    fun deleteRoutine(id: Long): Boolean {
        db.writableDatabase.delete("gym_exercise", "routine_id = ?", arrayOf(id.toString()))
        return db.writableDatabase.delete("gym_routine", "id = ?", arrayOf(id.toString())) > 0
    }

    // --- Ejercicios sueltos ---

    fun findExercise(text: String, routine: String? = null): GymExercise? {
        val wanted = key(text)
        val pool = (if (routine != null) findRoutine(routine)?.exercises else null) ?: routines().flatMap { it.exercises }
        return pool.firstOrNull { key(it.name) == wanted }
            ?: pool.firstOrNull { key(it.name).contains(wanted) || wanted.contains(key(it.name)) }
    }

    /** Cambia series, repeticiones, peso, descanso o notas de un ejercicio (lo que no sea null). */
    fun updateExercise(id: Long, sets: Int? = null, reps: String? = null, weight: Double? = null, restSeconds: Int? = null, notes: String? = null): Boolean {
        val values = buildList<Pair<String, Any?>> {
            sets?.let { add("sets" to it.coerceIn(1, 20)) }
            reps?.let { add("reps" to it.trim()) }
            weight?.let { add("weight" to it) }
            restSeconds?.let { add("rest_seconds" to it) }
            notes?.let { add("notes" to it.trim()) }
        }
        if (values.isEmpty()) return false
        return db.writableDatabase.update("gym_exercise", contentValues(*values.toTypedArray()), "id = ?", arrayOf(id.toString())) > 0
    }

    fun addExercise(routineId: Long, exercise: ExerciseInput): Long =
        insertExercise(routineId, exercises(routineId).size, exercise)

    fun deleteExercise(id: Long): Boolean = db.writableDatabase.delete("gym_exercise", "id = ?", arrayOf(id.toString())) > 0

    // --- Registro de entrenamientos ---

    fun log(day: LocalDate, routineName: String?, exercise: String, sets: Int, reps: String, weight: Double?): Long =
        db.writableDatabase.insert(
            "gym_log", null,
            contentValues(
                "day" to day.toString(), "routine_name" to routineName, "exercise" to exercise.trim(),
                "sets" to sets, "reps" to reps, "weight" to weight, "created_at" to ApacheDatabase.now()
            )
        )

    /** Apunta una rutina entera como hecha hoy, con sus pesos actuales. Devuelve cuántos ejercicios. */
    fun logRoutine(routine: GymRoutine, day: LocalDate = LocalDate.now()): Int {
        routine.exercises.forEach { log(day, routine.name, it.name, it.sets, it.reps, it.weight) }
        return routine.exercises.size
    }

    /** Historial de un ejercicio (lo más reciente primero). */
    fun history(exercise: String, limit: Int = 20): List<GymLog> {
        val wanted = key(exercise)
        return recentLogs(500).filter { key(it.exercise) == wanted || key(it.exercise).contains(wanted) }.take(limit)
    }

    fun recentLogs(limit: Int = 50): List<GymLog> =
        db.readableDatabase.rawQuery("SELECT * FROM gym_log ORDER BY day DESC, id DESC LIMIT $limit", null).mapRows { it.toLog() }

    /** Días distintos entrenados desde [from]. */
    fun trainedDays(from: LocalDate): List<LocalDate> =
        db.readableDatabase.rawQuery("SELECT DISTINCT day FROM gym_log WHERE day >= ? ORDER BY day", arrayOf(from.toString()))
            .mapRows { LocalDate.parse(it.string("day")) }

    fun deleteLog(id: Long): Boolean = db.writableDatabase.delete("gym_log", "id = ?", arrayOf(id.toString())) > 0

    private fun android.database.Cursor.toExercise() = GymExercise(
        id = long("id"),
        routineId = long("routine_id"),
        name = string("name"),
        sets = long("sets").toInt(),
        reps = string("reps"),
        weight = doubleOrNull("weight"),
        restSeconds = longOrNull("rest_seconds")?.toInt(),
        notes = stringOrNull("notes")
    )

    private fun android.database.Cursor.toLog() = GymLog(
        id = long("id"),
        day = LocalDate.parse(string("day")),
        routineName = stringOrNull("routine_name"),
        exercise = string("exercise"),
        sets = long("sets").toInt(),
        reps = string("reps"),
        weight = doubleOrNull("weight")
    )

    private fun android.database.Cursor.doubleOrNull(column: String): Double? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getDouble(index)
    }

    private fun android.database.Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    companion object {
        fun key(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

        /** "60", "60.0" → "60 kg"; "62.5" → "62,5 kg"; null → "sin peso". */
        fun formatWeight(weight: Double?): String = when {
            weight == null -> "sin peso"
            weight % 1.0 == 0.0 -> "${weight.toInt()} kg"
            else -> "${"%.1f".format(java.util.Locale.forLanguageTag("es-ES"), weight)} kg"
        }

        fun describe(e: GymExercise): String =
            "${e.name}: ${e.sets}×${e.reps}" + (e.weight?.let { " con ${formatWeight(it)}" } ?: "") +
                (e.restSeconds?.let { ", descanso ${it}s" } ?: "") + (e.notes?.let { " ($it)" } ?: "")
    }
}
