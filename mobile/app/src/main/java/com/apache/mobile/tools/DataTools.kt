package com.apache.mobile.tools

import com.apache.mobile.ApacheApp
import com.apache.mobile.data.CalendarEvent
import com.apache.mobile.data.MemoryStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TIME = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-ES"))

private fun CalendarEvent.describe(): String {
    val range = if (endAt != null) "${startAt.format(DAY_TIME)} – ${endAt.format(HOUR)}" else startAt.format(DAY_TIME)
    return "#$id · $title · $range" + (location?.let { " · $it" } ?: "") + (if (status == "completed") " · hecho" else "")
}

// ---------------------------------------------------------------------------
// Memoria
// ---------------------------------------------------------------------------

class RememberFactTool(private val app: ApacheApp) : Tool {
    override val name = "rememberFact"
    override val description =
        "Guarda en la memoria permanente un dato del usuario que seguirá siendo cierto: nombre, " +
            "ciudad, gustos, alergias, rutinas, trabajo, personas importantes, cómo quiere que le " +
            "hables. No guardes cosas pasajeras. Si la clave ya existe, se actualiza."
    override val parameters = Schema.obj(
        "key" to Schema.string("Nombre corto del dato, ej: 'nombre', 'ciudad', 'comida favorita'."),
        "value" to Schema.string("El dato, ej: 'Fran', 'Madrid', 'la pizza'."),
        "type" to Schema.string("Categoría.", MemoryStore.TYPES),
        "importance" to Schema.number("Importancia de 0 a 1 (1 = muy importante, ej. una alergia).")
    , required = listOf("key", "value"))

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val key = args.str("key") ?: return@withContext "Falta el nombre del dato."
        val value = args.str("value") ?: return@withContext "Falta el contenido del dato."
        val (fact, isNew) = app.memory.remember(
            key, value,
            type = args.str("type") ?: "otro",
            importance = if (args.has("importance")) args.optDouble("importance", 0.5) else 0.5
        )
        if (isNew) "Guardado: ${fact.key} = ${fact.value}." else "Actualizado: ${fact.key} = ${fact.value}."
    }
}

class ForgetFactTool(private val app: ApacheApp) : Tool {
    override val name = "forgetFact"
    override val description = "Borra un dato de la memoria permanente cuando el usuario pide que lo olvides."
    override val parameters = Schema.obj(
        "key" to Schema.string("Clave del dato tal cual aparece en la memoria."),
        required = listOf("key")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val key = args.str("key") ?: return@withContext "¿Qué quieres que olvide?"
        val fact = app.memory.findByKey(key) ?: return@withContext "No tenía guardado nada sobre '$key'."
        app.memory.delete(fact.id)
        "Olvidado: ${fact.key}."
    }
}

// ---------------------------------------------------------------------------
// Calendario y horario
// ---------------------------------------------------------------------------

class GetCalendarEventsTool(private val app: ApacheApp) : Tool {
    override val name = "getCalendarEvents"
    override val description =
        "Consulta el calendario y el horario del usuario entre dos fechas (por defecto, de hoy a 7 días). " +
            "Para '¿qué tengo hoy?', '¿qué hago el viernes?'..."
    override val parameters = Schema.obj(
        "from" to Schema.string("Fecha de inicio 'yyyy-MM-dd'. Por defecto hoy."),
        "to" to Schema.string("Fecha de fin 'yyyy-MM-dd'. Por defecto 7 días después.")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val from = parseDateTime(args.str("from"))?.toLocalDate() ?: LocalDate.now()
        val to = parseDateTime(args.str("to"))?.toLocalDate() ?: from.plusDays(7)
        val events = app.events.between(from.atStartOfDay(), to.atTime(23, 59, 59))
        if (events.isEmpty()) "No hay nada en el calendario en esas fechas." else events.joinToString("\n") { it.describe() }
    }
}

class CreateCalendarEventTool(private val app: ApacheApp) : Tool {
    override val name = "createCalendarEvent"
    override val description = "Crea un evento en el calendario (cita, reunión, plan...). Avisa con una notificación al empezar."
    override val parameters = Schema.obj(
        "title" to Schema.string("Título del evento."),
        "startAt" to Schema.string("Inicio 'yyyy-MM-ddTHH:mm'."),
        "endAt" to Schema.string("Fin 'yyyy-MM-ddTHH:mm'. Opcional."),
        "location" to Schema.string("Lugar. Opcional."),
        "description" to Schema.string("Detalles. Opcional."),
        required = listOf("title", "startAt")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val start = parseDateTime(args.str("startAt")) ?: return@withContext "No entiendo la fecha de inicio."
        val event = app.events.create(
            title = args.str("title") ?: return@withContext "Falta el título.",
            startAt = start,
            endAt = parseDateTime(args.str("endAt")),
            location = args.str("location"),
            description = args.str("description")
        )
        app.alarms.scheduleEvent(event)
        "Evento creado: ${event.describe()}"
    }
}

class UpdateCalendarEventTool(private val app: ApacheApp) : Tool {
    override val name = "updateCalendarEvent"
    override val description = "Modifica un evento existente. Necesita su id (obtenlo con getCalendarEvents)."
    override val parameters = Schema.obj(
        "eventId" to Schema.integer("Id del evento."),
        "title" to Schema.string("Nuevo título. Opcional."),
        "startAt" to Schema.string("Nuevo inicio 'yyyy-MM-ddTHH:mm'. Opcional."),
        "endAt" to Schema.string("Nuevo fin 'yyyy-MM-ddTHH:mm'. Opcional."),
        "location" to Schema.string("Nuevo lugar. Opcional."),
        required = listOf("eventId")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val id = args.long("eventId") ?: return@withContext "Falta el id del evento."
        val updated = app.events.update(
            id,
            title = args.str("title"),
            startAt = parseDateTime(args.str("startAt")),
            endAt = parseDateTime(args.str("endAt")),
            location = args.str("location")
        ) ?: return@withContext "No existe el evento #$id."
        app.alarms.scheduleEvent(updated)
        "Actualizado: ${updated.describe()}"
    }
}

class DeleteCalendarEventTool(private val app: ApacheApp) : Tool {
    override val name = "deleteCalendarEvent"
    override val description = "Elimina un evento del calendario. Necesita su id (obtenlo con getCalendarEvents)."
    override val parameters = Schema.obj("eventId" to Schema.integer("Id del evento."), required = listOf("eventId"))

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val id = args.long("eventId") ?: return@withContext "Falta el id del evento."
        val event = app.events.find(id) ?: return@withContext "No existe el evento #$id."
        app.events.setStatus(id, "cancelled")
        app.alarms.cancelEvent(id)
        "Eliminado: ${event.title}."
    }
}

class PlanDayScheduleTool(private val app: ApacheApp) : Tool {
    override val name = "planDaySchedule"
    override val description =
        "Organiza un día creando varios bloques a la vez (estudio, trabajo, gimnasio, comidas, descansos). " +
            "Antes consulta con getCalendarEvents lo que ya hay ese día para no solapar, y no planifiques " +
            "antes de la hora actual si es hoy."
    override val parameters = Schema.obj(
        "date" to Schema.string("Día 'yyyy-MM-dd'."),
        "blocks" to Schema.array(
            "Bloques en orden.",
            Schema.obj(
                "title" to Schema.string("Qué se hace."),
                "start" to Schema.string("Inicio 'HH:mm'."),
                "end" to Schema.string("Fin 'HH:mm'."),
                required = listOf("title", "start", "end")
            )
        ),
        required = listOf("date", "blocks")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val date = parseDateTime(args.str("date"))?.toLocalDate() ?: return@withContext "La fecha no es válida."
        val blocks = args.optJSONArray("blocks") ?: return@withContext "No hay bloques."

        val created = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (i in 0 until blocks.length()) {
            val block = blocks.optJSONObject(i) ?: continue
            val title = block.str("title").orEmpty()
            val start = parseTime(block.str("start"))
            val end = parseTime(block.str("end"))

            if (title.isBlank() || start == null || end == null || !end.isAfter(start)) {
                failed.add(title.ifBlank { "bloque ${i + 1}" })
                continue
            }

            val event = app.events.create(title, date.atTime(start), date.atTime(end))
            app.alarms.scheduleEvent(event)
            created.add("${start.format(HOUR)}-${end.format(HOUR)} $title")
        }

        buildString {
            if (created.isNotEmpty()) appendLine("Horario creado:\n" + created.joinToString("\n"))
            if (failed.isNotEmpty()) appendLine("No se pudieron crear: ${failed.joinToString()}")
        }.trim().ifBlank { "No se ha creado ningún bloque." }
    }
}

// ---------------------------------------------------------------------------
// Recordatorios
// ---------------------------------------------------------------------------

class CreateReminderTool(private val app: ApacheApp) : Tool {
    override val name = "createReminder"
    override val description = "Crea un recordatorio que avisará con una notificación a la hora indicada."
    override val parameters = Schema.obj(
        "title" to Schema.string("Qué hay que recordar."),
        "triggerAt" to Schema.string("Cuándo avisar 'yyyy-MM-ddTHH:mm' (calcula tú 'dentro de 10 minutos' con la hora actual)."),
        required = listOf("title", "triggerAt")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val title = args.str("title") ?: return@withContext "¿Qué te recuerdo?"
        val at = parseDateTime(args.str("triggerAt")) ?: return@withContext "No entiendo cuándo avisarte."
        if (at.isBefore(LocalDateTime.now())) return@withContext "Esa hora ya ha pasado."
        val reminder = app.reminders.create(title, at)
        app.alarms.scheduleReminder(reminder)
        "Recordatorio para ${at.format(DAY_TIME)}: $title."
    }
}

class ListRemindersTool(private val app: ApacheApp) : Tool {
    override val name = "listReminders"
    override val description = "Lista los recordatorios pendientes."
    override val parameters = Schema.obj()

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val pending = app.reminders.pending()
        if (pending.isEmpty()) "No hay recordatorios pendientes."
        else pending.joinToString("\n") { "#${it.id} · ${it.triggerAt.format(DAY_TIME)} · ${it.title}" }
    }
}

class CancelReminderTool(private val app: ApacheApp) : Tool {
    override val name = "cancelReminder"
    override val description = "Cancela un recordatorio pendiente. Necesita su id (obtenlo con listReminders)."
    override val parameters = Schema.obj("reminderId" to Schema.integer("Id del recordatorio."), required = listOf("reminderId"))

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val id = args.long("reminderId") ?: return@withContext "Falta el id."
        val reminder = app.reminders.find(id) ?: return@withContext "No existe el recordatorio #$id."
        app.reminders.setStatus(id, "cancelled")
        app.alarms.cancelReminder(id)
        "Cancelado: ${reminder.title}."
    }
}
