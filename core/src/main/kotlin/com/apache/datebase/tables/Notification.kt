package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Notificación mostrada o generada por Apache.
 *
 * Las notificaciones permiten separar el hecho de generar
 * un aviso de la ejecución del recordatorio que lo originó.
 */
object Notifications : Table("notification") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val type = varchar("type", 50)

    val title = varchar("title", 255)

    val message = text("message")

    val read = bool("read").default(false)

    val createdAt = datetime("created_at")

    val readAt = datetime("read_at").nullable()

    override val primaryKey = PrimaryKey(id)
}