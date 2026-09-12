package com.apache.memory

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Una conversación pertenece a un usuario y contiene los mensajes
 * intercambiados con Apache.
 *
 * El identificador es generado automáticamente por MySQL.
 */
object Conversations : Table("conversation") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val title = varchar("title", 200).nullable()

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Cada mensaje pertenece a una conversación.
 *m
 * Guardamos el historial completo para poder reconstruir
 * el contexto que se manda a Gemini en cada turno.
 *
 * Los roles utilizados por Apache son:
 * - user
 * - model
 * - function
 * - system
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