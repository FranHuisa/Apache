package com.apache.util

import androidx.compose.ui.graphics.Color
import com.apache.model.CalendarEventDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun calendarDateTime(): String =
    LocalDateTime.now()
        .plusHours(1)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

fun calendarDate(): String =
    LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

fun calendarTime(): String =
    LocalDateTime.now().plusHours(1).withMinute(0).format(DateTimeFormatter.ofPattern("HH:mm"))

fun calendarStartAt(date: String, time: String): String =
    LocalDateTime.parse(
        "${date.trim()} ${time.trim()}",
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

/**
 * Devuelve el estado que se debe mostrar en la interfaz.
 *
 * `overdue` no se guarda en la base de datos porque depende de la hora actual.
 * Si el evento tiene hora de finalización, se utiliza esa hora para determinar
 * si ha quedado atrasado. En caso contrario se utiliza la hora de inicio.
 */
fun calendarDisplayStatus(event: CalendarEventDto): String {
    return when (event.status) {
        "completed" -> "Completado"
        "cancelled" -> "Cancelado"
        else -> {
            val referenceTime = event.endAt?.let { LocalDateTime.parse(it) }
                ?: LocalDateTime.parse(event.startAt)

            if (referenceTime.isBefore(LocalDateTime.now())) "Atrasado" else "Confirmado"
        }
    }
}

fun calendarStatusColor(event: CalendarEventDto): Color {
    return when (calendarDisplayStatus(event)) {
        "Completado" -> Color(0xFF2E7D5A)
        "Atrasado" -> Color(0xFF8A5A2B)
        "Cancelado" -> Color(0xFF6A3030)
        else -> Color(0xFF1E5E3A)
    }
}

fun calendarStatusTextColor(event: CalendarEventDto): Color {
    return when (calendarDisplayStatus(event)) {
        "Completado" -> Color(0xFFB8F0D0)
        "Atrasado" -> Color(0xFFFFD39A)
        "Cancelado" -> Color(0xFFFFB5B5)
        else -> Color(0xFFA7E8BE)
    }
}
