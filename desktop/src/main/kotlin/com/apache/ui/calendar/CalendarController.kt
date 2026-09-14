package com.apache.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.model.CalendarEventDto
import com.apache.model.CreateCalendarEventDto
import com.apache.model.UpdateCalendarEventDto
import com.apache.network.cancelCalendarEvent
import com.apache.network.completeCalendarEvent
import com.apache.network.createCalendarEvent
import com.apache.network.fetchCalendarEvents
import com.apache.network.updateCalendarEvent
import com.apache.util.calendarDate
import com.apache.util.calendarStartAt
import com.apache.util.calendarTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Controla el estado del formulario, la lista de eventos y las llamadas CRUD al Core. */
class CalendarController(private val scope: CoroutineScope) {

    var events by mutableStateOf<List<CalendarEventDto>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    // --- Formulario de creación / edición ---
    var title by mutableStateOf("")
    var date by mutableStateOf(calendarDate())
    var time by mutableStateOf(calendarTime())
    var location by mutableStateOf("")

    var editingEventId by mutableStateOf<Long?>(null)
        private set

    // --- Diálogos ---
    var eventPendingCancellation by mutableStateOf<CalendarEventDto?>(null)
    var showCompletedArchive by mutableStateOf(false)

    val activeEvents: List<CalendarEventDto>
        get() = events.filter { it.status != "completed" && it.status != "cancelled" }.sortedBy { it.startAt }

    val completedEvents: List<CalendarEventDto>
        get() = events.filter { it.status == "completed" }.sortedByDescending { it.startAt }

    fun resetForm() {
        editingEventId = null
        title = ""
        location = ""
        date = calendarDate()
        time = calendarTime()
    }

    fun beginEdit(event: CalendarEventDto) {
        val start = LocalDateTime.parse(event.startAt)

        editingEventId = event.id
        title = event.title
        date = start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        time = start.format(DateTimeFormatter.ofPattern("HH:mm"))
        location = event.location.orEmpty()
        error = null
    }

    fun load() {
        scope.launch {
            isLoading = true
            error = null

            try {
                events = withContext(Dispatchers.IO) { fetchCalendarEvents() }
            } catch (_: Exception) {
                error = "No se ha podido conectar con el Core. Arráncalo y vuelve a intentarlo."
            } finally {
                isLoading = false
            }
        }
    }

    fun save() {
        if (title.isBlank()) {
            error = "Escribe un título para el evento."
            return
        }

        val startAt = try {
            calendarStartAt(date, time)
        } catch (_: Exception) {
            error = "La fecha o la hora no tienen un formato válido."
            return
        }

        val eventId = editingEventId

        scope.launch {
            isLoading = true
            error = null

            try {
                if (eventId == null) {
                    val created = withContext(Dispatchers.IO) {
                        createCalendarEvent(
                            CreateCalendarEventDto(
                                title = title.trim(),
                                startAt = startAt,
                                location = location.trim().ifBlank { null }
                            )
                        )
                    }
                    events = (events + created).sortedBy { it.startAt }
                } else {
                    val updated = withContext(Dispatchers.IO) {
                        updateCalendarEvent(
                            eventId = eventId,
                            event = UpdateCalendarEventDto(
                                title = title.trim(),
                                startAt = startAt,
                                location = location.trim().ifBlank { null }
                            )
                        )
                    }
                    events = events.map { if (it.id == updated.id) updated else it }.sortedBy { it.startAt }
                }

                resetForm()
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() }
                    ?: if (eventId == null) "No se ha podido crear el evento." else "No se ha podido actualizar el evento."
            } finally {
                isLoading = false
            }
        }
    }

    fun complete(event: CalendarEventDto) {
        scope.launch {
            isLoading = true
            error = null

            try {
                val completed = withContext(Dispatchers.IO) { completeCalendarEvent(event.id) }
                events = events.map { if (it.id == completed.id) completed else it }.sortedBy { it.startAt }
            } catch (e: Exception) {
                error = e.message ?: "No se ha podido completar el evento."
            } finally {
                isLoading = false
            }
        }
    }

    /** La cancelación es lógica: el evento sigue existiendo en MySQL, pero deja de mostrarse. */
    fun cancel(event: CalendarEventDto) {
        scope.launch {
            isLoading = true
            error = null

            try {
                withContext(Dispatchers.IO) { cancelCalendarEvent(event.id) }
                events = events.filter { it.id != event.id }

                if (editingEventId == event.id) resetForm()
            } catch (e: Exception) {
                error = e.message ?: "No se ha podido cancelar el evento."
            } finally {
                isLoading = false
            }
        }
    }
}
