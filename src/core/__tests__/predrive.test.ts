import { describe, expect, it } from "vitest";
import { INITIAL_PRE_DRIVE, preDrive, type PreDriveEvent, type PreDriveState } from "../predrive";
import type { SaveData } from "../scenario";
import { scenario } from "./fixture";

const run = (events: PreDriveEvent[], from: PreDriveState = INITIAL_PRE_DRIVE) => events.reduce(preDrive, from);
const midSave: SaveData = { scenarioId: "s1", cursor: { chapter: 0, beat: 1 }, flags: { b1: "fast" }, updatedAt: 1 };

describe("preDrive", () => {
  it("offers a new start when there is no history", () => {
    const s = run([{ type: "selectStory", scenario }, { type: "startNew", now: 5 }, { type: "setDestination", etaMinutes: 25 }]);
    expect(s.kind).toBe("ready");
    if (s.kind !== "ready") return;
    expect(s.save.cursor).toEqual({ chapter: 0, beat: 0 });
    expect(s.plan.mode).toBe("full");
  });

  it("resumes the saved cursor when the driver says yes", () => {
    const s = run([{ type: "selectStory", scenario, save: midSave }, { type: "answer", yes: true, now: 5 }, { type: "setDestination", etaMinutes: 25 }]);
    expect(s.kind === "ready" && s.save).toBe(midSave);
    expect(s.kind === "ready" && s.plan.beats.map((b) => b.id)).toEqual(["b2", "b3"]);
  });

  it("restarts with cleared data after no then yes", () => {
    const s = run([{ type: "selectStory", scenario, save: midSave }, { type: "answer", yes: false, now: 5 }, { type: "answer", yes: true, now: 7 }]);
    expect(s).toMatchObject({ kind: "awaitingDestination", resumed: false, save: { cursor: { chapter: 0, beat: 0 }, flags: {}, updatedAt: 7 } });
  });

  it("returns to story select when the driver declines both", () => {
    const s = run([{ type: "selectStory", scenario, save: midSave }, { type: "answer", yes: false, now: 5 }, { type: "answer", yes: false, now: 6 }]);
    expect(s.kind).toBe("selecting");
  });

  it("treats a finished or foreign save as no history", () => {
    const done: SaveData = { ...midSave, cursor: { chapter: 2, beat: 0 } };
    expect(run([{ type: "selectStory", scenario, save: done }]).kind).toBe("offerNew");
    expect(run([{ type: "selectStory", scenario, save: { ...midSave, scenarioId: "other" } }]).kind).toBe("offerNew");
    expect(run([{ type: "selectStory", scenario, save: { ...midSave, cursor: { chapter: 0, beat: 9 } } }]).kind).toBe("offerNew");
  });

  it("rejects a non-positive ETA", () => {
    const s = run([{ type: "selectStory", scenario }, { type: "startNew", now: 5 }, { type: "setDestination", etaMinutes: 0 }]);
    expect(s.kind).toBe("awaitingDestination");
  });
});
