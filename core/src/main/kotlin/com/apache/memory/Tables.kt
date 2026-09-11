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

/**
 * Recuerdos a largo plazo sobre el usuario (nombre, gustos, preferencias, datos que ha pedido
 * explícitamente que se recuerden...), independientes de cualquier conversación concreta.
 *
 * A diferencia de [Messages] (historial de una conversación, que se usa para reconstruir el
 * contexto de ESE turno), estas filas sobreviven a conversaciones nuevas y a reinicios de la
 * app: es lo que hace que Apache se sienta como un asistente personal que te conoce, en vez de
 * "olvidarlo todo" cada vez que se abre una conversación nueva. Se inyectan en el system
 * instruction de cada turno (ver Agent.buildSystemInstruction).
 */
object UserMemories : Table("user_memories") {
    val id = integer("id").autoIncrement()
    val content = text("content")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}