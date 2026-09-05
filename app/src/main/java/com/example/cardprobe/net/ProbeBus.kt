package com.example.cardprobe.net

import com.example.cardprobe.diagnostic.ProbeDiagnostics
import com.example.cardprobe.model.ProbeEvent
import com.example.cardprobe.probe.ProbeStore

object ProbeBus {
    fun emit(event: ProbeEvent) {
        ProbeDiagnostics.busEvent(event.sizeBytes)
        ProbeStore.onEvent(event)
    }
}
