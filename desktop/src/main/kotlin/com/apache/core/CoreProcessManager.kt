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
            // Salida en UTF-8 para que core.log no rompa las tildes (Windows usa cp1252 por defecto).
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8",
            "-jar",
            coreJar.absolutePath
        )
            .directory(coreJar.parentFile)
            .redirectErrorStream(true)
            /*
             * IMPORTANTE: la salida del Core tiene que ir a algún sitio que se
             * vacíe. Si se deja en un pipe que nadie lee, en cuanto se llena
             * (en Windows son pocos KB y el Core escribe logs en DEBUG) el
             * Core se queda bloqueado escribiendo y deja de responder.
             * Se guarda en ~/.apache/core.log para poder revisar errores.
             */
            .redirectOutput(ProcessBuilder.Redirect.to(coreLogFile()))
            .start()

        // Esperamos a que Spring Boot termine de iniciar.
        waitForCore()

        if (!isCoreRunning()) {
            throw IllegalStateException(
                "El Core de Apache no ha podido iniciarse correctamente."
            )
        }
    }

    /** Archivo donde se guarda la salida del Core: ~/.apache/core.log (se sobrescribe en cada arranque). */
    private fun coreLogFile(): File {
        val directory = File(System.getProperty("user.home"), ".apache")
        directory.mkdirs()
        return File(directory, "core.log")
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

        /**
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

        /**
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

        /*
         * Primero intentamos utilizar JAVA_HOME.
         *
         * Esto permite utilizar la instalación de Java del sistema
         * aunque Apache esté ejecutándose con el runtime reducido
         * incluido por jpackage.
         */
        val javaHome = System.getenv("JAVA_HOME")

        if (!javaHome.isNullOrBlank()) {

            val javaExecutable = File(
                javaHome,
                "bin/$executableName"
            )

            if (javaExecutable.exists() && javaExecutable.isFile) {
                return javaExecutable.absolutePath
            }
        }

        /*
         * Si JAVA_HOME no está disponible, buscamos Java mediante
         * el PATH del sistema.
         */
        try {

            val process = ProcessBuilder(
                if (
                    System.getProperty("os.name")
                        .lowercase()
                        .contains("win")
                ) {
                    "where.exe"
                } else {
                    "which"
                },
                executableName
            )
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream
                .bufferedReader()
                .readLines()

            process.waitFor()

            val javaFromPath = result
                .firstOrNull()
                ?.trim()

            if (
                !javaFromPath.isNullOrBlank() &&
                File(javaFromPath).exists()
            ) {
                return File(javaFromPath).absolutePath
            }

        } catch (_: Exception) {
            // Continuamos con la búsqueda alternativa.
        }

        /*
         * Como último recurso utilizamos el java.home de la JVM actual.
         *
         * Esto funciona durante el desarrollo cuando Apache se ejecuta
         * directamente mediante Gradle con una instalación completa
         * de Java.
         */
        val currentJavaHome = System.getProperty("java.home")

        val currentJavaExecutable = File(
            currentJavaHome,
            "bin/$executableName"
        )

        if (
            currentJavaExecutable.exists() &&
            currentJavaExecutable.isFile
        ) {
            return currentJavaExecutable.absolutePath
        }

        throw IllegalStateException(
            "No se ha encontrado una instalación de Java válida para iniciar el Core de Apache."
        )
    }
}