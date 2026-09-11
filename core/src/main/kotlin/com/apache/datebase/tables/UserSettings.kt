package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Configuración personal de un usuario.
 *
 * Esta tabla corresponde directamente a la tabla `user_settings`
 * creada previamente en la base de datos MySQL.
 *
 * La relación con `users` y `calendar` ya existe en MySQL.
 * En esta primera fase no la declaramos como referencia Exposed
 * porque las tablas relacionadas se definirán en los siguientes pasos.
 */
object UserSettings : Table("user_settings") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val language = varchar("language", 10)

    val timezone = varchar("timezone", 50)

    val voiceEnabled = bool("voice_enabled").default(true)

    val wakeWord = varchar("wake_word", 50)

    val notificationEnabled = bool("notification_enabled").default(true)

    val theme = varchar("theme", 20)

    val defaultCalendarId = long("default_calendar_id").nullable()

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}