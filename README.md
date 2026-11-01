# HearClear

Standalone Android live-listening app with optional RNNoise speech-noise reduction. The app module is `:hearclear`; the application ID is `com.dipak.hearclear`.

## Accounts and payment trial (implementation branch)

This branch adds server-timed 48-hour trials, one active phone per account, free transfers, lifetime access and tiered one-time pricing from ₹50 to ₹250. It needs an owner-deployed HTTPS backend and real receiving UPI details. It is not a live paid service; unconfigured builds keep listening locked. See [deployment, manual payment verification, privacy and device tests](docs/ACCOUNTS_AND_PAYMENTS.md).

## Requirements

- JDK 17
- Android SDK platform 35
- Android NDK `27.2.12479018`
- CMake `3.22.1`

If needed, install the Android SDK, NDK, and CMake components through Android Studio's SDK Manager and accept their licenses.

## Clone, build, test, and install (PowerShell)

```powershell
git clone https://github.com/dipakp17-art/HearClear.git
Set-Location .\HearClear
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :hearclear:assembleDebug :hearclear:testDebugUnitTest --console=plain
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb devices
& $adb install -r ".\hearclear\build\outputs\apk\debug\hearclear-debug.apk"
& $adb shell monkey -p com.dipak.hearclear 1
```

If your Android SDK is installed somewhere else, set `ANDROID_HOME` to that directory before building.
The install command installs or updates HearClear on the connected device. The APK output is `hearclear\build\outputs\apk\debug\hearclear-debug.apk`.

## Sharing and updates

Download installable test APKs from [GitHub Releases](https://github.com/dipakp17-art/HearClear/releases/latest). The app's Share button opens this download page. Android may ask users to allow installation from the browser or file-sharing app they use.

These are manually installed test builds, not Google Play releases. Users must download and install each update themselves. GitHub's download count measures file downloads, not unique installs or active users. The account-enabled build sends account/device entitlement requests and payment references to the configured server; it does not upload audio. See the account data notice and deployment documentation. Older public test APKs remain unrestricted.

## Project documentation

- [App overview and requirements](docs/HEARCLEAR.md)
- [Listening diagnosis and device test plan](docs/LISTENING_BASELINE.md)
- [RNNoise integration, tests, and limitations](docs/RNNOISE_LISTENING.md)

## Safety and scope

This is experimental listening assistance, not a fitted medical hearing aid. Settings are not prescribed from an audiogram; output is not calibrated to sound pressure at the ear. Start at low volume, use headphones, and stop if uncomfortable. Consult an audiologist for individualized fitting and calibrated real-ear measurements.

