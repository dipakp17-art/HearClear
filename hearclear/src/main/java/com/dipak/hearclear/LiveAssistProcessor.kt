package com.dipak.hearclear

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos

/**
 * Experimental speech-oriented tone controls and gentle peak compression for
 * headphone listening. This is not a hearing-loss fitting or a calibrated SPL limiter.
 */
internal class LiveAssistProcessor(private val sampleRate: Int) {
    private val focus = SpeechFocusFilter(sampleRate)
    private val bass = PeakingBand(sampleRate, 250.0)
    private val speech = PeakingBand(sampleRate, 1500.0)
    private val treble = PeakingBand(sampleRate, 4000.0)
    private val leveler = VoiceLeveler(sampleRate)

    fun process(samples: ShortArray, count: Int, gain: Int, bassDb: Int, speechDb: Int, trebleDb: Int, speechFocus: Boolean = false) {
        bass.setGain(bassDb.coerceIn(-6, 6))
        speech.setGain(speechDb.coerceIn(-6, 6))
        treble.setGain(trebleDb.coerceIn(-6, 6))

        for (i in 0 until count) {
            val raw = samples[i] / 32768.0
            val filtered = focus.process(raw)
            val input = if (speechFocus) filtered else raw
            val shaped = treble.process(speech.process(bass.process(input)))
            val limited = leveler.process(shaped, gain)
            samples[i] = (limited * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private class PeakingBand(
        private val sampleRate: Int,
        private val frequency: Double,
        private val q: Double = 0.8,
    ) {
        private var gainDb = Int.MIN_VALUE
        private var b0 = 1.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0
        private var z1 = 0.0
        private var z2 = 0.0

        fun setGain(db: Int) {
            if (gainDb == db) return
            gainDb = db
            val amplitude = 10.0.pow(db / 40.0)
            val omega = 2.0 * PI * frequency / sampleRate
            val alpha = sin(omega) / (2.0 * q)
            val cosOmega = cos(omega)
            val a0 = 1.0 + alpha / amplitude
            b0 = (1.0 + alpha * amplitude) / a0
            b1 = (-2.0 * cosOmega) / a0
            b2 = (1.0 - alpha * amplitude) / a0
            a1 = (-2.0 * cosOmega) / a0
            a2 = (1.0 - alpha / amplitude) / a0
        }

        fun process(input: Double): Double {
            val output = b0 * input + z1
            z1 = b1 * input - a1 * output + z2
            z2 = b2 * input - a2 * output
            return output
        }
    }
}




