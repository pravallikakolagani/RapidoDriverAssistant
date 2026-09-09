package com.rapido.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.rapido.assistant.model.DecisionResult
import com.rapido.assistant.model.DecisionType
import com.rapido.assistant.model.RideOffer
import java.util.Locale

class VoiceAnnouncer private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingUtterances = mutableListOf<String>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("en", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }

            // Set audio attributes tailored for helmet Bluetooth headsets
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
            tts?.setSpeechRate(1.05f) // Crisp, slightly brisk delivery for road awareness

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Speech started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Speech done: $utteranceId")
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Speech error on $utteranceId")
                }
            })

            isInitialized = true
            flushPendingUtterances()
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech engine")
        }
    }

    fun announceRide(offer: RideOffer, decision: DecisionResult) {
        val directionText = if (offer.isTowardsHome) {
            "Towards Home (${offer.compassDirection})"
        } else {
            "Away from Home (${offer.compassDirection})"
        }

        val actionText = when (decision.decision) {
            DecisionType.ACCEPT -> "Auto-accepting now."
            DecisionType.REJECT_LOW_FARE -> "Low fare. Auto-rejecting."
            DecisionType.REJECT_FAR_PICKUP -> "Pickup too far. Auto-rejecting."
            DecisionType.REJECT_AWAY_FROM_HOME -> "Away from home. Auto-rejecting."
            DecisionType.REJECT_NIGHT_RIDE -> "Night ride filter active. Rejecting."
            DecisionType.MANUAL_REVIEW -> "Review ride offer."
        }

        val speech = "New ride to ${offer.dropLocation}: $directionText. ${offer.fare.toInt()} rupees. $actionText"
        speak(speech)
    }

    fun speak(text: String) {
        if (!isInitialized) {
            pendingUtterances.add(text)
            return
        }
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "RAPIDO_ASSIST_${System.currentTimeMillis()}")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "RAPIDO_ALERT")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun flushPendingUtterances() {
        if (pendingUtterances.isNotEmpty()) {
            for (text in pendingUtterances) {
                speak(text)
            }
            pendingUtterances.clear()
        }
    }

    companion object {
        private const val TAG = "VoiceAnnouncer"

        @Volatile
        private var instance: VoiceAnnouncer? = null

        fun getInstance(context: Context): VoiceAnnouncer {
            return instance ?: synchronized(this) {
                instance ?: VoiceAnnouncer(context.applicationContext).also { instance = it }
            }
        }
    }
}
