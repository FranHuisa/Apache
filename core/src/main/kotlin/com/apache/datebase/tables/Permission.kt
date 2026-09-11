package com.apache.database.tables

import org.jetbrains.exposed.sql.Table

/**
 * Permisos disponibles dentro de Apache.
 *
 * Los permisos definen qué acciones puede realizar
 * el sistema en nombre del usuario.
 */
object Permissions : Table("permission") {

    val id = long("id").autoIncrement()

    val name = varchar("name", 100)

    val description = text("description").nullable()

    override val primaryKey = PrimaryKey(id)
}