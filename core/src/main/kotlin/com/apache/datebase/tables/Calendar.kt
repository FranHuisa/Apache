package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Calendario de un usuario.
 *
 * Apache 0.1 utiliza calendarios internos.
 * Las integraciones con Google Calendar o Microsoft Calendar
 * se añadirán en versiones posteriores.
 */
object Calendars : Table("calendar") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val name = varchar("name", 100)

    val description = text("description").nullable()

    val color = varchar("color", 20).nullable()

    val timezone = varchar("timezone", 50)

    val active = bool("active").default(true)

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}