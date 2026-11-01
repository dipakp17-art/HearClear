# HearClear

Free Android live-listening test app with optional offline RNNoise reduction, separate ear controls and APK sharing. Application ID: `com.dipak.hearclear`; module: `:hearclear`.

## Current version: 1.4 — free offline testing

- No sign-in, payment or 48-hour limit.
- No laptop server, Cloudflare tunnel or internet connection required for listening.
- Everyone can install and test this APK. Menu → Share app sends the installed APK.
- Account/payment screens and network checks are disabled. Earlier saved sessions do not restrict listening.
- The APK has no INTERNET permission. Microphone processing stays on the phone.

The account/payment implementation remains in the repository for a possible future update. It is **not active in this version**. Existing deployment/payment documents describe that paused implementation, not requirements for the free test app. See [free testing notes](docs/FREE_TESTING.md).

## Requirements

JDK 17, Android SDK platform 35, NDK `27.2.12479018`, CMake `3.22.1`. Install missing SDK tools through Android Studio's SDK Manager. Supported phones: Android 8.0+.

## Update, build and install (PowerShell)

In your existing HearClear checkout:

```powershell
git pull --ff-only
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :hearclear:assembleDebug --console=plain
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r ".\hearclear\build\outputs\apk\debug\hearclear-debug.apk"
```

The free test update is on `feature/accounts-trial-payments` in PR #1. New checkouts can use `git clone --branch feature/accounts-trial-payments https://github.com/dipakp17-art/HearClear.git`. No account API URL or server command is needed. Install over the existing app using the same signing key; do not uninstall just to update.

For unit tests run `:hearclear:testDebugUnitTest`. CI also checks the preserved backend/Windows tooling and existing native/JNI/audio comparisons. Account-screen tests are intentionally skipped while accounts are disabled; free-mode UI/permission/access tests run instead.

## Listening

Connect headphones, allow microphone access and start at 1× boost. Place the phone near the speaker. Try one noise reduction option at a time; RNNoise needs 48 kHz capture and does not remove every fan or background sound. Open Menu → App information for instructions, privacy and licences.

This is experimental assistance, not a medically fitted hearing aid. Output is not calibrated to ear-level loudness. Start low and stop if uncomfortable.

## Documentation

- [Free testing release](docs/FREE_TESTING.md)
- [Screen layout and navigation](docs/ACCOUNT_UI.md) — account/payment sections describe the paused implementation
- [Audio baseline/device tests](docs/LISTENING_BASELINE.md)
- [RNNoise source, tests and limitations](docs/RNNOISE_LISTENING.md)
