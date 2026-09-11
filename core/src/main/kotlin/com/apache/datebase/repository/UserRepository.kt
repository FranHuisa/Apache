package com.apache.database.repository

import com.apache.database.tables.Users
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
/**
 * Repository encargado de acceder a la tabla `users`.
 *
 * Esta clase contiene únicamente operaciones relacionadas
 * con la persistencia de usuarios.
 *
 * La lógica de negocio pertenece al Service.
 */
@Repository
class UserRepository {

    /**
     * Busca un usuario por su identificador.
     */
    fun findById(id: Long): UserRecord? = transaction {

        Users
            .selectAll()
            .where { Users.id eq id }
            .singleOrNull()
            ?.let { row ->

                UserRecord(
                    id = row[Users.id],
                    name = row[Users.name],
                    displayName = row[Users.displayName],
                    active = row[Users.active],
                    createdAt = row[Users.createdAt],
                    updatedAt = row[Users.updatedAt]
                )
            }
    }

    /**
     * Busca un usuario por su nombre.
     */
    fun findByName(name: String): UserRecord? = transaction {

        Users
            .selectAll()
            .where { Users.name eq name }
            .singleOrNull()
            ?.let { row ->

                UserRecord(
                    id = row[Users.id],
                    name = row[Users.name],
                    displayName = row[Users.displayName],
                    active = row[Users.active],
                    createdAt = row[Users.createdAt],
                    updatedAt = row[Users.updatedAt]
                )
            }
    }

    /**
     * Crea un nuevo usuario.
     */
    fun create(
        name: String,
        displayName: String?,
        active: Boolean = true
    ): Long = transaction {

        val now = LocalDateTime.now()

        Users.insert {
            it[Users.name] = name
            it[Users.displayName] = displayName
            it[Users.active] = active
            it[Users.createdAt] = now
            it[Users.updatedAt] = now
        }[Users.id]
    }

    /**
     * Actualiza los datos básicos de un usuario.
     */
    fun update(
        id: Long,
        name: String,
        displayName: String?,
        active: Boolean
    ): Boolean = transaction {

        Users.update({ Users.id eq id }) {
            it[Users.name] = name
            it[Users.displayName] = displayName
            it[Users.active] = active
            it[Users.updatedAt] = LocalDateTime.now()
        } > 0
    }
}

/**
 * Representación de un usuario obtenida desde la base de datos.
 *
 * No es una tabla Exposed. Es simplemente el objeto
 * que utilizamos para transportar los datos del usuario.
 */
data class UserRecord(
    val id: Long,
    val name: String,
    val displayName: String?,
    val active: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)