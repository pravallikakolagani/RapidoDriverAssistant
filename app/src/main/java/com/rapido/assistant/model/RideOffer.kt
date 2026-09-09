package com.rapido.assistant.model

data class RideOffer(
    val fare: Double,
    val pickupDistanceKm: Double,
    val dropLocation: String,
    val pickupLocation: String = "",
    val rawText: String = "",
    val dropLat: Double? = null,
    val dropLng: Double? = null,
    var isTowardsHome: Boolean = false,
    var bearingAngle: Float = 0f,
    var compassDirection: String = "N"
)

enum class DecisionType {
    ACCEPT,
    REJECT_LOW_FARE,
    REJECT_FAR_PICKUP,
    REJECT_AWAY_FROM_HOME,
    REJECT_NIGHT_RIDE,
    MANUAL_REVIEW
}

data class DecisionResult(
    val decision: DecisionType,
    val reason: String,
    val offer: RideOffer
)
