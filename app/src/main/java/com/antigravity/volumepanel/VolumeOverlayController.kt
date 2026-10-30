package com.antigravity.volumepanel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

class VolumeOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = PreferencesManager(context)
    private val handler = Handler(Looper.getMainLooper())

    private var sliderView: LiquidGlassSliderView? = null
    private var isShowing = false

    private val dismissRunnable = Runnable { dismiss() }

    fun show(direction: Int = 0) {
        handler.removeCallbacks(dismissRunnable)

        if (sliderView == null) {
            sliderView = LiquidGlassSliderView(
                context = context,
                audioManager = audioManager,
                prefs = prefs,
                onInteraction = {
                    handler.removeCallbacks(dismissRunnable)
                    handler.postDelayed(dismissRunnable, AUTO_DISMISS_DELAY_MS)
                }
            )
        }

        val slider = sliderView ?: return

        if (!isShowing) {
            val gravity = if (prefs.isRightSide) {
                Gravity.END or Gravity.CENTER_VERTICAL
            } else {
                Gravity.START or Gravity.CENTER_VERTICAL
            }

            var windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

            // Enable Native Window Blur Behind on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                windowFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
                x = (8 * context.resources.displayMetrics.density).toInt()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blur = prefs.blurRadius.toInt().coerceIn(15, 60)
                    setBlurBehindRadius(blur)
                }
            }

            try {
                windowManager.addView(slider, params)
                isShowing = true

                // Premium Spring Pop-in Animation
                val startX = if (prefs.isRightSide) 120f else -120f
                slider.translationX = startX
                slider.scaleX = 0.85f
                slider.scaleY = 0.85f
                slider.alpha = 0f

                slider.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()

            } catch (_: Exception) {}
        }

        slider.updateAccentColor()
        slider.refreshVolume()

        if (direction != 0) {
            slider.adjustVolumeByStep(direction)
        }

        handler.postDelayed(dismissRunnable, AUTO_DISMISS_DELAY_MS)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val slider = sliderView ?: return

        if (isShowing) {
            val endX = if (prefs.isRightSide) 120f else -120f
            slider.animate()
                .translationX(endX)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .alpha(0f)
                .setDuration(160)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            if (isShowing) {
                                windowManager.removeView(slider)
                                isShowing = false
                            }
                        } catch (_: Exception) {}
                        slider.animate().setListener(null)
                    }
                })
                .start()
        }
    }

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 2500L
    }
}
