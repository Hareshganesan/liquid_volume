package com.antigravity.volumepanel

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeAccessibilityService : AccessibilityService() {

    private lateinit var overlayController: VolumeOverlayController

    override fun onCreate() {
        super.onCreate()
        overlayController = VolumeOverlayController(this)
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    overlayController.show(direction = 1)
                    return true // Mutes the stock ColorOS volume panel!
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    overlayController.show(direction = -1)
                    return true // Mutes the stock ColorOS volume panel!
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event listening required
    }

    override fun onInterrupt() {
        // No-op
    }

    companion object {
        var instance: VolumeAccessibilityService? = null
            private set

        fun triggerTest(direction: Int = 0) {
            instance?.overlayController?.show(direction)
        }
    }
}
