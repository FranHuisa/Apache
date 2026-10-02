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
