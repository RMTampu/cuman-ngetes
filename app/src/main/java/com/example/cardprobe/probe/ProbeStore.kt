package com.example.cardprobe.probe

import android.content.Context
import com.example.cardprobe.engine.PresenceAnalyzer
import com.example.cardprobe.model.Direction
import com.example.cardprobe.model.PresenceReport
import com.example.cardprobe.model.ProbeEvent
import com.example.cardprobe.model.ProbeTrial
import java.util.ArrayDeque

object ProbeStore {
    private const val PREFS = "card_probe_dataset"
    private const val KEY_TRIALS = "trials"
    private const val MAX_EVENTS = 20000

    private val events = ArrayDeque<ProbeEvent>()
    private val trials = ArrayList<ProbeTrial>()

    @Volatile private var activeDealAtMs: Long? = null
    @Volatile private var sessionId: Long = System.currentTimeMillis()
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        load()
    }

    @Synchronized
    fun newCaptureSession() {
        sessionId = System.currentTimeMillis()
        events.clear()
        activeDealAtMs = null
    }

    @Synchronized
    fun onEvent(event: ProbeEvent) {
        events.addLast(event)
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    @Synchronized
    fun markDeal(now: Long = System.currentTimeMillis()): Int {
        activeDealAtMs = now
        return trials.size + 1
    }

    @Synchronized
    fun cancelActive() {
        activeDealAtMs = null
    }

    fun activeDeal(): Long? = activeDealAtMs

    @Synchronized
    fun finishReveal(revealAtMs: Long): ProbeTrial? {
        val dealAt = activeDealAtMs ?: return null
        if (revealAtMs <= dealAt) return null

        val baseline = inboundStats(dealAt - 2500L, dealAt - 500L)
        val deal = inboundStats(dealAt - 300L, dealAt + 1700L)
        val reveal = inboundStats(revealAtMs - 900L, revealAtMs + 1200L)

        val trial = ProbeTrial(
            sessionId = sessionId,
            dealAtMs = dealAt,
            revealAtMs = revealAtMs,
            baselineInBytes = baseline.first,
            baselineInEvents = baseline.second,
            dealInBytes = deal.first,
            dealInEvents = deal.second,
            revealInBytes = reveal.first,
            revealInEvents = reveal.second
        )
        trials.add(trial)
        activeDealAtMs = null
        persist()
        return trial
    }

    @Synchronized
    fun report(): PresenceReport = PresenceAnalyzer.analyze(trials.toList())

    @Synchronized
    fun allTrials(): List<ProbeTrial> = trials.toList()

    @Synchronized
    fun resetDataset() {
        events.clear()
        trials.clear()
        activeDealAtMs = null
        persist()
    }

    @Synchronized
    fun summary(): String {
        val report = report()
        val active = if (activeDealAtMs == null) "SIAP" else "MENUNGGU REVEAL"
        return buildString {
            append("HAND: ").append(trials.size).append(" selesai • ").append(active).append('\n')
            append("STATUS: ").append(report.status.name).append('\n')
            append("Vote prefetch: ").append(report.prefetchVotes)
                .append(" • reveal-network: ").append(report.revealVotes).append('\n')
            append(report.message)
        }
    }

    @Synchronized
    private fun inboundStats(from: Long, to: Long): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        events.forEach { e ->
            if (
                e.timeMs in from..to &&
                (e.direction == Direction.IN || e.direction == Direction.UDP_IN)
            ) {
                bytes += e.sizeBytes.toLong()
                count++
            }
        }
        return bytes to count
    }

    @Synchronized
    private fun persist() {
        val ctx = appContext ?: return
        val value = trials.joinToString("\n") { t ->
            listOf(
                t.sessionId,
                t.dealAtMs,
                t.revealAtMs,
                t.baselineInBytes,
                t.baselineInEvents,
                t.dealInBytes,
                t.dealInEvents,
                t.revealInBytes,
                t.revealInEvents
            ).joinToString(",")
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRIALS, value)
            .apply()
    }

    @Synchronized
    private fun load() {
        trials.clear()
        val ctx = appContext ?: return
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRIALS, "")
            .orEmpty()
        raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val p = line.split(',')
            if (p.size != 9) return@forEach
            runCatching {
                ProbeTrial(
                    sessionId = p[0].toLong(),
                    dealAtMs = p[1].toLong(),
                    revealAtMs = p[2].toLong(),
                    baselineInBytes = p[3].toLong(),
                    baselineInEvents = p[4].toInt(),
                    dealInBytes = p[5].toLong(),
                    dealInEvents = p[6].toInt(),
                    revealInBytes = p[7].toLong(),
                    revealInEvents = p[8].toInt()
                )
            }.getOrNull()?.let(trials::add)
        }
    }
}
