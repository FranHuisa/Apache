package com.apache.memory

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Una conversación es simplemente un contenedor de mensajes con un id.
 * Se crea una fila la primera vez que el usuario habla con Apache desde
 * una sesión nueva (ver MemoryRepository.getOrCreateConversation).
 */
object Conversations : Table("conversations") {
    val id = varchar("id", 36)
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * Cada mensaje de una conversación: quién lo dijo (role: "user" | "model" | "function")
 * y el contenido en texto plano. Guardamos el historial completo para poder
 * reconstruir el contexto que se le manda a Gemini en cada turno (Gemini no
 * tiene memoria propia entre llamadas HTTP).
 */
object Messages : Table("messages") {
    val id = integer("id").autoIncrement()
    val conversationId = varchar("conversation_id", 36).references(Conversations.id)
    val role = varchar("role", 16)
    val content = text("content")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}