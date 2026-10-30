package com.antigravity.volumepanel

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat

@SuppressLint("ViewConstructor")
class VolumePanelView(
    context: Context,
    private val audioManager: AudioManager,
    private val prefs: PreferencesManager,
    private val onUserInteracted: () -> Unit
) : FrameLayout(context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val containerWidth = dpToPx(56)
    private val containerHeight = dpToPx(190)
    private val cornerRadius = dpToPx(28).toFloat()

    private val sliderTrack = FrameLayout(context)
    private val sliderFill = View(context)
    private val iconView = ImageView(context)

    private var currentStream = AudioManager.STREAM_MUSIC
    private var maxVolume = 1
    private var currentVolume = 0

    init {
        setupLayout()
        applyStyling()
        refreshVolumeState()
    }

    private fun setupLayout() {
        val rootParams = LayoutParams(containerWidth, containerHeight).apply {
            gravity = Gravity.CENTER
        }
        layoutParams = rootParams

        // Slider track (contains the fill bar)
        val trackParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        sliderTrack.layoutParams = trackParams
        sliderTrack.clipToOutline = true

        // Slider fill (grows from bottom to top)
        val fillParams = LayoutParams(LayoutParams.MATCH_PARENT, 0).apply {
            gravity = Gravity.BOTTOM
        }
        sliderFill.layoutParams = fillParams

        // Icon at bottom
        val iconSize = dpToPx(26)
        val iconParams = LayoutParams(iconSize, iconSize).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(16)
        }
        iconView.layoutParams = iconParams
        iconView.setColorFilter(Color.WHITE)

        sliderTrack.addView(sliderFill)
        addView(sliderTrack)
        addView(iconView)
    }

    fun applyStyling() {
        val accentColor = try {
            Color.parseColor(prefs.accentColorHex)
        } catch (_: Exception) {
            Color.parseColor("#6366F1")
        }

        // Background Glass Shape
        val glassBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = FloatArray(8) { cornerRadius }
            setColor(Color.parseColor("#451E293B")) // Frosted glass tint
            setStroke(dpToPx(1), Color.parseColor("#30FFFFFF")) // Subtle highlight border
        }
        background = glassBg
        clipToOutline = true

        // Apply Native Hardware Gaussian Blur (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = prefs.blurRadius.coerceIn(10f, 60f)
            setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
        }

        // Slider Fill Shape
        val fillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = FloatArray(8) { cornerRadius }
            setColor(accentColor)
        }
        sliderFill.background = fillBg
    }

    fun refreshVolumeState() {
        maxVolume = audioManager.getStreamMaxVolume(currentStream).coerceAtLeast(1)
        currentVolume = audioManager.getStreamVolume(currentStream)
        updateSliderHeight(animate = true)
        updateIcon()
    }

    fun adjustVolumeByStep(direction: Int) {
        val newVol = (currentVolume + direction).coerceIn(0, maxVolume)
        if (newVol != currentVolume) {
            currentVolume = newVol
            audioManager.setStreamVolume(currentStream, currentVolume, 0)
            updateSliderHeight(animate = true)
            updateIcon()
            triggerHaptic()
        }
    }

    private fun updateSliderHeight(animate: Boolean) {
        val targetHeight = ((currentVolume.toFloat() / maxVolume.toFloat()) * containerHeight).toInt()

        if (animate) {
            val anim = ValueAnimator.ofInt(sliderFill.height, targetHeight).apply {
                duration = 140
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val lp = sliderFill.layoutParams
                    lp.height = va.animatedValue as Int
                    sliderFill.layoutParams = lp
                }
            }
            anim.start()
        } else {
            val lp = sliderFill.layoutParams
            lp.height = targetHeight
            sliderFill.layoutParams = lp
        }
    }

    private fun updateIcon() {
        when {
            currentVolume == 0 -> {
                iconView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_volume_mute))
            }
            currentVolume < (maxVolume / 2) -> {
                iconView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_volume_down))
            }
            else -> {
                iconView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_volume_up))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        onUserInteracted()
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val touchY = event.y.coerceIn(0f, containerHeight.toFloat())
                val progress = 1f - (touchY / containerHeight.toFloat())
                val newVol = (progress * maxVolume).toInt().coerceIn(0, maxVolume)

                if (newVol != currentVolume) {
                    currentVolume = newVol
                    audioManager.setStreamVolume(currentStream, currentVolume, 0)
                    updateSliderHeight(animate = false)
                    updateIcon()
                    triggerHaptic()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun triggerHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(10)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
