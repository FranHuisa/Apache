package com.apache.tools


import org.springframework.stereotype.Component
import java.io.File

/**
 * Tool DESTRUCTIVE de ejemplo: es la que dispara el flujo de "¿Quieres que
 * continúe?" descrito en el documento de diseño original. El propio
 * PermissionManager es quien intercepta esta tool ANTES de llegar aquí
 * cuando riskLevel == DESTRUCTIVE (ver Agent.kt) — execute() solo se llama
 * una vez el usuario ya ha confirmado explícitamente.
 */
@Component
class DeleteFileTool : Tool {
    override val name = "deleteFile"
    override val description = "Elimina un archivo del disco de forma permanente. Requiere confirmación del usuario."
    override val riskLevel = RiskLevel.DESTRUCTIVE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Ruta absoluta del archivo a eliminar.")
        ),
        "required" to listOf("path")
    )

    override fun execute(args: Map<String, Any?>): String {
        val path = args["path"] as? String ?: return "No se ha especificado la ruta del archivo."
        val file = File(path)
        if (!file.exists()) return "El archivo '$path' no existe."
        return if (file.delete()) "He eliminado '$path'." else "No he podido eliminar '$path'."
    }
}