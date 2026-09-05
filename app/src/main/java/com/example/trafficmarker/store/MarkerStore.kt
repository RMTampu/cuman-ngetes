package com.example.trafficmarker.store

import android.content.Context
import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.Marker
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

    fun addFrom(event: TrafficEvent): Marker {
        val marker = Marker(
            id = UUID.randomUUID().toString(),
            host = event.host,
            port = event.port,
            direction = event.direction,
            centerSize = event.sizeBytes
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
        items.forEach { m ->
            array.put(JSONObject().apply {
                put("id", m.id)
                put("host", m.host)
                put("port", m.port)
                put("direction", m.direction.name)
                put("centerSize", m.centerSize)
                put("tolerancePercent", m.tolerancePercent)
            })
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    private fun load() {
        items.clear()
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                items += Marker(
                    id = o.getString("id"),
                    host = o.getString("host"),
                    port = o.getInt("port"),
                    direction = Direction.valueOf(o.getString("direction")),
                    centerSize = o.getInt("centerSize"),
                    tolerancePercent = o.optInt("tolerancePercent", 25)
                )
            }
        }
    }
}
