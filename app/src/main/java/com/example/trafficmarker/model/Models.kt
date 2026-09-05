package com.example.trafficmarker.model

enum class Direction { CONNECT, OUT, IN, UDP_OUT, UDP_IN }

data class TrafficEvent(
    val timeMs: Long = System.currentTimeMillis(),
    val host: String,
    val port: Int,
    val direction: Direction,
    val sizeBytes: Int,
    val matched: Boolean = false
)

data class BurstSignature(
    val endpointGroup: String,
    val port: Int,
    val startOffsetMs: Long,
    val durationMs: Long,
    val totalIn: Int,
    val totalOut: Int,
    val countIn: Int,
    val countOut: Int,
    val maxIn: Int,
    val maxOut: Int,
    val sequence: String
) {
    val totalBytes: Int get() = totalIn + totalOut
    val eventCount: Int get() = countIn + countOut
}

data class MomentSample(
    val capturedAtMs: Long,
    val beforeMs: Long,
    val afterMs: Long,
    val bursts: List<BurstSignature>
)

data class Marker(
    val id: String,
    val host: String,
    val port: Int,
    val direction: Direction,
    val centerSize: Int,
    val tolerancePercent: Int = 25,
    val title: String = "Tanpa Judul",
    val samples: List<MomentSample> = emptyList()
) {
    fun matches(event: TrafficEvent): Boolean {
        if (!host.equals(event.host, ignoreCase = true)) return false
        if (port != event.port || direction != event.direction) return false
        if (centerSize <= 0) return true
        val tolerance = maxOf(64, centerSize * tolerancePercent / 100)
        return kotlin.math.abs(event.sizeBytes - centerSize) <= tolerance
    }
}
