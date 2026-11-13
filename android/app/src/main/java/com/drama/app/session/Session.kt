package com.drama.app.session

import com.drama.app.authoring.AccountStore
import com.drama.app.core.Choice
import com.drama.app.core.DriveEvent
import com.drama.app.core.DriveReport
import com.drama.app.core.DriveState
import com.drama.app.core.Effect
import com.drama.app.core.EmergencyCause
import com.drama.app.core.PreDriveEvent
import com.drama.app.core.PreDriveState
import com.drama.app.core.RouteHint
import com.drama.app.core.Speaker
import com.drama.app.core.drive
import com.drama.app.core.preDrive
import com.drama.app.core.startDrive

enum class LineSpeaker { NARRATOR, CHARACTER, SYSTEM }

data class Line(val speaker: LineSpeaker, val text: String)

data class SessionView(
    val pre: PreDriveState = PreDriveState.Selecting,
    val drive: DriveState? = null,
    val transcript: List<Line> = emptyList(),
    val choices: List<Choice>? = null,
    val uxLocked: Boolean = false,
    val bgm: Boolean = false,
    val sos: EmergencyCause? = null,
    val report: DriveReport? = null,
    val etaMinutes: Double = 0.0,
)

interface Outputs {
    fun speak(line: Line)
    fun stopAllAudio()
    fun bgm(on: Boolean)
    fun reroute(prefer: RouteHint)
    fun sos(cause: EmergencyCause)
    fun listenForChoice(choices: List<Choice>)
    fun stopListening()
}

class Session(private val store: AccountStore, private val outputs: Outputs, private val clock: () -> Long) {
    var view = SessionView()
        private set

    private fun log(line: Line) {
        view = view.copy(transcript = (view.transcript + line).takeLast(50))
        outputs.speak(line)
    }

    private fun apply(effects: List<Effect>) {
        for (e in effects) when (e) {
            is Effect.Speak -> log(Line(if (e.speaker == Speaker.NARRATOR) LineSpeaker.NARRATOR else LineSpeaker.CHARACTER, e.text))
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
                view = view.copy(choices = e.choices)
                outputs.listenForChoice(e.choices)
            }
            Effect.HideChoices -> {
                view = view.copy(choices = null)
                outputs.stopListening()
            }
            is Effect.Reroute -> {
                outputs.reroute(e.prefer)
                view = view.copy(transcript = view.transcript + Line(LineSpeaker.SYSTEM, if (e.prefer == RouteHint.DETOUR) "우회 경로로 재탐색합니다." else "현재 경로를 유지합니다."))
            }
            is Effect.Save -> store.putSave(e.data)
            is Effect.UxRestrictions -> view = view.copy(uxLocked = e.active)
            is Effect.ShowReport -> view = view.copy(report = e.report)
        }
    }

    fun pre(event: PreDriveEvent) {
        if (view.drive != null) return
        view = view.copy(pre = preDrive(view.pre, event))
    }

    fun selectStory(scenarioId: String) {
        val scenario = store.getScenario(scenarioId) ?: return
        pre(PreDriveEvent.SelectStory(scenario, store.getSave(scenarioId)))
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

    fun reset() {
        outputs.stopListening()
        view = SessionView()
    }
}
