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

    private var overlayView: MasterVolumeOverlayView? = null
    private var isShowing = false

    private val dismissRunnable = Runnable { dismiss() }

    fun show(direction: Int = 0) {
        handler.removeCallbacks(dismissRunnable)

        if (overlayView == null) {
            overlayView = MasterVolumeOverlayView(
                context = context,
                audioManager = audioManager,
                prefs = prefs,
                onInteraction = {
                    resetDismissTimer()
                },
                onRequestDismiss = {
                    dismiss()
                }
            )
        }

        val overlay = overlayView ?: return

        if (!isShowing) {
            val gravity = if (prefs.isRightSide) {
                Gravity.END or Gravity.CENTER_VERTICAL
            } else {
                Gravity.START or Gravity.CENTER_VERTICAL
            }

            var windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

            // Native Window Blur Behind on Android 12+
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
                x = (10 * context.resources.displayMetrics.density).toInt()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blur = prefs.blurRadius.toInt().coerceIn(20, 60)
                    setBlurBehindRadius(blur)
                }
            }

            try {
                windowManager.addView(overlay, params)
                isShowing = true

                // Enter animation
                val startX = if (prefs.isRightSide) 120f else -120f
                overlay.translationX = startX
                overlay.alpha = 0f

                overlay.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()

            } catch (_: Exception) {}
        }

        if (direction != 0) {
            overlay.handleHardwareVolumeStep(direction)
        } else {
            overlay.refreshAllSliders()
        }

        resetDismissTimer()
    }

    private fun resetDismissTimer() {
        handler.removeCallbacks(dismissRunnable)
        // If user is inside Master mode, give them 5 seconds to adjust streams comfortably
        val delay = if (overlayView?.isExpanded == true) 5000L else AUTO_DISMISS_DELAY_MS
        handler.postDelayed(dismissRunnable, delay)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val overlay = overlayView ?: return

        if (isShowing) {
            val endX = if (prefs.isRightSide) 120f else -120f
            overlay.animate()
                .translationX(endX)
                .alpha(0f)
                .setDuration(160)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            if (isShowing) {
                                windowManager.removeView(overlay)
                                isShowing = false
                            }
                        } catch (_: Exception) {}
                        overlay.animate().setListener(null)
                    }
                })
                .start()
        }
    }

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 2500L
    }
}
