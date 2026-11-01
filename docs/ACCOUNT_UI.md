# Account and navigation update (1.3)

## What changed

The listening page has a compact menu with Account, Share app and App information. The short account status line also opens Account. Information and licensing have their own readable page rather than being mixed into sound settings. Sharing sends the currently installed universal APK through a read-only FileProvider grant instead of a link to an older public release. Only the dedicated `shared_apk/` cache directory is exposed; no account data or audio files are shared.

Account now shows one form at a time: sign-in, registration, transfer or password recovery. Payment and refresh controls are hidden while signed out; a signed-in dashboard shows access status, renewal, activation and sign-out. Account/password/recovery/consent errors appear before a network request. Transfers require explicit confirmation. Registration's private recovery code stays encrypted on this phone until the user confirms it is saved, so leaving the screen does not silently lose it.

Payment is a separate page, available only with a session. It displays the server-reserved price, recipient, QR, countdown and payment reference together. Pending references hide the QR/payment controls; paid accounts do not get another checkout. State-specific buttons replace the old blanket enable-all behavior. No payment callback unlocks access locally. Pricing, trials, one-device enforcement, verification hours and manual settlement checks are unchanged.

Main listening refreshes an existing session periodically while visible, rather than leaving a paid user with an expired in-memory lease and no automatic refresh. The foreground service still performs its own access checks. Account access can be opened even before headphones are connected or microphone permission is granted. Server HTML errors, expired tunnel addresses, timeouts and HTTPS failures now have actionable retry messages rather than JSON/parser errors. No TLS verification is bypassed. Failed unauthenticated sign-in no longer clears an unrelated valid local session.

## Validation

Build and run tests with the same configured HTTPS endpoint used for the installed test build:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :hearclear:assembleDebug :hearclear:testDebugUnitTest '-PhearclearApiUrl=https://lindsay-term-gibraltar-static.trycloudflare.com' --console=plain
```

That tunnel address is temporary; replace it with the current one if recreated. It is not hardcoded into the app. The test build remains on `feature/accounts-trial-payments` in PR #1. Install over the existing app with the same signing key; do not uninstall just to update, since uninstalling removes local session/recovery data.

Unit tests cover form rules (including Unicode password lengths matching Python), error messages and actual Android Activity/widget behavior with Robolectric: signed-out payment visibility, transfer/sign-in mode switching, short-password errors, registration consent/privacy navigation and opening Account from the main start action without headphones. CI also runs existing backend, native/JNI, audio and Windows setup checks. Robolectric is a test-only dependency.

Still test on the real phone: menu navigation/back/dark mode/narrow screens/font scaling; real signup/sign-in/transfer/recovery; private code copying/acknowledgement; checkout QR/UPI/receipt/approval; background/resume/session expiry; and APK sharing via the chosen sharing app, including recipient installation. These checks do not establish live tunnel reachability or a real payment.
