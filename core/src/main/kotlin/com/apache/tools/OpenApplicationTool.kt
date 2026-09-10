package com.apache.tools

import com.apache.tools.application.ApplicationSource
import com.apache.tools.application.ApplicationSourceManager

import org.springframework.stereotype.Component

/**
 * Tool que controla aplicaciones desde Apache: abrir, cerrar y consultar
 * si una aplicación está ejecutándose.
 *
 * Riesgo REVERSIBLE: abrir una aplicación no supone un riesgo relevante.
 * Cerrar una aplicación puede provocar pérdida de cambios no guardados,
 * por lo que posteriormente podremos añadir confirmación para esta acción.
 *
 * IMPORTANTE (diseño): esta tool NO sabe cómo trabajar directamente con
 * Windows ni con una aplicación concreta. Solo conoce la interfaz
 * [ApplicationSource] y delega en [ApplicationSourceManager].
 */
@Component
class OpenApplicationTool(
    private val sourceManager: ApplicationSourceManager
) : Tool {

    override val name = "applicationControl"

    override val description =
        "Controla aplicaciones del ordenador: abrir una aplicación, cerrar una aplicación " +
                "o comprobar si una aplicación está abierta."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val requiresGeminiResponse = false

    override val parametersSchema: Map<String, Any?> =
        mapOf(
            "type" to "object",
            "properties" to
                mapOf(
                    "action" to
                        mapOf(
                            "type" to "string",
                            "description" to
                                "Acción a realizar sobre una aplicación.",
                            "enum" to
                                listOf(
                                    "open",
                                    "close",
                                    "status"
                                )
                        ),
                    "application" to
                        mapOf(
                            "type" to "string",
                            "description" to
                                "Nombre de la aplicación sobre la que realizar la acción."
                        )
                ),
            "required" to listOf("action", "application")
        )

    override fun execute(args: Map<String, Any?>): String {

        val action =
            (args["action"] as? String)?.trim()
                ?: return "No se ha especificado ninguna acción."

        val application =
            (args["application"] as? String)?.trim()
                ?: return "No se ha especificado ninguna aplicación."

        if (application.isBlank()) {
            return "No se ha especificado ninguna aplicación."
        }

        val source = sourceManager.activeSource()

        if (source.id == "none") {
            return "No hay ninguna fuente de aplicaciones disponible."
        }

        return when (action) {

            "open" ->
                resultOf(
                    source.open(application),
                    "Abierta.",
                    action,
                    application
                )

            "close" ->
                resultOf(
                    source.close(application),
                    "Cerrada.",
                    action,
                    application
                )

            "status" -> {
                if (source.isRunning(application)) {
                    "Está abierta."
                } else {
                    "No está abierta."
                }
            }

            else ->
                "Acción '$action' no reconocida. Acciones disponibles: open, close, status."
        }
    }

    private fun resultOf(
        success: Boolean,
        successMessage: String,
        action: String,
        application: String
    ): String {
        return if (success) {
            successMessage
        } else {
            "No he podido ${describeAction(action)} ${application.lowercase()}."
        }
    }

    private fun describeAction(action: String): String =
        when (action) {
            "open" -> "abrir"
            "close" -> "cerrar"
            else -> "controlar"
        }
}