package com.example.pokeredge.engine

import com.example.pokeredge.model.Card
import java.util.Random

object EquityCalculator {
    fun estimate(
        hole: List<Card>,
        board: List<Card>,
        opponents: Int,
        iterations: Int = 1800
    ): Double {
        require(hole.size == 2)
        require(board.size <= 5)
        require(opponents in 1..5)

        val known = (hole + board).toSet()
        require(known.size == hole.size + board.size)

        val remaining = Card.deck().filter { it !in known }
        val boardMissing = 5 - board.size
        val needed = opponents * 2 + boardMissing
        require(needed <= remaining.size)

        val seed = (hole + board).fold(17L) { acc, c ->
            acc * 67L + c.rank * 5L + c.suit
        }.xor(opponents.toLong() shl 24)
        val random = Random(seed)

        var equity = 0.0
        repeat(iterations) {
            val sample = remaining.toTypedArray()
            for (i in 0 until needed) {
                val j = i + random.nextInt(sample.size - i)
                val tmp = sample[i]
                sample[i] = sample[j]
                sample[j] = tmp
            }

            var p = 0
            val opponentHands = ArrayList<List<Card>>(opponents)
            repeat(opponents) {
                opponentHands += listOf(sample[p++], sample[p++])
            }

            val runout = ArrayList<Card>(boardMissing)
            repeat(boardMissing) { runout += sample[p++] }

            val fullBoard = board + runout
            val hero = HandEvaluator.evaluate(hole + fullBoard)
            val rivals = opponentHands.map { HandEvaluator.evaluate(it + fullBoard) }
            val best = (rivals + hero).maxOf { it.score }

            if (hero.score == best) {
                val ties = 1 + rivals.count { it.score == best }
                equity += 1.0 / ties
            }
        }

        return equity * 100.0 / iterations
    }
}
