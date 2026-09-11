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
 *
 * La grabación se detiene automáticamente después de varios segundos
 * de silencio una vez que se ha detectado voz.
 */
class MicrophoneRecorder {

    private val format = AudioFormat(
        16_000f,
        16,
        1,
        true,
        false
    )

    private val silenceTimeoutMs = 2_000L

    /**
     * Nivel mínimo de audio necesario para considerar
     * que el usuario está hablando.
     *
     * El micrófono utilizado presenta un nivel de ruido
     * aproximado de 21.000-23.000 en silencio.
     */
    private val voiceThreshold = 30_000.0

    private var line: TargetDataLine? = null

    private var recordingThread: Thread? = null

    private var audioData = ByteArrayOutputStream()

    @Volatile
    private var recording = false

    @Synchronized
    fun start(onAutoStop: (ByteArray) -> Unit = {}) {

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

            var voiceDetected = false
            var silenceStartedAt: Long? = null

            try {

                while (recording) {

                    val bytesRead =
                        dataLine.read(
                            buffer,
                            0,
                            buffer.size
                        )

                    if (bytesRead <= 0) {
                        continue
                    }

                    audioData.write(
                        buffer,
                        0,
                        bytesRead
                    )

                    val audioLevel =
                        calculateRms(
                            buffer,
                            bytesRead
                        )

                    println("Apache audio level: $audioLevel")

                    val hasVoice =
                        audioLevel >= voiceThreshold

                    if (hasVoice) {

                        if (!voiceDetected) {
                            println("Apache voice detected.")
                        }

                        voiceDetected = true
                        silenceStartedAt = null

                    } else if (voiceDetected) {

                        if (silenceStartedAt == null) {

                            silenceStartedAt =
                                System.currentTimeMillis()

                            println("Apache silence started.")
                        }

                        val silenceDuration =
                            System.currentTimeMillis() -
                                    silenceStartedAt

                        if (silenceDuration >= silenceTimeoutMs) {

                            println("Apache automatic stop.")

                            val recordedAudio =
                                finishRecording()

                            if (recordedAudio.isNotEmpty()) {
                                onAutoStop(recordedAudio)
                            }

                            break
                        }
                    }
                }

            } finally {

                closeLine(dataLine)
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

        return finishRecording()
    }

    @Synchronized
    private fun finishRecording(): ByteArray {

        recording = false

        line?.stop()
        line?.close()

        line = null
        recordingThread = null

        return audioData.toByteArray()
    }

    private fun closeLine(dataLine: TargetDataLine) {

        try {
            dataLine.stop()
        } catch (_: Exception) {
        }

        try {
            dataLine.close()
        } catch (_: Exception) {
        }
    }

    private fun calculateRms(
        buffer: ByteArray,
        length: Int
    ): Double {

        if (length < 2) {
            return 0.0
        }

        var sum = 0.0
        var samples = 0

        var index = 0

        while (index + 1 < length) {

            val low =
                buffer[index].toInt() and 0xFF

            val high =
                buffer[index + 1].toInt()

            val sample =
                (high shl 8) or low

            val signedSample =
                if (sample and 0x8000 != 0) {
                    sample - 0x10000
                } else {
                    sample
                }

            sum +=
                signedSample.toDouble() *
                        signedSample.toDouble()

            samples++

            index += 2
        }

        if (samples == 0) {
            return 0.0
        }

        return kotlin.math.sqrt(
            sum / samples
        )
    }
}