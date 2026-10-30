package com.antigravity.volumepanel

/**
 * Explicit interaction states for the Volume Overlay lifecycle.
 * Prevents animation conflicts, race conditions, and corrupted window transitions.
 */
sealed class OverlayState {
    object Hidden : OverlayState()
    object CompactEntering : OverlayState()
    object CompactVisible : OverlayState()
    object CompactInteracting : OverlayState()
    object MasterEntering : OverlayState()
    object MasterVisible : OverlayState()
    object MasterInteracting : OverlayState()
    object Collapsing : OverlayState()
    object Dismissing : OverlayState()

    val isVisible: Boolean
        get() = this !is Hidden

    val isExpanded: Boolean
        get() = this is MasterEntering || this is MasterVisible || this is MasterInteracting

    val isUserInteracting: Boolean
        get() = this is CompactInteracting || this is MasterInteracting
}
