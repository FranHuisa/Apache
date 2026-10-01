package com.apache.tools

import com.apache.ApacheDefaults
import com.apache.database.service.UserMemoryService
import org.springframework.stereotype.Component

/**
 * Tool que guarda un dato del usuario en la memoria a largo plazo.
 *
 * Gemini la usa cuando el usuario dice "recuerda que..." o cuando cuenta algo
 * personal y duradero. Lo guardado se añade a la instrucción de sistema en
 * todas las conversaciones futuras, y se puede ver y editar en la sección
 * Memoria del Desktop.
 */
@Component
class RememberFactTool(
    private val memoryService: UserMemoryService
) : Tool {

    override val name = "rememberFact"

    override val description =
        "Guarda en la memoria permanente de Apache un dato sobre el usuario para tenerlo en cuenta " +
            "en el futuro: su nombre, gustos, alergias, rutinas, trabajo, personas importantes, " +
            "preferencias de cómo quiere que le respondas... Úsalo cuando el usuario diga 'recuerda " +
            "que...' o cuando cuente algo personal que siga siendo cierto más adelante. No guardes " +
            "cosas pasajeras (lo que va a hacer hoy, el tiempo, etc.). Si el dato ya existía con la " +
            "misma clave, se actualiza."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "Nombre corto del dato, ej: 'nombre', 'comida favorita', 'hora de levantarse'."
            ),
            "value" to mapOf(
                "type" to "string",
                "description" to "El dato en sí, ej: 'Fran', 'la pizza', 'a las 8:00 entre semana'."
            ),
            "type" to mapOf(
                "type" to "string",
                "description" to "Categoría del dato.",
                "enum" to UserMemoryService.TYPES
            ),
            "importance" to mapOf(
                "type" to "number",
                "description" to "Importancia de 0 a 1 (1 = muy importante, ej: una alergia). Por defecto 0.5."
            )
        ),
        "required" to listOf("key", "value")
    )

    override fun execute(args: Map<String, Any?>): String {
        val key = (args["key"] as? String)?.trim().orEmpty()
        val value = (args["value"] as? String)?.trim().orEmpty()

        if (key.isBlank() || value.isBlank()) {
            return "Falta el nombre o el contenido del dato a recordar."
        }

        val importance = (args["importance"] as? Number)?.toDouble()
            ?: (args["importance"] as? String)?.toDoubleOrNull()
            ?: UserMemoryService.DEFAULT_IMPORTANCE

        return try {
            val (memory, isNew) = memoryService.remember(
                userId = ApacheDefaults.DEFAULT_USER_ID,
                key = key,
                value = value,
                type = (args["type"] as? String) ?: UserMemoryService.DEFAULT_TYPE,
                importance = importance
            )

            if (isNew) {
                "Guardado en memoria: ${memory.key} = ${memory.value}."
            } else {
                "Memoria actualizada: ${memory.key} = ${memory.value}."
            }
        } catch (e: Exception) {
            "No se ha podido guardar en memoria: ${e.message}"
        }
    }
}

/** Tool que borra un dato de la memoria a largo plazo ("olvida que..."). */
@Component
class ForgetFactTool(
    private val memoryService: UserMemoryService
) : Tool {

    override val name = "forgetFact"

    override val description =
        "Borra un dato de la memoria permanente de Apache cuando el usuario pida que lo olvides o " +
            "cuando ya no sea cierto y no tenga sustituto. Usa la misma clave con la que aparece " +
            "en la lista de cosas que sabes del usuario."

    // El usuario lo puede volver a añadir desde la sección Memoria o pidiéndolo.
    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "Clave del dato a olvidar, tal cual aparece en la memoria."
            )
        ),
        "required" to listOf("key")
    )

    override fun execute(args: Map<String, Any?>): String {
        val key = (args["key"] as? String)?.trim()
        if (key.isNullOrBlank()) {
            return "Falta la clave del dato a olvidar."
        }

        return try {
            val removed = memoryService.forgetByKey(ApacheDefaults.DEFAULT_USER_ID, key)
            if (removed != null) {
                "Olvidado: ${removed.key} (${removed.value})."
            } else {
                "No había ningún dato guardado con la clave '$key'."
            }
        } catch (e: Exception) {
            "No se ha podido borrar de la memoria: ${e.message}"
        }
    }
}
