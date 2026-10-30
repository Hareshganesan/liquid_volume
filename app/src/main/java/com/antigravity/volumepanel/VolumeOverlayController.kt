package com.antigravity.volumepanel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager

/**
 * Production-Quality WindowManager & Lifecycle Orchestrator
 *
 * Responsibilities:
 * - Formal OverlayState machine to prevent transition collisions and stale views.
 * - Dynamic WindowManager layout re-targeting (Compact Edge ➔ Master Center Modal).
 * - System Volume BroadcastReceiver (android.media.VOLUME_CHANGED_ACTION) for real-time external synchronization.
 * - Focus arbitration (Pass-through focus in Compact; Back-button capture in Master).
 * - True hardware blur support (FLAG_BLUR_BEHIND) with graceful fallback.
 * - Apple fluid spring animations for entrance, collapse, and dismissal.
 */
class VolumeOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = PreferencesManager(context)
    private val handler = Handler(Looper.getMainLooper())

    private var overlayView: MasterVolumeOverlayView? = null
    var currentState: OverlayState = OverlayState.Hidden
        private set

    private val dismissRunnable = Runnable { dismiss() }

    // External System Volume Broadcast Receiver
    private var isReceiverRegistered = false
    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                val streamValue = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                if (streamType != -1 && streamValue != -1) {
                    overlayView?.onExternalVolumeChange(streamType, streamValue)
                }
            }
        }
    }

    fun show(direction: Int = 0) {
        handler.removeCallbacks(dismissRunnable)

        if (overlayView == null) {
            overlayView = MasterVolumeOverlayView(
                context = context,
                audioManager = audioManager,
                prefs = prefs,
                onInteractionStart = {
                    transitionTo(if (overlayView?.isExpanded == true) OverlayState.MasterInteracting else OverlayState.CompactInteracting)
                    handler.removeCallbacks(dismissRunnable)
                },
                onInteractionEnd = {
                    transitionTo(if (overlayView?.isExpanded == true) OverlayState.MasterVisible else OverlayState.CompactVisible)
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

        if (!currentState.isVisible) {
            registerVolumeReceiver()
            val params = createCompactLayoutParams()

            try {
                windowManager.addView(overlay, params)
                transitionTo(OverlayState.CompactEntering)

                // Apple Fluid Spring Entrance
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
                    .setDuration(LiquidGlassTokens.DURATION_COMPACT_ENTER_MS)
                    .setInterpolator(LiquidGlassTokens.INTERPOLATOR_APPLE_SPRING)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (currentState is OverlayState.CompactEntering) {
                                transitionTo(OverlayState.CompactVisible)
                            }
                            overlay.animate().setListener(null)
                        }
                    })
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
        transitionTo(OverlayState.MasterEntering)

        val masterParams = createMasterLayoutParams()
        try {
            windowManager.updateViewLayout(overlay, masterParams)
        } catch (_: Exception) {}

        overlay.expandToMasterView()
        transitionTo(OverlayState.MasterVisible)
        resetDismissTimer()
    }

    private fun collapseToCompact() {
        val overlay = overlayView ?: return
        transitionTo(OverlayState.Collapsing)

        overlay.collapseToCompactView {
            val compactParams = createCompactLayoutParams()
            try {
                windowManager.updateViewLayout(overlay, compactParams)
            } catch (_: Exception) {}
            transitionTo(OverlayState.CompactVisible)
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

            // Prevent cutout clipping on AMOLED punch-hole displays (OPPO K13)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blur = prefs.blurRadius.toInt().coerceIn(15, 60)
                setBlurBehindRadius(blur)
            }
        }
    }

    private fun createMasterLayoutParams(): WindowManager.LayoutParams {
        var windowFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        // In Master mode, omit FLAG_NOT_FOCUSABLE so the window can receive physical Back button events
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blur = prefs.blurRadius.toInt().coerceIn(25, 75)
                setBlurBehindRadius(blur)
            }
        }
    }

    private fun resetDismissTimer() {
        handler.removeCallbacks(dismissRunnable)
        if (currentState.isUserInteracting) return // Never auto-dismiss while user is actively touching slider!
        val delay = if (currentState.isExpanded) 6500L else prefs.timeoutMs
        handler.postDelayed(dismissRunnable, delay)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val overlay = overlayView ?: return

        if (currentState.isVisible && currentState !is OverlayState.Dismissing) {
            transitionTo(OverlayState.Dismissing)

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
                    .setDuration(LiquidGlassTokens.DURATION_COMPACT_EXIT_MS)
                    .setInterpolator(LiquidGlassTokens.INTERPOLATOR_SMOOTH_EXIT)
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
        unregisterVolumeReceiver()
        try {
            if (currentState.isVisible) {
                windowManager.removeView(overlay)
            }
        } catch (_: Exception) {}
        transitionTo(OverlayState.Hidden)
        overlayView = null
    }

    private fun registerVolumeReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                context.registerReceiver(volumeChangeReceiver, filter)
                isReceiverRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun unregisterVolumeReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(volumeChangeReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    private fun transitionTo(newState: OverlayState) {
        currentState = newState
    }
}
