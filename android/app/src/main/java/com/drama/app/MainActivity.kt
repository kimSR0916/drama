package com.drama.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drama.app.authoring.BeatDraft
import com.drama.app.authoring.ChapterDraft
import com.drama.app.authoring.ScenarioDraft
import com.drama.app.core.DriveState
import com.drama.app.core.EmergencyCause
import com.drama.app.core.Gear
import com.drama.app.core.HarshKind
import com.drama.app.core.PacingMode
import com.drama.app.core.PreDriveState
import com.drama.app.core.Prompts
import com.drama.app.core.ScenarioSource
import com.drama.app.session.LineSpeaker
import kotlin.math.ceil

private val Accent = Color(0xFF4C8DFF)
private val Danger = Color(0xFFE5483B)
private val Panel = Color(0xFF172033)
private val Muted = Color(0xFF9AA6BA)

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
    LaunchedEffect(ui.systemUxRestricted) { if (ui.systemUxRestricted) authoring = false }
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        StatusBar(ui)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s.sos != null -> SosScreen(s.sos)
                s.report != null -> ReportScreen(vm, ui)
                s.drive != null -> DriveScreen(vm, ui)
                authoring -> AuthoringScreen(vm, ui) { authoring = false }
                else -> PreDriveScreen(vm, ui) { authoring = true }
            }
        }
        if (ui.demoMode && s.drive != null && s.sos == null && s.report == null) DemoPanel(vm, s.drive)
    }
}

@Composable
private fun StatusBar(ui: UiState) {
    val s = ui.session
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("드라마", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Chip(if (s.uxLocked || ui.systemUxRestricted) "주행 제한 활성" else "정차 중", if (s.uxLocked) Accent else Panel)
        if (s.drive is DriveState.Driving) Chip("도착까지 ${ceil(s.etaMinutes).toInt()}분", Panel)
        if (s.bgm) Chip("BGM 재생", Panel)
        if (ui.demoMode) Chip("시연 모드 ${DEMO_SPEED}배속", Color(0xFF5B3DB8))
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
private fun PreDriveScreen(vm: DramaViewModel, ui: UiState, openAuthoring: () -> Unit) {
    when (val p = ui.session.pre) {
        PreDriveState.Selecting -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("오늘은 어떤 이야기를 달릴까요?")
                Spacer(Modifier.weight(1f))
                if (!ui.systemUxRestricted) BigButton("내 시나리오 만들기", Panel, onClick = openAuthoring)
            }
            Spacer(Modifier.height(24.dp))
            LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(ui.stories, key = { it.id }) { story ->
                    Button(
                        onClick = { vm.selectStory(story.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 200.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(if (story.source == ScenarioSource.CUSTOM) "내 시나리오" else "기본 테마", color = Muted, fontSize = 18.sp)
                            Text(story.title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(story.premise, fontSize = 18.sp, color = Muted, maxLines = 3)
                        }
                    }
                }
            }
        }
        is PreDriveState.OfferNew -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Title(p.scenario.title)
            Text(p.scenario.premise, fontSize = 24.sp, color = Muted)
            Text("${p.scenario.persona.name} · ${p.scenario.persona.tone}", fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("새로 시작") { vm.startNew() }
                BigButton("뒤로", Panel) { vm.back() }
            }
        }
        is PreDriveState.AskResume, is PreDriveState.AskRestart -> Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Title(if (p is PreDriveState.AskResume) Prompts.ASK_RESUME else Prompts.ASK_RESTART)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("예", modifier = Modifier.width(240.dp)) { vm.answer(true) }
                BigButton("아니오", Panel, Modifier.width(240.dp)) { vm.answer(false) }
            }
        }
        is PreDriveState.AwaitingDestination, is PreDriveState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Title("목적지까지 얼마나 걸리나요?")
            Text("10분 미만이면 한 사건으로 압축하고, 10분 이상이면 한 장을 끝까지 들려드려요.", fontSize = 22.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ui.naviEtaMinutes?.let { eta -> BigButton("내비 경로 ${ceil(eta).toInt()}분") { vm.setDestination(eta) } }
                listOf(5.0, 15.0, 30.0).forEach { m -> BigButton("${m.toInt()}분", Panel) { vm.setDestination(m) } }
            }
            if (p is PreDriveState.Ready) {
                Text(
                    "${p.etaMinutes.toInt()}분 · ${if (p.plan.mode == PacingMode.SHORT) "한 사건 압축" else "풀 서사"} · 장면 ${p.plan.beats.size}개",
                    fontSize = 24.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigButton("주행 시작") { vm.startDriving() }
                    Text("시연 모드", fontSize = 20.sp)
                    Switch(checked = ui.demoMode, onCheckedChange = vm::setDemoMode)
                }
            }
        }
    }
}

@Composable
private fun DriveScreen(vm: DramaViewModel, ui: UiState) {
    val s = ui.session
    val listState = rememberLazyListState()
    LaunchedEffect(s.transcript.size) { if (s.transcript.isNotEmpty()) listState.animateScrollToItem(s.transcript.size - 1) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (s.drive is DriveState.Arrived) Chip("도착했어요. P단을 체결하면 오늘의 리포트가 열립니다.", Accent)
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(s.transcript) { line ->
                val latest = line === s.transcript.last()
                Text(
                    line.text,
                    fontSize = if (latest) 36.sp else 22.sp,
                    fontStyle = if (line.speaker == LineSpeaker.NARRATOR) FontStyle.Italic else FontStyle.Normal,
                    color = when {
                        line.speaker == LineSpeaker.SYSTEM -> Accent
                        latest -> Color.White
                        else -> Muted
                    },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        s.choices?.let { choices ->
            Text("말로 답하거나 버튼을 누르세요", fontSize = 20.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                choices.forEach { c -> BigButton(c.label, modifier = Modifier.weight(1f)) { vm.choose(c.id) } }
            }
        }
    }
}

@Composable
private fun DemoPanel(vm: DramaViewModel, drive: DriveState) {
    val driving = drive is DriveState.Driving
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("차량 신호", color = Muted, fontSize = 18.sp)
        @Composable fun sig(label: String, enabled: Boolean = driving, color: Color = Color(0xFF2B3A55), onClick: () -> Unit) =
            Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) { Text(label, fontSize = 18.sp) }
        sig("급가속") { vm.harsh(HarshKind.ACCEL) }
        sig("급제동") { vm.harsh(HarshKind.BRAKE) }
        sig("정체 발생") { vm.congestion() }
        sig("경로 안내") { vm.guidance("300미터 앞에서 오른쪽으로 가세요.") }
        sig("기어 P", enabled = true) { vm.gear(Gear.P) }
        sig("에어백 전개", enabled = true, color = Danger) { vm.emergency(EmergencyCause.AIRBAG) }
    }
}

@Composable
private fun SosScreen(cause: EmergencyCause) {
    Box(Modifier.fillMaxSize().background(Danger, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOS", fontSize = 96.sp, fontWeight = FontWeight.Black)
            Text(if (cause == EmergencyCause.AIRBAG) "에어백 전개 감지" else "충돌 감지", fontSize = 32.sp)
            Text("모든 연출을 멈추고 긴급 구조를 우선합니다.", fontSize = 26.sp)
        }
    }
}

@Composable
private fun ReportScreen(vm: DramaViewModel, ui: UiState) {
    val r = ui.session.report ?: return
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Title("오늘의 주행 리포트")
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Stat("안전 점수", "${r.safetyScore}점")
            Stat("급가속 · 급제동", "${r.harsh[HarshKind.ACCEL] ?: 0} · ${r.harsh[HarshKind.BRAKE] ?: 0}")
            Stat("이번에 본 장면", "${r.playedBeats}개")
            Stat("주행 시간", "${r.durationMinutes}분")
        }
        Text("스토리 진행률 ${r.progressPercent}%", fontSize = 24.sp)
        LinearProgressIndicator(progress = { r.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().height(14.dp))
        r.reward?.let {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF6A4CFF), Color(0xFF0A9BD3))), RoundedCornerShape(20.dp))
                    .padding(28.dp),
            ) { Text("보상 카드 · ${it.title}", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        }
        BigButton("처음으로") { vm.home() }
    }
}

@Composable
private fun Stat(label: String, value: String) = Column {
    Text(label, color = Muted, fontSize = 20.sp)
    Text(value, fontSize = 40.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AuthoringScreen(vm: DramaViewModel, ui: UiState, close: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var premise by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf("") }
    var chapterTitle by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var beats by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("내 시나리오 만들기") }
        item { Text("정차 중에만 열립니다. 모바일 앱에서 만든 시나리오도 같은 계정 저장소에 쌓입니다.", color = Muted, fontSize = 20.sp) }
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
                OutlinedTextField(opening, { opening = it }, label = { Text("도입부 대사") }, modifier = Modifier.weight(2f))
            }
        }
        item { OutlinedTextField(beats, { beats = it }, label = { Text("장면 (한 줄에 하나)") }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
        items(ui.authoringIssues) { Text(it, color = Danger, fontSize = 18.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("저장") {
                    val draft = ScenarioDraft(
                        title, premise, name, tone,
                        listOf(ChapterDraft(chapterTitle, opening, beats.lines().filter { it.isNotBlank() }.map { BeatDraft(it) })),
                    )
                    if (vm.saveDraft(draft)) close()
                }
                BigButton("닫기", Panel, onClick = close)
            }
        }
    }
}
