package com.rapido.assistant.engine

import com.rapido.assistant.data.DriverPreferences
import com.rapido.assistant.model.DecisionResult
import com.rapido.assistant.model.DecisionType
import com.rapido.assistant.model.RideOffer
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class EvaluationConfig(
    val minFare: Double = 40.0,
    val maxPickupDistanceKm: Double = 3.0,
    val skipNightRides: Boolean = false,
    val towardsToleranceDegrees: Float = 60f,
    val currLat: Double = 12.9352, // Koramangala
    val currLng: Double = 77.6245,
    val homeLat: Double = 12.9784, // Indiranagar (North-East of Koramangala)
    val homeLng: Double = 77.6408
)

object DecisionEngine {

    // Known landmark coordinates for Bangalore to allow accurate bearing calculations for simulated & recognized areas
    private val LANDMARK_COORDINATES = mapOf(
        "indiranagar" to Pair(12.9784, 77.6408),
        "koramangala" to Pair(12.9352, 77.6245),
        "whitefield" to Pair(12.9698, 77.7500),
        "electronic city" to Pair(12.8399, 77.6770),
        "marathahalli" to Pair(12.9591, 77.6974),
        "mg road" to Pair(12.9756, 77.6066),
        "hsr layout" to Pair(12.9121, 77.6446),
        "bellandur" to Pair(12.9260, 77.6762),
        "jp nagar" to Pair(12.9063, 77.5857),
        "jayanagar" to Pair(12.9308, 77.5838),
        "rajajinagar" to Pair(12.9982, 77.5530),
        "hebbal" to Pair(13.0358, 77.5970)
    )

    fun evaluate(offer: RideOffer, preferences: DriverPreferences, currentHour: Int = getCurrentHour()): DecisionResult {
        val config = EvaluationConfig(
            minFare = preferences.minFare,
            maxPickupDistanceKm = preferences.maxPickupDistanceKm,
            skipNightRides = preferences.skipNightRides,
            towardsToleranceDegrees = preferences.towardsToleranceDegrees,
            currLat = preferences.currentLat,
            currLng = preferences.currentLng,
            homeLat = preferences.homeLat,
            homeLng = preferences.homeLng
        )
        return evaluate(offer, config, currentHour)
    }

    fun evaluate(offer: RideOffer, config: EvaluationConfig, currentHour: Int = getCurrentHour()): DecisionResult {
        // 1. Calculate direction and bearing
        enrichWithDirection(offer, config)

        // 2. Night rides check (10 PM to 6 AM)
        if (config.skipNightRides && (currentHour >= 22 || currentHour < 6)) {
            return DecisionResult(
                decision = DecisionType.REJECT_NIGHT_RIDE,
                reason = "Night ride skipped (10 PM - 6 AM filter active)",
                offer = offer
            )
        }

        // 3. Minimum Fare check
        if (offer.fare < config.minFare) {
            return DecisionResult(
                decision = DecisionType.REJECT_LOW_FARE,
                reason = "Fare ₹${offer.fare.toInt()} is below minimum ₹${config.minFare.toInt()}",
                offer = offer
            )
        }

        // 4. Maximum Pickup Distance check
        if (offer.pickupDistanceKm > config.maxPickupDistanceKm) {
            return DecisionResult(
                decision = DecisionType.REJECT_FAR_PICKUP,
                reason = "Pickup ${offer.pickupDistanceKm} km exceeds max ${config.maxPickupDistanceKm} km",
                offer = offer
            )
        }

        // 5. Away from Home check (if ride is away and preference prioritizes home)
        if (!offer.isTowardsHome) {
            return DecisionResult(
                decision = DecisionType.REJECT_AWAY_FROM_HOME,
                reason = "Ride is Away from Home (${offer.compassDirection}, bearing ${offer.bearingAngle.toInt()}°)",
                offer = offer
            )
        }

        return DecisionResult(
            decision = DecisionType.ACCEPT,
            reason = "Matches criteria: Towards Home (${offer.compassDirection}), Fare ₹${offer.fare.toInt()}, Pickup ${offer.pickupDistanceKm} km",
            offer = offer
        )
    }

    fun enrichWithDirection(offer: RideOffer, config: EvaluationConfig) {
        val currLat = config.currLat
        val currLng = config.currLng
        val homeLat = config.homeLat
        val homeLng = config.homeLng

        // Resolve destination coordinates
        val dropCoords = if (offer.dropLat != null && offer.dropLng != null) {
            Pair(offer.dropLat, offer.dropLng)
        } else {
            resolveCoordinatesFromAddress(offer.dropLocation)
        }

        val homeBearing = calculateBearing(currLat, currLng, homeLat, homeLng)
        val dropBearing = if (dropCoords != null) {
            calculateBearing(currLat, currLng, dropCoords.first, dropCoords.second)
        } else {
            // Fallback bearing based on hash or mock direction
            (homeBearing + 35f) % 360f
        }

        val angleDelta = angularDifference(dropBearing, homeBearing)
        val isTowards = angleDelta <= config.towardsToleranceDegrees

        offer.bearingAngle = dropBearing
        offer.compassDirection = bearingToCompass(dropBearing)
        offer.isTowardsHome = isTowards
    }

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)

        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        if (bearing < 0) {
            bearing += 360f
        }
        return bearing
    }

    fun angularDifference(angle1: Float, angle2: Float): Float {
        val diff = Math.abs((angle1 - angle2 + 540) % 360 - 180)
        return diff
    }

    fun bearingToCompass(bearing: Float): String {
        val normalized = (bearing % 360 + 360) % 360
        return when {
            normalized >= 337.5 || normalized < 22.5 -> "N"
            normalized >= 22.5 && normalized < 67.5 -> "NE"
            normalized >= 67.5 && normalized < 112.5 -> "E"
            normalized >= 112.5 && normalized < 157.5 -> "SE"
            normalized >= 157.5 && normalized < 202.5 -> "S"
            normalized >= 202.5 && normalized < 247.5 -> "SW"
            normalized >= 247.5 && normalized < 292.5 -> "W"
            else -> "NW"
        }
    }

    private fun resolveCoordinatesFromAddress(address: String): Pair<Double, Double>? {
        val normalized = address.lowercase(Locale.ROOT)
        for ((key, coords) in LANDMARK_COORDINATES) {
            if (normalized.contains(key)) {
                return coords
            }
        }
        return null
    }

    private fun getCurrentHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    // Parsing helpers for screen scraping and notifications
    fun parseRideOfferFromText(text: String): RideOffer? {
        val cleanText = text.replace(",", "")

        // Regex for fare: ₹ 50, ₹50, Rs 50, Rs. 120, 120 Rs, etc.
        val fareRegex = Regex("""(?:₹|Rs\.?|INR)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        val fareMatch = fareRegex.find(cleanText)
            ?: Regex("""(\d+)\s*(?:₹|Rs\.?|rupees)""", RegexOption.IGNORE_CASE).find(cleanText)

        val fare = fareMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: return null

        // Regex for pickup distance: 2.5 km, 2.5km, 3 km away
        val distRegex = Regex("""([\d.]+)\s*(?:km|kms|kilometers)""", RegexOption.IGNORE_CASE)
        val distMatch = distRegex.find(cleanText)
        val pickupKm = distMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.5

        // Extract drop location
        val dropLocation = extractDropLocation(cleanText)

        return RideOffer(
            fare = fare,
            pickupDistanceKm = pickupKm,
            dropLocation = dropLocation,
            rawText = text
        )
    }

    private fun extractDropLocation(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("Drop", ignoreCase = true) || line.contains("To", ignoreCase = true)) {
                if (i + 1 < lines.size) {
                    val next = lines[i + 1]
                    if (!next.contains("km", ignoreCase = true) && !next.contains("₹", ignoreCase = true)) {
                        return next
                    }
                }
            }
        }

        // Check for known localities
        for (key in LANDMARK_COORDINATES.keys) {
            if (text.contains(key, ignoreCase = true)) {
                return key.split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
            }
        }

        return "Indiranagar"
    }
}
