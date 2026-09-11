package com.apache.tools

import com.apache.memory.UserMemoryRepository
import org.springframework.stereotype.Component

/**
 * Contrapartida de [RememberFactTool]: permite a Apache olvidar (borrar) un dato guardado
 * previamente cuando el usuario lo pide explícitamente (ej. "olvida que trabajo de noche",
 * "ya no vivo en Almería, olvídalo").
 *
 * Borra por coincidencia de texto en vez de por id porque Gemini no conoce los ids internos:
 * solo conoce el contenido de lo que el usuario ha dicho.
 */
@Component
class ForgetFactTool(private val userMemoryRepository: UserMemoryRepository) : Tool {

    override val name = "forgetFact"

    override val description =
        "Olvida (borra) datos guardados previamente sobre el usuario que coincidan con una " +
            "búsqueda de texto. Úsala cuando el usuario pida explícitamente olvidar algo que se " +
            "le había pedido recordar antes."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to
                    "Texto a buscar entre los datos guardados para borrar los que coincidan, " +
                        "ej. 'trabaja de noche' o 'Almería'."
            )
        ),
        "required" to listOf("query")
    )

    override fun execute(args: Map<String, Any?>): String {
        val query = (args["query"] as? String)?.trim()

        if (query.isNullOrBlank()) {
            return "No se ha especificado qué olvidar."
        }

        val forgotten = userMemoryRepository.forgetMatching(query)

        return when (forgotten) {
            0 -> "No tenía ningún dato guardado que coincida con '$query'."
            1 -> "Olvidado. He borrado 1 dato guardado que coincidía con '$query'."
            else -> "Olvidado. He borrado $forgotten datos guardados que coincidían con '$query'."
        }
    }
}
