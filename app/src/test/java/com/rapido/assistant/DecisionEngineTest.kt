package com.rapido.assistant

import com.rapido.assistant.engine.DecisionEngine
import com.rapido.assistant.engine.EvaluationConfig
import com.rapido.assistant.model.DecisionType
import com.rapido.assistant.model.RideOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {

    @Test
    fun testBearingAndCompassCalculations() {
        // Due North
        val northCompass = DecisionEngine.bearingToCompass(0f)
        assertEquals("N", northCompass)

        // North-East
        val neCompass = DecisionEngine.bearingToCompass(45f)
        assertEquals("NE", neCompass)

        // East
        val eastCompass = DecisionEngine.bearingToCompass(90f)
        assertEquals("E", eastCompass)

        // South
        val southCompass = DecisionEngine.bearingToCompass(180f)
        assertEquals("S", southCompass)

        // West
        val westCompass = DecisionEngine.bearingToCompass(270f)
        assertEquals("W", westCompass)
    }

    @Test
    fun testAngularDifference() {
        // Same angle
        assertEquals(0f, DecisionEngine.angularDifference(45f, 45f), 0.01f)

        // Across 0/360 boundary (350° and 10° is 20° apart)
        assertEquals(20f, DecisionEngine.angularDifference(350f, 10f), 0.01f)
        assertEquals(20f, DecisionEngine.angularDifference(10f, 350f), 0.01f)

        // Opposite directions (0° and 180° is 180°)
        assertEquals(180f, DecisionEngine.angularDifference(0f, 180f), 0.01f)
    }

    @Test
    fun testTextParsing() {
        val sample1 = """
            Rapido Captain
            New Ride Request
            ₹50 • 2.5 km • Koramangala
            ACCEPT
        """.trimIndent()

        val offer1 = DecisionEngine.parseRideOfferFromText(sample1)
        assertNotNull(offer1)
        assertEquals(50.0, offer1!!.fare, 0.01)
        assertEquals(2.5, offer1.pickupDistanceKm, 0.01)

        val sample2 = """
            ₹120
            1.2 km away
            Drop: Indiranagar
        """.trimIndent()

        val offer2 = DecisionEngine.parseRideOfferFromText(sample2)
        assertNotNull(offer2)
        assertEquals(120.0, offer2!!.fare, 0.01)
        assertEquals(1.2, offer2.pickupDistanceKm, 0.01)
        assertEquals("Indiranagar", offer2.dropLocation)
    }

    @Test
    fun testDecisionEvaluationPresetTowardsHome() {
        val config = EvaluationConfig(
            minFare = 40.0,
            maxPickupDistanceKm = 3.0,
            skipNightRides = false
        )

        // Indiranagar is North-East of Koramangala (same direction as home)
        val offer = RideOffer(
            fare = 120.0,
            pickupDistanceKm = 1.2,
            dropLocation = "Indiranagar 100ft Road"
        )

        val result = DecisionEngine.evaluate(offer, config, currentHour = 14)
        assertEquals(DecisionType.ACCEPT, result.decision)
        assertTrue(offer.isTowardsHome)
    }

    @Test
    fun testDecisionEvaluationLowFare() {
        val config = EvaluationConfig(minFare = 40.0, maxPickupDistanceKm = 3.0)
        val offer = RideOffer(fare = 25.0, pickupDistanceKm = 1.0, dropLocation = "Indiranagar")

        val result = DecisionEngine.evaluate(offer, config, currentHour = 14)
        assertEquals(DecisionType.REJECT_LOW_FARE, result.decision)
    }

    @Test
    fun testDecisionEvaluationTooFarPickup() {
        val config = EvaluationConfig(minFare = 40.0, maxPickupDistanceKm = 3.0)
        val offer = RideOffer(fare = 150.0, pickupDistanceKm = 7.5, dropLocation = "Indiranagar")

        val result = DecisionEngine.evaluate(offer, config, currentHour = 14)
        assertEquals(DecisionType.REJECT_FAR_PICKUP, result.decision)
    }

    @Test
    fun testDecisionEvaluationAwayFromHome() {
        val config = EvaluationConfig(minFare = 40.0, maxPickupDistanceKm = 3.0)
        // Driver is at Koramangala (12.9352, 77.6245), Home is at Indiranagar (North)
        // Whitefield is far East, Electronic City is South (Away from home)
        val offer = RideOffer(fare = 100.0, pickupDistanceKm = 1.5, dropLocation = "Electronic City")

        val result = DecisionEngine.evaluate(offer, config, currentHour = 14)
        assertEquals(DecisionType.REJECT_AWAY_FROM_HOME, result.decision)
    }

    @Test
    fun testNightRideFilter() {
        val config = EvaluationConfig(skipNightRides = true)
        val offer = RideOffer(fare = 100.0, pickupDistanceKm = 1.0, dropLocation = "Indiranagar")

        // 11 PM (23:00) should be rejected
        val resultNight = DecisionEngine.evaluate(offer, config, currentHour = 23)
        assertEquals(DecisionType.REJECT_NIGHT_RIDE, resultNight.decision)

        // 2 PM (14:00) should be accepted
        val resultDay = DecisionEngine.evaluate(offer, config, currentHour = 14)
        assertEquals(DecisionType.ACCEPT, resultDay.decision)
    }
}
