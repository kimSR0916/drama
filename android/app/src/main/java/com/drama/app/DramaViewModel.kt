package com.drama.app

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drama.app.ai.AiDirector
import com.drama.app.ai.ClaudeLlm
import com.drama.app.ai.DriveContext
import com.drama.app.ai.LineWriter
import com.drama.app.ai.Llm
import com.drama.app.ai.ScenarioWriter
import com.drama.app.ai.WriteResult
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
import com.drama.app.core.ReactionKind
import com.drama.app.core.RouteHint
import com.drama.app.core.SceneKind
import com.drama.app.core.Slot
import com.drama.app.core.safetyScore
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource
import com.drama.app.platform.NaviSignals
import com.drama.app.platform.VehicleSignals
import com.drama.app.platform.Voice
import com.drama.app.platform.matchChoice
import com.drama.app.session.Outputs
import com.drama.app.session.Session
import com.drama.app.session.SessionView
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DEMO_SPEED = 10
const val LINE_TIMEOUT_SECONDS = 20L
const val SCENARIO_TIMEOUT_SECONDS = 600L
const val TALK_WINDOW_MS = 10_000L
const val HARSH_TOAST_MS = 4_000L
const val TALK_MAX_CHARS = 100
const val TALK_FALLBACK = "지금은 대답하기 어렵네요. 조금 있다 다시 이야기해요."
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
    val speaking: String? = null,
    val aiEnabled: Boolean = false,
    val aiBusy: Boolean = false,
    val aiMessage: String? = null,
    val aiCreatedId: String? = null,
    val aiBaseUrl: String = "",
    val talkOpen: Boolean = false,
    val linked: Boolean = false,
    val harshToast: HarshKind? = null,
    val liveScore: Int? = null,
)

class DramaViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AccountStore(PrefsKeyValue(app), "local-account")
    private val builtins = parseBuiltins(app.assets.open("scenarios.json").bufferedReader().use { it.readText() })
    private var naviEta: Pair<Long, Double>? = null
    private var etaMinutes = 0.0

    private val aiPrefs = app.getSharedPreferences("drama-ai", Context.MODE_PRIVATE)
    private var llm: Llm? = null
    private var scenarioLlm: Llm? = null
    private var talkToken = 0
    private val director = AiDirector(
        background = { work -> viewModelScope.launch(Dispatchers.IO) { work() } },
        deliver = { block -> viewModelScope.launch { block() } },
    )
    private var talkOpen = false
    private var harshToastUntil = 0L

    private fun now() = SystemClock.elapsedRealtime()

    private fun onMain(block: () -> Unit) {
        viewModelScope.launch { block() }
    }

    private val voice = Voice(app, onHeard = { heard -> onMain { onHeard(heard) } }, onSpeaking = { text -> onMain { post { it.copy(speaking = text) } } })
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

    private val session: Session = Session(store, object : Outputs {
        override fun speak(text: String) = voice.speak(text)
        override fun stopAllAudio() = voice.stopSpeaking()
        override fun bgm(on: Boolean) = Unit
        override fun reroute(prefer: RouteHint) = navi.reroute(prefer)
        override fun sos(cause: EmergencyCause) = post { it.copy(sosLocked = true) }
        override fun listenForChoice(branch: Branch) = voice.listen { onMain { session.send(DriveEvent.QuestionSpoken(now())); refresh() } }
        override fun stopListening() = voice.stopListening()
    }, clock = ::now, generated = director::take)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        configureAi(aiPrefs.getString("key", "").orEmpty(), aiPrefs.getString("baseUrl", "").orEmpty(), announce = false)
        post { it.copy(linked = aiPrefs.getBoolean("linked", false)) }
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

    private fun refresh() {
        if (session.view.choices != null) talkOpen = false
        prepareLines()
        post {
            it.copy(
                talkOpen = talkOpen,
                liveScore = when (val d = session.view.drive) {
                    is DriveState.Driving -> safetyScore(d.trip.harsh)
                    is DriveState.Arrived -> safetyScore(d.trip.harsh)
                    else -> null
                },
            session = session.view,
            stories = store.listScenarios().sortedBy { s -> if (s.source == ScenarioSource.CUSTOM) -1 else builtins.indexOfFirst { it.id == s.id } },
            naviEtaMinutes = naviFresh(),
                answerSecondsLeft = (session.view.drive as? DriveState.Driving)?.trip?.pending?.takeIf { it.spoken }?.let { p -> ((p.deadline - now() + 999) / 1000).toInt().coerceAtLeast(0) },
            )
        }
    }

    fun configureAi(key: String, baseUrl: String, announce: Boolean = true) {
        val k = key.trim()
        val url = baseUrl.trim()
        aiPrefs.edit().putString("key", k).putString("baseUrl", url).apply()
        llm = if (k.isEmpty()) null else ClaudeLlm(k, url.ifEmpty { null }, Duration.ofSeconds(LINE_TIMEOUT_SECONDS))
        scenarioLlm = if (k.isEmpty()) null else ClaudeLlm(k, url.ifEmpty { null }, Duration.ofSeconds(SCENARIO_TIMEOUT_SECONDS))
        director.writer = llm?.let(::LineWriter)
        post {
            it.copy(
                aiEnabled = llm != null,
                aiBaseUrl = url,
                aiMessage = if (!announce) null else if (llm != null) "AI 기능을 켰습니다." else "AI 기능을 껐습니다. 미리 쓴 장면으로 진행합니다.",
            )
        }
    }

    private fun driveContext(): DriveContext? {
        val trip = when (val d = session.view.drive) {
            is DriveState.Driving -> d.trip
            is DriveState.Arrived -> d.trip
            else -> return null
        }
        return DriveContext(
            scenario = trip.scenario,
            chapterTitle = trip.scenario.chapters.getOrNull(trip.save.cursor.chapter)?.title.orEmpty(),
            recent = session.view.log,
            etaMinutes = session.view.etaMinutes.toInt(),
            harshCount = trip.harsh.values.sum(),
        )
    }

    private fun prepareLines() {
        val trip = (session.view.drive as? DriveState.Driving)?.trip ?: return
        val context = driveContext() ?: return
        val r = trip.scenario.persona.reactions
        val wanted = linkedMapOf<Slot, String>(
            Slot.Reaction(ReactionKind.BRAKE) to r.harshBrake,
            Slot.Reaction(ReactionKind.CONGESTION) to r.congestion,
            Slot.Reaction(ReactionKind.ACCEL) to r.harshAccel,
            Slot.Reaction(ReactionKind.TURN) to r.harshTurn,
        )
        trip.plan.beats.drop(trip.played).firstOrNull { it.beat.kind == SceneKind.SUB && it.beat.branch == null }
            ?.let { wanted[Slot.SubScene(it.beat.id)] = it.beat.line }
        director.prepare(context, wanted)
    }

    /** Saving with the key field left empty keeps the stored key, so changing only the server address does not turn AI off. */
    fun saveAiSettings(key: String, baseUrl: String) =
        configureAi(key.trim().ifEmpty { aiPrefs.getString("key", "").orEmpty() }, baseUrl)

    fun generateScenario(idea: String) {
        val model = scenarioLlm
        if (model == null) {
            post { it.copy(aiMessage = "AI 설정에서 API 키를 먼저 입력해 주세요.") }
            return
        }
        if (_ui.value.aiBusy) return
        post { it.copy(aiBusy = true, aiMessage = "AI가 시나리오를 쓰는 중입니다. 몇 분 걸릴 수 있어요.") }
        val id = "custom-${System.currentTimeMillis()}"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ScenarioWriter(model).write(idea, id) }.getOrElse { WriteResult.Failed("AI 호출에 실패했습니다.") } }
            when (result) {
                is WriteResult.Ok -> {
                    store.putScenario(result.scenario)
                    post { it.copy(aiBusy = false, aiCreatedId = id, aiMessage = "'${result.scenario.title}' 시나리오를 만들었습니다.") }
                    refresh()
                }
                is WriteResult.Failed -> post { it.copy(aiBusy = false, aiMessage = result.message) }
            }
        }
    }

    fun say(utterance: String) {
        val model = llm ?: return
        val text = utterance.trim().take(TALK_MAX_CHARS)
        if (text.isEmpty() || !session.driverSaid(text)) return
        val context = driveContext()
        val trip = session.tripStartedAt
        refresh()
        if (context == null || trip == null) return
        viewModelScope.launch {
            val reply = withContext(Dispatchers.IO) { runCatching { LineWriter(model).reply(context, text) }.getOrNull() }
            session.characterReplied(reply ?: TALK_FALLBACK, trip)
            refresh()
        }
    }

    private fun openTalkWindow() {
        if (llm == null || session.view.choices != null || session.view.drive !is DriveState.Driving) return
        talkOpen = true
        val token = ++talkToken
        voice.listen { }
        viewModelScope.launch {
            delay(TALK_WINDOW_MS)
            if (talkOpen && token == talkToken) {
                talkOpen = false
                if (session.view.choices == null) voice.stopListening()
                refresh()
            }
        }
    }

    private fun onNaviEta(minutes: Double) {
        naviEta = now() to minutes
        refresh()
    }

    private fun naviFresh(): Double? = naviEta?.takeIf { now() - it.first <= NAVI_FRESH_MS }?.second

    fun onPermissionsChanged() = vehicle.ensureSpeed()

    private fun tick(forceArrival: Boolean = false) {
        if (_ui.value.harshToast != null && now() >= harshToastUntil) post { it.copy(harshToast = null) }
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
        post { it.copy(harshToast = null) }
        director.reset()
        session.startDriving()
        etaMinutes = session.view.etaMinutes
        refresh()
    }

    fun choose(hint: RouteHint, via: InputVia = InputVia.BUTTON) { session.send(DriveEvent.DriverInput(hint, via, now())); refresh() }
    fun harsh(kind: HarshKind) {
        if (session.view.drive is DriveState.Driving) {
            harshToastUntil = now() + HARSH_TOAST_MS
            post { it.copy(harshToast = kind) }
        }
        session.send(DriveEvent.HarshDriving(kind, now()))
        refresh()
    }

    fun linkAccount() {
        aiPrefs.edit().putBoolean("linked", true).apply()
        post { it.copy(linked = true) }
    }
    fun guidance(text: String) { session.send(DriveEvent.RouteGuidance(text, now())); refresh() }
    fun gear(gear: Gear) { session.send(DriveEvent.GearChanged(gear, now())); refresh() }
    fun emergency(cause: EmergencyCause) { session.send(DriveEvent.Emergency(cause, now())); refresh() }

    fun congestion() {
        if (session.view.drive !is DriveState.Driving) return
        etaMinutes += 3
        session.send(DriveEvent.Congestion(now()))
        openTalkWindow()
        refresh()
    }

    private fun onHeard(text: String) {
        val choices = session.view.choices
        when {
            choices != null -> matchChoice(text, choices)?.let { choose(it, InputVia.VOICE) }
            talkOpen -> {
                talkOpen = false
                voice.stopListening()
                say(text)
            }
        }
    }

    fun home() {
        if (_ui.value.sosLocked) return
        talkOpen = false
        director.reset()
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
