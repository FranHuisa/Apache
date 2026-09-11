package com.apache.memory

import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.sql.Database
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Punto ÚNICO de configuración de la base de datos.
 *
 * Apache 0.1 utiliza MySQL como base de datos principal.
 *
 * La conexión se configura desde application.yml mediante:
 * - apache.db.url
 * - apache.db.driver
 * - apache.db.username
 * - apache.db.password
 *
 * Las tablas de Apache se gestionarán mediante sus propias definiciones
 * Exposed y no se crearán automáticamente desde esta clase.
 */
@Component
class DatabaseFactory(

    @Value("\${apache.db.url}")
    private val url: String,

    @Value("\${apache.db.driver}")
    private val driver: String,

    @Value("\${apache.db.username}")
    private val username: String,

    @Value("\${apache.db.password}")
    private val password: String

) {

    @PostConstruct
    fun connect() {

        Database.connect(
            url = url,
            driver = driver,
            user = username,
            password = password
        )
    }
}