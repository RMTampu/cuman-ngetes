package com.example.pokeredge.store

import android.content.Context
import com.example.pokeredge.model.Card
import com.example.pokeredge.model.GameState

object GameStateStore {
    private const val PREFS = "poker_edge_state"
    private const val KEY_HOLE = "hole"
    private const val KEY_BOARD = "board"
    private const val KEY_POT = "pot"
    private const val KEY_CALL = "call"
    private const val KEY_OPPONENTS = "opponents"
    private const val KEY_STEP = "chip_step"

    private val hole = ArrayList<Card>()
    private val board = ArrayList<Card>()

    private var pot = 0L
    private var call = 0L
    private var opponents = 1
    private var chipStep = 100L

    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        load()
    }

    @Synchronized
    fun snapshot(): GameState =
        GameState(hole.toList(), board.toList(), pot, call, opponents, chipStep)

    @Synchronized
    fun addHole(card: Card): Boolean {
        if (hole.size >= 2 || card in hole || card in board) return false
        hole += card
        persist()
        return true
    }

    @Synchronized
    fun addBoard(card: Card): Boolean {
        if (board.size >= 5 || card in hole || card in board) return false
        board += card
        persist()
        return true
    }

    @Synchronized
    fun undoLastCard(): Boolean {
        when {
            board.isNotEmpty() -> board.removeAt(board.lastIndex)
            hole.isNotEmpty() -> hole.removeAt(hole.lastIndex)
            else -> return false
        }
        persist()
        return true
    }

    @Synchronized
    fun adjustPot(delta: Long) {
        pot = (pot + delta).coerceAtLeast(0L)
        persist()
    }

    @Synchronized
    fun adjustCall(delta: Long) {
        call = (call + delta).coerceAtLeast(0L)
        persist()
    }

    @Synchronized
    fun adjustOpponents(delta: Int) {
        opponents = (opponents + delta).coerceIn(1, 5)
        persist()
    }

    @Synchronized
    fun cycleChipStep() {
        chipStep = when (chipStep) {
            10L -> 50L
            50L -> 100L
            100L -> 500L
            500L -> 1000L
            1000L -> 5000L
            else -> 10L
        }
        persist()
    }

    @Synchronized
    fun resetHand() {
        hole.clear()
        board.clear()
        pot = 0L
        call = 0L
        opponents = 1
        persist()
    }

    @Synchronized
    private fun persist() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOLE, hole.joinToString(";") { Card.encode(it) })
            .putString(KEY_BOARD, board.joinToString(";") { Card.encode(it) })
            .putLong(KEY_POT, pot)
            .putLong(KEY_CALL, call)
            .putInt(KEY_OPPONENTS, opponents)
            .putLong(KEY_STEP, chipStep)
            .apply()
    }

    @Synchronized
    private fun load() {
        val ctx = appContext ?: return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        hole.clear()
        board.clear()
        p.getString(KEY_HOLE, "").orEmpty()
            .split(';')
            .mapNotNull(Card::decode)
            .take(2)
            .forEach(hole::add)
        p.getString(KEY_BOARD, "").orEmpty()
            .split(';')
            .mapNotNull(Card::decode)
            .filter { it !in hole }
            .take(5)
            .forEach(board::add)
        pot = p.getLong(KEY_POT, 0L).coerceAtLeast(0L)
        call = p.getLong(KEY_CALL, 0L).coerceAtLeast(0L)
        opponents = p.getInt(KEY_OPPONENTS, 1).coerceIn(1, 5)
        chipStep = p.getLong(KEY_STEP, 100L).takeIf { it > 0L } ?: 100L
    }
}
