# Applies to the independent HearClear app

# Listening baseline investigation

## Findings from code inspection

The previous playback path used YAMNet scene confidence to apply a 0.2 multiplier to the entire microphone mixture. This could reduce a person's speech along with the fan. Its adaptive expander could also learn sustained speech as noise, and stacking it with a device noise suppressor made comparisons difficult. These are plausible causes of unclear/pumping speech, not a diagnosis of this phone's acoustic echo.

Playback now has no YAMNet dependency. The optional software filter is a fixed linear 180 Hz–6 kHz band-pass with no gate/noise learner. Android NoiseSuppressor has its own switch. Both default off under new preference keys; old ear/boost settings persist until reset. No gain or EQ is derived from an audiogram. Output is **not calibrated to safe ear-level loudness**.

The fixed filter does not separate sources, reject all fans, or improve every voice. Gain leveling/peak limiting can still change a loud mixture: compare initially at 1×, flat EQ. Do not evaluate by repeatedly increasing gain.

## Repeatable automated checks

Run on Windows with JDK 17 and the Android SDK:

```powershell
.\gradlew.bat :hearclear:testDebugUnitTest :hearclear:assembleDebug --console=plain
python tools/test_listening_delay.py
```

The GitHub Actions workflow also runs Android build/JUnit tests, Java signal checks, generates comparison WAV/CSV artifacts, and checks the external delay analysis.

For custom recordings, compile the standalone comparator with JDK 17:

```powershell
javac -d build/listening-check hearclear/src/main/java/com/dipak/hearclear/SpeechFocusFilter.java hearclear/src/test/java/com/dipak/hearclear/ListeningComparisonCheck.java
java -cp build/listening-check com.dipak.hearclear.ListeningComparisonCheck speech.wav fan.wav build/real-comparison
```

Inputs must be equal-length mono PCM16 little-endian WAVs at the same 16 or 48 kHz rate, with at least one second of audio. Keep combined peak below 0.95. Use recordings you have permission to use. With no arguments, the comparator creates deterministic **synthetic speech-like tones and fan-like noise**, not actual speech. It outputs speech-only, fan-only and mixed signals for both-off/software-only modes, RMS dBFS, peak and clipping counts. It checks that filtering a mixture equals filtering each source then adding them, so source labels cannot attenuate speech. Synthetic sustained speech also checks no learned fade over time. These checks do not establish intelligibility, clinical benefit, or Android NS behavior.

### Local host results

| Fixture (synthetic) | Both off RMS dBFS | Software only RMS dBFS |
| --- | ---: | ---: |
| Speech-like | -34.703 | -36.886 |
| Fan-like | -30.338 | -43.507 |
| Mixed | -28.982 | -36.028 |

No clipping in these fixtures. The speech-like signal loses about 2.18 dB, so this filter is not a universal clarity improvement. Rumble response/silence, stereo channel order/levels, and gain/peak-limit checks pass at 16/48 kHz. Python delay-estimator tests recover a simulated 137 ms delay within 1 ms and reject silence. **137 ms is a test fixture, not a measured phone delay.** Android-only effects, real speech intelligibility, and physical end-to-end latency remain unmeasured until device testing.

## Device comparison: four modes × three signals × two routes

Use an external speaker playing the same clean speech WAV, fan WAV and their fixed mix, at unchanged speaker volume/positions. Keep the phone, fan/speaker and headphones in fixed positions. If a live fan is used instead of a recording, note that it is less repeatable. A prerecorded fan mixed with speech measures a different acoustic case than spatially separated sources.

1. Record phone model, Android version, headphone model, connection, app commit and system media volume step.
2. Turn off Android Mono audio for ear separation. Disable optional headphone spatial/surround/ANC/transparency processing for the first controlled run, recording any settings you cannot disable.
3. Open HearClear, expand **More settings**, and tap **Reset sound settings**. This resets ear levels/tones and both processing switches, but does not change system media volume.
4. Start assist-only listening. Let routing settle, then capture the diagnostics. They use **AudioRecord/AudioTrack routedDevice**, not merely connected-device inventory. A wired headset may capture through its own mic; log what actually happens.
5. Run all rows below on wired and Bluetooth separately, stopping/restarting between rows to reset software/effect state. Give device NS two seconds to settle. Do three trials per row, randomizing row order after the first trial. Replay the same files with the same settings. Do not claim a mode was applied unless NS status confirms it.
6. Collect the headphone acoustic output with a coupler/external recorder, not with a person wearing it for instrument tests. Keep setup identical. For a listening comparison, ask a consenting listener to repeat the same speech sentences, recording word accuracy and effort at comfortable levels. This is not a calibrated fitting.
7. After the neutral baseline, repeat only justified changes one at a time. Note any clipping, pumping, missing syllables, metallic artifacts, fan increase, dropouts and diagnostic underruns.

| Mode | Software switch | Android NS switch | Signals per route |
| --- | --- | --- | --- |
| Both off | Off | Off, device-confirmed | Speech, fan, fixed mixture |
| Software only | On | Off, device-confirmed | Speech, fan, fixed mixture |
| Android NS only | Off | On, device-confirmed | Speech, fan, fixed mixture |
| Combined | On | On, device-confirmed | Speech, fan, fixed mixture |

If the effect reports unavailable, no control, request failure or mismatched state, mark that NS trial **not applied**, rather than treating it as on/off. A device-confirmed effect state does not prove all vendor capture processing is bypassed. Do not simulate Android NS on the host.

Collect logs:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -v time -s HearClearDiagnostics:I
```

Diagnostics show actual input/output type, name and ID; requested software mode; actual Android NS status; sample rate; settings; latest read and processing/write times; and track underruns. Read/processing durations are **not end-to-end latency**, and are not called that in the UI.

## External end-to-end delay measurement

The app cannot determine acoustic microphone-to-ear delay just by timing read/write calls, especially over Bluetooth. Use two synchronized external recording channels:

- Channel 1 records the direct reference pulse adjacent to the phone's **actual input microphone**.
- Channel 2 records the headphone's acoustic return in an isolated coupler. Keep headphone output out of the reference mic and phone mic to avoid leakage/feedback.
- Account for microphone spacing/sound travel. Use a stereo recorder/interface sharing one clock, with two independently placed microphones. A phone's single mic recording both sounds is not an equivalent isolated two-channel measurement.

Generate an irregular pulse pattern:

```powershell
python tools/measure_listening_delay.py --make-reference reference.wav
```

Play it on the external speaker while live listening is running, and record the channels above as uncompressed PCM16 stereo. With the setup checked:

```powershell
python tools/measure_listening_delay.py wired_trial1.wav
python tools/measure_listening_delay.py bluetooth_trial1.wav --max-ms 1500
```

The script estimates the nonnegative envelope correlation delay at approximately 1 ms resolution, rejects silence/weak matches and boundary peaks, and reports correlation and clipping. Correlation is not proof the setup is valid: direct leakage or automatic recorder processing can produce a convincing wrong peak. Inspect recordings and repeat at least three trials per processing mode/route; report median and range. End-to-end results are **pending**, not the app's 10 ms read size or 40 ms buffer target.

### Record results

| Phone/headphones | Actual mic/output | Mode | Signal | Trial | Media volume | NS actual status | Word accuracy/artifacts | Delay ms | Correlation | Underruns |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Pending device test | | | | | | | | | | |

## Remaining decisions

Only evaluate a small real-time speech denoiser after the above baseline is collected. Compare it to both-off and individual effects using the same real speech/fan recordings, latency and artifact tests. Do not stack an untested model with both suppressors, infer medical settings from the audiogram, or claim the digital limiter guarantees safe ear-level output.

