package com.example.trafficmarker.engine

import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.TrafficEvent

object QuickMarkerEngine {
    fun choose(events: List<TrafficEvent>): TrafficEvent? {
        if (events.isEmpty()) return null
        return events
            .asSequence()
            .filter { it.direction != Direction.CONNECT }
            .maxWithOrNull(
                compareBy<TrafficEvent> { it.sizeBytes }
                    .thenBy { it.timeMs }
            )
            ?: events.maxByOrNull { it.timeMs }
    }
}
