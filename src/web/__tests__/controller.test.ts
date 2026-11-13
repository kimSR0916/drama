import { describe, expect, it } from "vitest";
import { BUILTIN_SCENARIOS } from "../../authoring/builtin";
import { accountStore, memoryKeyValue } from "../../authoring/store";
import { createController } from "../controller";

const MIN = 60_000;

function setup() {
  const store = accountStore(memoryKeyValue(), "tester");
  BUILTIN_SCENARIOS.forEach((s) => store.putScenario(s));
  return { store, c: createController(store) };
}

describe("simulator controller", () => {
  it("drives a short trip end to end and resumes from the saved cursor next time", () => {
    const { store, c } = setup();
    const id = "builtin-detective";
    c.selectStory(id);
    c.startNew();
    c.pre({ type: "setDestination", etaMinutes: 6 });
    c.startDriving();
    expect(c.view().uxLocked).toBe(true);
    for (let i = 0; i < 6; i++) c.elapse(MIN);
    expect(c.view().drive?.phase).toBe("arrived");
    c.send({ type: "gear", gear: "P", now: c.view().now });
    expect(c.view().uxLocked).toBe(false);
    expect(c.view().report?.playedBeats).toBe(1);
    expect(store.getSave(id)?.cursor).toEqual({ chapter: 0, beat: 1 });

    c.reset();
    c.selectStory(id);
    expect(c.view().pre.kind).toBe("askResume");
  });

  it("restart clears the stored save before driving", () => {
    const { store, c } = setup();
    const id = "builtin-radio";
    store.putSave({ scenarioId: id, cursor: { chapter: 0, beat: 2 }, flags: { x: "y" }, updatedAt: 1 });
    c.selectStory(id);
    c.answer(false);
    c.answer(true);
    expect(store.getSave(id)?.cursor).toEqual({ chapter: 0, beat: 0 });
    expect(store.getSave(id)?.flags).toEqual({});
  });

  it("congestion adds delay to the ETA and SOS stops BGM", () => {
    const { c } = setup();
    c.selectStory("builtin-starship");
    c.startNew();
    c.pre({ type: "setDestination", etaMinutes: 20 });
    c.startDriving();
    c.congestion();
    expect(c.view().etaMinutes).toBe(23);
    expect(c.view().bgm).toBe(true);
    c.send({ type: "emergency", cause: "collision", now: c.view().now });
    expect(c.view()).toMatchObject({ bgm: false, sos: "collision", choices: null });
  });
});
