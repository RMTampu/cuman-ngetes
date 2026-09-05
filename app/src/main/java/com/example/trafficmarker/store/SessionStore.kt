package com.example.trafficmarker.store

import com.example.trafficmarker.model.TrafficEvent
import java.util.ArrayDeque

object SessionStore {
    private const val MAX_EVENTS = 3000
    private val events = ArrayDeque<TrafficEvent>()
    @Volatile private var recording = false
    @Volatile private var startedAt = 0L

    @Synchronized
    fun start(clearPrevious: Boolean = true) {
        if (clearPrevious) events.clear()
        startedAt = System.currentTimeMillis()
        recording = true
    }

    @Synchronized
    fun stop() {
        recording = false
    }

    fun isRecording(): Boolean = recording

    @Synchronized
    fun add(event: TrafficEvent) {
        if (!recording) return
        events.addLast(event)
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    @Synchronized
    fun all(): List<TrafficEvent> = events.toList()

    @Synchronized
    fun recent(windowMs: Long, nowMs: Long = System.currentTimeMillis()): List<TrafficEvent> {
        val from = nowMs - windowMs
        return events.filter { it.timeMs in from..nowMs }
    }

    @Synchronized
    fun range(fromMs: Long, toMs: Long): List<TrafficEvent> =
        events.filter { it.timeMs in fromMs..toMs }

    @Synchronized
    fun size(): Int = events.size

    fun startedAt(): Long = startedAt
}
