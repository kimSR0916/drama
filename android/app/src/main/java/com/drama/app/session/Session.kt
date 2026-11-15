package com.drama.app.session

import com.drama.app.authoring.AccountStore
import com.drama.app.authoring.LogEntry
import com.drama.app.core.Branch
import com.drama.app.core.DriveEvent
import com.drama.app.core.DriveReport
import com.drama.app.core.DriveState
import com.drama.app.core.Effect
import com.drama.app.core.EmergencyCause
import com.drama.app.core.PreDriveEvent
import com.drama.app.core.PreDriveState
import com.drama.app.core.RouteHint
import com.drama.app.core.Slot
import com.drama.app.core.Speaker
import com.drama.app.core.drive
import com.drama.app.core.preDrive
import com.drama.app.core.startDrive

data class SessionView(
    val pre: PreDriveState = PreDriveState.Selecting,
    val drive: DriveState? = null,
    val subtitle: LogEntry? = null,
    val log: List<LogEntry> = emptyList(),
    val choices: Branch? = null,
    val uxLocked: Boolean = false,
    val bgm: Boolean = false,
    val sos: EmergencyCause? = null,
    val report: DriveReport? = null,
    val etaMinutes: Double = 0.0,
)

interface Outputs {
    fun speak(text: String)
    fun stopAllAudio()
    fun bgm(on: Boolean)
    fun reroute(prefer: RouteHint)
    fun sos(cause: EmergencyCause)
    fun listenForChoice(branch: Branch)
    fun stopListening()
}

class Session(
    private val store: AccountStore,
    private val outputs: Outputs,
    private val clock: () -> Long,
    private val generated: (Slot) -> String? = { null },
) {
    var view = SessionView()
        private set

    private val scenarioId: String?
        get() = when (val p = view.pre) {
            is PreDriveState.Ready -> p.scenario.id
            is PreDriveState.AwaitingDestination -> p.scenario.id
            is PreDriveState.AskResume -> p.scenario.id
            is PreDriveState.AskRestart -> p.scenario.id
            is PreDriveState.OfferNew -> p.scenario.id
            PreDriveState.Selecting -> null
        }

    private fun log(entry: LogEntry) {
        view = view.copy(subtitle = entry, log = view.log + entry)
        if (entry.speaker != Speaker.DRIVER) outputs.speak(entry.text)
    }

    private fun persistLog() = scenarioId?.let { store.putLog(it, view.log) }

    private fun apply(effects: List<Effect>) {
        for (e in effects) when (e) {
            is Effect.Speak -> log(LogEntry(e.speaker, e.slot?.let(generated) ?: e.text))
            is Effect.Bgm -> {
                view = view.copy(bgm = e.on)
                outputs.bgm(e.on)
            }
            Effect.StopAllAudio -> {
                view = view.copy(bgm = false, choices = null)
                outputs.stopListening()
                outputs.stopAllAudio()
            }
            is Effect.Sos -> {
                view = view.copy(sos = e.cause)
                outputs.sos(e.cause)
            }
            is Effect.ShowChoices -> {
                view = view.copy(choices = e.branch)
                outputs.listenForChoice(e.branch)
            }
            Effect.HideChoices -> {
                view = view.copy(choices = null)
                outputs.stopListening()
            }
            is Effect.Reroute -> outputs.reroute(e.prefer)
            is Effect.Save -> {
                store.putSave(e.data)
                persistLog()
            }
            is Effect.UxRestrictions -> view = view.copy(uxLocked = e.active)
            is Effect.ShowReport -> view = view.copy(report = e.report)
        }
    }

    fun pre(event: PreDriveEvent) {
        if (view.drive != null) return
        view = view.copy(pre = preDrive(view.pre, event))
        if (event is PreDriveEvent.Answer && event.yes && view.pre is PreDriveState.AwaitingDestination) {
            val p = view.pre as PreDriveState.AwaitingDestination
            view = view.copy(log = if (p.resumed) store.getLog(p.scenario.id) else emptyList())
        }
        if (event is PreDriveEvent.StartNew) view = view.copy(log = emptyList())
    }

    fun selectStory(scenarioId: String) {
        val scenario = store.getScenario(scenarioId) ?: return
        pre(PreDriveEvent.SelectStory(scenario, store.getSave(scenarioId)))
        view = view.copy(log = store.getLog(scenarioId))
    }

    fun startDriving() {
        val ready = view.pre as? PreDriveState.Ready ?: return
        if (view.drive != null) return
        val step = startDrive(ready, clock())
        view = view.copy(drive = step.state, etaMinutes = ready.etaMinutes)
        apply(step.effects)
    }

    fun send(event: DriveEvent) {
        val current = view.drive ?: return
        if (event is DriveEvent.Tick) view = view.copy(etaMinutes = event.etaMinutes)
        val step = drive(current, event)
        view = view.copy(drive = step.state)
        apply(step.effects)
    }

    private val canTalk: Boolean
        get() = view.sos == null && (view.drive is DriveState.Driving || view.drive is DriveState.Arrived)

    fun driverSaid(text: String): Boolean {
        if (!canTalk || view.choices != null) return false
        log(LogEntry(Speaker.DRIVER, text))
        return true
    }

    fun characterReplied(text: String) {
        if (canTalk) log(LogEntry(Speaker.CHARACTER, text))
    }

    fun reset() {
        outputs.stopListening()
        view = SessionView()
    }
}
