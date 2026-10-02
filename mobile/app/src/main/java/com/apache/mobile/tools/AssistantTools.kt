package com.apache.mobile.tools

import com.apache.mobile.ApacheApp
import com.apache.mobile.data.DiaryEntry
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", Locale.forLanguageTag("es-ES"))
private val HOUR = DateTimeFormatter.ofPattern("HH:mm")

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key)
    if (array != null) return (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotEmpty() }
    return optString(key, "").split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
}

// ---------------------------------------------------------------------------
// Rutinas por voz
// ---------------------------------------------------------------------------

class CreateRoutineTool(private val app: ApacheApp) : Tool {
    override val name = "createRoutine"
    override val description =
        "Crea (o sustituye) una rutina: una frase del usuario que dispara varios pasos. Ej.: 'cuando diga " +
            "me voy a dormir, pon una alarma a las 7, dime qué tengo mañana y cuánto voy a dormir'."
    override val parameters = Schema.obj(
        "trigger" to Schema.string("La frase que la dispara, tal cual la dirá: 'me voy a dormir'."),
        "steps" to Schema.array(
            "Pasos en orden, cada uno como una orden clara para ti: 'Pon una alarma a las 7:00', " +
                "'Dime qué tengo mañana en el calendario', 'Dime cuántas horas voy a dormir hasta la alarma'.",
            Schema.string("Paso")
        ),
        required = listOf("trigger", "steps")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val trigger = args.str("trigger") ?: return@withContext "Falta la frase de la rutina."
        val steps = args.stringList("steps")
        val routine = try {
            app.routines.save(trigger, steps)
        } catch (e: IllegalArgumentException) {
            return@withContext e.message ?: "No se ha podido guardar la rutina."
        }
        "Rutina guardada: cuando diga «${routine.trigger}» haré ${routine.steps.size} pasos: ${routine.steps.joinToString("; ")}."
    }
}

class ListRoutinesTool(private val app: ApacheApp) : Tool {
    override val name = "listRoutines"
    override val description = "Muestra las rutinas del usuario (frase y pasos)."
    override val parameters = Schema.obj()

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val routines = app.routines.all()
        if (routines.isEmpty()) "No tiene ninguna rutina todavía."
        else routines.joinToString("\n") { "«${it.trigger}»: ${it.steps.joinToString("; ")}" }
    }
}

class DeleteRoutineTool(private val app: ApacheApp) : Tool {
    override val name = "deleteRoutine"
    override val description = "Borra una rutina por su frase (vale aproximada)."
    override val parameters = Schema.obj(
        "trigger" to Schema.string("Frase de la rutina a borrar."),
        required = listOf("trigger")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val text = args.str("trigger") ?: return@withContext "¿Qué rutina quieres borrar?"
        val routine = app.routines.find(text) ?: return@withContext "No encuentro ninguna rutina con «$text»."
        app.routines.delete(routine.id)
        "Rutina «${routine.trigger}» borrada."
    }
}

// ---------------------------------------------------------------------------
// Diario
// ---------------------------------------------------------------------------

class SaveDiaryEntryTool(private val app: ApacheApp) : Tool {
    override val name = "saveDiaryEntry"
    override val description =
        "Guarda en el diario lo que el usuario cuenta de su día (qué ha hecho, con quién, cómo se siente). " +
            "Escribe un resumen breve en primera persona, como si lo escribiera él. Si ya había algo ese día, se añade."
    override val parameters = Schema.obj(
        "text" to Schema.string("Resumen en 1-4 frases, en primera persona."),
        "mood" to Schema.string("Ánimo en una o dos palabras: 'contento', 'cansado', 'estresado'..."),
        "date" to Schema.string("Día 'yyyy-MM-dd'. Por defecto hoy (si es de madrugada y habla de ayer, ayer)."),
        required = listOf("text")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val text = args.str("text") ?: return@withContext "No hay nada que guardar."
        val day = args.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val entry = app.diary.save(day, text, args.str("mood"))
        "Guardado en el diario del ${entry.day.format(DAY)}."
    }
}

class SearchDiaryTool(private val app: ApacheApp) : Tool {
    override val name = "searchDiary"
    override val description =
        "Busca en el pasado del usuario: su diario y su calendario. Por fechas ('¿qué hice el finde pasado?' " +
            "→ from/to) o por palabras ('¿cuándo fui al médico?' → query 'médico'). Devuelve lo que encuentre."
    override val parameters = Schema.obj(
        "query" to Schema.string("Palabras a buscar, ej: 'médico', 'cine', 'Laura'. Opcional."),
        "from" to Schema.string("Desde 'yyyy-MM-dd'. Opcional."),
        "to" to Schema.string("Hasta 'yyyy-MM-dd'. Opcional.")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val query = args.str("query")
        val from = args.str("from")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val to = args.str("to")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: from

        val entries: List<DiaryEntry> = when {
            from != null -> app.diary.between(from, to ?: from).let { list ->
                if (query != null) list.filter { matches(it.text, query) } else list
            }
            query != null -> app.diary.search(query)
            else -> app.diary.recent(7)
        }

        // Calendario: lo que tuvo en esas fechas o lo que se llamaba así (hasta 2 años atrás).
        val today = LocalDate.now()
        val events = if (from != null) {
            app.events.between(from.atStartOfDay(), (to ?: from).atTime(23, 59, 59))
        } else if (query != null) {
            app.events.between(today.minusYears(2).atStartOfDay(), today.plusDays(1).atStartOfDay())
                .filter { matches(it.title + " " + (it.description ?: "") + " " + (it.location ?: ""), query) }
                .sortedByDescending { it.startAt }.take(15)
        } else emptyList()

        if (entries.isEmpty() && events.isEmpty()) {
            return@withContext "No encuentro nada en el diario ni en el calendario" +
                (query?.let { " sobre «$it»" } ?: "") + (from?.let { " en esas fechas" } ?: "") + "."
        }

        buildString {
            if (entries.isNotEmpty()) {
                appendLine("Diario:")
                entries.forEach { e ->
                    appendLine("- ${e.day.format(DAY)}" + (e.mood?.let { " (ánimo: $it)" } ?: "") + ": ${e.text.replace("\n", " ")}")
                }
            }
            if (events.isNotEmpty()) {
                appendLine("Calendario:")
                events.forEach { ev ->
                    appendLine("- ${ev.startAt.toLocalDate().format(DAY)} ${ev.startAt.format(HOUR)}: ${ev.title}" +
                        (if (ev.status == "completed") " (hecho)" else if (ev.status == "cancelled") " (cancelado)" else ""))
                }
            }
        }.trim()
    }

    private fun matches(text: String, query: String): Boolean {
        val haystack = normalize(text)
        return normalize(query).split(' ').filter { it.length >= 3 }.any { haystack.contains(it) }
    }

    private fun normalize(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
}

// ---------------------------------------------------------------------------
// Avisos proactivos bajo demanda
// ---------------------------------------------------------------------------

class GetAlertsTool(private val app: ApacheApp) : Tool {
    override val name = "getAlerts"
    override val description =
        "Revisa si hay algo que el usuario deba saber ahora: lluvia cerca de sus eventos, un evento que " +
            "empieza pronto, mañana empieza temprano sin alarma, o la compra lleva días pendiente. Para " +
            "'¿algo que deba saber?', '¿me olvido de algo?'."
    override val parameters = Schema.obj()

    override suspend fun execute(args: JSONObject): String {
        val notices = com.apache.mobile.reminders.Proactive.collect(app)
        return if (notices.isEmpty()) "Nada importante ahora mismo."
        else notices.joinToString("\n") { "- ${it.title}: ${it.text}" }
    }
}
