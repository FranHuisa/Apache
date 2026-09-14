package com.apache.network

import com.apache.model.CalendarEventDto
import com.apache.model.CreateCalendarEventDto
import com.apache.model.UpdateCalendarEventDto
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

fun fetchCalendarEvents(): List<CalendarEventDto> {
    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/calendar/events")
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException("Error del Core: ${response.code}")
        }

        return objectMapper.readValue(bodyText)
    }
}

fun createCalendarEvent(event: CreateCalendarEventDto): CalendarEventDto {
    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/calendar/events")
        .post(objectMapper.writeValueAsString(event).toRequestBody(jsonMediaType))
        .build()

    httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException("Error ${response.code}: ${bodyText.ifBlank { "Sin respuesta del Core." }}")
        }

        return objectMapper.readValue(bodyText)
    }
}

/** Actualiza un evento existente en el Core. */
fun updateCalendarEvent(eventId: Long, event: UpdateCalendarEventDto): CalendarEventDto {
    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/calendar/events/$eventId")
        .put(objectMapper.writeValueAsString(event).toRequestBody(jsonMediaType))
        .build()

    httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException("Error ${response.code}: ${bodyText.ifBlank { "Sin respuesta del Core." }}")
        }

        return objectMapper.readValue(bodyText)
    }
}

/** Marca un evento como completado en el Core. */
fun completeCalendarEvent(eventId: Long): CalendarEventDto {
    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/calendar/events/$eventId/complete")
        .post("".toRequestBody(jsonMediaType))
        .build()

    httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException("Error ${response.code}: ${bodyText.ifBlank { "Sin respuesta del Core." }}")
        }

        return objectMapper.readValue(bodyText)
    }
}

/** Cancela un evento mediante borrado lógico en el Core. */
fun cancelCalendarEvent(eventId: Long): CalendarEventDto {
    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/calendar/events/$eventId/cancel")
        .post("".toRequestBody(jsonMediaType))
        .build()

    httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException("Error ${response.code}: ${bodyText.ifBlank { "Sin respuesta del Core." }}")
        }

        return objectMapper.readValue(bodyText)
    }
}
