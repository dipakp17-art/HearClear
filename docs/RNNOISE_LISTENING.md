# RNNoise listening tests

HearClear includes an optional RNNoise speech-noise-reduction stage from [Xiph RNNoise](https://github.com/xiph/rnnoise), pinned to v0.1 / commit `cdf196b1e9de2f8ff1003328ebf9a4316477429d`. This is the older original model, not the current upstream model. Source and built-in weights are vendored for offline builds; the BSD license, provenance, and checksums are included.

The native stage processes 480-sample frames at 48 kHz on PCM16 amplitude scale, before boost and ear-specific processing. The streaming frame assembler adds buffering. RNNoise is bypassed at 16 kHz or when the native library is unavailable; no resampling is silently applied. The model may leave tonal fan hum and can alter speech. A reduction in mixture loudness is not proof of improved intelligibility.

## Build

The first native build requires Android NDK `27.2.12479018` and CMake `3.22.1`.

```powershell
.\gradlew.bat :hearclear:assembleDebug :hearclear:testDebugUnitTest --console=plain
```

CI additionally builds the pinned host library and requires the JNI integration test. Locally, native JNI tests skip if the host Linux library is unavailable; pure Java/Kotlin tests and Android native compilation still run.

## Host comparison

CI compiles the vendored C library and runs:

```text
python tools/test_rnnoise_native.py /path/to/library.so --speech speech.wav --noise fan.wav --output build/real-rnnoise
```

Inputs must be equal-length mono PCM16 48 kHz WAV files, at least two seconds long, with no clipping in their mixture. Without speech/noise arguments, the script uses synthetic fixtures, not real speech.

Prior synthetic host measurements showed near-zero change to speech-like tones, near-zero change to tonal fan hum, a modest reduction of a synthetic mixture, and stronger reduction of broadband noise. Host timing is not Android-device performance or microphone-to-ear delay. These results do not establish intelligibility or clinical benefit.

## Device validation still required

- Compare RNNoise, software filter, Android NoiseSuppressor, and both-off separately using the same real speech, fan, and mixed signals.
- Measure word accuracy, listening effort, pumping, missing syllables, and artifacts—not noise loudness alone.
- Confirm actual mic/headphone routes, native-active status, CPU/battery use, dropouts, and wired versus Bluetooth acoustic latency.
- Do not infer hearing-aid gain/EQ from an audiogram or treat digital limiting as calibrated ear-level protection.
