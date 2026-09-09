package com.rapido.assistant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.rapido.assistant.R

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var hudView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var countDownTimer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_SHOW_OFFER -> {
                val fare = intent.getDoubleExtra(EXTRA_FARE, 0.0)
                val pickupKm = intent.getDoubleExtra(EXTRA_PICKUP_KM, 0.0)
                val dropLoc = intent.getStringExtra(EXTRA_DROP_LOC) ?: "Unknown"
                val isTowards = intent.getBooleanExtra(EXTRA_IS_TOWARDS, true)
                val bearing = intent.getFloatExtra(EXTRA_BEARING, 0f)
                val compass = intent.getStringExtra(EXTRA_COMPASS) ?: "N"
                val decision = intent.getStringExtra(EXTRA_DECISION) ?: "ACCEPT"
                val countdownSecs = intent.getIntExtra(EXTRA_COUNTDOWN, 5)

                showOrUpdateHud(fare, pickupKm, dropLoc, isTowards, bearing, compass, decision, countdownSecs)
            }
            ACTION_HIDE_HUD -> {
                hideHud()
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "rapido_hud_channel"
        val channelName = "Rapido Assistant HUD"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active floating HUD overlay for Rapido rides"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rapido Assistant HUD Active")
            .setContentText("Monitoring rides in real-time")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOrUpdateHud(
        fare: Double,
        pickupKm: Double,
        dropLoc: String,
        isTowards: Boolean,
        bearing: Float,
        compass: String,
        decision: String,
        countdownSecs: Int
    ) {
        if (!Settings.canDrawOverlays(this)) return

        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        try {
            if (hudView == null) {
                val themedContext = ContextThemeWrapper(this, R.style.Theme_RapidoDriverAssistant)
                hudView = LayoutInflater.from(themedContext).inflate(R.layout.overlay_hud, null)

                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = 0
                    y = 120
                }

                setupDragAndControls(hudView!!, params!!)
                windowManager?.addView(hudView, params)
            }

            updateHudContents(fare, pickupKm, dropLoc, isTowards, bearing, compass, decision, countdownSecs)
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to inflate or show HUD overlay: ${e.message}", e)
        }
    }

    private fun updateHudContents(
        fare: Double,
        pickupKm: Double,
        dropLoc: String,
        isTowards: Boolean,
        bearing: Float,
        compass: String,
        decision: String,
        countdownSecs: Int
    ) {
        val root = hudView ?: return

        val tvDirection = root.findViewById<TextView>(R.id.tvDirection)
        val tvBearingCompass = root.findViewById<TextView>(R.id.tvBearingCompass)
        val tvFare = root.findViewById<TextView>(R.id.tvFare)
        val tvPickupDistance = root.findViewById<TextView>(R.id.tvPickupDistance)
        val tvDropLocation = root.findViewById<TextView>(R.id.tvDropLocation)
        val tvDecisionStatus = root.findViewById<TextView>(R.id.tvDecisionStatus)
        val pbCountdown = root.findViewById<ProgressBar>(R.id.pbCountdown)

        tvFare.text = "₹${fare.toInt()}"
        tvPickupDistance.text = "${pickupKm} km"
        tvDropLocation.text = "To: $dropLoc"

        if (isTowards) {
            tvDirection.text = "TOWARDS HOME"
            tvDirection.setTextColor(ContextCompat.getColor(this, R.color.hud_neon_green))
            tvBearingCompass.text = "$compass (${bearing.toInt()}°) • TOWARDS HOME"
        } else {
            tvDirection.text = "AWAY FROM HOME"
            tvDirection.setTextColor(ContextCompat.getColor(this, R.color.hud_crimson_red))
            tvBearingCompass.text = "$compass (${bearing.toInt()}°) • AWAY FROM HOME"
        }

        countDownTimer?.cancel()

        val isAccept = decision == "ACCEPT"
        val totalMs = (countdownSecs * 1000).toLong()

        countDownTimer = object : CountDownTimer(totalMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                val progress = ((millisUntilFinished.toFloat() / totalMs) * 100).toInt()
                pbCountdown.progress = progress

                if (isAccept) {
                    tvDecisionStatus.text = "Auto-Accepting in ${secondsLeft}s..."
                    tvDecisionStatus.setTextColor(ContextCompat.getColor(this@OverlayService, R.color.hud_neon_green))
                } else {
                    tvDecisionStatus.text = "Auto-Rejecting in ${secondsLeft}s ($decision)..."
                    tvDecisionStatus.setTextColor(ContextCompat.getColor(this@OverlayService, R.color.hud_crimson_red))
                }
            }

            override fun onFinish() {
                pbCountdown.progress = 0
                tvDecisionStatus.text = if (isAccept) "Accepted!" else "Rejected!"
            }
        }.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndControls(view: View, layoutParams: WindowManager.LayoutParams) {
        val dragHandle = view.findViewById<View>(R.id.headerDragHandle)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseHud)
        val btnAccept = view.findViewById<Button>(R.id.btnAcceptNow)
        val btnReject = view.findViewById<Button>(R.id.btnRejectNow)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, layoutParams)
                    true
                }
                else -> false
            }
        }

        btnClose.setOnClickListener {
            hideHud()
        }

        btnAccept.setOnClickListener {
            countDownTimer?.cancel()
            val intent = Intent(RapidoAccessibilityService.ACTION_MANUAL_ACCEPT)
            sendBroadcast(intent)
            hideHud()
        }

        btnReject.setOnClickListener {
            countDownTimer?.cancel()
            val intent = Intent(RapidoAccessibilityService.ACTION_MANUAL_REJECT)
            sendBroadcast(intent)
            hideHud()
        }
    }

    private fun hideHud() {
        countDownTimer?.cancel()
        if (hudView != null && windowManager != null) {
            try {
                windowManager?.removeView(hudView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            hudView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHud()
    }

    companion object {
        const val NOTIFICATION_ID = 2001

        const val ACTION_SHOW_OFFER = "com.rapido.assistant.action.SHOW_OFFER"
        const val ACTION_HIDE_HUD = "com.rapido.assistant.action.HIDE_HUD"

        const val EXTRA_FARE = "extra_fare"
        const val EXTRA_PICKUP_KM = "extra_pickup_km"
        const val EXTRA_DROP_LOC = "extra_drop_loc"
        const val EXTRA_IS_TOWARDS = "extra_is_towards"
        const val EXTRA_BEARING = "extra_bearing"
        const val EXTRA_COMPASS = "extra_compass"
        const val EXTRA_DECISION = "extra_decision"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_COUNTDOWN = "extra_countdown"
    }
}
