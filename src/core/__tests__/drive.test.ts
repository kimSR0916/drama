import { describe, expect, it } from "vitest";
import { drive, startDrive, type DriveEvent, type Effect, type Step } from "../drive";
import { INITIAL_PRE_DRIVE, preDrive } from "../predrive";
import { scenario } from "./fixture";

const MIN = 60_000;

function begin(etaMinutes: number) {
  const ready = [
    { type: "selectStory", scenario } as const,
    { type: "startNew", now: 0 } as const,
    { type: "setDestination", etaMinutes } as const,
  ].reduce(preDrive, INITIAL_PRE_DRIVE);
  if (ready.kind !== "ready") throw new Error("not ready");
  return startDrive(ready, 0);
}

function feed(start: Step, events: DriveEvent[]) {
  const effects: Effect[] = [...start.effects];
  let state = start.state;
  for (const e of events) {
    const step = drive(state, e);
    state = step.state;
    effects.push(...step.effects);
  }
  return { state, effects };
}

const spoken = (effects: Effect[]) => effects.flatMap((e) => (e.kind === "speak" ? [e.text] : []));

describe("drive loop", () => {
  it("locks the UI and opens the story on start", () => {
    expect(begin(30).effects).toEqual([
      { kind: "uxRestrictions", active: true },
      { kind: "bgm", on: true },
      { kind: "speak", speaker: "narrator", text: "첫 번째 의뢰가 들어왔어요." },
    ]);
  });

  it("spreads beats across the trip and asks for a choice", () => {
    const { state, effects } = feed(begin(41), [
      { type: "tick", now: 5 * MIN, etaMinutes: 36 },
      { type: "tick", now: 10 * MIN, etaMinutes: 31 },
    ]);
    expect(spoken(effects)).toContain("편지가 사라졌대요.");
    expect(effects).toContainEqual(expect.objectContaining({ kind: "showChoices" }));
    expect(state.phase === "driving" && state.trip.played).toBe(1);
  });

  it("reroutes and records the flag on a driver choice", () => {
    const { state, effects } = feed(begin(41), [
      { type: "tick", now: 10 * MIN, etaMinutes: 31 },
      { type: "driverInput", choiceId: "fast", via: "voice", now: 10 * MIN + 1 },
    ]);
    expect(effects).toContainEqual({ kind: "reroute", prefer: "detour" });
    expect(state.phase === "driving" && state.trip.flags).toEqual({ b1: "fast" });
  });

  it("falls back to the non-detour choice when the driver stays silent", () => {
    const { effects } = feed(begin(41), [
      { type: "tick", now: 10 * MIN, etaMinutes: 31 },
      { type: "tick", now: 10 * MIN + 31_000, etaMinutes: 30 },
    ]);
    expect(effects).toContainEqual({ kind: "reroute", prefer: "keep" });
  });

  it("reacts to harsh driving at most once per cooldown but counts every event", () => {
    const { state, effects } = feed(begin(30), [
      { type: "harshDriving", kind: "brake", now: 1000 },
      { type: "harshDriving", kind: "brake", now: 2000 },
      { type: "harshDriving", kind: "accel", now: 40_000 },
    ]);
    expect(spoken(effects).filter((t) => t.includes("천천히") || t.includes("서두르면"))).toHaveLength(2);
    expect(state.phase === "driving" && state.trip.harsh).toEqual({ accel: 1, brake: 2, turn: 0 });
  });

  it("uses congestion to pull the next beat forward", () => {
    const { effects } = feed(begin(30), [{ type: "congestion", now: 1000 }]);
    expect(spoken(effects)).toEqual(expect.arrayContaining(["막히는 동안 단서를 정리해 볼까요?", "편지가 사라졌대요."]));
  });

  it("voices route guidance through the character", () => {
    const { effects } = feed(begin(30), [{ type: "routeGuidance", instruction: "300m 앞 우회전", now: 1000 }]);
    expect(spoken(effects)).toContain("하루: 300m 앞 우회전");
  });

  it("smart-saves with a cliffhanger one minute before arrival, then reports on P", () => {
    const { state, effects } = feed(begin(8), [
      { type: "tick", now: 4 * MIN, etaMinutes: 4 },
      { type: "driverInput", choiceId: "safe", via: "button", now: 4 * MIN + 1 },
      { type: "tick", now: 7 * MIN, etaMinutes: 1 },
      { type: "gear", gear: "D", now: 7.5 * MIN },
      { type: "gear", gear: "P", now: 8 * MIN },
    ]);
    expect(spoken(effects)).toContain("다음에 계속.");
    expect(effects).toContainEqual({ kind: "save", data: { scenarioId: "s1", cursor: { chapter: 0, beat: 1 }, flags: { b1: "safe" }, updatedAt: 7 * MIN } });
    expect(effects.at(-2)).toEqual({ kind: "uxRestrictions", active: false });
    expect(state).toMatchObject({ phase: "parked", report: { safetyScore: 100, progressPercent: 25, playedBeats: 1, reward: { kind: "safety" } } });
  });

  it("keeps the screen locked after arrival until P", () => {
    const { state, effects } = feed(begin(8), [
      { type: "tick", now: 7 * MIN, etaMinutes: 1 },
      { type: "gear", gear: "D", now: 7.5 * MIN },
    ]);
    expect(state.phase).toBe("arrived");
    expect(effects).not.toContainEqual({ kind: "uxRestrictions", active: false });
  });

  it("awards a chapter card when the drive finishes a chapter", () => {
    const { state } = feed(begin(30), [
      { type: "congestion", now: 1 * MIN },
      { type: "driverInput", choiceId: "safe", via: "voice", now: 1 * MIN + 1 },
      { type: "congestion", now: 2 * MIN },
      { type: "congestion", now: 3 * MIN },
      { type: "gear", gear: "P", now: 4 * MIN },
    ]);
    expect(state).toMatchObject({ phase: "parked", report: { reward: { kind: "chapter", title: "1장 사라진 편지 클리어" }, progressPercent: 75 } });
  });

  it("drops everything for SOS on an airbag deployment and ignores later events", () => {
    const { state, effects } = feed(begin(30), [
      { type: "congestion", now: 1000 },
      { type: "emergency", cause: "airbag", now: 2000 },
      { type: "routeGuidance", instruction: "좌회전", now: 3000 },
      { type: "gear", gear: "P", now: 4000 },
    ]);
    expect(effects.slice(-3)).toEqual([
      { kind: "stopAllAudio" },
      { kind: "sos", cause: "airbag" },
      { kind: "save", data: { scenarioId: "s1", cursor: { chapter: 0, beat: 1 }, flags: {}, updatedAt: 2000 } },
    ]);
    expect(state).toEqual({ phase: "emergency", cause: "airbag" });
  });

  it("still counts harsh driving in the final minute after arrival", () => {
    const { state } = feed(begin(15), [
      { type: "tick", now: 14 * MIN, etaMinutes: 1 },
      { type: "harshDriving", kind: "brake", now: 14 * MIN + 10_000 },
      { type: "harshDriving", kind: "brake", now: 14 * MIN + 50_000 },
      { type: "gear", gear: "P", now: 15 * MIN },
    ]);
    expect(state).toMatchObject({ phase: "parked", report: { harsh: { brake: 2 }, safetyScore: 90 } });
  });

  it("never reroutes on its own when every option is a detour", () => {
    const allDetour = { ...scenario, chapters: [{ ...scenario.chapters[0]!, beats: [{ id: "d", line: "갈림길", choices: [
      { id: "l", label: "왼쪽", reply: "왼쪽!", routeHint: "detour" as const },
      { id: "r", label: "오른쪽", reply: "오른쪽!", routeHint: "detour" as const },
    ] }] }] };
    const ready = [{ type: "selectStory", scenario: allDetour } as const, { type: "startNew", now: 0 } as const, { type: "setDestination", etaMinutes: 30 } as const].reduce(preDrive, INITIAL_PRE_DRIVE);
    if (ready.kind !== "ready") throw new Error("not ready");
    const { effects } = feed(startDrive(ready, 0), [
      { type: "congestion", now: 1000 },
      { type: "tick", now: 31_001, etaMinutes: 29 },
    ]);
    expect(effects).toContainEqual({ kind: "hideChoices" });
    expect(effects.some((e) => e.kind === "reroute")).toBe(false);
  });
});
