# Version 1.4: unrestricted offline testing

The owner has paused sign-in, trials, payments and one-device enforcement to test listening first. Everyone can use and share this version with no 48-hour cutoff. The laptop, account server and Cloudflare tunnel may be off.

## Implementation

`BuildConfig.ACCOUNTS_ENABLED` is hardcoded false in the Android module for this release; it is not automatically enabled by an API URL or an old local Gradle property. The manifest disables AccountActivity and PaymentActivity and removes INTERNET permission. Direct lifecycle guards also redirect disabled screens to App information. The menu hides Account and the account status line. Share app and App information remain available.

Access checks allow listening regardless of stored tokens, lease expiration, trial deadlines or payment state. Foreground service access polling and main-screen account refresh do not run. Explicit account refresh is a local no-op. No account or payment request can be sent through the normal app flow, even if an old API URL was supplied to the build. The audio capture, headphone checks, gain, limiter, ear controls and optional denoising are unchanged.

Earlier local account data is retained but does not restrict free listening. Sharing copies the installed APK only; it does not include private preferences, credentials or recordings. Upgrading over the old APK with the same signing key preserves local settings.

## Tests

Free-mode Robolectric checks cover listening without a valid session/lease, no-op account refresh, the two-item menu, hidden account entry, disabled account/payment activities, no INTERNET permission and accurate offline/free information. Dormant account UI tests are skipped while the flag is false; backend policy, form validation and existing audio tests remain available.

Still test on phones: headphones/permission/start/stop, listening beyond the previous 75-second access window, airplane-mode operation with wired headphones, Bluetooth media operation, old-account upgrades and APK sharing/recipient installation. No clinical listening benefit is claimed by software tests.

## Future paid version

Payment/account code and backend tools remain for future development. Re-enabling requires a deliberate source update, restoring the network permission, configuring a stable HTTPS server, testing the account/payment flow and explaining the new rules to users. Simply starting the server or supplying `hearclearApiUrl` cannot activate payment enforcement in this free build. Already distributed unrestricted APKs cannot be retroactively locked by a future update.
