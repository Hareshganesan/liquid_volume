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
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * Production-Quality WindowManager & Lifecycle Orchestrator
 *
 * Responsibilities:
 * - Formal OverlayState machine to eliminate race conditions and collision glitches.
 * - Dynamic WindowManager layout re-targeting (Compact Edge ➔ Master Center Modal).
 * - System Volume BroadcastReceiver (android.media.VOLUME_CHANGED_ACTION) with RECEIVER_EXPORTED for API 34+.
 * - Canonical Application-scoped AudioManager instance to prevent service scoping limitations.
 * - Dynamic active stream re-binding on hardware events and external broadcasts.
 * - Focus arbitration (Pass-through touch in Compact; Back-button capture in Master).
 * - True hardware blur support (FLAG_BLUR_BEHIND) on API 31+ with safe bounds.
 * - Apple fluid spring animations for entrance, collapse, and bezel-anchored dismissal.
 */
class VolumeOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = (context.applicationContext ?: context).getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = PreferencesManager(context)
    private val handler = Handler(Looper.getMainLooper())

    private var overlayView: MasterVolumeOverlayView? = null
    var currentState: OverlayState = OverlayState.Hidden
        private set

    private val dismissRunnable = Runnable { dismiss() }

    // External System Volume Broadcast Receiver (Registered with ContextCompat.RECEIVER_EXPORTED for Android 14+)
    private var isReceiverRegistered = false
    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                val streamValue = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)

                Log.d("LiquidVolume", "VOLUME_CHANGED_ACTION received: streamType=$streamType, streamValue=$streamValue")

                if (streamType != -1 && streamValue != -1) {
                    val overlay = overlayView
                    if (overlay != null && !overlay.isExpanded) {
                        // Dynamically bind compact slider to the active stream changed by Android OS
                        if (overlay.compactSlider.streamType != streamType) {
                            Log.d("LiquidVolume", "Switching compactSlider to newly adjusted active stream: $streamType")
                            overlay.compactSlider.updateStreamType(streamType)
                        }
                    }
                    overlay?.onExternalVolumeChange(streamType, streamValue)
                }
            }
        }
    }

    fun show(direction: Int = 0) {
        handler.removeCallbacks(dismissRunnable)
        Log.d("LiquidVolume", "VolumeOverlayController.show(direction=$direction, currentState=$currentState)")

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

        // If the view was in the middle of dismissing, cancel the exit and restore visibility immediately
        if (currentState is OverlayState.Dismissing) {
            overlay.animate().cancel()
            overlay.alpha = 1f
            overlay.scaleX = 1f
            overlay.scaleY = 1f
            overlay.translationX = 0f
            transitionTo(OverlayState.CompactVisible)
        } else if (!currentState.isVisible) {
            registerVolumeReceiver()
            val params = createCompactLayoutParams()

            try {
                windowManager.addView(overlay, params)
                transitionTo(OverlayState.CompactEntering)

                val density = context.resources.displayMetrics.density
                val startTranslationX = if (prefs.isRightSide) 55f * density else -55f * density

                overlay.pivotX = if (prefs.isRightSide) overlay.width.toFloat() else 0f
                overlay.pivotY = overlay.height / 2f
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

            } catch (e: Exception) {
                Log.w("LiquidVolume", "Failed to add overlay window: ${e.message}")
            }
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

            // Prevent cutout clipping on AMOLED displays (OPPO K13)
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
                val endTranslationX = if (prefs.isRightSide) 42f * density else -42f * density

                // Anchor scale pivot to the physical bezel edge
                overlay.pivotX = if (prefs.isRightSide) overlay.width.toFloat() else 0f
                overlay.pivotY = overlay.height / 2f

                overlay.animate()
                    .translationX(endTranslationX)
                    .scaleX(0.65f)
                    .scaleY(0.85f)
                    .alpha(0f)
                    .setDuration(LiquidGlassTokens.DURATION_COMPACT_EXIT_MS)
                    .setInterpolator(LiquidGlassTokens.INTERPOLATOR_EXPONENTIAL_EXIT)
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
            if (overlay.isAttachedToWindow) {
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
                ContextCompat.registerReceiver(
                    context,
                    volumeChangeReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
                Log.d("LiquidVolume", "Registered VOLUME_CHANGED_ACTION receiver with RECEIVER_EXPORTED")
            } catch (e: Exception) {
                Log.w("LiquidVolume", "Failed to register volumeChangeReceiver: ${e.message}")
            }
        }
    }

    private fun unregisterVolumeReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(volumeChangeReceiver)
                Log.d("LiquidVolume", "Unregistered VOLUME_CHANGED_ACTION receiver")
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    private fun transitionTo(newState: OverlayState) {
        currentState = newState
    }
}
