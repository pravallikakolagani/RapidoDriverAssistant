package com.rapido.assistant.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rapido.assistant.audio.VoiceAnnouncer
import com.rapido.assistant.data.DriverPreferences
import com.rapido.assistant.engine.DecisionEngine
import com.rapido.assistant.model.DecisionType

class RapidoNotificationService : NotificationListenerService() {

    private lateinit var preferences: DriverPreferences
    private var lastNotificationTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        preferences = DriverPreferences(this)
        Log.d(TAG, "RapidoNotificationService Started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName
        val isTargetApp = pkg.contains("rapido", ignoreCase = true) ||
                pkg == packageName ||
                pkg.contains("simulator", ignoreCase = true)

        if (!isTargetApp) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val fullContent = "$title\n$text\n$bigText"

        val hasRidePings = fullContent.contains("Ride", ignoreCase = true) ||
                fullContent.contains("₹") ||
                fullContent.contains("Rs", ignoreCase = true)

        if (!hasRidePings) return

        val now = System.currentTimeMillis()
        if (now - lastNotificationTime < 4000) return
        lastNotificationTime = now

        Log.d(TAG, "Incoming Ride Notification: $fullContent")

        val offer = DecisionEngine.parseRideOfferFromText(fullContent) ?: return
        val decision = DecisionEngine.evaluate(offer, preferences)

        // 1. Wake up device if screen is off (Pocket / Lockscreen Mode)
        wakeScreenIfNeeded()

        // 2. Announce ride via helmet Bluetooth
        if (preferences.voiceEnabled) {
            VoiceAnnouncer.getInstance(this).announceRide(offer, decision)
        }

        // 3. Show Floating HUD
        if (preferences.overlayEnabled) {
            val hudIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_OFFER
                putExtra(OverlayService.EXTRA_FARE, offer.fare)
                putExtra(OverlayService.EXTRA_PICKUP_KM, offer.pickupDistanceKm)
                putExtra(OverlayService.EXTRA_DROP_LOC, offer.dropLocation)
                putExtra(OverlayService.EXTRA_IS_TOWARDS, offer.isTowardsHome)
                putExtra(OverlayService.EXTRA_BEARING, offer.bearingAngle)
                putExtra(OverlayService.EXTRA_COMPASS, offer.compassDirection)
                putExtra(OverlayService.EXTRA_DECISION, decision.decision.name)
                putExtra(OverlayService.EXTRA_REASON, decision.reason)
                putExtra(OverlayService.EXTRA_COUNTDOWN, preferences.countdownSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(hudIntent)
            } else {
                startService(hudIntent)
            }
        }

        // 4. Background Direct Auto-Accept via Notification Action if available
        if (decision.decision == DecisionType.ACCEPT && preferences.autoAccept) {
            notification.actions?.forEach { action ->
                val actionTitle = action.title?.toString() ?: ""
                if (actionTitle.contains("Accept", ignoreCase = true)) {
                    try {
                        Log.d(TAG, "Triggering Notification Accept Action directly")
                        action.actionIntent.send()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error triggering notification accept action: ${e.message}")
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenIfNeeded() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                pm.isInteractive
            } else {
                pm.isScreenOn
            }

            if (!isScreenOn) {
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "RapidoAssistant:RideWakeLock"
                )
                wakeLock.acquire(5000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "RapidoNotificationSvc"
    }
}
