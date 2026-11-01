package com.dipak.hearclear

/** Per-session native state. Access and release only on the audio worker. */
internal object RnNoiseNative {
    val available: Boolean = runCatching { System.loadLibrary("hearclear_rnnoise") }.isSuccess
    external fun create(): Long
    external fun process(handle: Long, input: ShortArray, output: ShortArray)
    external fun destroy(handle: Long)
}

internal class RnNoiseDenoiser : AutoCloseable {
    private var handle = if (RnNoiseNative.available) RnNoiseNative.create() else 0L
    init { check(handle != 0L) { "RNNoise unavailable on this device." } }
    private val frames = DenoiseFrameBuffer { input, output ->
        check(handle != 0L)
        RnNoiseNative.process(handle, input, output)
    }
    fun process(samples: ShortArray, count: Int) = frames.process(samples, count)
    override fun close() {
        if (handle != 0L) { RnNoiseNative.destroy(handle); handle = 0 }
    }
}

