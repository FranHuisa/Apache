package com.apache.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Relación entre un usuario y un permiso.
 *
 * Permite conceder o revocar permisos individualmente
 * para cada usuario.
 */
object UserPermissions : Table("user_permission") {

    val id = long("id").autoIncrement()

    val userId = long("user_id")

    val permissionId = long("permission_id")

    val granted = bool("granted").default(false)

    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}