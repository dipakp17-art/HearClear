package com.dipak.hearclear

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Base64
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.util.concurrent.Executors

/** A server quote and manual verification flow; no local payment callback unlocks access. */
class PaymentActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var timing: TextView
    private lateinit var progress: ProgressBar
    private lateinit var qr: ImageView
    private lateinit var referenceGroup: LinearLayout
    private lateinit var reference: EditText
    private lateinit var pay: Button
    private lateinit var submit: Button
    private lateinit var refresh: Button
    private lateinit var retry: Button
    private lateinit var checkout: LinearLayout
    private var order = ""
    private var uri = ""
    private var pending = false
    private var busy = false
    private var deadline = 0L
    private var timer: CountDownTimer? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val ui = ScreenUi(this, "Payment & activation")
        val summary = ui.card()
        ui.text(summary, "One payment. Lifetime access.", true)
        status = ui.text(summary, "Checking your account…").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        ui.text(summary, "Approval hours: 10 AM–5 PM India time, every day. Later submissions wait until the next window. Updates and phone transfers are free.")
        progress = ProgressBar(this).apply { visibility = View.GONE; summary.addView(this, LinearLayout.LayoutParams(ui.dp(28), ui.dp(28))) }
        checkout = ui.card()
        details = ui.text(checkout, "")
        qr = ImageView(this).apply {
            contentDescription = "UPI payment QR code"; adjustViewBounds = true
            checkout.addView(this, LinearLayout.LayoutParams(-1, ui.dp(240)))
        }
        timing = ui.text(checkout, "")
        pay = ui.button(checkout, "Pay using a UPI app", true) {
            if (canPay()) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                .onFailure { status.text = "No UPI app could open this payment. Scan the QR with another phone and check the recipient." }
        }
        referenceGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; checkout.addView(this) }
        reference = ui.field(referenceGroup, "Transaction reference from your payment app")
        submit = ui.button(referenceGroup, "Submit for verification", true) {
            val value = reference.text.toString().trim()
            if (!AccountForm.validReference(value)) {
                reference.error = "Enter 8–40 letters or numbers from the transaction reference"
                reference.requestFocus()
            } else if (canPay()) work {
                AccountClient.receipt(this, order, value)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    pending = true; timer?.cancel(); qr.visibility = View.GONE
                    status.text = "Payment submitted. The owner must verify settlement. Do not pay again."
                    timing.text = "Awaiting approval during 10 AM–5 PM India time. Tap Check approval to refresh."
                }
            }
        }
        val actions = ui.card()
        refresh = ui.button(actions, "Check approval / refresh access", true) { load() }
        retry = ui.button(actions, "Reload payment details") { load() }
        ui.button(actions, "Back to account") { finish() }
        render()
    }
    private fun canPay() = !busy && AccountClient.hasSession(this) && !AccountClient.paid && !pending && order.isNotBlank() && SystemClock.elapsedRealtime() < deadline
    private fun render() {
        val signedIn = AccountClient.hasSession(this)
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        checkout.visibility = if (signedIn && !AccountClient.paid && order.isNotBlank()) View.VISIBLE else View.GONE
        pay.visibility = if (pending) View.GONE else View.VISIBLE
        referenceGroup.visibility = if (pending) View.GONE else View.VISIBLE
        submit.visibility = if (pending) View.GONE else View.VISIBLE
        pay.isEnabled = canPay(); submit.isEnabled = canPay(); reference.isEnabled = canPay()
        refresh.isEnabled = !busy && signedIn
        retry.isEnabled = !busy && signedIn && !AccountClient.paid
    }
    private fun bindQuote(quote: JSONObject, requestedAt: Long) {
        if (isFinishing || isDestroyed) return
        val nextOrder = quote.getString("id")
        if (nextOrder != order) reference.text.clear()
        order = nextOrder; uri = quote.getString("upi_uri")
        pending = quote.getString("state") == "pending"
        details.text = "₹${quote.getInt("amount")} · one time\n${quote.getString("payee")}\n${quote.getString("upi_id")}\nOrder: $order"
        val image = Base64.decode(quote.getString("qr_png"), Base64.DEFAULT)
        qr.setImageBitmap(BitmapFactory.decodeByteArray(image, 0, image.size))
        qr.visibility = if (pending) View.GONE else View.VISIBLE
        status.text = if (pending) "Payment submitted. Awaiting owner approval; do not pay again." else "Check the recipient and exact amount before paying."
        timer?.cancel()
        deadline = requestedAt + quote.getLong("remaining_seconds") * 1000
        if (pending) timing.text = "Verification: 10 AM–5 PM India time. Tap Check approval after the owner approves."
        else timer = object : CountDownTimer((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1), 1000) {
            override fun onTick(left: Long) { timing.text = "Payment window: ${(left + 59999) / 60000} minutes left. Submit your reference before it ends." }
            override fun onFinish() {
                deadline = 0L; qr.visibility = View.GONE
                timing.text = "Payment window ended. If you paid already, contact the owner. Otherwise reload payment details."
                render()
            }
        }.start()
        render()
    }
    private fun load() {
        if (!AccountClient.hasSession(this)) {
            startActivity(Intent(this, AccountActivity::class.java)); finish(); return
        }
        work {
            AccountClient.refresh(this)
            if (AccountClient.paid) runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    timer?.cancel(); order = ""; qr.visibility = View.GONE
                    status.text = "Lifetime access is active. All future updates are included. No more payment is needed."
                }
            } else {
                val requested = SystemClock.elapsedRealtime()
                val quote = AccountClient.quote(this)
                runOnUiThread { bindQuote(quote, requested) }
            }
        }
    }
    private fun work(action: () -> Unit) {
        if (busy) return
        busy = true; render()
        worker.execute {
            val failure = runCatching(action).exceptionOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                if (failure != null) {
                    status.text = AccountClient.failureMessage(failure)
                    if (!AccountClient.hasSession(this)) status.append("\nReturn to Account and sign in again.")
                }
                render()
            }
        }
    }
    override fun onResume() { super.onResume(); load() }
    override fun onDestroy() { timer?.cancel(); worker.shutdown(); super.onDestroy() }
}
