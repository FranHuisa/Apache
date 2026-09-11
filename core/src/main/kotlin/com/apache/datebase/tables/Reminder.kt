package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Recordatorio asociado a un usuario.
 *
 * Un recordatorio permite a Apache avisar al usuario
 * en un momento determinado.
 */
object Reminders : Table("reminder") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val eventId = long("event_id").nullable()

    val title = varchar("title", 255)

    val message = text("message").nullable()

    val triggerAt = datetime("trigger_at")

    val status = varchar("status", 30)

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}