package com.apache.tools

import org.springframework.stereotype.Component

/**
 * Registro central de todas las [Tool] disponibles en Apache.
 *
 * Spring inyecta aquí automáticamente TODAS las clases que implementen
 * [Tool] y estén anotadas con @Component (ver paquete `tools`), gracias
 * a `List<Tool>` en el constructor. Esto significa que añadir una tool
 * nueva es tan simple como crear una clase que implemente Tool y anotarla
 * con @Component: no hay que registrarla manualmente en ningún sitio más.
 */
@Component
class ToolRegistry(private val tools: List<Tool>) {

    private val toolsByName: Map<String, Tool> = tools.associateBy { it.name }

    /** Busca una tool por nombre. Devuelve null si no existe (ej. si Gemini "alucina" un nombre). */
    fun findByName(name: String): Tool? = toolsByName[name]

    /** Todas las tools registradas, tal cual, para iterar sobre ellas (ej. logging, debug). */
    fun all(): List<Tool> = tools

    /**
     * Convierte todas las tools registradas al formato que espera la API de
     * Gemini para "function declarations" dentro del campo `tools` de la
     * petición de generateContent. Se llama en cada turno de conversación
     * en GeminiClient, de forma que Gemini siempre conoce el catálogo
     * actualizado de funciones disponibles (no hay estado guardado en Gemini
     * entre llamadas).
     */
    fun toGeminiFunctionDeclarations(): List<Map<String, Any?>> =
        tools.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parametersSchema
            )
        }
}