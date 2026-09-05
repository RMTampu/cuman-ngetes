package com.example.cardprobe.engine

import com.example.cardprobe.model.PresenceStatus
import com.example.cardprobe.model.ProbeTrial
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceAnalyzerTest {
    private fun trial(
        deal: Long,
        reveal: Long,
        base: Long = 100L,
        session: Long = 1L
    ) = ProbeTrial(session, 1, 2, base, 1, deal, 5, reveal, 1)

    @Test
    fun detectsPrefetchCandidateAcrossRepeatedHands() {
        val r = PresenceAnalyzer.analyze(
            listOf(
                trial(5000, 100),
                trial(4200, 120),
                trial(6100, 80)
            )
        )
        assertEquals(PresenceStatus.PREFETCH_CANDIDATE, r.status)
    }

    @Test
    fun upgradesToCrossSessionOnlyAfterRepeatedSessions() {
        val r = PresenceAnalyzer.analyze(
            listOf(
                trial(5000, 100, session = 1),
                trial(5200, 120, session = 1),
                trial(4800, 100, session = 1),
                trial(6100, 80, session = 2),
                trial(5700, 110, session = 2),
                trial(6300, 90, session = 2)
            )
        )
        assertEquals(PresenceStatus.PREFETCH_CROSS_SESSION, r.status)
        assertEquals(2, r.sessionCount)
    }

    @Test
    fun detectsRevealNetworkDependency() {
        val r = PresenceAnalyzer.analyze(
            listOf(
                trial(1500, 5000),
                trial(1800, 4300),
                trial(1200, 5200)
            )
        )
        assertEquals(PresenceStatus.REVEAL_REQUIRES_NETWORK, r.status)
    }
}
