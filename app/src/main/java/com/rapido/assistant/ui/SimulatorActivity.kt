package com.rapido.assistant.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.rapido.assistant.R
import com.rapido.assistant.audio.AudioBugFixer
import com.rapido.assistant.databinding.ActivitySimulatorBinding
import com.rapido.assistant.service.RapidoAccessibilityService

class SimulatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimulatorBinding

    private val simClickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RapidoAccessibilityService.ACTION_CLICK_ACCEPT -> {
                    binding.btnMockAccept.performClick()
                }
                RapidoAccessibilityService.ACTION_CLICK_REJECT -> {
                    binding.btnMockReject.performClick()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimulatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filter = IntentFilter().apply {
            addAction(RapidoAccessibilityService.ACTION_CLICK_ACCEPT)
            addAction(RapidoAccessibilityService.ACTION_CLICK_REJECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(simClickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(simClickReceiver, filter)
        }

        setupPresets()
        setupActions()
    }

    private fun setupPresets() {
        // P1: Towards Home (Indiranagar, ₹120, 1.2 km -> Auto-Accept)
        binding.btnPresetTowards.setOnClickListener {
            binding.etSimFare.setText("120")
            binding.etSimPickup.setText("1.2")
            binding.etSimDrop.setText("Indiranagar 100ft Road")
            Toast.makeText(this, "Loaded Preset 1: Towards Home", Toast.LENGTH_SHORT).show()
        }

        // P2: Away from Home (Whitefield, ₹80, 2.5 km -> Auto-Reject)
        binding.btnPresetAway.setOnClickListener {
            binding.etSimFare.setText("80")
            binding.etSimPickup.setText("2.5")
            binding.etSimDrop.setText("Whitefield ITPL Main Road")
            Toast.makeText(this, "Loaded Preset 2: Away from Home", Toast.LENGTH_SHORT).show()
        }

        // P3: Too Far (Electronic City, ₹150, 7.5 km -> Auto-Reject)
        binding.btnPresetTooFar.setOnClickListener {
            binding.etSimFare.setText("150")
            binding.etSimPickup.setText("7.5")
            binding.etSimDrop.setText("Electronic City Phase 1")
            Toast.makeText(this, "Loaded Preset 3: Too Far", Toast.LENGTH_SHORT).show()
        }

        // P4: Low Fare (Koramangala, ₹25, 0.8 km -> Auto-Reject)
        binding.btnPresetLowFare.setOnClickListener {
            binding.etSimFare.setText("25")
            binding.etSimPickup.setText("0.8")
            binding.etSimDrop.setText("Koramangala 4th Block")
            Toast.makeText(this, "Loaded Preset 4: Low Fare", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupActions() {
        // Launch mock screen
        binding.btnLaunchSimScreen.setOnClickListener {
            val fare = binding.etSimFare.text.toString().trim()
            val pickup = binding.etSimPickup.text.toString().trim()
            val drop = binding.etSimDrop.text.toString().trim()

            binding.tvMockFare.text = "₹$fare"
            binding.tvMockPickup.text = "$pickup km away"
            binding.tvMockDrop.text = drop
            binding.tvMockResultStatus.text = "Screen active: Waiting for Accessibility Service..."
            binding.tvMockResultStatus.setTextColor(getColor(R.color.hud_amber))

            binding.panelSimulatorControls.visibility = View.GONE
            binding.mockRapidoScreen.visibility = View.VISIBLE

            // Broadcast offer to RapidoAutomationService
            val simIntent = Intent(RapidoAccessibilityService.ACTION_SIMULATE_OFFER).apply {
                putExtra("fare", fare.toDoubleOrNull() ?: 120.0)
                putExtra("pickup", pickup.toDoubleOrNull() ?: 1.2)
                putExtra("drop", drop)
            }
            sendBroadcast(simIntent)
        }

        // Exit mock screen
        binding.btnExitMockScreen.setOnClickListener {
            binding.mockRapidoScreen.visibility = View.GONE
            binding.panelSimulatorControls.visibility = View.VISIBLE
        }

        // Mock Accept Clicked
        binding.btnMockAccept.setOnClickListener {
            binding.tvMockResultStatus.text = "🎉 Ride Successfully ACCEPTED!"
            binding.tvMockResultStatus.setTextColor(getColor(R.color.hud_neon_green))
            Toast.makeText(this, "CLICK DETECTED: Ride Accepted", Toast.LENGTH_SHORT).show()
        }

        // Mock Reject Clicked
        binding.btnMockReject.setOnClickListener {
            binding.tvMockResultStatus.text = "❌ Ride REJECTED / SKIPPED"
            binding.tvMockResultStatus.setTextColor(getColor(R.color.hud_crimson_red))
            Toast.makeText(this, "CLICK DETECTED: Ride Rejected", Toast.LENGTH_SHORT).show()
        }

        // Trigger Notification Simulation
        binding.btnTriggerSimNotification.setOnClickListener {
            sendSimulatedNotification()
        }

        // Simulate Map Audio Bug
        binding.btnSimulateMapAudio.setOnClickListener {
            AudioBugFixer.fixNavigationAudio(this, showToast = true)
        }
    }

    private fun sendSimulatedNotification() {
        val channelId = "rapido_sim_channel"
        val channelName = "Rapido Simulator Pings"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Simulated Rapido ride alerts"
            }
            nm.createNotificationChannel(channel)
        }

        val fare = binding.etSimFare.text.toString().trim()
        val pickup = binding.etSimPickup.text.toString().trim()
        val drop = binding.etSimDrop.text.toString().trim()

        val acceptIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "com.rapido.assistant.MOCK_ACCEPT"
        }
        val pAccept = PendingIntent.getBroadcast(
            this,
            0,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Rapido Captain: New Ride Request")
            .setContentText("₹$fare • $pickup km • Drop: $drop")
            .setStyle(NotificationCompat.BigTextStyle().bigText("₹$fare • $pickup km away\nDrop to: $drop"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_input_add, "Accept", pAccept)
            .build()

        nm.notify(9001, notification)
        Toast.makeText(this, "Dispatched Simulated Notification (Check Status Bar)", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(simClickReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
