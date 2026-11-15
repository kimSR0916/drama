package com.drama.app.core

import kotlin.math.max
import kotlin.math.roundToInt

enum class HarshKind { ACCEL, BRAKE, TURN }

const val HARSH_PENALTY = 4
const val BOND_PER_LEVEL = 50
const val BOND_PER_SCENE = 3
const val BOND_PER_ANSWER = 5
const val BOND_SAFE_BONUS = 10
const val SAFE_DRIVER_SCORE = 90

enum class CardTier(val label: String) { WOOD("일반 우드"), SILVER("우수 실버"), GOLD("100점 골드") }

fun cardTier(safetyScore: Int): CardTier = when {
    safetyScore >= 100 -> CardTier.GOLD
    safetyScore >= SAFE_DRIVER_SCORE -> CardTier.SILVER
    else -> CardTier.WOOD
}

data class Bond(val points: Int, val level: Int, val title: String, val nextAt: Int)

fun bondOf(persona: Persona, points: Int): Bond {
    val level = (points / BOND_PER_LEVEL + 1).coerceAtMost(max(1, persona.bondTitles.size))
    return Bond(points, level, persona.bondTitles.getOrElse(level - 1) { "" }, level * BOND_PER_LEVEL)
}

data class RewardCard(val title: String, val tier: CardTier)

data class DriveReport(
    val safetyScore: Int,
    val harsh: Map<HarshKind, Int>,
    val playedBeats: Int,
    val progressBefore: Int,
    val progressAfter: Int,
    val bondBefore: Bond,
    val bondAfter: Bond,
    val durationMinutes: Int,
    val chapterTitle: String,
    val reward: RewardCard,
)

fun safetyScore(harsh: Map<HarshKind, Int>): Int = max(0, 100 - HARSH_PENALTY * harsh.values.sum())

fun bondGain(playedBeats: Int, answered: Int, safetyScore: Int): Int =
    playedBeats * BOND_PER_SCENE + answered * BOND_PER_ANSWER + if (playedBeats > 0 && safetyScore >= SAFE_DRIVER_SCORE) BOND_SAFE_BONUS else 0

fun buildReport(
    scenario: Scenario,
    before: SaveData,
    after: SaveData,
    playedBeats: Int,
    harsh: Map<HarshKind, Int>,
    startedAt: Long,
    endedAt: Long,
): DriveReport {
    val score = safetyScore(harsh)
    val chapter = scenario.chapters.getOrNull(before.cursor.chapter) ?: scenario.chapters.last()
    return DriveReport(
        safetyScore = score,
        harsh = harsh,
        playedBeats = playedBeats,
        progressBefore = scenario.progressPercent(before.cursor),
        progressAfter = scenario.progressPercent(after.cursor),
        bondBefore = bondOf(scenario.persona, before.bond),
        bondAfter = bondOf(scenario.persona, after.bond),
        durationMinutes = max(0, ((endedAt - startedAt) / 60_000.0).roundToInt()),
        chapterTitle = chapter.title,
        reward = RewardCard(chapter.reward, cardTier(score)),
    )
}
