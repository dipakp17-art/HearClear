package com.dipak.hearclear

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class AppInfoActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val licencesOnly = intent.getBooleanExtra("licences", false)
        val privacyOnly = intent.getBooleanExtra("privacy", false)
        val ui = ScreenUi(this, if (licencesOnly) "Open-source licences" else if (privacyOnly) "Account data notice" else "App information")
        fun section(title: String, body: String) {
            val card = ui.card(); ui.text(card, title, true); ui.text(card, body)
        }
        if (licencesOnly) {
            section("RNNoise · BSD licence", resources.openRawResource(R.raw.rnnoise_license).bufferedReader().use { it.readText() })
            section("AndroidX · Apache License 2.0", resources.openRawResource(R.raw.androidx_license).bufferedReader().use { it.readText() })
            return
        }
        if (!BuildConfig.ACCOUNTS_ENABLED) {
            if (!privacyOnly) {
                section("HearClear ${BuildConfig.VERSION_NAME}", "Free live-listening test version, with separate ear controls and optional noise reduction.")
                section("Free testing", "No sign-in, payment or 48-hour limit. Anyone can use and share this test version. Account and payment features are paused.")
                section("Getting started", "Connect headphones, allow microphone access, then start at 1× boost. Place the phone near the person speaking and try one noise filter at a time.")
                section("Offline use & sharing", "No account server or internet connection is needed for listening. Your laptop can be switched off. Menu → Share app sends this installed APK so other people can test it too. Updates are installed manually.")
                section("Listening safety", "Experimental assistance, not a medically fitted hearing aid. Output is not calibrated to ear-level loudness. Start low, raise slowly and stop if uncomfortable. Noise reduction will not remove every background sound.")
            }
            section("Privacy", "Sound processing stays on your phone. This version makes no account or payment requests and does not upload audio. Settings are stored locally. Account data from an earlier installed version is not used to restrict listening.")
            ui.button(ui.card(), "Open-source licences") {
                startActivity(Intent(this, AppInfoActivity::class.java).putExtra("licences", true))
            }
            return
        }
        if (!privacyOnly) {
            section("HearClear ${BuildConfig.VERSION_NAME}", "Live listening from your phone microphone to headphones, with separate left and right levels and optional noise reduction.")
            section("Getting started", "Connect headphones, sign in or create an account, then start at 1× boost. Place the phone near the person speaking. Try one noise filter at a time.")
            section("Access & payments", "Each account gets a 48-hour trial. One payment unlocks lifetime access and all future updates. Prices rise by ₹50 per 100 purchase slots, from ₹50 to a maximum of ₹250. Checkout shows the exact reserved price.\n\nOne account can use one active phone. Transfers are free and replace the previous phone within 75 seconds. Keep your recovery code private.\n\nPayments are checked manually, every day from 10 AM to 5 PM India time. Submissions outside those hours wait for the next window. A payment screenshot or reference alone does not unlock access.")
            section("Connection", "The app needs internet to verify access periodically. The laptop server and tunnel must stay running. Microphone processing stays on the phone.\n\nShare app sends your installed test APK. Updates are installed manually. A temporary tunnel address may change when restarted, so these builds are for testing.")
            section("Listening safety", "Experimental assistance, not a medically fitted hearing aid. Sound output is not calibrated to ear-level loudness. Start low, raise slowly, and stop if uncomfortable. Noise reduction may change speech and will not remove every fan or background sound.")
        }
        section("Account data", "The account server stores your account ID, password hash, recovery-code hash, a hashed Android app/device identifier, trial deadline and payment reference/status. Access checks enforce trial timing and replace old-phone sessions. The owner can see registered and paid account counts.\n\nIt does not receive microphone audio, recordings, contacts or precise location. Keep your password and recovery code private. The app temporarily keeps a recovery code encrypted on this phone until you confirm it is saved. Ask the app owner for account help, deletion or recovery support.")
        if (!privacyOnly) ui.button(ui.card(), "Open-source licences") {
            startActivity(Intent(this, AppInfoActivity::class.java).putExtra("licences", true))
        }
    }
}
