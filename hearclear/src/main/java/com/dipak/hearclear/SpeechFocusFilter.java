package com.dipak.hearclear;

/**
 * Fixed, linear speech-band filter. It has no gate, noise learner, classifier input,
 * or whole-scene gain. It reduces low rumble but cannot separate overlapping sources.
 */
public final class SpeechFocusFilter {
    private final double highAlpha, lowAlpha;
    private double previousInput, high1, previousHigh1, high2, low1, low2;

    public SpeechFocusFilter(int sampleRate) {
        if (sampleRate < 16000) throw new IllegalArgumentException("Unsupported sample rate");
        double dt = 1.0 / sampleRate;
        double highRc = 1.0 / (2.0 * Math.PI * 180.0);
        double lowRc = 1.0 / (2.0 * Math.PI * 6000.0);
        highAlpha = highRc / (highRc + dt);
        lowAlpha = dt / (lowRc + dt);
    }

    public double process(double input) {
        high1 = highAlpha * (high1 + input - previousInput);
        previousInput = input;
        high2 = highAlpha * (high2 + high1 - previousHigh1);
        previousHigh1 = high1;
        low1 += lowAlpha * (high2 - low1);
        low2 += lowAlpha * (low1 - low2);
        return low2;
    }
}


