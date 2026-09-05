package com.example.pokeredge.model

data class Card(val rank: Int, val suit: Int) {
    init {
        require(rank in 2..14)
        require(suit in 0..3)
    }

    val short: String
        get() = rankText(rank) + suitText(suit)

    companion object {
        fun rankText(rank: Int): String = when (rank) {
            14 -> "A"
            13 -> "K"
            12 -> "Q"
            11 -> "J"
            10 -> "T"
            else -> rank.toString()
        }

        fun suitText(suit: Int): String = when (suit) {
            0 -> "♠"
            1 -> "♥"
            2 -> "♦"
            else -> "♣"
        }

        fun encode(card: Card): String = card.rank.toString() + ":" + card.suit

        fun decode(raw: String): Card? {
            val p = raw.split(':')
            if (p.size != 2) return null
            return runCatching { Card(p[0].toInt(), p[1].toInt()) }.getOrNull()
        }

        fun deck(): List<Card> = buildList {
            for (rank in 2..14) for (suit in 0..3) add(Card(rank, suit))
        }
    }
}
