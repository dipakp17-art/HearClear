package com.dipak.hearclear

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ListeningPipelineTest {
    @Test fun bypassPreservesQuietPcmAndSourceBuffer() {
        val input = ShortArray(4800) { (500 * sin(2 * PI * 1000 * it / 48000)).toInt().toShort() }
        val expected = input.copyOf()
        LiveAssistProcessor(48000).process(input, input.size, 1, 0, 0, 0, false)
        input.indices.forEach { assertTrue(kotlin.math.abs(input[it].toInt() - expected[it]) <= 1) }
    }

    @Test fun processedMixtureIsIndependentOfChunkBoundaries() {
        val source = ShortArray(16000) {
            (800 * sin(2 * PI * 1200 * it / 16000) + 600 * sin(2 * PI * 80 * it / 16000)).toInt().toShort()
        }
        val whole = source.copyOf()
        LiveAssistProcessor(16000).process(whole, whole.size, 1, 0, 0, 0, true)
        val chunked = ShortArray(source.size)
        val processor = LiveAssistProcessor(16000)
        for (offset in source.indices step 160) {
            val chunk = source.copyOfRange(offset, offset + 160)
            processor.process(chunk, chunk.size, 1, 0, 0, 0, true)
            chunk.copyInto(chunked, offset)
        }
        assertArrayEquals(whole, chunked)
    }
}

