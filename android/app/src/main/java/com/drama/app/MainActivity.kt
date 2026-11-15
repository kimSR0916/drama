package com.drama.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drama.app.authoring.LogEntry
import com.drama.app.core.Branch
import com.drama.app.core.CardTier
import com.drama.app.core.Cursor
import com.drama.app.core.DriveState
import com.drama.app.core.EmergencyCause
import com.drama.app.core.Gear
import com.drama.app.core.HARSH_PENALTY
import com.drama.app.core.HarshKind
import com.drama.app.core.SAFE_DRIVER_SCORE
import com.drama.app.core.PacingMode
import com.drama.app.core.PreDriveState
import com.drama.app.core.Prompts
import com.drama.app.core.RouteHint
import com.drama.app.core.SaveData
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource
import com.drama.app.core.Speaker
import com.drama.app.core.planDrive
import kotlin.math.ceil

private val Bg = Color(0xFF0B0E11)
private val Surface0 = Color(0xFF15181E)
private val Panel = Color(0xFF282C35)
private val Line = Color(0xFF3A424E)
private val Accent = Color(0xFF0088FF)
private val Danger = Color(0xFFFF4A35)
private val Muted = Color(0xFFB7BBC4)
private val Good = Color(0xFF1F7A4D)
private val SubtitleBar = listOf(Color(0xEC247E91), Color(0xE82A7F8C))

class MainActivity : ComponentActivity() {
    private val vm: DramaViewModel by viewModels()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        vm.onPermissionsChanged()
    }

    override fun onResume() {
        super.onResume()
        vm.onPermissionsChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, "android.car.permission.CAR_SPEED"), 0)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Bg)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(vm) }
            }
        }
    }
}

@Composable
private fun App(vm: DramaViewModel) {
    val ui by vm.ui.collectAsState()
    val s = ui.session
    var authoring by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var aiSettings by remember { mutableStateOf(false) }
    LaunchedEffect(ui.systemUxRestricted) {
        if (ui.systemUxRestricted) {
            authoring = false
            aiSettings = false
        }
    }
    LaunchedEffect(ui.aiCreatedId) { if (ui.aiCreatedId != null) authoring = false }
    LaunchedEffect(s.drive) { if (s.drive != null && s.report == null) showLog = false }

    when {
        ui.sosLocked -> EmergencyScreen()
        s.drive != null && s.report == null -> DriveScreen(vm, ui)
        else -> Column(Modifier.fillMaxSize().background(Surface0).padding(horizontal = 40.dp, vertical = 28.dp)) {
            Header(ui)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !ui.linked -> LinkScreen(vm)
                    showLog -> LogScreen(ui) { showLog = false }
                    s.report != null -> ReportScreen(vm, ui) { showLog = true }
                    aiSettings -> AiSettingsScreen(vm, ui) { aiSettings = false }
                    authoring -> AuthoringScreen(vm, ui, openAiSettings = { aiSettings = true }) { authoring = false }
                    else -> PreDriveScreen(vm, ui, openAuthoring = { authoring = true }, openLog = { showLog = true }, openAiSettings = { aiSettings = true })
                }
            }
        }
    }
}

@Composable
private fun Waveform(color: Color, modifier: Modifier = Modifier, bars: Int = 9) {
    val heights = listOf(0.35f, 0.6f, 0.9f, 0.5f, 1f, 0.55f, 0.85f, 0.45f, 0.3f, 0.7f, 0.5f, 0.95f, 0.4f, 0.65f, 0.3f)
    Canvas(modifier.width((bars * 9).dp).height(40.dp)) {
        val gap = size.width / bars
        repeat(bars) { i ->
            val h = size.height * heights[i % heights.size]
            drawLine(color, Offset(gap * i + gap / 2, (size.height - h) / 2), Offset(gap * i + gap / 2, (size.height + h) / 2), strokeWidth = gap * 0.45f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun Header(ui: UiState) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Waveform(Accent, bars = 5)
            Text("드라이브 스토리", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Chip(if (ui.systemUxRestricted) "주행 제한 활성" else "정차 중 · P", Panel)
            Chip(if (ui.aiEnabled) "AI 켜짐" else "AI 꺼짐", if (ui.aiEnabled) Good else Panel)
            if (ui.demoMode) Chip("시연 모드 ${DEMO_SPEED}배속", Color(0xFF3B2F7A))
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(Modifier.background(color, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, fontSize = 18.sp)
    }
}

@Composable
private fun BigButton(text: String, color: Color = Accent, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = Panel, disabledContentColor = Muted),
    ) { Text(text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 54.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Pictogram(scenario: Scenario?, size: Int) {
    if (scenario?.id == "missing-researcher" && scenario.source != ScenarioSource.CUSTOM) {
        Image(
            painterResource(if (size <= 140) R.drawable.design_story_thumbnail else R.drawable.design_story_cover),
            contentDescription = null,
            modifier = Modifier.size(width = (if (size <= 140) size * 1.5f else size.toFloat()).dp, height = size.dp).clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
        return
    }
    Box(
        Modifier.size(size.dp).background(Brush.linearGradient(listOf(Color(0xFF273342), Color(0xFF182431))), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(scenario?.persona?.icon ?: "🎭", fontSize = (size * 0.5).sp) }
}

private fun PreDriveState.scenarioOrNull(): Scenario? = when (this) {
    PreDriveState.Selecting -> null
    is PreDriveState.OfferNew -> scenario
    is PreDriveState.AskResume -> scenario
    is PreDriveState.AskRestart -> scenario
    is PreDriveState.AwaitingDestination -> scenario
    is PreDriveState.Ready -> scenario
}

@Composable
private fun LinkScreen(vm: DramaViewModel) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("모바일 테마를 불러오세요", fontSize = 72.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("모바일에서 사용한 계정으로 연결하세요.", fontSize = 36.sp, color = Muted)
        Spacer(Modifier.height(36.dp))
        Image(painterResource(R.drawable.design_link), contentDescription = null, modifier = Modifier.size(width = 140.dp, height = 280.dp))
        Spacer(Modifier.height(36.dp))
        BigButton("계정 연결", modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) { vm.linkAccount() }
    }
}

private fun genreOf(s: Scenario) = if (s.source == ScenarioSource.CUSTOM) "커스텀" else s.genre

@Composable
private fun ThemeList(vm: DramaViewModel, ui: UiState, openAuthoring: () -> Unit, openAiSettings: () -> Unit) {
    val tabs = listOf("전체") + ui.stories.map(::genreOf).distinct()
    var tab by remember { mutableStateOf("전체") }
    val shown = ui.stories.filter { tab == "전체" || genreOf(it) == tab }
    var selected by remember { mutableStateOf<String?>(null) }
    val current = shown.firstOrNull { it.id == selected } ?: shown.firstOrNull()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Title("내 테마")
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            tabs.forEach { t ->
                val on = t == tab
                Button(
                    onClick = { tab = t },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (on) Color.White else Color.Transparent, contentColor = if (on) Color.Black else Color.White),
                ) { Text(t, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(shown, key = { it.id }) { story ->
                val on = story.id == current?.id
                Button(
                    onClick = { selected = story.id },
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, if (on) Accent else Line),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Pictogram(story, 104)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(story.title, fontSize = 38.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(story.premise, fontSize = 26.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text("›", fontSize = 40.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!ui.systemUxRestricted) {
                BigButton("AI 설정", Panel, onClick = openAiSettings)
                BigButton("내 시나리오 만들기", Panel, onClick = openAuthoring)
            }
            Spacer(Modifier.weight(1f))
            BigButton("이 테마 사용", modifier = Modifier.widthIn(max = 420.dp).weight(1f), enabled = current != null) { current?.let { vm.selectStory(it.id) } }
        }
    }
}

@Composable
private fun Bubble(e: LogEntry) {
    val mine = e.speaker == Speaker.DRIVER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        if (!mine) {
            Image(painterResource(R.drawable.design_headset), contentDescription = null, modifier = Modifier.size(36.dp).background(Panel, RoundedCornerShape(10.dp)).padding(6.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            e.text,
            fontSize = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(if (mine) Accent else Panel, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(question: String, onYes: () -> Unit, onNo: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 900.dp).fillMaxWidth().background(Surface0, RoundedCornerShape(36.dp)).padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(question, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("터치하거나 음성으로 답변하세요", fontSize = 22.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("예", modifier = Modifier.weight(1f), onClick = onYes)
                BigButton("아니요", Panel, Modifier.weight(1f), onClick = onNo)
            }
        }
    }
}

@Composable
private fun StoryDetail(vm: DramaViewModel, ui: UiState, scenario: Scenario, save: SaveData?, openLog: () -> Unit) {
    val cursor = save?.cursor ?: Cursor.START
    val chapter = scenario.chapters.getOrNull(cursor.chapter) ?: scenario.chapters.first()
    val next = if (save != null) chapter.recap else chapter.opening
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(0.26f).fillMaxHeight().background(Surface0, RoundedCornerShape(20.dp)).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Chip(genreOf(scenario), Panel)
            Text(scenario.title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(scenario.premise, fontSize = 20.sp, color = Muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Pictogram(scenario, 300) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Text("${scenario.persona.name} · ${scenario.persona.tone}", fontSize = 20.sp, color = Muted)
        }
        Column(Modifier.weight(0.74f).background(Brush.horizontalGradient(listOf(Color(0xFF222730), Bg)), RoundedCornerShape(20.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Title(scenario.title)
            Text("${chapter.title} · 진행률 ${scenario.progressPercent(cursor)}%", fontSize = 26.sp, color = Muted)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.6f).fillMaxHeight().background(Bg.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("이전 대화 기록", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    val recent = ui.session.log.takeLast(4)
                    if (recent.isEmpty()) Text("아직 기록이 없습니다. 첫 주행을 시작해 보세요.", fontSize = 22.sp, color = Muted)
                    recent.forEach { Bubble(it) }
                }
                Column(Modifier.weight(1f).fillMaxHeight().background(Bg.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("다음 이야기", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(scenario.persona.name, fontSize = 20.sp, color = Muted)
                    Text(next, fontSize = 28.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(28.dp))
                    Waveform(Muted, Modifier.align(Alignment.CenterHorizontally), bars = 15)
                    Text("터치하거나 음성으로 답변하세요", fontSize = 18.sp, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("뒤로", Panel) { vm.back() }
                if (ui.session.log.isNotEmpty()) BigButton("이전 대화 기록", Panel, onClick = openLog)
                BigButton("스토리 재생", modifier = Modifier.weight(1f)) { if (save == null) vm.startNew() }
            }
        }
    }
}

@Composable
private fun LengthCard(label: String, minutes: Double, detail: String, goal: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else Line),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color(0xFF242B36) else Surface0, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 22.sp, color = Accent, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("예상 주행", fontSize = 20.sp, color = Muted)
                Text("${ceil(minutes).toInt()}분", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(detail, fontSize = 20.sp, color = Muted)
            }
            Text(goal, fontSize = 18.sp, color = Muted)
        }
    }
}

@Composable
private fun DestinationScreen(vm: DramaViewModel, ui: UiState, scenario: Scenario, save: SaveData, resumed: Boolean, chosen: Double?, openLog: () -> Unit) {
    val options = listOfNotNull(ui.naviEtaMinutes) + listOf(8.0, 15.0, 32.0)
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Title("목적지 설정")
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1.6f).fillMaxHeight().clip(RoundedCornerShape(20.dp))) {
                MapStandIn(Modifier.fillMaxSize())
                Text("내비 목적지의 예상 이동 시간에 맞춰 이야기 분량이 정해집니다.", color = Color.White, fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xE615181E)).padding(24.dp))
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(options) { minutes ->
                    val plan = planDrive(scenario, save.cursor, minutes, resumed)
                    val short = plan?.mode == PacingMode.SHORT
                    val chapter = scenario.chapters.getOrNull(save.cursor.chapter)
                    LengthCard(
                        label = (if (minutes == ui.naviEtaMinutes) "내비 경로 · " else "") + if (short) "단거리 스토리" else "장거리 스토리",
                        minutes = minutes,
                        detail = "· 장면 ${plan?.beats?.size ?: 0}개",
                        goal = "완료 목표 · ${if (short) "핵심 사건 1개" else chapter?.reward.orEmpty()}",
                        selected = chosen == minutes,
                    ) { vm.setDestination(minutes) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton("뒤로", Panel) { vm.back() }
            if (ui.session.log.isNotEmpty()) BigButton("이전 대화 기록", Panel, onClick = openLog)
            Text("시연 모드", fontSize = 20.sp)
            Switch(checked = ui.demoMode, onCheckedChange = vm::setDemoMode)
            BigButton("이 목적지로 설정", modifier = Modifier.weight(1f), enabled = chosen != null) { vm.startDriving() }
        }
    }
}

@Composable
private fun PreDriveScreen(vm: DramaViewModel, ui: UiState, openAuthoring: () -> Unit, openLog: () -> Unit, openAiSettings: () -> Unit) {
    when (val p = ui.session.pre) {
        PreDriveState.Selecting -> ThemeList(vm, ui, openAuthoring, openAiSettings)
        is PreDriveState.OfferNew -> StoryDetail(vm, ui, p.scenario, null, openLog)
        is PreDriveState.AskResume -> Box {
            StoryDetail(vm, ui, p.scenario, p.save, openLog)
            ConfirmDialog(Prompts.ASK_RESUME, onYes = { vm.answer(true) }, onNo = { vm.answer(false) })
        }
        is PreDriveState.AskRestart -> Box {
            StoryDetail(vm, ui, p.scenario, p.save, openLog)
            ConfirmDialog(Prompts.ASK_RESTART, onYes = { vm.answer(true) }, onNo = { vm.answer(false) })
        }
        is PreDriveState.AwaitingDestination -> DestinationScreen(vm, ui, p.scenario, p.save, p.resumed, null, openLog)
        is PreDriveState.Ready -> DestinationScreen(vm, ui, p.scenario, p.save, p.resumed, p.etaMinutes, openLog)
    }
}

@Composable
private fun MapStandIn(modifier: Modifier) {
    Image(
        painterResource(R.drawable.design_map),
        contentDescription = "시연용 정적 지도 이미지. 표시된 경로와 장소명은 실제 내비게이션 안내가 아닙니다.",
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun DriveScreen(vm: DramaViewModel, ui: UiState) {
    val s = ui.session
    val scenario = s.pre.scenarioOrNull()
    val save = (s.pre as? PreDriveState.Ready)?.save
    val arrived = s.drive is DriveState.Arrived
    Box(Modifier.fillMaxSize().background(Bg)) {
        MapStandIn(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.background(Color(0xF015181E), RoundedCornerShape(16.dp)).padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📍 ${scenario?.chapters?.getOrNull(save?.cursor?.chapter ?: 0)?.title.orEmpty()}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(if (arrived) "도착까지 1분" else "도착까지 ${ceil(s.etaMinutes).toInt()}분", fontSize = 28.sp, color = Color(0xFFCBD3DF))
                }
                Spacer(Modifier.weight(1f))
                ui.harshToast?.let { HarshToast(it) }
                Spacer(Modifier.weight(1f))
                ui.liveScore?.let { ScoreGauge(it) }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    if (ui.autoSaved) AutoSavedToast()
                }
                Box(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (arrived) ParkCard(Modifier, ui.autoSaved)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.End) {
                    StoryPlayingCard(ui)
                }
            }
            Spacer(Modifier.height(16.dp))
            val branch = s.choices
            if (branch != null) ChoiceBar(ui, branch) { hint -> vm.choose(hint) } else SubtitleBar(ui, scenario)
            if (ui.demoMode && s.sos == null) DemoPanel(vm, s.drive, ui.aiEnabled)
        }
    }
}

@Composable
private fun ScoreGauge(score: Int) {
    val color = when {
        score >= SAFE_DRIVER_SCORE -> Color(0xFF3DDC84)
        score >= 80 -> Color(0xFFFFC145)
        else -> Danger
    }
    Box(Modifier.size(170.dp, 120.dp).background(Color(0xD915181E), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(130.dp).padding(top = 18.dp)) {
            val stroke = 12.dp.toPx()
            drawArc(Line, startAngle = 180f, sweepAngle = 180f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, startAngle = 180f, sweepAngle = 180f * score / 100f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 30.dp)) {
            Text("${score}점", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("안전 점수", fontSize = 16.sp, color = Muted)
        }
    }
}

@Composable
private fun StoryPlayingCard(ui: UiState) {
    val label = when {
        ui.talkOpen -> "지금 말을 걸어 보세요"
        ui.session.choices != null -> "답변을 기다리는 중"
        else -> "스토리 재생 중"
    }
    Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xE615181E), RoundedCornerShape(18.dp)).padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("📍", fontSize = 30.sp)
            Column {
                Text(ui.session.pre.scenarioOrNull()?.persona?.name.orEmpty(), fontSize = 18.sp, color = Muted)
                Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().background(Color(0xE615181E), RoundedCornerShape(18.dp)).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            Waveform(if (ui.speaking != null) Color(0xFF2ED3B7) else Line, bars = 15)
        }
    }
}

@Composable
private fun AutoSavedToast() {
    Row(
        Modifier.background(Color(0xE615181E), RoundedCornerShape(16.dp)).border(BorderStroke(1.dp, Color(0xFF2E6FD8)), RoundedCornerShape(16.dp)).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("💾", fontSize = 28.sp)
        Text("스토리가 자동 저장되었습니다.", fontSize = 24.sp)
    }
}

@Composable
private fun HarshToast(kind: HarshKind) {
    val label = when (kind) {
        HarshKind.ACCEL -> "급가속 감지"
        HarshKind.BRAKE -> "급제동 감지"
        HarshKind.TURN -> "거친 조향 감지"
    }
    Row(
        Modifier.background(Color(0xF21E222A), RoundedCornerShape(18.dp)).border(BorderStroke(1.dp, Line), RoundedCornerShape(18.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("⚠", fontSize = 40.sp, color = Danger)
        Column {
            Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("전방을 주시해 주세요.", fontSize = 20.sp, color = Color(0xFFCBD3DF))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("안전 점수", fontSize = 18.sp, color = Muted)
            Text("-${HARSH_PENALTY}점", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Danger)
        }
    }
}

@Composable
private fun ParkCard(modifier: Modifier, saved: Boolean) {
    Column(
        modifier.background(Color(0xF207090D), RoundedCornerShape(20.dp)).padding(horizontal = 80.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🔒", fontSize = 40.sp)
        Text("주차 후 확인해 주세요", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("P단으로 전환하면 주행 결과를 확인할 수 있어요.", fontSize = 20.sp, color = Color(0xFFCBD3DF))
        if (saved) Text("진행 기록 저장 완료", fontSize = 16.sp, color = Muted)
    }
}

@Composable
private fun SubtitleBar(ui: UiState, scenario: Scenario?) {
    val line = ui.speaking ?: ui.session.subtitle?.takeIf { it.speaker != Speaker.DRIVER }?.text ?: ""
    Row(
        Modifier.fillMaxWidth(0.82f).background(Color(0xF00D1016), RoundedCornerShape(20.dp)).padding(horizontal = 32.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Waveform(Accent, bars = 5)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (scenario != null) Text(scenario.persona.name, fontSize = 18.sp, color = Muted)
            Text(line, fontSize = 30.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChoiceBar(ui: UiState, branch: Branch, choose: (RouteHint) -> Unit) {
    val question = ui.speaking ?: ui.session.subtitle?.text.orEmpty()
    Column(
        Modifier.fillMaxWidth().background(Color(0xF50B0E11), RoundedCornerShape(20.dp)).padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Waveform(Accent, bars = 7)
            Text(question, fontSize = 32.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("🎙", fontSize = 34.sp)
        }
        Text(
            "\"${branch.detour.keyword}\" 또는 \"${branch.keep.keyword}\"라고 말하세요" + (ui.answerSecondsLeft?.let { " · ${it}초 후 기존 경로 유지" } ?: ""),
            fontSize = 20.sp,
            color = Muted,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton(branch.keep.label, Panel, Modifier.weight(1f).heightIn(min = 96.dp)) { choose(RouteHint.KEEP) }
            BigButton(branch.detour.label, Accent, Modifier.weight(1f).heightIn(min = 96.dp)) { choose(RouteHint.DETOUR) }
        }
    }
}

@Composable
private fun DemoPanel(vm: DramaViewModel, drive: DriveState?, aiEnabled: Boolean) {
    val driving = drive is DriveState.Driving
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().background(Color(0xEB15181E), RoundedCornerShape(16.dp)).horizontalScroll(rememberScrollState()).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("차량 신호", color = Muted, fontSize = 18.sp)
        @Composable fun sig(label: String, enabled: Boolean = driving, color: Color = Color(0xFF2B3340), onClick: () -> Unit) =
            Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) { Text(label, fontSize = 18.sp) }
        sig("급가속") { vm.harsh(HarshKind.ACCEL) }
        sig("급제동") { vm.harsh(HarshKind.BRAKE) }
        sig("거친 조향") { vm.harsh(HarshKind.TURN) }
        sig("정체 발생") { vm.congestion() }
        sig("경로 안내") { vm.guidance("300미터 앞에서 오른쪽으로 가세요.") }
        if (aiEnabled) sig("말 걸기", color = Good) { vm.say("우리 지금 어디로 가는 거야?") }
        sig("기어 P", enabled = true) { vm.gear(Gear.P) }
        sig("에어백 전개", enabled = true, color = Danger) { vm.emergency(EmergencyCause.AIRBAG) }
    }
}

@Composable
private fun EmergencyScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp).widthIn(max = 1000.dp).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF181011), Color(0xFF0B0B0C))), RoundedCornerShape(48.dp)).padding(horizontal = 48.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("⚠", fontSize = 100.sp, color = Danger)
            Text("긴급 상황 감지", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Danger)
            Text("스토리가 종료되었습니다.", fontSize = 38.sp)
            Text("전방을 확인하고 안전 운전에 집중해 주세요.", fontSize = 26.sp, color = Color(0xFFCBD3DF))
        }
    }
}

private fun tierColors(tier: CardTier): List<Color> = when (tier) {
    CardTier.GOLD -> listOf(Color(0xFFFFE08A), Color(0xFFC9932B))
    CardTier.SILVER -> listOf(Color(0xFFE6EBF2), Color(0xFF8D99AB))
    CardTier.WOOD -> listOf(Color(0xFFB98552), Color(0xFF6E4A2A))
}

@Composable
private fun ResultCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(horizontal = 28.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun ReportScreen(vm: DramaViewModel, ui: UiState, openLog: () -> Unit) {
    val r = ui.session.report ?: return
    val scenario = ui.session.pre.scenarioOrNull()
    val brakes = r.harsh[HarshKind.BRAKE] ?: 0
    val turns = r.harsh[HarshKind.TURN] ?: 0
    val gained = r.progressAfter - r.progressBefore
    val bond = r.bondAfter
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("주행 결과", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(scenario?.title.orEmpty(), fontSize = 22.sp, color = Muted)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ResultCard {
                    Text("안전 주행 점수", fontSize = 22.sp, color = Muted)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${r.safetyScore}점", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text("/ 100점", fontSize = 24.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text("급제동 ${brakes}회 · " + if (turns == 0) "부드러운 조향 달성" else "거친 조향 ${turns}회", fontSize = 22.sp, color = Muted)
                }
                ResultCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("스토리 진행률", fontSize = 22.sp, color = Muted)
                        Chip("+${gained}%p", Accent)
                    }
                    Text("${r.progressBefore}% → ${r.progressAfter}%", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { r.progressAfter / 100f }, modifier = Modifier.fillMaxWidth().height(12.dp), color = Accent, trackColor = Line)
                    Text(r.chapterTitle, fontSize = 18.sp, color = Muted)
                }
                ResultCard {
                    Text("${scenario?.persona?.name.orEmpty()} 신뢰도", fontSize = 22.sp, color = Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lv.${bond.level} ${bond.title}", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${bond.points} / ${bond.nextAt}", fontSize = 24.sp, color = Muted)
                    }
                    LinearProgressIndicator(progress = { (bond.points.toFloat() / bond.nextAt).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(12.dp), color = Accent, trackColor = Line)
                }
            }
            val colors = tierColors(r.reward.tier)
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .border(BorderStroke(3.dp, Brush.linearGradient(colors)), RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF15304A), Color(0xFF0D1520))), RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("획득 보상", fontSize = 24.sp, color = Color(0xFFCBD3DF))
                Spacer(Modifier.weight(1f))
                Pictogram(scenario, 180)
                Text(r.reward.title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Chip(r.reward.tier.label, Color(0x33FFFFFF))
                Spacer(Modifier.weight(1f))
                Text("안전 운전 점수로만 등급이 정해집니다", color = Muted, fontSize = 18.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.weight(1f))
            BigButton("기록 보기", Panel, Modifier.width(320.dp), onClick = openLog)
            BigButton("완료", modifier = Modifier.width(320.dp)) { vm.home() }
        }
    }
}

@Composable
private fun LogScreen(ui: UiState, close: () -> Unit) {
    val scenario = ui.session.pre.scenarioOrNull()
    val save = (ui.session.pre as? PreDriveState.AskResume)?.save
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("이전 대화 기록")
            Spacer(Modifier.weight(1f))
            BigButton("닫기", Panel, onClick = close)
        }
        if (scenario != null && save != null) {
            Text("진행 중인 챕터: ${scenario.chapters.getOrNull(save.cursor.chapter)?.title.orEmpty()} (진행률 ${scenario.progressPercent(save.cursor)}%)", fontSize = 22.sp, color = Muted)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ui.session.log) { e -> LogRow(e, scenario?.persona?.name ?: "캐릭터") }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry, name: String) {
    val (who, color) = when (e.speaker) {
        Speaker.DRIVER -> "나(운전자)" to Accent
        Speaker.NARRATOR -> "내레이션" to Muted
        Speaker.CHARACTER -> name to Color(0xFFB9A6FF)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("[$who]", color = color, fontSize = 22.sp, modifier = Modifier.width(160.dp))
        Text(
            if (e.speaker == Speaker.DRIVER) "\"${e.text}\" (선택 완료)" else "\"${e.text}\"",
            fontSize = 22.sp,
            fontStyle = if (e.speaker == Speaker.NARRATOR) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

@Composable
private fun AiSettingsScreen(vm: DramaViewModel, ui: UiState, close: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(ui.aiBaseUrl) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("AI 설정")
        Text(
            "Claude API 키를 넣으면 AI가 시나리오를 만들고, 주행 상황에 맞춰 대사를 쓰고, 말을 걸면 대답합니다. " +
                "키는 이 기기에만 저장됩니다. 키가 없으면 미리 쓴 장면으로 진행합니다.",
            color = Muted, fontSize = 20.sp,
        )
        Text(if (ui.aiEnabled) "현재 상태: 켜짐 (저장된 키 사용 중. 키 칸을 비워 두고 저장하면 그대로 유지됩니다)" else "현재 상태: 꺼짐", fontSize = 22.sp)
        OutlinedTextField(key, { key = it }, label = { Text("API 키") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("서버 주소 (비워 두면 기본값)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ui.aiMessage?.let { Text(it, color = Accent, fontSize = 20.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("저장") { vm.saveAiSettings(key, baseUrl) }
            BigButton("AI 끄기", Panel) { vm.configureAi("", baseUrl) }
            BigButton("닫기", Panel, onClick = close)
        }
    }
}

@Composable
private fun AuthoringScreen(vm: DramaViewModel, ui: UiState, openAiSettings: () -> Unit, close: () -> Unit) {
    var idea by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var premise by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf("") }
    var chapterTitle by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var beats by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("내 시나리오 만들기") }
        if (!manual) item {
            Column(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AI로 만들기", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("한 줄 아이디어만 쓰면 AI가 캐릭터, 챕터, 선택지, 반응 대사까지 씁니다. 예: 좀비 사태 속 구급차 기사", color = Muted, fontSize = 18.sp)
                OutlinedTextField(idea, { idea = it }, label = { Text("한 줄 아이디어") }, singleLine = true, enabled = !ui.aiBusy, modifier = Modifier.fillMaxWidth())
                ui.aiMessage?.let { Text(it, color = Accent, fontSize = 18.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (ui.aiEnabled) BigButton(if (ui.aiBusy) "생성 중…" else "AI로 생성") { vm.generateScenario(idea) }
                    else BigButton("AI 설정 열기", onClick = openAiSettings)
                    BigButton("직접 쓰기", Panel) { manual = true }
                    BigButton("닫기", Panel, onClick = close)
                }
            }
        }
        if (manual) {
        item { Text("대사는 한 줄에 80자 이하로 써 주세요. 첫 장면이 핵심 장면이 됩니다.", color = Muted, fontSize = 20.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("제목") }, modifier = Modifier.weight(1f))
                OutlinedTextField(name, { name = it }, label = { Text("캐릭터 이름") }, modifier = Modifier.weight(1f))
                OutlinedTextField(tone, { tone = it }, label = { Text("캐릭터 말투") }, modifier = Modifier.weight(1f))
            }
        }
        item { OutlinedTextField(premise, { premise = it }, label = { Text("세계관") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(chapterTitle, { chapterTitle = it }, label = { Text("1장 제목") }, modifier = Modifier.weight(1f))
                OutlinedTextField(opening, { opening = it }, label = { Text("도입 대사") }, modifier = Modifier.weight(2f))
            }
        }
        item { OutlinedTextField(beats, { beats = it }, label = { Text("장면 대사 (한 줄에 하나)") }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("저장") {
                    if (vm.saveDraft(title, premise, name, tone, chapterTitle, opening, beats.lines().filter { it.isNotBlank() })) close()
                }
                BigButton("닫기", Panel, onClick = close)
            }
        }
        items(ui.authoringIssues) { Text(it, color = Danger, fontSize = 18.sp) }
        }
    }
}
