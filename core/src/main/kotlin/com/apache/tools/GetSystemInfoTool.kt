package com.apache.tools

import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * Tool de la Fase 3 (control del PC), adelantada al prototipo como ejemplo de tool de solo lectura
 * que consulta información real del sistema.
 *
 * En Windows utiliza PowerShell para obtener información que las APIs estándar de la JVM no
 * proporcionan, como el uso real de CPU, RAM y GPU.
 */
@Component
class GetSystemInfoTool : Tool {

    override val name = "getSystemInfo"

    override val description =
            "Consulta información de los recursos del sistema: CPU, RAM, GPU, VRAM, discos y estado de Windows."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> =
            mapOf("type" to "object", "properties" to emptyMap<String, Any?>())

    override fun execute(args: Map<String, Any?>): String {

        // Obtiene el porcentaje de uso actual de la CPU mediante Windows.

        val cpuUsage =
                getPowerShellValue(
                        "(Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average"
                )

        // Obtiene la memoria RAM real del equipo, en lugar de la memoria utilizada por la JVM.

        val memoryInfo =
                getPowerShellValue(
                        """
            ${'$'}os = Get-CimInstance Win32_OperatingSystem
            ${'$'}total = [math]::Round(${'$'}os.TotalVisibleMemorySize / 1MB, 1)
            ${'$'}free = [math]::Round(${'$'}os.FreePhysicalMemory / 1MB, 1)
            "${'$'}total|${'$'}free"
            """.trimIndent()
                )

        val memoryParts = memoryInfo.split("|")
        val totalRam = memoryParts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val freeRam = memoryParts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val usedRam = totalRam - freeRam

        // Calcula el porcentaje de RAM utilizada a partir de la memoria total y disponible.

        val ramUsage =
                if (totalRam > 0) {
                    (usedRam / totalRam) * 100
                } else {
                    0.0
                }

        // Obtiene el nombre de la GPU y la cantidad total de VRAM que Windows expone.

        val gpuInfo =
                getPowerShellValue(
                        """
            Get-CimInstance Win32_VideoController |
            Where-Object { ${'$'}_.Name -and ${'$'}_.AdapterRAM } |
            Select-Object -First 1 Name, AdapterRAM |
            ForEach-Object {
                "${'$'}(${'$'}_.Name)|${'$'}([math]::Round(${'$'}_.AdapterRAM / 1GB, 1))"
            }
            """.trimIndent()
                )

        val gpuParts = gpuInfo.split("|")
        val gpuName = gpuParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "N/D"
        val vramTotal = gpuParts.getOrNull(1)?.toDoubleOrNull() ?: 0.0

        // Obtiene temporalmente el uso de la GPU.

        val gpuUsage = "N/D"

        // Obtiene todas las unidades de almacenamiento locales disponibles.

        val disks =
                getPowerShellValue(
                        """
            Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" |
            ForEach-Object {
                ${'$'}total = [math]::Round(${'$'}_.Size / 1GB, 1)
                ${'$'}free = [math]::Round(${'$'}_.FreeSpace / 1GB, 1)
                ${'$'}used = ${'$'}total - ${'$'}free
                ${'$'}usage = if (${'$'}total -gt 0) { [math]::Round((${'$'}used / ${'$'}total) * 100, 0) } else { 0 }
                "${'$'}(${'$'}_.DeviceID)|${'$'}used|${'$'}total|${'$'}usage"
            }
            """.trimIndent()
                )

        // Obtiene la versión de Windows y calcula cuánto tiempo lleva encendido el equipo.

        val windowsInfo =
                getPowerShellValue(
                        """
            ${'$'}os = Get-CimInstance Win32_OperatingSystem
            ${'$'}boot = ${'$'}os.LastBootUpTime
            ${'$'}uptime = (Get-Date) - ${'$'}boot
            ${'$'}hours = [math]::Floor(${'$'}uptime.TotalHours)
            ${'$'}minutes = ${'$'}uptime.Minutes
            "${'$'}(${'$'}os.Caption)|${'$'}hours" + "h " + "${'$'}minutes" + "m"
            """.trimIndent()
                )

        val windowsParts = windowsInfo.split("|")
        val windowsName = windowsParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Windows"
        val uptime = windowsParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "N/D"

        // Convierte la información de cada disco en una línea sencilla para Apache.

        val diskLines =
                disks.lines().filter { it.contains("|") }.mapNotNull { line ->
                    val parts = line.split("|")

                    if (parts.size >= 4) {

                        val drive = parts[0]
                        val used = parts[1]
                        val total = parts[2]
                        val usage = parts[3]

                        "Disco $drive: ${used}GB/${total}GB | Uso: ${usage}%"
                    } else {

                        null
                    }
                }

        // Devuelve únicamente la información relevante para que Apache pueda utilizarla.

        return buildString {
            appendLine("CPU: Uso: ${formatValue(cpuUsage)}%")

            appendLine(
                    "RAM: ${formatNumber(usedRam)}/${formatNumber(totalRam)} GB | Uso: ${formatNumber(ramUsage)}%"
            )

            appendLine(
                    "GPU: $gpuName | Uso: ${formatValue(gpuUsage)}% | VRAM: ${formatNumber(vramTotal)} GB"
            )

            diskLines.forEach { appendLine(it) }

            append("Sistema: $windowsName | Encendido: $uptime")
        }
    }

    // Ejecuta una consulta de PowerShell y devuelve únicamente su resultado.

    // Ejecuta una consulta de PowerShell y devuelve únicamente su resultado.

    private fun getPowerShellValue(command: String): String {
        return try {
            val process =
                    ProcessBuilder(
                                    "powershell.exe",
                                    "-NoProfile",
                                    "-NonInteractive",
                                    "-Command",
                                    command
                            )
                            .redirectErrorStream(true)
                            .start()

            val finished = process.waitFor(5, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return "N/D"
            }

            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) {
            // Si Windows no puede proporcionar el dato, Apache recibe un valor controlado.
            "N/D"
        }
    }

    // Formatea porcentajes y otros valores numéricos evitando decimales innecesarios.

    private fun formatValue(value: String): String {

        val number = value.toDoubleOrNull() ?: return "N/D"

        return if (number % 1.0 == 0.0) {
            number.toInt().toString()
        } else {
            "%.1f".format(number)
        }
    }

    // Formatea cantidades de memoria y almacenamiento de forma sencilla.

    private fun formatNumber(value: Double): String {

        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            "%.1f".format(value)
        }
    }
}
