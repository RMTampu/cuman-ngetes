package com.example.trafficmarker.engine

import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
import org.junit.Assert.assertEquals
import org.junit.Test

class LookaheadControllerTest {
    private val marker = Marker("m", "x", 443, Direction.OUT, 100, 10)

    @Test
    fun providerAlwaysReceivesTwenty() {
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
        LookaheadController.scan(marker, provider)
        assertEquals(20, requested)
    }

    @Test
    fun missingProviderIsUnavailable() {
        val result = LookaheadController.scan(marker, null)
        assertEquals(LookaheadStatus.UNAVAILABLE, result.status)
    }
}
