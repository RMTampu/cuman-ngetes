package com.example.trafficmarker.recorder

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.example.trafficmarker.model.TrafficEvent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object TrafficRecorder {
    const val SAVE_FOLDER = "TrafficMarkerRecorder"
    private const val MAX_EVENTS = 10000

    data class RecordedEvent(
        val event: TrafficEvent,
        val stepIndex: Int?
    )

    private val events = ArrayDeque<RecordedEvent>()
    @Volatile private var enabled = false

    @Synchronized
    fun start(clearPrevious: Boolean = false) {
        if (clearPrevious) events.clear()
        enabled = true
    }

    fun stop() { enabled = false }
    fun isEnabled(): Boolean = enabled

    @Synchronized
    fun onEvent(event: TrafficEvent) {
        if (!enabled) return
        events.addLast(RecordedEvent(event, StepRecorder.currentStepIndex()))
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    @Synchronized
    fun all(): List<RecordedEvent> = events.toList()

    @Synchronized
    fun range(fromMs: Long, toMs: Long): List<RecordedEvent> =
        events.filter { it.event.timeMs in fromMs..toMs }

    @Synchronized
    fun recentText(limit: Int = 12): String {
        if (events.isEmpty()) return "Recorder: belum ada data."
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return events.takeLast(limit).joinToString("\n") { r ->
            val e = r.event
            val step = r.stepIndex?.let { " S$it" } ?: ""
            fmt.format(Date(e.timeMs)) + step + " " + e.direction.name +
                " " + e.host + ":" + e.port + " " + e.sizeBytes + " B"
        }
    }

    fun saveJsonl(context: Context): String {
        val snapshot = all()
        require(snapshot.isNotEmpty()) { "Recorder belum memiliki data" }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "TrafficRecorder-$stamp.jsonl"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/x-ndjson")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SAVE_FOLDER
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Gagal membuat file recorder")

        try {
            resolver.openOutputStream(uri, "w")!!.bufferedWriter(Charsets.UTF_8).use { writer ->
                snapshot.forEach { r ->
                    val e = r.event
                    writer.append(
                        JSONObject().apply {
                            put("timeMs", e.timeMs)
                            put("step", r.stepIndex ?: JSONObject.NULL)
                            put("host", e.host)
                            put("port", e.port)
                            put("direction", e.direction.name)
                            put("sizeBytes", e.sizeBytes)
                        }.toString()
                    )
                    writer.newLine()
                }
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return "Download/$SAVE_FOLDER/$name"
    }
}
