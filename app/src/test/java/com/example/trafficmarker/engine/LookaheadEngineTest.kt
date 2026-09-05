package com.example.trafficmarker.engine

import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.TrafficEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class LookaheadEngineTest {
    private val marker = Marker("m", "10.0.0.1", 443, Direction.IN, 1000, 10)

    @Test
    fun reportsExactRelativeStep() {
        val items = (1..300).map { step ->
            LookaheadItem(
                absoluteIndex = 1000L + step,
                event = TrafficEvent(
                    host = "10.0.0.1",
                    port = 443,
                    direction = Direction.IN,
                    sizeBytes = if (step == 137) 1000 else 3000
                )
            )
        }
        val result = LookaheadEngine.scan(marker, items, exactOrdering = true)
        assertEquals(LookaheadStatus.EXACT, result.status)
        assertEquals(300, result.windowSize)
        assertEquals(1, result.hits.size)
        assertEquals(137, result.hits.single().relativeStep)
        assertEquals(1137L, result.hits.single().absoluteIndex)
    }

    @Test
    fun unavailableDoesNotInventFutureData() {
        val result = LookaheadEngine.unavailable("Tidak ada provider")
        assertEquals(LookaheadStatus.UNAVAILABLE, result.status)
        assertEquals(0, result.windowSize)
        assertEquals(0, result.hits.size)
    }
}
