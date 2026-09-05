package com.example.trafficmarker.engine

import com.example.trafficmarker.model.BurstSignature
import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.TrafficEvent

object ManualLookaheadStore {
    const val LIMIT = 20
    private const val IDLE_FLUSH_MS = 850L
    private const val MATCH_THRESHOLD = 0.78

    data class Hit(val markerTitle: String, val confidence: Double)
    data class Step(val index: Int, val burst: BurstSignature, val hits: List<Hit>)

    @Volatile private var active = false
    @Volatile private var startedAtMs = 0L
    private val current = ArrayList<TrafficEvent>()
    private val steps = ArrayList<Step>()
    private var lastEventMs = 0L
    private var markers: List<Marker> = emptyList()

    @Synchronized
    fun start(markerSnapshot: List<Marker>, nowMs: Long = System.currentTimeMillis()) {
        active = true
        startedAtMs = nowMs
        current.clear()
        steps.clear()
        lastEventMs = 0L
        markers = markerSnapshot.filter { it.samples.isNotEmpty() }
    }

    @Synchronized
    fun stop() {
        flushCurrent()
        active = false
    }

    @Synchronized
    fun onEvent(event: TrafficEvent) {
        if (!active || event.direction == Direction.CONNECT || steps.size >= LIMIT) return

        if (current.isNotEmpty() && event.timeMs - lastEventMs > MomentFingerprintEngine.BURST_GAP_MS) {
            flushCurrent()
        }
        if (steps.size >= LIMIT) {
            active = false
            return
        }

        current += event
        lastEventMs = event.timeMs
    }

    @Synchronized
    fun snapshot(nowMs: Long = System.currentTimeMillis()): String {
        if (active && current.isNotEmpty() && nowMs - lastEventMs >= IDLE_FLUSH_MS) {
            flushCurrent()
        }
        if (steps.size >= LIMIT) active = false

        return buildString {
            append("LOAD 20: ").append(steps.size).append("/").append(LIMIT)
            append(if (active) " • MENGUMPULKAN" else " • SELESAI")
            append("\nMode: ESTIMATED (burst TLS, bukan isi HTTPS)")

            if (markers.isEmpty()) {
                append("\nBelum ada marker momen yang memiliki sampel.")
                return@buildString
            }

            val matchedSteps = steps.filter { it.hits.isNotEmpty() }
            if (matchedSteps.isEmpty()) {
                append("\nBelum ada marker cocok pada burst yang terkumpul.")
            } else {
                append("\n\nHASIL:")
                matchedSteps.forEach { step ->
                    step.hits.sortedByDescending { it.confidence }.take(3).forEach { hit ->
                        append("\n+").append(step.index)
                            .append("  ").append(hit.markerTitle)
                            .append("  ").append((hit.confidence * 100).toInt()).append("%")
                    }
                }
            }
        }
    }

    @Synchronized
    fun stepCount(): Int = steps.size

    @Synchronized
    fun isActive(): Boolean = active

    private fun flushCurrent() {
        if (current.isEmpty() || steps.size >= LIMIT) {
            current.clear()
            return
        }

        val burst = MomentFingerprintEngine.buildBurst(current.toList(), startedAtMs)
        current.clear()

        // Repetitive tiny two-packet keepalives (e.g. 37 B / 40 B) are not treated as a logical step.
        if (burst.totalBytes < 128 && burst.eventCount <= 2) return

        val hits = markers.mapNotNull { marker ->
            val confidence = MomentFingerprintEngine.score(marker, burst)
            if (confidence >= MATCH_THRESHOLD) Hit(marker.title, confidence) else null
        }

        steps += Step(
            index = steps.size + 1,
            burst = burst,
            hits = hits
        )
        if (steps.size >= LIMIT) active = false
    }
}
