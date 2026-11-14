package com.drama.app.core

const val SHORT_TRIP_MINUTES = 10.0

enum class PacingMode { SHORT, FULL }

data class PlannedBeat(val index: Int, val beat: Beat)

data class DrivePlan(
    val mode: PacingMode,
    val opening: String,
    val beats: List<PlannedBeat>,
)

fun pacingMode(etaMinutes: Double): PacingMode =
    if (etaMinutes < SHORT_TRIP_MINUTES) PacingMode.SHORT else PacingMode.FULL

fun planDrive(scenario: Scenario, cursor: Cursor, etaMinutes: Double): DrivePlan? {
    val chapter = scenario.chapters.getOrNull(cursor.chapter) ?: return null
    val mode = pacingMode(etaMinutes)
    val remaining = chapter.beats.withIndex().drop(cursor.beat).map { PlannedBeat(it.index, it.value) }
    val beats = when (mode) {
        PacingMode.FULL -> remaining
        PacingMode.SHORT -> listOfNotNull(remaining.firstOrNull { it.beat.kind == SceneKind.CORE } ?: remaining.firstOrNull())
    }
    return DrivePlan(mode, if (cursor.beat == 0) chapter.opening else chapter.recap, beats)
}

object Prompts {
    const val ASK_RESUME = "이어서 하시겠습니까?"
    const val ASK_RESTART = "처음부터 시작하시겠습니까?"
}

sealed interface PreDriveState {
    data object Selecting : PreDriveState
    data class OfferNew(val scenario: Scenario, val bond: Int) : PreDriveState
    data class AskResume(val scenario: Scenario, val save: SaveData) : PreDriveState
    data class AskRestart(val scenario: Scenario, val save: SaveData) : PreDriveState
    data class AwaitingDestination(val scenario: Scenario, val save: SaveData, val resumed: Boolean) : PreDriveState
    data class Ready(
        val scenario: Scenario,
        val save: SaveData,
        val resumed: Boolean,
        val etaMinutes: Double,
        val plan: DrivePlan,
    ) : PreDriveState
}

sealed interface PreDriveEvent {
    data class SelectStory(val scenario: Scenario, val save: SaveData?) : PreDriveEvent
    data class StartNew(val now: Long) : PreDriveEvent
    data class Answer(val yes: Boolean, val now: Long) : PreDriveEvent
    data class SetDestination(val etaMinutes: Double) : PreDriveEvent
    data object Back : PreDriveEvent
}

private fun freshSave(scenario: Scenario, now: Long, bond: Int) = SaveData(scenario.id, Cursor.START, emptyMap(), bond, now)

fun preDrive(state: PreDriveState, event: PreDriveEvent): PreDriveState = when (event) {
    is PreDriveEvent.SelectStory -> when (state) {
        is PreDriveState.Ready -> state
        else -> {
            val save = event.save
            val hasHistory = save != null && save.scenarioId == event.scenario.id && event.scenario.fits(save.cursor)
            if (hasHistory) PreDriveState.AskResume(event.scenario, save!!) else PreDriveState.OfferNew(event.scenario, save?.takeIf { it.scenarioId == event.scenario.id }?.bond ?: 0)
        }
    }
    is PreDriveEvent.StartNew -> when (state) {
        is PreDriveState.OfferNew -> PreDriveState.AwaitingDestination(state.scenario, freshSave(state.scenario, event.now, state.bond), resumed = false)
        else -> state
    }
    is PreDriveEvent.Answer -> when (state) {
        is PreDriveState.AskResume ->
            if (event.yes) PreDriveState.AwaitingDestination(state.scenario, state.save, resumed = true)
            else PreDriveState.AskRestart(state.scenario, state.save)
        is PreDriveState.AskRestart ->
            if (event.yes) PreDriveState.AwaitingDestination(state.scenario, freshSave(state.scenario, event.now, state.save.bond), resumed = false)
            else PreDriveState.Selecting
        else -> state
    }
    is PreDriveEvent.SetDestination -> {
        val target = when (state) {
            is PreDriveState.AwaitingDestination -> Triple(state.scenario, state.save, state.resumed)
            is PreDriveState.Ready -> Triple(state.scenario, state.save, state.resumed)
            else -> null
        }
        val plan = target?.takeIf { event.etaMinutes > 0 }?.let { planDrive(it.first, it.second.cursor, event.etaMinutes) }
        if (target == null || plan == null) state
        else PreDriveState.Ready(target.first, target.second, target.third, event.etaMinutes, plan)
    }
    PreDriveEvent.Back -> when (state) {
        is PreDriveState.Ready -> PreDriveState.AwaitingDestination(state.scenario, state.save, state.resumed)
        else -> PreDriveState.Selecting
    }
}
