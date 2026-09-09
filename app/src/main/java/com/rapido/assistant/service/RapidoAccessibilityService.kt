package com.rapido.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rapido.assistant.audio.AudioBugFixer
import com.rapido.assistant.audio.VoiceAnnouncer
import com.rapido.assistant.data.DriverPreferences
import com.rapido.assistant.engine.DecisionEngine
import com.rapido.assistant.model.DecisionType
import com.rapido.assistant.model.RideOffer

class RapidoAccessibilityService : AccessibilityService() {

    private lateinit var preferences: DriverPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingActionRunnable: Runnable? = null
    private var lastProcessedOfferHash: Int = 0
    private var lastProcessedTime: Long = 0

    private val manualActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_MANUAL_ACCEPT -> {
                    cancelPendingAction()
                    executeClickAction(ACTION_TYPE_ACCEPT)
                }
                ACTION_MANUAL_REJECT -> {
                    cancelPendingAction()
                    executeClickAction(ACTION_TYPE_REJECT)
                }
                ACTION_SIMULATE_OFFER -> {
                    val fare = intent.getDoubleExtra("fare", 120.0)
                    val pickup = intent.getDoubleExtra("pickup", 1.2)
                    val drop = intent.getStringExtra("drop") ?: "Indiranagar"
                    val offer = RideOffer(fare, pickup, drop)
                    processRideOffer(offer)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferences = DriverPreferences(this)
        Log.d(TAG, "RapidoAccessibilityService Connected")

        val filter = IntentFilter().apply {
            addAction(ACTION_MANUAL_ACCEPT)
            addAction(ACTION_MANUAL_REJECT)
            addAction(ACTION_SIMULATE_OFFER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(manualActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(manualActionReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val rootNode = rootInActiveWindow ?: return

        // 1. Check for "Go to map" navigation bug trigger
        if (preferences.autoFixAudioBug) {
            checkForMapNavigationTrigger(rootNode)
        }

        // 2. Check for incoming ride offer
        checkForRideOffer(rootNode)
    }

    private fun checkForMapNavigationTrigger(root: AccessibilityNodeInfo) {
        val mapNodes = root.findAccessibilityNodeInfosByText("Go to map")
        if (mapNodes.isNotEmpty()) {
            AudioBugFixer.fixNavigationAudio(this, showToast = true)
            return
        }

        val navigateNodes = root.findAccessibilityNodeInfosByText("Start Navigation")
        if (navigateNodes.isNotEmpty()) {
            AudioBugFixer.fixNavigationAudio(this, showToast = true)
        }
    }

    private fun checkForRideOffer(root: AccessibilityNodeInfo) {
        val nodeTexts = mutableListOf<String>()
        collectAllText(root, nodeTexts)
        val fullText = nodeTexts.joinToString("\n")

        // Quick heuristic check for ride offer presence
        val hasRideOfferIndicators = (fullText.contains("₹") || fullText.contains("Rs", ignoreCase = true)) &&
                (fullText.contains("km", ignoreCase = true) || fullText.contains("Pickup", ignoreCase = true)) &&
                (fullText.contains("Accept", ignoreCase = true) || fullText.contains("Ride", ignoreCase = true))

        if (!hasRideOfferIndicators) return

        val currentHash = fullText.hashCode()
        val now = System.currentTimeMillis()
        if (currentHash == lastProcessedOfferHash && (now - lastProcessedTime < 8000)) {
            // Already processed this offer
            return
        }

        val offer = DecisionEngine.parseRideOfferFromText(fullText) ?: return
        lastProcessedOfferHash = currentHash
        lastProcessedTime = now

        processRideOffer(offer)
    }

    private fun processRideOffer(offer: RideOffer) {
        val decision = DecisionEngine.evaluate(offer, preferences)
        Log.d(TAG, "Ride Evaluated: ${decision.decision} - ${decision.reason}")

        // 1. Announce via Voice (Bluetooth Helmet)
        if (preferences.voiceEnabled) {
            VoiceAnnouncer.getInstance(this).announceRide(offer, decision)
        }

        // 2. Show HUD Overlay
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

        // 3. Automated programmatic click execution
        cancelPendingAction()

        if (decision.decision == DecisionType.ACCEPT && preferences.autoAccept) {
            val delayMs = (preferences.countdownSeconds * 1000).toLong()
            pendingActionRunnable = Runnable {
                executeClickAction(ACTION_TYPE_ACCEPT)
            }
            mainHandler.postDelayed(pendingActionRunnable!!, delayMs)
        } else if (decision.decision != DecisionType.ACCEPT && preferences.autoReject) {
            val delayMs = (preferences.countdownSeconds * 1000).toLong()
            pendingActionRunnable = Runnable {
                executeClickAction(ACTION_TYPE_REJECT)
            }
            mainHandler.postDelayed(pendingActionRunnable!!, delayMs)
        }
    }

    private fun executeClickAction(actionType: String) {
        val rootNode = rootInActiveWindow ?: return
        val targetLabels = if (actionType == ACTION_TYPE_ACCEPT) {
            listOf("ACCEPT", "Accept", "ACCEPT RIDE", "Confirm")
        } else {
            listOf("REJECT", "Reject", "SKIP", "Skip", "DECLINE")
        }

        var clicked = false
        if (rootNode != null) {
            for (label in targetLabels) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(label)
                for (node in nodes) {
                    if (performClickOnNodeOrParent(node)) {
                        Log.d(TAG, "Successfully clicked $actionType button with label '$label'")
                        clicked = true
                        break
                    }
                }
                if (clicked) break
            }
        }

        // Broadcast to notify simulator activity
        val simClickAction = if (actionType == ACTION_TYPE_ACCEPT) ACTION_CLICK_ACCEPT else ACTION_CLICK_REJECT
        sendBroadcast(Intent(simClickAction))
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    private fun cancelPendingAction() {
        pendingActionRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingActionRunnable = null
    }

    private fun collectAllText(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            texts.add(text)
        }
        val contentDesc = node.contentDescription?.toString()?.trim()
        if (!contentDesc.isNullOrEmpty() && contentDesc != text) {
            texts.add(contentDesc)
        }
        for (i in 0 until node.childCount) {
            collectAllText(node.getChild(i), texts)
        }
    }

    override fun onInterrupt() {
        cancelPendingAction()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelPendingAction()
        try {
            unregisterReceiver(manualActionReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }

    companion object {
        private const val TAG = "RapidoAccessibility"
        const val ACTION_MANUAL_ACCEPT = "com.rapido.assistant.action.MANUAL_ACCEPT"
        const val ACTION_MANUAL_REJECT = "com.rapido.assistant.action.MANUAL_REJECT"
        const val ACTION_SIMULATE_OFFER = "com.rapido.assistant.action.SIMULATE_OFFER"
        const val ACTION_CLICK_ACCEPT = "com.rapido.assistant.action.CLICK_ACCEPT"
        const val ACTION_CLICK_REJECT = "com.rapido.assistant.action.CLICK_REJECT"

        private const val ACTION_TYPE_ACCEPT = "ACCEPT"
        private const val ACTION_TYPE_REJECT = "REJECT"
    }
}
