package com.example.trafficmarker.engine

import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.TrafficEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickMarkerEngineTest {
    @Test
    fun choosesLargestNonConnectEvent() {
        val chosen = QuickMarkerEngine.choose(
            listOf(
                TrafficEvent(timeMs = 1, host = "a", port = 443, direction = Direction.CONNECT, sizeBytes = 0),
                TrafficEvent(timeMs = 2, host = "a", port = 443, direction = Direction.OUT, sizeBytes = 300),
                TrafficEvent(timeMs = 3, host = "a", port = 443, direction = Direction.IN, sizeBytes = 900)
            )
        )
        assertEquals(900, chosen!!.sizeBytes)
        assertEquals(Direction.IN, chosen.direction)
    }

    @Test
    fun emptyReturnsNull() {
        assertNull(QuickMarkerEngine.choose(emptyList()))
    }
}
