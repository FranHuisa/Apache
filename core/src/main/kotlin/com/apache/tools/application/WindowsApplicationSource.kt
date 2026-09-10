package com.apache.tools.application

import org.springframework.stereotype.Component

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Fuente de aplicaciones de Windows.
 *
 * Utiliza PowerShell para localizar y abrir aplicaciones instaladas en Windows y consultar los
 * procesos que se encuentran actualmente en ejecución.
 */
@Component
class WindowsApplicationSource : ApplicationSource {

    override val id = "windows-applications"
    override val displayName = "Windows"
    override val priority = 100

    override fun isAvailable(): Boolean {
        return System.getProperty("os.name")
            ?.contains("Windows", ignoreCase = true) == true
    }

    override fun open(application: String): Boolean {
        return try {
            val appName = application.trim()

            if (appName.isBlank()) {
                return false
            }

            val normalizedName = when (appName.lowercase()) {
                "notepad" -> "bloc de notas"
                "calculator" -> "calculadora"
                else -> appName
            }

            val escapedName = escapePowerShellString(normalizedName)

            val command = """
                ${'$'}app = Get-StartApps |
                    Where-Object { ${'$'}_.Name -like "*$escapedName*" } |
                    Select-Object -First 1

                if (${'$'}null -eq ${'$'}app) {
                    exit 1
                }

                Start-Process "shell:AppsFolder\${'$'}(${'$'}app.AppID)"
            """.trimIndent()

            executePowerShell(command)
        } catch (_: Exception) {
            false
        }
    }

    override fun close(application: String): Boolean {
        return try {
            val processName = resolveProcessName(application)
                ?: return false

            val escapedName = escapePowerShellString(processName)

            val command = """
                ${'$'}processes = Get-Process -Name '$escapedName' -ErrorAction SilentlyContinue

                if (${'$'}null -eq ${'$'}processes) {
                    exit 1
                }

                ${'$'}processes | Stop-Process -ErrorAction SilentlyContinue

                Start-Sleep -Milliseconds 300

                if (Get-Process -Name '$escapedName' -ErrorAction SilentlyContinue) {
                    exit 1
                }

                exit 0
            """.trimIndent()

            executePowerShell(command)
        } catch (_: Exception) {
            false
        }
    }

    override fun isRunning(application: String): Boolean {
        return try {
            val processName = resolveProcessName(application)
                ?: return false

            val escapedName = escapePowerShellString(processName)

            val command = """
                if (Get-Process -Name '$escapedName' -ErrorAction SilentlyContinue) {
                    exit 0
                }

                exit 1
            """.trimIndent()

            executePowerShell(command)
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveProcessName(application: String): String? {
        val normalized = when (application.trim().lowercase()) {
            "notepad" -> "notepad"
            "bloc de notas" -> "notepad"
            "calculadora" -> "calculator"
            "calculator" -> "calculator"
            else -> application.trim().removeSuffix(".exe")
        }

        if (normalized.isBlank()) {
            return null
        }

        val escapedName = escapePowerShellString(normalized)

        val command = """
            ${'$'}process = Get-Process |
                Where-Object {
                    ${'$'}_.ProcessName -like "*$escapedName*"
                } |
                Select-Object -First 1

            if (${'$'}null -eq ${'$'}process) {
                exit 1
            }

            Write-Output ${'$'}process.ProcessName
        """.trimIndent()

        val result = executePowerShellAndRead(command)

        return result
            .lines()
            .map { it.trim() }
            .firstOrNull {
                it.isNotBlank() &&
                    !it.startsWith("#<") &&
                    !it.startsWith("<")
            }
    }

    private fun executePowerShell(command: String): Boolean {
        return try {
            println("=== APACHE POWERSHELL ===")
            println(command)

            val encodedCommand = Base64.getEncoder().encodeToString(
                command.toByteArray(StandardCharsets.UTF_16LE)
            )

            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-EncodedCommand",
                encodedCommand
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .readText()

            val exitCode = process.waitFor()

            println("=== POWERSHELL OUTPUT ===")
            println(output)
            println("=== POWERSHELL EXIT CODE: $exitCode ===")

            exitCode == 0
        } catch (e: Exception) {
            println("=== POWERSHELL ERROR ===")
            e.printStackTrace()
            false
        }
    }

    private fun executePowerShellAndRead(command: String): String {
        return try {
            val encodedCommand = Base64.getEncoder().encodeToString(
                command.toByteArray(StandardCharsets.UTF_16LE)
            )

            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-EncodedCommand",
                encodedCommand
            )
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream
                .bufferedReader()
                .readText()

            process.waitFor()

            result.trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun escapePowerShellString(value: String): String {
        return value.replace("'", "''")
    }
}