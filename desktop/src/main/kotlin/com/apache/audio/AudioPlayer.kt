package com.apache.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Reproduce audio PCM mediante los altavoces del sistema.
 *
 * El audio grabado por el micrófono usa siempre 16 kHz, pero el audio
 * generado por Text-to-Speech puede venir con otra frecuencia de
 * muestreo, así que se indica explícitamente en cada reproducción
 * (por defecto 16 kHz, para no romper otros usos existentes).
 */
class AudioPlayer {

    fun play(audioData: ByteArray, sampleRate: Int = 16_000) {

        val format = AudioFormat(
            sampleRate.toFloat(),
            16,
            1,
            true,
            false
        )

        val dataLine: SourceDataLine = AudioSystem.getSourceDataLine(format)

        dataLine.open(format)
        dataLine.start()

        try {
            dataLine.write(audioData, 0, audioData.size)
            dataLine.drain()
        } finally {
            dataLine.stop()
            dataLine.close()
        }
    }
}