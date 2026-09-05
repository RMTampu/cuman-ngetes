package com.example.cardprobe.model

enum class Direction { CONNECT, OUT, IN, UDP_OUT, UDP_IN }

data class ProbeEvent(
    val timeMs: Long = System.currentTimeMillis(),
    val host: String,
    val port: Int,
    val direction: Direction,
    val sizeBytes: Int
)

data class ProbeTrial(
    val sessionId: Long,
    val dealAtMs: Long,
    val revealAtMs: Long,
    val baselineInBytes: Long,
    val baselineInEvents: Int,
    val dealInBytes: Long,
    val dealInEvents: Int,
    val revealInBytes: Long,
    val revealInEvents: Int,
    val dealTopEndpoint: String = "",
    val dealTopBytes: Long = 0L,
    val revealTopEndpoint: String = "",
    val revealTopBytes: Long = 0L
)

enum class PresenceStatus {
    NEED_MORE_TRIALS,
    PREFETCH_CANDIDATE,
    PREFETCH_CROSS_SESSION,
    REVEAL_REQUIRES_NETWORK,
    INCONCLUSIVE
}

data class PresenceReport(
    val status: PresenceStatus,
    val trialCount: Int,
    val sessionCount: Int,
    val prefetchVotes: Int,
    val revealVotes: Int,
    val message: String
)
