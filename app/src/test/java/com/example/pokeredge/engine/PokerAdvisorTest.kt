package com.example.pokeredge.engine

import com.example.pokeredge.model.Card
import com.example.pokeredge.model.GameState
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerAdvisorTest {
    @Test
    fun royalFlushHasVeryHighEquityHeadsUp() {
        val state = GameState(
            hole = listOf(Card(14,0), Card(13,0)),
            board = listOf(Card(12,0), Card(11,0), Card(10,0), Card(2,1), Card(3,2)),
            pot = 1000,
            call = 100,
            opponents = 1,
            chipStep = 100
        )
        val result = PokerAdvisor.analyze(state)
        assertTrue(result.equityPercent > 99.0)
        assertTrue(result.recommendation == "RAISE")
    }

    @Test
    fun duplicateCardsRejectedByAdvisor() {
        val state = GameState(
            hole = listOf(Card(14,0), Card(14,0)),
            board = emptyList(),
            pot = 0,
            call = 0,
            opponents = 1,
            chipStep = 100
        )
        var failed = false
        try {
            PokerAdvisor.analyze(state)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
