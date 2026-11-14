package com.drama.app

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drama.app.authoring.AccountStore
import com.drama.app.authoring.DraftResult
import com.drama.app.authoring.KeyValue
import com.drama.app.authoring.customDraftJson
import com.drama.app.authoring.parseBuiltins
import com.drama.app.authoring.parseScenarioJson
import com.drama.app.core.Branch
import com.drama.app.core.DriveEvent
import com.drama.app.core.DriveState
import com.drama.app.core.EmergencyCause
import com.drama.app.core.Gear
import com.drama.app.core.HarshKind
import com.drama.app.core.InputVia
import com.drama.app.core.PreDriveEvent
import com.drama.app.core.RouteHint
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource
import com.drama.app.platform.NaviSignals
import com.drama.app.platform.VehicleSignals
import com.drama.app.platform.Voice
import com.drama.app.platform.matchChoice
import com.drama.app.session.Outputs
import com.drama.app.session.Session
import com.drama.app.session.SessionView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DEMO_SPEED = 10
private const val TICK_MS = 1_000L
private const val NAVI_FRESH_MS = 10_000L

class PrefsKeyValue(context: Context) : KeyValue {
    private val prefs = context.getSharedPreferences("drama", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun set(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun keys(): Set<String> = prefs.all.keys
}

data class UiState(
    val session: SessionView = SessionView(),
    val stories: List<Scenario> = emptyList(),
    val demoMode: Boolean = true,
    val systemUxRestricted: Boolean = false,
    val naviEtaMinutes: Double? = null,
    val authoringIssues: List<String> = emptyList(),
    val sosLocked: Boolean = false,
    val answerSecondsLeft: Int? = null,
)

class DramaViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AccountStore(PrefsKeyValue(app), "local-account")
    private val builtins = parseBuiltins(app.assets.open("scenarios.json").bufferedReader().use { it.readText() })
    private var naviEta: Pair<Long, Double>? = null
    private var etaMinutes = 0.0

    private fun now() = SystemClock.elapsedRealtime()

    private fun onMain(block: () -> Unit) {
        viewModelScope.launch { block() }
    }

    private val voice = Voice(app) { heard -> onMain { onHeard(heard) } }
    private val navi = NaviSignals(
        app,
        clock = ::now,
        onEtaMinutes = { m -> onMain { onNaviEta(m) } },
        onCongestion = { onMain { congestion() } },
        onGuidance = { text -> onMain { guidance(text) } },
        onArrived = { onMain { tick(forceArrival = true) } },
    )
    private val vehicle = VehicleSignals(
        app,
        onGear = { g -> onMain { gear(g) } },
        onAirbag = { onMain { emergency(EmergencyCause.AIRBAG) } },
        onHarsh = { k -> onMain { harsh(k) } },
        onUxRestricted = { restricted -> onMain { post { it.copy(systemUxRestricted = restricted) } } },
    )

    private val session = Session(store, object : Outputs {
        override fun speak(text: String) = voice.speak(text)
        override fun stopAllAudio() = voice.stopSpeaking()
        override fun bgm(on: Boolean) = Unit
        override fun reroute(prefer: RouteHint) = navi.reroute(prefer)
        override fun sos(cause: EmergencyCause) = post { it.copy(sosLocked = true) }
        override fun listenForChoice(branch: Branch) = voice.listen()
        override fun stopListening() = voice.stopListening()
    }, clock = ::now)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        builtins.forEach(store::putScenario)
        vehicle.start()
        navi.start()
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private fun post(f: (UiState) -> UiState) {
        _ui.value = f(_ui.value)
    }

    private fun refresh() = post {
        it.copy(
            session = session.view,
            stories = store.listScenarios().sortedBy { s -> if (s.source == ScenarioSource.CUSTOM) -1 else builtins.indexOfFirst { it.id == s.id } },
            naviEtaMinutes = naviFresh(),
            answerSecondsLeft = (session.view.drive as? DriveState.Driving)?.trip?.pending?.let { p -> ((p.deadline - now() + 999) / 1000).toInt().coerceAtLeast(0) },
        )
    }

    private fun onNaviEta(minutes: Double) {
        naviEta = now() to minutes
        refresh()
    }

    private fun naviFresh(): Double? = naviEta?.takeIf { now() - it.first <= NAVI_FRESH_MS }?.second

    fun onPermissionsChanged() = vehicle.ensureSpeed()

    private fun tick(forceArrival: Boolean = false) {
        navi.poll()
        if (session.view.drive !is DriveState.Driving) {
            if (_ui.value.naviEtaMinutes != naviFresh()) refresh()
            return
        }
        val elapsedMinutes = TICK_MS * (if (_ui.value.demoMode) DEMO_SPEED else 1) / 60_000.0
        etaMinutes = when {
            forceArrival -> 0.0
            !_ui.value.demoMode && naviFresh() != null -> naviFresh()!!
            else -> (etaMinutes - elapsedMinutes).coerceAtLeast(0.0)
        }
        session.send(DriveEvent.Tick(now(), etaMinutes))
        refresh()
    }

    fun selectStory(id: String) { session.selectStory(id); refresh() }
    fun startNew() { session.pre(PreDriveEvent.StartNew(System.currentTimeMillis())); refresh() }
    fun answer(yes: Boolean) { session.pre(PreDriveEvent.Answer(yes, System.currentTimeMillis())); refresh() }
    fun back() { session.pre(PreDriveEvent.Back); refresh() }
    fun setDestination(minutes: Double) { session.pre(PreDriveEvent.SetDestination(minutes)); refresh() }
    fun setDemoMode(on: Boolean) = post { it.copy(demoMode = on) }

    fun startDriving() {
        session.startDriving()
        etaMinutes = session.view.etaMinutes
        refresh()
    }

    fun choose(hint: RouteHint, via: InputVia = InputVia.BUTTON) { session.send(DriveEvent.DriverInput(hint, via, now())); refresh() }
    fun harsh(kind: HarshKind) { session.send(DriveEvent.HarshDriving(kind, now())); refresh() }
    fun guidance(text: String) { session.send(DriveEvent.RouteGuidance(text, now())); refresh() }
    fun gear(gear: Gear) { session.send(DriveEvent.GearChanged(gear, now())); refresh() }
    fun emergency(cause: EmergencyCause) { session.send(DriveEvent.Emergency(cause, now())); refresh() }

    fun congestion() {
        if (session.view.drive !is DriveState.Driving) return
        etaMinutes += 3
        session.send(DriveEvent.Congestion(now()))
        refresh()
    }

    private fun onHeard(text: String) {
        val choices = session.view.choices ?: return
        matchChoice(text, choices)?.let { choose(it, InputVia.VOICE) }
    }

    fun home() {
        if (_ui.value.sosLocked) return
        session.reset()
        refresh()
    }

    fun saveDraft(title: String, premise: String, name: String, tone: String, chapterTitle: String, opening: String, lines: List<String>): Boolean {
        val json = customDraftJson(title, premise, name, tone, chapterTitle, opening, lines)
        return when (val r = parseScenarioJson(json, "custom-${System.currentTimeMillis()}", ScenarioSource.CUSTOM)) {
            is DraftResult.Ok -> {
                store.putScenario(r.scenario)
                post { it.copy(authoringIssues = emptyList()) }
                refresh()
                true
            }
            is DraftResult.Invalid -> {
                post { it.copy(authoringIssues = r.issues) }
                false
            }
        }
    }

    override fun onCleared() {
        vehicle.stop()
        navi.stop()
        voice.release()
    }
}
