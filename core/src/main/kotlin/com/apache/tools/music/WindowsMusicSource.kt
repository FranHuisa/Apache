package com.apache.tools.music

import java.io.File
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * Fuente multimedia de Windows.
 *
 * Utiliza Windows Media Session para detectar y controlar la aplicación
 * multimedia activa. Esto permite trabajar con Spotify y otros reproductores
 * que expongan una sesión multimedia al sistema operativo.
 *
 * No depende de una versión concreta de Spotify ni busca el proceso
 * "Spotify.exe". La fuente trabaja contra la interfaz multimedia de Windows.
 */
@Component
class WindowsMusicSource : MusicSource {

    override val id = "windows-media"

    override val displayName = "Windows Media"

    override val priority = 100

    override fun isAvailable(): Boolean {
        val result = runPowerShell(
            """
            ${'$'}type = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]

            if (${ '$' }null -eq ${'$'}type) {
                exit 1
            }

            ${'$'}manager = ${'$'}type::RequestAsync().GetAwaiter().GetResult()
            ${'$'}sessions = ${'$'}manager.GetSessions()

            if (${ '$' }sessions.Count -gt 0) {
                exit 0
            }

            exit 1
            """.trimIndent()
        )

        return result.success
    }

    override fun play(): Boolean {
        return executeMediaAction("play")
    }

    override fun pause(): Boolean {
        return executeMediaAction("pause")
    }

    override fun playPause(): Boolean {
        return executeMediaAction("playPause")
    }

    override fun next(): Boolean {
        return executeMediaAction("next")
    }

    override fun previous(): Boolean {
        return executeMediaAction("previous")
    }

    override fun volumeUp(): Boolean {
        return sendVolumeKey(0xAF)
    }

    override fun volumeDown(): Boolean {
        return sendVolumeKey(0xAE)
    }

    override fun setVolume(percent: Int): Boolean {
        // Windows Media Session no expone un volumen global mediante esta API.
        // Se implementará mediante Core Audio cuando añadamos el control preciso.
        return false
    }

    override fun getStatus(): PlaybackStatus {
        val result = runPowerShell(
            """
            ${'$'}type = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]

            if (${ '$' }null -eq ${'$'}type) {
                exit 1
            }

            ${'$'}manager = ${'$'}type::RequestAsync().GetAwaiter().GetResult()
            ${'$'}session = ${'$'}manager.GetCurrentSession()

            if (${ '$' }null -eq ${'$'}session) {
                exit 2
            }

            ${'$'}playback = ${'$'}session.GetPlaybackInfo()
            ${'$'}status = ${'$'}playback.PlaybackStatus.ToString()

            ${'$'}properties = ${'$'}session.TryGetMediaPropertiesAsync().GetAwaiter().GetResult()

            ${'$'}title = if (${ '$' }properties.Title) {
                ${'$'}properties.Title
            } else {
                ""
            }

            ${'$'}artist = if (${ '$' }properties.Artist) {
                ${'$'}properties.Artist
            } else {
                ""
            }

            Write-Output "STATUS|${'$'}status"
            Write-Output "TITLE|${'$'}title"
            Write-Output "ARTIST|${'$'}artist"
            """.trimIndent()
        )

        if (!result.success) {
            return PlaybackStatus(isPlaying = false)
        }

        var status = ""
        var title: String? = null
        var artist: String? = null

        result.output.lines().forEach { line ->
            val separator = line.indexOf("|")

            if (separator <= 0) {
                return@forEach
            }

            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)

            when (key) {
                "STATUS" -> status = value
                "TITLE" -> title = value.takeIf { it.isNotBlank() }
                "ARTIST" -> artist = value.takeIf { it.isNotBlank() }
            }
        }

        return PlaybackStatus(
            isPlaying = status.equals("Playing", ignoreCase = true),
            title = title,
            artist = artist
        )
    }

    private fun executeMediaAction(action: String): Boolean {
        val result = runPowerShell(
            """
            ${'$'}type = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]

            if (${ '$' }null -eq ${'$'}type) {
                exit 1
            }

            ${'$'}manager = ${'$'}type::RequestAsync().GetAwaiter().GetResult()
            ${'$'}session = ${'$'}manager.GetCurrentSession()

            if (${ '$' }null -eq ${'$'}session) {
                exit 2
            }

            ${'$'}success = switch ("$action") {
                "play" {
                    ${'$'}session.TryPlayAsync().GetAwaiter().GetResult()
                }

                "pause" {
                    ${'$'}session.TryPauseAsync().GetAwaiter().GetResult()
                }

                "playPause" {
                    ${'$'}playback = ${'$'}session.GetPlaybackInfo()

                    if (${ '$' }playback.PlaybackStatus.ToString() -eq "Playing") {
                        ${'$'}session.TryPauseAsync().GetAwaiter().GetResult()
                    } else {
                        ${'$'}session.TryPlayAsync().GetAwaiter().GetResult()
                    }
                }

                "next" {
                    ${'$'}session.TrySkipNextAsync().GetAwaiter().GetResult()
                }

                "previous" {
                    ${'$'}session.TrySkipPreviousAsync().GetAwaiter().GetResult()
                }

                default {
                    ${'$'}false
                }
            }

            if (${ '$' }success) {
                exit 0
            }

            exit 3
            """.trimIndent()
        )

        return result.success
    }

    private fun sendVolumeKey(virtualKey: Int): Boolean {
        return try {
            val type = """
                using System;
                using System.Runtime.InteropServices;

                public static class ApacheVolumeControl
                {
                    [DllImport("user32.dll")]
                    private static extern void keybd_event(
                        byte bVk,
                        byte bScan,
                        uint dwFlags,
                        UIntPtr dwExtraInfo
                    );

                    public static void Press(byte key)
                    {
                        keybd_event(key, 0, 0, UIntPtr.Zero);
                        keybd_event(key, 0, 2, UIntPtr.Zero);
                    }
                }
            """.trimIndent()

            val result = runPowerShell(
                """
                ${'$'}source = @'
                $type
                '@

                Add-Type -TypeDefinition ${'$'}source
                [ApacheVolumeControl]::Press([byte]$virtualKey)
                """.trimIndent().replace("\$virtualKey", virtualKey.toString())
            )

            result.success
        } catch (_: Exception) {
            false
        }
    }

    private fun runPowerShell(script: String): PowerShellResult {
        val tempFile = File.createTempFile("apache_music_", ".ps1")

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
                return PowerShellResult(false, "")
            }

            val output =
                process.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .readText()
                    .trim()

            PowerShellResult(
                success = process.exitValue() == 0,
                output = output
            )
        } catch (_: Exception) {
            PowerShellResult(false, "")
        } finally {
            tempFile.delete()
        }
    }

    private data class PowerShellResult(
        val success: Boolean,
        val output: String
    )
}