package com.dipak.hearclear;

/** Maintains 480-sample frame boundaries for arbitrary AudioRecord read sizes.
 * One frame of output delay; no mixing of undelayed raw audio with delayed processed audio.
 */
public final class DenoiseFrameBuffer {
    public interface Processor { void process(short[] input, short[] output); }
    private final Processor processor;
    private final short[] input = new short[480], output = new short[480];
    private int position;
    public DenoiseFrameBuffer(Processor processor) { this.processor = processor; }
    public void process(short[] samples, int count) {
        if (count < 0 || count > samples.length) throw new IllegalArgumentException("Invalid count");
        for (int i = 0; i < count; i++) {
            input[position] = samples[i];
            samples[i] = output[position];
            if (++position == 480) {
                processor.process(input, output);
                position = 0;
            }
        }
    }
}

