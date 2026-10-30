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
        // Position
        if (prefs.isRightSide) {
            binding.rbRight.isChecked = true
        } else {
            binding.rbLeft.isChecked = true
        }

        // Blur
        binding.sliderBlur.value = prefs.blurRadius.coerceIn(10f, 60f)
        binding.tvBlurLabel.text = "Glass Blur Radius: ${prefs.blurRadius.toInt()} px"

        // Accent Colors
        when (prefs.accentColorHex.uppercase()) {
            "#06B6D4" -> binding.chipCyan.isChecked = true
            "#10B981" -> binding.chipGreen.isChecked = true
            "#F59E0B" -> binding.chipAmber.isChecked = true
            "#F43F5E" -> binding.chipRose.isChecked = true
            else -> binding.chipIndigo.isChecked = true
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

        // Position change
        binding.rgSide.setOnCheckedChangeListener { _, checkedId ->
            prefs.isRightSide = (checkedId == R.id.rbRight)
        }

        // Blur change
        binding.sliderBlur.addOnChangeListener { _, value, _ ->
            prefs.blurRadius = value
            binding.tvBlurLabel.text = "Glass Blur Radius: ${value.toInt()} px"
        }

        // Accent colors
        binding.chipGroupColors.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val colorHex = when (checkedIds.first()) {
                    R.id.chipCyan -> "#06B6D4"
                    R.id.chipGreen -> "#10B981"
                    R.id.chipAmber -> "#F59E0B"
                    R.id.chipRose -> "#F43F5E"
                    else -> "#6366F1"
                }
                prefs.accentColorHex = colorHex
            }
        }

        // Test button
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
        // Overlay permission
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

        // Accessibility permission
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
