package com.dipak.hearclear;

/** Smooth quiet-signal gain with fast peak limiting, not a calibrated SPL limiter. */
public final class VoiceLeveler {
    private final double powerStep, gainUp, gainDown, limiterRelease;
    private double power, appliedGain = 1.0, limiter = 1.0;

    public VoiceLeveler(int sampleRate) {
        if (sampleRate < 16000) throw new IllegalArgumentException("Unsupported rate");
        powerStep = 1.0 - Math.exp(-1.0 / (sampleRate * 0.030));
        gainUp = 1.0 - Math.exp(-1.0 / (sampleRate * 0.200));
        gainDown = 1.0 - Math.exp(-1.0 / (sampleRate * 0.005));
        limiterRelease = 1.0 - Math.exp(-1.0 / (sampleRate * 0.080));
    }

    public double process(double input, int maximumGain) {
        int ceiling = Math.max(1, Math.min(20, maximumGain));
        power += powerStep * (input * input - power);
        // Quiet voices receive the requested boost; louder voices need less gain.
        double desired = Math.max(1.0, Math.min(ceiling, 0.10 / Math.max(0.0005, Math.sqrt(power))));
        appliedGain += (desired < appliedGain ? gainDown : gainUp) * (desired - appliedGain);
        // Honor a lowered user ceiling immediately.
        appliedGain = Math.min(appliedGain, ceiling);
        double value = input * appliedGain;
        double peak = Math.abs(value);
        double needed = peak > 0.85 ? 0.85 / peak : 1.0;
        if (needed < limiter) limiter = needed;
        else limiter += limiterRelease * (needed - limiter);
        return Math.max(-0.85, Math.min(0.85, value * limiter));
    }
}


