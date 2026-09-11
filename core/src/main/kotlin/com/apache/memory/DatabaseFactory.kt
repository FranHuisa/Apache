
package com.apache.memory

import jakarta.annotation.PostConstruct

import org.jetbrains.exposed.sql.Database

import org.jetbrains.exposed.sql.SchemaUtils

import org.jetbrains.exposed.sql.transactions.transaction

import org.springframework.beans.factory.annotation.Value

import org.springframework.stereotype.Component

/**
 * Punto ÚNICO de configuración de la base de datos.
 *
 * Esto es justo lo que comentábamos: para pasar de SQLite (prototipo) a
 * MySQL (producción) solo hay que cambiar `url`, `driver` y añadir el
 * driver de MySQL como dependencia en build.gradle.kts. Las tablas
 * (Conversations.kt, Messages.kt) y los repositorios (MemoryRepository.kt)
 * no necesitan tocarse: Exposed genera el SQL adecuado para cada dialecto.
 *
 * Ejemplo de application.yml para MySQL cuando llegue el momento:
 *   apache.db.url: jdbc\:mysql://localhost:3306/apache
 *   apache.db.driver: com.mysql.cj.jdbc.Driver
 */
@Component
class DatabaseFactory(

    @Value("\${apache.db.url:jdbc:sqlite:./apache.db}") private val url: String,

    @Value("\${apache.db.driver:org.sqlite.JDBC}") private val driver: String,

    @Value("\${apache.db.username:}") private val username: String,

    @Value("\${apache.db.password:}") private val password: String

) {

    @PostConstruct
    fun connect() {

        Database.connect(
            url = url,
            driver = driver,
            user = username,
            password = password
        )

        transaction {

            // Crea las tablas si no existen. Con SQLite/prototipo esto basta;
            // en un entorno con MySQL real se sustituiría por migraciones (Flyway/Liquibase).

            SchemaUtils.create(Conversations, Messages)
        }
    }
}
