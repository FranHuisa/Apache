package com.apache.tools

/**
 * Contrato que debe implementar toda herramienta ("tool") que Apache pueda
 * invocar. Una Tool tiene dos caras:
 *
 * 1. Su "declaración" (name, description, parameters): esto es lo que se le
 *    envía a Gemini en cada llamada para que sepa qué funciones existen y
 *    cómo llamarlas (function calling / function declarations).
 * 2. Su "implementación" (execute): el código Kotlin real que se ejecuta
 *    cuando Gemini decide invocar esta tool.
 *
 * Mantener estas dos caras separadas (pero en la misma clase, por
 * simplicidad) es lo que permite iterar el agente con libertad: se puede
 * cambiar la implementación de una tool sin tocar su contrato con Gemini,
 * y viceversa, añadir un parámetro opcional nuevo sin romper nada.
 */
interface Tool {

    /** Nombre único de la tool, tal cual lo verá y usará Gemini (ej. "getCurrentTime"). */
    val name: String

    /** Descripción en lenguaje natural de qué hace la tool y cuándo usarla. Gemini decide basándose en esto. */
    val description: String

    /** Nivel de riesgo de la tool. Determina cómo la trata el PermissionManager. */
    val riskLevel: RiskLevel

    /**
     * Esquema JSON (formato OpenAPI/JSON Schema simplificado) de los parámetros
     * que acepta la tool. Ejemplo para una tool con un parámetro "path" de tipo string:
     *
     * ```
     * mapOf(
     *   "type" to "object",
     *   "properties" to mapOf(
     *     "path" to mapOf("type" to "string", "description" to "Ruta del archivo")
     *   ),
     *   "required" to listOf("path")
     * )
     * ```
     *
     * Si la tool no recibe parámetros, se puede devolver un objeto vacío.
     */
    val parametersSchema: Map<String, Any?>

    /**
     * Indica si, después de ejecutar la tool, Apache debe volver a Gemini
     * para que redacte la respuesta final.
     *
     * Por defecto se mantiene el comportamiento actual de Apache.
     */
    val requiresGeminiResponse: Boolean
        get() = true

    /**
     * Ejecuta la tool con los argumentos que ha decidido Gemini.
     * @param args argumentos ya parseados (nombre del parámetro -> valor)
     * @return texto plano que se le devuelve a Gemini como resultado de la función,
     *         para que pueda componer la respuesta final al usuario.
     */
    fun execute(args: Map<String, Any?>): String
}
