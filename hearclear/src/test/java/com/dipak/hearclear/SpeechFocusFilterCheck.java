package com.dipak.hearclear;

public final class SpeechFocusFilterCheck {
    private static double rms(int rate, double hz) {
        SpeechFocusFilter f = new SpeechFocusFilter(rate);
        double energy = 0;
        for (int i = 0; i < rate * 3; i++) {
            double y = f.process(0.02 * Math.sin(2 * Math.PI * hz * i / rate));
            if (!Double.isFinite(y)) throw new AssertionError("Non-finite output");
            if (i >= rate * 2) energy += y * y;
        }
        return Math.sqrt(energy / rate);
    }
    public static void main(String[] args) {
        for (int rate : new int[]{16000, 48000}) {
            double speechBand = rms(rate, 1500);
            double rumble = rms(rate, 60);
            if (speechBand < 0.008) throw new AssertionError("Speech band excessively reduced");
            if (rumble >= speechBand * 0.2) throw new AssertionError("Rumble rejection too weak");
            SpeechFocusFilter f = new SpeechFocusFilter(rate);
            for (int i = 0; i < rate; i++)
                if (f.process(0) != 0) throw new AssertionError("Silence produced output");
            System.out.println(rate + " Hz: fixed speech-band response, rumble and silence passed");
        }
    }
}


