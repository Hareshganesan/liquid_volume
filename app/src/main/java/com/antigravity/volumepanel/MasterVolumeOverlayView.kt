package com.antigravity.volumepanel

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
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

@SuppressLint("ViewConstructor")
class MasterVolumeOverlayView(
    context: Context,
    private val audioManager: AudioManager,
    private val prefs: PreferencesManager,
    private val onInteraction: () -> Unit,
    private val onRequestDismiss: () -> Unit
) : FrameLayout(context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Modes
    var isExpanded = false
        private set

    // Views
    private val compactContainer = FrameLayout(context)
    private val compactSlider = IOSCapsuleSliderView(
        context = context,
        audioManager = audioManager,
        streamType = AudioManager.STREAM_MUSIC,
        prefs = prefs,
        onInteraction = onInteraction,
        vibrator = vibrator
    )
    private val btnExpand = ImageView(context)

    // Master Panel Views
    private val masterCard = LinearLayout(context)
    private val slidersRow = LinearLayout(context)
    private val soundModeRow = LinearLayout(context)

    private val masterSliders = mutableListOf<IOSCapsuleSliderView>()

    init {
        setupCompactMode()
        setupMasterMode()
        showCompactMode()
    }

    private fun setupCompactMode() {
        val pillWidth = dpToPx(46)
        val pillHeight = dpToPx(190)

        val containerParams = LayoutParams(pillWidth + dpToPx(16), pillHeight + dpToPx(36)).apply {
            gravity = if (prefs.isRightSide) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
        }
        compactContainer.layoutParams = containerParams

        // 3-Dots Expand Button at top
        val btnSize = dpToPx(28)
        val btnParams = LayoutParams(btnSize, btnSize).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(2)
        }
        btnExpand.layoutParams = btnParams
        btnExpand.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_more_horiz))
        btnExpand.setColorFilter(Color.WHITE)
        btnExpand.setBackgroundResource(android.R.color.transparent)
        btnExpand.setOnClickListener {
            onInteraction()
            expandToMasterMode()
        }

        // The Compact Pill Slider
        val sliderParams = LayoutParams(LayoutParams.MATCH_PARENT, pillHeight).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        compactSlider.layoutParams = sliderParams

        compactContainer.addView(compactSlider)
        compactContainer.addView(btnExpand)
        addView(compactContainer)
    }

    private fun setupMasterMode() {
        masterCard.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        masterCard.orientation = LinearLayout.VERTICAL
        masterCard.setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(18))

        // Frosted Card Glass Background
        val cardBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(32).toFloat()
            setColor(Color.parseColor("#4A131620")) // Deep glass tint
            setStroke(dpToPx(1), Color.parseColor("#38FFFFFF")) // Glowing glass rim
        }
        masterCard.background = cardBg

        // Header Row (Title + Close Button)
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = TextView(context).apply {
            text = "Audio Control"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_volume_mute))
            setColorFilter(Color.parseColor("#94A3B8"))
            setOnClickListener {
                onInteraction()
                collapseToCompactMode()
            }
        }

        headerRow.addView(tvTitle)
        headerRow.addView(btnClose)
        masterCard.addView(headerRow)

        // 4 Sliders Row
        slidersRow.orientation = LinearLayout.HORIZONTAL
        slidersRow.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(16)
            bottomMargin = dpToPx(16)
        }

        val streams = listOf(
            Triple("Media", AudioManager.STREAM_MUSIC, R.drawable.ic_volume_up),
            Triple("Ring", AudioManager.STREAM_RING, R.drawable.ic_bell),
            Triple("Notif", AudioManager.STREAM_NOTIFICATION, R.drawable.ic_notifications),
            Triple("Alarm", AudioManager.STREAM_ALARM, R.drawable.ic_alarm)
        )

        masterSliders.clear()
        for ((label, streamType, iconRes) in streams) {
            val streamColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dpToPx(12)
                }
            }

            val slider = IOSCapsuleSliderView(
                context = context,
                audioManager = audioManager,
                streamType = streamType,
                prefs = prefs,
                onInteraction = onInteraction,
                vibrator = vibrator,
                customIconRes = iconRes
            ).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(170))
            }
            masterSliders.add(slider)

            val tvLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(6), 0, 0)
            }

            streamColumn.addView(slider)
            streamColumn.addView(tvLabel)
            slidersRow.addView(streamColumn)
        }

        masterCard.addView(slidersRow)

        // Sound Mode Selector Row (Ring, Vibrate, Silent)
        setupSoundModeSelector()
        masterCard.addView(soundModeRow)

        masterCard.visibility = View.GONE
        addView(masterCard)
    }

    private fun setupSoundModeSelector() {
        soundModeRow.orientation = LinearLayout.HORIZONTAL
        soundModeRow.gravity = Gravity.CENTER
        soundModeRow.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(6)
        }

        val modes = listOf(
            Triple("Ring", AudioManager.RINGER_MODE_NORMAL, R.drawable.ic_bell),
            Triple("Vibrate", AudioManager.RINGER_MODE_VIBRATE, R.drawable.ic_vibrate),
            Triple("Mute", AudioManager.RINGER_MODE_SILENT, R.drawable.ic_volume_mute)
        )

        for ((name, mode, iconRes) in modes) {
            val modeBtn = TextView(context).apply {
                text = name
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dpToPx(6)
                }

                val currentRinger = audioManager.ringerMode
                val isSelected = (currentRinger == mode)
                background = createPillDrawable(isSelected)

                setOnClickListener {
                    onInteraction()
                    try {
                        audioManager.ringerMode = mode
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } catch (_: Exception) {}
                    setupSoundModeSelector() // Refresh buttons
                    refreshAllSliders()
                }
            }
            soundModeRow.addView(modeBtn)
        }
    }

    private fun createPillDrawable(selected: Boolean): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(14).toFloat()
            if (selected) {
                setColor(Color.parseColor(prefs.accentColorHex))
            } else {
                setColor(Color.parseColor("#25FFFFFF"))
                setStroke(dpToPx(1), Color.parseColor("#30FFFFFF"))
            }
        }
    }

    fun showCompactMode() {
        isExpanded = false
        masterCard.visibility = View.GONE
        compactContainer.visibility = View.VISIBLE
        compactSlider.refreshVolume()
    }

    fun expandToMasterMode() {
        isExpanded = true
        compactContainer.visibility = View.GONE
        masterCard.visibility = View.VISIBLE
        masterCard.scaleX = 0.85f
        masterCard.scaleY = 0.85f
        masterCard.alpha = 0f

        masterCard.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        refreshAllSliders()
    }

    fun collapseToCompactMode() {
        isExpanded = false
        masterCard.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                masterCard.visibility = View.GONE
                compactContainer.visibility = View.VISIBLE
                compactSlider.refreshVolume()
            }
            .start()
    }

    fun handleHardwareVolumeStep(direction: Int) {
        // Intelligently adjusts the active stream
        audioManager.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

        if (isExpanded) {
            refreshAllSliders()
        } else {
            compactSlider.refreshVolume()
        }
    }

    fun refreshAllSliders() {
        compactSlider.refreshVolume()
        for (s in masterSliders) {
            s.refreshVolume()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

/**
 * High-performance iOS 18 Styled Liquid Glass Capsule Slider
 */
@SuppressLint("ViewConstructor")
class IOSCapsuleSliderView(
    context: Context,
    private val audioManager: AudioManager,
    val streamType: Int,
    private val prefs: PreferencesManager,
    private val onInteraction: () -> Unit,
    private val vibrator: Vibrator?,
    private val customIconRes: Int? = null
) : View(context) {

    private val normalWidth = dpToPx(44).toFloat()
    private val expandedWidth = dpToPx(54).toFloat()
    private var currentPillWidth = normalWidth
    private val pillHeight = dpToPx(170).toFloat()

    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#34141722") // Frosted glass dark
    }

    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1).toFloat()
        color = Color.parseColor("#45FFFFFF") // Specular border
    }

    private val liquidFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F8FAFC") // Pure white milk glass fill
    }

    private val pillRect = RectF()
    private val fillRect = RectF()
    private val clipPath = Path()

    private val icon: Drawable? = ContextCompat.getDrawable(context, customIconRes ?: R.drawable.ic_volume_up)
    private var maxVolume = 1
    private var animatedProgress = 0.5f
    private var progressAnimator: ValueAnimator? = null
    private var expandAnimator: ValueAnimator? = null

    init {
        refreshVolume()
    }

    fun refreshVolume() {
        maxVolume = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val currentVol = audioManager.getStreamVolume(streamType)
        val newProgress = currentVol.toFloat() / maxVolume.toFloat()

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(animatedProgress, newProgress.coerceIn(0f, 1f)).apply {
            duration = 140
            interpolator = OvershootInterpolator(0.9f)
            addUpdateListener { va ->
                animatedProgress = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (expandedWidth + dpToPx(10)).toInt()
        val h = (pillHeight + dpToPx(10)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = (width - currentPillWidth) / 2f
        val top = (height - pillHeight) / 2f
        val right = left + currentPillWidth
        val bottom = top + pillHeight
        val radius = currentPillWidth / 2f

        pillRect.set(left, top, right, bottom)

        // 1. Draw Outer Glass Pill Background
        canvas.drawRoundRect(pillRect, radius, radius, glassBgPaint)

        // 2. Liquid Fill
        clipPath.reset()
        clipPath.addRoundRect(pillRect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        val fillHeight = pillHeight * animatedProgress
        val fillTop = bottom - fillHeight
        fillRect.set(left, fillTop, right, bottom)
        canvas.drawRect(fillRect, liquidFillPaint)

        canvas.restore()

        // 3. Glowing Glass Rim
        canvas.drawRoundRect(pillRect, radius, radius, glassBorderPaint)

        // 4. Dynamic Icon (with Inversion)
        val ic = icon ?: return
        val iconSize = dpToPx(24)
        val iconX = (pillRect.centerX() - iconSize / 2f).toInt()
        val iconY = (pillRect.bottom - dpToPx(26) - iconSize / 2f).toInt()

        ic.setBounds(iconX, iconY, iconX + iconSize, iconY + iconSize)

        val iconCenterY = iconY + iconSize / 2f
        val isCoveredByLiquid = fillTop < iconCenterY
        val iconColor = if (isCoveredByLiquid) Color.parseColor("#0F172A") else Color.WHITE

        DrawableCompat.setTint(ic, iconColor)
        ic.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        onInteraction()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                animatePillWidth(expandedWidth)
                handleTouch(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouch(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animatePillWidth(normalWidth)
                return true
            }
        }
        return super.onTouchEvent(event)
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
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }

        animatedProgress = progress
        invalidate()
    }

    private fun animatePillWidth(target: Float) {
        expandAnimator?.cancel()
        expandAnimator = ValueAnimator.ofFloat(currentPillWidth, target).apply {
            duration = 160
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { va ->
                currentPillWidth = va.animatedValue as Float
                requestLayout()
                invalidate()
            }
            start()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
