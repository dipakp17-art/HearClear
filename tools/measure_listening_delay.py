#!/usr/bin/env python3
"""External two-channel acoustic delay measurement; never an in-app timing estimate."""
import argparse
import array
import json
import math
import random
import sys
import wave


def envelope(samples, rate):
    step = max(1, rate // 1000)
    result = [math.sqrt(sum(v * v for v in samples[i:i + step]) / len(samples[i:i + step]))
              for i in range(0, len(samples) - step + 1, step)]
    floor = sorted(result)[len(result) // 10]
    return [max(0.0, v - floor) for v in result], step


def estimate(reference, returned, rate, max_ms=1000):
    if len(reference) != len(returned) or len(reference) < rate:
        raise ValueError("Need matching reference/returned channels, at least 1 second")
    x, step = envelope(reference, rate)
    y, _ = envelope(returned, rate)
    max_lag = min(round(max_ms * rate / (1000 * step)), len(x) // 2)
    scores = []
    for lag in range(max_lag + 1):
        count = len(x) - lag
        xx = x[:count]
        yy = y[lag:lag + count]
        dot = sum(a * b for a, b in zip(xx, yy))
        norm = math.sqrt(sum(a * a for a in xx) * sum(b * b for b in yy))
        scores.append(dot / norm if norm else 0.0)
    best = max(range(len(scores)), key=scores.__getitem__)
    if scores[best] < 0.6:
        raise ValueError("No reliable matching pulse pattern; check isolation, levels and routing")
    if best == max_lag:
        raise ValueError("Peak at search boundary; increase --max-ms")
    return {"delay_ms": best * step * 1000 / rate,
            "resolution_ms": step * 1000 / rate, "envelope_correlation": scores[best],
            "method": "external dual-channel acoustic reference/return; not an SPL calibration"}


def make_reference(path, rate=48000):
    samples = array.array("h", [0]) * (rate * 7)
    rng = random.Random(27419)
    start = 0.5
    for index in range(10):
        duration = rng.uniform(0.035, 0.11)
        for i in range(int(duration * rate)):
            t = i / rate
            window = math.sin(math.pi * t / duration) ** 2
            value = 0.12 * window * math.sin(2 * math.pi * (700 + index * 113) * t)
            samples[int(start * rate) + i] = round(value * 32767)
        start += duration + rng.uniform(0.20, 0.38)
    if sys.byteorder != "little":
        samples.byteswap()
    with wave.open(str(path), "wb") as output:
        output.setparams((1, 2, rate, len(samples), "NONE", "not compressed"))
        output.writeframes(samples.tobytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", nargs="?", help="PCM16 stereo WAV: channel 1 direct reference, channel 2 headphone acoustic return")
    parser.add_argument("--make-reference", metavar="WAV")
    parser.add_argument("--max-ms", type=int, default=1000)
    args = parser.parse_args()
    if args.make_reference:
        make_reference(args.make_reference)
        print("Reference generated. Use an external speaker/recorder, not ears, for the measurement.")
        return
    if not args.capture:
        parser.error("Provide capture.wav or --make-reference reference.wav")
    with wave.open(args.capture, "rb") as source:
        if source.getnchannels() != 2 or source.getsampwidth() != 2 or source.getcomptype() != "NONE":
            raise ValueError("Use uncompressed PCM16 stereo capture")
        rate = source.getframerate()
        pcm = array.array("h", source.readframes(source.getnframes()))
    if sys.byteorder != "little":
        pcm.byteswap()
    result = estimate(pcm[0::2], pcm[1::2], rate, args.max_ms)
    result["clipped_samples"] = sum(abs(v) >= 32760 for v in pcm)
    result["warning"] = "Requires isolated channels. Acoustic leakage or unaligned microphones can invalidate this result."
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
