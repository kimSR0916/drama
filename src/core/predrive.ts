import { planDrive, type DrivePlan } from "./pacing";
import { isFinished, START, type SaveData, type Scenario } from "./scenario";

export type PreDriveState =
  | { kind: "selecting" }
  | { kind: "offerNew"; scenario: Scenario }
  | { kind: "askResume"; scenario: Scenario; save: SaveData }
  | { kind: "askRestart"; scenario: Scenario; save: SaveData }
  | { kind: "awaitingDestination"; scenario: Scenario; save: SaveData; resumed: boolean }
  | { kind: "ready"; scenario: Scenario; save: SaveData; resumed: boolean; etaMinutes: number; plan: DrivePlan };

export type PreDriveEvent =
  | { type: "selectStory"; scenario: Scenario; save?: SaveData }
  | { type: "startNew"; now: number }
  | { type: "answer"; yes: boolean; now: number }
  | { type: "setDestination"; etaMinutes: number };

export const INITIAL_PRE_DRIVE: PreDriveState = { kind: "selecting" };

export const PROMPTS = {
  askResume: "이어서 하시겠습니까?",
  askRestart: "처음부터 시작하시겠습니까?",
} as const;

function freshSave(scenario: Scenario, now: number): SaveData {
  return { scenarioId: scenario.id, cursor: START, flags: {}, updatedAt: now };
}

export function preDrive(state: PreDriveState, event: PreDriveEvent): PreDriveState {
  switch (event.type) {
    case "selectStory": {
      if (state.kind === "ready") return state;
      const { scenario, save } = event;
      const hasHistory = save !== undefined && save.scenarioId === scenario.id && !isFinished(scenario, save.cursor);
      return hasHistory ? { kind: "askResume", scenario, save } : { kind: "offerNew", scenario };
    }
    case "startNew":
      if (state.kind !== "offerNew") return state;
      return { kind: "awaitingDestination", scenario: state.scenario, save: freshSave(state.scenario, event.now), resumed: false };
    case "answer":
      if (state.kind === "askResume") {
        return event.yes
          ? { kind: "awaitingDestination", scenario: state.scenario, save: state.save, resumed: true }
          : { kind: "askRestart", scenario: state.scenario, save: state.save };
      }
      if (state.kind === "askRestart") {
        return event.yes
          ? { kind: "awaitingDestination", scenario: state.scenario, save: freshSave(state.scenario, event.now), resumed: false }
          : { kind: "askResume", scenario: state.scenario, save: state.save };
      }
      return state;
    case "setDestination": {
      if (state.kind !== "awaitingDestination" && state.kind !== "ready") return state;
      if (!(event.etaMinutes > 0)) return state;
      const plan = planDrive(state.scenario, state.save.cursor, event.etaMinutes);
      if (!plan) return state;
      return {
        kind: "ready",
        scenario: state.scenario,
        save: state.save,
        resumed: state.resumed,
        etaMinutes: event.etaMinutes,
        plan,
      };
    }
  }
}
