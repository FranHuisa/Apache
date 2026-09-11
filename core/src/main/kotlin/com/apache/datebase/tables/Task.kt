package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Tarea perteneciente a un usuario.
 *
 * Una tarea representa algo que el usuario debe realizar.
 * A diferencia de un evento de calendario, una tarea no tiene
 * por qué ocupar un periodo concreto de tiempo.
 */
object Tasks : Table("task") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val title = varchar("title", 255)

    val description = text("description").nullable()

    val status = varchar("status", 30)

    val priority = varchar("priority", 20)

    val dueAt = datetime("due_at").nullable()

    val completedAt = datetime("completed_at").nullable()

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}