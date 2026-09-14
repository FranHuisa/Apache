package com.apache.model

data class CalendarEventDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    val startAt: String,
    val endAt: String? = null,
    val location: String? = null,
    val allDay: Boolean = false,
    val status: String
)

data class CreateCalendarEventDto(
    val title: String,
    val startAt: String,
    val description: String? = null,
    val endAt: String? = null,
    val location: String? = null,
    val allDay: Boolean = false
)

data class UpdateCalendarEventDto(
    val title: String? = null,
    val startAt: String? = null,
    val description: String? = null,
    val endAt: String? = null,
    val location: String? = null
)
