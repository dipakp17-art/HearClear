package com.dipak.hearclear

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

/** Sign-in and account management. Checkout has a dedicated payment page. */
class AccountActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var ui: ScreenUi
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var form: LinearLayout
    private lateinit var dashboard: LinearLayout
    private lateinit var heading: TextView
    private lateinit var hint: TextView
    private lateinit var id: EditText
    private lateinit var password: EditText
    private lateinit var recoveryGroup: LinearLayout
    private lateinit var recovery: EditText
    private lateinit var consentGroup: LinearLayout
    private lateinit var consent: CheckBox
    private lateinit var primary: Button
    private lateinit var loginTab: Button
    private lateinit var registerTab: Button
    private lateinit var pay: Button
    private lateinit var continueButton: Button
    private lateinit var recoveryButton: Button
    private val actions = mutableListOf<Button>()
    private var mode = AccountForm.Mode.LOGIN
    private var busy = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!BuildConfig.ACCOUNTS_ENABLED) {
            startActivity(Intent(this, AppInfoActivity::class.java))
            finish()
            return
        }
        ui = ScreenUi(this, "Account")
        val summary = ui.card()
        ui.text(summary, "Your listening access", true)
        status = ui.text(summary, AccountClient.summary).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        progress = ProgressBar(this).apply { visibility = View.GONE; summary.addView(this, LinearLayout.LayoutParams(ui.dp(28), ui.dp(28))) }
        dashboard = ui.card()
        continueButton = action(dashboard, "Return to listening", true) { finish() }
        pay = action(dashboard, "Payment & activation") { startActivity(Intent(this, PaymentActivity::class.java)) }
        action(dashboard, "Refresh access") { refresh() }
        recoveryButton = action(dashboard, "Save recovery code") { showRecovery() }
        action(dashboard, "Sign out") {
            AlertDialog.Builder(this).setTitle("Sign out?").setMessage("Listening will stop. Your purchase stays with your account.")
                .setNegativeButton("Cancel", null).setPositiveButton("Sign out") { _, _ ->
                    stopService(Intent(this, ListeningService::class.java)); AccountClient.clearLease()
                    work { AccountClient.logout(this) }
                }.show()
        }
        form = ui.card()
        loginTab = action(form, "Sign in") { setMode(AccountForm.Mode.LOGIN) }
        registerTab = action(form, "Create a new account") { setMode(AccountForm.Mode.REGISTER) }
        heading = ui.text(form, "", true)
        hint = ui.text(form, "")
        id = ui.field(form, "Account ID")
        password = ui.field(form, "Password · 12–128 characters", true)
        recoveryGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; form.addView(this) }
        recovery = ui.field(recoveryGroup, "Private recovery code", true)
        consentGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; form.addView(this) }
        ui.text(consentGroup, "48 hours free, then one payment. All future updates are included.")
        action(consentGroup, "Read account data notice") { startActivity(Intent(this, AppInfoActivity::class.java).putExtra("privacy", true)) }
        consent = CheckBox(this).apply { text = "I agree to the account data notice"; minHeight = ui.dp(48); consentGroup.addView(this) }
        primary = action(form, "Sign in", true) { submit() }
        action(form, "Moved to a new phone?") { setMode(AccountForm.Mode.TRANSFER) }
        action(form, "Forgot your password?") { setMode(AccountForm.Mode.RECOVER) }
        action(form, "Back to listening") { finish() }
        setMode(AccountForm.Mode.LOGIN)
    }
    private fun action(parent: LinearLayout, text: String, primary: Boolean = false, callback: () -> Unit) =
        ui.button(parent, text, primary, callback).also { actions.add(it) }
    private fun setMode(value: AccountForm.Mode) {
        mode = value
        id.error = null; password.error = null; recovery.error = null
        recovery.text.clear()
        recoveryGroup.visibility = if (value in listOf(AccountForm.Mode.TRANSFER, AccountForm.Mode.RECOVER)) View.VISIBLE else View.GONE
        consentGroup.visibility = if (value == AccountForm.Mode.REGISTER) View.VISIBLE else View.GONE
        heading.text = when (value) {
            AccountForm.Mode.REGISTER -> "Start your free trial"
            AccountForm.Mode.TRANSFER -> "Use this phone instead"
            AccountForm.Mode.RECOVER -> "Reset your password"
            else -> "Welcome back"
        }
        hint.text = when (value) {
            AccountForm.Mode.REGISTER -> "Choose an account ID and password. Save the recovery code after registration."
            AccountForm.Mode.TRANSFER -> "Enter your existing account, password and saved recovery code. Your old phone will stop within 75 seconds. Transfer is free."
            AccountForm.Mode.RECOVER -> "Enter your account ID, saved recovery code and a new password. This phone becomes your active phone."
            else -> "Use your account ID and password. New phone? Choose the transfer link below."
        }
        primary.text = when (value) {
            AccountForm.Mode.REGISTER -> "Create account · start trial"
            AccountForm.Mode.TRANSFER -> "Transfer to this phone"
            AccountForm.Mode.RECOVER -> "Save new password"
            else -> "Sign in"
        }
        loginTab.isSelected = value == AccountForm.Mode.LOGIN
        registerTab.isSelected = value == AccountForm.Mode.REGISTER
        render()
    }
    private fun render() {
        val signedIn = AccountClient.hasSession(this)
        form.visibility = if (signedIn) View.GONE else View.VISIBLE
        dashboard.visibility = if (signedIn) View.VISIBLE else View.GONE
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        actions.forEach { it.isEnabled = !busy }
        id.isEnabled = !busy; password.isEnabled = !busy; recovery.isEnabled = !busy; consent.isEnabled = !busy
        pay.visibility = if (signedIn && !AccountClient.paid) View.VISIBLE else View.GONE
        continueButton.isEnabled = !busy && AccountClient.canListen()
        recoveryButton.visibility = if (signedIn && AccountClient.pendingRecovery(this).isNotBlank()) View.VISIBLE else View.GONE
    }
    private fun submit() {
        if (busy) return
        val name = id.text.toString().trim().lowercase(Locale.ROOT)
        val pw = password.text.toString()
        val code = recovery.text.toString().trim()
        val accepted = consent.isChecked
        id.error = null; password.error = null; recovery.error = null
        when (AccountForm.validate(mode, name, pw, code, accepted)) {
            AccountForm.Error.ACCOUNT -> { id.error = "Use 4–40 letters, numbers, dots, underscores or hyphens"; id.requestFocus(); return }
            AccountForm.Error.PASSWORD -> { password.error = "Use 12–128 characters"; password.requestFocus(); return }
            AccountForm.Error.RECOVERY -> { recovery.error = "Enter the code saved when you registered"; recovery.requestFocus(); return }
            AccountForm.Error.CONSENT -> { status.text = "Read and accept the account data notice before creating an account."; return }
            else -> Unit
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(password.windowToken, 0)
        val operation = mode.name.lowercase(Locale.ROOT)
        val run = {
            work(after = { password.text.clear(); recovery.text.clear(); showRecovery() }) {
                AccountClient.authenticate(this, operation, name, pw, code, accepted)
            }
        }
        if (mode in listOf(AccountForm.Mode.TRANSFER, AccountForm.Mode.RECOVER)) {
            AlertDialog.Builder(this).setTitle("Make this your active phone?").setMessage("The previous phone loses access. Your purchase and trial deadline are preserved.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> run() }.show()
        } else run()
    }
    private fun showRecovery() {
        val code = AccountClient.pendingRecovery(this)
        if (code.isBlank() || isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setTitle("Keep your recovery code safe")
            .setMessage("$code\n\nYou need this private code to transfer phones or reset your password. You can reopen it here until you confirm it is saved.")
            .setPositiveButton("Copy") { _, _ -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("HearClear recovery code", code)) }
            .setNegativeButton("I saved it") { _, _ -> AccountClient.acknowledgeRecovery(this); render() }.show()
    }
    private fun refresh() {
        if (AccountClient.hasSession(this) && !busy) work { AccountClient.refresh(this) }
    }
    private fun work(after: () -> Unit = {}, action: () -> Unit) {
        if (busy) return
        busy = true; status.text = "Connecting…"; render()
        worker.execute {
            val failure = runCatching(action).exceptionOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                status.text = failure?.let { AccountClient.failureMessage(it) } ?: AccountClient.summary
                render()
                if (failure == null) after()
                else if (failure is AccountClient.ApiException && failure.code == "transfer_required") {
                    setMode(AccountForm.Mode.TRANSFER)
                    recovery.requestFocus()
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (!BuildConfig.ACCOUNTS_ENABLED) return
        if (AccountClient.hasSession(this)) refresh()
        else { status.text = if (AccountClient.configured()) AccountClient.summary else "Account service is not configured in this build."; render() }
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
