package com.apache.database.service

import com.apache.database.repository.UserRecord
import com.apache.database.repository.UserRepository
import org.springframework.stereotype.Service

/**
 * Capa de servicio de usuarios.
 *
 * El Service contiene la lógica de negocio y utiliza
 * el Repository para acceder a la base de datos.
 *
 * Apache no debería acceder directamente al Repository
 * desde la IA o desde las herramientas.
 */
@Service
class UserService(
    private val userRepository: UserRepository
) {

    /**
     * Obtiene un usuario por su identificador.
     */
    fun getUser(id: Long): UserRecord? {
        return userRepository.findById(id)
    }

    /**
     * Obtiene un usuario mediante su nombre.
     */
    fun getUserByName(name: String): UserRecord? {
        return userRepository.findByName(name)
    }

    /**
     * Obtiene el usuario principal de Apache.
     *
     * En Apache 0.1 utilizamos inicialmente el usuario `default`
     * creado durante la configuración de MySQL.
     */
    fun getDefaultUser(): UserRecord? {
        return userRepository.findByName("default")
    }

    /**
     * Crea un nuevo usuario.
     */
    fun createUser(
        name: String,
        displayName: String?,
        active: Boolean = true
    ): Long {
        require(name.isNotBlank()) {
            "El nombre del usuario no puede estar vacío."
        }

        return userRepository.create(
            name = name,
            displayName = displayName,
            active = active
        )
    }

    /**
     * Actualiza los datos básicos de un usuario.
     */
    fun updateUser(
        id: Long,
        name: String,
        displayName: String?,
        active: Boolean
    ): Boolean {

        require(name.isNotBlank()) {
            "El nombre del usuario no puede estar vacío."
        }

        return userRepository.update(
            id = id,
            name = name,
            displayName = displayName,
            active = active
        )
    }
}