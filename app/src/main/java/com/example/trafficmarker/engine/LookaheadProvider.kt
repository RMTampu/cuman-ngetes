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
    const val WINDOW = 20

    fun scan(
        marker: Marker,
        provider: LookaheadProvider?
    ): LookaheadResult {
        if (provider == null) {
            return LookaheadEngine.unavailable("Belum ada provider data depan")
        }

        val batch = try {
            provider.loadAhead(WINDOW)
        } catch (t: Throwable) {
            return LookaheadEngine.unavailable(
                "Provider gagal: " + (t.message ?: t.javaClass.simpleName)
            )
        } ?: return LookaheadEngine.unavailable("Sumber tidak menyediakan data di depan")

        return LookaheadEngine.scan(
            marker = marker,
            orderedItems = batch.items.take(WINDOW),
            exactOrdering = batch.exactOrdering
        )
    }
}
