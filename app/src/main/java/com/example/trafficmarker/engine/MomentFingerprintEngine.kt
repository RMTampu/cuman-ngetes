package com.example.trafficmarker.engine

import com.example.trafficmarker.model.BurstSignature
import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.MomentSample
import com.example.trafficmarker.model.TrafficEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object MomentFingerprintEngine {
    const val DEFAULT_BEFORE_MS = 8000L
    const val DEFAULT_AFTER_MS = 1500L
    const val BURST_GAP_MS = 650L

    fun endpointGroup(host: String): String {
        val parts = host.split(".")
        return if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            parts.take(3).joinToString(".") + ".*"
        } else {
            host.lowercase()
        }
    }

    fun createSample(
        anchorMs: Long,
        events: List<TrafficEvent>,
        beforeMs: Long = DEFAULT_BEFORE_MS,
        afterMs: Long = DEFAULT_AFTER_MS
    ): MomentSample {
        val from = anchorMs - beforeMs
        val to = anchorMs + afterMs
        val bursts = buildBursts(
            events.filter { it.timeMs in from..to && it.direction != Direction.CONNECT },
            anchorMs
        )
        return MomentSample(
            capturedAtMs = anchorMs,
            beforeMs = beforeMs,
            afterMs = afterMs,
            bursts = bursts
        )
    }

    fun buildBursts(events: List<TrafficEvent>, anchorMs: Long): List<BurstSignature> {
        if (events.isEmpty()) return emptyList()

        val grouped = events
            .filter { it.direction != Direction.CONNECT }
            .groupBy { endpointGroup(it.host) + "|" + it.port }

        val out = ArrayList<BurstSignature>()
        grouped.values.forEach { endpointEvents ->
            val sorted = endpointEvents.sortedBy { it.timeMs }
            var bucket = ArrayList<TrafficEvent>()
            var lastTime = Long.MIN_VALUE

            sorted.forEach { event ->
                if (bucket.isNotEmpty() && event.timeMs - lastTime > BURST_GAP_MS) {
                    out += buildBurst(bucket, anchorMs)
                    bucket = ArrayList()
                }
                bucket += event
                lastTime = event.timeMs
            }
            if (bucket.isNotEmpty()) out += buildBurst(bucket, anchorMs)
        }

        return out.sortedBy { it.startOffsetMs }
    }

    fun buildBurst(events: List<TrafficEvent>, anchorMs: Long): BurstSignature {
        require(events.isNotEmpty())
        val sorted = events.sortedBy { it.timeMs }
        val first = sorted.first()
        val last = sorted.last()

        var totalIn = 0
        var totalOut = 0
        var countIn = 0
        var countOut = 0
        var maxIn = 0
        var maxOut = 0
        val seq = StringBuilder()

        sorted.forEach { e ->
            when (e.direction) {
                Direction.IN, Direction.UDP_IN -> {
                    totalIn += e.sizeBytes
                    countIn++
                    maxIn = max(maxIn, e.sizeBytes)
                    if (seq.length < 16) seq.append('I')
                }
                Direction.OUT, Direction.UDP_OUT -> {
                    totalOut += e.sizeBytes
                    countOut++
                    maxOut = max(maxOut, e.sizeBytes)
                    if (seq.length < 16) seq.append('O')
                }
                Direction.CONNECT -> Unit
            }
        }

        return BurstSignature(
            endpointGroup = endpointGroup(first.host),
            port = first.port,
            startOffsetMs = first.timeMs - anchorMs,
            durationMs = max(0L, last.timeMs - first.timeMs),
            totalIn = totalIn,
            totalOut = totalOut,
            countIn = countIn,
            countOut = countOut,
            maxIn = maxIn,
            maxOut = maxOut,
            sequence = seq.toString()
        )
    }

    fun score(marker: Marker, candidate: BurstSignature): Double {
        if (marker.samples.isEmpty()) return 0.0
        val perSample = marker.samples.mapNotNull { sample ->
            val references = sample.bursts
                .sortedByDescending(::activityScore)
                .take(6)
            references.maxOfOrNull { similarity(it, candidate) }
        }
        if (perSample.isEmpty()) return 0.0
        return perSample.average().coerceIn(0.0, 1.0)
    }

    fun similarity(a: BurstSignature, b: BurstSignature): Double {
        val endpoint = when {
            a.port == b.port && a.endpointGroup == b.endpointGroup -> 1.0
            a.port == b.port -> 0.65
            else -> 0.0
        }
        if (endpoint == 0.0) return 0.0

        val byteScore = (ratio(a.totalIn, b.totalIn) + ratio(a.totalOut, b.totalOut)) / 2.0
        val countScore = (ratio(a.countIn, b.countIn) + ratio(a.countOut, b.countOut)) / 2.0
        val maxScore = (ratio(a.maxIn, b.maxIn) + ratio(a.maxOut, b.maxOut)) / 2.0
        val durationScore = ratioLong(a.durationMs + 50, b.durationMs + 50)
        val sequenceScore = sequenceSimilarity(a.sequence, b.sequence)

        var score =
            0.26 * endpoint +
            0.27 * byteScore +
            0.14 * countScore +
            0.14 * maxScore +
            0.09 * durationScore +
            0.10 * sequenceScore

        if (min(a.totalBytes, b.totalBytes) < 128 && max(a.eventCount, b.eventCount) <= 2) {
            score *= 0.72
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun activityScore(b: BurstSignature): Long =
        b.totalBytes.toLong() + (b.eventCount * 96L) + b.maxIn + b.maxOut

    private fun ratio(a: Int, b: Int): Double {
        if (a == 0 && b == 0) return 1.0
        val hi = max(a, b)
        if (hi == 0) return 1.0
        return min(a, b).toDouble() / hi.toDouble()
    }

    private fun ratioLong(a: Long, b: Long): Double {
        if (a == 0L && b == 0L) return 1.0
        val hi = max(a, b)
        if (hi == 0L) return 1.0
        return min(a, b).toDouble() / hi.toDouble()
    }

    private fun sequenceSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val n = min(a.length, b.length)
        var same = 0
        for (i in 0 until n) if (a[i] == b[i]) same++
        val prefix = same.toDouble() / max(a.length, b.length).toDouble()

        val aIn = a.count { it == 'I' }
        val bIn = b.count { it == 'I' }
        val aOut = a.count { it == 'O' }
        val bOut = b.count { it == 'O' }
        val balance = (ratio(aIn, bIn) + ratio(aOut, bOut)) / 2.0
        return 0.55 * prefix + 0.45 * balance
    }
}
