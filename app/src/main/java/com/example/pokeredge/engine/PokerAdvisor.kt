package com.example.pokeredge.engine

import com.example.pokeredge.model.AnalysisResult
import com.example.pokeredge.model.Card
import com.example.pokeredge.model.GameState
import java.util.Locale

object PokerAdvisor {
    fun analyze(state: GameState): AnalysisResult {
        require(state.hole.size == 2)
        require(state.board.size <= 5)
        require((state.hole + state.board).toSet().size == state.hole.size + state.board.size)

        val visible = state.hole + state.board
        val handName = if (visible.size >= 5) {
            HandEvaluator.evaluate(visible).name
        } else {
            preflopName(state.hole)
        }

        val equity = EquityCalculator.estimate(
            state.hole,
            state.board,
            state.opponents
        )
        val potOdds = if (state.call <= 0L) 0.0
        else state.call * 100.0 / (state.pot + state.call).coerceAtLeast(1L)

        val outs = improvementOuts(state.hole, state.board)
        val recommendation = recommendation(equity, potOdds, state.call)

        val detail = buildString {
            append("Equity vs ").append(state.opponents)
                .append(" lawan acak: ").append(fmt(equity)).append("%")
            if (state.call > 0L) {
                append(" • pot odds ").append(fmt(potOdds)).append("%")
            }
            if (state.board.size in 3..4) {
                append(" • outs peningkatan ").append(outs)
            }
        }

        return AnalysisResult(
            handName = handName,
            equityPercent = equity,
            potOddsPercent = potOdds,
            improvementOuts = outs,
            recommendation = recommendation,
            detail = detail
        )
    }

    fun improvementOuts(hole: List<Card>, board: List<Card>): Int {
        if (hole.size != 2 || board.size !in 3..4) return 0
        val current = HandEvaluator.evaluate(hole + board)
        val known = (hole + board).toSet()
        return Card.deck().count { next ->
            next !in known &&
                HandEvaluator.evaluate(hole + board + next).score > current.score
        }
    }

    private fun recommendation(equity: Double, potOdds: Double, call: Long): String {
        if (call <= 0L) {
            return when {
                equity >= 68.0 -> "RAISE / VALUE"
                equity >= 43.0 -> "CHECK / BET"
                else -> "CHECK"
            }
        }

        val edge = equity - potOdds
        return when {
            edge < -2.0 -> "FOLD"
            equity >= 62.0 && edge >= 15.0 -> "RAISE"
            edge >= 2.0 -> "CALL"
            else -> "MARGINAL / FOLD"
        }
    }

    private fun preflopName(hole: List<Card>): String {
        val a = hole[0]
        val b = hole[1]
        if (a.rank == b.rank) return "Pocket " + Card.rankText(a.rank) + Card.rankText(b.rank)
        val hi = maxOf(a.rank, b.rank)
        val lo = minOf(a.rank, b.rank)
        val suited = if (a.suit == b.suit) " suited" else " offsuit"
        return Card.rankText(hi) + Card.rankText(lo) + suited
    }

    private fun fmt(value: Double): String =
        String.format(Locale.US, "%.1f", value)
}
