package com.rapido.assistant.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rapido.assistant.R
import com.rapido.assistant.audio.AudioBugFixer
import com.rapido.assistant.audio.VoiceAnnouncer
import com.rapido.assistant.data.DriverPreferences
import com.rapido.assistant.databinding.ActivitySimulatorBinding
import com.rapido.assistant.service.RapidoAccessibilityService

class SimulatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimulatorBinding
    private lateinit var preferences: DriverPreferences
    private var countdownTimer: CountDownTimer? = null

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

        preferences = DriverPreferences(this)

        val filter = IntentFilter().apply {
            addAction(RapidoAccessibilityService.ACTION_CLICK_ACCEPT)
            addAction(RapidoAccessibilityService.ACTION_CLICK_REJECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(simClickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(simClickReceiver, filter)
        }

        setupNavigation()
        setupScenarios()
        setupActions()

        // Load initial scenario (Prime Surge)
        loadScenario(145.0, 1.1, "Indiranagar 100ft Road", "Prime Surge")
    }

    private fun setupNavigation() {
        binding.btnBackToDashboard.setOnClickListener {
            finish()
        }
    }

    private fun setupScenarios() {
        // Scenario 1: Prime Surge
        binding.chipPrime.setOnClickListener {
            loadScenario(145.0, 1.1, "Indiranagar 100ft Road", "Prime Surge")
        }

        // Scenario 2: Money Trap
        binding.chipTrap.setOnClickListener {
            loadScenario(35.0, 4.8, "Whitefield Outer Ring", "Money Trap")
        }

        // Scenario 3: Marginal
        binding.chipMarginal.setOnClickListener {
            loadScenario(65.0, 2.9, "Koramangala 5th Block", "Marginal")
        }

        // Scenario 4: Rain Surge
        binding.chipRain.setOnClickListener {
            loadScenario(210.0, 0.8, "MG Road Metro", "Rain Surge")
        }

        // Custom parameters trigger
        binding.btnSimulateCustom.setOnClickListener {
            val fare = binding.etSimFare.text.toString().toDoubleOrNull() ?: 100.0
            val pickup = binding.etSimPickup.text.toString().toDoubleOrNull() ?: 1.5
            val drop = binding.etSimDrop.text.toString().ifBlank { "Destination Point" }
            loadScenario(fare, pickup, drop, "Custom Ride")
        }
    }

    private fun loadScenario(fare: Double, pickup: Double, drop: String, scenarioName: String) {
        // Update input fields
        binding.etSimFare.setText(fare.toInt().toString())
        binding.etSimPickup.setText(pickup.toString())
        binding.etSimDrop.setText(drop)

        // Update live card
        binding.tvMockFare.text = "₹${fare.toInt()}"
        val estTripDistance = 4.2
        val totalDistance = pickup + estTripDistance
        val ratePerKm = fare / totalDistance
        binding.tvMockRate.text = String.format("₹%.1f / km", ratePerKm)
        val mins = (pickup * 2.8).toInt().coerceAtLeast(1)
        binding.tvMockPickup.text = "$pickup km away ($mins mins)"
        binding.tvMockDrop.text = drop

        // AI Strategy Evaluation
        val meetsFare = fare >= preferences.minFare
        val meetsPickup = pickup <= preferences.maxPickupDistanceKm
        val isProfitable = meetsFare && meetsPickup

        val mintColor = ContextCompat.getColor(this, R.color.samsung_mint)
        val redColor = ContextCompat.getColor(this, R.color.hud_crimson_red)

        if (isProfitable) {
            val score = (88 + (ratePerKm * 1.5)).toInt().coerceIn(85, 99)
            binding.tvAiRecommendationBadge.text = "🛡️ AI EVALUATION: HIGH PROFIT · ACCEPT"
            binding.tvAiRecommendationBadge.setTextColor(mintColor)
            binding.tvAiRecommendationBadge.background.setTint(Color.parseColor("#3010B981"))
            binding.tvAiScore.text = "Score: $score/100"
            binding.tvAiScore.setTextColor(mintColor)
            binding.tvMockResultStatus.text = "Eligible for Auto-Accept: Deadhead ${pickup}km ≤ max ${preferences.maxPickupDistanceKm}km, Fare ₹${fare.toInt()} ≥ min ₹${preferences.minFare.toInt()}"
            binding.tvMockResultStatus.setTextColor(ContextCompat.getColor(this, R.color.hud_text_secondary))
        } else {
            val score = (fare / pickup * 2).toInt().coerceIn(12, 45)
            binding.tvAiRecommendationBadge.text = "🛑 AI EVALUATION: MONEY TRAP · SKIP"
            binding.tvAiRecommendationBadge.setTextColor(redColor)
            binding.tvAiRecommendationBadge.background.setTint(Color.parseColor("#30FF1744"))
            binding.tvAiScore.text = "Score: $score/100"
            binding.tvAiScore.setTextColor(redColor)
            binding.tvMockResultStatus.text = "Warning: Exceeds deadhead limit or below min fare threshold (Profit rate ₹${String.format("%.1f", ratePerKm)}/km)"
            binding.tvMockResultStatus.setTextColor(redColor)
        }

        // Broadcast to Accessibility Service / Floating HUD
        val simIntent = Intent(RapidoAccessibilityService.ACTION_SIMULATE_OFFER).apply {
            setPackage(packageName)
            putExtra("fare", fare)
            putExtra("pickup", pickup)
            putExtra("drop", drop)
        }
        sendBroadcast(simIntent)

        // Start 15s Countdown
        startCountdown(isProfitable)
        Toast.makeText(this, "Loaded: $scenarioName", Toast.LENGTH_SHORT).show()
    }

    private fun startCountdown(isProfitable: Boolean) {
        countdownTimer?.cancel()
        val totalMs = 15000L
        binding.progressBarCountdown.max = 100
        binding.progressBarCountdown.progress = 100

        countdownTimer = object : CountDownTimer(totalMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = ((millisUntilFinished.toFloat() / totalMs) * 100).toInt()
                binding.progressBarCountdown.progress = progress
                val secs = (millisUntilFinished / 1000) + 1
                binding.tvTimerCountdown.text = "⏱️ ${secs}s"

                // Auto-accept demonstration trigger at 11s if profitable and auto-accept is enabled
                if (isProfitable && preferences.autoAccept && secs == 12L) {
                    binding.tvMockResultStatus.text = "⚡ Auto-Accepting matching order in 1s..."
                }
            }

            override fun onFinish() {
                binding.progressBarCountdown.progress = 0
                binding.tvTimerCountdown.text = "⏱️ Expired"
                binding.tvMockResultStatus.text = "⚠️ Offer expired without driver response"
            }
        }.start()
    }

    private fun setupActions() {
        // Accept button
        binding.btnMockAccept.setOnClickListener {
            countdownTimer?.cancel()
            binding.tvTimerCountdown.text = "⏱️ ACCEPTED"
            binding.tvMockResultStatus.text = "🎉 RIDE ACCEPTED! Order dispatched to Captain."
            binding.tvMockResultStatus.setTextColor(ContextCompat.getColor(this, R.color.samsung_mint))
            Toast.makeText(this, "Order Accepted!", Toast.LENGTH_SHORT).show()
        }

        // Reject button
        binding.btnMockReject.setOnClickListener {
            countdownTimer?.cancel()
            binding.tvTimerCountdown.text = "⏱️ SKIPPED"
            binding.tvMockResultStatus.text = "❌ RIDE SKIPPED! Screen reset to standby."
            binding.tvMockResultStatus.setTextColor(ContextCompat.getColor(this, R.color.hud_crimson_red))
            Toast.makeText(this, "Order Skipped", Toast.LENGTH_SHORT).show()
        }

        // Push notification simulation
        binding.btnTriggerSimNotification.setOnClickListener {
            sendSimulatedNotification()
        }

        // Audio routing test
        binding.btnSimulateMapAudio.setOnClickListener {
            AudioBugFixer.fixNavigationAudio(this, showToast = true)
            VoiceAnnouncer.getInstance(this).speak("Bluetooth audio fix applied. Navigation guidance speech is clear.")
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
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle("Rapido Captain: New Ride Request")
            .setContentText("₹$fare • $pickup km • Drop: $drop")
            .setStyle(NotificationCompat.BigTextStyle().bigText("₹$fare • $pickup km away\nDrop to: $drop"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_app_icon, "Accept", pAccept)
            .build()

        nm.notify(9001, notification)
        Toast.makeText(this, "Dispatched Simulated Notification (Check Status Bar)", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        try {
            unregisterReceiver(simClickReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
