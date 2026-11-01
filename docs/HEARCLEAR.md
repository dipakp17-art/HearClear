# HearClear app

HearClear is a standalone Android application in the `:hearclear` module. It uses application ID `com.dipak.hearclear` and does not depend on SoundMonitor.

The app provides live microphone listening through connected headphones, optional RNNoise speech-noise reduction, an optional speech-band filter, optional Android NoiseSuppressor, stereo ear-level and tone controls, and route/processing diagnostics. Processing modes are independently switchable for comparison. Audio is not recorded by the listening app.

The RNNoise source is vendored from Xiph RNNoise v0.1 with its license, provenance, and checksums. See [RNNOISE_LISTENING.md](RNNOISE_LISTENING.md). The model and processing are experimental and cannot guarantee speech intelligibility or safe output loudness.

## Requirements

- JDK 17
- Android SDK with platform 35
- Android NDK `27.2.12479018`
- CMake `3.22.1`
- A connected Android device for installation and listening tests

Android Studio can install missing SDK components after you accept their licenses.

## Build, test, install

From PowerShell in the cloned repository:

```powershell
.\gradlew.bat :hearclear:assembleDebug :hearclear:testDebugUnitTest --console=plain
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb install -r ".\hearclear\build\outputs\apk\debug\hearclear-debug.apk"
& $adb shell monkey -p com.dipak.hearclear 1
```

For comparison procedures, synthetic limitations, and external acoustic delay measurement, see [LISTENING_BASELINE.md](LISTENING_BASELINE.md). GitHub Actions runs the Android build/tests and standalone signal checks on pushes and pull requests to `main`.

## Safety and limitations

HearClear is experimental listening assistance, not a medical hearing aid or a substitute for audiology care. Gain and EQ are not derived from an audiogram. The digital limiter is not calibrated to sound pressure at the ear and cannot guarantee safe levels. Start at low media volume and stop if listening is uncomfortable. Audiologist-directed fitting and calibrated real-ear measurements are required for individualized hearing-aid settings.
