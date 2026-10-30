package com.antigravity.volumepanel

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

@SuppressLint("ViewConstructor")
class LiquidGlassSliderView(
    context: Context,
    private val audioManager: AudioManager,
    private val prefs: PreferencesManager,
    private val onInteraction: () -> Unit
) : View(context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Geometry
    private val normalWidth = dpToPx(44).toFloat()
    private val expandedWidth = dpToPx(56).toFloat()
    private var currentPillWidth = normalWidth
    private val pillHeight = dpToPx(180).toFloat()

    // Paints
    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38181A22") // Translucent glass dark tint
    }

    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1).toFloat()
        color = Color.parseColor("#45FFFFFF") // Glowing frosted rim
    }

    private val liquidFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F1F5F9") // Creamy frosted white liquid
    }

    // Rectangles & Paths
    private val pillRect = RectF()
    private val fillRect = RectF()
    private val clipPath = Path()

    // Icons
    private var iconMute: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_volume_mute)
    private var iconDown: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_volume_down)
    private var iconUp: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_volume_up)

    // State
    private var currentStream = AudioManager.STREAM_MUSIC
    private var maxVolume = 1
    private var targetProgress = 0.5f
    private var animatedProgress = 0.5f
    private var progressAnimator: ValueAnimator? = null
    private var expandAnimator: ValueAnimator? = null
    private var isTouching = false

    init {
        updateAccentColor()
        refreshVolume()
    }

    fun updateAccentColor() {
        val accent = try {
            Color.parseColor(prefs.accentColorHex)
        } catch (_: Exception) {
            Color.parseColor("#F1F5F9")
        }
        liquidFillPaint.color = accent
        invalidate()
    }

    fun refreshVolume() {
        maxVolume = audioManager.getStreamMaxVolume(currentStream).coerceAtLeast(1)
        val currentVol = audioManager.getStreamVolume(currentStream)
        val newProgress = currentVol.toFloat() / maxVolume.toFloat()
        animateProgressTo(newProgress)
    }

    fun adjustVolumeByStep(direction: Int) {
        val currentVol = audioManager.getStreamVolume(currentStream)
        val newVol = (currentVol + direction).coerceIn(0, maxVolume)
        if (newVol != currentVol) {
            audioManager.setStreamVolume(currentStream, newVol, 0)
            triggerHaptic()
            val newProgress = newVol.toFloat() / maxVolume.toFloat()
            animateProgressTo(newProgress)
        }
    }

    private fun animateProgressTo(target: Float) {
        targetProgress = target.coerceIn(0f, 1f)
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(animatedProgress, targetProgress).apply {
            duration = 160
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { va ->
                animatedProgress = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animatePillWidth(target: Float) {
        expandAnimator?.cancel()
        expandAnimator = ValueAnimator.ofFloat(currentPillWidth, target).apply {
            duration = 180
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { va ->
                currentPillWidth = va.animatedValue as Float
                requestLayout()
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (expandedWidth + dpToPx(16)).toInt()
        val h = (pillHeight + dpToPx(16)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = (width - currentPillWidth) / 2f
        val top = (height - pillHeight) / 2f
        val right = left + currentPillWidth
        val bottom = top + pillHeight
        val radius = currentPillWidth / 2f

        pillRect.set(left, top, right, bottom)

        // 1. Draw Outer Glass Pill Background
        canvas.drawRoundRect(pillRect, radius, radius, glassBgPaint)

        // 2. Clip inside capsule for liquid fill
        clipPath.reset()
        clipPath.addRoundRect(pillRect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // 3. Draw Liquid Fill Rising from Bottom
        val fillHeight = pillHeight * animatedProgress
        val fillTop = bottom - fillHeight
        fillRect.set(left, fillTop, right, bottom)
        canvas.drawRect(fillRect, liquidFillPaint)

        canvas.restore()

        // 4. Draw Frosted Highlight Rim (Specular Border)
        canvas.drawRoundRect(pillRect, radius, radius, glassBorderPaint)

        // 5. Draw Dynamic Center/Bottom Icon
        drawSpeakerIcon(canvas, pillRect, fillTop)
    }

    private fun drawSpeakerIcon(canvas: Canvas, pill: RectF, fillTop: Float) {
        val icon = when {
            animatedProgress <= 0.02f -> iconMute
            animatedProgress < 0.5f -> iconDown
            else -> iconUp
        } ?: return

        val iconSize = dpToPx(24)
        val iconX = (pill.centerX() - iconSize / 2f).toInt()
        val iconY = (pill.bottom - dpToPx(28) - iconSize / 2f).toInt()

        icon.setBounds(iconX, iconY, iconX + iconSize, iconY + iconSize)

        // If the liquid covers the icon, invert icon color to dark for high contrast!
        val iconCenterY = iconY + iconSize / 2f
        val isCoveredByLiquid = fillTop < iconCenterY
        val iconTint = if (isCoveredByLiquid) {
            Color.parseColor("#18181B") // Dark charcoal icon over bright liquid
        } else {
            Color.WHITE // Crisp white icon over dark glass
        }

        DrawableCompat.setTint(icon, iconTint)
        icon.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        onInteraction()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                animatePillWidth(expandedWidth) // Expands like iOS / ColorOS 16!
                handleTouchPosition(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouchPosition(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                animatePillWidth(normalWidth) // Snaps back smoothly
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchPosition(touchY: MotionEventY) {
        val top = (height - pillHeight) / 2f
        val bottom = top + pillHeight
        val clampedY = touchY.coerceIn(top, bottom)

        val progress = 1f - ((clampedY - top) / pillHeight)
        val newVol = (progress * maxVolume).toInt().coerceIn(0, maxVolume)

        val currentVol = audioManager.getStreamVolume(currentStream)
        if (newVol != currentVol) {
            audioManager.setStreamVolume(currentStream, newVol, 0)
            triggerHaptic()
        }

        animatedProgress = progress
        invalidate()
    }

    private fun triggerHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(8)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
typealias MotionEventY = Float
