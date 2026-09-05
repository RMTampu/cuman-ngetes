package com.example.cardprobe.engine

import com.example.cardprobe.model.PresenceReport
import com.example.cardprobe.model.PresenceStatus
import com.example.cardprobe.model.ProbeTrial
import kotlin.math.ceil
import kotlin.math.max

object PresenceAnalyzer {
    private const val MIN_TRIALS = 3
    private const val CROSS_SESSION_MIN_TRIALS = 6

    fun analyze(trials: List<ProbeTrial>): PresenceReport {
        val sessions = trials.map { it.sessionId }.distinct().size

        if (trials.size < MIN_TRIALS) {
            return PresenceReport(
                PresenceStatus.NEED_MORE_TRIALS,
                trials.size,
                sessions,
                0,
                0,
                "Butuh minimal 3 hand. Untuk bukti lintas sesi gunakan minimal 6 hand pada minimal 2 sesi."
            )
        }

        var prefetch = 0
        var reveal = 0

        trials.forEach { t ->
            val baselineAdjusted = max(512L, t.baselineInBytes)
            val dealSignal = t.dealInBytes >= baselineAdjusted + 512L
            val revealQuiet = t.revealInBytes <= max(512L, t.dealInBytes / 10L)
            if (dealSignal && revealQuiet) prefetch++

            val revealSignal =
                t.revealInBytes >= 1024L &&
                    t.revealInBytes >= max(1024L, t.dealInBytes / 3L)
            if (revealSignal) reveal++
        }

        val needed = ceil(trials.size * 0.67).toInt()
        val crossNeeded = ceil(trials.size * 0.75).toInt()
        val prefetchCandidate = prefetch >= needed && reveal < needed
        val crossSession =
            trials.size >= CROSS_SESSION_MIN_TRIALS &&
                sessions >= 2 &&
                prefetch >= crossNeeded &&
                reveal < needed

        return when {
            crossSession ->
                PresenceReport(
                    PresenceStatus.PREFETCH_CROSS_SESSION,
                    trials.size,
                    sessions,
                    prefetch,
                    reveal,
                    "Kandidat prefetch bertahan lintas sesi. Ini bukti timing yang lebih kuat, tetapi tetap tidak membuktikan nilai hole-card tersedia dalam bentuk yang dapat dibaca."
                )
            prefetchCandidate ->
                PresenceReport(
                    PresenceStatus.PREFETCH_CANDIDATE,
                    trials.size,
                    sessions,
                    prefetch,
                    reveal,
                    "Trafik lebih kuat saat kartu tertutup dan relatif tenang saat reveal. Ulangi pada sesi target baru untuk menguji apakah pola bertahan."
                )
            reveal >= needed ->
                PresenceReport(
                    PresenceStatus.REVEAL_REQUIRES_NETWORK,
                    trials.size,
                    sessions,
                    prefetch,
                    reveal,
                    "Trafik inbound signifikan muncul konsisten di sekitar reveal. Data penting kemungkinan masih datang saat kartu dibuka."
                )
            else ->
                PresenceReport(
                    PresenceStatus.INCONCLUSIVE,
                    trials.size,
                    sessions,
                    prefetch,
                    reveal,
                    "Metadata belum membedakan apakah data kartu sudah tersedia sebelum reveal."
                )
        }
    }
}
