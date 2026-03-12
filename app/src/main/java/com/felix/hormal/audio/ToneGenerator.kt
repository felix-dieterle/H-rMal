package com.felix.hormal.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

enum class Ear { LEFT, RIGHT, BOTH }

class ToneGenerator {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    companion object {
        const val SAMPLE_RATE = 44100
        private const val DB_REFERENCE = 90.0   // 90 dB HL = amplitude 1.0
    }

    /**
     * Play a tone at [frequencyHz] Hz at [dBHL] hearing level for [durationMs] ms in the given [ear].
     */
    fun playTone(frequencyHz: Int, dBHL: Int, durationMs: Int, ear: Ear) {
        stop()

        val amplitude = dBToAmplitude(dBHL)
        val numSamples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(numSamples * 2) // stereo

        val angularFreq = 2.0 * PI * frequencyHz / SAMPLE_RATE

        for (i in 0 until numSamples) {
            val sample = (amplitude * sin(angularFreq * i) * Short.MAX_VALUE).toInt().toShort()
            // Stereo: interleaved L, R
            buffer[i * 2]     = if (ear == Ear.RIGHT) 0 else sample  // LEFT channel
            buffer[i * 2 + 1] = if (ear == Ear.LEFT) 0 else sample   // RIGHT channel
        }

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(buffer.size * 2, minBufSize))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(buffer, 0, buffer.size)
        audioTrack?.play()
        isPlaying = true
    }

    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        isPlaying = false
    }

    private fun dBToAmplitude(dBHL: Int): Double {
        // 0 dB HL -> very quiet, 90 dB HL -> amplitude 1.0
        val db = dBHL.coerceIn(-10, 90)
        return 10.0.pow((db - DB_REFERENCE) / 20.0)
    }
}
