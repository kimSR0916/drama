import { describe, expect, it } from "vitest";
import { planDrive } from "../pacing";
import { advance, START } from "../scenario";
import { scenario } from "./fixture";

describe("planDrive", () => {
  it("compresses a short trip to one event and a cliffhanger", () => {
    const plan = planDrive(scenario, START, 9)!;
    expect(plan.mode).toBe("short");
    expect(plan.beats.map((b) => b.id)).toEqual(["b1"]);
    expect(plan.ending).toBe("다음에 계속.");
    expect(plan.completesChapter).toBe(false);
  });

  it("plays the whole remaining chapter on a trip of ten minutes or more", () => {
    const plan = planDrive(scenario, START, 10)!;
    expect(plan.mode).toBe("full");
    expect(plan.beats.map((b) => b.id)).toEqual(["b1", "b2", "b3"]);
    expect(plan.ending).toBe("1장 해결!");
  });

  it("recaps instead of reopening when resuming mid-chapter", () => {
    const plan = planDrive(scenario, { chapter: 0, beat: 2 }, 5)!;
    expect(plan.opening).toContain("이어서");
    expect(plan.completesChapter).toBe(true);
  });

  it("returns null once the story is finished", () => {
    expect(planDrive(scenario, { chapter: 2, beat: 0 }, 30)).toBeNull();
  });

  it("advance rolls into the next chapter when a chapter completes", () => {
    expect(advance(scenario, START, 1)).toEqual({ chapter: 0, beat: 1 });
    expect(advance(scenario, START, 3)).toEqual({ chapter: 1, beat: 0 });
  });
});
