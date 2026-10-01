package com.apache.tools

import com.apache.ApacheDefaults
import com.apache.database.service.CalendarService
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Component

/**
 * Tool que organiza el horario de un día: crea varios bloques (eventos con
 * hora de inicio y fin) de una sola vez.
 *
 * Se podría hacer con varias llamadas a createCalendarEvent, pero eso obliga
 * a una vuelta a Gemini por cada bloque. Con esta tool, "organízame el día"
 * se resuelve en una única llamada, y los bloques aparecen directamente en la
 * ventana Horario del Desktop (que lee los mismos eventos del calendario).
 */
@Component
class PlanDayScheduleTool(
    private val calendarService: CalendarService
) : Tool {

    override val name = "planDaySchedule"

    override val description =
        "Organiza el horario de un día creando varios bloques de tiempo a la vez (estudio, " +
            "trabajo, gimnasio, comidas, descansos...). Úsalo cuando el usuario pida organizar, " +
            "planificar o hacer un horario para un día. Antes consulta con getCalendarEvents lo " +
            "que ya tiene ese día para no solapar bloques, respeta las horas que el usuario indique, " +
            "deja pequeños descansos entre bloques largos y no planifiques nada antes de la hora " +
            "actual si el día es hoy."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "date" to mapOf(
                "type" to "string",
                "description" to "Día a organizar, formato 'yyyy-MM-dd'."
            ),
            "blocks" to mapOf(
                "type" to "array",
                "description" to "Bloques del horario, en orden cronológico.",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "title" to mapOf(
                            "type" to "string",
                            "description" to "Qué se hace en el bloque, ej: 'Estudiar matemáticas'."
                        ),
                        "start" to mapOf(
                            "type" to "string",
                            "description" to "Hora de inicio, formato 'HH:mm'."
                        ),
                        "end" to mapOf(
                            "type" to "string",
                            "description" to "Hora de fin, formato 'HH:mm'."
                        ),
                        "description" to mapOf(
                            "type" to "string",
                            "description" to "Detalles o consejo para el bloque. Opcional."
                        )
                    ),
                    "required" to listOf("title", "start", "end")
                )
            )
        ),
        "required" to listOf("date", "blocks")
    )

    override fun execute(args: Map<String, Any?>): String {

        val date = try {
            LocalDate.parse((args["date"] as? String)?.trim().orEmpty())
        } catch (_: Exception) {
            return "La fecha del horario no es válida. Usa el formato yyyy-MM-dd."
        }

        val blocks = (args["blocks"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

        if (blocks.isEmpty()) {
            return "No se ha indicado ningún bloque para el horario."
        }

        val created = mutableListOf<String>()
        val failed = mutableListOf<String>()

        blocks.forEach { block ->
            val title = (block["title"] as? String)?.trim().orEmpty()
            val start = parseTime(block["start"] as? String)
            val end = parseTime(block["end"] as? String)

            if (title.isBlank() || start == null || end == null) {
                failed.add("'${title.ifBlank { "sin título" }}': faltan el título o las horas")
                return@forEach
            }

            if (!end.isAfter(start)) {
                failed.add("'$title': la hora de fin debe ser posterior a la de inicio")
                return@forEach
            }

            try {
                val event = calendarService.createEvent(
                    userId = ApacheDefaults.DEFAULT_USER_ID,
                    title = title,
                    description = (block["description"] as? String)?.trim()?.ifBlank { null },
                    startAt = date.atTime(start),
                    endAt = date.atTime(end)
                )

                created.add("#${event.id} · ${start.format(HOUR)}-${end.format(HOUR)} · $title")
            } catch (e: Exception) {
                failed.add("'$title': ${e.message}")
            }
        }

        return buildString {
            if (created.isNotEmpty()) {
                appendLine("Horario del ${date.format(DAY)} creado con ${created.size} bloque(s):")
                created.forEach { appendLine(it) }
            }
            if (failed.isNotEmpty()) {
                appendLine("No se han podido crear estos bloques:")
                failed.forEach { appendLine(it) }
            }
            append("El usuario puede verlo y editarlo en la sección Horario de Apache.")
        }
    }

    /** Acepta "HH:mm", "H:mm" o "HH:mm:ss" (Gemini no siempre es consistente). */
    private fun parseTime(value: String?): LocalTime? {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) return null

        return try {
            LocalTime.parse(if (trimmed.length == 4 && trimmed[1] == ':') "0$trimmed" else trimmed)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
