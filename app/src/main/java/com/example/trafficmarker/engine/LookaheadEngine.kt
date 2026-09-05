package com.example.trafficmarker.engine

import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.TrafficEvent

enum class LookaheadStatus { EXACT, ESTIMATED, UNAVAILABLE }

data class LookaheadItem(
    val absoluteIndex: Long?,
    val event: TrafficEvent,
    val confidence: Double = 1.0
)

data class LookaheadHit(
    val relativeStep: Int,
    val absoluteIndex: Long?,
    val confidence: Double,
    val event: TrafficEvent
)

data class LookaheadResult(
    val status: LookaheadStatus,
    val windowSize: Int,
    val hits: List<LookaheadHit>,
    val reason: String? = null
)

object LookaheadEngine {
    fun unavailable(reason: String): LookaheadResult =
        LookaheadResult(
            status = LookaheadStatus.UNAVAILABLE,
            windowSize = 0,
            hits = emptyList(),
            reason = reason
        )

    fun scan(
        marker: Marker,
        orderedItems: List<LookaheadItem>,
        exactOrdering: Boolean
    ): LookaheadResult {
        if (orderedItems.isEmpty()) {
            return unavailable("Sumber belum menyediakan data di depan")
        }

        val hits = orderedItems.mapIndexedNotNull { index, item ->
            if (!marker.matches(item.event)) return@mapIndexedNotNull null
            LookaheadHit(
                relativeStep = index + 1,
                absoluteIndex = item.absoluteIndex,
                confidence = item.confidence.coerceIn(0.0, 1.0),
                event = item.event
            )
        }

        return LookaheadResult(
            status = if (exactOrdering) LookaheadStatus.EXACT else LookaheadStatus.ESTIMATED,
            windowSize = orderedItems.size,
            hits = hits
        )
    }
}
