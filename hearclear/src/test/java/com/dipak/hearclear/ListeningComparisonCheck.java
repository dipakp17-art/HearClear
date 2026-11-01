package com.dipak.hearclear;

import java.nio.charset.StandardCharsets;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Deterministic raw/software comparison. No Android NS emulation or intelligibility claim.
 * Optional args: speech.wav fan.wav output-dir; no args generates synthetic speech-like fixtures.
 */
public final class ListeningComparisonCheck {
    private static double[] filter(double[] input, int rate) {
        SpeechFocusFilter f = new SpeechFocusFilter(rate);
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) output[i] = f.process(input[i]);
        return output;
    }
    private static double rms(double[] x) {
        double e = 0; for (double v : x) e += v * v;
        return Math.sqrt(e / x.length);
    }
    private static int u16(byte[] b, int p) {
        return (b[p] & 255) | ((b[p + 1] & 255) << 8);
    }
    private static long u32(byte[] b, int p) {
        return (u16(b, p) | ((long)u16(b, p + 2) << 16));
    }
    private static boolean tag(byte[] b, int p, String value) {
        return p + 4 <= b.length && new String(b, p, 4, StandardCharsets.US_ASCII).equals(value);
    }
    private static double[] read(Path file, int[] rate) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length < 12 || !tag(bytes, 0, "RIFF") || !tag(bytes, 8, "WAVE"))
            throw new IllegalArgumentException("Expected RIFF WAVE");
        int format = 0, channels = 0, currentRate = 0, bits = 0, align = 0;
        int dataAt = -1, dataSize = 0;
        for (int pos = 12; pos + 8 <= bytes.length;) {
            long length = u32(bytes, pos + 4);
            long end = pos + 8L + length;
            if (end > bytes.length) throw new IllegalArgumentException("Truncated WAV chunk");
            int body = pos + 8;
            if (tag(bytes, pos, "fmt ")) {
                if (length < 16) throw new IllegalArgumentException("Invalid WAV format");
                format = u16(bytes, body); channels = u16(bytes, body + 2);
                currentRate = (int)u32(bytes, body + 4);
                align = u16(bytes, body + 12); bits = u16(bytes, body + 14);
            } else if (tag(bytes, pos, "data") && dataAt < 0) {
                dataAt = body; dataSize = (int)length;
            }
            long next = end + (length & 1);
            if (next > Integer.MAX_VALUE) throw new IllegalArgumentException("WAV too large");
            pos = (int)next;
        }
        if (format != 1 || channels != 1 || bits != 16 || align != 2 ||
            (currentRate != 16000 && currentRate != 48000) || dataAt < 0 || dataSize % 2 != 0)
            throw new IllegalArgumentException("Use mono little-endian PCM16 WAV, 16 or 48 kHz");
        if (rate[0] != 0 && rate[0] != currentRate) throw new IllegalArgumentException("Rates differ");
        if (dataSize < currentRate * 2) throw new IllegalArgumentException("Need at least one second");
        rate[0] = currentRate;
        double[] x = new double[dataSize / 2];
        for (int i = 0; i < x.length; i++) x[i] = (short)u16(bytes, dataAt + i * 2) / 32768.0;
        return x;
    }
    private static void littleInt(DataOutputStream out, int value) throws IOException {
        out.writeInt(Integer.reverseBytes(value));
    }
    private static void littleShort(DataOutputStream out, int value) throws IOException {
        out.writeShort(Short.reverseBytes((short)value));
    }
    private static void write(Path path, double[] x, int rate) throws Exception {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeBytes("RIFF"); littleInt(out, 36 + x.length * 2); out.writeBytes("WAVEfmt ");
            littleInt(out, 16); littleShort(out, 1); littleShort(out, 1);
            littleInt(out, rate); littleInt(out, rate * 2); littleShort(out, 2); littleShort(out, 16);
            out.writeBytes("data"); littleInt(out, x.length * 2);
            for (double value : x)
                littleShort(out, (int)Math.round(Math.max(-1, Math.min(0.999969, value)) * 32768));
        }
    }
    private static void text(Path path, String value) throws IOException {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 0 && args.length != 1 && args.length != 3)
            throw new IllegalArgumentException("Usage: [output-dir] OR speech.wav fan.wav output-dir");
        boolean real = args.length == 3;
        Path output = Paths.get(args.length == 0 ? "build/listening-comparison" : args[args.length - 1]);
        Files.createDirectories(output);
        int[] rateBox = {0};
        double[] speech, fan;
        if (real) {
            speech = read(Paths.get(args[0]), rateBox); fan = read(Paths.get(args[1]), rateBox);
            if (speech.length != fan.length) throw new IllegalArgumentException("Fixture lengths differ");
        } else {
            rateBox[0] = 48000;
            speech = new double[rateBox[0] * 4]; fan = new double[speech.length];
            Random random = new Random(74123);
            for (int i = 0; i < speech.length; i++) {
                double t = i / (double)rateBox[0];
                double envelope = 0.3 + 0.7 * Math.pow(Math.sin(2 * Math.PI * 2 * t), 2);
                speech[i] = envelope * (0.03 * Math.sin(2 * Math.PI * 300 * t) +
                    0.02 * Math.sin(2 * Math.PI * 1000 * t) + 0.01 * Math.sin(2 * Math.PI * 2500 * t));
                fan[i] = 0.04 * Math.sin(2 * Math.PI * 80 * t) +
                    0.015 * Math.sin(2 * Math.PI * 160 * t) + 0.006 * (random.nextDouble() * 2 - 1);
            }
        }
        int rate = rateBox[0];
        double[] mix = new double[speech.length];
        for (int i = 0; i < mix.length; i++) {
            mix[i] = speech[i] + fan[i];
            if (Math.abs(mix[i]) >= 0.95) throw new IllegalArgumentException("Lower fixture levels; mix would approach clipping");
        }
        double[] fs = filter(speech, rate), fn = filter(fan, rate), fm = filter(mix, rate);
        double error = 0;
        for (int i = 0; i < mix.length; i++) error = Math.max(error, Math.abs(fm[i] - fs[i] - fn[i]));
        if (error > 1e-9) throw new AssertionError("Mixture suppresses speech nonlinearly: " + error);
        if (!real) {
            double early = 0, late = 0;
            for (int i = rate; i < rate * 2; i++) {
                early += fs[i] * fs[i]; late += fs[i + rate * 2] * fs[i + rate * 2];
            }
            if (Math.abs(late / early - 1) > 0.001)
                throw new AssertionError("Sustained speech loses level over time");
        }
        StringBuilder csv = new StringBuilder("fixture,mode,rms_dbfs,peak,clipped_samples\n");
        String[] names = {"speech", "fan", "speech_plus_fan"};
        double[][] inputs = {speech, fan, mix}, outputs = {fs, fn, fm};
        for (int c = 0; c < names.length; c++) {
            for (int mode = 0; mode < 2; mode++) {
                double[] signal = mode == 0 ? inputs[c] : outputs[c];
                double peak = 0; int clipped = 0;
                for (double v : signal) {
                    peak = Math.max(peak, Math.abs(v)); if (Math.abs(v) >= 1) clipped++;
                }
                if (clipped != 0) throw new AssertionError("Unexpected clipping");
                String modeName = mode == 0 ? "both_off" : "software_only";
                csv.append(String.format(Locale.ROOT, "%s,%s,%.3f,%.6f,%d%n",
                    names[c], modeName, 20 * Math.log10(Math.max(1e-12, rms(signal))), peak, clipped));
                write(output.resolve(names[c] + "_" + modeName + ".wav"), signal, rate);
            }
        }
        text(output.resolve("metrics.csv"), csv.toString());
        text(output.resolve("README.txt"),
            (real ? "User-provided source fixtures." : "SYNTHETIC speech-like tones and fan-like noise; NOT actual speech or an intelligibility test.") +
            "\nGain 1x, no EQ; tests isolate the fixed software filter only.\nAndroid NS-only and combined modes require real-device capture.\n" +
            "The filter is linear: mixture equals filtered speech + filtered noise. Max error=" + error + "\n");
        System.out.print(csv);
        System.out.println("Mixture linearity and no learned sustained-speech attenuation: passed. Android NS: NOT TESTED on host.");
    }
}


