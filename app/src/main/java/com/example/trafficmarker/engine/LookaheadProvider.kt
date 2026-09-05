package com.example.trafficmarker.engine

import com.example.trafficmarker.model.Marker

data class LookaheadBatch(
    val items: List<LookaheadItem>,
    val exactOrdering: Boolean,
    val sourceName: String,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

interface LookaheadProvider {
    fun loadAhead(limit: Int): LookaheadBatch?
}

object LookaheadController {
    const val MIN_WINDOW = 100
    const val MAX_WINDOW = 300

    fun scan(
        marker: Marker,
        provider: LookaheadProvider?,
        requestedWindow: Int
    ): LookaheadResult {
        if (provider == null) {
            return LookaheadEngine.unavailable("Belum ada provider data depan")
        }

        val limit = requestedWindow.coerceIn(MIN_WINDOW, MAX_WINDOW)
        val batch = try {
            provider.loadAhead(limit)
        } catch (t: Throwable) {
            return LookaheadEngine.unavailable(
                "Provider gagal: " + (t.message ?: t.javaClass.simpleName)
            )
        } ?: return LookaheadEngine.unavailable("Sumber tidak menyediakan data di depan")

        return LookaheadEngine.scan(
            marker = marker,
            orderedItems = batch.items.take(limit),
            exactOrdering = batch.exactOrdering
        )
    }
}
