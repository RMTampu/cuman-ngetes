package com.example.trafficmarker.engine

import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.TrafficEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class LookaheadControllerTest {
    private val marker = Marker("m", "x", 443, Direction.OUT, 100, 10)

    @Test
    fun clampsRequestToThreeHundred() {
        var requested = 0
        val provider = object : LookaheadProvider {
            override fun loadAhead(limit: Int): LookaheadBatch {
                requested = limit
                return LookaheadBatch(
                    items = emptyList(),
                    exactOrdering = true,
                    sourceName = "test"
                )
            }
        }
        LookaheadController.scan(marker, provider, 999)
        assertEquals(300, requested)
    }

    @Test
    fun clampsRequestToOneHundred() {
        var requested = 0
        val provider = object : LookaheadProvider {
            override fun loadAhead(limit: Int): LookaheadBatch {
                requested = limit
                return LookaheadBatch(
                    items = emptyList(),
                    exactOrdering = true,
                    sourceName = "test"
                )
            }
        }
        LookaheadController.scan(marker, provider, 1)
        assertEquals(100, requested)
    }

    @Test
    fun missingProviderIsUnavailable() {
        val result = LookaheadController.scan(marker, null, 300)
        assertEquals(LookaheadStatus.UNAVAILABLE, result.status)
    }
}
