package com.dipak.hearclear

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.InputType
import android.util.Base64
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

/** Compact account/payment screen. No payment callback grants access locally. */
class AccountActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var id: EditText
    private lateinit var password: EditText
    private lateinit var recovery: EditText
    private lateinit var reference: EditText
    private lateinit var consent: CheckBox
    private lateinit var qr: ImageView
    private lateinit var payment: TextView
    private lateinit var payButton: Button
    private lateinit var submit: Button
    private val buttons = mutableListOf<Button>()
    private var order = ""
    private var uri = ""
    private var canPay = false
    private var quoteTimer: CountDownTimer? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = "HearClear account"
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 24) }
        setContentView(ScrollView(this).apply { addView(column) })
        fun text(value: String, size: Float = 15f) = TextView(this).apply { text = value; textSize = size; setPadding(0, 8, 0, 8); column.addView(this) }
        fun field(hintText: String, secret: Boolean = false) = EditText(this).apply {
            hint = hintText; isSingleLine = true
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            column.addView(this)
        }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label; column.addView(this); buttons.add(this); setOnClickListener { action() }
        }
        text("One account · one active phone", 21f)
        status = text(AccountClient.summary)
        text("48 hours free. Then one payment for lifetime access and all updates. ₹50 for the first 100 buyers, then ₹100, ₹150, ₹200 and ₹250 maximum. Your checkout shows the exact price.")
        text("Accounts need internet to start and periodically while listening. Transferring replaces your old phone within 75 seconds. No extra transfer charge.")
        id = field("Account ID (4–40 characters)")
        password = field("Password (at least 12 characters)", true)
        recovery = field("Recovery code (for transfer or password reset)", true)
        consent = CheckBox(this).apply {
            text = "I accept the account data notice below."
            column.addView(this)
        }
        button("Create account · start trial") { authenticate("register") }
        button("Sign in on this phone") { authenticate("login") }
        button("Transfer account to this phone") {
            AlertDialog.Builder(this).setTitle("Replace your old phone?")
                .setMessage("Your password and saved recovery code are required. Listening on the old phone will stop within 75 seconds. The trial does not restart.")
                .setPositiveButton("Transfer") { _, _ -> authenticate("transfer") }.setNegativeButton("Cancel", null).show()
        }
        button("Reset password with recovery code") { authenticate("recover") }
        button("Refresh access / check approval") { work { AccountClient.refresh(this); AccountClient.summary } }
        button("Show payment / QR") {
            work {
                val requestedAt = SystemClock.elapsedRealtime()
                val quote = AccountClient.quote(this)
                val image = Base64.decode(quote.getString("qr_png"), Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    order = quote.getString("id"); uri = quote.getString("upi_uri")
                    qr.setImageBitmap(bitmap)
                    payment.text = "One time: ₹${quote.getInt("amount")}\nPayee: ${quote.getString("payee")}\nUPI: ${quote.getString("upi_id")}\nOrder: $order\n${if (quote.getString("state") == "pending") "Payment submitted; awaiting manual approval. Do not pay again." else "Payment window: 30 minutes from quote creation. Submit the reference before it ends."}"
                    canPay = quote.getString("state") == "quoted"
                    payButton.isEnabled = canPay
                    submit.isEnabled = canPay
                    qr.visibility = if (canPay) View.VISIBLE else View.GONE
                    quoteTimer?.cancel()
                    if (canPay) {
                        val remaining = quote.getLong("remaining_seconds") * 1000 - (SystemClock.elapsedRealtime() - requestedAt)
                        quoteTimer = object : CountDownTimer(remaining.coerceAtLeast(1), 1000) {
                            override fun onTick(left: Long) {}
                            override fun onFinish() {
                                canPay = false; payButton.isEnabled = false; submit.isEnabled = false
                                qr.visibility = View.GONE
                                payment.append("\nPayment window ended. If you already paid, contact the owner. Otherwise reopen payment details.")
                            }
                        }.start()
                    }
                }
                "Check the payee and amount before paying. A receipt alone does not unlock the app."
            }
        }
        payment = text("")
        qr = ImageView(this).apply { adjustViewBounds = true; contentDescription = "Payment UPI QR code"; visibility = View.GONE; column.addView(this, LinearLayout.LayoutParams(-1, 600)) }
        payButton = button("Open UPI payment app") {
            if (uri.isNotBlank()) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                .onFailure { status.text = "No UPI app found. Scan the QR using another phone." }
        }.apply { isEnabled = false }
        reference = field("Transaction reference from payment app")
        submit = button("Submit payment for owner verification") {
            if (order.isBlank()) status.text = "Show payment details first."
            else { val value = reference.text.toString(); work { AccountClient.receipt(this, order, value); canPay = false; "Submitted. The owner must verify settlement. Use Refresh to check approval; do not pay twice." } }
        }.apply { isEnabled = false }
        button("Sign out") {
            stopService(Intent(this, ListeningService::class.java))
            AccountClient.clearLease()
            clearPayment()
            work { AccountClient.logout(this); "Signed out." }
        }
        button("Back to listening") { finish() }
        text("Account data notice: The server stores your account ID, password hash, recovery-code hash, a hashed Android app/device identifier, trial deadline and payment reference/status. Session requests check access and replace old-phone sessions. It does not receive microphone audio, recordings, contacts or precise location. The app owner can see registered and paid account counts. Ask the owner for account help or deletion. Keep your recovery code private; losing both password and recovery code requires owner assistance.")
        if (!AccountClient.configured()) status.text = "Accounts and payments are not live yet. The owner must configure the secure server and UPI details."
        else if (AccountClient.hasSession(this)) work { AccountClient.refresh(this); AccountClient.summary }
    }
    private fun authenticate(mode: String) {
        clearPayment()
        val name = id.text.toString().trim()
        val pw = password.text.toString()
        val code = recovery.text.toString().trim()
        val accepted = consent.isChecked
        work {
            val recoveryCode = AccountClient.authenticate(this, mode, name, pw, code, accepted)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                password.text.clear(); recovery.text.clear()
                if (recoveryCode.isNotEmpty() && !isFinishing) AlertDialog.Builder(this)
                    .setTitle("Save your recovery code")
                    .setMessage("$recoveryCode\n\nKeep this private. You need it to transfer phones or reset your password. It is shown only once.")
                    .setPositiveButton("Copy code") { _, _ -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("HearClear recovery code", recoveryCode)) }
                    .setNegativeButton("I saved it", null).show()
            }
            AccountClient.summary
        }
    }
    private fun clearPayment() {
        quoteTimer?.cancel()
        order = ""; uri = ""; canPay = false
        qr.visibility = View.GONE; payment.text = ""
        payButton.isEnabled = false; submit.isEnabled = false
    }
    private fun work(action: () -> String) {
        buttons.forEach { it.isEnabled = false }
        status.text = "Please wait…"
        worker.execute {
            val message = runCatching(action).getOrElse { it.message ?: "Could not connect. Try again." }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    buttons.forEach { it.isEnabled = true }
                    if (AccountClient.paid) clearPayment()
                    payButton.isEnabled = canPay && uri.isNotBlank()
                    submit.isEnabled = canPay && order.isNotBlank()
                    status.text = message
                }
            }
        }
    }
    override fun onDestroy() { quoteTimer?.cancel(); worker.shutdown(); super.onDestroy() }
}
