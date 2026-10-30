package com.antigravity.volumepanel

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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

@SuppressLint("ViewConstructor")
class MasterVolumeOverlayView(
    context: Context,
    private val audioManager: AudioManager,
    private val prefs: PreferencesManager,
    private val onInteraction: () -> Unit,
    private val onRequestDismiss: () -> Unit
) : FrameLayout(context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    var isExpanded = false
        private set

    // Root Containers
    private val backdropDim = View(context)
    private val compactRoot = FrameLayout(context)
    private val compactSlider = IOSControlSlider(
        context = context,
        audioManager = audioManager,
        streamType = AudioManager.STREAM_MUSIC,
        prefs = prefs,
        onInteraction = onInteraction,
        vibrator = vibrator
    )
    private val btnExpandDots = ImageView(context)

    // Master Dialog Elements
    private val masterCard = LinearLayout(context)
    private val slidersContainer = LinearLayout(context)
    private val soundModeSegmented = LinearLayout(context)
    private val masterSliders = mutableListOf<IOSControlSlider>()

    init {
        setupBackdrop()
        setupCompactLayout()
        setupMasterLayout()
        showCompactView()
    }

    private fun setupBackdrop() {
        backdropDim.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        backdropDim.setBackgroundColor(Color.parseColor("#4D000000")) // 30% black dim
        backdropDim.visibility = View.GONE
        backdropDim.setOnClickListener {
            onInteraction()
            collapseToCompactView()
        }
        addView(backdropDim)
    }

    private fun setupCompactLayout() {
        val width = dpToPx(prefs.sliderWidthDp)
        val height = dpToPx(prefs.sliderHeightDp)

        val containerParams = LayoutParams(width + dpToPx(16), height + dpToPx(38)).apply {
            gravity = if (prefs.isRightSide) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
        }
        compactRoot.layoutParams = containerParams

        // 3-Dots Button
        val dotSize = dpToPx(28)
        val dotParams = LayoutParams(dotSize, dotSize).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(2)
        }
        btnExpandDots.layoutParams = dotParams
        btnExpandDots.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_more_horiz))
        btnExpandDots.setColorFilter(Color.WHITE)

        val dotBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#30FFFFFF"))
            setStroke(dpToPx(1), Color.parseColor("#20FFFFFF"))
        }
        btnExpandDots.background = dotBg
        btnExpandDots.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        btnExpandDots.setOnClickListener {
            onInteraction()
            expandToMasterView()
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

        // Card Glass Effect
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(34).toFloat()
            setColor(Color.parseColor("#44101422")) // Deep Frosted Glass
            setStroke(dpToPx(1), Color.parseColor("#40FFFFFF")) // Specular border
        }
        masterCard.background = cardBg

        // Header (Title + Subtitle + Close Button)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(context).apply {
            text = "Audio Control Center"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val tvSub = TextView(context).apply {
            text = "Tap icons to mute streams"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
        }
        titleCol.addView(tvTitle)
        titleCol.addView(tvSub)

        val btnClose = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_volume_mute))
            setColorFilter(Color.WHITE)
            val closeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#28FFFFFF"))
            }
            background = closeBg
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            setOnClickListener {
                onInteraction()
                collapseToCompactView()
            }
        }

        header.addView(titleCol)
        header.addView(btnClose)
        masterCard.addView(header)

        // Sliders Row
        slidersContainer.orientation = LinearLayout.HORIZONTAL
        slidersContainer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(20)
            bottomMargin = dpToPx(20)
        }

        val streams = listOf(
            Triple("Media", AudioManager.STREAM_MUSIC, R.drawable.ic_volume_up),
            Triple("Ringtone", AudioManager.STREAM_RING, R.drawable.ic_bell),
            Triple("Alerts", AudioManager.STREAM_NOTIFICATION, R.drawable.ic_notifications),
            Triple("Alarm", AudioManager.STREAM_ALARM, R.drawable.ic_alarm)
        )

        masterSliders.clear()
        for ((label, streamType, iconRes) in streams) {
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dpToPx(14)
                }
            }

            val slider = IOSControlSlider(
                context = context,
                audioManager = audioManager,
                streamType = streamType,
                prefs = prefs,
                onInteraction = onInteraction,
                vibrator = vibrator,
                customIconRes = iconRes,
                isMasterMode = true
            ).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(52), dpToPx(160))
            }
            masterSliders.add(slider)

            val tvLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(8), 0, 0)
            }

            col.addView(slider)
            col.addView(tvLabel)
            slidersContainer.addView(col)
        }
        masterCard.addView(slidersContainer)

        // Sound Mode Segmented Switch
        setupSegmentedSoundBar()
        masterCard.addView(soundModeSegmented)

        masterCard.visibility = View.GONE
        addView(masterCard)
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
            setColor(Color.parseColor("#28161A26"))
            setStroke(dpToPx(1), Color.parseColor("#20FFFFFF"))
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
                        setColor(Color.parseColor(prefs.accentColorHex))
                    }
                    background = activeBg
                }

                val icon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                        marginEnd = dpToPx(6)
                    }
                    setImageDrawable(ContextCompat.getDrawable(context, iconRes))
                    setColorFilter(if (isSelected) Color.parseColor("#0F172A") else Color.WHITE)
                }

                val label = TextView(context).apply {
                    text = name
                    textSize = 13f
                    setTextColor(if (isSelected) Color.parseColor("#0F172A") else Color.WHITE)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                addView(icon)
                addView(label)

                setOnClickListener {
                    onInteraction()
                    try {
                        audioManager.ringerMode = mode
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
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

        refreshAll()
    }

    fun collapseToCompactView() {
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
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

/**
 * Super-polished iOS 18 Control Center Slider
 */
@SuppressLint("ViewConstructor")
class IOSControlSlider(
    context: Context,
    private val audioManager: AudioManager,
    val streamType: Int,
    private val prefs: PreferencesManager,
    private val onInteraction: () -> Unit,
    private val vibrator: Vibrator?,
    private val customIconRes: Int? = null,
    private val isMasterMode: Boolean = false
) : View(context) {

    private val pillRect = RectF()
    private val fillRect = RectF()
    private val clipPath = Path()

    private val normalWidth = dpToPx(if (isMasterMode) 52 else prefs.sliderWidthDp).toFloat()
    private val expandedWidth = normalWidth + dpToPx(8).toFloat()
    private var currentWidth = normalWidth
    private val pillHeight = dpToPx(if (isMasterMode) 160 else prefs.sliderHeightDp).toFloat()

    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38121624") // High-contrast frosted tint
    }

    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1).toFloat()
        color = Color.parseColor("#45FFFFFF") // Glowing rim
    }

    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor(prefs.accentColorHex)
    }

    private val percentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dpToPx(11).toFloat()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val icon: Drawable? = ContextCompat.getDrawable(context, customIconRes ?: R.drawable.ic_volume_up)
    private var maxVolume = 1
    private var animatedProgress = 0.5f
    private var progressAnimator: ValueAnimator? = null
    private var widthAnimator: ValueAnimator? = null

    init {
        refreshVolume()
    }

    fun refreshVolume() {
        maxVolume = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val currentVol = audioManager.getStreamVolume(streamType)
        val target = (currentVol.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(animatedProgress, target).apply {
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
        val w = (expandedWidth + dpToPx(8)).toInt()
        val h = (pillHeight + dpToPx(8)).toInt()
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

        // 1. Frosted Glass Capsule Background
        canvas.drawRoundRect(pillRect, radius, radius, glassBgPaint)

        // 2. Liquid Fill
        clipPath.reset()
        clipPath.addRoundRect(pillRect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        val fillHeight = pillHeight * animatedProgress
        val fillTop = bottom - fillHeight
        fillRect.set(left, fillTop, right, bottom)
        canvas.drawRect(fillRect, liquidPaint)

        canvas.restore()

        // 3. Specular Border
        canvas.drawRoundRect(pillRect, radius, radius, glassBorderPaint)

        // 4. Percentage Text (if in master mode)
        if (isMasterMode) {
            val percent = (animatedProgress * 100).toInt()
            val textY = top + dpToPx(20)
            percentTextPaint.color = if (fillTop < textY) Color.parseColor("#0F172A") else Color.WHITE
            canvas.drawText("$percent%", pillRect.centerX(), textY.toFloat(), percentTextPaint)
        }

        // 5. Speaker / Stream Icon with Dynamic Inversion
        val ic = icon ?: return
        val iconSize = dpToPx(22)
        val iconX = (pillRect.centerX() - iconSize / 2f).toInt()
        val iconY = (pillRect.bottom - dpToPx(24) - iconSize / 2f).toInt()

        ic.setBounds(iconX, iconY, iconX + iconSize, iconY + iconSize)

        val iconCenterY = iconY + iconSize / 2f
        val isCovered = fillTop < iconCenterY
        val iconColor = if (isCovered) Color.parseColor("#0F172A") else Color.WHITE

        DrawableCompat.setTint(ic, iconColor)
        ic.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        onInteraction()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                animateWidth(expandedWidth)
                handleTouch(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouch(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateWidth(normalWidth)
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

    private fun animateWidth(target: Float) {
        widthAnimator?.cancel()
        widthAnimator = ValueAnimator.ofFloat(currentWidth, target).apply {
            duration = 140
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { va ->
                currentWidth = va.animatedValue as Float
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
