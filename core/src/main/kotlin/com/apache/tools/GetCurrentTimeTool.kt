package com.apache.tools

import com.apache.tools.RiskLevel
import com.apache.tools.Tool
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Primera tool del proyecto (la de la Fase 2 del plan original).
 * Sirve como "hola mundo" del sistema de function calling: no tiene
 * parámetros, es de solo lectura, y permite comprobar que todo el circuito
 * Gemini -> ToolRegistry -> Agent -> Tool funciona antes de añadir
 * herramientas más complejas.
 */
@Component
class GetCurrentTimeTool : Tool {
    override val name = "getCurrentTime"
    override val description = "Devuelve la fecha y hora actuales del sistema."
    override val riskLevel = RiskLevel.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any?>()
    )

    override fun execute(args: Map<String, Any?>): String {
        val now = LocalDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm:ss"))
    }
}