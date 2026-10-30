package com.antigravity.volumepanel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

class VolumeOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = PreferencesManager(context)
    private val handler = Handler(Looper.getMainLooper())

    private var panelView: VolumePanelView? = null
    private var isShowing = false

    private val dismissRunnable = Runnable { dismiss() }

    fun show(direction: Int = 0) {
        handler.removeCallbacks(dismissRunnable)

        if (panelView == null) {
            panelView = VolumePanelView(
                context = context,
                audioManager = audioManager,
                prefs = prefs,
                onUserInteracted = {
                    handler.removeCallbacks(dismissRunnable)
                    handler.postDelayed(dismissRunnable, AUTO_DISMISS_DELAY_MS)
                }
            )
        }

        val panel = panelView ?: return

        if (!isShowing) {
            val gravity = if (prefs.isRightSide) {
                Gravity.END or Gravity.CENTER_VERTICAL
            } else {
                Gravity.START or Gravity.CENTER_VERTICAL
            }

            val margin = (16 * context.resources.displayMetrics.density).toInt()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
                x = margin
            }

            try {
                windowManager.addView(panel, params)
                isShowing = true

                // Slide-in animation
                val translationStartX = if (prefs.isRightSide) 200f else -200f
                panel.translationX = translationStartX
                panel.alpha = 0f
                panel.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

            } catch (_: Exception) {
                // Permission not granted or already attached
            }
        }

        panel.refreshVolumeState()
        if (direction != 0) {
            panel.adjustVolumeByStep(direction)
        }

        handler.postDelayed(dismissRunnable, AUTO_DISMISS_DELAY_MS)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val panel = panelView ?: return

        if (isShowing) {
            val translationEndX = if (prefs.isRightSide) 200f else -200f
            panel.animate()
                .translationX(translationEndX)
                .alpha(0f)
                .setDuration(160)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            if (isShowing) {
                                windowManager.removeView(panel)
                                isShowing = false
                            }
                        } catch (_: Exception) {}
                        panel.animate().setListener(null)
                    }
                })
                .start()
        }
    }

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 2500L
    }
}
