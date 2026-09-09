package com.apache.tools

import com.apache.tools.RiskLevel
import com.apache.tools.Tool
import org.springframework.stereotype.Component

/**
 * Ejemplo de tool de riesgo REVERSIBLE: tiene un efecto real en el sistema
 * (lanza un proceso), pero es una acción benigna y fácil de deshacer
 * (cerrar la app), así que se ejecuta directamente sin pedir confirmación
 * (ver PermissionManager). Sí queda registrada en el log del agente.
 *
 * IMPORTANTE (seguridad): la lista `allowedApps` es una allowlist explícita
 * a propósito. NUNCA se debe construir el comando a partir de una ruta o
 * nombre arbitrario que venga directamente de Gemini/el usuario sin pasar
 * por esta validación — eso es exactamente el escenario "ejecuta cualquier
 * comando de Windows" que el diseño original quiere evitar.
 */
@Component
class OpenApplicationTool : Tool {
    override val name = "openApplication"
    override val description =
        "Abre una aplicación instalada en el ordenador. Solo funciona con aplicaciones de la lista permitida."
    override val riskLevel = RiskLevel.REVERSIBLE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "appName" to mapOf(
                "type" to "string",
                "description" to "Nombre de la aplicación a abrir, ej: 'chrome', 'calculadora', 'bloc de notas'."
            )
        ),
        "required" to listOf("appName")
    )

    // Allowlist: nombre lógico -> comando real del sistema operativo.
    // En la Fase 3 esto se ampliaría y se detectaría el SO automáticamente.
    private val allowedApps = mapOf(
        "chrome" to "google-chrome",
        "calculadora" to "gnome-calculator",
        "bloc de notas" to "gedit",
        "terminal" to "gnome-terminal"
    )

    override fun execute(args: Map<String, Any?>): String {
        val requested = (args["appName"] as? String)?.lowercase()?.trim()
            ?: return "No se ha especificado qué aplicación abrir."

        val command = allowedApps[requested]
            ?: return "'$requested' no está en la lista de aplicaciones permitidas. " +
                "Aplicaciones disponibles: ${allowedApps.keys.joinToString(", ")}."

        return try {
            ProcessBuilder(command).start()
            "He abierto $requested."
        } catch (e: Exception) {
            "No he podido abrir $requested: ${e.message}"
        }
    }
}