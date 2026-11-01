package com.dipak.hearclear;

/** Interleaves limited mono ear signals with independent, smoothly changing attenuation. */
public final class StereoPcm {
    private double left = 1.0, right = 1.0;
    private final double step;
    public StereoPcm(int sampleRate) {
        if (sampleRate <= 0) throw new IllegalArgumentException("Invalid sample rate");
        step = 1.0 - Math.exp(-1.0 / (sampleRate * 0.020));
    }
    public void mix(short[] l, short[] r, short[] out, int count, int leftPercent, int rightPercent) {
        if (count < 0 || count > l.length || count > r.length || count > out.length / 2)
            throw new IllegalArgumentException("Invalid buffer length");
        double lt = Math.max(0, Math.min(100, leftPercent)) / 100.0;
        double rt = Math.max(0, Math.min(100, rightPercent)) / 100.0;
        for (int i = 0; i < count; i++) {
            left += step * (lt - left);
            right += step * (rt - right);
            out[i * 2] = (short) Math.round(l[i] * left);
            out[i * 2 + 1] = (short) Math.round(r[i] * right);
        }
    }
}

