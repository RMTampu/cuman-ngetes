package com.example.trafficmarker.recorder

import com.example.trafficmarker.model.TrafficEvent
import java.util.concurrent.CopyOnWriteArrayList

data class StepRecord(
    val index: Int,
    val startedAtMs: Long,
    val resultAtMs: Long,
    val label: String,
    val events: List<TrafficEvent>
)

object StepRecorder {
    private val steps = CopyOnWriteArrayList<StepRecord>()
    @Volatile private var activeStep: Int? = null
    @Volatile private var activeStartedAt = 0L
    private var nextIndex = 1

    @Synchronized
    fun reset() {
        steps.clear()
        activeStep = null
        activeStartedAt = 0L
        nextIndex = 1
    }

    @Synchronized
    fun startStep(nowMs: Long = System.currentTimeMillis()): Int {
        if (activeStep != null) return activeStep!!
        val index = nextIndex++
        activeStep = index
        activeStartedAt = nowMs
        return index
    }

    @Synchronized
    fun finishStep(label: String, nowMs: Long = System.currentTimeMillis()): StepRecord {
        val index = activeStep ?: error("Belum ada step aktif")
        val clean = label.trim().ifBlank { "Normal" }
        val events = TrafficRecorder.range(activeStartedAt, nowMs).map { it.event }
        val record = StepRecord(index, activeStartedAt, nowMs, clean, events)
        steps += record
        activeStep = null
        activeStartedAt = 0L
        return record
    }

    fun currentStepIndex(): Int? = activeStep
    fun all(): List<StepRecord> = steps.toList()
    fun count(): Int = steps.size
    fun isActive(): Boolean = activeStep != null

    fun summary(): String {
        val last = steps.lastOrNull()
        return buildString {
            append("STEP: ").append(steps.size)
            if (activeStep != null) append(" • S").append(activeStep).append(" AKTIF")
            else append(" • SIAP")
            if (last != null) {
                append("\nTerakhir S").append(last.index)
                    .append(" = ").append(last.label)
                    .append(" • ").append(last.events.size).append(" event")
                    .append(" • ").append(last.resultAtMs - last.startedAtMs).append(" ms")
            }
        }
    }
}
