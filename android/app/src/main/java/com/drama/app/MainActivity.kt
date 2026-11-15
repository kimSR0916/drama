package com.drama.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drama.app.authoring.LogEntry
import com.drama.app.core.Branch
import com.drama.app.core.CardTier
import com.drama.app.core.DriveState
import com.drama.app.core.EmergencyCause
import com.drama.app.core.Gear
import com.drama.app.core.HarshKind
import com.drama.app.core.PacingMode
import com.drama.app.core.PreDriveState
import com.drama.app.core.Prompts
import com.drama.app.core.RouteHint
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource
import com.drama.app.core.Speaker
import kotlin.math.ceil

private val Accent = Color(0xFF4C8DFF)
private val Danger = Color(0xFFE5483B)
private val Panel = Color(0xFF172033)
private val Muted = Color(0xFF9AA6BA)
private val MapBase = Color(0xFF1B2638)
private val MapRoad = Color(0xFF2C3A52)

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
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color(0xFF0B1120), surface = Color(0xFF0B1120))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
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
        ui.sosLocked -> LockedScreen()
        s.drive != null && s.report == null -> DriveScreen(vm, ui)
        else -> Column(Modifier.fillMaxSize().padding(32.dp)) {
            StatusBar(ui)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
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
private fun StatusBar(ui: UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("드라마", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Chip(if (ui.systemUxRestricted) "주행 제한 활성" else "정차 중 · P", Panel)
        if (ui.demoMode) Chip("시연 모드 ${DEMO_SPEED}배속", Color(0xFF5B3DB8))
        Chip(if (ui.aiEnabled) "AI 켜짐" else "AI 꺼짐", if (ui.aiEnabled) Color(0xFF1F7A4D) else Panel)
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(Modifier.background(color, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, fontSize = 18.sp)
    }
}

@Composable
private fun BigButton(text: String, color: Color = Accent, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 40.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Pictogram(scenario: Scenario?, size: Int) {
    Box(
        Modifier.size(size.dp).background(Brush.linearGradient(listOf(Color(0xFF6A4CFF), Color(0xFF0A9BD3))), CircleShape),
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
private fun PreDriveScreen(vm: DramaViewModel, ui: UiState, openAuthoring: () -> Unit, openLog: () -> Unit, openAiSettings: () -> Unit) {
    val p = ui.session.pre
    val hasLog = ui.session.log.isNotEmpty()
    when (p) {
        PreDriveState.Selecting -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("오늘은 어떤 이야기를 달릴까요?")
                Spacer(Modifier.weight(1f))
                if (!ui.systemUxRestricted) {
                    BigButton("AI 설정", Panel, onClick = openAiSettings)
                    Spacer(Modifier.width(16.dp))
                    BigButton("내 시나리오 만들기", Panel, onClick = openAuthoring)
                }
            }
            Spacer(Modifier.height(24.dp))
            LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(ui.stories, key = { it.id }) { story ->
                    Button(
                        onClick = { vm.selectStory(story.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 220.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Pictogram(story, 52)
                                Text(if (story.source == ScenarioSource.CUSTOM) "내 시나리오" else story.genre, color = Muted, fontSize = 18.sp)
                            }
                            Text(story.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(story.premise, fontSize = 17.sp, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        is PreDriveState.OfferNew -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Pictogram(p.scenario, 96)
                Column {
                    Text(p.scenario.genre, color = Muted, fontSize = 20.sp)
                    Title(p.scenario.title)
                }
            }
            Text(p.scenario.premise, fontSize = 24.sp, color = Muted)
            Text("${p.scenario.persona.name} · ${p.scenario.persona.tone}", fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("시작하기") { vm.startNew() }
                BigButton("뒤로", Panel) { vm.back() }
            }
        }
        is PreDriveState.AskResume, is PreDriveState.AskRestart -> Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Pictogram(p.scenarioOrNull(), 96)
                Column {
                    Text(p.scenarioOrNull()?.title.orEmpty(), color = Muted, fontSize = 22.sp)
                    Title(if (p is PreDriveState.AskResume) Prompts.ASK_RESUME else Prompts.ASK_RESTART)
                }
            }
            if (p is PreDriveState.AskResume) {
                val chapter = p.scenario.chapters.getOrNull(p.save.cursor.chapter)
                Text("${chapter?.title.orEmpty()} · 진행률 ${p.scenario.progressPercent(p.save.cursor)}%", fontSize = 24.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("예", modifier = Modifier.width(240.dp)) { vm.answer(true) }
                BigButton("아니오", Panel, Modifier.width(240.dp)) { vm.answer(false) }
                if (hasLog && p is PreDriveState.AskResume) BigButton("이전 대화 기록", Panel, onClick = openLog)
            }
        }
        is PreDriveState.AwaitingDestination, is PreDriveState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Title("목적지까지 얼마나 걸리나요?")
            Text("10분 미만이면 핵심 장면 하나로 압축하고, 10분 이상이면 풀 에피소드로 들려드려요.", fontSize = 22.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ui.naviEtaMinutes?.let { eta -> BigButton("내비 경로 ${ceil(eta).toInt()}분") { vm.setDestination(eta) } }
                listOf(8.0, 15.0, 32.0).forEach { m -> BigButton("${m.toInt()}분", Panel) { vm.setDestination(m) } }
            }
            if (p is PreDriveState.Ready) {
                Text(
                    "${p.etaMinutes.toInt()}분 · ${if (p.plan.mode == PacingMode.SHORT) "핵심 장면 압축" else "풀 에피소드"} · 장면 ${p.plan.beats.size}개",
                    fontSize = 24.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigButton("주행 시작") { vm.startDriving() }
                    if (hasLog) BigButton("이전 대화 기록", Panel, onClick = openLog)
                    Text("시연 모드", fontSize = 20.sp)
                    Switch(checked = ui.demoMode, onCheckedChange = vm::setDemoMode)
                }
            }
        }
    }
}

@Composable
private fun MapStandIn(modifier: Modifier) {
    Canvas(modifier.background(MapBase)) {
        val step = 140.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(MapRoad, Offset(x, 0f), Offset(x + size.height * 0.3f, size.height), strokeWidth = 18f)
            x += step
        }
        var y = step / 2
        while (y < size.height) {
            drawLine(MapRoad, Offset(0f, y), Offset(size.width, y - 40f), strokeWidth = 12f)
            y += step
        }
        drawLine(Accent, Offset(size.width * 0.15f, size.height), Offset(size.width * 0.55f, size.height * 0.25f), strokeWidth = 22f)
    }
}

@Composable
private fun DriveScreen(vm: DramaViewModel, ui: UiState) {
    val s = ui.session
    val scenario = s.pre.scenarioOrNull()
    Box(Modifier.fillMaxSize()) {
        MapStandIn(Modifier.fillMaxSize())
        Text("내비게이션 영역", color = Muted, fontSize = 18.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp))
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            FloatingBar(ui, scenario)
            Spacer(Modifier.weight(1f))
            s.choices?.let { ChoicePanel(it, ui.answerSecondsLeft) { hint -> vm.choose(hint) } }
            if (ui.demoMode && s.sos == null) DemoPanel(vm, s.drive, ui.aiEnabled)
        }
    }
}

@Composable
private fun FloatingBar(ui: UiState, scenario: Scenario?) {
    val s = ui.session
    val line = ui.speaking ?: s.subtitle?.takeIf { it.speaker != Speaker.DRIVER }?.text ?: ""
    Row(
        Modifier.fillMaxWidth().background(Color(0xE60B1120), RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Pictogram(scenario, 64)
        Column(Modifier.weight(1f)) {
            Text(scenario?.persona?.name.orEmpty(), color = Muted, fontSize = 18.sp)
            Text(line, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ui.talkOpen) Chip("지금 말을 걸어 보세요", Color(0xFF1F7A4D))
        when (s.drive) {
            is DriveState.Arrived -> Chip("P단 체결 시 리포트", Accent)
            else -> Chip("도착까지 ${ceil(s.etaMinutes).toInt()}분", Panel)
        }
    }
}

@Composable
private fun ChoicePanel(branch: Branch, secondsLeft: Int?, choose: (RouteHint) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "\"${branch.detour.keyword}\" 또는 \"${branch.keep.keyword}\"라고 말하세요" + (secondsLeft?.let { " · ${it}초 후 기존 경로 유지" } ?: ""),
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.background(Color(0xCC0B1120), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ChoiceButton("A · 우회", branch.detour.label, Color(0xFF2F6FE0), Modifier.weight(1f)) { choose(RouteHint.DETOUR) }
            ChoiceButton("B · 기본값", branch.keep.label, Color(0xFF3A4558), Modifier.weight(1f)) { choose(RouteHint.KEEP) }
        }
    }
}

@Composable
private fun ChoiceButton(tag: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tag, fontSize = 22.sp, color = Color(0xFFD0DAEA))
            Text(label, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DemoPanel(vm: DramaViewModel, drive: DriveState?, aiEnabled: Boolean) {
    val driving = drive is DriveState.Driving
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().background(Color(0xE6172033), RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("차량 신호", color = Muted, fontSize = 18.sp)
        @Composable fun sig(label: String, enabled: Boolean = driving, color: Color = Color(0xFF2B3A55), onClick: () -> Unit) =
            Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) { Text(label, fontSize = 18.sp) }
        sig("급가속") { vm.harsh(HarshKind.ACCEL) }
        sig("급제동") { vm.harsh(HarshKind.BRAKE) }
        sig("거친 조향") { vm.harsh(HarshKind.TURN) }
        sig("정체 발생") { vm.congestion() }
        sig("경로 안내") { vm.guidance("300미터 앞에서 오른쪽으로 가세요.") }
        if (aiEnabled) sig("말 걸기", color = Color(0xFF1F7A4D)) { vm.say("우리 지금 어디로 가는 거야?") }
        sig("기어 P", enabled = true) { vm.gear(Gear.P) }
        sig("에어백 전개", enabled = true, color = Danger) { vm.emergency(EmergencyCause.AIRBAG) }
    }
}

@Composable
private fun LockedScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("긴급 상황이 감지되어 이야기를 종료했습니다", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("진행 기록은 저장되었습니다. 차량 SOS 안내를 따라 주세요.", fontSize = 24.sp, color = Muted)
        }
    }
}

private fun tierColors(tier: CardTier): List<Color> = when (tier) {
    CardTier.GOLD -> listOf(Color(0xFFFFE08A), Color(0xFFC9932B))
    CardTier.SILVER -> listOf(Color(0xFFE6EBF2), Color(0xFF8D99AB))
    CardTier.WOOD -> listOf(Color(0xFFB98552), Color(0xFF6E4A2A))
}

@Composable
private fun ReportScreen(vm: DramaViewModel, ui: UiState, openLog: () -> Unit) {
    val r = ui.session.report ?: return
    val brakes = r.harsh[HarshKind.BRAKE] ?: 0
    val turns = r.harsh[HarshKind.TURN] ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Title("오늘의 주행 결과")
            Text(r.chapterTitle, color = Muted, fontSize = 22.sp)
            Text("스토리 진행률  ${r.progressBefore}% → ${r.progressAfter}%  (+${r.progressAfter - r.progressBefore}%)", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { r.progressAfter / 100f }, modifier = Modifier.fillMaxWidth().height(14.dp))
            Text("안전 주행 점수  ${r.safetyScore}점 / 100점", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("급제동 ${brakes}회 · " + if (turns == 0) "부드러운 조향 달성" else "거친 조향 ${turns}회", fontSize = 22.sp, color = Muted)
            val bond = r.bondAfter
            Text("${ui.session.pre.scenarioOrNull()?.persona?.name.orEmpty()} 호감도  Lv.${bond.level} ${bond.title} (${bond.points} / ${bond.nextAt})", fontSize = 26.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("이전 대화 기록", Panel, onClick = openLog)
                BigButton("처음으로") { vm.home() }
            }
        }
        val colors = tierColors(r.reward.tier)
        Box(
            Modifier.weight(1f).heightIn(min = 420.dp)
                .border(BorderStroke(10.dp, Brush.linearGradient(colors)), RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2350), Color(0xFF0B1120))), RoundedCornerShape(24.dp))
                .padding(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("보상 카드 · ${r.reward.tier.label}", color = colors.first(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(40.dp))
                Pictogram(ui.session.pre.scenarioOrNull(), 140)
                Spacer(Modifier.height(20.dp))
                Text(r.reward.title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("안전 운전 점수로만 등급이 정해집니다", color = Muted, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun LogScreen(ui: UiState, close: () -> Unit) {
    val scenario = ui.session.pre.scenarioOrNull()
    val save = (ui.session.pre as? PreDriveState.AskResume)?.save
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("이전 대화 기록")
            Spacer(Modifier.weight(1f))
            BigButton("닫기", Panel, onClick = close)
        }
        if (scenario != null && save != null) {
            Text("진행 중인 챕터: ${scenario.chapters.getOrNull(save.cursor.chapter)?.title.orEmpty()} (진행률 ${scenario.progressPercent(save.cursor)}%)", fontSize = 22.sp, color = Muted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Text(if (ui.aiEnabled) "현재 상태: 켜짐 (저장된 키 사용 중)" else "현재 상태: 꺼짐", fontSize = 22.sp)
        OutlinedTextField(key, { key = it }, label = { Text("API 키") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("서버 주소 (비워 두면 기본값)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ui.aiMessage?.let { Text(it, color = Accent, fontSize = 20.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("저장") { vm.configureAi(key, baseUrl) }
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
