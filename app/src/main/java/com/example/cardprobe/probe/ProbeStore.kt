package com.example.cardprobe.probe

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.cardprobe.engine.PresenceAnalyzer
import com.example.cardprobe.model.Direction
import com.example.cardprobe.model.PresenceReport
import com.example.cardprobe.model.ProbeEvent
import com.example.cardprobe.model.ProbeTrial
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object ProbeStore {
    const val EXPORT_FOLDER = "CardPresenceProbe"

    private const val PREFS = "card_probe_dataset"
    private const val KEY_TRIALS = "trials"
    private const val MAX_EVENTS = 20000

    private data class WindowStats(
        val bytes: Long,
        val events: Int,
        val topEndpoint: String,
        val topBytes: Long
    )

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
    fun newCaptureSession(): Int {
        sessionId = System.currentTimeMillis()
        events.clear()
        activeDealAtMs = null
        return sessionCount() + 1
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
            baselineInBytes = baseline.bytes,
            baselineInEvents = baseline.events,
            dealInBytes = deal.bytes,
            dealInEvents = deal.events,
            revealInBytes = reveal.bytes,
            revealInEvents = reveal.events,
            dealTopEndpoint = deal.topEndpoint,
            dealTopBytes = deal.topBytes,
            revealTopEndpoint = reveal.topEndpoint,
            revealTopBytes = reveal.topBytes
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
    fun sessionCount(): Int = trials.map { it.sessionId }.distinct().size

    @Synchronized
    fun resetDataset() {
        events.clear()
        trials.clear()
        activeDealAtMs = null
        sessionId = System.currentTimeMillis()
        persist()
    }

    @Synchronized
    fun summary(): String {
        val report = report()
        val active = if (activeDealAtMs == null) "SIAP" else "MENUNGGU REVEAL"
        return buildString {
            append("HAND: ").append(trials.size).append(" selesai • ").append(active).append('\n')
            append("SESI DATA: ").append(report.sessionCount).append('\n')
            append("STATUS: ").append(report.status.name).append('\n')
            append("Vote prefetch: ").append(report.prefetchVotes)
                .append(" • reveal-network: ").append(report.revealVotes).append('\n')
            append(report.message)
        }
    }

    @Synchronized
    fun detailText(limit: Int = 12): String {
        if (trials.isEmpty()) return "Belum ada hand."
        val sessionOrder = trials.map { it.sessionId }.distinct()
        return trials.takeLast(limit).mapIndexed { localIndex, t ->
            val absolute = trials.size - minOf(limit, trials.size) + localIndex + 1
            val session = sessionOrder.indexOf(t.sessionId) + 1
            "H" + absolute +
                " / S" + session +
                " • base ↓" + t.baselineInBytes + " B" +
                " • deal ↓" + t.dealInBytes + " B/" + t.dealInEvents +
                " [" + endpointText(t.dealTopEndpoint, t.dealTopBytes) + "]" +
                " • reveal ↓" + t.revealInBytes + " B/" + t.revealInEvents +
                " [" + endpointText(t.revealTopEndpoint, t.revealTopBytes) + "]"
        }.joinToString("\n")
    }

    @Synchronized
    fun exportCsv(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IllegalStateException("Export publik membutuhkan Android 10 atau lebih baru")
        }

        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        val name = "CardPresenceProbe-" + formatter.format(Date()) + ".csv"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_FOLDER
            )
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Tidak dapat membuat file export")

        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
            out.appendLine(
                "hand,session_id,deal_at_ms,reveal_at_ms,baseline_in_bytes,baseline_in_events," +
                    "deal_in_bytes,deal_in_events,deal_top_endpoint,deal_top_bytes," +
                    "reveal_in_bytes,reveal_in_events,reveal_top_endpoint,reveal_top_bytes"
            )
            trials.forEachIndexed { index, t ->
                out.appendLine(
                    listOf(
                        index + 1,
                        t.sessionId,
                        t.dealAtMs,
                        t.revealAtMs,
                        t.baselineInBytes,
                        t.baselineInEvents,
                        t.dealInBytes,
                        t.dealInEvents,
                        csv(t.dealTopEndpoint),
                        t.dealTopBytes,
                        t.revealInBytes,
                        t.revealInEvents,
                        csv(t.revealTopEndpoint),
                        t.revealTopBytes
                    ).joinToString(",")
                )
            }
        } ?: throw IllegalStateException("Tidak dapat membuka file export")

        return "Download/" + EXPORT_FOLDER + "/" + name
    }

    @Synchronized
    private fun inboundStats(from: Long, to: Long): WindowStats {
        var totalBytes = 0L
        var count = 0
        val byEndpoint = LinkedHashMap<String, Long>()

        events.forEach { e ->
            if (
                e.timeMs in from..to &&
                (e.direction == Direction.IN || e.direction == Direction.UDP_IN)
            ) {
                totalBytes += e.sizeBytes.toLong()
                count++
                val endpoint = e.host + ":" + e.port
                byEndpoint[endpoint] = (byEndpoint[endpoint] ?: 0L) + e.sizeBytes.toLong()
            }
        }

        val top = byEndpoint.maxByOrNull { it.value }
        return WindowStats(
            bytes = totalBytes,
            events = count,
            topEndpoint = top?.key.orEmpty(),
            topBytes = top?.value ?: 0L
        )
    }

    private fun endpointText(endpoint: String, bytes: Long): String =
        if (endpoint.isBlank()) "-" else endpoint + " " + bytes + "B"

    private fun csv(value: String): String =
        """ + value.replace(""", """") + """

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
                t.revealInEvents,
                t.dealTopEndpoint,
                t.dealTopBytes,
                t.revealTopEndpoint,
                t.revealTopBytes
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
            if (p.size != 9 && p.size != 13) return@forEach

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
                    revealInEvents = p[8].toInt(),
                    dealTopEndpoint = if (p.size >= 13) p[9] else "",
                    dealTopBytes = if (p.size >= 13) p[10].toLong() else 0L,
                    revealTopEndpoint = if (p.size >= 13) p[11] else "",
                    revealTopBytes = if (p.size >= 13) p[12].toLong() else 0L
                )
            }.getOrNull()?.let(trials::add)
        }
    }
}
