package com.rapido.assistant.data

import android.content.Context
import android.content.SharedPreferences

class DriverPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var minFare: Double
        get() = prefs.getFloat(KEY_MIN_FARE, 40f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_MIN_FARE, value.toFloat()).apply()

    var maxPickupDistanceKm: Double
        get() = prefs.getFloat(KEY_MAX_PICKUP_KM, 3.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_MAX_PICKUP_KM, value.toFloat()).apply()

    var autoAccept: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ACCEPT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ACCEPT, value).apply()

    var autoReject: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REJECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REJECT, value).apply()

    var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    var skipNightRides: Boolean
        get() = prefs.getBoolean(KEY_SKIP_NIGHT_RIDES, false)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_NIGHT_RIDES, value).apply()

    var autoFixAudioBug: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FIX_AUDIO_BUG, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FIX_AUDIO_BUG, value).apply()

    var countdownSeconds: Int
        get() = prefs.getInt(KEY_COUNTDOWN_SECONDS, 5)
        set(value) = prefs.edit().putInt(KEY_COUNTDOWN_SECONDS, value).apply()

    // Home location (Default: Indiranagar, Bangalore: 12.9784, 77.6408)
    var homeLat: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_HOME_LAT, java.lang.Double.doubleToLongBits(12.9784)))
        set(value) = prefs.edit().putLong(KEY_HOME_LAT, java.lang.Double.doubleToLongBits(value)).apply()

    var homeLng: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_HOME_LNG, java.lang.Double.doubleToLongBits(77.6408)))
        set(value) = prefs.edit().putLong(KEY_HOME_LNG, java.lang.Double.doubleToLongBits(value)).apply()

    var homeAddress: String
        get() = prefs.getString(KEY_HOME_ADDRESS, "Indiranagar, Bengaluru") ?: "Indiranagar, Bengaluru"
        set(value) = prefs.edit().putString(KEY_HOME_ADDRESS, value).apply()

    // Current driver location (Default: Koramangala, Bangalore: 12.9352, 77.6245)
    var currentLat: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_CURR_LAT, java.lang.Double.doubleToLongBits(12.9352)))
        set(value) = prefs.edit().putLong(KEY_CURR_LAT, java.lang.Double.doubleToLongBits(value)).apply()

    var currentLng: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_CURR_LNG, java.lang.Double.doubleToLongBits(77.6245)))
        set(value) = prefs.edit().putLong(KEY_CURR_LNG, java.lang.Double.doubleToLongBits(value)).apply()

    var towardsToleranceDegrees: Float
        get() = prefs.getFloat(KEY_TOWARDS_TOLERANCE, 60f)
        set(value) = prefs.edit().putFloat(KEY_TOWARDS_TOLERANCE, value).apply()

    companion object {
        private const val PREFS_NAME = "rapido_assistant_prefs"
        private const val KEY_MIN_FARE = "key_min_fare"
        private const val KEY_MAX_PICKUP_KM = "key_max_pickup_km"
        private const val KEY_AUTO_ACCEPT = "key_auto_accept"
        private const val KEY_AUTO_REJECT = "key_auto_reject"
        private const val KEY_VOICE_ENABLED = "key_voice_enabled"
        private const val KEY_OVERLAY_ENABLED = "key_overlay_enabled"
        private const val KEY_SKIP_NIGHT_RIDES = "key_skip_night_rides"
        private const val KEY_AUTO_FIX_AUDIO_BUG = "key_auto_fix_audio_bug"
        private const val KEY_COUNTDOWN_SECONDS = "key_countdown_seconds"
        private const val KEY_HOME_LAT = "key_home_lat"
        private const val KEY_HOME_LNG = "key_home_lng"
        private const val KEY_HOME_ADDRESS = "key_home_address"
        private const val KEY_CURR_LAT = "key_curr_lat"
        private const val KEY_CURR_LNG = "key_curr_lng"
        private const val KEY_TOWARDS_TOLERANCE = "key_towards_tolerance"
    }
}
