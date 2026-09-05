package com.example.trafficmarker.engine

import com.example.trafficmarker.model.BurstSignature
import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.MomentSample
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentFingerprintEngineTest {
    private fun burst(
        totalIn: Int,
        totalOut: Int,
        countIn: Int,
        countOut: Int,
        maxIn: Int,
        maxOut: Int,
        sequence: String
    ) = BurstSignature(
        endpointGroup = "163.181.27.*",
        port = 443,
        startOffsetMs = -1000,
        durationMs = 420,
        totalIn = totalIn,
        totalOut = totalOut,
        countIn = countIn,
        countOut = countOut,
        maxIn = maxIn,
        maxOut = maxOut,
        sequence = sequence
    )

    @Test
    fun repeatedMomentScoresHigh() {
        val reference = burst(3400, 800, 3, 2, 1700, 780, "IOOII")
        val marker = Marker(
            id = "m1",
            host = "163.181.27.*",
            port = 443,
            direction = Direction.IN,
            centerSize = 4200,
            title = "Target A",
            samples = listOf(
                MomentSample(1000, 8000, 1500, listOf(reference)),
                MomentSample(2000, 8000, 1500, listOf(burst(3320, 820, 3, 2, 1660, 800, "IOOII")))
            )
        )

        val candidate = burst(3360, 790, 3, 2, 1680, 770, "IOOII")
        assertTrue(MomentFingerprintEngine.score(marker, candidate) > 0.85)
    }

    @Test
    fun tinyKeepaliveDoesNotScoreAsTarget() {
        val reference = burst(3400, 800, 3, 2, 1700, 780, "IOOII")
        val marker = Marker(
            id = "m1",
            host = "163.181.27.*",
            port = 443,
            direction = Direction.IN,
            centerSize = 4200,
            title = "Target A",
            samples = listOf(MomentSample(1000, 8000, 1500, listOf(reference)))
        )

        val heartbeat = burst(40, 37, 1, 1, 40, 37, "OI")
        assertTrue(MomentFingerprintEngine.score(marker, heartbeat) < 0.78)
    }
}
