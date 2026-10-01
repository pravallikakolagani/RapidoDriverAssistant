package com.rapido.assistant.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rapido.assistant.R
import com.rapido.assistant.audio.AudioBugFixer
import com.rapido.assistant.data.DriverPreferences
import com.rapido.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: DriverPreferences

    companion object {
        private const val PERM_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = DriverPreferences(this)

        requestRequiredPermissions()
        updateDeviceLocation()
        setupListeners()
        loadPreferences()
    }

    private fun requestRequiredPermissions() {
        val requiredPerms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requiredPerms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requiredPerms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            requiredPerms.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (requiredPerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, requiredPerms.toTypedArray(), PERM_REQUEST_CODE)
        }
    }

    private fun updateDeviceLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val lm = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                val loc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                loc?.let {
                    preferences.currentLat = it.latitude
                    preferences.currentLng = it.longitude
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
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
        binding.switchAudioBugFix.isChecked = preferences.autoFixAudioBug

        binding.etHomeAddress.setText(preferences.homeAddress)
    }

    private fun setupListeners() {
        // Sliders
        binding.sliderMinFare.addOnChangeListener { _, value, _ ->
            binding.tvMinFareValue.text = "₹${value.toInt()}"
        }

        binding.sliderMaxPickup.addOnChangeListener { _, value, _ ->
            binding.tvMaxPickupValue.text = String.format("%.1f km", value)
        }

        // Dedicated Hero Demo Card & Button
        binding.cardLaunchDemo.setOnClickListener {
            startActivity(Intent(this, SimulatorActivity::class.java))
        }
        binding.btnOpenSimulator.setOnClickListener {
            startActivity(Intent(this, SimulatorActivity::class.java))
        }

        // Quick Audio Fix Button in Header
        binding.btnQuickAudioFix.setOnClickListener {
            AudioBugFixer.fixNavigationAudio(this, showToast = true)
        }

        // Interactive Sensor & Permission Tiles (Tap to toggle / grant)
        binding.tileAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.tileOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        binding.tileNotification.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.tileLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    PERM_REQUEST_CODE
                )
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        // Save Settings
        binding.btnSaveSettings.setOnClickListener {
            preferences.minFare = binding.sliderMinFare.value.toDouble()
            preferences.maxPickupDistanceKm = binding.sliderMaxPickup.value.toDouble()
            preferences.autoAccept = binding.switchAutoAccept.isChecked
            preferences.voiceEnabled = binding.switchVoice.isChecked
            preferences.overlayEnabled = binding.switchOverlay.isChecked
            preferences.autoFixAudioBug = binding.switchAudioBugFix.isChecked
            preferences.homeAddress = binding.etHomeAddress.text.toString().trim()

            Toast.makeText(this, "Strategy Settings Saved Successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePermissionStatuses() {
        val mintColor = ContextCompat.getColor(this, R.color.samsung_mint)
        val amberColor = ContextCompat.getColor(this, R.color.samsung_amber)

        // Accessibility
        val isA11yEnabled = isAccessibilityServiceRunning()
        if (isA11yEnabled) {
            binding.tvAccessibilityStatus.text = "Active"
            binding.tvAccessibilityStatus.setTextColor(mintColor)
        } else {
            binding.tvAccessibilityStatus.text = "Tap to Enable"
            binding.tvAccessibilityStatus.setTextColor(amberColor)
        }

        // Overlay
        val isOverlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        if (isOverlayEnabled) {
            binding.tvOverlayStatus.text = "Allowed"
            binding.tvOverlayStatus.setTextColor(mintColor)
        } else {
            binding.tvOverlayStatus.text = "Tap to Allow"
            binding.tvOverlayStatus.setTextColor(amberColor)
        }

        // Notification
        val isNotificationEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (isNotificationEnabled) {
            binding.tvNotificationStatus.text = "Listening"
            binding.tvNotificationStatus.setTextColor(mintColor)
        } else {
            binding.tvNotificationStatus.text = "Tap to Enable"
            binding.tvNotificationStatus.setTextColor(amberColor)
        }

        // Location
        val isLocationEnabled = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (isLocationEnabled) {
            binding.tvLocationStatus.text = "Synced"
            binding.tvLocationStatus.setTextColor(mintColor)
        } else {
            binding.tvLocationStatus.text = "Tap to Grant"
            binding.tvLocationStatus.setTextColor(amberColor)
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
