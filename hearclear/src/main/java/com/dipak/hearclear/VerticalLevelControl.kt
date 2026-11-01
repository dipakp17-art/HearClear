package com.dipak.hearclear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.roundToInt

/** Compact vertical 0–100 level fader with keyboard and accessibility range actions. */
class VerticalLevelControl @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    var onLevelChanged: ((Int) -> Unit)? = null
    var level: Int = 100
        set(value) {
            val bounded = value.coerceIn(0, 100)
            if (field == bounded) return
            field = bounded
            invalidate()
            onLevelChanged?.invoke(bounded)
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
    init { isFocusable = true; isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2f
        val top = paddingTop + 16 * density
        val bottom = height - paddingBottom - 16 * density
        val y = bottom - (bottom - top) * level / 100f
        paint.strokeWidth = 8 * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = context.getColor(R.color.hc_line)
        canvas.drawLine(x, top, x, bottom, paint)
        paint.color = context.getColor(R.color.hc_accent)
        paint.alpha = if (isEnabled) 255 else 90
        canvas.drawLine(x, y, x, bottom, paint)
        canvas.drawCircle(x, y, 12 * density, paint)
        paint.alpha = 255
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val top = paddingTop + 16 * density
                val bottom = height - paddingBottom - 16 * density
                if (bottom > top) level = ((bottom - event.y) * 100 / (bottom - top)).roundToInt()
                return true
            }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled && keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            level += if (keyCode == KeyEvent.KEYCODE_DPAD_UP) 5 else -5
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 100f, level.toFloat())
        if (isEnabled) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!isEnabled) return super.performAccessibilityAction(action, arguments)
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> level += 5
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> level -= 5
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                val value = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
                level = value.roundToInt()
            }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        return true
    }
}

