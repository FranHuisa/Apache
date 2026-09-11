package com.apache.tools

import com.apache.memory.UserMemoryRepository
import org.springframework.stereotype.Component

/**
 * Tool que permite a Apache guardar un dato o preferencia del usuario de forma persistente,
 * más allá de la conversación actual (ver [com.apache.memory.UserMemories]).
 *
 * Es la pieza que convierte a Apache en un asistente que "te conoce" en vez de olvidarlo todo
 * en cuanto se abre una conversación nueva: lo que se guarda aquí se le recuerda a Gemini en
 * el system instruction de TODAS las conversaciones futuras (ver Agent.buildSystemInstruction).
 *
 * Se espera que Gemini la use cuando el usuario cuente algo sobre sí mismo que tenga sentido
 * recordar a largo plazo (su nombre, gustos, rutinas, preferencias de cómo quiere que le hable
 * Apache...), no para datos de un único turno.
 */
@Component
class RememberFactTool(private val userMemoryRepository: UserMemoryRepository) : Tool {

    override val name = "rememberFact"

    override val description =
        "Guarda de forma permanente un dato o preferencia sobre el usuario para recordarlo en " +
            "futuras conversaciones (ej. su nombre, gustos, rutinas, cómo prefiere que le hable " +
            "Apache). Úsala cuando el usuario comparta algo sobre sí mismo que tenga sentido " +
            "recordar siempre, no para detalles que solo importan en este momento."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "content" to mapOf(
                "type" to "string",
                "description" to
                    "El dato a recordar, redactado en tercera persona y de forma autocontenida, " +
                        "ej. 'Al usuario le gusta que le llamen Fran' o 'El usuario trabaja de noche " +
                        "los fines de semana'."
            )
        ),
        "required" to listOf("content")
    )

    override fun execute(args: Map<String, Any?>): String {
        val content = (args["content"] as? String)?.trim()

        if (content.isNullOrBlank()) {
            return "No se ha especificado ningún dato que recordar."
        }

        userMemoryRepository.remember(content)

        return "Recordado: $content"
    }
}
