package com.apache.api

import com.apache.ApacheDefaults
import com.apache.database.repository.CalendarEventRecord
import com.apache.database.service.CalendarService

import java.time.LocalDateTime

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * API que usa la aplicación de escritorio para consultar,
 * crear, editar y gestionar eventos.
 */
@RestController
@RequestMapping("/api/calendar")
class CalendarController(
    private val calendarService: CalendarService
) {

    @GetMapping("/events")
    fun listEvents(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: LocalDateTime?,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: LocalDateTime?
    ): List<CalendarEventResponse> {

        val start =
            from ?: LocalDateTime.now()
                .toLocalDate()
                .atStartOfDay()

        val end =
            to ?: start
                .plusDays(30)
                .withHour(23)
                .withMinute(59)
                .withSecond(59)

        return calendarService
            .getEvents(
                ApacheDefaults.DEFAULT_USER_ID,
                start,
                end
            )
            .map { it.toResponse() }
    }

    @PostMapping("/events")
    fun createEvent(
        @RequestBody request: CreateCalendarEventRequest
    ): CalendarEventResponse =
        calendarService
            .createEvent(
                userId = ApacheDefaults.DEFAULT_USER_ID,
                title = request.title,
                description = request.description,
                startAt = request.startAt,
                endAt = request.endAt,
                location = request.location,
                allDay = request.allDay
            )
            .toResponse()

    @PutMapping("/events/{id}")
    fun updateEvent(
        @PathVariable id: Long,
        @RequestBody request: UpdateCalendarEventRequest
    ): CalendarEventResponse {

        calendarService.updateEvent(
            eventId = id,
            title = request.title,
            description = request.description,
            startAt = request.startAt,
            endAt = request.endAt,
            location = request.location
        )

        return calendarService.getEvent(id)
            ?.toResponse()
            ?: error("No se ha podido recuperar el evento actualizado (id=$id).")
    }

    /**
     * Marca un evento como completado.
     */
    @PostMapping("/events/{id}/complete")
    fun completeEvent(
        @PathVariable id: Long
    ): CalendarEventResponse {

        calendarService.completeEvent(id)

        return calendarService.getEvent(id)
            ?.toResponse()
            ?: error("No se ha podido recuperar el evento completado (id=$id).")
    }

    /**
     * Cancela un evento mediante borrado lógico.
     *
     * El evento no se elimina físicamente de la base de datos.
     */
    @PostMapping("/events/{id}/cancel")
    fun cancelEvent(
        @PathVariable id: Long
    ): CalendarEventResponse {

        calendarService.deleteEvent(id)

        return calendarService.getEvent(id)
            ?.toResponse()
            ?: error("No se ha podido recuperar el evento cancelado (id=$id).")
    }
}

data class CreateCalendarEventRequest(
    val title: String,
    val startAt: LocalDateTime,
    val description: String? = null,
    val endAt: LocalDateTime? = null,
    val location: String? = null,
    val allDay: Boolean = false
)

data class UpdateCalendarEventRequest(
    val title: String? = null,
    val description: String? = null,
    val startAt: LocalDateTime? = null,
    val endAt: LocalDateTime? = null,
    val location: String? = null
)

data class CalendarEventResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val startAt: String,
    val endAt: String?,
    val location: String?,
    val allDay: Boolean,
    val status: String
)

private fun CalendarEventRecord.toResponse() = CalendarEventResponse(
    id = id,
    title = title,
    description = description,
    startAt = startAt.toString(),
    endAt = endAt?.toString(),
    location = location,
    allDay = allDay,
    status = status
)
