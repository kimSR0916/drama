import { describe, expect, it } from "vitest";
import { planDrive } from "../../core/pacing";
import { START } from "../../core/scenario";
import { BUILTIN_SCENARIOS } from "../builtin";
import { parseScenarioDraft } from "../draft";
import { accountStore, memoryKeyValue } from "../store";

const minimal = {
  title: "출근길 판타지",
  premise: "운전자는 용을 타고 출근하는 기사다.",
  persona: { name: "루나", tone: "장난스러운 요정" },
  chapters: [{ title: "1장 출발", opening: "루나: 출발!", beats: [{ line: "루나: 앞에 성이 보여요." }] }],
};

describe("parseScenarioDraft", () => {
  it("builds a playable custom scenario with defaulted reactions and endings", () => {
    const r = parseScenarioDraft(minimal, "u1");
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    expect(r.scenario.source).toBe("custom");
    expect(r.scenario.persona.reactions.harshBrake).toContain("루나");
    expect(planDrive(r.scenario, START, 5)?.ending).toContain("루나");
  });

  it("reports every missing field in Korean instead of throwing", () => {
    const r = parseScenarioDraft({ chapters: [{ beats: [] }] }, "u1");
    expect(r.ok).toBe(false);
    if (r.ok) return;
    expect(r.issues).toEqual(expect.arrayContaining(["제목을(를) 입력해 주세요.", "1장에 장면을 하나 이상 넣어 주세요."]));
  });

  it("rejects a single dangling choice and maps detour to a route hint", () => {
    const one = parseScenarioDraft({ ...minimal, chapters: [{ ...minimal.chapters[0], beats: [{ line: "갈림길", choices: [{ label: "왼쪽", reply: "왼쪽!" }] }] }] }, "u1");
    expect(one.ok).toBe(false);
    const two = parseScenarioDraft({ ...minimal, chapters: [{ ...minimal.chapters[0], beats: [{ line: "갈림길", choices: [{ label: "왼쪽", reply: "왼쪽!", detour: true }, { label: "오른쪽", reply: "오른쪽!" }] }] }] }, "u1");
    expect(two.ok && two.scenario.chapters[0]!.beats[0]!.choices!.map((c) => c.routeHint)).toEqual(["detour", "keep"]);
  });

  it("survives non-object input", () => {
    expect(parseScenarioDraft(null, "x").ok).toBe(false);
    expect(parseScenarioDraft("hello", "x").ok).toBe(false);
  });
});

describe("builtin themes", () => {
  it("ships at least three valid themes with unique ids", () => {
    expect(BUILTIN_SCENARIOS.length).toBeGreaterThanOrEqual(3);
    expect(new Set(BUILTIN_SCENARIOS.map((s) => s.id)).size).toBe(BUILTIN_SCENARIOS.length);
    expect(BUILTIN_SCENARIOS.every((s) => s.source === "builtin")).toBe(true);
  });
});

describe("accountStore", () => {
  it("keeps scenarios and saves per account and deletes the save with its scenario", () => {
    const kv = memoryKeyValue();
    const a = accountStore(kv, "alice");
    const b = accountStore(kv, "bob");
    const s = BUILTIN_SCENARIOS[0]!;
    a.putScenario(s);
    a.putSave({ scenarioId: s.id, cursor: { chapter: 0, beat: 1 }, flags: {}, updatedAt: 1 });
    expect(a.listScenarios().map((x) => x.id)).toEqual([s.id]);
    expect(b.listScenarios()).toEqual([]);
    expect(b.getSave(s.id)).toBeUndefined();
    a.deleteScenario(s.id);
    expect(a.getSave(s.id)).toBeUndefined();
  });

  it("treats corrupt stored JSON as missing", () => {
    const kv = memoryKeyValue();
    kv.set("alice/save/x", "{oops");
    expect(accountStore(kv, "alice").getSave("x")).toBeUndefined();
  });
});
