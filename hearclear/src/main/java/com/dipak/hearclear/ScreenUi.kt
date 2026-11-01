package com.dipak.hearclear

import android.app.Activity
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

/** Shared spacing, cards and touch targets for the account and information pages. */
internal class ScreenUi(private val activity: Activity, title: String) {
    val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(24))
    }
    init {
        activity.setContentView(ScrollView(activity).apply {
            setBackgroundColor(activity.getColor(R.color.hc_page))
            isFillViewport = true
            addView(content)
        })
        val bar = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(Button(activity).apply {
            text = "‹"; textSize = 28f; contentDescription = "Back"
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(activity.getColor(R.color.hc_ink))
            setOnClickListener { activity.finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(TextView(activity).apply {
            text = title; textSize = 22f; setTypeface(null, Typeface.BOLD)
            setTextColor(activity.getColor(R.color.hc_ink))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(bar)
    }
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    fun card(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.hc_card)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12); topMargin = dp(4) })
    }
    fun text(parent: LinearLayout, value: String, heading: Boolean = false): TextView = TextView(activity).apply {
        text = value; textSize = if (heading) 20f else 15f
        setTextColor(activity.getColor(if (heading) R.color.hc_ink else R.color.hc_muted))
        if (heading) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(8))
        setLineSpacing(dp(3).toFloat(), 1f)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    fun button(parent: LinearLayout, label: String, primary: Boolean = false, action: () -> Unit): Button = Button(activity).apply {
        text = label; textSize = 15f; isAllCaps = false; minHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setBackgroundResource(if (primary) R.drawable.hc_action else R.drawable.hc_soft)
        setTextColor(activity.getColor(if (primary) R.color.hc_white else R.color.hc_accent))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        setOnClickListener { action() }
    }
    fun field(parent: LinearLayout, label: String, secret: Boolean = false): EditText {
        val caption = text(parent, label)
        return EditText(activity).apply {
            id = View.generateViewId(); caption.labelFor = id
            isSingleLine = true; minHeight = dp(48); textSize = 16f
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setTextColor(activity.getColor(R.color.hc_ink))
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }
}
