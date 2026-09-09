package com.rapido.assistant.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("NotificationAction", "Received simulated notification accept action")
        Toast.makeText(context, "🎉 Notification Accept Action Executed Successfully!", Toast.LENGTH_LONG).show()
    }
}
