package com.example.trafficmarker.net

import com.example.trafficmarker.diagnostic.DiagnosticStore
import com.example.trafficmarker.model.TrafficEvent
import com.example.trafficmarker.store.AlertManager
import com.example.trafficmarker.store.MarkerStore
import com.example.trafficmarker.store.SessionStore
import java.util.concurrent.CopyOnWriteArraySet

object TrafficBus {
    fun interface Listener { fun onEvent(event: TrafficEvent) }
    private val listeners = CopyOnWriteArraySet<Listener>()

    fun add(listener: Listener) { listeners += listener }
    fun remove(listener: Listener) { listeners -= listener }

    fun emit(raw: TrafficEvent) {
        DiagnosticStore.busEvent(raw.host, raw.port, raw.direction.name, raw.sizeBytes)
        SessionStore.add(raw)
        val marker = MarkerStore.findMatch(raw)
        val event = if (marker != null) raw.copy(matched = true) else raw
        if (marker != null) AlertManager.fire(marker, event)
        listeners.forEach { it.onEvent(event) }
    }
}
