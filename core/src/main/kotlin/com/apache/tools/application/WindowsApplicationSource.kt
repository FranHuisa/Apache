package com.apache.tools.application

import org.springframework.stereotype.Component

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

            val escapedName = escapePowerShellString(appName)

            val command = """
                ${'$'}app = Get-StartApps |
                    Where-Object { ${'$'}_.Name -like "*$escapedName*" } |
                    Select-Object -First 1

                if (${ '$' }null -eq ${'$'}app) {
                    exit 1
                }

                Start-Process "shell:AppsFolder\${'$'}(${ '$' }app.AppID)"
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

                if (${ '$' }null -eq ${'$'}processes) {
                    exit 1
                }

                ${'$'}processes | Stop-Process -ErrorAction SilentlyContinue

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
        val normalized = application
            .trim()
            .removeSuffix(".exe")

        if (normalized.isBlank()) {
            return null
        }

        val escapedName = escapePowerShellString(normalized)

        val command = """
            Get-Process |
                Where-Object {
                    ${'$'}_.ProcessName -like "*$escapedName*"
                } |
                Select-Object -First 1 -ExpandProperty ProcessName
        """.trimIndent()

        val result = executePowerShellAndRead(command)

        return result
            .trim()
            .lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
    }

    private fun executePowerShell(command: String): Boolean {
        return try {
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                command
            )
                .redirectErrorStream(true)
                .start()

            process.inputStream
                .bufferedReader()
                .readText()

            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun executePowerShellAndRead(command: String): String {
        return try {
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                command
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