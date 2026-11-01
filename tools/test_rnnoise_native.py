#!/usr/bin/env python3
"""Test vendored RNNoise native model on host; synthetic fixtures do not establish speech intelligibility."""
import argparse
import array
import csv
import ctypes
import math
from pathlib import Path
import random
import statistics
import sys
import time
import wave

RATE = 48000
FRAME = 480


def read(path):
    with wave.open(str(path), "rb") as source:
        if (source.getnchannels(), source.getsampwidth(), source.getframerate(), source.getcomptype()) != (1, 2, RATE, "NONE"):
            raise ValueError("Use mono PCM16, 48 kHz WAV")
        pcm = array.array("h", source.readframes(source.getnframes()))
    if sys.byteorder != "little":
        pcm.byteswap()
    return list(pcm)


def write(path, samples):
    pcm = array.array("h", [max(-32768, min(32767, round(v))) for v in samples])
    if sys.byteorder != "little":
        pcm.byteswap()
    with wave.open(str(path), "wb") as target:
        target.setparams((1, 2, RATE, len(pcm), "NONE", "not compressed"))
        target.writeframes(pcm.tobytes())


def rms(samples):
    return math.sqrt(sum(v * v for v in samples) / max(1, len(samples)))


def process(lib, samples):
    state = lib.rnnoise_create()
    if not state:
        raise RuntimeError("Native state allocation failed")
    output, timings = [], []
    frame_type = ctypes.c_float * FRAME
    try:
        for offset in range(0, len(samples), FRAME):
            chunk = samples[offset:offset + FRAME]
            frame = frame_type(*(chunk + [0] * (FRAME - len(chunk))))
            started = time.perf_counter_ns()
            lib.rnnoise_process_frame(state, frame, frame)
            timings.append((time.perf_counter_ns() - started) / 1e6)
            result = list(frame)[:len(chunk)]
            if not all(math.isfinite(v) for v in result):
                raise AssertionError("Non-finite native output")
            output.extend(result)
    finally:
        lib.rnnoise_destroy(state)
    return output, timings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library")
    parser.add_argument("--speech", type=Path)
    parser.add_argument("--noise", type=Path)
    parser.add_argument("--output", type=Path, default=Path("build/rnnoise-comparison"))
    args = parser.parse_args()
    if bool(args.speech) != bool(args.noise):
        parser.error("Provide both --speech and --noise")
    lib = ctypes.CDLL(str(Path(args.library).resolve()))
    lib.rnnoise_create.argtypes = []
    lib.rnnoise_create.restype = ctypes.c_void_p
    lib.rnnoise_process_frame.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.POINTER(ctypes.c_float)]
    lib.rnnoise_process_frame.restype = ctypes.c_float
    lib.rnnoise_destroy.argtypes = [ctypes.c_void_p]
    lib.rnnoise_destroy.restype = None
    silence, _ = process(lib, [0] * RATE)
    if max(abs(v) for v in silence) > 0.01:
        raise AssertionError("Silence produced unexpected output")
    synthetic = not args.speech
    if synthetic:
        speech, noise = [], []
        rng = random.Random(74123)
        for i in range(RATE * 4):
            t = i / RATE
            envelope = 0.3 + 0.7 * math.sin(2 * math.pi * 2 * t) ** 2
            speech.append(round(32768 * envelope * (0.03 * math.sin(2 * math.pi * 300 * t) +
                0.02 * math.sin(2 * math.pi * 1000 * t) + 0.01 * math.sin(2 * math.pi * 2500 * t))))
            noise.append(round(32768 * (0.04 * math.sin(2 * math.pi * 80 * t) +
                0.015 * math.sin(2 * math.pi * 160 * t) + 0.006 * (rng.random() * 2 - 1))))
    else:
        speech, noise = read(args.speech), read(args.noise)
    if len(speech) != len(noise) or len(speech) < RATE * 2:
        raise ValueError("Use equal lengths of at least two seconds")
    mixed = [a + b for a, b in zip(speech, noise)]
    if max(abs(v) for v in mixed) >= 31000:
        raise ValueError("Lower fixture volume to avoid input clipping")
    args.output.mkdir(parents=True, exist_ok=True)
    rng = random.Random(118)
    broadband = [round(1500 * (rng.random() * 2 - 1)) for _ in speech]
    cases = [("speech", speech), ("tonal_fan", noise), ("mixed", mixed)]
    if synthetic:
        cases += [("broadband_noise", broadband)]
    rows = []
    for name, samples in cases:
        processed, timings = process(lib, samples)
        repeat, _ = process(lib, samples)
        if repeat != processed:
            raise AssertionError("New native sessions are not deterministic")
        before, after = rms(samples[RATE:]), rms(processed[RATE:])
        change = 20 * math.log10(max(after, 1e-10) / max(before, 1e-10))
        peak = max(abs(v) for v in processed)
        if peak >= 32768:
            raise AssertionError("Fixture clipped before wrapper limiting")
        rows.append(dict(fixture=name, rms_change_db=round(change, 3), peak=round(peak, 2),
            mean_ms_per_10ms_frame=round(statistics.mean(timings), 3),
            p95_ms_per_frame=round(sorted(timings)[int(len(timings) * 0.95)], 3)))
        write(args.output / (name + "_raw.wav"), samples)
        write(args.output / (name + "_rnnoise.wav"), processed)
        if synthetic and name == "broadband_noise" and change > -3:
            raise AssertionError("Synthetic broadband fixture not reduced by 3 dB")
    with (args.output / "metrics.csv").open("w", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    (args.output / "README.txt").write_text(
        ("SYNTHETIC speech-like tones and fan-like noise; NOT real speech.\n" if synthetic else "User-provided fixtures.\n") +
        "Native model test only; Java streaming wrapper adds one additional 480-sample frame.\n" +
        "RMS reduction of a mixture does not prove speech preservation. No clinical/intelligibility claim.\n" +
        "Tonal fan hum may remain: keep its result separate from broadband noise.\n" +
        "Timings are this host CPU, not Android or end-to-end acoustic delay.\n")
    for row in rows:
        print(row)
    print("Native model silence, finite/bounded output, repeatable fresh sessions and fixture checks passed.")


if __name__ == "__main__":
    main()

