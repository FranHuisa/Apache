package com.apache

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Punto de entrada del "cerebro" de Apache. Al arrancar, Spring Boot:
 * 1. Levanta un servidor web embebido (Tomcat) en el puerto configurado.
 * 2. Escanea el paquete com.apache y registra automáticamente todos los
 *    @Component / @Repository / @RestController.
 * 3. Inicializa los componentes necesarios del Core.
 *
 * Este proceso corre en segundo plano en el PC del usuario; la app de
 * escritorio (módulo `desktop`) es un proceso totalmente separado que
 * le habla por HTTP en localhost.
 */
@SpringBootApplication
class ApacheCoreApplication

fun main(args: Array<String>) {
    runApplication<ApacheCoreApplication>(*args)
}