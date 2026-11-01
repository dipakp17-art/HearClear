# Accounts, trials and one-time access

This branch is an implementation for testing, not a live payment service. The APK intentionally cannot start listening until an HTTPS account server is configured. Do not publish this build as a working paid service before deployment, owner payment details and two-phone tests are complete. Existing public v1.1 APKs remain unrestricted; this change cannot retroactively lock already distributed builds.

## Product rules

- One 48-hour trial from server registration time; signing in or transferring never renews it.
- One lifetime purchase per account. Updates, including major updates, and phone transfers are free.
- Pricing: purchase slots 1–100 ₹50; 101–200 ₹100; 201–300 ₹150; 301–400 ₹200; 401+ ₹250. No price above ₹250. Downloads and account registrations do not increase the price.
- Checkout reserves the lowest available purchase slot for 30 minutes and displays its fixed price. Concurrent checkouts reserve distinct slots to prevent overselling the cheaper tier. Unpaid expired quotes release their slot. Submitted references hold their slot until approval/rejection. Thus a higher quote may appear while cheaper slots are temporarily reserved. Only approved orders become purchases; pending orders need timely operator review to avoid holding cheaper slots indefinitely.
- Opening a checkout again returns the same pending order; never ask a pending buyer to pay again. Late/unmatched payments need owner assistance. The app does not automatically refund money.
- Account IDs are not secrets. Use a 12+ character password. Registration returns a private recovery code once; store it safely. Phone transfer requires both password and recovery code. Password reset requires the recovery code and a new password.
- One active device/session per account. Successful login replaces old sessions. A different phone requires explicit transfer. A transfer preserves the purchase and revokes the old server session immediately.
- The app renews a 75-second in-memory lease every 30 seconds while listening. An old phone stops within 75 seconds, even if it disconnects. Network failure does not extend access. Internet is required at startup and periodically; this version does **not** offer long-term offline operation.
- A server rejection clears access immediately. App restart, reboot and wall-clock changes cannot extend the monotonic in-memory lease. The audio loop and foreground service both check it.

## Limits to account/device enforcement

The server stores a SHA-256 hash of the Android app-scoped ANDROID_ID to bind a session and remember the device's original trial deadline. This normally survives reinstall with the same signing identity and Android user. It does not prove a human identity or stop factory resets, different Android users, rooted/spoofed devices, or patched APKs. Public source and earlier unrestricted APKs cannot be made tamper-proof by this client gate. No phone-number/SMS verification, Play Integrity or hardware attestation is included. Before a broad paid launch, add verified account recovery/support and evaluate these abuse controls. Do not promise “one human only.”

Sessions and recovery codes are stored hashed server-side. Passwords use salted PBKDF2-HMAC-SHA256 (600,000 iterations). The APK encrypts its session token with an Android Keystore AES-GCM key; no password is saved. Keys are not claimed to be hardware-backed on every phone. Account backups are disabled. There are no microphone uploads or listening analytics. The owner can report registered accounts, paid accounts and pending references.

## Deploy the backend (owner/operator)

Requires Python 3.11+, a persistent disk, an HTTPS domain and a trusted reverse proxy. No paid hosting or payment account has been created by this change. Use a dedicated service user and a virtual environment. Keep all payment details/secrets out of Git.

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r backend/requirements.txt
python -m unittest discover -s backend -v
export HEARCLEAR_DB=/var/lib/hearclear/hearclear.sqlite3
source backend/payment.env.example
export HEARCLEAR_PAYEE='YOUR_RECEIVING_NAME'
python backend/server.py serve
```

The example config contains the owner's supplied receiving UPI ID. Replace `YOUR_RECEIVING_NAME` with the exact recipient name shown by the owner's UPI app. The name is intentionally unset in the example, so checkout stays disabled until both fields are configured. This does not verify ownership or settlement. The server listens only on `127.0.0.1:8787`. Run it with a service manager for restart on reboot. Put an HTTPS reverse proxy in front; never expose this HTTP listener directly. The proxy must **overwrite** `X-Real-IP` with the connecting client's IP, cap request size at 8 KB, enforce connection/request rate limits and timeouts, and avoid request-body/Authorization logging. The in-process limiter adds per-IP and per-account throttling, but is not a substitute for proxy protection. Use a single service process for its limiter; SQLite serializes payment/transfer writes. Test expected load before public release.

Example nginx location inside an already configured TLS server (replace domain/certificate configuration separately):

```nginx
location /v1/ {
    client_max_body_size 8k;
    proxy_pass http://127.0.0.1:8787;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_read_timeout 15s;
    proxy_connect_timeout 5s;
}
```

The receiving UPI identity goes only into the displayed quote and its generated QR. Launching a UPI app, its callback, a transaction reference or screenshot never grants access. Automatic bank/provider payment verification is not implemented.

### Verify settlement and grant access

Run these on the protected server console, not in the Android app or public GitHub Actions logs:

```bash
python backend/server.py report
```

Inspect the pending order. Independently check your bank/payment account for the **settled amount, transaction reference, payee and order context**. Resolve ambiguities or reused/third-party references before approval. Then:

```bash
python backend/server.py approve --order ORDER_ID --amount 50 --reference BANK_TRANSACTION_REFERENCE --settlement-checked
```

Use the actual quoted amount, not always 50. Incorrect amount/reference is rejected. Repeating an approval is idempotent. The app unlocks on its next status refresh. To reject a reference after checking it:

```bash
python backend/server.py reject --order ORDER_ID
```

Do not reject a real settled payment without arranging support/refund. The command releases its slot but does not issue a refund. No public admin API or built-in owner dashboard is exposed. Console/database access is the admin boundary. Protect regular SQLite backups (use SQLite backup API or stop the service while copying). Losing this database loses purchases/trial history. Never commit database files, logs, recovery codes or references to the public repo. The audit table records registrations, transfers and payment actions; it does not store audio. Decide and publish owner contact, account deletion, retention and recovery procedures before accepting customers.

## Configure and build Android (PowerShell)

From a checkout of **HearClear**, not SoundMonitor:

```powershell
git fetch origin
git switch feature/accounts-trial-payments
.\gradlew.bat :hearclear:assembleDebug :hearclear:testDebugUnitTest '-PhearclearApiUrl=https://YOUR_ACCOUNT_SERVER' --console=plain
```

Replace the example URL with your deployed HTTPS base URL. Alternatively put `hearclearApiUrl=https://YOUR_ACCOUNT_SERVER` in the local user Gradle properties file. No secret API/admin key belongs in the APK. Without a URL the app builds and shows a setup-required message; listening stays locked. Keep the original application ID and signing key for upgrades. For wider distribution use a stable owner-controlled release key, not CI's disposable debug key. This branch does not publish APKs, change repo visibility, create a release, or alter existing purchases.

## Validation

CI runs backend auth/trial/payment/transfer/concurrency/QR tests, Android build/unit tests (including monotonic lease expiry), and existing RNNoise/JNI/signal tests. No bank transaction or real deployment has been performed. Device tests still required:

1. Register on phone A; securely save the one-time recovery code; verify trial and server deadline.
2. Verify expired-trial lock and rejection of unapproved/incorrect payments using a test database. Do not alter a real buyer's deadline for testing.
3. Check the QR/UPI recipient and price, submit a real test settlement only with the owner's authorization, and approve it once. Check lifetime entitlement.
4. On phone B, ordinary login must request transfer. Wrong code must fail. Valid transfer must preserve purchase; A must stop within 75 seconds, including when A is offline.
5. Test relaunch/reboot/reinstall, password recovery, simultaneous transfers, network loss, and old-app update with the same signing key. A second account on the same device must not get a fresh trial.
6. Test checkout expiry, rejected/duplicate/late references, tier boundaries, pending display, owner support, accessibility and narrow screens. Listen for no audio regressions.

Source references: Android Keystore documentation https://developer.android.com/privacy-and-security/keystore ; QR generator https://pypi.org/project/qrcode/ .
