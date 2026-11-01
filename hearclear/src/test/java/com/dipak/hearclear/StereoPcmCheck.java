package com.dipak.hearclear;

public final class StereoPcmCheck {
    public static void main(String[] args) {
        for (int rate : new int[]{16000, 48000}) {
            StereoPcm mixer = new StereoPcm(rate);
            short[] left = new short[rate], right = new short[rate], out = new short[rate * 2];
            java.util.Arrays.fill(left, (short) 10000);
            java.util.Arrays.fill(right, (short) -20000);
            mixer.mix(left, right, out, rate, 100, 100);
            if (out[0] != 10000 || out[1] != -20000) throw new AssertionError("Ear channels swapped");
            mixer.mix(left, right, out, rate, 0, 50);
            if (out[out.length - 2] != 0 || out[out.length - 1] != -10000)
                throw new AssertionError("Independent ear levels incorrect");
            for (int i = 0; i < out.length; i += 2)
                if (out[i] < 0 || out[i] > 10000 || out[i + 1] < -20000 || out[i + 1] > 0)
                    throw new AssertionError("Unexpected gain or cross-channel leakage");
            boolean rejected = false;
            try { mixer.mix(left, right, new short[1], 1, 100, 100); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Invalid buffer accepted");
            System.out.println(rate + " Hz: stereo order, independent levels, attenuation limits and buffer checks passed");
        }
    }
}

