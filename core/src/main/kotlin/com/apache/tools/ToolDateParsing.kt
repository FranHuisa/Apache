package com.apache.tools

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Parseo de fechas/horas tolerante, compartido por las tools de calendario,
 * tareas y recordatorios.
 *
 * Gemini nos manda las fechas como texto libre dentro del JSON de argumentos
 * (nunca un tipo fecha real), así que aceptamos las dos formas más
 * habituales que le pedimos en la descripción de cada parámetro:
 *  - "2026-09-15T18:30"  (fecha y hora, formato ISO)
 *  - "2026-09-15"        (solo fecha; se asume medianoche)
 */
object ToolDateParsing {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Devuelve null si [value] es null, vacío, o no se puede interpretar como fecha. */
    fun parseDateTime(value: String?): LocalDateTime? {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) return null

        return try {
            LocalDateTime.parse(trimmed, dateTimeFormatter)
        } catch (e: Exception) {
            try {
                LocalDate.parse(trimmed, dateFormatter).atStartOfDay()
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun formatForUser(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm"))
}
