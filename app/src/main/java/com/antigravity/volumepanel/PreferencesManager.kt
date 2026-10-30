package com.antigravity.volumepanel

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("liquid_volume_prefs", Context.MODE_PRIVATE)

    var isRightSide: Boolean
        get() = prefs.getBoolean(KEY_IS_RIGHT_SIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_RIGHT_SIDE, value).apply()

    var blurRadius: Float
        get() = prefs.getFloat(KEY_BLUR_RADIUS, 30f)
        set(value) = prefs.edit().putFloat(KEY_BLUR_RADIUS, value).apply()

    var accentColorHex: String
        get() = prefs.getString(KEY_ACCENT_COLOR, "#6366F1") ?: "#6366F1"
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()

    companion object {
        private const val KEY_IS_RIGHT_SIDE = "is_right_side"
        private const val KEY_BLUR_RADIUS = "blur_radius"
        private const val KEY_ACCENT_COLOR = "accent_color"
    }
}
