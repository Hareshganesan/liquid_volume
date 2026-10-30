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
import android.view.animation.PathInterpolator

class VolumeOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = PreferencesManager(context)
    private val handler = Handler(Looper.getMainLooper())

    private var overlayView: MasterVolumeOverlayView? = null
    private var isShowing = false

    private val dismissRunnable = Runnable { dismiss() }

    // Apple iOS 18 fluid spring curves
    private val appleSpringEnter = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val appleSmoothExit = PathInterpolator(0.4f, 0f, 0.2f, 1f)

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
                onRequestExpand = {
                    expandToMaster()
                },
                onRequestCollapse = {
                    collapseToCompact()
                },
                onRequestDismiss = {
                    dismiss()
                }
            )
        }

        val overlay = overlayView ?: return

        if (!isShowing) {
            val params = createCompactLayoutParams()

            try {
                windowManager.addView(overlay, params)
                isShowing = true

                // Ultra-smooth Apple fluid entry animation
                val density = context.resources.displayMetrics.density
                val startTranslationX = if (prefs.isRightSide) 55f * density else -55f * density

                overlay.translationX = startTranslationX
                overlay.scaleX = 0.85f
                overlay.scaleY = 0.85f
                overlay.alpha = 0f

                overlay.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(260)
                    .setInterpolator(appleSpringEnter)
                    .start()

            } catch (_: Exception) {}
        }

        overlay.updateActiveStream()

        if (direction != 0) {
            overlay.handleHardwareKeyStep(direction)
        } else {
            overlay.refreshAll()
        }

        resetDismissTimer()
    }

    private fun expandToMaster() {
        val overlay = overlayView ?: return

        var windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blur = prefs.blurRadius.toInt().coerceIn(25, 75)
                setBlurBehindRadius(blur)
            }
        }

        try {
            windowManager.updateViewLayout(overlay, params)
        } catch (_: Exception) {}

        overlay.expandToMasterView()
        resetDismissTimer()
    }

    private fun collapseToCompact() {
        val overlay = overlayView ?: return
        overlay.collapseToCompactView {
            val params = createCompactLayoutParams()
            try {
                windowManager.updateViewLayout(overlay, params)
            } catch (_: Exception) {}
        }
        resetDismissTimer()
    }

    private fun createCompactLayoutParams(): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val screenHeight = context.resources.displayMetrics.heightPixels

        val gravity = if (prefs.isRightSide) {
            Gravity.END or Gravity.TOP
        } else {
            Gravity.START or Gravity.TOP
        }

        val vPercent = prefs.verticalPositionPercent.coerceIn(10f, 90f) / 100f
        val sliderHeightPx = prefs.sliderHeightDp * density
        val targetY = ((screenHeight * vPercent) - (sliderHeightPx / 2f)).toInt().coerceAtLeast(0)

        var windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = (8 * density).toInt()
            this.y = targetY

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blur = prefs.blurRadius.toInt().coerceIn(15, 60)
                setBlurBehindRadius(blur)
            }
        }
    }

    private fun resetDismissTimer() {
        handler.removeCallbacks(dismissRunnable)
        val delay = if (overlayView?.isExpanded == true) 6000L else prefs.timeoutMs
        handler.postDelayed(dismissRunnable, delay)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val overlay = overlayView ?: return

        if (isShowing) {
            if (overlay.isExpanded) {
                overlay.collapseToCompactView {
                    finishDismiss(overlay)
                }
            } else {
                val density = context.resources.displayMetrics.density
                val endTranslationX = if (prefs.isRightSide) 50f * density else -50f * density

                overlay.animate()
                    .translationX(endTranslationX)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .alpha(0f)
                    .setDuration(210)
                    .setInterpolator(appleSmoothExit)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            finishDismiss(overlay)
                            overlay.animate().setListener(null)
                        }
                    })
                    .start()
            }
        }
    }

    private fun finishDismiss(overlay: MasterVolumeOverlayView) {
        try {
            if (isShowing) {
                windowManager.removeView(overlay)
                isShowing = false
            }
        } catch (_: Exception) {}
        overlayView = null
    }
}
