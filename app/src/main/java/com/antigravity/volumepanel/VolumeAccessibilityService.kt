package com.antigravity.volumepanel

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Liquid Volume Accessibility Service
 *
 * Responsibilities:
 * - Intercepts physical KEYCODE_VOLUME_UP and KEYCODE_VOLUME_DOWN events.
 * - Suppresses the OEM/ColorOS stock volume dialog by consuming both ACTION_DOWN and ACTION_UP.
 * - Dispatches fluid volume changes to VolumeOverlayController.
 * - Strict adherence to Google Play Accessibility Policy: No screen inspection, no keystroke logging.
 */
class VolumeAccessibilityService : AccessibilityService() {

    private lateinit var overlayController: VolumeOverlayController

    override fun onCreate() {
        super.onCreate()
        overlayController = VolumeOverlayController(this)
        instance = this
        Log.d("LiquidVolume", "VolumeAccessibilityService created successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayController.dismiss()
        instance = null
        Log.d("LiquidVolume", "VolumeAccessibilityService destroyed")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    Log.d("LiquidVolume", "AccessibilityService.onKeyEvent: VOLUME_UP ACTION_DOWN")
                    overlayController.show(direction = 1)
                }
                // Return true for BOTH DOWN and UP to fully suppress the default ColorOS dialog
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    Log.d("LiquidVolume", "AccessibilityService.onKeyEvent: VOLUME_DOWN ACTION_DOWN")
                    overlayController.show(direction = -1)
                }
                // Return true for BOTH DOWN and UP
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event reading required. Preserves user privacy completely.
    }

    override fun onInterrupt() {
        // No-op
    }

    companion object {
        var instance: VolumeAccessibilityService? = null
            private set

        fun triggerTest(direction: Int = 0) {
            Log.d("LiquidVolume", "VolumeAccessibilityService.triggerTest(direction=$direction)")
            instance?.overlayController?.show(direction)
        }
    }
}
