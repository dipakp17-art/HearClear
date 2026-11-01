package com.dipak.hearclear

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RnNoiseIntegrationTest {
    private fun requireLibrary() {
        if (System.getProperty("hearclear.requireNative") == "true")
            assertTrue("CI must load the compiled host JNI library", RnNoiseNative.available)
        assumeTrue(RnNoiseNative.available)
    }
    @Test fun nativeSilenceAndSessionRelease() {
        requireLibrary()
        val denoiser = RnNoiseDenoiser()
        repeat(100) {
            val silence = ShortArray(480)
            denoiser.process(silence, silence.size)
            assertArrayEquals(ShortArray(480), silence)
        }
        denoiser.close()
        denoiser.close()
    }
    @Test fun freshSessionsAreDeterministicThroughJni() {
        requireLibrary()
        fun run(): ShortArray {
            val source = ShortArray(4800) { ((it * 47) % 2000 - 1000).toShort() }
            RnNoiseDenoiser().use { it.process(source, source.size) }
            return source
        }
        assertArrayEquals(run(), run())
    }
}

