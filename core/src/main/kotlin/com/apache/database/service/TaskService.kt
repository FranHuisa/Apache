package com.apache.database.service

import com.apache.database.repository.TaskRecord
import com.apache.database.repository.TaskRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Service

/** Prioridades válidas para una tarea. */
object TaskPriority {
    const val LOW = "low"
    const val MEDIUM = "medium"
    const val HIGH = "high"

    val ALL = setOf(LOW, MEDIUM, HIGH)

    /** Normaliza cualquier texto de prioridad recibido (de Gemini, de la UI...) a un valor válido. */
    fun normalize(value: String?): String {
        val normalized = value?.trim()?.lowercase()
        return if (normalized in ALL) normalized!! else MEDIUM
    }
}

/** Capa de servicio de tareas. */
@Service
class TaskService(
    private val taskRepository: TaskRepository
) {

    fun createTask(
        userId: Long,
        title: String,
        description: String? = null,
        priority: String? = null,
        dueAt: LocalDateTime? = null
    ): TaskRecord {

        require(title.isNotBlank()) {
            "El título de la tarea no puede estar vacío."
        }

        val taskId = taskRepository.create(
            userId = userId,
            title = title,
            description = description,
            priority = TaskPriority.normalize(priority),
            dueAt = dueAt
        )

        return taskRepository.findById(taskId)
            ?: error("No se ha podido recuperar la tarea recién creada (id=$taskId).")
    }

    fun listTasks(userId: Long, includeCompleted: Boolean = false): List<TaskRecord> =
        taskRepository.findByUser(userId, includeCompleted)

    fun getTask(taskId: Long): TaskRecord? = taskRepository.findById(taskId)

    fun completeTask(taskId: Long): Boolean {
        require(taskRepository.findById(taskId) != null) {
            "La tarea con id=$taskId no existe."
        }
        return taskRepository.markCompleted(taskId)
    }

    fun updateTask(
        taskId: Long,
        title: String? = null,
        description: String? = null,
        priority: String? = null,
        dueAt: LocalDateTime? = null
    ): Boolean {

        require(taskRepository.findById(taskId) != null) {
            "La tarea con id=$taskId no existe."
        }

        return taskRepository.update(
            taskId = taskId,
            title = title,
            description = description,
            priority = priority?.let { TaskPriority.normalize(it) },
            dueAt = dueAt
        )
    }

    fun deleteTask(taskId: Long): Boolean = taskRepository.delete(taskId)
}
