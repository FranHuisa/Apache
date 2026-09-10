package com.apache.audio

import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

/**
 * Se encarga de capturar audio desde el micrófono del sistema.
 *
 * Utiliza PCM 16-bit, 16 kHz y mono, un formato adecuado para
 * el posterior procesamiento mediante Speech-to-Text.
 */
class MicrophoneRecorder {

    private val format = AudioFormat(
        16_000f,
        16,
        1,
        true,
        false
    )

    private var line: TargetDataLine? = null
    private var recordingThread: Thread? = null
    private var audioData = ByteArrayOutputStream()

    @Volatile
    private var recording = false

    @Synchronized
    fun start() {

        if (recording) {
            return
        }

        val dataLine = AudioSystem.getTargetDataLine(format)

        dataLine.open(format)
        dataLine.start()

        line = dataLine
        audioData = ByteArrayOutputStream()
        recording = true

        recordingThread = Thread {
            val buffer = ByteArray(4096)

            try {
                while (recording) {
                    val bytesRead = dataLine.read(
                        buffer,
                        0,
                        buffer.size
                    )

                    if (bytesRead > 0) {
                        audioData.write(
                            buffer,
                            0,
                            bytesRead
                        )
                    }
                }
            } finally {
                dataLine.stop()
                dataLine.close()
            }
        }.apply {
            isDaemon = true
            name = "Apache-MicrophoneRecorder"
            start()
        }
    }

    @Synchronized
    fun stop(): ByteArray {

        if (!recording) {
            return ByteArray(0)
        }

        recording = false

        line?.stop()
        line?.close()

        line = null
        recordingThread = null

        return audioData.toByteArray()
    }
}