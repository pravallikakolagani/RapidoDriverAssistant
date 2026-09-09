package com.rapido.assistant.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

object AudioBugFixer {

    private const val TAG = "AudioBugFixer"
    private const val FIX_DELAY_MS = 300L
    private var lastFixTimestamp: Long = 0

    /**
     * Executes an automated Mute -> 300ms -> Unmute cycle to reset Android's audio routing
     * and restore navigation speech after launching external Google Maps / Rapido Map.
     */
    fun fixNavigationAudio(context: Context, showToast: Boolean = false) {
        val now = System.currentTimeMillis()
        // Prevent rapid duplicate triggers within 2 seconds
        if (now - lastFixTimestamp < 2000) {
            return
        }
        lastFixTimestamp = now

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val mainHandler = Handler(Looper.getMainLooper())

            // 1. Capture current stream volumes
            val musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val voiceVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)

            Log.d(TAG, "Triggering Audio Bug Fix: musicVol=$musicVol, voiceVol=$voiceVol")

            // 2. Mute / Lower streams
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            }

            // 3. Wait 300ms, then Unmute and restore volume
            mainHandler.postDelayed({
                try {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    }

                    // Restore exact volume levels
                    val targetVol = if (musicVol <= 0) (maxMusicVol * 0.75).toInt() else musicVol
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

                    Log.d(TAG, "Audio Bug Fix Completed successfully. Volume restored to $targetVol")

                    if (showToast) {
                        Toast.makeText(context, "Map Voice Audio Restored (Mute-Unmute Fixed)", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error completing unmute cycle: ${e.message}", e)
                }
            }, FIX_DELAY_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply audio bug fix: ${e.message}", e)
        }
    }
}
