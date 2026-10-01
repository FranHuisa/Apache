package com.apache.ui.schedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.model.CalendarEventDto
import com.apache.model.CreateCalendarEventDto
import com.apache.model.UpdateCalendarEventDto
import com.apache.network.cancelCalendarEvent
import com.apache.network.completeCalendarEvent
import com.apache.network.createCalendarEvent
import com.apache.network.fetchCalendarEventsBetween
import com.apache.network.updateCalendarEvent
import com.apache.ui.chat.ChatController
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controla la ventana Horario: el día seleccionado, sus bloques, el
 * formulario para crear/editar bloques y la petición a Apache para que
 * organice el día.
 *
 * Los bloques del horario son eventos normales del calendario (con hora de
 * inicio y fin), así que todo lo que se crea aquí aparece también en la
 * sección Calendario, y viceversa.
 */
class ScheduleController(
    private val scope: CoroutineScope,
    private val chat: ChatController
) {

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    var events by mutableStateOf<List<CalendarEventDto>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    // --- Formulario de bloque ---
    var title by mutableStateOf("")
    var startTime by mutableStateOf(defaultStart().format(HOUR))
    var endTime by mutableStateOf(defaultStart().plusHours(1).format(HOUR))
    var note by mutableStateOf("")

    var editingEventId by mutableStateOf<Long?>(null)
        private set

    // --- Organizar con Apache ---
    var planRequest by mutableStateOf("")
    var isPlanning by mutableStateOf(false)
        private set
    var planReply by mutableStateOf<String?>(null)
        private set

    /** Bloques del día seleccionado, ordenados por hora de inicio. */
    val dayBlocks: List<CalendarEventDto>
        get() = events.filter { it.status != "cancelled" }.sortedBy { it.startAt }

    /** Total de minutos planificados en el día (los bloques sin fin cuentan 1 h). */
    val plannedMinutes: Long
        get() = dayBlocks.sumOf { Duration.between(blockStart(it), blockEnd(it)).toMinutes().coerceAtLeast(0) }

    val isToday: Boolean
        get() = selectedDate == LocalDate.now()

    val dateLabel: String
        get() = selectedDate.format(DAY_LABEL).replaceFirstChar { it.titlecase(SPANISH) }

    // --- Navegación entre días ---

    fun previousDay() = selectDate(selectedDate.minusDays(1))

    fun nextDay() = selectDate(selectedDate.plusDays(1))

    fun goToToday() = selectDate(LocalDate.now())

    private fun selectDate(date: LocalDate) {
        selectedDate = date
        resetForm()
        planReply = null
        load()
    }

    fun load() {
        val date = selectedDate

        scope.launch {
            isLoading = true
            error = null

            try {
                val loaded = withContext(Dispatchers.IO) {
                    fetchCalendarEventsBetween(
                        from = date.atStartOfDay().format(ISO),
                        to = date.atTime(23, 59, 59).format(ISO)
                    )
                }
                // Si el usuario ha cambiado de día mientras se cargaba, descartamos la respuesta.
                if (date == selectedDate) events = loaded
            } catch (_: Exception) {
                error = "No se ha podido cargar el horario. Comprueba que el Core está arrancado."
            } finally {
                isLoading = false
            }
        }
    }

    // --- Formulario ---

    fun resetForm() {
        editingEventId = null
        title = ""
        note = ""
        val start = defaultStart()
        startTime = start.format(HOUR)
        endTime = start.plusHours(1).format(HOUR)
    }

    /** Prepara el formulario para un bloque nuevo que empieza a una hora concreta (clic en la línea de tiempo). */
    fun beginNewAt(hour: Int) {
        resetForm()
        val start = LocalTime.of(hour.coerceIn(0, 23), 0)
        startTime = start.format(HOUR)
        endTime = (if (hour >= 23) LocalTime.of(23, 59) else start.plusHours(1)).format(HOUR)
    }

    fun beginEdit(event: CalendarEventDto) {
        editingEventId = event.id
        title = event.title
        note = event.description.orEmpty()
        startTime = blockStart(event).format(HOUR)
        endTime = blockEnd(event).format(HOUR)
        error = null
    }

    fun save() {
        if (title.isBlank()) {
            error = "Escribe qué vas a hacer en este bloque."
            return
        }

        val start = parseTime(startTime)
        val end = parseTime(endTime)

        if (start == null || end == null) {
            error = "Las horas deben tener el formato HH:mm (ej.: 09:30)."
            return
        }

        if (!end.isAfter(start)) {
            error = "La hora de fin debe ser posterior a la de inicio."
            return
        }

        val startAt = selectedDate.atTime(start).format(ISO)
        val endAt = selectedDate.atTime(end).format(ISO)
        val eventId = editingEventId
        val cleanTitle = title.trim()
        val cleanNote = note.trim().ifBlank { null }

        scope.launch {
            isLoading = true
            error = null

            try {
                withContext(Dispatchers.IO) {
                    if (eventId == null) {
                        createCalendarEvent(
                            CreateCalendarEventDto(
                                title = cleanTitle,
                                startAt = startAt,
                                endAt = endAt,
                                description = cleanNote
                            )
                        )
                    } else {
                        updateCalendarEvent(
                            eventId = eventId,
                            event = UpdateCalendarEventDto(
                                title = cleanTitle,
                                startAt = startAt,
                                endAt = endAt,
                                description = cleanNote
                            )
                        )
                    }
                }
                resetForm()
                load()
            } catch (e: Exception) {
                error = e.message ?: "No se ha podido guardar el bloque."
            } finally {
                isLoading = false
            }
        }
    }

    fun complete(event: CalendarEventDto) = runAction("No se ha podido completar el bloque.") {
        completeCalendarEvent(event.id)
    }

    /** Borrado lógico, igual que en Calendario: el evento queda como "cancelled" en MySQL. */
    fun remove(event: CalendarEventDto) = runAction("No se ha podido eliminar el bloque.") {
        cancelCalendarEvent(event.id)
    }

    private fun runAction(errorMessage: String, action: () -> Unit) {
        scope.launch {
            isLoading = true
            error = null

            try {
                withContext(Dispatchers.IO) { action() }
                resetForm()
                load()
            } catch (e: Exception) {
                error = e.message ?: errorMessage
            } finally {
                isLoading = false
            }
        }
    }

    // --- Organizar con Apache ---

    /**
     * Pide a Apache (Gemini) que organice el día seleccionado. Se envía por la
     * misma conversación del chat, así que la petición y la respuesta también
     * quedan en la sección Chat. Al terminar se recargan los bloques.
     */
    fun planWithApache() {
        val request = planRequest.trim()

        if (request.isBlank()) {
            error = "Cuéntale a Apache qué tienes que hacer ese día."
            return
        }

        if (isPlanning || chat.isLoading) return

        val date = selectedDate
        val message =
            "Organiza mi horario del ${date.format(DAY_LABEL)} (${date}). $request"

        scope.launch {
            isPlanning = true
            error = null
            planReply = null

            try {
                val reply = chat.runTurn(message, speak = false)

                if (reply == null) {
                    error = "Apache no ha podido organizar el día. Revisa la conexión con el Core."
                } else {
                    planReply = reply
                    planRequest = ""
                }
            } finally {
                isPlanning = false
                if (date == selectedDate) load()
            }
        }
    }

    companion object {
        private val SPANISH = Locale("es", "ES")
        val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        /** Próxima hora en punto (ej.: a las 10:20 propone 11:00), sin pasar de las 22:00. */
        private fun defaultStart(): LocalTime {
            val nextHour = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            return if (nextHour.hour in 6..22) nextHour else LocalTime.of(9, 0)
        }

        /** Acepta "9:30" o "09:30". */
        fun parseTime(value: String): LocalTime? {
            val trimmed = value.trim()
            return try {
                LocalTime.parse(if (trimmed.length == 4 && trimmed[1] == ':') "0$trimmed" else trimmed, HOUR)
            } catch (_: Exception) {
                null
            }
        }

        fun blockStart(event: CalendarEventDto): LocalTime = LocalDateTime.parse(event.startAt).toLocalTime()

        /** Fin del bloque; si el evento no tiene hora de fin se asume 1 hora (sin pasar de medianoche). */
        fun blockEnd(event: CalendarEventDto): LocalTime {
            val start = LocalDateTime.parse(event.startAt)
            val end = event.endAt?.let { LocalDateTime.parse(it) } ?: start.plusHours(1)
            return if (end.toLocalDate().isAfter(start.toLocalDate())) LocalTime.of(23, 59) else end.toLocalTime()
        }
    }
}
