package com.antigravity.volumepanel

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("liquid_volume_prefs", Context.MODE_PRIVATE)

    var isRightSide: Boolean
        get() = prefs.getBoolean(KEY_IS_RIGHT_SIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_RIGHT_SIDE, value).apply()

    // Vertical alignment percentage (0% = top, 50% = center, 100% = bottom)
    var verticalPositionPercent: Float
        get() = prefs.getFloat(KEY_VERTICAL_POS, 42f) // Defaults near physical volume rocker
        set(value) = prefs.edit().putFloat(KEY_VERTICAL_POS, value).apply()

    // Slider Dimensions
    var sliderHeightDp: Int
        get() = prefs.getInt(KEY_SLIDER_HEIGHT, 180)
        set(value) = prefs.edit().putInt(KEY_SLIDER_HEIGHT, value).apply()

    var sliderWidthDp: Int
        get() = prefs.getInt(KEY_SLIDER_WIDTH, 46)
        set(value) = prefs.edit().putInt(KEY_SLIDER_WIDTH, value).apply()

    // Timeout (in milliseconds)
    var timeoutMs: Long
        get() = prefs.getLong(KEY_TIMEOUT_MS, 2500L)
        set(value) = prefs.edit().putLong(KEY_TIMEOUT_MS, value).apply()

    // Blur & Styling
    var blurRadius: Float
        get() = prefs.getFloat(KEY_BLUR_RADIUS, 40f)
        set(value) = prefs.edit().putFloat(KEY_BLUR_RADIUS, value).apply()

    var accentColorHex: String
        get() = prefs.getString(KEY_ACCENT_COLOR, "#F8FAFC") ?: "#F8FAFC"
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    companion object {
        private const val KEY_IS_RIGHT_SIDE = "is_right_side"
        private const val KEY_VERTICAL_POS = "vertical_pos"
        private const val KEY_SLIDER_HEIGHT = "slider_height"
        private const val KEY_SLIDER_WIDTH = "slider_width"
        private const val KEY_TIMEOUT_MS = "timeout_ms"
        private const val KEY_BLUR_RADIUS = "blur_radius"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_HAPTIC = "haptic_enabled"
    }
}
