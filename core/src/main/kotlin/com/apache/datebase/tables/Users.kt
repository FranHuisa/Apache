package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Tabla de usuarios de Apache.
 *
 * Esta tabla corresponde directamente a la tabla `users`
 * creada previamente en la base de datos MySQL.
 */
object Users : Table("users") {

    val id = long("id").autoIncrement()

    val name = varchar("name", 100)

    val displayName = varchar("display_name", 100).nullable()

    val active = bool("active").default(true)

    val createdAt = datetime("created_at")

    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}