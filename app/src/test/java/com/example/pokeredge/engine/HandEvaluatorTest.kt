package com.example.pokeredge.engine

import com.example.pokeredge.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandEvaluatorTest {
    private fun c(rank: Int, suit: Int) = Card(rank, suit)

    @Test
    fun straightFlushBeatsQuads() {
        val sf = HandEvaluator.evaluate(
            listOf(c(14,0), c(13,0), c(12,0), c(11,0), c(10,0), c(2,1), c(3,2))
        )
        val quads = HandEvaluator.evaluate(
            listOf(c(9,0), c(9,1), c(9,2), c(9,3), c(14,0), c(2,1), c(3,2))
        )
        assertEquals("Straight Flush", sf.name)
        assertEquals("Four of a Kind", quads.name)
        assertTrue(sf > quads)
    }

    @Test
    fun detectsWheelStraight() {
        val v = HandEvaluator.evaluate(
            listOf(c(14,0), c(5,1), c(4,2), c(3,3), c(2,0), c(11,1), c(9,2))
        )
        assertEquals("Straight", v.name)
    }
}
