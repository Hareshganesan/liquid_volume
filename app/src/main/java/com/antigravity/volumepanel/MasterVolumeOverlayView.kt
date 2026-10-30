package com.antigravity.volumepanel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Production-Quality Liquid Volume Master View
 *
 * Architecture & Features:
 * - Coordinates Compact Volume Slider and Expanded Master Audio Control Center.
 * - Hardware GPU Frosted Glass styling without blue/purple casts.
 * - Minimal, authentic iOS design language: zero marketing copy, pure functional controls.
 * - Physical Back button interception when expanded.
 * - Real-time synchronization for 4 independent audio streams.
 * - 3-Way Segmented Ringer mode switcher (Silent, Vibrate, Ring).
 * - Real-time active output device detection (Phone Speaker / Bluetooth / Wired).
 */
@SuppressLint("ViewConstructor")
class MasterVolumeOverlayView(
    context: Context,
    private val audioManager: AudioManager,
    private val prefs: PreferencesManager,
    private val onInteractionStart: () -> Unit,
    private val onInteractionEnd: () -> Unit,
    private val onRequestExpand: () -> Unit,
    private val onRequestCollapse: () -> Unit,
    private val onRequestDismiss: () -> Unit
) : FrameLayout(context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    var isExpanded = false
        private set

    // Containers
    private val backdropDim = View(context)
    private val compactRoot = FrameLayout(context)
    val compactSlider = IOSControlSlider(
        context = context,
        audioManager = audioManager,
        streamType = AudioManager.STREAM_MUSIC,
        prefs = prefs,
        onInteractionStart = onInteractionStart,
        onInteractionEnd = onInteractionEnd,
        onRequestDismiss = onRequestDismiss,
        vibrator = vibrator,
        isMasterMode = false
    )
    private val btnExpandDots = ImageView(context)

    // Master Dialog Elements
    private val masterCard = LinearLayout(context)
    private val slidersContainer = LinearLayout(context)
    private val soundModeSegmented = LinearLayout(context)
    private val tvOutputDevice = TextView(context)
    private val masterSliders = mutableListOf<IOSControlSlider>()
    private val percentIndicators = mutableListOf<TextView>()

    init {
        setupBackdrop()
        setupCompactLayout()
        setupMasterLayout()
        showCompactView()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Intercept physical Back button to smoothly close Master Control Center
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && isExpanded) {
            onRequestCollapse()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            onRequestDismiss()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun setupBackdrop() {
        backdropDim.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        backdropDim.setBackgroundColor(LiquidGlassTokens.COLOR_BACKDROP_SCRIM)
        backdropDim.visibility = View.GONE
        backdropDim.setOnClickListener {
            onInteractionStart()
            onRequestCollapse()
        }
        addView(backdropDim)
    }

    private fun setupCompactLayout() {
        val width = dpToPx(prefs.sliderWidthDp)
        val height = dpToPx(prefs.sliderHeightDp)

        val containerParams = LayoutParams(width + dpToPx(24), height + dpToPx(50)).apply {
            gravity = if (prefs.isRightSide) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
        }
        compactRoot.layoutParams = containerParams

        // 3-Dots Expand Button (Minimalist Apple Frosted Glass Orb)
        val dotSize = dpToPx(34)
        val dotParams = LayoutParams(dotSize, dotSize).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(3)
        }
        btnExpandDots.layoutParams = dotParams
        btnExpandDots.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_more_horiz))
        btnExpandDots.setColorFilter(Color.WHITE)

        val dotBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#38FFFFFF")) // Translucent frosted orb
            setStroke(dpToPx(1.2f), Color.parseColor("#55FFFFFF"))
        }
        btnExpandDots.background = dotBg
        btnExpandDots.setPadding(dpToPx(7), dpToPx(7), dpToPx(7), dpToPx(7))
        btnExpandDots.setOnClickListener {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            onInteractionStart()
            onRequestExpand()
        }

        // Compact Slider
        val sliderParams = LayoutParams(LayoutParams.MATCH_PARENT, height).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        compactSlider.layoutParams = sliderParams

        compactRoot.addView(compactSlider)
        compactRoot.addView(btnExpandDots)
        addView(compactRoot)
    }

    private fun setupMasterLayout() {
        masterCard.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        masterCard.orientation = LinearLayout.VERTICAL
        masterCard.setPadding(dpToPx(24), dpToPx(22), dpToPx(24), dpToPx(22))

        // True Liquid Frosted Glass: Pure translucent neutral obsidian glass (NO dark blue tint!)
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(LiquidGlassTokens.CORNER_RADIUS_MASTER_CARD_DP).toFloat()
            setColor(LiquidGlassTokens.COLOR_GLASS_BASE)
            setStroke(dpToPx(LiquidGlassTokens.SPECULAR_BORDER_WIDTH_DP), Color.parseColor("#44FFFFFF"))
        }
        masterCard.background = cardBg
        masterCard.elevation = dpToPx(24).toFloat()

        // 1. Clean Minimal Header: Active Output Route Pill + Close Button (No Marketing Copy)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Output Device Pill
        val deviceBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply {
                marginEnd = dpToPx(14)
            }
            val bannerBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18).toFloat()
                setColor(Color.parseColor("#22FFFFFF"))
                setStroke(dpToPx(1), Color.parseColor("#30FFFFFF"))
            }
            background = bannerBg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
        }

        val devIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                marginEnd = dpToPx(8)
            }
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_output_speaker))
            setColorFilter(Color.parseColor(prefs.accentColorHex))
        }

        tvOutputDevice.apply {
            text = getActiveOutputDeviceText()
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val dotActive = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(7), dpToPx(7))
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22C55E")) // Active connection indicator
            }
            background = dotBg
        }

        deviceBanner.addView(devIcon)
        deviceBanner.addView(tvOutputDevice)
        deviceBanner.addView(dotActive)
        header.addView(deviceBanner)

        // Apple Style Circular Close Button
        val btnClose = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(34), dpToPx(34))
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_close))
            setColorFilter(Color.WHITE)
            val closeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2AFFFFFF"))
                setStroke(dpToPx(1), Color.parseColor("#38FFFFFF"))
            }
            background = closeBg
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                onInteractionStart()
                onRequestCollapse()
            }
        }
        header.addView(btnClose)
        masterCard.addView(header)

        // 2. 4-Stream Sliders Row
        slidersContainer.orientation = LinearLayout.HORIZONTAL
        slidersContainer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(20)
            bottomMargin = dpToPx(18)
        }

        val streams = listOf(
            Triple("Media", AudioManager.STREAM_MUSIC, R.drawable.ic_volume_up),
            Triple("Ring", AudioManager.STREAM_RING, R.drawable.ic_bell),
            Triple("Alerts", AudioManager.STREAM_NOTIFICATION, R.drawable.ic_notifications),
            Triple("Alarm", AudioManager.STREAM_ALARM, R.drawable.ic_alarm)
        )

        masterSliders.clear()
        percentIndicators.clear()

        for (i in streams.indices) {
            val (label, streamType, iconRes) = streams[i]
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (i < streams.size - 1) {
                        marginEnd = dpToPx(14)
                    }
                }
            }

            // Percentage Text above Slider
            val tvPercent = TextView(context).apply {
                text = "0%"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(6))
            }
            percentIndicators.add(tvPercent)

            // Slider
            val slider = IOSControlSlider(
                context = context,
                audioManager = audioManager,
                streamType = streamType,
                prefs = prefs,
                onInteractionStart = onInteractionStart,
                onInteractionEnd = onInteractionEnd,
                onRequestDismiss = onRequestDismiss,
                vibrator = vibrator,
                customIconRes = iconRes,
                isMasterMode = true,
                onVolumeChanged = { percent ->
                    tvPercent.text = if (percent == 0) "Muted" else "$percent%"
                    tvPercent.setTextColor(if (percent == 0) Color.parseColor("#F87171") else Color.parseColor("#F1F5F9"))
                }
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(LiquidGlassTokens.MASTER_SLIDER_WIDTH_DP),
                    dpToPx(LiquidGlassTokens.MASTER_SLIDER_HEIGHT_DP)
                )
            }
            masterSliders.add(slider)

            // Stream Label underneath
            val tvLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(8), 0, 0)
            }

            col.addView(tvPercent)
            col.addView(slider)
            col.addView(tvLabel)
            slidersContainer.addView(col)
        }
        masterCard.addView(slidersContainer)

        // 3. Sound Mode Segmented Switch (Silent, Vibrate, Ring)
        setupSegmentedSoundBar()
        masterCard.addView(soundModeSegmented)

        masterCard.visibility = View.GONE
        addView(masterCard)
    }

    private fun getActiveOutputDeviceText(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (d in devices) {
                    when (d.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                            val name = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
                            return "$name · Wireless"
                        }
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                            return "Headphones · Hi-Res"
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return "Phone Speaker"
    }

    private fun setupSegmentedSoundBar() {
        soundModeSegmented.removeAllViews()
        soundModeSegmented.orientation = LinearLayout.HORIZONTAL
        soundModeSegmented.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44)).apply {
            topMargin = dpToPx(4)
        }

        val barBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat()
            setColor(Color.parseColor("#20FFFFFF")) // Translucent frosted glass bar
            setStroke(dpToPx(1), Color.parseColor("#25FFFFFF"))
        }
        soundModeSegmented.background = barBg
        soundModeSegmented.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))

        val modes = listOf(
            Triple("Silent", AudioManager.RINGER_MODE_SILENT, R.drawable.ic_volume_mute),
            Triple("Vibrate", AudioManager.RINGER_MODE_VIBRATE, R.drawable.ic_vibrate),
            Triple("Ring", AudioManager.RINGER_MODE_NORMAL, R.drawable.ic_bell)
        )

        val currentMode = audioManager.ringerMode

        for ((name, mode, iconRes) in modes) {
            val isSelected = (currentMode == mode)
            val btn = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

                if (isSelected) {
                    val activeBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(12).toFloat()
                        val activeColor = when (mode) {
                            AudioManager.RINGER_MODE_SILENT -> LiquidGlassTokens.COLOR_MODE_SILENT
                            AudioManager.RINGER_MODE_VIBRATE -> LiquidGlassTokens.COLOR_MODE_VIBRATE
                            else -> Color.parseColor(prefs.accentColorHex)
                        }
                        setColor(activeColor)
                    }
                    background = activeBg
                }

                val icon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(15), dpToPx(15)).apply {
                        marginEnd = dpToPx(6)
                    }
                    setImageDrawable(ContextCompat.getDrawable(context, iconRes))
                    setColorFilter(if (isSelected) LiquidGlassTokens.COLOR_TEXT_DARK_INVERTED else Color.WHITE)
                }

                val label = TextView(context).apply {
                    text = name
                    textSize = 12.5f
                    setTextColor(if (isSelected) LiquidGlassTokens.COLOR_TEXT_DARK_INVERTED else Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                }

                addView(icon)
                addView(label)

                setOnClickListener {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    onInteractionStart()
                    try {
                        audioManager.ringerMode = mode
                    } catch (e: Exception) {
                        Log.w("LiquidVolume", "Failed to change ringerMode to $mode: ${e.message}")
                    }
                    setupSegmentedSoundBar()
                    refreshAll()
                }
            }
            soundModeSegmented.addView(btn)
        }
    }

    fun showCompactView() {
        isExpanded = false
        backdropDim.visibility = View.GONE
        masterCard.visibility = View.GONE
        compactRoot.visibility = View.VISIBLE
        compactSlider.refreshVolume()
    }

    fun expandToMasterView() {
        isExpanded = true
        compactRoot.visibility = View.GONE
        backdropDim.visibility = View.VISIBLE
        backdropDim.alpha = 0f
        backdropDim.animate().alpha(1f).setDuration(LiquidGlassTokens.DURATION_MASTER_ENTER_MS).start()

        masterCard.visibility = View.VISIBLE
        tvOutputDevice.text = getActiveOutputDeviceText()

        masterCard.scaleX = 0.82f
        masterCard.scaleY = 0.82f
        masterCard.alpha = 0f

        masterCard.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(LiquidGlassTokens.DURATION_MASTER_ENTER_MS)
            .setInterpolator(LiquidGlassTokens.INTERPOLATOR_APPLE_SPRING)
            .start()

        refreshAll()
    }

    fun collapseToCompactView(onCollapsed: (() -> Unit)? = null) {
        isExpanded = false
        backdropDim.animate().alpha(0f).setDuration(LiquidGlassTokens.DURATION_MASTER_COLLAPSE_MS).start()

        masterCard.animate()
            .scaleX(0.82f)
            .scaleY(0.82f)
            .alpha(0f)
            .setDuration(LiquidGlassTokens.DURATION_MASTER_COLLAPSE_MS)
            .setInterpolator(LiquidGlassTokens.INTERPOLATOR_DECELERATE)
            .withEndAction {
                backdropDim.visibility = View.GONE
                masterCard.visibility = View.GONE
                compactRoot.visibility = View.VISIBLE
                compactRoot.alpha = 0f
                compactRoot.scaleX = 0.88f
                compactRoot.scaleY = 0.88f
                compactRoot.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(190)
                    .setInterpolator(LiquidGlassTokens.INTERPOLATOR_APPLE_SPRING)
                    .start()
                compactSlider.refreshVolume()
                onCollapsed?.invoke()
            }
            .start()
    }

    fun updateActiveStream() {
        val activeStream = when {
            audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION -> AudioManager.STREAM_VOICE_CALL
            audioManager.mode == AudioManager.MODE_RINGTONE -> AudioManager.STREAM_RING
            audioManager.isMusicActive -> AudioManager.STREAM_MUSIC
            else -> AudioManager.STREAM_MUSIC
        }
        Log.d("LiquidVolume", "updateActiveStream: selectedStream=$activeStream (isMusicActive=${audioManager.isMusicActive}, mode=${audioManager.mode})")
        compactSlider.updateStreamType(activeStream)
    }

    fun handleHardwareKeyStep(direction: Int) {
        Log.d("LiquidVolume", "handleHardwareKeyStep(direction=$direction) calling adjustSuggestedStreamVolume")
        try {
            audioManager.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)
        } catch (e: Exception) {
            Log.w("LiquidVolume", "adjustSuggestedStreamVolume error: ${e.message}")
        }
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

        if (isExpanded) {
            refreshAll()
        } else {
            compactSlider.refreshVolume()
        }
    }

    fun onExternalVolumeChange(streamType: Int, volume: Int) {
        Log.d("LiquidVolume", "MasterVolumeOverlayView.onExternalVolumeChange: streamType=$streamType, volume=$volume")
        if (compactSlider.streamType == streamType) {
            compactSlider.refreshVolume(volume)
        }
        for (s in masterSliders) {
            if (s.streamType == streamType) {
                s.refreshVolume(volume)
            }
        }
    }

    fun refreshAll() {
        compactSlider.refreshVolume()
        for (s in masterSliders) {
            s.refreshVolume()
        }
        setupSegmentedSoundBar()
    }

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
