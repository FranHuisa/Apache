package com.apache.tools

import com.apache.ApacheDefaults
import com.apache.database.repository.CalendarEventRecord
import com.apache.database.service.CalendarService
import java.time.LocalDate
import org.springframework.stereotype.Component

private fun CalendarEventRecord.describe(): String {
    val range = if (endAt != null) {
        "${ToolDateParsing.formatForUser(startAt)} → ${ToolDateParsing.formatForUser(endAt)}"
    } else {
        ToolDateParsing.formatForUser(startAt)
    }

    val locationPart = location?.let { " en $it" } ?: ""

    return "#$id · $title · $range$locationPart"
}

/** Tool que crea un evento nuevo en el calendario principal del usuario. */
@Component
class CreateCalendarEventTool(
    private val calendarService: CalendarService
) : Tool {

    override val name = "createCalendarEvent"

    override val description =
        "Crea un evento nuevo en el calendario del usuario (una reunión, cita o cualquier " +
            "compromiso con fecha y hora). Úsalo cuando el usuario pida agendar, programar o " +
            "añadir algo a su calendario."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf(
                "type" to "string",
                "description" to "Título del evento, ej: 'Reunión con el equipo'."
            ),
            "startAt" to mapOf(
                "type" to "string",
                "description" to "Fecha y hora de inicio en formato ISO 'yyyy-MM-ddTHH:mm', ej: '2026-09-15T18:30'."
            ),
            "endAt" to mapOf(
                "type" to "string",
                "description" to "Fecha y hora de fin, mismo formato que startAt. Opcional."
            ),
            "description" to mapOf(
                "type" to "string",
                "description" to "Detalles adicionales del evento. Opcional."
            ),
            "location" to mapOf(
                "type" to "string",
                "description" to "Lugar del evento. Opcional."
            )
        ),
        "required" to listOf("title", "startAt")
    )

    override fun execute(args: Map<String, Any?>): String {
        val title = (args["title"] as? String)?.trim()
            ?: return "Falta el título del evento."

        val startAt = ToolDateParsing.parseDateTime(args["startAt"] as? String)
            ?: return "No he podido entender la fecha de inicio del evento."

        val endAt = ToolDateParsing.parseDateTime(args["endAt"] as? String)

        return try {
            val event = calendarService.createEvent(
                userId = ApacheDefaults.DEFAULT_USER_ID,
                title = title,
                description = (args["description"] as? String)?.trim(),
                startAt = startAt,
                endAt = endAt,
                location = (args["location"] as? String)?.trim()
            )

            "Evento creado: ${event.describe()}"
        } catch (e: IllegalArgumentException) {
            "No se ha podido crear el evento: ${e.message}"
        }
    }
}

/** Tool de solo lectura que consulta los eventos del calendario en un rango de fechas. */
@Component
class GetCalendarEventsTool(
    private val calendarService: CalendarService
) : Tool {

    override val name = "getCalendarEvents"

    override val description =
        "Consulta los eventos del calendario del usuario entre dos fechas. Si no se especifica " +
            "ningún rango, se asume desde hoy hasta dentro de 7 días. Útil para preguntas como " +
            "'¿qué tengo hoy?', '¿qué planes tengo esta semana?' o '¿tengo algo el viernes?'."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "from" to mapOf(
                "type" to "string",
                "description" to "Fecha de inicio del rango, formato 'yyyy-MM-dd'. Opcional, por defecto hoy."
            ),
            "to" to mapOf(
                "type" to "string",
                "description" to "Fecha de fin del rango, formato 'yyyy-MM-dd'. Opcional, por defecto 7 días después de 'from'."
            )
        )
    )

    override fun execute(args: Map<String, Any?>): String {

        val from = ToolDateParsing.parseDateTime(args["from"] as? String)
            ?: LocalDate.now().atStartOfDay()

        val to = ToolDateParsing.parseDateTime(args["to"] as? String)
            ?: from.plusDays(7).toLocalDate().atTime(23, 59, 59)

        val events = try {
            calendarService.getEvents(ApacheDefaults.DEFAULT_USER_ID, from, to)
        } catch (e: IllegalArgumentException) {
            return "No se han podido consultar los eventos: ${e.message}"
        }

        if (events.isEmpty()) {
            return "No hay eventos programados en ese rango de fechas."
        }

        return events.joinToString("\n") { it.describe() }
    }
}

/** Tool que modifica un evento existente. Solo cambia los campos que se indiquen. */
@Component
class UpdateCalendarEventTool(
    private val calendarService: CalendarService
) : Tool {

    override val name = "updateCalendarEvent"

    override val description =
        "Modifica un evento de calendario ya existente (título, fecha, hora, lugar o descripción). " +
            "Necesita el id del evento, que se obtiene primero con getCalendarEvents."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "eventId" to mapOf("type" to "integer", "description" to "Id del evento a modificar."),
            "title" to mapOf("type" to "string", "description" to "Nuevo título. Opcional."),
            "startAt" to mapOf("type" to "string", "description" to "Nueva fecha/hora de inicio, formato ISO. Opcional."),
            "endAt" to mapOf("type" to "string", "description" to "Nueva fecha/hora de fin, formato ISO. Opcional."),
            "description" to mapOf("type" to "string", "description" to "Nueva descripción. Opcional."),
            "location" to mapOf("type" to "string", "description" to "Nuevo lugar. Opcional.")
        ),
        "required" to listOf("eventId")
    )

    override fun execute(args: Map<String, Any?>): String {
        val eventId = (args["eventId"] as? Number)?.toLong()
            ?: (args["eventId"] as? String)?.toLongOrNull()
            ?: return "Falta el id del evento a modificar."

        return try {
            val updated = calendarService.updateEvent(
                eventId = eventId,
                title = (args["title"] as? String)?.trim(),
                description = (args["description"] as? String)?.trim(),
                startAt = ToolDateParsing.parseDateTime(args["startAt"] as? String),
                endAt = ToolDateParsing.parseDateTime(args["endAt"] as? String),
                location = (args["location"] as? String)?.trim()
            )

            if (updated) "Evento #$eventId actualizado." else "No se ha podido actualizar el evento #$eventId."
        } catch (e: IllegalArgumentException) {
            "No se ha podido actualizar el evento: ${e.message}"
        }
    }
}

/** Tool que cancela (borrado lógico) un evento de calendario. */
@Component
class DeleteCalendarEventTool(
    private val calendarService: CalendarService
) : Tool {

    override val name = "deleteCalendarEvent"

    override val description =
        "Elimina (cancela) un evento del calendario del usuario. Necesita el id del evento, " +
            "que se obtiene primero con getCalendarEvents."

    override val riskLevel = RiskLevel.RECOVERABLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "eventId" to mapOf("type" to "integer", "description" to "Id del evento a eliminar.")
        ),
        "required" to listOf("eventId")
    )

    override fun execute(args: Map<String, Any?>): String {
        val eventId = (args["eventId"] as? Number)?.toLong()
            ?: (args["eventId"] as? String)?.toLongOrNull()
            ?: return "Falta el id del evento a eliminar."

        val deleted = calendarService.deleteEvent(eventId)

        return if (deleted) "Evento #$eventId eliminado." else "No se ha encontrado el evento #$eventId."
    }
}
