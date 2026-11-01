package com.dipak.hearclear

import android.content.Context

class ListeningSettings(context: Context) {
    private val p = context.getSharedPreferences("hearclear", Context.MODE_PRIVATE)
    fun value(key: String, fallback: Int = 0) = p.getInt(key, fallback)
    fun put(key: String, value: Int) { p.edit().putInt(key, value).apply() }
    var gain: Int
        get() = value("gain", 1).coerceIn(1, 20)
        set(v) = put("gain", v.coerceIn(1, 20))
    var software: Boolean
        get() = value("software") == 1
        set(v) = put("software", if (v) 1 else 0)
    var androidNs: Boolean
        get() = value("androidNs") == 1
        set(v) = put("androidNs", if (v) 1 else 0)
    fun reset() { p.edit().clear().apply() }
}

