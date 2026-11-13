package com.drama.app.core

const val ARRIVAL_LEAD_MINUTES = 1.0
const val REACTION_COOLDOWN_MS = 30_000L
const val CHOICE_TIMEOUT_MS = 30_000L
private const val MINUTE = 60_000L

enum class Gear { P, R, N, D }
enum class EmergencyCause { AIRBAG, COLLISION }
enum class Speaker { NARRATOR, CHARACTER }
enum class InputVia { VOICE, BUTTON }

sealed interface DriveEvent {
    val now: Long

    data class Tick(override val now: Long, val etaMinutes: Double) : DriveEvent
    data class HarshDriving(val kind: HarshKind, override val now: Long) : DriveEvent
    data class Congestion(override val now: Long) : DriveEvent
    data class RouteGuidance(val instruction: String, override val now: Long) : DriveEvent
    data class DriverInput(val choiceId: String, val via: InputVia, override val now: Long) : DriveEvent
    data class Emergency(val cause: EmergencyCause, override val now: Long) : DriveEvent
    data class GearChanged(val gear: Gear, override val now: Long) : DriveEvent
}

sealed interface Effect {
    data class Speak(val speaker: Speaker, val text: String) : Effect
    data class Bgm(val on: Boolean) : Effect
    data object StopAllAudio : Effect
    data class Sos(val cause: EmergencyCause) : Effect
    data class ShowChoices(val choices: List<Choice>) : Effect
    data object HideChoices : Effect
    data class Reroute(val prefer: RouteHint) : Effect
    data class Save(val data: SaveData) : Effect
    data class UxRestrictions(val active: Boolean) : Effect
    data class ShowReport(val report: DriveReport) : Effect
}

data class PendingChoice(val beatId: String, val choices: List<Choice>, val deadline: Long)

data class Trip(
    val scenario: Scenario,
    val plan: DrivePlan,
    val save: SaveData,
    val startedAt: Long,
    val arrivalAt: Long,
    val played: Int,
    val lastBeatAt: Long,
    val lastReactionAt: Long?,
    val harsh: Map<HarshKind, Int>,
    val flags: Map<String, String>,
    val pending: PendingChoice?,
)

sealed interface DriveState {
    data class Driving(val trip: Trip) : DriveState
    data class Arrived(val trip: Trip, val save: SaveData) : DriveState
    data class Parked(val report: DriveReport) : DriveState
    data class Emergency(val cause: EmergencyCause) : DriveState
}

data class Step(val state: DriveState, val effects: List<Effect>)

private fun say(speaker: Speaker, text: String) = Effect.Speak(speaker, text)

fun startDrive(ready: PreDriveState.Ready, now: Long): Step {
    val trip = Trip(
        scenario = ready.scenario,
        plan = ready.plan,
        save = ready.save,
        startedAt = now,
        arrivalAt = now + (ready.etaMinutes * MINUTE).toLong(),
        played = 0,
        lastBeatAt = now,
        lastReactionAt = null,
        harsh = HarshKind.entries.associateWith { 0 },
        flags = ready.save.flags,
        pending = null,
    )
    return Step(
        DriveState.Driving(trip),
        listOf(Effect.UxRestrictions(true), Effect.Bgm(true), say(Speaker.NARRATOR, ready.plan.opening)),
    )
}

private fun playNextBeat(trip: Trip, now: Long): Step {
    val beat = trip.plan.beats.getOrNull(trip.played) ?: return Step(DriveState.Driving(trip), emptyList())
    val effects = mutableListOf<Effect>(say(Speaker.CHARACTER, beat.line))
    val pending = if (beat.choices.isNotEmpty()) {
        effects += Effect.ShowChoices(beat.choices)
        PendingChoice(beat.id, beat.choices, now + CHOICE_TIMEOUT_MS)
    } else null
    return Step(DriveState.Driving(trip.copy(played = trip.played + 1, lastBeatAt = now, pending = pending)), effects)
}

private fun resolveChoice(trip: Trip, choice: Choice): Trip {
    val pending = trip.pending ?: return trip
    return trip.copy(pending = null, flags = trip.flags + (pending.beatId to choice.id))
}

private fun choiceEffects(choice: Choice, byDriver: Boolean): List<Effect> {
    val reroute = if (choice.routeHint == RouteHint.DETOUR && !byDriver) null else choice.routeHint
    return listOfNotNull(Effect.HideChoices, say(Speaker.CHARACTER, choice.reply), reroute?.let { Effect.Reroute(it) })
}

private fun safeDefault(choices: List<Choice>): Choice = choices.firstOrNull { it.routeHint != RouteHint.DETOUR } ?: choices.first()

private fun beatDue(trip: Trip, now: Long): Boolean {
    val remainingBeats = trip.plan.beats.size - trip.played
    if (remainingBeats <= 0 || trip.pending != null) return false
    val storyWindow = trip.arrivalAt - (ARRIVAL_LEAD_MINUTES * MINUTE).toLong() - trip.lastBeatAt
    return (now - trip.lastBeatAt) * (remainingBeats + 1) >= storyWindow
}

private fun progress(trip: Trip, now: Long) = SaveData(
    scenarioId = trip.scenario.id,
    cursor = trip.scenario.advance(trip.save.cursor, trip.played),
    flags = trip.flags,
    updatedAt = now,
)

private fun countHarsh(trip: Trip, kind: HarshKind) = trip.copy(harsh = trip.harsh + (kind to (trip.harsh[kind] ?: 0) + 1))

private fun arrive(trip: Trip, now: Long): Step {
    val effects = mutableListOf<Effect>()
    var settled = trip
    trip.pending?.let {
        val choice = safeDefault(it.choices)
        settled = resolveChoice(trip, choice)
        effects += choiceEffects(choice, byDriver = false)
    }
    val playedAll = settled.played == settled.plan.beats.size
    val chapter = settled.scenario.chapters.getOrNull(settled.save.cursor.chapter)
    val ending = if (playedAll) settled.plan.ending else chapter?.cliffhanger ?: settled.plan.ending
    val save = progress(settled, now)
    effects += listOf(say(Speaker.CHARACTER, ending), Effect.Bgm(false), Effect.Save(save))
    return Step(DriveState.Arrived(settled, save), effects)
}

private fun park(trip: Trip, save: SaveData, now: Long): Step {
    val report = buildReport(trip.scenario, trip.save.cursor, save.cursor, trip.played, trip.harsh, trip.startedAt, now)
    return Step(DriveState.Parked(report), listOf(Effect.UxRestrictions(false), Effect.ShowReport(report)))
}

private fun emergency(cause: EmergencyCause, after: List<Effect> = emptyList()) =
    Step(DriveState.Emergency(cause), listOf(Effect.StopAllAudio, Effect.Sos(cause)) + after)

private fun driving(trip: Trip, event: DriveEvent): Step {
    fun stay(t: Trip, effects: List<Effect> = emptyList()) = Step(DriveState.Driving(t), effects)
    return when (event) {
        is DriveEvent.Tick -> {
            val t = trip.copy(arrivalAt = event.now + (event.etaMinutes * MINUTE).toLong())
            val pending = t.pending
            when {
                event.etaMinutes <= ARRIVAL_LEAD_MINUTES -> arrive(t, event.now)
                pending != null && event.now >= pending.deadline -> {
                    val choice = safeDefault(pending.choices)
                    stay(resolveChoice(t, choice), choiceEffects(choice, byDriver = false))
                }
                beatDue(t, event.now) -> playNextBeat(t, event.now)
                else -> stay(t)
            }
        }
        is DriveEvent.HarshDriving -> {
            val t = countHarsh(trip, event.kind)
            val last = trip.lastReactionAt
            if (last != null && event.now - last < REACTION_COOLDOWN_MS) {
                stay(t)
            } else {
                val r = trip.scenario.persona.reactions
                val line = when (event.kind) {
                    HarshKind.ACCEL -> r.harshAccel
                    HarshKind.BRAKE -> r.harshBrake
                    HarshKind.TURN -> r.harshTurn
                }
                stay(t.copy(lastReactionAt = event.now), listOf(say(Speaker.CHARACTER, line)))
            }
        }
        is DriveEvent.Congestion -> {
            val reaction = say(Speaker.CHARACTER, trip.scenario.persona.reactions.congestion)
            if (trip.pending != null) stay(trip, listOf(reaction))
            else playNextBeat(trip, event.now).let { Step(it.state, listOf(reaction) + it.effects) }
        }
        is DriveEvent.RouteGuidance -> stay(trip, listOf(say(Speaker.CHARACTER, "${trip.scenario.persona.name}: ${event.instruction}")))
        is DriveEvent.DriverInput -> {
            val choice = trip.pending?.choices?.firstOrNull { it.id == event.choiceId }
            if (choice == null) stay(trip) else stay(resolveChoice(trip, choice), choiceEffects(choice, byDriver = true))
        }
        is DriveEvent.GearChanged -> {
            if (event.gear != Gear.P) {
                stay(trip)
            } else {
                val arrived = arrive(trip, event.now)
                val state = arrived.state as DriveState.Arrived
                val parked = park(state.trip, state.save, event.now)
                Step(parked.state, arrived.effects + parked.effects)
            }
        }
        is DriveEvent.Emergency -> emergency(event.cause, listOf(Effect.Save(progress(trip, event.now))))
    }
}

// Emergency is terminal by design: once SOS starts, no story or UI transition may follow.
fun drive(state: DriveState, event: DriveEvent): Step = when (state) {
    is DriveState.Emergency -> Step(state, emptyList())
    is DriveState.Driving -> driving(state.trip, event)
    is DriveState.Arrived -> when {
        event is DriveEvent.Emergency -> emergency(event.cause)
        event is DriveEvent.HarshDriving -> Step(state.copy(trip = countHarsh(state.trip, event.kind)), emptyList())
        event is DriveEvent.GearChanged && event.gear == Gear.P -> park(state.trip, state.save, event.now)
        else -> Step(state, emptyList())
    }
    is DriveState.Parked -> if (event is DriveEvent.Emergency) emergency(event.cause) else Step(state, emptyList())
}
