package com.apache.mobile.data

import java.time.LocalDateTime

/** Imagen que Apache enseña en el chat (la descarga la propia pantalla del chat). */
data class ChatImage(val url: String, val title: String = "", val sourceUrl: String? = null)

/** Mensaje visible del chat (los mensajes internos de herramientas no se muestran). */
data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val images: List<ChatImage> = emptyList()
)

data class ConversationSummary(val id: Long, val title: String, val updatedAt: String)

data class MemoryFact(
    val id: Long,
    val type: String,
    val key: String,
    val value: String,
    val importance: Double
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime?,
    val status: String
)

data class Reminder(
    val id: Long,
    val title: String,
    val triggerAt: LocalDateTime,
    val status: String
)

/** Elemento de una lista ("compra", "tareas"...). */
data class TaskItem(
    val id: Long,
    val list: String,
    val title: String,
    val done: Boolean
)

/** Resumen de una lista: cuántos quedan y cuántos hay en total. */
data class TaskListSummary(val name: String, val pending: Int, val total: Int)

/** Rutina por voz: al decir [trigger], Apache hace todos los [steps]. */
data class Routine(val id: Long, val trigger: String, val steps: List<String>, val lastRun: String?)

/** Entrada del diario de un día. */
data class DiaryEntry(val day: java.time.LocalDate, val text: String, val mood: String?)
