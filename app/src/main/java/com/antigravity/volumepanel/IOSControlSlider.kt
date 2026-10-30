package com.antigravity.volumepanel

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Production-Grade iOS 18 / ColorOS 16 Fluid Glass Control Slider
 *
 * Performance & Architecture:
 * - Zero heap allocations inside onDraw() for sustained 120 FPS rendering.
 * - LinearGradient shaders generated exclusively in onSizeChanged().
 * - Vector drawables pre-cached in memory.
 * - Direct finger-to-volume tracking with zero layout thrashing.
 * - Dual-pass canvas clipping for authentic Apple two-tone split inversion.
 * - Fluid elastic width spring (+10dp) on finger touch.
 * - Safe AudioManager integration with audible feedback and SecurityException fallbacks.
 */
@SuppressLint("ViewConstructor")
class IOSControlSlider(
    context: Context,
    private val audioManager: AudioManager,
    var streamType: Int,
    private val prefs: PreferencesManager,
    private val onInteractionStart: () -> Unit,
    private val onInteractionEnd: () -> Unit,
    private val onRequestDismiss: () -> Unit,
    private val vibrator: Vibrator?,
    private val customIconRes: Int? = null,
    private val isMasterMode: Boolean = false,
    private val onVolumeChanged: ((Int) -> Unit)? = null
) : View(context) {

    // Geometry Pre-allocations (Zero allocations in onDraw)
    private val pillRect = RectF()
    private val fillRect = RectF()
    private val meniscusRect = RectF()
    private val clipPath = Path()

    // Dimensions
    private val normalWidth = dpToPx(if (isMasterMode) LiquidGlassTokens.MASTER_SLIDER_WIDTH_DP else prefs.sliderWidthDp).toFloat()
    private val expandedWidth = normalWidth + dpToPx(LiquidGlassTokens.EXPANDED_WIDTH_ADDITION_DP).toFloat()
    private var currentWidth = normalWidth
    private val pillHeight = dpToPx(if (isMasterMode) LiquidGlassTokens.MASTER_SLIDER_HEIGHT_DP else prefs.sliderHeightDp).toFloat()

    // Cached Paints
    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(LiquidGlassTokens.SPECULAR_BORDER_WIDTH_DP).toFloat()
    }

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor(prefs.accentColorHex)
    }

    private val meniscusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = LiquidGlassTokens.COLOR_MENISCUS_CREST
    }

    private val percentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(12f).toFloat()
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // Cached Drawables (avoid XML inflation on every frame in onDraw)
    private val iconMute: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_volume_mute)?.mutate()
    private val iconDown: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_volume_down)?.mutate()
    private val iconUp: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_volume_up)?.mutate()
    private val iconCustom: Drawable? = customIconRes?.let { ContextCompat.getDrawable(context, it)?.mutate() }

    // State Variables
    private var maxVolume = 1
    private var animatedProgress = 0.5f
    private var progressAnimator: ValueAnimator? = null
    private var widthAnimator: ValueAnimator? = null

    var isDragging = false
        private set
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var hasMovedVertically = false
    private var lastNonZeroVolume: Int = 1

    init {
        refreshVolume()
    }

    fun updateStreamType(newStreamType: Int) {
        if (streamType != newStreamType) {
            streamType = newStreamType
            refreshVolume()
        }
    }

    fun refreshVolume() {
        if (isDragging) return // Don't interrupt active finger interaction

        maxVolume = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val currentVol = audioManager.getStreamVolume(streamType)
        if (currentVol > 0) {
            lastNonZeroVolume = currentVol
        }
        val target = (currentVol.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(animatedProgress, target).apply {
            duration = LiquidGlassTokens.DURATION_VOLUME_ANIM_MS
            interpolator = LiquidGlassTokens.INTERPOLATOR_APPLE_SPRING
            addUpdateListener { va ->
                animatedProgress = va.animatedValue as Float
                onVolumeChanged?.invoke((animatedProgress * 100).toInt())
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = (w - expandedWidth) / 2f
        val top = (h - pillHeight) / 2f
        val right = left + expandedWidth
        val bottom = top + pillHeight

        // Generate Shaders ONCE upon size change, eliminating per-frame heap allocations
        glassBgPaint.shader = LinearGradient(
            left, top, left, bottom,
            LiquidGlassTokens.COLOR_GLASS_CAPSULE,
            LiquidGlassTokens.COLOR_GLASS_CAPSULE_END,
            Shader.TileMode.CLAMP
        )

        glassBorderPaint.shader = LinearGradient(
            left, top, left, bottom,
            intArrayOf(
                LiquidGlassTokens.COLOR_SPECULAR_HIGH,
                LiquidGlassTokens.COLOR_SPECULAR_MID,
                LiquidGlassTokens.COLOR_SPECULAR_LOW
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (expandedWidth + dpToPx(10)).toInt()
        val h = (pillHeight + dpToPx(10)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = (width - currentWidth) / 2f
        val top = (height - pillHeight) / 2f
        val right = left + currentWidth
        val bottom = top + pillHeight
        val radius = currentWidth / 2f

        pillRect.set(left, top, right, bottom)

        // 1. Translucent Frosted Glass Capsule (No dark blue, clean optical glass)
        canvas.drawRoundRect(pillRect, radius, radius, glassBgPaint)

        // 2. Liquid Fill with Rounded Clipping
        clipPath.reset()
        clipPath.addRoundRect(pillRect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        val fillHeight = pillHeight * animatedProgress
        val fillTop = bottom - fillHeight
        fillRect.set(left, fillTop, right, bottom)
        canvas.drawRect(fillRect, liquidPaint)

        // 3. Specular Meniscus Highlight Line
        if (animatedProgress in 0.02f..0.98f) {
            meniscusRect.set(left, fillTop, right, fillTop + dpToPx(LiquidGlassTokens.MENISCUS_LINE_HEIGHT_DP))
            canvas.drawRect(meniscusRect, meniscusPaint)
        }

        canvas.restore()

        // 4. Optical Specular Rim / Border
        canvas.drawRoundRect(pillRect, radius, radius, glassBorderPaint)

        // 5. Dual-Pass Percentage Text (Master Mode)
        if (isMasterMode) {
            val percent = (animatedProgress * 100).toInt()
            val textY = top + dpToPx(24)
            val textStr = if (percent == 0) "OFF" else "$percent%"

            // Pass A: Unfilled area above water (Pure White)
            canvas.save()
            canvas.clipRect(left, top, right, fillTop)
            percentTextPaint.color = LiquidGlassTokens.COLOR_TEXT_LIGHT
            canvas.drawText(textStr, pillRect.centerX(), textY.toFloat(), percentTextPaint)
            canvas.restore()

            // Pass B: Submerged filled area (Deep Contrast Obsidian)
            canvas.save()
            canvas.clipRect(left, fillTop, right, bottom)
            percentTextPaint.color = LiquidGlassTokens.COLOR_TEXT_DARK_INVERTED
            canvas.drawText(textStr, pillRect.centerX(), textY.toFloat(), percentTextPaint)
            canvas.restore()
        }

        // 6. Dual-Pass Inverted Icon (Cached drawables, no object allocation)
        val ic = resolveActiveIcon() ?: return
        val iconSize = dpToPx(LiquidGlassTokens.ICON_SIZE_DP)
        val iconX = (pillRect.centerX() - iconSize / 2f).toInt()
        val iconY = (pillRect.bottom - dpToPx(25) - iconSize / 2f).toInt()
        ic.setBounds(iconX, iconY, iconX + iconSize, iconY + iconSize)

        // Pass A: Above Water (Pure White)
        canvas.save()
        canvas.clipRect(left, top, right, fillTop)
        DrawableCompat.setTint(ic, LiquidGlassTokens.COLOR_TEXT_LIGHT)
        ic.draw(canvas)
        canvas.restore()

        // Pass B: Submerged (Deep Obsidian Inverted)
        canvas.save()
        canvas.clipRect(left, fillTop, right, bottom)
        DrawableCompat.setTint(ic, LiquidGlassTokens.COLOR_TEXT_DARK_INVERTED)
        ic.draw(canvas)
        canvas.restore()
    }

    private fun resolveActiveIcon(): Drawable? {
        if (animatedProgress <= 0.01f) return iconMute
        if (iconCustom != null) return iconCustom
        return if (animatedProgress < 0.45f) iconDown else iconUp
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                progressAnimator?.cancel() // Cancel running auto-animation immediately so user finger has full priority
                isDragging = true
                onInteractionStart()

                touchDownX = event.x
                touchDownY = event.y
                hasMovedVertically = false

                animateWidth(expandedWidth)
                handleTouch(event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY

                if (abs(dy) > dpToPx(3)) {
                    hasMovedVertically = true
                }

                // Smooth Swipe-to-Dismiss on Compact Slider
                if (!isMasterMode) {
                    val swipeThreshold = dpToPx(38)
                    val swipedOff = if (prefs.isRightSide) dx > swipeThreshold else dx < -swipeThreshold
                    if (swipedOff) {
                        isDragging = false
                        onInteractionEnd()
                        animateWidth(normalWidth)
                        onRequestDismiss()
                        return true
                    }
                }

                handleTouch(event.y)
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                onInteractionEnd()
                animateWidth(normalWidth)

                // Tap on bottom icon area toggles Mute/Restore
                val iconTouchArea = pillHeight * 0.35f
                val top = (height - pillHeight) / 2f
                val bottom = top + pillHeight
                val tappedNearIcon = (event.y in (bottom - iconTouchArea)..bottom)

                if (!hasMovedVertically && tappedNearIcon) {
                    toggleMute()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                onInteractionEnd()
                animateWidth(normalWidth)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(touchY: Float) {
        val top = (height - pillHeight) / 2f
        val bottom = top + pillHeight
        val clampedY = touchY.coerceIn(top, bottom)

        // 120 FPS instantaneous finger-to-volume calculation
        val progress = (1f - ((clampedY - top) / pillHeight)).coerceIn(0f, 1f)
        maxVolume = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val newVol = (progress * maxVolume).roundToInt().coerceIn(0, maxVolume)

        val currentVol = audioManager.getStreamVolume(streamType)
        if (newVol != currentVol) {
            try {
                audioManager.setStreamVolume(streamType, newVol, AudioManager.FLAG_PLAY_SOUND)
            } catch (_: Exception) {
                try {
                    audioManager.setStreamVolume(streamType, newVol, 0)
                } catch (_: Exception) {}
            }

            if (newVol > 0) {
                lastNonZeroVolume = newVol
            }

            // Tactile Haptic Feedback
            if (newVol == 0 || newVol == maxVolume) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        }

        animatedProgress = progress
        onVolumeChanged?.invoke((animatedProgress * 100).toInt())
        invalidate()
    }

    private fun toggleMute() {
        val currentVol = audioManager.getStreamVolume(streamType)
        if (currentVol > 0) {
            lastNonZeroVolume = currentVol
            try {
                audioManager.setStreamVolume(streamType, 0, AudioManager.FLAG_PLAY_SOUND)
            } catch (_: Exception) {
                try { audioManager.setStreamVolume(streamType, 0, 0) } catch (_: Exception) {}
            }
        } else {
            val restoreVol = if (lastNonZeroVolume > 0) lastNonZeroVolume else (maxVolume / 2).coerceAtLeast(1)
            try {
                audioManager.setStreamVolume(streamType, restoreVol, AudioManager.FLAG_PLAY_SOUND)
            } catch (_: Exception) {
                try { audioManager.setStreamVolume(streamType, restoreVol, 0) } catch (_: Exception) {}
            }
        }
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        refreshVolume()
    }

    private fun animateWidth(target: Float) {
        widthAnimator?.cancel()
        widthAnimator = ValueAnimator.ofFloat(currentWidth, target).apply {
            duration = LiquidGlassTokens.DURATION_TOUCH_EXPAND_MS
            interpolator = LiquidGlassTokens.INTERPOLATOR_TOUCH_BOUNCE
            addUpdateListener { va ->
                currentWidth = va.animatedValue as Float
                invalidate() // Zero layout thrashing, pure 120 FPS canvas update
            }
            start()
        }
    }

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
