package com.apache.mobile.tools

import com.apache.mobile.ApacheApp
import com.apache.mobile.data.GymStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/*
 * Gimnasio: rutinas con ejercicios, series, repeticiones y pesos, y registro
 * de lo entrenado para ver el progreso.
 */

private val GYM_DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))

private val EXERCISE_SCHEMA: JSONObject = Schema.obj(
    "name" to Schema.string("Ejercicio, ej: 'Press banca', 'Sentadilla', 'Dominadas'."),
    "sets" to Schema.integer("Series."),
    "reps" to Schema.string("Repeticiones: '10', '8-12' o 'al fallo'."),
    "weight" to Schema.number("Peso en kg (opcional)."),
    "rest" to Schema.integer("Descanso entre series en segundos (opcional)."),
    "notes" to Schema.string("Notas (opcional): 'agarre cerrado', 'con mancuernas'..."),
    required = listOf("name")
)

/** Peso: acepta 60, "60", "60 kg" o "62,5". */
private fun JSONObject.weight(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    (opt(key) as? Number)?.let { return it.toDouble() }
    return optString(key).replace(',', '.').replace(Regex("[^0-9.]"), "").toDoubleOrNull()
}

private fun JSONObject.toExerciseInput(): GymStore.ExerciseInput? {
    val name = str("name") ?: return null
    return GymStore.ExerciseInput(
        name = name,
        sets = int("sets") ?: 3,
        reps = str("reps") ?: (int("reps")?.toString() ?: "10"),
        weight = weight("weight"),
        restSeconds = int("rest"),
        notes = str("notes")
    )
}

private fun describeRoutine(routine: com.apache.mobile.data.GymRoutine): String = buildString {
    append("Rutina «${routine.name}»")
    routine.day?.let { append(" (${it})") }
    appendLine(":")
    routine.exercises.forEachIndexed { i, e -> appendLine("${i + 1}. ${GymStore.describe(e)}") }
}.trim()

class SaveWorkoutRoutineTool(private val app: ApacheApp) : Tool {
    override val name = "saveWorkoutRoutine"
    override val description =
        "Crea o sustituye una rutina de gimnasio con sus ejercicios, series, repeticiones y pesos. Úsala " +
            "cuando el usuario te dicte su rutina o te pida que le hagas una (diséñala tú con criterio: " +
            "ejercicios, series y repeticiones razonables para su objetivo y nivel)."
    override val parameters = Schema.obj(
        "name" to Schema.string("Nombre: 'Pecho y tríceps', 'Pierna', 'Full body A'..."),
        "day" to Schema.string("Día o días en que se hace, ej: 'lunes', 'lunes y jueves' (opcional)."),
        "exercises" to Schema.array("Ejercicios en orden.", EXERCISE_SCHEMA),
        required = listOf("name", "exercises")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val name = args.str("name") ?: return@withContext "Falta el nombre de la rutina."
        val array = args.optJSONArray("exercises")
        val exercises = (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it)?.toExerciseInput() }
        if (exercises.isEmpty()) return@withContext "La rutina necesita al menos un ejercicio."
        val routine = app.gym.saveRoutine(name, args.str("day"), exercises)
        "Guardada. " + describeRoutine(routine)
    }
}

class GetWorkoutRoutinesTool(private val app: ApacheApp) : Tool {
    override val name = "getWorkoutRoutines"
    override val description =
        "Muestra las rutinas de gimnasio del usuario: una concreta (por nombre o día, ej: 'pierna', 'lunes', " +
            "'hoy') o todas. Para '¿qué me toca hoy en el gym?', '¿cuál es mi rutina de pecho?'."
    override val parameters = Schema.obj(
        "routine" to Schema.string("Nombre o día de la rutina, o 'hoy'. Vacío = todas.")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val wanted = args.str("routine")
        if (wanted != null) {
            val routine = if (GymStore.key(wanted) == "hoy") app.gym.routineForToday() else app.gym.findRoutine(wanted)
            return@withContext routine?.let { describeRoutine(it) }
                ?: if (GymStore.key(wanted) == "hoy") "Hoy no le toca ninguna rutina (ninguna tiene este día asignado)."
                else "No encuentro ninguna rutina «$wanted»."
        }
        val routines = app.gym.routines()
        if (routines.isEmpty()) "Todavía no tiene rutinas de gimnasio."
        else routines.joinToString("\n\n") { describeRoutine(it) }
    }
}

class UpdateWorkoutExerciseTool(private val app: ApacheApp) : Tool {
    override val name = "updateWorkoutExercise"
    override val description =
        "Cambia un ejercicio de una rutina (peso, series, repeticiones, descanso o notas), lo añade o lo quita. " +
            "Ej.: 'sube el press banca a 70 kg', 'pon 4 series de sentadilla', 'añade curl de bíceps a la de espalda'."
    override val parameters = Schema.obj(
        "exercise" to Schema.string("Ejercicio (vale aproximado)."),
        "routine" to Schema.string("Rutina (nombre o día). Necesaria para añadir."),
        "action" to Schema.string("'update' (por defecto), 'add' o 'remove'.", listOf("update", "add", "remove")),
        "sets" to Schema.integer("Nuevas series."),
        "reps" to Schema.string("Nuevas repeticiones."),
        "weight" to Schema.number("Nuevo peso en kg."),
        "rest" to Schema.integer("Nuevo descanso en segundos."),
        "notes" to Schema.string("Notas."),
        required = listOf("exercise")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val exerciseName = args.str("exercise") ?: return@withContext "¿Qué ejercicio?"
        val routineName = args.str("routine")
        when (args.str("action") ?: "update") {
            "add" -> {
                val routine = routineName?.let { app.gym.findRoutine(it) }
                    ?: return@withContext "¿A qué rutina lo añado? No encuentro «${routineName ?: ""}»."
                val input = GymStore.ExerciseInput(
                    exerciseName, args.int("sets") ?: 3, args.str("reps") ?: "10",
                    args.weight("weight"), args.int("rest"), args.str("notes")
                )
                app.gym.addExercise(routine.id, input)
                "Añadido a «${routine.name}»: ${input.name} ${input.sets}×${input.reps}" +
                    (input.weight?.let { " con ${GymStore.formatWeight(it)}" } ?: "") + "."
            }
            "remove" -> {
                val exercise = app.gym.findExercise(exerciseName, routineName)
                    ?: return@withContext "No encuentro «$exerciseName» en tus rutinas."
                app.gym.deleteExercise(exercise.id)
                "Quitado: ${exercise.name}."
            }
            else -> {
                val exercise = app.gym.findExercise(exerciseName, routineName)
                    ?: return@withContext "No encuentro «$exerciseName» en tus rutinas."
                val changed = app.gym.updateExercise(
                    exercise.id, args.int("sets"), args.str("reps"), args.weight("weight"), args.int("rest"), args.str("notes")
                )
                if (!changed) return@withContext "No me has dicho qué cambiar de ${exercise.name}."
                val updated = app.gym.findExercise(exercise.name, routineName) ?: exercise
                "Actualizado: ${GymStore.describe(updated)}."
            }
        }
    }
}

class DeleteWorkoutRoutineTool(private val app: ApacheApp) : Tool {
    override val name = "deleteWorkoutRoutine"
    override val description = "Borra una rutina de gimnasio entera (solo si el usuario lo pide claramente)."
    override val parameters = Schema.obj(
        "routine" to Schema.string("Nombre o día de la rutina."),
        required = listOf("routine")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val text = args.str("routine") ?: return@withContext "¿Qué rutina?"
        val routine = app.gym.findRoutine(text) ?: return@withContext "No encuentro la rutina «$text»."
        app.gym.deleteRoutine(routine.id)
        "Rutina «${routine.name}» borrada."
    }
}

class LogWorkoutTool(private val app: ApacheApp) : Tool {
    override val name = "logWorkout"
    override val description =
        "Apunta lo que el usuario ha entrenado: una rutina entera ('he hecho la de pierna') o ejercicios sueltos " +
            "con lo que levantó ('press banca 4×8 con 65 kg'). Si levantó más peso que en su rutina, pregúntale si " +
            "quiere subirlo en la rutina."
    override val parameters = Schema.obj(
        "routine" to Schema.string("Rutina hecha entera (nombre o día), opcional."),
        "exercises" to Schema.array("Ejercicios sueltos hechos (opcional).", EXERCISE_SCHEMA),
        "date" to Schema.string("Día 'yyyy-MM-dd'. Por defecto hoy.")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val day = args.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val done = mutableListOf<String>()

        args.str("routine")?.let { text ->
            val routine = app.gym.findRoutine(text) ?: return@withContext "No encuentro la rutina «$text»."
            app.gym.logRoutine(routine, day)
            done += "rutina «${routine.name}» (${routine.exercises.size} ejercicios)"
        }

        val array = args.optJSONArray("exercises")
        val inputs = (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it)?.toExerciseInput() }
        val improvements = mutableListOf<String>()
        inputs.forEach { e ->
            app.gym.log(day, null, e.name, e.sets, e.reps, e.weight)
            done += "${e.name} ${e.sets}×${e.reps}" + (e.weight?.let { " con ${GymStore.formatWeight(it)}" } ?: "")
            val planned = app.gym.findExercise(e.name)
            if (planned?.weight != null && e.weight != null && e.weight > planned.weight) {
                improvements += "${planned.name}: hoy ${GymStore.formatWeight(e.weight)}, en la rutina ${GymStore.formatWeight(planned.weight)}"
            }
        }

        if (done.isEmpty()) return@withContext "¿Qué has entrenado?"
        "Apuntado (${day.format(GYM_DAY)}): ${done.joinToString("; ")}." +
            if (improvements.isNotEmpty()) " Ha subido peso → ${improvements.joinToString("; ")}." else ""
    }
}

class GetWorkoutProgressTool(private val app: ApacheApp) : Tool {
    override val name = "getWorkoutProgress"
    override val description =
        "Progreso en el gimnasio: historial y mejor marca de un ejercicio ('¿cómo voy en press banca?') o, sin " +
            "ejercicio, los últimos entrenamientos y cuántos días ha ido este mes."
    override val parameters = Schema.obj(
        "exercise" to Schema.string("Ejercicio (opcional).")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val exercise = args.str("exercise")
        if (exercise != null) {
            val history = app.gym.history(exercise)
            if (history.isEmpty()) return@withContext "No hay registros de «$exercise» todavía."
            val best = history.filter { it.weight != null }.maxByOrNull { it.weight!! }
            return@withContext buildString {
                appendLine("Historial de ${history.first().exercise}:")
                history.take(10).forEach { appendLine("- ${it.day.format(GYM_DAY)}: ${it.sets}×${it.reps} con ${GymStore.formatWeight(it.weight)}") }
                best?.let { appendLine("Mejor marca: ${GymStore.formatWeight(it.weight)} (${it.day.format(GYM_DAY)}).") }
            }.trim()
        }
        val today = LocalDate.now()
        val thisMonth = app.gym.trainedDays(today.withDayOfMonth(1))
        val recent = app.gym.recentLogs(30).groupBy { it.day }.entries.take(5)
        if (recent.isEmpty()) return@withContext "Todavía no ha apuntado ningún entrenamiento."
        buildString {
            appendLine("Días entrenados este mes: ${thisMonth.size}.")
            appendLine("Últimos entrenamientos:")
            recent.forEach { (day, logs) ->
                val name = logs.firstNotNullOfOrNull { it.routineName }
                appendLine("- ${day.format(GYM_DAY)}: " + (name?.let { "«$it»" } ?: logs.joinToString { it.exercise }))
            }
        }.trim()
    }
}
