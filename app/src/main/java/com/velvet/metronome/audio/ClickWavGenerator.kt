package com.velvet.metronome.audio

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/**
 * Per project brief: temporary placeholder click sound.
 * Writes an 800Hz / 10ms sine burst (1ms linear fade in/out) WAV to internal storage
 * on first launch, until real samples are added. The C++ engine currently
 * synthesises the same waveform in memory; this file exists as the canonical
 * artefact that future versions (or external tools) can load.
 */
object ClickWavGenerator {
    private const val FILENAME    = "click.wav"
    private const val SAMPLE_RATE = 48000
    private const val FREQ_HZ     = 800.0
    private const val DURATION_MS = 10
    private const val FADE_MS     = 1
    private const val AMP         = 0.7

    fun ensureClickWav(context: Context): File {
        val file = File(context.filesDir, FILENAME)
        if (!file.exists()) generate(file)
        return file
    }

    private fun generate(out: File) {
        val numSamples  = SAMPLE_RATE * DURATION_MS / 1000
        val fadeSamples = SAMPLE_RATE * FADE_MS / 1000
        val pcm = ShortArray(numSamples)
        val twoPiF = 2.0 * PI * FREQ_HZ
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = when {
                i < fadeSamples              -> i.toDouble() / fadeSamples
                i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                else                          -> 1.0
            }
            val amplitude = sin(twoPiF * t) * AMP * env
            pcm[i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        writeWav(out, pcm, SAMPLE_RATE)
    }

    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val byteRate  = sampleRate * 2 // 16-bit mono
        val dataSize  = pcm.size * 2
        val totalSize = 36 + dataSize
        FileOutputStream(file).use { fos ->
            fos.write("RIFF".toByteArray())
            fos.write(intLE(totalSize))
            fos.write("WAVE".toByteArray())
            fos.write("fmt ".toByteArray())
            fos.write(intLE(16))     // fmt chunk size
            fos.write(shortLE(1))    // PCM
            fos.write(shortLE(1))    // mono
            fos.write(intLE(sampleRate))
            fos.write(intLE(byteRate))
            fos.write(shortLE(2))    // block align
            fos.write(shortLE(16))   // bits per sample
            fos.write("data".toByteArray())
            fos.write(intLE(dataSize))
            for (s in pcm) fos.write(shortLE(s.toInt()))
        }
    }

    private fun intLE(v: Int) = byteArrayOf(
        (v and 0xff).toByte(),
        ((v shr 8)  and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(),
        ((v shr 24) and 0xff).toByte()
    )

    private fun shortLE(v: Int) = byteArrayOf(
        (v and 0xff).toByte(),
        ((v shr 8) and 0xff).toByte()
    )
}
