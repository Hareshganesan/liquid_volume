package com.antigravity.volumepanel

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.antigravity.volumepanel.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private var localOverlayController: VolumeOverlayController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        localOverlayController = VolumeOverlayController(this)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun setupUI() {
        // Screen Side
        if (prefs.isRightSide) {
            binding.rbRight.isChecked = true
        } else {
            binding.rbLeft.isChecked = true
        }

        // Vertical Offset
        val vPos = prefs.verticalPositionPercent.coerceIn(10f, 90f)
        binding.sliderVerticalOffset.valueFrom = 10f
        binding.sliderVerticalOffset.valueTo = 90f
        binding.sliderVerticalOffset.stepSize = 1f
        binding.sliderVerticalOffset.value = vPos
        binding.tvVerticalOffsetLabel.text = "Vertical Position: ${vPos.toInt()}%"

        // Length (Height)
        val length = prefs.sliderHeightDp.coerceIn(130, 240)
        binding.sliderLength.valueFrom = 130f
        binding.sliderLength.valueTo = 240f
        binding.sliderLength.stepSize = 5f
        binding.sliderLength.value = length.toFloat()
        binding.tvLengthLabel.text = "Slider Length: $length dp"

        // Width (Thickness)
        val width = prefs.sliderWidthDp.coerceIn(36, 56)
        binding.sliderWidth.valueFrom = 36f
        binding.sliderWidth.valueTo = 56f
        binding.sliderWidth.stepSize = 2f
        binding.sliderWidth.value = width.toFloat()
        binding.tvWidthLabel.text = "Slider Thickness: $width dp"

        // Timeout
        val timeoutSec = (prefs.timeoutMs / 1000f).coerceIn(1.0f, 6.0f)
        binding.sliderTimeout.valueFrom = 1.0f
        binding.sliderTimeout.valueTo = 6.0f
        binding.sliderTimeout.stepSize = 0.5f
        binding.sliderTimeout.value = timeoutSec
        binding.tvTimeoutLabel.text = "Auto-Dismiss Timeout: ${String.format("%.1f", timeoutSec)} seconds"

        // Blur
        val blur = prefs.blurRadius.coerceIn(15f, 60f)
        binding.sliderBlur.valueFrom = 15f
        binding.sliderBlur.valueTo = 60f
        binding.sliderBlur.stepSize = 5f
        binding.sliderBlur.value = blur
        binding.tvBlurLabel.text = "Glass Background Blur: ${blur.toInt()} px"

        // Accent Theme Chips
        when (prefs.accentColorHex.uppercase()) {
            "#4F46E5", "#6366F1" -> binding.chipIndigo.isChecked = true
            "#06B6D4" -> binding.chipCyan.isChecked = true
            "#10B981" -> binding.chipEmerald.isChecked = true
            "#F43F5E" -> binding.chipRose.isChecked = true
            else -> binding.chipWhite.isChecked = true
        }
    }

    private fun setupListeners() {
        // Permissions
        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Side Change
        binding.rgSide.setOnCheckedChangeListener { _, checkedId ->
            prefs.isRightSide = (checkedId == R.id.rbRight)
        }

        // Vertical Offset
        binding.sliderVerticalOffset.addOnChangeListener { _, value, _ ->
            prefs.verticalPositionPercent = value
            binding.tvVerticalOffsetLabel.text = "Vertical Position: ${value.toInt()}%"
        }

        // Length
        binding.sliderLength.addOnChangeListener { _, value, _ ->
            prefs.sliderHeightDp = value.toInt()
            binding.tvLengthLabel.text = "Slider Length: ${value.toInt()} dp"
        }

        // Width
        binding.sliderWidth.addOnChangeListener { _, value, _ ->
            prefs.sliderWidthDp = value.toInt()
            binding.tvWidthLabel.text = "Slider Thickness: ${value.toInt()} dp"
        }

        // Timeout
        binding.sliderTimeout.addOnChangeListener { _, value, _ ->
            prefs.timeoutMs = (value * 1000L).toLong()
            binding.tvTimeoutLabel.text = "Auto-Dismiss Timeout: ${String.format("%.1f", value)} seconds"
        }

        // Blur
        binding.sliderBlur.addOnChangeListener { _, value, _ ->
            prefs.blurRadius = value
            binding.tvBlurLabel.text = "Glass Background Blur: ${value.toInt()} px"
        }

        // Colors
        binding.chipGroupColors.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val colorHex = when (checkedIds.first()) {
                    R.id.chipIndigo -> "#6366F1"
                    R.id.chipCyan -> "#06B6D4"
                    R.id.chipEmerald -> "#10B981"
                    R.id.chipRose -> "#F43F5E"
                    else -> "#F8FAFC"
                }
                prefs.accentColorHex = colorHex
            }
        }

        // Live Test Button
        binding.btnTestPanel.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant 'Draw Over Apps' permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (VolumeAccessibilityService.instance != null) {
                VolumeAccessibilityService.triggerTest(0)
            } else {
                localOverlayController?.show(0)
            }
        }
    }

    private fun updatePermissionStates() {
        val hasOverlay = Settings.canDrawOverlays(this)
        if (hasOverlay) {
            binding.btnGrantOverlay.text = getString(R.string.granted)
            binding.btnGrantOverlay.isEnabled = false
            binding.btnGrantOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            binding.btnGrantOverlay.text = getString(R.string.grant_permission)
            binding.btnGrantOverlay.isEnabled = true
            binding.btnGrantOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        }

        val hasAccessibility = isAccessibilityServiceEnabled()
        if (hasAccessibility) {
            binding.btnGrantAccessibility.text = getString(R.string.granted)
            binding.btnGrantAccessibility.isEnabled = false
            binding.btnGrantAccessibility.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            binding.btnGrantAccessibility.text = getString(R.string.grant_permission)
            binding.btnGrantAccessibility.isEnabled = true
            binding.btnGrantAccessibility.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }
}
