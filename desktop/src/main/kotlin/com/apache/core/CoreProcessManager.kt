package com.apache.core

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gestiona el proceso del Core de Apache.
 *
 * El Desktop utiliza esta clase para iniciar el Core automáticamente
 * cuando Apache se ejecuta como aplicación independiente.
 *
 * También comprueba si el Core ya está funcionando para evitar
 * iniciar varias instancias del mismo proceso.
 */
object CoreProcessManager {

    private const val CORE_PORT = 8080
    private const val CORE_HEALTH_URL = "http://localhost:$CORE_PORT"
    private const val CORE_RESOURCE = "/core/apache-core.jar"

    private var coreProcess: Process? = null

    fun start() {

        // Si el Core ya está funcionando, no hacemos nada.
        if (isCoreRunning()) {
            return
        }

        val coreJar = findCoreJar()
            ?: throw IllegalStateException(
                "No se ha encontrado el Core de Apache."
            )

        val javaExecutable = findJavaExecutable()

        coreProcess = ProcessBuilder(
            javaExecutable,
            "-jar",
            coreJar.absolutePath
        )
            .directory(coreJar.parentFile)
            .redirectErrorStream(true)
            .start()

        // Esperamos a que Spring Boot termine de iniciar.
        waitForCore()

        if (!isCoreRunning()) {
            throw IllegalStateException(
                "El Core de Apache no ha podido iniciarse correctamente."
            )
        }
    }

    fun stop() {
        coreProcess?.let { process ->

            if (process.isAlive) {
                process.destroy()
            }
        }

        coreProcess = null
    }

    private fun isCoreRunning(): Boolean {

        return try {

            val connection = URL(CORE_HEALTH_URL)
                .openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 500
            connection.readTimeout = 500

            connection.responseCode

            connection.disconnect()

            true

        } catch (_: Exception) {

            false
        }
    }

    private fun waitForCore() {

        val timeout = 30_000L
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {

            if (isCoreRunning()) {
                return
            }

            Thread.sleep(500)
        }
    }

    private fun findCoreJar(): File? {

        /*
         * Cuando Apache está empaquetado, el Core se encuentra dentro
         * de los recursos del Desktop y no existe como archivo físico.
         *
         * Extraemos el JAR a una carpeta temporal para poder ejecutarlo
         * mediante ProcessBuilder.
         */
        val resource = javaClass.getResourceAsStream(CORE_RESOURCE)

        if (resource != null) {

            val runtimeDirectory = File(
                System.getProperty("java.io.tmpdir"),
                "apache/core"
            )

            if (!runtimeDirectory.exists()) {
                runtimeDirectory.mkdirs()
            }

            val coreJar = File(
                runtimeDirectory,
                "apache-core.jar"
            )

            resource.use { input ->
                coreJar.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            return coreJar
        }

        /*
         * Durante el desarrollo mantenemos las ubicaciones físicas
         * como alternativa para poder ejecutar Apache directamente
         * desde Gradle.
         */
        val currentDirectory = File(
            System.getProperty("user.dir")
        )

        val possibleLocations = listOf(

            // Ejecución mediante :desktop:run.
            File(
                currentDirectory,
                "build/resources/main/core/apache-core.jar"
            ),

            // Otra posible ubicación durante el desarrollo.
            File(
                currentDirectory,
                "desktop/build/resources/main/core/apache-core.jar"
            ),

            // Core generado directamente por Gradle.
            File(
                currentDirectory,
                "core/build/libs/core-0.1.0.jar"
            ),

            // Ubicación utilizada si el Core se encuentra
            // junto al ejecutable de Apache.
            File(
                currentDirectory,
                "core/apache-core.jar"
            ),

            // Ubicación alternativa para el JAR empaquetado.
            File(
                currentDirectory,
                "core/core-0.1.0.jar"
            )
        )

        return possibleLocations.firstOrNull {
            it.exists() && it.isFile
        }
    }

    private fun findJavaExecutable(): String {

        val javaHome = System.getProperty("java.home")

        val executableName =
            if (
                System.getProperty("os.name")
                    .lowercase()
                    .contains("win")
            ) {
                "java.exe"
            } else {
                "java"
            }

        return File(
            javaHome,
            "bin/$executableName"
        ).absolutePath
    }
}
