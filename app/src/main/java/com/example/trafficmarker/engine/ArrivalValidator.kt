package com.example.trafficmarker.engine

import com.example.trafficmarker.model.Marker
import com.example.trafficmarker.recorder.StepRecord
import kotlin.math.roundToInt

enum class ArrivalProof {
    NO_DATA,
    ARRIVED,
    STEP_LINKED,
    LEAD_CANDIDATE,
    VALIDATED,
    EXACT
}

data class LeadMetric(
    val leadSteps: Int,
    val truePositive: Int,
    val falsePositive: Int,
    val falseNegative: Int,
    val precision: Double,
    val recall: Double
)

data class ArrivalValidation(
    val proof: ArrivalProof,
    val targetTitle: String,
    val targetOccurrences: Int,
    val best: LeadMetric?,
    val message: String
)

object ArrivalValidator {
    private const val MAX_LEAD = 20
    private const val MATCH_THRESHOLD = 0.78

    fun validate(marker: Marker, steps: List<StepRecord>): ArrivalValidation {
        val targets = steps.filter { it.label.equals(marker.title, ignoreCase = true) }
        if (steps.isEmpty()) {
            return ArrivalValidation(ArrivalProof.NO_DATA, marker.title, 0, null, "Belum ada step terekam")
        }
        if (targets.isEmpty()) {
            return ArrivalValidation(
                ArrivalProof.ARRIVED,
                marker.title,
                0,
                null,
                "Trafik terekam, tetapi belum ada ground-truth '" + marker.title + "'"
            )
        }
        if (marker.samples.isEmpty()) {
            return ArrivalValidation(
                ArrivalProof.ARRIVED,
                marker.title,
                targets.size,
                null,
                "Marker belum memiliki sampel momen"
            )
        }

        val metrics = (1..MAX_LEAD).map { lead -> evaluateLead(marker, steps, lead) }
        val best = metrics.maxWithOrNull(
            compareBy<LeadMetric> { it.precision * it.recall }
                .thenBy { it.truePositive }
                .thenByDescending { it.falsePositive + it.falseNegative }
        )

        if (best == null || best.truePositive == 0) {
            return ArrivalValidation(
                ArrivalProof.STEP_LINKED,
                marker.title,
                targets.size,
                best,
                "Belum ditemukan sinyal yang konsisten sebelum hasil aktual"
            )
        }

        val proof = when {
            targets.size >= 5 &&
                best.falsePositive == 0 &&
                best.falseNegative == 0 &&
                best.precision == 1.0 &&
                best.recall == 1.0 -> ArrivalProof.VALIDATED
            best.truePositive >= 2 && best.precision >= 0.8 && best.recall >= 0.6 ->
                ArrivalProof.LEAD_CANDIDATE
            else -> ArrivalProof.STEP_LINKED
        }

        val pctP = (best.precision * 100).roundToInt()
        val pctR = (best.recall * 100).roundToInt()
        val message = when (proof) {
            ArrivalProof.VALIDATED ->
                "Terbukti pada dataset uji: pola marker muncul " + best.leadSteps +
                    " step lebih awal (precision " + pctP + "%, recall " + pctR +
                    "%). Ini belum berarti EXACT secara universal."
            ArrivalProof.LEAD_CANDIDATE ->
                "Kandidat lead +" + best.leadSteps + ": precision " + pctP +
                    "%, recall " + pctR + "%. Perlu lebih banyak sampel."
            else ->
                "Ada korelasi step, tetapi belum cukup kuat untuk menyatakan data dikirim lebih awal."
        }

        return ArrivalValidation(proof, marker.title, targets.size, best, message)
    }

    private fun evaluateLead(marker: Marker, steps: List<StepRecord>, lead: Int): LeadMetric {
        var tp = 0
        var fp = 0
        var fn = 0

        for (i in steps.indices) {
            val source = steps[i]
            val predicted = MomentFingerprintEngine.buildBursts(source.events, source.resultAtMs)
                .any { MomentFingerprintEngine.score(marker, it) >= MATCH_THRESHOLD }

            val targetIndex = i + lead
            val actuallyTarget =
                targetIndex in steps.indices &&
                    steps[targetIndex].label.equals(marker.title, ignoreCase = true)

            if (predicted && actuallyTarget) tp++
            else if (predicted && !actuallyTarget) fp++
            else if (!predicted && actuallyTarget) fn++
        }

        val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        return LeadMetric(lead, tp, fp, fn, precision, recall)
    }

    fun format(result: ArrivalValidation): String = buildString {
        append("ARRIVAL PROOF: ").append(result.proof.name)
        append("\nTarget: ").append(result.targetTitle)
        append("\nGround-truth: ").append(result.targetOccurrences)
        result.best?.let {
            append("\nLead terbaik: +").append(it.leadSteps)
            append("\nTP/FP/FN: ").append(it.truePositive).append("/")
                .append(it.falsePositive).append("/").append(it.falseNegative)
            append("\nPrecision: ").append((it.precision * 100).roundToInt()).append("%")
            append(" • Recall: ").append((it.recall * 100).roundToInt()).append("%")
        }
        append("\n").append(result.message)
    }
}
