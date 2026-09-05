package com.example.trafficmarker.store

import android.content.Context
import com.example.trafficmarker.model.BurstSignature
import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.model.MomentSample
import com.example.trafficmarker.model.TrafficEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

object MarkerStore {
    private const val PREF = "markers"
    private const val KEY = "items"
    private lateinit var context: Context
    private val items = CopyOnWriteArrayList<Marker>()
    private val lastAlert = HashMap<String, Long>()

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    fun all(): List<Marker> = items.toList()

    fun addFrom(event: TrafficEvent, title: String = "Legacy Marker"): Marker {
        val marker = Marker(
            id = UUID.randomUUID().toString(),
            host = event.host,
            port = event.port,
            direction = event.direction,
            centerSize = event.sizeBytes,
            title = title.trim().ifBlank { "Legacy Marker" }
        )
        items += marker
        save()
        return marker
    }

    @Synchronized
    fun addMomentSample(title: String, sample: MomentSample): Marker {
        val cleanTitle = title.trim().ifBlank { "Tanpa Judul" }
        val existingIndex = items.indexOfFirst { it.title.equals(cleanTitle, ignoreCase = true) }

        if (existingIndex >= 0) {
            val existing = items[existingIndex]
            val updated = existing.copy(
                title = cleanTitle,
                samples = (existing.samples + sample).takeLast(12)
            )
            items[existingIndex] = updated
            save()
            return updated
        }

        val reference = sample.bursts.maxByOrNull {
            it.totalBytes.toLong() + (it.eventCount * 96L)
        }

        val marker = Marker(
            id = UUID.randomUUID().toString(),
            host = reference?.endpointGroup ?: "-",
            port = reference?.port ?: 0,
            direction = if ((reference?.totalIn ?: 0) >= (reference?.totalOut ?: 0)) Direction.IN else Direction.OUT,
            centerSize = reference?.totalBytes ?: 0,
            tolerancePercent = 25,
            title = cleanTitle,
            samples = listOf(sample)
        )
        items += marker
        save()
        return marker
    }

    fun clear() {
        items.clear()
        synchronized(lastAlert) { lastAlert.clear() }
        save()
    }

    @Synchronized
    fun importUnique(imported: List<Marker>): Int {
        val signatures = items.mapTo(HashSet()) { signatureOf(it) }
        val ids = items.mapTo(HashSet()) { it.id }
        var added = 0

        imported.forEach { marker ->
            val signature = signatureOf(marker)
            if (signature in signatures) return@forEach

            val safeMarker = if (marker.id in ids) {
                marker.copy(id = UUID.randomUUID().toString())
            } else {
                marker
            }
            items += safeMarker
            signatures += signature
            ids += safeMarker.id
            added++
        }

        if (added > 0) save()
        return added
    }

    private fun signatureOf(marker: Marker): String =
        marker.title.lowercase() + "|" +
            marker.host.lowercase() + "|" +
            marker.port + "|" +
            marker.direction.name + "|" +
            marker.centerSize + "|" +
            marker.samples.size

    fun findMatch(event: TrafficEvent): Marker? {
        val marker = items.firstOrNull { it.matches(event) } ?: return null
        val now = System.currentTimeMillis()
        synchronized(lastAlert) {
            val last = lastAlert[marker.id] ?: 0L
            if (now - last < 5000L) return null
            lastAlert[marker.id] = now
        }
        return marker
    }

    private fun save() {
        val array = JSONArray()
        items.forEach { array.put(markerToJson(it)) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    private fun load() {
        items.clear()
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                items += markerFromJson(array.getJSONObject(i))
            }
        }
    }

    fun markerToJson(marker: Marker): JSONObject = JSONObject().apply {
        put("id", marker.id)
        put("title", marker.title)
        put("host", marker.host)
        put("port", marker.port)
        put("direction", marker.direction.name)
        put("centerSize", marker.centerSize)
        put("tolerancePercent", marker.tolerancePercent)
        put("samples", JSONArray().apply {
            marker.samples.forEach { put(sampleToJson(it)) }
        })
    }

    fun markerFromJson(o: JSONObject): Marker {
        val samples = ArrayList<MomentSample>()
        val sampleArray = o.optJSONArray("samples") ?: JSONArray()
        for (i in 0 until sampleArray.length()) {
            samples += sampleFromJson(sampleArray.getJSONObject(i))
        }

        return Marker(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            host = o.optString("host", "-"),
            port = o.optInt("port", 0),
            direction = runCatching { Direction.valueOf(o.optString("direction", Direction.IN.name)) }
                .getOrDefault(Direction.IN),
            centerSize = o.optInt("centerSize", 0),
            tolerancePercent = o.optInt("tolerancePercent", 25),
            title = o.optString("title", "Legacy Marker"),
            samples = samples
        )
    }

    private fun sampleToJson(sample: MomentSample): JSONObject = JSONObject().apply {
        put("capturedAtMs", sample.capturedAtMs)
        put("beforeMs", sample.beforeMs)
        put("afterMs", sample.afterMs)
        put("bursts", JSONArray().apply {
            sample.bursts.forEach { b ->
                put(JSONObject().apply {
                    put("endpointGroup", b.endpointGroup)
                    put("port", b.port)
                    put("startOffsetMs", b.startOffsetMs)
                    put("durationMs", b.durationMs)
                    put("totalIn", b.totalIn)
                    put("totalOut", b.totalOut)
                    put("countIn", b.countIn)
                    put("countOut", b.countOut)
                    put("maxIn", b.maxIn)
                    put("maxOut", b.maxOut)
                    put("sequence", b.sequence)
                })
            }
        })
    }

    private fun sampleFromJson(o: JSONObject): MomentSample {
        val bursts = ArrayList<BurstSignature>()
        val array = o.optJSONArray("bursts") ?: JSONArray()
        for (i in 0 until array.length()) {
            val b = array.getJSONObject(i)
            bursts += BurstSignature(
                endpointGroup = b.optString("endpointGroup", "-"),
                port = b.optInt("port", 0),
                startOffsetMs = b.optLong("startOffsetMs", 0L),
                durationMs = b.optLong("durationMs", 0L),
                totalIn = b.optInt("totalIn", 0),
                totalOut = b.optInt("totalOut", 0),
                countIn = b.optInt("countIn", 0),
                countOut = b.optInt("countOut", 0),
                maxIn = b.optInt("maxIn", 0),
                maxOut = b.optInt("maxOut", 0),
                sequence = b.optString("sequence", "")
            )
        }
        return MomentSample(
            capturedAtMs = o.optLong("capturedAtMs", 0L),
            beforeMs = o.optLong("beforeMs", 8000L),
            afterMs = o.optLong("afterMs", 1500L),
            bursts = bursts
        )
    }
}
