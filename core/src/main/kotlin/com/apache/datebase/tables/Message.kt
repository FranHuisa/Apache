package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Mensaje perteneciente a una conversación.
 *
 * Los mensajes pueden proceder del usuario, Apache,
 * el sistema o una herramienta ejecutada por Apache.
 */
object Messages : Table("message") {

    val id = long("id").autoIncrement()

    val conversationId = long("conversation_id")

    val role = varchar("role", 20)

    val content = text("content")

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}