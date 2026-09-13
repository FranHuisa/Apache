package com.apache.tools

import com.apache.ApacheDefaults
import com.apache.database.repository.ReminderRecord
import com.apache.database.service.ReminderService
import org.springframework.stereotype.Component

private fun ReminderRecord.describe(): String =
    "#$id · $title · ${ToolDateParsing.formatForUser(triggerAt)}"

/** Tool que crea un recordatorio nuevo para el usuario. */
@Component
class CreateReminderTool(
    private val reminderService: ReminderService
) : Tool {

    override val name = "createReminder"

    override val description =
        "Crea un recordatorio para que Apache avise al usuario en un momento futuro concreto. " +
            "Úsalo cuando el usuario pida que le recuerdes algo a una hora determinada."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string", "description" to "Qué hay que recordar."),
            "message" to mapOf(
                "type" to "string",
                "description" to "Texto exacto del aviso. Opcional; si no se indica, se usa el título."
            ),
            "triggerAt" to mapOf(
                "type" to "string",
                "description" to "Fecha y hora en que debe saltar el recordatorio, formato ISO 'yyyy-MM-ddTHH:mm'. Debe ser futura."
            )
        ),
        "required" to listOf("title", "triggerAt")
    )

    override fun execute(args: Map<String, Any?>): String {
        val title = (args["title"] as? String)?.trim()
            ?: return "Falta el título del recordatorio."

        val triggerAt = ToolDateParsing.parseDateTime(args["triggerAt"] as? String)
            ?: return "No he podido entender la fecha y hora del recordatorio."

        return try {
            val reminder = reminderService.createReminder(
                userId = ApacheDefaults.DEFAULT_USER_ID,
                title = title,
                message = (args["message"] as? String)?.trim(),
                triggerAt = triggerAt
            )

            "Recordatorio creado: ${reminder.describe()}"
        } catch (e: IllegalArgumentException) {
            "No se ha podido crear el recordatorio: ${e.message}"
        }
    }
}

/** Tool de solo lectura que lista los recordatorios pendientes del usuario. */
@Component
class ListRemindersTool(
    private val reminderService: ReminderService
) : Tool {

    override val name = "listReminders"

    override val description = "Lista los recordatorios pendientes (todavía no disparados) del usuario."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any?>()
    )

    override fun execute(args: Map<String, Any?>): String {
        val reminders = reminderService.listReminders(ApacheDefaults.DEFAULT_USER_ID)

        if (reminders.isEmpty()) {
            return "No hay recordatorios pendientes."
        }

        return reminders.joinToString("\n") { it.describe() }
    }
}

/** Tool que cancela un recordatorio antes de que se dispare. */
@Component
class CancelReminderTool(
    private val reminderService: ReminderService
) : Tool {

    override val name = "cancelReminder"

    override val description =
        "Cancela un recordatorio pendiente. Necesita el id, que se obtiene primero con listReminders."

    override val riskLevel = RiskLevel.RECOVERABLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "reminderId" to mapOf("type" to "integer", "description" to "Id del recordatorio a cancelar.")
        ),
        "required" to listOf("reminderId")
    )

    override fun execute(args: Map<String, Any?>): String {
        val reminderId = (args["reminderId"] as? Number)?.toLong()
            ?: (args["reminderId"] as? String)?.toLongOrNull()
            ?: return "Falta el id del recordatorio a cancelar."

        val cancelled = reminderService.cancelReminder(reminderId)

        return if (cancelled) "Recordatorio #$reminderId cancelado." else "No se ha encontrado el recordatorio #$reminderId."
    }
}
