package com.antigravity.volumepanel

import android.graphics.Color
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/**
 * Liquid Glass Design System Tokens
 * Centralized design tokens for surface opacity, blur intensity, corner radii,
 * dimensions, typography, and fluid animation curves.
 */
object LiquidGlassTokens {

    // --- Color & Translucency Tokens ---
    // Pure optical glass: Translucent neutral tints allowing background blur to shine through without color cast
    const val COLOR_GLASS_BASE = 0x2A141724.toInt() // 16% neutral translucent dark glass
    const val COLOR_GLASS_CAPSULE = 0x26FFFFFF.toInt() // 15% translucent white frosted capsule
    const val COLOR_GLASS_CAPSULE_END = 0x12FFFFFF.toInt() // 7% translucent bottom fade

    // Specular Rim / Optical Highlights
    const val COLOR_SPECULAR_HIGH = 0x8AFFFFFF.toInt() // Crisp top reflection
    const val COLOR_SPECULAR_MID = 0x38FFFFFF.toInt()
    const val COLOR_SPECULAR_LOW = 0x18FFFFFF.toInt() // Soft bottom reflection

    // Meniscus Wave Highlight
    const val COLOR_MENISCUS_CREST = 0xA8FFFFFF.toInt() // Luminous liquid wave crest

    // Text & Contrast
    const val COLOR_TEXT_LIGHT = 0xFFF8FAFC.toInt() // Crisp white
    const val COLOR_TEXT_MUTED = 0xFF94A3B8.toInt() // Slate 400
    const val COLOR_TEXT_DARK_INVERTED = 0xFF090D16.toInt() // Deep obsidian contrast for inverted liquid text/icons

    // Mode Highlights
    const val COLOR_MODE_SILENT = 0xFFEF4444.toInt() // Coral Red
    const val COLOR_MODE_VIBRATE = 0xFFF59E0B.toInt() // Warm Amber
    const val COLOR_MODE_RING = 0xFF10B981.toInt() // Emerald Green

    // Backdrop Scrim
    const val COLOR_BACKDROP_SCRIM = 0x48000000.toInt() // 28% neutral dim letting GPU blur glow through

    // --- Dimensions & Geometry (in dp) ---
    const val DEFAULT_SLIDER_WIDTH_DP = 46
    const val DEFAULT_SLIDER_HEIGHT_DP = 180
    const val MASTER_SLIDER_WIDTH_DP = 54
    const val MASTER_SLIDER_HEIGHT_DP = 165
    const val EXPANDED_WIDTH_ADDITION_DP = 10 // Liquid expansion on finger touch
    const val CORNER_RADIUS_MASTER_CARD_DP = 36f
    const val SPECULAR_BORDER_WIDTH_DP = 1.25f
    const val MENISCUS_LINE_HEIGHT_DP = 2.5f
    const val ICON_SIZE_DP = 23

    // --- Blur Tokens ---
    const val DEFAULT_BLUR_RADIUS_PX = 40f
    const val MIN_BLUR_RADIUS_PX = 15f
    const val MAX_BLUR_RADIUS_PX = 80f

    // --- Motion & Physics Tokens ---
    const val DURATION_COMPACT_ENTER_MS = 250L
    const val DURATION_COMPACT_EXIT_MS = 200L
    const val DURATION_MASTER_ENTER_MS = 280L
    const val DURATION_MASTER_COLLAPSE_MS = 200L
    const val DURATION_TOUCH_EXPAND_MS = 150L
    const val DURATION_VOLUME_ANIM_MS = 140L

    // Apple fluid spring curves
    val INTERPOLATOR_APPLE_SPRING: Interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    val INTERPOLATOR_SMOOTH_EXIT: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    val INTERPOLATOR_TOUCH_BOUNCE: Interpolator = OvershootInterpolator(1.4f)
    val INTERPOLATOR_DECELERATE: Interpolator = DecelerateInterpolator(1.8f)
}
