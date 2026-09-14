package com.apache.audio

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Reproduce audio PCM recibido desde Apache Core.
 *
 * El Core devuelve el audio codificado en Base64 y VoiceController lo convierte
 * a ByteArray antes de llegar aquí. El formato esperado es PCM lineal de 16 bits,
 * mono, con la frecuencia de muestreo indicada por el Core.
 */
class AudioPlayer {

    fun play(audioData: ByteArray, sampleRate: Int) {
        if (audioData.isEmpty() || sampleRate <= 0) return

        val format = AudioFormat(
            sampleRate.toFloat(),
            16,
            1,
            true,
            false
        )

        val audioInputStream = AudioInputStream(
            ByteArrayInputStream(audioData),
            format,
            audioData.size.toLong() / format.frameSize
        )

        AudioSystem.getClip().use { clip ->
            clip.open(audioInputStream)
            clip.start()

            while (clip.isRunning) {
                Thread.sleep(10)
            }
        }
    }
}
