package com.apache.database.service

import com.apache.database.repository.CalendarEventRecord
import com.apache.database.repository.CalendarRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Service

/**
 * Capa de servicio de calendario y eventos.
 *
 * Igual que [com.apache.memory.MemoryService], concentra la validación y las reglas de negocio; el
 * Repository solo sabe hablar con Exposed/MySQL.
 */
@Service
class CalendarService(private val calendarRepository: CalendarRepository) {

    fun createEvent(
            userId: Long,
            title: String,
            description: String? = null,
            startAt: LocalDateTime,
            endAt: LocalDateTime? = null,
            location: String? = null,
            allDay: Boolean = false
    ): CalendarEventRecord {

        require(title.isNotBlank()) { "El título del evento no puede estar vacío." }

        if (endAt != null) {
            require(!endAt.isBefore(startAt)) {
                "La fecha de fin del evento no puede ser anterior a la de inicio."
            }
        }

        val calendarId = calendarRepository.getOrCreateDefaultCalendar(userId)

        val eventId =
                calendarRepository.createEvent(
                        calendarId = calendarId,
                        title = title,
                        description = description,
                        startAt = startAt,
                        endAt = endAt,
                        location = location,
                        allDay = allDay
                )

        return calendarRepository.findEventById(eventId)
                ?: error("No se ha podido recuperar el evento recién creado (id=$eventId).")
    }

    /** Eventos del usuario entre dos fechas, ambas inclusive. */
    fun getEvents(userId: Long, from: LocalDateTime, to: LocalDateTime): List<CalendarEventRecord> {

        require(!to.isBefore(from)) { "La fecha 'hasta' no puede ser anterior a la fecha 'desde'." }

        val calendarId = calendarRepository.getOrCreateDefaultCalendar(userId)

        return calendarRepository.findEventsBetween(calendarId, from, to)
    }

    fun getEvent(eventId: Long): CalendarEventRecord? = calendarRepository.findEventById(eventId)

    fun updateEvent(
            eventId: Long,
            title: String? = null,
            description: String? = null,
            startAt: LocalDateTime? = null,
            endAt: LocalDateTime? = null,
            location: String? = null
    ): Boolean {

        val existingEvent = calendarRepository.findEventById(eventId)

        require(existingEvent != null) { "El evento con id=$eventId no existe." }

        if (title != null) {
            require(title.isNotBlank()) { "El título del evento no puede estar vacío." }
        }

        val finalStartAt = startAt ?: existingEvent.startAt
        val finalEndAt = endAt ?: existingEvent.endAt

        if (finalEndAt != null) {
            require(!finalEndAt.isBefore(finalStartAt)) {
                "La fecha de fin del evento no puede ser anterior a la de inicio."
            }
        }

        return calendarRepository.updateEvent(
                eventId = eventId,
                title = title,
                description = description,
                startAt = startAt,
                endAt = endAt,
                location = location
        )
    }

    fun deleteEvent(eventId: Long): Boolean = calendarRepository.cancelEvent(eventId)

    fun completeEvent(eventId: Long): Boolean {

        val existingEvent = calendarRepository.findEventById(eventId)

        require(existingEvent != null) { "El evento con id=$eventId no existe." }

        require(existingEvent.status != "cancelled") {
            "No se puede completar un evento cancelado."
        }

        return calendarRepository.completeEvent(eventId)
    }
}
