package com.example.pokeredge.engine

import com.example.pokeredge.model.Card

object HandEvaluator {
    data class HandValue(val score: Long, val name: String) : Comparable<HandValue> {
        override fun compareTo(other: HandValue): Int = score.compareTo(other.score)
    }

    fun evaluate(cards: List<Card>): HandValue {
        require(cards.size in 5..7)
        var best: HandValue? = null
        val n = cards.size
        for (a in 0 until n - 4)
            for (b in a + 1 until n - 3)
                for (c in b + 1 until n - 2)
                    for (d in c + 1 until n - 1)
                        for (e in d + 1 until n) {
                            val v = evaluate5(listOf(cards[a], cards[b], cards[c], cards[d], cards[e]))
                            if (best == null || v > best!!) best = v
                        }
        return best!!
    }

    private fun evaluate5(cards: List<Card>): HandValue {
        val ranks = cards.map { it.rank }.sortedDescending()
        val counts = ranks.groupingBy { it }.eachCount()
        val groups = counts.entries.sortedWith(
            compareByDescending<Map.Entry<Int, Int>> { it.value }
                .thenByDescending { it.key }
        )
        val flush = cards.map { it.suit }.distinct().size == 1
        val straightHigh = straightHigh(ranks)

        if (flush && straightHigh > 0) return value(8, listOf(straightHigh), "Straight Flush")
        if (groups[0].value == 4) {
            val quad = groups[0].key
            val kicker = groups.first { it.value == 1 }.key
            return value(7, listOf(quad, kicker), "Four of a Kind")
        }
        if (groups[0].value == 3 && groups.size > 1 && groups[1].value >= 2) {
            return value(6, listOf(groups[0].key, groups[1].key), "Full House")
        }
        if (flush) return value(5, ranks, "Flush")
        if (straightHigh > 0) return value(4, listOf(straightHigh), "Straight")
        if (groups[0].value == 3) {
            val kickers = groups.filter { it.value == 1 }.map { it.key }.sortedDescending()
            return value(3, listOf(groups[0].key) + kickers, "Three of a Kind")
        }
        if (groups[0].value == 2 && groups.size > 1 && groups[1].value == 2) {
            val pairs = groups.filter { it.value == 2 }.map { it.key }.sortedDescending()
            val kicker = groups.first { it.value == 1 }.key
            return value(2, pairs.take(2) + kicker, "Two Pair")
        }
        if (groups[0].value == 2) {
            val kickers = groups.filter { it.value == 1 }.map { it.key }.sortedDescending()
            return value(1, listOf(groups[0].key) + kickers, "One Pair")
        }
        return value(0, ranks, "High Card")
    }

    private fun straightHigh(ranks: List<Int>): Int {
        val unique = ranks.distinct().sortedDescending().toMutableList()
        if (14 in unique) unique.add(1)
        var run = 1
        for (i in 1 until unique.size) {
            if (unique[i - 1] - unique[i] == 1) {
                run++
                if (run >= 5) return unique[i - 4]
            } else {
                run = 1
            }
        }
        return 0
    }

    private fun value(category: Int, kickers: List<Int>, name: String): HandValue {
        var score = category.toLong()
        repeat(5) { i ->
            score = score * 15L + (kickers.getOrNull(i) ?: 0)
        }
        return HandValue(score, name)
    }
}
