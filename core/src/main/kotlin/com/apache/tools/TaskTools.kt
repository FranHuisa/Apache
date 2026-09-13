package com.apache.tools

import com.apache.ApacheDefaults
import com.apache.database.repository.TaskRecord
import com.apache.database.service.TaskService
import org.springframework.stereotype.Component

private fun TaskRecord.describe(): String {
    val duePart = dueAt?.let { " · vence ${ToolDateParsing.formatForUser(it)}" } ?: ""
    val statusMark = if (status == "completed") "[hecha] " else ""

    return "#$id · $statusMark$title · prioridad: $priority$duePart"
}

/** Tool que crea una tarea nueva para el usuario. */
@Component
class CreateTaskTool(
    private val taskService: TaskService
) : Tool {

    override val name = "createTask"

    override val description =
        "Crea una tarea pendiente para el usuario. A diferencia de un evento de calendario, " +
            "una tarea no ocupa un periodo de tiempo concreto: es algo que hay que hacer, " +
            "opcionalmente con una fecha límite y una prioridad."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string", "description" to "Título de la tarea."),
            "description" to mapOf("type" to "string", "description" to "Detalles adicionales. Opcional."),
            "priority" to mapOf(
                "type" to "string",
                "description" to "Prioridad: 'low', 'medium' o 'high'. Opcional, por defecto 'medium'."
            ),
            "dueAt" to mapOf(
                "type" to "string",
                "description" to "Fecha límite, formato 'yyyy-MM-dd' o 'yyyy-MM-ddTHH:mm'. Opcional."
            )
        ),
        "required" to listOf("title")
    )

    override fun execute(args: Map<String, Any?>): String {
        val title = (args["title"] as? String)?.trim()
            ?: return "Falta el título de la tarea."

        return try {
            val task = taskService.createTask(
                userId = ApacheDefaults.DEFAULT_USER_ID,
                title = title,
                description = (args["description"] as? String)?.trim(),
                priority = args["priority"] as? String,
                dueAt = ToolDateParsing.parseDateTime(args["dueAt"] as? String)
            )

            "Tarea creada: ${task.describe()}"
        } catch (e: IllegalArgumentException) {
            "No se ha podido crear la tarea: ${e.message}"
        }
    }
}

/** Tool de solo lectura que lista las tareas del usuario. */
@Component
class ListTasksTool(
    private val taskService: TaskService
) : Tool {

    override val name = "listTasks"

    override val description =
        "Lista las tareas pendientes del usuario. Si el usuario pide ver también las ya " +
            "completadas, pasa includeCompleted=true."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "includeCompleted" to mapOf(
                "type" to "boolean",
                "description" to "Si es true, incluye también las tareas ya completadas. Por defecto false."
            )
        )
    )

    override fun execute(args: Map<String, Any?>): String {
        val includeCompleted = (args["includeCompleted"] as? Boolean) ?: false

        val tasks = taskService.listTasks(ApacheDefaults.DEFAULT_USER_ID, includeCompleted)

        if (tasks.isEmpty()) {
            return if (includeCompleted) "No hay tareas." else "No hay tareas pendientes."
        }

        return tasks.joinToString("\n") { it.describe() }
    }
}

/** Tool que marca una tarea como completada. */
@Component
class CompleteTaskTool(
    private val taskService: TaskService
) : Tool {

    override val name = "completeTask"

    override val description =
        "Marca una tarea como completada. Necesita el id de la tarea, que se obtiene primero con listTasks."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "taskId" to mapOf("type" to "integer", "description" to "Id de la tarea a completar.")
        ),
        "required" to listOf("taskId")
    )

    override fun execute(args: Map<String, Any?>): String {
        val taskId = (args["taskId"] as? Number)?.toLong()
            ?: (args["taskId"] as? String)?.toLongOrNull()
            ?: return "Falta el id de la tarea."

        return try {
            val completed = taskService.completeTask(taskId)
            if (completed) "Tarea #$taskId marcada como completada." else "No se ha podido completar la tarea #$taskId."
        } catch (e: IllegalArgumentException) {
            "No se ha podido completar la tarea: ${e.message}"
        }
    }
}

/** Tool que modifica una tarea existente. Solo cambia los campos que se indiquen. */
@Component
class UpdateTaskTool(
    private val taskService: TaskService
) : Tool {

    override val name = "updateTask"

    override val description =
        "Modifica una tarea existente (título, descripción, prioridad o fecha límite). " +
            "Necesita el id de la tarea, que se obtiene primero con listTasks."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "taskId" to mapOf("type" to "integer", "description" to "Id de la tarea a modificar."),
            "title" to mapOf("type" to "string", "description" to "Nuevo título. Opcional."),
            "description" to mapOf("type" to "string", "description" to "Nueva descripción. Opcional."),
            "priority" to mapOf("type" to "string", "description" to "Nueva prioridad: 'low', 'medium' o 'high'. Opcional."),
            "dueAt" to mapOf("type" to "string", "description" to "Nueva fecha límite. Opcional.")
        ),
        "required" to listOf("taskId")
    )

    override fun execute(args: Map<String, Any?>): String {
        val taskId = (args["taskId"] as? Number)?.toLong()
            ?: (args["taskId"] as? String)?.toLongOrNull()
            ?: return "Falta el id de la tarea a modificar."

        return try {
            val updated = taskService.updateTask(
                taskId = taskId,
                title = (args["title"] as? String)?.trim(),
                description = (args["description"] as? String)?.trim(),
                priority = args["priority"] as? String,
                dueAt = ToolDateParsing.parseDateTime(args["dueAt"] as? String)
            )

            if (updated) "Tarea #$taskId actualizada." else "No se ha podido actualizar la tarea #$taskId."
        } catch (e: IllegalArgumentException) {
            "No se ha podido actualizar la tarea: ${e.message}"
        }
    }
}

/** Tool que elimina una tarea. */
@Component
class DeleteTaskTool(
    private val taskService: TaskService
) : Tool {

    override val name = "deleteTask"

    override val description =
        "Elimina definitivamente una tarea. Necesita el id de la tarea, que se obtiene primero con listTasks."

    override val riskLevel = RiskLevel.RECOVERABLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "taskId" to mapOf("type" to "integer", "description" to "Id de la tarea a eliminar.")
        ),
        "required" to listOf("taskId")
    )

    override fun execute(args: Map<String, Any?>): String {
        val taskId = (args["taskId"] as? Number)?.toLong()
            ?: (args["taskId"] as? String)?.toLongOrNull()
            ?: return "Falta el id de la tarea a eliminar."

        val deleted = taskService.deleteTask(taskId)

        return if (deleted) "Tarea #$taskId eliminada." else "No se ha encontrado la tarea #$taskId."
    }
}
