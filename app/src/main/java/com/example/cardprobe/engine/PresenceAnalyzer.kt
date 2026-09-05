package com.example.cardprobe.engine

import com.example.cardprobe.model.PresenceReport
import com.example.cardprobe.model.PresenceStatus
import com.example.cardprobe.model.ProbeTrial
import kotlin.math.ceil
import kotlin.math.max

object PresenceAnalyzer {
    private const val MIN_TRIALS = 3

    fun analyze(trials: List<ProbeTrial>): PresenceReport {
        if (trials.size < MIN_TRIALS) {
            return PresenceReport(
                PresenceStatus.NEED_MORE_TRIALS,
                trials.size,
                0,
                0,
                "Butuh minimal 3 hand. Data ini hanya menguji timing metadata, bukan isi kartu."
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

        return when {
            prefetch >= needed && reveal < needed ->
                PresenceReport(
                    PresenceStatus.PREFETCH_CANDIDATE,
                    trials.size,
                    prefetch,
                    reveal,
                    "Trafik lebih kuat saat kartu tertutup dan relatif tenang saat reveal. Ini kandidat prefetch, bukan bukti nilai hole-card sudah diketahui."
                )
            reveal >= needed ->
                PresenceReport(
                    PresenceStatus.REVEAL_REQUIRES_NETWORK,
                    trials.size,
                    prefetch,
                    reveal,
                    "Trafik inbound signifikan muncul konsisten di sekitar reveal. Data penting kemungkinan masih datang saat kartu dibuka."
                )
            else ->
                PresenceReport(
                    PresenceStatus.INCONCLUSIVE,
                    trials.size,
                    prefetch,
                    reveal,
                    "Metadata belum membedakan apakah data kartu sudah tersedia sebelum reveal."
                )
        }
    }
}
