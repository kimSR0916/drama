package com.drama.app.core

const val ARRIVAL_LEAD_MINUTES = 1.0
const val REACTION_COOLDOWN_MS = 30_000L
const val ANSWER_WINDOW_MS = 5_000L
const val QUESTION_SPEECH_CAP_MS = 30_000L
const val SPEECH_MS_PER_CHAR = 110L
const val LEAD_SILENCE_MS = 200L

fun speechMs(text: String): Long = LEAD_SILENCE_MS + text.length * SPEECH_MS_PER_CHAR

enum class Gear { P, R, N, D }
enum class EmergencyCause { AIRBAG, COLLISION }
enum class Speaker { NARRATOR, CHARACTER, DRIVER }
enum class InputVia { VOICE, BUTTON }

sealed interface DriveEvent {
    val now: Long

    data class Tick(override val now: Long, val etaMinutes: Double) : DriveEvent
    data class HarshDriving(val kind: HarshKind, override val now: Long) : DriveEvent
    data class Congestion(override val now: Long) : DriveEvent
    data class RouteGuidance(val instruction: String, override val now: Long) : DriveEvent
    data class DriverInput(val hint: RouteHint, val via: InputVia, override val now: Long) : DriveEvent
    data class QuestionSpoken(override val now: Long) : DriveEvent
    data class Emergency(val cause: EmergencyCause, override val now: Long) : DriveEvent
    data class GearChanged(val gear: Gear, override val now: Long) : DriveEvent
}

sealed interface Effect {
    data class Speak(val speaker: Speaker, val text: String) : Effect
    data class Bgm(val on: Boolean) : Effect
    data object StopAllAudio : Effect
    data class Sos(val cause: EmergencyCause) : Effect
    data class ShowChoices(val branch: Branch) : Effect
    data object HideChoices : Effect
    data class Reroute(val prefer: RouteHint) : Effect
    data class Save(val data: SaveData) : Effect
    data class UxRestrictions(val active: Boolean) : Effect
    data class ShowReport(val report: DriveReport) : Effect
}

const val TIMEOUT_FLAG = "TIMEOUT"

data class PendingChoice(val beatId: String, val branch: Branch, val deadline: Long, val spoken: Boolean = false)

data class Trip(
    val scenario: Scenario,
    val plan: DrivePlan,
    val save: SaveData,
    val startedAt: Long,
    val etaMinutes: Double,
    val played: Int,
    val answered: Int,
    val lastBeatEta: Double,
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
        etaMinutes = ready.etaMinutes,
        played = 0,
        answered = 0,
        lastBeatEta = ready.etaMinutes,
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
    val planned = trip.plan.beats.getOrNull(trip.played) ?: return Step(DriveState.Driving(trip), emptyList())
    val beat = planned.beat
    val effects = mutableListOf<Effect>(say(Speaker.CHARACTER, beat.line))
    val pending = beat.branch?.let {
        effects += Effect.ShowChoices(it)
        PendingChoice(beat.id, it, now + speechMs(beat.line) + QUESTION_SPEECH_CAP_MS)
    }
    return Step(DriveState.Driving(trip.copy(played = trip.played + 1, lastBeatEta = trip.etaMinutes, pending = pending)), effects)
}

private fun choose(trip: Trip, hint: RouteHint): Step {
    val pending = trip.pending ?: return Step(DriveState.Driving(trip), emptyList())
    val option = pending.branch.option(hint)
    return Step(
        DriveState.Driving(trip.copy(pending = null, answered = trip.answered + 1, flags = trip.flags + (pending.beatId to hint.name))),
        listOf(Effect.HideChoices, say(Speaker.DRIVER, option.label), say(Speaker.CHARACTER, option.reply), Effect.Reroute(hint)),
    )
}

private fun timeOut(trip: Trip): Pair<Trip, List<Effect>> {
    val pending = trip.pending ?: return trip to emptyList()
    return trip.copy(pending = null, flags = trip.flags + (pending.beatId to TIMEOUT_FLAG)) to
        listOf(Effect.HideChoices, say(Speaker.CHARACTER, pending.branch.timeoutLine), Effect.Reroute(RouteHint.KEEP))
}

private fun beatDue(trip: Trip): Boolean {
    val remainingBeats = trip.plan.beats.size - trip.played
    if (remainingBeats <= 0 || trip.pending != null) return false
    val travelled = trip.lastBeatEta - trip.etaMinutes
    return travelled * (remainingBeats + 1) >= trip.lastBeatEta - ARRIVAL_LEAD_MINUTES
}

private fun progress(trip: Trip, now: Long): SaveData {
    val lastPlayed = trip.plan.beats.getOrNull(trip.played - 1)?.index
    return SaveData(
        scenarioId = trip.scenario.id,
        cursor = trip.scenario.after(trip.save.cursor, lastPlayed),
        flags = trip.flags,
        bond = trip.save.bond + bondGain(trip.played, trip.answered, safetyScore(trip.harsh)),
        updatedAt = now,
    )
}

private fun countHarsh(trip: Trip, kind: HarshKind) = trip.copy(harsh = trip.harsh + (kind to (trip.harsh[kind] ?: 0) + 1))

private fun arrive(trip: Trip, now: Long): Step {
    val (settled, effects) = timeOut(trip)
    val save = progress(settled, now)
    val chapter = settled.scenario.chapters.getOrNull(settled.save.cursor.chapter)
    val ending = when {
        chapter == null -> null
        save.cursor.chapter > settled.save.cursor.chapter -> chapter.finale
        else -> chapter.cliffhanger
    }
    return Step(
        DriveState.Arrived(settled, save),
        effects + listOfNotNull(ending?.let { say(Speaker.CHARACTER, it) }, Effect.Bgm(false), Effect.Save(save)),
    )
}

private fun park(trip: Trip, save: SaveData, now: Long): Step {
    val report = buildReport(trip.scenario, trip.save, save, trip.played, trip.harsh, trip.startedAt, now)
    return Step(DriveState.Parked(report), listOf(Effect.UxRestrictions(false), Effect.ShowReport(report)))
}

private fun emergency(cause: EmergencyCause, after: List<Effect> = emptyList()) =
    Step(DriveState.Emergency(cause), listOf(Effect.StopAllAudio, Effect.Sos(cause)) + after)

private fun driving(trip: Trip, event: DriveEvent): Step {
    fun stay(t: Trip, effects: List<Effect> = emptyList()) = Step(DriveState.Driving(t), effects)
    return when (event) {
        is DriveEvent.Tick -> {
            val t = trip.copy(etaMinutes = event.etaMinutes)
            val pending = t.pending
            when {
                event.etaMinutes <= ARRIVAL_LEAD_MINUTES -> arrive(t, event.now)
                pending != null && event.now >= pending.deadline -> timeOut(t).let { (next, effects) -> stay(next, effects) }
                beatDue(t) -> playNextBeat(t, event.now)
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
        is DriveEvent.RouteGuidance -> stay(trip, listOf(say(Speaker.CHARACTER, event.instruction)))
        is DriveEvent.DriverInput -> choose(trip, event.hint)
        is DriveEvent.QuestionSpoken -> {
            val pending = trip.pending
            if (pending == null || pending.spoken) stay(trip)
            else stay(trip.copy(pending = pending.copy(deadline = event.now + ANSWER_WINDOW_MS, spoken = true)))
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

// Emergency is terminal by design: once SOS starts, no story or UI transition may follow, even after P.
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
