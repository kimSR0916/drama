package com.drama.app.core

import kotlin.math.max
import kotlin.math.roundToInt

enum class HarshKind { ACCEL, BRAKE, TURN }

const val HARSH_PENALTY = 5
const val SAFE_DRIVER_SCORE = 95

enum class RewardKind { CHAPTER, SAFETY }

data class RewardCard(val kind: RewardKind, val title: String)

data class DriveReport(
    val safetyScore: Int,
    val harsh: Map<HarshKind, Int>,
    val playedBeats: Int,
    val progressPercent: Int,
    val durationMinutes: Int,
    val reward: RewardCard?,
)

fun buildReport(
    scenario: Scenario,
    before: Cursor,
    after: Cursor,
    playedBeats: Int,
    harsh: Map<HarshKind, Int>,
    startedAt: Long,
    endedAt: Long,
): DriveReport {
    val safetyScore = max(0, 100 - HARSH_PENALTY * harsh.values.sum())
    val total = scenario.totalBeats
    val progress = if (total == 0) 100 else (scenario.beatsBefore(after) * 100.0 / total).roundToInt()
    val finishedChapter = if (after.chapter > before.chapter) scenario.chapters.getOrNull(before.chapter) else null
    val reward = when {
        finishedChapter != null -> RewardCard(RewardKind.CHAPTER, "${finishedChapter.title} 클리어")
        safetyScore >= SAFE_DRIVER_SCORE -> RewardCard(RewardKind.SAFETY, "무사고 드라이버")
        else -> null
    }
    return DriveReport(
        safetyScore = safetyScore,
        harsh = harsh,
        playedBeats = playedBeats,
        progressPercent = progress,
        durationMinutes = max(0, ((endedAt - startedAt) / 60_000.0).roundToInt()),
        reward = reward,
    )
}
