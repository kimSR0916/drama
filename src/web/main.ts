import { BUILTIN_SCENARIOS } from "../authoring/builtin";
import { parseScenarioDraft } from "../authoring/draft";
import { accountStore, type KeyValue } from "../authoring/store";
import type { HarshKind } from "../core/report";
import { PROMPTS } from "../core/predrive";
import { createController, type Line } from "./controller";

const SIM_MS_PER_REAL_MS = 60;
const FRAME_MS = 250;

const browserKeyValue: KeyValue = {
  get: (k) => {
    try { return localStorage.getItem(k); } catch { return null; }
  },
  set: (k, v) => {
    try { localStorage.setItem(k, v); } catch { /* storage blocked: session-only */ }
  },
  remove: (k) => {
    try { localStorage.removeItem(k); } catch { /* storage blocked */ }
  },
  keys: () => {
    try { return Object.keys(localStorage); } catch { return []; }
  },
};

const store = accountStore(browserKeyValue, "demo-account");
const ttsToggle = document.querySelector<HTMLInputElement>("#tts")!;

function speak(line: Line) {
  if (!ttsToggle.checked || line.speaker === "system" || !("speechSynthesis" in window)) return;
  const u = new SpeechSynthesisUtterance(line.text);
  u.lang = "ko-KR";
  speechSynthesis.speak(u);
}

const c = createController(store, speak);
const screen = document.querySelector<HTMLDivElement>("#screen")!;
const signals = document.querySelector<HTMLDivElement>("#signals")!;

const esc = (s: string) => s.replace(/[&<>"']/g, (ch) => `&#${ch.charCodeAt(0)};`);

function stories() {
  const custom = store.listScenarios().filter((s) => s.source === "custom");
  return [...custom, ...BUILTIN_SCENARIOS];
}

function storyOf(id: string) {
  return stories().find((s) => s.id === id);
}

function screenHtml(): string {
  const v = c.view();
  if (v.sos) return `<div class="sos">SOS 모드<br/><small>TTS/BGM 음소거 · 긴급 구조 연결 중</small></div>`;
  if (v.report) {
    const r = v.report;
    return `<div class="report"><h3>주행 리포트</h3><dl>
      <dt>안전 점수</dt><dd>${r.safetyScore}점</dd>
      <dt>급가속·급제동·급회전</dt><dd>${r.harsh.accel} · ${r.harsh.brake} · ${r.harsh.turn}</dd>
      <dt>이번에 본 장면</dt><dd>${r.playedBeats}개</dd>
      <dt>스토리 진행률</dt><dd>${r.progressPercent}%</dd>
      <dt>주행 시간</dt><dd>${r.durationMinutes}분</dd></dl>
      ${r.reward ? `<div class="card">보상 카드 · ${esc(r.reward.title)}</div>` : ""}
      <p><button data-action="home">처음으로</button></p></div>`;
  }
  if (v.drive) {
    const lines = v.transcript.map((l) => `<p class="${l.speaker}">${esc(l.text)}</p>`).join("");
    const choices = v.choices
      ? `<div>${v.choices.map((ch) => `<button class="big" data-action="choose" data-id="${ch.id}">${esc(ch.label)}</button>`).join("")}</div>`
      : "";
    return `<div class="status"><span class="lock">${v.uxLocked ? "주행 제한 활성" : "제한 해제"}</span>
      <span>남은 시간 ${Math.ceil(v.etaMinutes)}분</span><span>BGM ${v.bgm ? "재생" : "정지"}</span>
      <span>${v.drive.phase === "arrived" ? "도착. P단 체결 전까지 화면 잠금" : ""}</span></div>
      <div class="transcript">${lines}</div>${choices}`;
  }
  const p = v.pre;
  switch (p.kind) {
    case "selecting":
      return `<h3>스토리 선택</h3><div class="stories">${stories()
        .map((s) => `<button data-action="select" data-id="${s.id}">${esc(s.title)}<small>${s.source === "custom" ? "내 시나리오" : "기본 테마"} · ${esc(s.persona.name)}</small></button>`)
        .join("")}</div>`;
    case "offerNew":
      return `<h3>${esc(p.scenario.title)}</h3><p>${esc(p.scenario.premise)}</p><button class="big" data-action="new">새로 시작</button>`;
    case "askResume":
    case "askRestart":
      return `<h3>${p.kind === "askResume" ? PROMPTS.askResume : PROMPTS.askRestart}</h3>
        <button class="big" data-action="yes">예</button><button class="big secondary" data-action="no">아니오</button>`;
    case "awaitingDestination":
    case "ready":
      return `<h3>목적지 설정</h3><p>내비 예상 소요 시간을 고르세요. 10분 미만이면 한 사건으로 압축됩니다.</p>
        ${[5, 15, 30].map((m) => `<button data-action="eta" data-min="${m}">${m}분</button>`).join("")}
        ${p.kind === "ready" ? `<p>${p.etaMinutes}분 · ${p.plan.mode === "short" ? "압축" : "풀 서사"} · 장면 ${p.plan.beats.length}개</p><button class="big" data-action="drive">주행 시작</button>` : ""}`;
  }
}

function signalsHtml(): string {
  const driving = c.view().drive?.phase === "driving";
  const active = c.view().drive && !c.view().sos && !c.view().report;
  const btn = (action: string, label: string, enabled: boolean, cls = "secondary", extra = "") =>
    `<button class="${cls}" data-action="${action}" ${extra} ${enabled ? "" : "disabled"}>${label}</button>`;
  return [
    btn("harsh", "급가속", driving, "secondary", 'data-kind="accel"'),
    btn("harsh", "급제동", driving, "secondary", 'data-kind="brake"'),
    btn("harsh", "급회전", driving, "secondary", 'data-kind="turn"'),
    btn("congestion", "정체 발생 (+3분)", driving),
    btn("guide", "경로 안내", driving),
    btn("park", "기어 P", !!active),
    btn("sos", "에어백 전개", !!active, "danger"),
  ].join("");
}

let shown = { screen: "", signals: "" };

function render() {
  const next = { screen: screenHtml(), signals: signalsHtml() };
  if (next.screen !== shown.screen) screen.innerHTML = next.screen;
  if (next.signals !== shown.signals) signals.innerHTML = next.signals;
  shown = next;
  const t = screen.querySelector(".transcript");
  if (t) t.scrollTop = t.scrollHeight;
}

document.body.addEventListener("click", (ev) => {
  const el = (ev.target as HTMLElement).closest<HTMLElement>("[data-action]");
  if (!el || (el as HTMLButtonElement).disabled) return;
  const now = c.view().now;
  switch (el.dataset.action) {
    case "select": {
      const s = storyOf(el.dataset.id!);
      if (s && !store.getScenario(s.id)) store.putScenario(s);
      c.selectStory(el.dataset.id!);
      break;
    }
    case "new": c.startNew(); break;
    case "yes": c.answer(true); break;
    case "no": c.answer(false); break;
    case "eta": c.pre({ type: "setDestination", etaMinutes: Number(el.dataset.min) }); break;
    case "drive": c.startDriving(); break;
    case "choose": c.send({ type: "driverInput", choiceId: el.dataset.id!, via: "button", now }); break;
    case "harsh": c.send({ type: "harshDriving", kind: el.dataset.kind as HarshKind, now }); break;
    case "congestion": c.congestion(); break;
    case "guide": c.send({ type: "routeGuidance", instruction: "300미터 앞에서 오른쪽으로 가세요.", now }); break;
    case "park": c.send({ type: "gear", gear: "P", now }); break;
    case "sos":
      if ("speechSynthesis" in window) speechSynthesis.cancel();
      c.send({ type: "emergency", cause: "airbag", now });
      break;
    case "home": c.reset(); break;
    default: return;
  }
  render();
});

document.querySelector<HTMLFormElement>("#author")!.addEventListener("submit", (ev) => {
  ev.preventDefault();
  const f = new FormData(ev.target as HTMLFormElement);
  const get = (k: string) => String(f.get(k) ?? "");
  const draft = {
    title: get("title"),
    premise: get("premise"),
    persona: { name: get("name"), tone: get("tone") },
    chapters: [{ title: get("chapterTitle"), opening: get("opening"), beats: get("beats").split("\n").filter((l) => l.trim()).map((line) => ({ line })) }],
  };
  const result = parseScenarioDraft(draft, `custom-${Date.now()}`);
  const issues = document.querySelector<HTMLUListElement>("#author-issues")!;
  if (!result.ok) {
    issues.innerHTML = result.issues.map((i) => `<li>${esc(i)}</li>`).join("");
    return;
  }
  issues.innerHTML = "";
  store.putScenario(result.scenario);
  (ev.target as HTMLFormElement).reset();
  render();
});

setInterval(() => {
  if (c.view().drive?.phase !== "driving") return;
  c.elapse(FRAME_MS * SIM_MS_PER_REAL_MS);
  render();
}, FRAME_MS);

render();
