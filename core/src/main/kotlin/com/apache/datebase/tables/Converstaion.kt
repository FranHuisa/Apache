package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Conversación mantenida entre el usuario y Apache.
 *
 * Una conversación agrupa todos los mensajes relacionados
 * con una misma sesión o contexto.
 */
object Conversations : Table("conversation") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val title = varchar("title", 255).nullable()

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}