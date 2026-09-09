package com.apache.tools

import com.apache.tools.RiskLevel
import com.apache.tools.Tool
import org.springframework.stereotype.Component
import java.io.File

/**
 * Tool de la Fase 3 (control del PC), adelantada al prototipo como ejemplo
 * de tool de solo lectura que usa APIs nativas de la JVM en vez de un
 * proceso externo. Usa java.lang.management y java.io.File, que funcionan
 * igual en Windows/Linux/macOS sin dependencias extra.
 */
@Component
class GetSystemInfoTool : Tool {
    override val name = "getSystemInfo"
    override val description =
        "Consulta información del sistema: uso de CPU, memoria RAM disponible/total y espacio en disco."
    override val riskLevel = RiskLevel.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any?>()
    )

    override fun execute(args: Map<String, Any?>): String {
        val runtime = Runtime.getRuntime()
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()

        val totalMemMb = runtime.maxMemory() / (1024 * 1024)
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)

        val disk = File("/")
        val totalDiskGb = disk.totalSpace / (1024 * 1024 * 1024)
        val freeDiskGb = disk.freeSpace / (1024 * 1024 * 1024)

        return """
            Carga media del sistema: ${osBean.systemLoadAverage}
            Núcleos disponibles: ${osBean.availableProcessors}
            Memoria JVM: ${freeMemMb}MB libres de ${totalMemMb}MB
            Disco: ${freeDiskGb}GB libres de ${totalDiskGb}GB
        """.trimIndent()
    }
}