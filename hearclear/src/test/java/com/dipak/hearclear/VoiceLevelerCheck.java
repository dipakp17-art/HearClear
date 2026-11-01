package com.dipak.hearclear;

public final class VoiceLevelerCheck {
    private static double rms(int rate, int ceiling, double amplitude) {
        VoiceLeveler leveler = new VoiceLeveler(rate);
        double energy = 0;
        for (int i = 0; i < rate * 3; i++) {
            double y = leveler.process(amplitude * Math.sin(2 * Math.PI * 1500 * i / rate), ceiling);
            if (!Double.isFinite(y) || Math.abs(y) > 0.85000001)
                throw new AssertionError("Output limit violated");
            if (i >= rate * 2) energy += y * y;
        }
        return Math.sqrt(energy / rate);
    }

    public static void main(String[] args) {
        for (int rate : new int[]{16000, 48000}) {
            double unity = rms(rate, 1, 0.002);
            double boosted = rms(rate, 20, 0.002);
            if (boosted < unity * 18) throw new AssertionError("Quiet voice boost too weak");
            rms(rate, 20, 1.0);
            VoiceLeveler leveler = new VoiceLeveler(rate);
            for (int i = 0; i < rate; i++)
                if (leveler.process(0, 20) != 0) throw new AssertionError("Silence not preserved");
            for (int i = 0; i < rate; i++) leveler.process(0.002, 20);
            double lowered = leveler.process(0.002, 1);
            if (lowered > 0.002001) throw new AssertionError("Lowered ceiling ignored");
            double transientOut = leveler.process(1.0, 20);
            if (Math.abs(transientOut) > 0.85000001) throw new AssertionError("Transient escaped limiter");
            System.out.println(rate + " Hz: quiet voice gain, silence, peak/transient limiting and lowered gain ceiling passed");
        }
    }
}


