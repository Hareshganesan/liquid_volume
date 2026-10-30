package com.antigravity.volumepanel

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class MasterVolumeOverlayView(
    context: Context,
    private val audioManager: AudioManager,
    private val prefs: PreferencesManager,
    private val onInteraction: () -> Unit,
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
    private val compactSlider = IOSControlSlider(
        context = context,
        audioManager = audioManager,
        streamType = AudioManager.STREAM_MUSIC,
        prefs = prefs,
        onInteraction = onInteraction,
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            onRequestDismiss()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun setupBackdrop() {
        backdropDim.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        backdropDim.setBackgroundColor(Color.parseColor("#73000000")) // 45% rich dark glass scrim
        backdropDim.visibility = View.GONE
        backdropDim.setOnClickListener {
            onInteraction()
            onRequestCollapse()
        }
        addView(backdropDim)
    }

    private fun setupCompactLayout() {
        val width = dpToPx(prefs.sliderWidthDp)
        val height = dpToPx(prefs.sliderHeightDp)

        val containerParams = LayoutParams(width + dpToPx(20), height + dpToPx(46)).apply {
            gravity = if (prefs.isRightSide) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
        }
        compactRoot.layoutParams = containerParams

        // 3-Dots Expand Button (Apple Frosted Glass Orb)
        val dotSize = dpToPx(32)
        val dotParams = LayoutParams(dotSize, dotSize).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(2)
        }
        btnExpandDots.layoutParams = dotParams
        btnExpandDots.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_more_horiz))
        btnExpandDots.setColorFilter(Color.WHITE)

        val dotBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#441E293B"))
            setStroke(dpToPx(1), Color.parseColor("#45FFFFFF"))
        }
        btnExpandDots.background = dotBg
        btnExpandDots.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
        btnExpandDots.setOnClickListener {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            onInteraction()
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
        masterCard.setPadding(dpToPx(26), dpToPx(24), dpToPx(26), dpToPx(24))

        // Master Card Apple Glass Styling
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(36).toFloat()
            setColor(Color.parseColor("#F00E1424")) // Opaque Obsidian Frosted Glass
            setStroke(dpToPx(1.2f), Color.parseColor("#48FFFFFF")) // Glowing Specular Rim
        }
        masterCard.background = cardBg
        masterCard.elevation = dpToPx(20).toFloat()

        // 1. Header (Badge + Title + Close Button)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Audio Icon Badge
        val iconBadge = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(38)).apply {
                marginEnd = dpToPx(12)
            }
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_volume_up))
            setColorFilter(Color.parseColor(prefs.accentColorHex))
            val badgeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#25FFFFFF"))
                setStroke(dpToPx(1), Color.parseColor("#35FFFFFF"))
            }
            background = badgeBg
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(context).apply {
            text = "Audio Control Center"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = -0.01f
        }
        val tvSub = TextView(context).apply {
            text = "ColorOS 16 · iOS 18 Liquid Glass"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
        }
        titleCol.addView(tvTitle)
        titleCol.addView(tvSub)

        // Close Button
        val btnClose = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_close))
            setColorFilter(Color.WHITE)
            val closeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#30FFFFFF"))
                setStroke(dpToPx(1), Color.parseColor("#30FFFFFF"))
            }
            background = closeBg
            setPadding(dpToPx(7), dpToPx(7), dpToPx(7), dpToPx(7))
            setOnClickListener {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                onInteraction()
                onRequestCollapse()
            }
        }

        header.addView(iconBadge)
        header.addView(titleCol)
        header.addView(btnClose)
        masterCard.addView(header)

        // 2. Output Device Indicator Pill
        val deviceBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(34)).apply {
                topMargin = dpToPx(14)
            }
            val bannerBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#22FFFFFF"))
                setStroke(dpToPx(1), Color.parseColor("#18FFFFFF"))
            }
            background = bannerBg
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
        }

        val devIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                marginEnd = dpToPx(8)
            }
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_output_speaker))
            setColorFilter(Color.parseColor("#38BDF8")) // Vivid sky blue
        }

        tvOutputDevice.apply {
            text = getActiveOutputDeviceText()
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val dotActive = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(6), dpToPx(6))
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22C55E")) // Green online dot
            }
            background = dotBg
        }

        deviceBanner.addView(devIcon)
        deviceBanner.addView(tvOutputDevice)
        deviceBanner.addView(dotActive)
        masterCard.addView(deviceBanner)

        // 3. Sliders Row
        slidersContainer.orientation = LinearLayout.HORIZONTAL
        slidersContainer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(20)
            bottomMargin = dpToPx(18)
        }

        val streams = listOf(
            Triple("Media", AudioManager.STREAM_MUSIC, R.drawable.ic_volume_up),
            Triple("Ringtone", AudioManager.STREAM_RING, R.drawable.ic_bell),
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

            // Floating Percentage Text above Slider
            val tvPercent = TextView(context).apply {
                text = "0%"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11.5f
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
                onInteraction = onInteraction,
                onRequestDismiss = onRequestDismiss,
                vibrator = vibrator,
                customIconRes = iconRes,
                isMasterMode = true,
                onVolumeChanged = { percent ->
                    tvPercent.text = if (percent == 0) "Muted" else "$percent%"
                    tvPercent.setTextColor(if (percent == 0) Color.parseColor("#F87171") else Color.parseColor("#CBD5E1"))
                }
            ).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(54), dpToPx(165))
            }
            masterSliders.add(slider)

            // Label underneath
            val tvLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#F1F5F9"))
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

        // 4. Sound Mode Segmented Switch
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
                            val name = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
                            return "$name · Wireless"
                        }
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                            return "Headphones · Hi-Res Audio"
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return "Phone Speaker · Spatial Audio"
    }

    private fun setupSegmentedSoundBar() {
        soundModeSegmented.removeAllViews()
        soundModeSegmented.orientation = LinearLayout.HORIZONTAL
        soundModeSegmented.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46)).apply {
            topMargin = dpToPx(4)
        }

        val barBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat()
            setColor(Color.parseColor("#22121B2A"))
            setStroke(dpToPx(1), Color.parseColor("#22FFFFFF"))
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
                            AudioManager.RINGER_MODE_SILENT -> Color.parseColor("#EF4444") // Coral Red
                            AudioManager.RINGER_MODE_VIBRATE -> Color.parseColor("#F59E0B") // Amber
                            else -> Color.parseColor(prefs.accentColorHex)
                        }
                        setColor(activeColor)
                    }
                    background = activeBg
                }

                val icon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                        marginEnd = dpToPx(6)
                    }
                    setImageDrawable(ContextCompat.getDrawable(context, iconRes))
                    setColorFilter(if (isSelected) Color.parseColor("#090D16") else Color.WHITE)
                }

                val label = TextView(context).apply {
                    text = name
                    textSize = 13f
                    setTextColor(if (isSelected) Color.parseColor("#090D16") else Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                }

                addView(icon)
                addView(label)

                setOnClickListener {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    onInteraction()
                    try {
                        audioManager.ringerMode = mode
                    } catch (_: Exception) {}
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
        masterCard.visibility = View.VISIBLE

        tvOutputDevice.text = getActiveOutputDeviceText()

        masterCard.scaleX = 0.82f
        masterCard.scaleY = 0.82f
        masterCard.alpha = 0f

        masterCard.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        refreshAll()
    }

    fun collapseToCompactView(onCollapsed: (() -> Unit)? = null) {
        isExpanded = false
        backdropDim.visibility = View.GONE
        masterCard.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                masterCard.visibility = View.GONE
                compactRoot.visibility = View.VISIBLE
                compactSlider.refreshVolume()
                onCollapsed?.invoke()
            }
            .start()
    }

    fun handleHardwareKeyStep(direction: Int) {
        audioManager.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

        if (isExpanded) {
            refreshAll()
        } else {
            compactSlider.refreshVolume()
        }
    }

    fun refreshAll() {
        compactSlider.refreshVolume()
        for (s in masterSliders) {
            s.refreshVolume()
        }
        setupSegmentedSoundBar()
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

/**
 * Ultra-Polished iOS 18 / ColorOS 16 Fluid Glass Slider
 * Key Highlights:
 * 1. True Apple Two-Tone Split Inversion (cleanly slices across icon and text as fluid rises)
 * 2. Specular Liquid Meniscus Crest Line
 * 3. Gradient Glass Capsule with Specular Rim Lighting
 * 4. Elastic Liquid Expansion (+10dp) with Spring Physics
 * 5. Tap-to-Mute / Restore with Tactile Haptics
 * 6. Swipe-to-Dismiss on Compact Pill
 */
@SuppressLint("ViewConstructor")
class IOSControlSlider(
    context: Context,
    private val audioManager: AudioManager,
    val streamType: Int,
    private val prefs: PreferencesManager,
    private val onInteraction: () -> Unit,
    private val onRequestDismiss: () -> Unit,
    private val vibrator: Vibrator?,
    private val customIconRes: Int? = null,
    private val isMasterMode: Boolean = false,
    private val onVolumeChanged: ((Int) -> Unit)? = null
) : View(context) {

    private val pillRect = RectF()
    private val fillRect = RectF()
    private val clipPath = Path()
    private val meniscusRect = RectF()

    private val normalWidth = dpToPx(if (isMasterMode) 54 else prefs.sliderWidthDp).toFloat()
    private val expandedWidth = normalWidth + dpToPx(10).toFloat()
    private var currentWidth = normalWidth
    private val pillHeight = dpToPx(if (isMasterMode) 165 else prefs.sliderHeightDp).toFloat()

    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f).toFloat()
    }

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor(prefs.accentColorHex)
    }

    private val meniscusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#90FFFFFF") // Glowing specular liquid crest
    }

    private val percentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(12f).toFloat()
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private var maxVolume = 1
    private var animatedProgress = 0.5f
    private var progressAnimator: ValueAnimator? = null
    private var widthAnimator: ValueAnimator? = null

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var hasMoved = false
    private var lastNonZeroVolume: Int = 1

    init {
        refreshVolume()
    }

    fun refreshVolume() {
        maxVolume = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val currentVol = audioManager.getStreamVolume(streamType)
        if (currentVol > 0) {
            lastNonZeroVolume = currentVol
        }
        val target = (currentVol.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(animatedProgress, target).apply {
            duration = 140
            interpolator = OvershootInterpolator(0.9f)
            addUpdateListener { va ->
                animatedProgress = va.animatedValue as Float
                onVolumeChanged?.invoke((animatedProgress * 100).toInt())
                invalidate()
            }
            start()
        }
    }

    private fun getCurrentIcon(): Drawable? {
        val iconRes = when {
            animatedProgress <= 0.01f -> R.drawable.ic_volume_mute
            customIconRes != null -> customIconRes
            streamType == AudioManager.STREAM_MUSIC -> {
                if (animatedProgress < 0.45f) R.drawable.ic_volume_down else R.drawable.ic_volume_up
            }
            streamType == AudioManager.STREAM_RING -> R.drawable.ic_bell
            streamType == AudioManager.STREAM_NOTIFICATION -> R.drawable.ic_notifications
            streamType == AudioManager.STREAM_ALARM -> R.drawable.ic_alarm
            else -> R.drawable.ic_volume_up
        }
        return ContextCompat.getDrawable(context, iconRes)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (expandedWidth + dpToPx(10)).toInt()
        val h = (pillHeight + dpToPx(10)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = (width - currentWidth) / 2f
        val top = (height - pillHeight) / 2f
        val right = left + currentWidth
        val bottom = top + pillHeight
        val radius = currentWidth / 2f

        pillRect.set(left, top, right, bottom)

        // 1. Frosted Glass Capsule Background (Linear Gradient for depth)
        glassBgPaint.shader = LinearGradient(
            left, top, left, bottom,
            Color.parseColor("#441B243B"),
            Color.parseColor("#250D1322"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pillRect, radius, radius, glassBgPaint)

        // 2. Liquid Fill with Rounded Clipping
        clipPath.reset()
        clipPath.addRoundRect(pillRect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        val fillHeight = pillHeight * animatedProgress
        val fillTop = bottom - fillHeight
        fillRect.set(left, fillTop, right, bottom)
        canvas.drawRect(fillRect, liquidPaint)

        // Specular Meniscus Line (liquid wave crest)
        if (animatedProgress in 0.02f..0.98f) {
            meniscusRect.set(left, fillTop, right, fillTop + dpToPx(2.5f))
            canvas.drawRect(meniscusRect, meniscusPaint)
        }

        canvas.restore()

        // 3. Specular Rim / Glowing Stroke (Simulates light catching edge of glass)
        glassBorderPaint.shader = LinearGradient(
            left, top, left, bottom,
            intArrayOf(
                Color.parseColor("#7AFFFFFF"), // Crisp top highlight
                Color.parseColor("#30FFFFFF"),
                Color.parseColor("#15FFFFFF")  // Subdued bottom
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pillRect, radius, radius, glassBorderPaint)

        // 4. Percentage Text with Two-Tone Split Inversion (in master mode)
        if (isMasterMode) {
            val percent = (animatedProgress * 100).toInt()
            val textY = top + dpToPx(24)
            val textStr = if (percent == 0) "OFF" else "$percent%"

            // Pass A: Unfilled area (Pure White)
            canvas.save()
            canvas.clipRect(left, top, right, fillTop)
            percentTextPaint.color = Color.WHITE
            canvas.drawText(textStr, pillRect.centerX(), textY.toFloat(), percentTextPaint)
            canvas.restore()

            // Pass B: Submerged filled area (Deep Dark Contrast)
            canvas.save()
            canvas.clipRect(left, fillTop, right, bottom)
            percentTextPaint.color = Color.parseColor("#090D16")
            canvas.drawText(textStr, pillRect.centerX(), textY.toFloat(), percentTextPaint)
            canvas.restore()
        }

        // 5. Dynamic Volume Icon with Exact Apple Two-Tone Split Inversion!
        val ic = getCurrentIcon() ?: return
        val iconSize = dpToPx(23)
        val iconX = (pillRect.centerX() - iconSize / 2f).toInt()
        val iconY = (pillRect.bottom - dpToPx(25) - iconSize / 2f).toInt()
        ic.setBounds(iconX, iconY, iconX + iconSize, iconY + iconSize)

        // Pass A: Icon portion ABOVE the fluid (Pure White)
        canvas.save()
        canvas.clipRect(left, top, right, fillTop)
        DrawableCompat.setTint(ic, Color.WHITE)
        ic.draw(canvas)
        canvas.restore()

        // Pass B: Icon portion SUBMERGED in fluid (Deep Obsidian)
        canvas.save()
        canvas.clipRect(left, fillTop, right, bottom)
        DrawableCompat.setTint(ic, Color.parseColor("#090D16"))
        ic.draw(canvas)
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        onInteraction()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                hasMoved = false
                animateWidth(expandedWidth)
                handleTouch(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY

                if (abs(dy) > dpToPx(4)) {
                    hasMoved = true
                }

                // Check swipe-to-dismiss gesture on compact slider
                if (!isMasterMode) {
                    val swipeThreshold = dpToPx(38)
                    val swipedOff = if (prefs.isRightSide) dx > swipeThreshold else dx < -swipeThreshold
                    if (swipedOff) {
                        animateWidth(normalWidth)
                        onRequestDismiss()
                        return true
                    }
                }

                handleTouch(event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                animateWidth(normalWidth)

                // Check if user tapped the bottom icon region to mute/unmute
                val iconTouchArea = pillHeight * 0.35f
                val top = (height - pillHeight) / 2f
                val bottom = top + pillHeight
                val tappedNearIcon = (event.y in (bottom - iconTouchArea)..bottom)

                if (!hasMoved && tappedNearIcon) {
                    toggleMute()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animateWidth(normalWidth)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun toggleMute() {
        val currentVol = audioManager.getStreamVolume(streamType)
        if (currentVol > 0) {
            lastNonZeroVolume = currentVol
            audioManager.setStreamVolume(streamType, 0, 0)
        } else {
            val restoreVol = if (lastNonZeroVolume > 0) lastNonZeroVolume else (maxVolume / 2).coerceAtLeast(1)
            audioManager.setStreamVolume(streamType, restoreVol, 0)
        }
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        refreshVolume()
    }

    private fun handleTouch(touchY: Float) {
        val top = (height - pillHeight) / 2f
        val bottom = top + pillHeight
        val clampedY = touchY.coerceIn(top, bottom)

        val progress = 1f - ((clampedY - top) / pillHeight)
        val newVol = (progress * maxVolume).toInt().coerceIn(0, maxVolume)

        val currentVol = audioManager.getStreamVolume(streamType)
        if (newVol != currentVol) {
            audioManager.setStreamVolume(streamType, newVol, 0)
            if (newVol > 0) {
                lastNonZeroVolume = newVol
            }
            if (newVol == 0 || newVol == maxVolume) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        }

        animatedProgress = progress
        onVolumeChanged?.invoke((animatedProgress * 100).toInt())
        invalidate()
    }

    private fun animateWidth(target: Float) {
        widthAnimator?.cancel()
        widthAnimator = ValueAnimator.ofFloat(currentWidth, target).apply {
            duration = 160
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { va ->
                currentWidth = va.animatedValue as Float
                requestLayout()
                invalidate()
            }
            start()
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
