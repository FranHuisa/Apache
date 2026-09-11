package com.apache.api

import com.apache.database.repository.UserRecord
import com.apache.database.service.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * API REST para comprobar y gestionar el usuario principal de Apache.
 *
 * Esta API sirve como primera prueba de comunicación entre:
 *
 * HTTP
 * ↓
 * Controller
 * ↓
 * Service
 * ↓
 * Repository
 * ↓
 * Exposed
 * ↓
 * MySQL
 */
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    /**
     * Obtiene el usuario principal de Apache.
     *
     * El usuario `default` fue creado durante la configuración
     * inicial de la base de datos.
     */
    @GetMapping("/default")
    fun getDefaultUser(): UserRecord? {
        return userService.getDefaultUser()
    }
}