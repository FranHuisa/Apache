package com.apache.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Reproduce audio PCM mediante los altavoces del sistema.
 *
 * Utiliza el mismo formato que MicrophoneRecorder.
 */
class AudioPlayer {

    private val format = AudioFormat(
        16_000f,
        16,
        1,
        true,
        false
    )

    fun play(audioData: ByteArray) {
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