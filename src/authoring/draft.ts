import type { Chapter, Persona, Scenario } from "../core/scenario";

export interface ScenarioDraft {
  title: string;
  premise: string;
  persona: { name: string; tone: string; reactions?: Partial<Persona["reactions"]> };
  chapters: { title: string; opening: string; beats: { line: string; choices?: { label: string; reply: string; detour?: boolean }[] }[]; cliffhanger?: string; finale?: string }[];
}

export type DraftResult = { ok: true; scenario: Scenario } | { ok: false; issues: string[] };

export const LIMITS = { title: 40, line: 120, chapters: 20, beatsPerChapter: 12, choices: 3 } as const;

const text = (v: unknown): string => (typeof v === "string" ? v.trim() : "");

function defaultReactions(name: string): Persona["reactions"] {
  return {
    harshAccel: `${name}: 조금만 천천히 가요. 이야기는 도망가지 않아요.`,
    harshBrake: `${name}: 깜짝이야. 괜찮아요?`,
    harshTurn: `${name}: 커브는 부드럽게 부탁해요.`,
    congestion: `${name}: 길이 막히네요. 그동안 이야기를 조금 더 해볼게요.`,
  };
}

export function parseScenarioDraft(input: unknown, id: string): DraftResult {
  const issues: string[] = [];
  const d = (input ?? {}) as Partial<ScenarioDraft>;
  const need = (value: string, label: string, max: number = LIMITS.line) => {
    if (!value) issues.push(`${label}을(를) 입력해 주세요.`);
    else if (value.length > max) issues.push(`${label}은(는) ${max}자 이하로 써 주세요.`);
    return value;
  };

  const title = need(text(d.title), "제목", LIMITS.title);
  const premise = need(text(d.premise), "세계관 설명", 500);
  const name = need(text(d.persona?.name), "캐릭터 이름", LIMITS.title);
  const tone = need(text(d.persona?.tone), "캐릭터 말투", LIMITS.title);
  const custom = d.persona?.reactions ?? {};
  const fallback = defaultReactions(name);
  const reactions: Persona["reactions"] = {
    harshAccel: text(custom.harshAccel) || fallback.harshAccel,
    harshBrake: text(custom.harshBrake) || fallback.harshBrake,
    harshTurn: text(custom.harshTurn) || fallback.harshTurn,
    congestion: text(custom.congestion) || fallback.congestion,
  };

  const rawChapters = Array.isArray(d.chapters) ? d.chapters : [];
  if (rawChapters.length === 0) issues.push("챕터를 하나 이상 만들어 주세요.");
  if (rawChapters.length > LIMITS.chapters) issues.push(`챕터는 ${LIMITS.chapters}개까지 만들 수 있어요.`);

  const chapters: Chapter[] = rawChapters.slice(0, LIMITS.chapters).map((c, ci) => {
    const at = `${ci + 1}장`;
    const rawBeats = Array.isArray(c?.beats) ? c.beats : [];
    if (rawBeats.length === 0) issues.push(`${at}에 장면을 하나 이상 넣어 주세요.`);
    if (rawBeats.length > LIMITS.beatsPerChapter) issues.push(`${at}의 장면은 ${LIMITS.beatsPerChapter}개까지예요.`);
    const chapterTitle = need(text(c?.title), `${at} 제목`, LIMITS.title);
    return {
      id: `c${ci + 1}`,
      title: chapterTitle,
      opening: need(text(c?.opening), `${at} 도입부`),
      beats: rawBeats.slice(0, LIMITS.beatsPerChapter).map((b, bi) => {
        const beatId = `c${ci + 1}b${bi + 1}`;
        const rawChoices = Array.isArray(b?.choices) ? b.choices : [];
        if (rawChoices.length === 1) issues.push(`${at} ${bi + 1}번째 장면의 선택지는 두 개 이상이어야 해요.`);
        if (rawChoices.length > LIMITS.choices) issues.push(`${at} ${bi + 1}번째 장면의 선택지는 ${LIMITS.choices}개까지예요.`);
        const choices = rawChoices.slice(0, LIMITS.choices).map((ch, k) => ({
          id: `${beatId}o${k + 1}`,
          label: need(text(ch?.label), `${at} 선택지 이름`, 12),
          reply: need(text(ch?.reply), `${at} 선택지 반응`),
          routeHint: ch?.detour ? ("detour" as const) : ("keep" as const),
        }));
        return { id: beatId, line: need(text(b?.line), `${at} ${bi + 1}번째 장면`), ...(choices.length > 1 ? { choices } : {}) };
      }),
      cliffhanger: text(c?.cliffhanger) || `${name}: 오늘은 여기까지. 다음 운전에서 이어갈게요.`,
      finale: text(c?.finale) || `${name}: ${chapterTitle}, 여기서 마무리예요.`,
    };
  });

  if (issues.length > 0) return { ok: false, issues };
  return { ok: true, scenario: { id, source: "custom", title, premise, persona: { name, tone, reactions }, chapters } };
}
