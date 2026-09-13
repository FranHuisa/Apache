package com.apache.memory

import jakarta.annotation.PostConstruct

import org.jetbrains.exposed.sql.Database

import org.jetbrains.exposed.sql.transactions.transaction

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

        println("========================================")
        println("Apache - Configuración de base de datos")
        println("URL: $url")
        println("Driver: $driver")
        println("Usuario: $username")
        println("========================================")

        val database = Database.connect(
            url = url,
            driver = driver,
            user = username,
            password = password
        )

        /**
         * Las instalaciones existentes pueden tener la tabla `reminder` creada antes de
         * que se añadiera la relación opcional con un evento de calendario. Esta migración
         * es aditiva: conserva todos los recordatorios y solo crea la columna si falta.
         */
        transaction(database) {

            val hasEventId = exec(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'reminder'
                  AND COLUMN_NAME = 'event_id'
                """.trimIndent()
            ) { resultSet ->
                resultSet.next() && resultSet.getInt(1) > 0
            } ?: false

            if (!hasEventId) {
                exec("ALTER TABLE reminder ADD COLUMN event_id BIGINT NULL")
            }
        }
    }
}
