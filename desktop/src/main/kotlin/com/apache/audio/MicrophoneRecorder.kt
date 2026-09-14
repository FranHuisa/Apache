package com.apache.audio

import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * Graba audio del micrófono en PCM lineal de 16 bits, mono y 16 kHz.
 *
 * La grabación se ejecuta en un hilo independiente para no bloquear la interfaz.
 * La captura puede finalizar mediante stop() o automáticamente después de
 * aproximadamente un segundo de silencio.
 */
class MicrophoneRecorder {

    private val format = AudioFormat(
        16_000f,
        16,
        1,
        true,
        false
    )

    @Volatile
    private var recording = false

    @Volatile
    private var line: TargetDataLine? = null

    @Volatile
    private var recordingThread: Thread? = null

    private var currentOutput: ByteArrayOutputStream? = null

    @Volatile
    private var currentCallback: ((ByteArray) -> Unit)? = null

    private val lock = Any()

    /**
     * Inicia una grabación en segundo plano.
     *
     * El callback se ejecuta cuando la grabación termina automáticamente
     * por silencio. Si la grabación se detiene manualmente mediante stop(),
     * el audio se devuelve directamente desde stop().
     */
    fun start(onAudioCaptured: (ByteArray) -> Unit) {
        synchronized(lock) {
            if (recording) return

            val info = DataLine.Info(TargetDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                throw IllegalStateException(
                    "El micrófono no admite el formato PCM de 16 kHz."
                )
            }

            val targetLine = AudioSystem.getLine(info) as TargetDataLine
            targetLine.open(format)
            targetLine.start()

            line = targetLine
            recording = true
            currentOutput = ByteArrayOutputStream()
            currentCallback = onAudioCaptured

            val thread = Thread {
                captureAudio(targetLine)
            }

            thread.isDaemon = true
            recordingThread = thread
            thread.start()
        }
    }

    /**
     * Detiene la grabación actual y devuelve el audio capturado hasta ese momento.
     */
    fun stop(): ByteArray {
        val targetLine: TargetDataLine?
        val output: ByteArray

        synchronized(lock) {
            if (!recording) {
                return currentOutput?.toByteArray() ?: ByteArray(0)
            }

            recording = false

            targetLine = line
            output = currentOutput?.toByteArray() ?: ByteArray(0)

            currentCallback = null
        }

        try {
            targetLine?.stop()
        } catch (_: Exception) {
            // La línea puede haberse cerrado automáticamente.
        }

        try {
            targetLine?.close()
        } catch (_: Exception) {
            // La línea puede haberse cerrado automáticamente.
        }

        return output
    }

    /**
     * Mantiene la captura hasta que se solicita detenerla o se detecta
     * aproximadamente un segundo de silencio.
     */
    private fun captureAudio(targetLine: TargetDataLine) {
        val output = currentOutput ?: return
        val buffer = ByteArray(4096)

        var silentBytes = 0

        // Aproximadamente un segundo de silencio:
        // 16.000 muestras * 2 bytes por muestra.
        val maxSilenceBytes = 16_000 * 2

        val silenceThreshold = 500

        var capturedBySilence = false

        try {
            while (recording) {
                val bytesRead = targetLine.read(buffer, 0, buffer.size)

                if (bytesRead <= 0) continue

                output.write(buffer, 0, bytesRead)

                var maxAmplitude = 0
                var index = 0

                while (index + 1 < bytesRead) {
                    val sample =
                        (buffer[index].toInt() and 0xFF) or
                            (buffer[index + 1].toInt() shl 8)

                    val amplitude = kotlin.math.abs(sample)

                    if (amplitude > maxAmplitude) {
                        maxAmplitude = amplitude
                    }

                    index += 2
                }

                if (maxAmplitude < silenceThreshold) {
                    silentBytes += bytesRead

                    if (silentBytes >= maxSilenceBytes) {
                        capturedBySilence = true
                        recording = false
                    }
                } else {
                    silentBytes = 0
                }
            }
        } finally {
            try {
                targetLine.stop()
            } catch (_: Exception) {
                // La línea puede haberse detenido previamente.
            }

            try {
                targetLine.close()
            } catch (_: Exception) {
                // La línea puede haberse cerrado previamente.
            }

            val audio = output.toByteArray()

            synchronized(lock) {
                if (line === targetLine) {
                    line = null
                }

                if (recordingThread === Thread.currentThread()) {
                    recordingThread = null
                }

                currentOutput = null

                val callback = if (capturedBySilence) {
                    currentCallback
                } else {
                    null
                }

                currentCallback = null

                if (callback != null && audio.isNotEmpty()) {
                    callback(audio)
                }
            }
        }
    }
}