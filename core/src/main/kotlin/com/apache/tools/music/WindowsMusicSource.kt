package com.apache.tools.music

import net.bjoernpetersen.volctl.VolumeControl

import org.endlesssource.mediainterface.SystemMediaFactory

import org.endlesssource.mediainterface.api.MediaSession

import org.endlesssource.mediainterface.api.SystemMediaInterface

import org.springframework.stereotype.Component

/**
 * Fuente multimedia de Windows.
 *
 * Utiliza mediainterface para acceder a la interfaz multimedia del sistema
 * operativo. Esto permite trabajar con Spotify y otros reproductores
 * compatibles con Windows Media Session.
 *
 * No depende de una versión concreta de Spotify ni busca el proceso
 * "Spotify.exe". La fuente trabaja contra la interfaz multimedia del sistema.
 */
@Component
class WindowsMusicSource : MusicSource {

    override val id = "windows-media"

    override val displayName = "Windows Media"

    override val priority = 100

    private val volumeControl = VolumeControl()

    override fun isAvailable(): Boolean {
        return try {
            withMediaInterface { media ->
                media.getActiveSession().isPresent
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun play(): Boolean {
        return executeOnActiveSession { session ->
            session.getControls().play()
        }
    }

    override fun pause(): Boolean {
        return executeOnActiveSession { session ->
            session.getControls().pause()
        }
    }

    override fun playPause(): Boolean {
        return executeOnActiveSession { session ->
            session.getControls().togglePlayPause()
        }
    }

    override fun next(): Boolean {
        return executeOnActiveSession { session ->
            session.getControls().next()
        }
    }

    override fun previous(): Boolean {
        return try {
            val previousStatus = getStatus()

            val firstAttempt = executeOnActiveSession { session ->
                session.getControls().previous()
            }

            if (!firstAttempt) {
                return false
            }

            Thread.sleep(300)

            val currentStatus = getStatus()

            val sameTrack =
                    previousStatus.title != null &&
                            currentStatus.title == previousStatus.title &&
                            currentStatus.artist == previousStatus.artist

            if (sameTrack) {
                executeOnActiveSession { session ->
                    session.getControls().previous()
                }
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun volumeUp(): Boolean {
        return try {
            val currentVolume = volumeControl.volume
            volumeControl.volume = (currentVolume + 10).coerceAtMost(100)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun volumeDown(): Boolean {
        return try {
            val currentVolume = volumeControl.volume
            volumeControl.volume = (currentVolume - 10).coerceAtLeast(0)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun setVolume(percent: Int): Boolean {
        return try {
            volumeControl.volume = percent.coerceIn(0, 100)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun getStatus(): PlaybackStatus {
        return try {
            withMediaInterface { media ->
                val session = media.getActiveSession().orElse(null)
                    ?: return@withMediaInterface PlaybackStatus(
                        isPlaying = false,
                        volumePercent = getVolume()
                    )

                val nowPlaying = session.getNowPlaying().orElse(null)
                val playbackState = session.getControls().getPlaybackState()

                PlaybackStatus(
                    isPlaying = playbackState.name.equals(
                        "PLAYING",
                        ignoreCase = true
                    ),
                    title = nowPlaying?.getTitle()?.orElse(null),
                    artist = nowPlaying?.getArtist()?.orElse(null),
                    volumePercent = getVolume()
                )
            }
        } catch (_: Exception) {
            PlaybackStatus(
                isPlaying = false,
                volumePercent = getVolume()
            )
        }
    }

    private fun getVolume(): Int? {
        return try {
            volumeControl.volume
        } catch (_: Exception) {
            null
        }
    }

    private fun executeOnActiveSession(
        action: (MediaSession) -> Boolean
    ): Boolean {
        return try {
            withMediaInterface { media ->
                val session = media.getActiveSession().orElse(null)
                    ?: return@withMediaInterface false

                action(session)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun <T> withMediaInterface(
        action: (SystemMediaInterface) -> T
    ): T {
        SystemMediaFactory.createSystemInterface().use { media ->
            return action(media)
        }
    }
}