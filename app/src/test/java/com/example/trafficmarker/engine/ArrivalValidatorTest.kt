package com.example.trafficmarker.engine

import com.example.trafficmarker.model.BurstSignature
import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.MomentSample
import com.example.trafficmarker.model.TrafficEvent
import com.example.trafficmarker.recorder.StepRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalValidatorTest {
    private val marker = Marker(
        id = "big",
        host = "163.181.27.*",
        port = 443,
        direction = Direction.IN,
        centerSize = 4200,
        title = "Bigwin",
        samples = listOf(
            MomentSample(
                capturedAtMs = 1000,
                beforeMs = 8000,
                afterMs = 1500,
                bursts = listOf(
                    BurstSignature(
                        endpointGroup = "163.181.27.*",
                        port = 443,
                        startOffsetMs = -500,
                        durationMs = 200,
                        totalIn = 3400,
                        totalOut = 800,
                        countIn = 2,
                        countOut = 1,
                        maxIn = 1700,
                        maxOut = 800,
                        sequence = "IOI"
                    )
                )
            )
        )
    )

    @Test
    fun validatesStableTwoStepLeadOnDataset() {
        val targets = setOf(2, 5, 8, 11, 14)
        val sources = targets.map { it - 2 }.toSet()

        val steps = (0 until 17).map { i ->
            val matching = i in sources
            val events = if (matching) {
                listOf(
                    TrafficEvent(1000L + i * 1000, "163.181.27.10", 443, Direction.IN, 1700),
                    TrafficEvent(1010L + i * 1000, "163.181.27.10", 443, Direction.OUT, 800),
                    TrafficEvent(1020L + i * 1000, "163.181.27.10", 443, Direction.IN, 1700)
                )
            } else {
                listOf(
                    TrafficEvent(1000L + i * 1000, "10.0.0.2", 443, Direction.IN, 120)
                )
            }
            StepRecord(
                index = i + 1,
                startedAtMs = 1000L + i * 1000,
                resultAtMs = 1500L + i * 1000,
                label = if (i in targets) "Bigwin" else "Normal",
                events = events
            )
        }

        val result = ArrivalValidator.validate(marker, steps)
        assertEquals(ArrivalProof.VALIDATED, result.proof)
        assertEquals(2, result.best!!.leadSteps)
        assertEquals(5, result.best!!.truePositive)
        assertEquals(0, result.best!!.falsePositive)
        assertEquals(0, result.best!!.falseNegative)
    }
}
