package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Memoria persistente de Apache.
 *
 * Permite guardar información relevante asociada a un usuario
 * para que Apache pueda utilizarla como contexto en conversaciones futuras.
 *
 * La relación con `users` ya existe en MySQL.
 */
object Memory : Table("memory") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val type = varchar("type", 50)

    val key = varchar("key", 255)

    val value = text("value")

    val importance = double("importance")

    val confidence = double("confidence")

    val expiresAt = datetime("expires_at").nullable()

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}