package com.rapido.assistant.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rapido.assistant.R
import com.rapido.assistant.audio.AudioBugFixer
import com.rapido.assistant.data.DriverPreferences
import com.rapido.assistant.databinding.ActivityMainBinding
import com.rapido.assistant.service.RapidoAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: DriverPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = DriverPreferences(this)

        setupListeners()
        loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun loadPreferences() {
        binding.sliderMinFare.value = preferences.minFare.toFloat().coerceIn(20f, 200f)
        binding.tvMinFareValue.text = "₹${preferences.minFare.toInt()}"

        binding.sliderMaxPickup.value = preferences.maxPickupDistanceKm.toFloat().coerceIn(1f, 10f)
        binding.tvMaxPickupValue.text = "${preferences.maxPickupDistanceKm} km"

        binding.switchAutoAccept.isChecked = preferences.autoAccept
        binding.switchVoice.isChecked = preferences.voiceEnabled
        binding.switchOverlay.isChecked = preferences.overlayEnabled
        binding.switchSkipNight.isChecked = preferences.skipNightRides
        binding.switchAudioBugFix.isChecked = preferences.autoFixAudioBug

        binding.etHomeAddress.setText(preferences.homeAddress)
    }

    private fun setupListeners() {
        // Slider listeners
        binding.sliderMinFare.addOnChangeListener { _, value, _ ->
            binding.tvMinFareValue.text = "₹${value.toInt()}"
        }

        binding.sliderMaxPickup.addOnChangeListener { _, value, _ ->
            binding.tvMaxPickupValue.text = String.format("%.1f km", value)
        }

        // Permission Buttons
        binding.btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnEnableOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        binding.btnEnableNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        // Save Settings
        binding.btnSaveSettings.setOnClickListener {
            preferences.minFare = binding.sliderMinFare.value.toDouble()
            preferences.maxPickupDistanceKm = binding.sliderMaxPickup.value.toDouble()
            preferences.autoAccept = binding.switchAutoAccept.isChecked
            preferences.voiceEnabled = binding.switchVoice.isChecked
            preferences.overlayEnabled = binding.switchOverlay.isChecked
            preferences.skipNightRides = binding.switchSkipNight.isChecked
            preferences.autoFixAudioBug = binding.switchAudioBugFix.isChecked
            preferences.homeAddress = binding.etHomeAddress.text.toString().trim()

            Toast.makeText(this, "Settings Saved Successfully!", Toast.LENGTH_SHORT).show()
        }

        // Open Simulator
        binding.btnOpenSimulator.setOnClickListener {
            startActivity(Intent(this, SimulatorActivity::class.java))
        }
        binding.btnQuickLaunchSimulator.setOnClickListener {
            startActivity(Intent(this, SimulatorActivity::class.java))
        }

        // Test Audio Bug Fix
        binding.btnTestAudioFix.setOnClickListener {
            AudioBugFixer.fixNavigationAudio(this, showToast = true)
        }
    }

    private fun updatePermissionStatuses() {
        // Accessibility
        val isA11yEnabled = isAccessibilityServiceRunning()
        if (isA11yEnabled) {
            binding.tvAccessibilityStatus.text = "ACTIVE (Running & Scanning)"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.hud_neon_green))
            binding.btnEnableAccessibility.isEnabled = false
            binding.btnEnableAccessibility.text = "ACTIVE"
        } else {
            binding.tvAccessibilityStatus.text = "DISABLED (Tap ENABLE to activate)"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.hud_crimson_red))
            binding.btnEnableAccessibility.isEnabled = true
            binding.btnEnableAccessibility.text = "ENABLE"
        }

        // Overlay
        val isOverlayEnabled = Settings.canDrawOverlays(this)
        if (isOverlayEnabled) {
            binding.tvOverlayStatus.text = "ACTIVE (HUD Allowed)"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.hud_neon_green))
            binding.btnEnableOverlay.isEnabled = false
            binding.btnEnableOverlay.text = "ACTIVE"
        } else {
            binding.tvOverlayStatus.text = "DISABLED (Tap ENABLE to allow)"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.hud_crimson_red))
            binding.btnEnableOverlay.isEnabled = true
            binding.btnEnableOverlay.text = "ENABLE"
        }

        // Notification
        val isNotificationEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (isNotificationEnabled) {
            binding.tvNotificationStatus.text = "ACTIVE (Listening for pings)"
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.hud_neon_green))
            binding.btnEnableNotification.isEnabled = false
            binding.btnEnableNotification.text = "ACTIVE"
        } else {
            binding.tvNotificationStatus.text = "DISABLED (Tap ENABLE for background alerts)"
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.hud_crimson_red))
            binding.btnEnableNotification.isEnabled = true
            binding.btnEnableNotification.text = "ENABLE"
        }
    }

    private fun isAccessibilityServiceRunning(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServicesSetting.contains(packageName, ignoreCase = true)
    }
}
