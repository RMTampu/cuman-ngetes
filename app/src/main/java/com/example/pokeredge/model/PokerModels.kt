package com.example.pokeredge.model

data class GameState(
    val hole: List<Card>,
    val board: List<Card>,
    val pot: Long,
    val call: Long,
    val opponents: Int,
    val chipStep: Long
)

data class AnalysisResult(
    val handName: String,
    val equityPercent: Double,
    val potOddsPercent: Double,
    val improvementOuts: Int,
    val recommendation: String,
    val detail: String
)
