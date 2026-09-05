package com.example.trafficmarker.recorder

import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.TrafficEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepRecorderTest {
    @Test
    fun recordsGroundTruthAndEventsInsideStep() {
        StepRecorder.reset()
        TrafficRecorder.start(clearPrevious = true)

        val index = StepRecorder.startStep(1000L)
        assertEquals(1, index)
        assertTrue(StepRecorder.isActive())

        TrafficRecorder.onEvent(
            TrafficEvent(
                timeMs = 1200L,
                host = "1.2.3.4",
                port = 443,
                direction = Direction.IN,
                sizeBytes = 777
            )
        )

        val step = StepRecorder.finishStep("Bigwin", 1500L)
        assertEquals(1, step.index)
        assertEquals("Bigwin", step.label)
        assertEquals(1, step.events.size)
        assertEquals(777, step.events.single().sizeBytes)
        assertFalse(StepRecorder.isActive())
    }
}
