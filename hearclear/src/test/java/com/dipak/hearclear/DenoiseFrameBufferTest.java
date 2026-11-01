package com.dipak.hearclear;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public final class DenoiseFrameBufferTest {
    private static short[] run(int chunk) {
        DenoiseFrameBuffer buffer = new DenoiseFrameBuffer((in, out) -> System.arraycopy(in, 0, out, 0, 480));
        short[] result = new short[2400];
        for (int offset = 0; offset < result.length; offset += chunk) {
            int count = Math.min(chunk, result.length - offset);
            short[] piece = new short[count];
            for (int i = 0; i < count; i++) piece[i] = (short)(offset + i + 1);
            buffer.process(piece, count);
            System.arraycopy(piece, 0, result, offset, count);
        }
        return result;
    }
    @Test public void exactFrameDelayWithNoDroppedSamples() {
        short[] output = run(480);
        for (int i = 0; i < 480; i++) assertEquals(0, output[i]);
        for (int i = 480; i < output.length; i++) assertEquals(i - 479, output[i]);
    }
    @Test public void arbitraryReadBoundariesMatchWholeFrames() {
        assertArrayEquals(run(480), run(137));
        assertArrayEquals(run(480), run(1));
        assertArrayEquals(run(480), run(960));
    }
    @Test public void separateSessionsDoNotReplayOldSamples() {
        short[] input = new short[960]; Arrays.fill(input, (short)99);
        DenoiseFrameBuffer old = new DenoiseFrameBuffer((in, out) -> Arrays.fill(out, (short)99));
        old.process(input, input.length);
        DenoiseFrameBuffer fresh = new DenoiseFrameBuffer((in, out) -> System.arraycopy(in, 0, out, 0, 480));
        short[] next = new short[480]; Arrays.fill(next, (short)17); fresh.process(next, next.length);
        assertArrayEquals(new short[480], next);
    }
}

