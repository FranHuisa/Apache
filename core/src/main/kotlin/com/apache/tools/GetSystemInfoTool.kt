package com.apache.tools

import java.io.File
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * Tool de la Fase 3 (control del PC), adelantada al prototipo como ejemplo de tool de solo lectura
 * que consulta información real del sistema.
 *
 * En Windows utiliza PowerShell para obtener información que las APIs estándar de la JVM no
 * proporcionan, como el uso real de CPU, RAM y GPU.
 *
 * NOTA IMPORTANTE SOBRE CÓMO SE EJECUTA EL SCRIPT (leer antes de tocar esta clase):
 *
 * El script se escribe a un archivo .ps1 temporal y se ejecuta con `powershell.exe -File`,
 * en vez de pasarlo inline con `-Command "<script>"`. Esto NO es una preferencia de estilo,
 * es la causa de los errores de sintaxis que estábamos viendo.
 *
 * Cuando ProcessBuilder lanza un proceso en Windows, la lista de argumentos que le das
 * (["powershell.exe", "-Command", scriptLargo]) se tiene que aplanar en un único string de
 * línea de comandos, porque así es como funciona CreateProcess en Windows (no existe un
 * array de argv real como en Linux). Para hacer eso, Java escapa comillas y barras
 * invertidas siguiendo la convención estándar de C (la misma que usa CommandLineToArgvW):
 * duplica backslashes y convierte cada `"` en `\"`.
 *
 * El problema es que `powershell.exe -Command` NO reconstruye ese argumento con las mismas
 * reglas de escapado que usó Java para generarlo. En cuanto el script tiene varias comillas
 * dobles (literales de string, filtros como "DriveType=3", interpolaciones $(...)) y varias
 * líneas, el texto que le llega realmente a PowerShell tiene comillas mal cerradas o
 * backslashes sueltos, y el parser de PowerShell falla con errores de sintaxis / expresión
 * — aunque el script en sí, leído como texto plano, sea perfectamente válido.
 *
 * Al escribir el script a un archivo y ejecutar `-File ruta.ps1`, PowerShell lee el
 * contenido directamente del archivo: no hay ningún aplanado ni escapado de línea de
 * comandos de por medio, así que las comillas llegan intactas.
 */
@Component
class GetSystemInfoTool : Tool {

    override val name = "getSystemInfo"

    override val description =
            "Consulta información de los recursos del sistema: CPU, RAM, GPU, VRAM, discos y estado de Windows."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> =
            mapOf("type" to "object", "properties" to emptyMap<String, Any?>())

    /**
     * El script de PowerShell en sí no ha cambiado de lógica respecto a la versión anterior:
     * sigue devolviendo líneas con formato "ETIQUETA|valor|valor..." que luego parseamos
     * en Kotlin. Lo único que cambia es CÓMO se lanza (ver runPowerShellScript).
     *
     * Los `${'$'}` siguen siendo necesarios aquí y no tienen nada que ver con el bug de
     * Windows: son escapes de Kotlin, no de PowerShell. Un string triple-quoted de Kotlin
     * (`"""..."""`) interpreta `$nombre` como plantilla de Kotlin, así que cualquier `$`
     * que quieras que llegue literal a PowerShell hay que escribirlo como `${'$'}` para que
     * Kotlin no intente sustituirlo por una variable de Kotlin inexistente.
     */
    private val powerShellScript =
            """
            ${'$'}ProgressPreference = 'SilentlyContinue'

            ${'$'}cpu = Get-CimInstance Win32_Processor
            ${'$'}os = Get-CimInstance Win32_OperatingSystem
            ${'$'}gpu = Get-CimInstance Win32_VideoController |
                Where-Object { ${'$'}_.Name -and ${'$'}_.AdapterRAM } |
                Select-Object -First 1

            ${'$'}cpuUsage = [math]::Round(
                (${'$'}cpu | Measure-Object -Property LoadPercentage -Average).Average,
                0
            )

            ${'$'}totalRam = [math]::Round(${'$'}os.TotalVisibleMemorySize / 1MB, 1)
            ${'$'}freeRam = [math]::Round(${'$'}os.FreePhysicalMemory / 1MB, 1)
            ${'$'}usedRam = [math]::Round(${'$'}totalRam - ${'$'}freeRam, 1)
            ${'$'}ramUsage = if (${'$'}totalRam -gt 0) {
                [math]::Round((${'$'}usedRam / ${'$'}totalRam) * 100, 0)
            } else {
                0
            }

            ${'$'}gpuName = if (${'$'}gpu) { ${'$'}gpu.Name } else { "N/D" }
            ${'$'}vramTotal = if (${'$'}gpu) {
                [math]::Round(${'$'}gpu.AdapterRAM / 1GB, 1)
            } else {
                0
            }

            ${'$'}boot = ${'$'}os.LastBootUpTime
            ${'$'}uptime = (Get-Date) - ${'$'}boot
            ${'$'}hours = [math]::Floor(${'$'}uptime.TotalHours)
            ${'$'}minutes = ${'$'}uptime.Minutes

            "CPU|${'$'}cpuUsage"
            "RAM|${'$'}usedRam|${'$'}totalRam|${'$'}ramUsage"
            "GPU|${'$'}gpuName|${'$'}vramTotal"

            Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" |
                ForEach-Object {
                    ${'$'}total = [math]::Round(${'$'}_.Size / 1GB, 1)
                    ${'$'}free = [math]::Round(${'$'}_.FreeSpace / 1GB, 1)
                    ${'$'}used = [math]::Round(${'$'}total - ${'$'}free, 1)
                    ${'$'}usage = if (${'$'}total -gt 0) {
                        [math]::Round((${'$'}used / ${'$'}total) * 100, 0)
                    } else {
                        0
                    }

                    "DISK|${'$'}(${'$'}_.DeviceID)|${'$'}used|${'$'}total|${'$'}usage"
                }

            "WINDOWS|${'$'}(${'$'}os.Caption)|${'$'}hours|${'$'}minutes"
            """.trimIndent()

    override fun execute(args: Map<String, Any?>): String {

        // Ejecuta el script (ver comentario de la clase para el porqué del archivo temporal).
        val systemInfo = runPowerShellScript(powerShellScript)

        if (systemInfo.startsWith("ERROR")) {
            return systemInfo
        }

        var cpuUsage = "N/D"
        var usedRam = 0.0
        var totalRam = 0.0
        var ramUsage = 0.0
        var gpuName = "N/D"
        var vramTotal = 0.0
        var windowsName = "Windows"
        var uptime = "N/D"

        val diskLines = mutableListOf<String>()

        // Procesa la salida del script (una línea por dato, con "ETIQUETA|valor|...") y la
        // vuelca en variables Kotlin normales. Cada rama corresponde a una de las líneas
        // "CPU|...", "RAM|...", "GPU|...", "DISK|...", "WINDOWS|..." que emite el script.
        systemInfo.lines().forEach { line ->
            val parts = line.split("|")

            when (parts.getOrNull(0)) {
                "CPU" -> {
                    cpuUsage = parts.getOrNull(1) ?: "N/D"
                }
                "RAM" -> {
                    usedRam = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    totalRam = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                    ramUsage = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                }
                "GPU" -> {
                    gpuName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "N/D"
                    vramTotal = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                }
                "DISK" -> {
                    if (parts.size >= 5) {
                        val drive = parts[1]
                        val used = parts[2]
                        val total = parts[3]
                        val usage = parts[4]

                        diskLines.add("Disco $drive: ${used}GB/${total}GB | Uso: ${usage}%")
                    }
                }
                "WINDOWS" -> {
                    windowsName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Windows"

                    val hours = parts.getOrNull(2) ?: "0"
                    val minutes = parts.getOrNull(3) ?: "0"

                    uptime = "${hours}h ${minutes}m"
                }
            }
        }

        // Devuelve únicamente la información relevante para que Apache pueda utilizarla
        // (esto es lo que Gemini "ve" como resultado de la función, no el bloque de texto crudo).
        return buildString {
            appendLine("CPU: Uso: ${formatValue(cpuUsage)}%")
            appendLine(
                    "RAM: ${formatNumber(usedRam)}/${formatNumber(totalRam)} GB | Uso: ${formatNumber(ramUsage)}%"
            )
            appendLine("GPU: $gpuName | Uso: N/D% | VRAM: ${formatNumber(vramTotal)} GB")
            diskLines.forEach { appendLine(it) }
            append("Sistema: $windowsName | Encendido: $uptime")
        }
    }

    /**
     * Ejecuta un script de PowerShell y devuelve su salida estándar (o un mensaje "ERROR: ..."
     * si algo falla).
     *
     * ESTE ES EL FIX: en vez de pasar el script como argumento de `-Command` (lo que
     * provocaba el error de sintaxis descrito arriba), lo escribimos en un archivo .ps1
     * temporal y lanzamos `powershell.exe -File <archivo>`. PowerShell lee el script
     * directamente del disco, así que no hay ningún proceso de "aplanado a línea de
     * comandos" que pueda corromper las comillas.
     *
     * Detalles relevantes:
     * - `-ExecutionPolicy Bypass`: evita que la política de ejecución del sistema
     *   (normalmente "Restricted" por defecto en Windows) impida correr el .ps1 temporal.
     *   Solo afecta a este proceso hijo, no cambia la configuración global del sistema.
     * - El archivo se escribe en UTF-8 y se borra siempre al final (bloque `finally`),
     *   tanto si la ejecución tuvo éxito como si falló o hizo timeout.
     * - Igual que antes, si el proceso tarda más de 5 segundos se mata (`destroyForcibly`)
     *   para no dejar colgado al agente esperando una respuesta que no llega.
     */
    private fun runPowerShellScript(script: String): String {
        val tempFile = File.createTempFile("apache_getsysteminfo_", ".ps1")

        return try {
            tempFile.writeText(script, Charsets.UTF_8)

            val process =
                    ProcessBuilder(
                                    "powershell.exe",
                                    "-NoProfile",
                                    "-NonInteractive",
                                    "-ExecutionPolicy",
                                    "Bypass",
                                    "-File",
                                    tempFile.absolutePath
                            )
                            .redirectErrorStream(true)
                            .start()

            val finished = process.waitFor(5, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return "ERROR: PowerShell tardó demasiado en responder."
            }

            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()

            if (output.isBlank()) {
                return "ERROR: PowerShell no devolvió ningún resultado."
            }

            output.trim()
        } catch (exception: Exception) {
            "ERROR: ${exception.message ?: exception.javaClass.simpleName}"
        } finally {
            // Siempre limpiamos el archivo temporal, incluso si algo ha fallado antes.
            tempFile.delete()
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