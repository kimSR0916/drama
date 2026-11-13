import type { AccountStore } from "../authoring/store";
import { drive, startDrive, type DriveEvent, type DriveState, type EmergencyCause, type Effect, type Speaker } from "../core/drive";
import { INITIAL_PRE_DRIVE, preDrive, type PreDriveEvent, type PreDriveState } from "../core/predrive";
import type { DriveReport } from "../core/report";
import type { Choice } from "../core/scenario";

const MINUTE = 60_000;
export const CONGESTION_DELAY_MINUTES = 3;

export interface Line {
  speaker: Speaker | "system";
  text: string;
}

export interface View {
  pre: PreDriveState;
  drive: DriveState | null;
  transcript: Line[];
  choices: Choice[] | null;
  uxLocked: boolean;
  bgm: boolean;
  sos: EmergencyCause | null;
  report: DriveReport | null;
  now: number;
  etaMinutes: number;
}

const initialView = (): View => ({
  pre: INITIAL_PRE_DRIVE,
  drive: null,
  transcript: [],
  choices: null,
  uxLocked: false,
  bgm: false,
  sos: null,
  report: null,
  now: 0,
  etaMinutes: 0,
});

export function createController(store: AccountStore, speak: (line: Line) => void = () => {}) {
  let view = initialView();

  const log = (line: Line) => {
    view = { ...view, transcript: [...view.transcript, line] };
    speak(line);
  };

  const apply = (effects: Effect[]) => {
    for (const e of effects) {
      switch (e.kind) {
        case "speak":
          log({ speaker: e.speaker, text: e.text });
          break;
        case "bgm":
          view = { ...view, bgm: e.on };
          break;
        case "stopAllAudio":
          view = { ...view, bgm: false };
          break;
        case "sos":
          view = { ...view, sos: e.cause, choices: null };
          log({ speaker: "system", text: "긴급 상황 감지. 모든 연출을 멈추고 SOS로 전환합니다." });
          break;
        case "showChoices":
          view = { ...view, choices: e.choices };
          break;
        case "hideChoices":
          view = { ...view, choices: null };
          break;
        case "reroute":
          log({ speaker: "system", text: e.prefer === "detour" ? "경로를 우회 경로로 갱신했습니다." : "현재 경로를 유지합니다." });
          break;
        case "save":
          store.putSave(e.data);
          log({ speaker: "system", text: "스마트 세이브 완료." });
          break;
        case "uxRestrictions":
          view = { ...view, uxLocked: e.active };
          break;
        case "showReport":
          view = { ...view, report: e.report };
          break;
      }
    }
  };

  const send = (event: DriveEvent) => {
    if (!view.drive) return;
    const step = drive(view.drive, event);
    view = { ...view, drive: step.state };
    apply(step.effects);
  };

  return {
    view: () => view,
    pre(event: PreDriveEvent) {
      if (view.drive) return;
      view = { ...view, pre: preDrive(view.pre, event) };
    },
    selectStory(scenarioId: string) {
      const scenario = store.getScenario(scenarioId);
      if (!scenario) return;
      const save = store.getSave(scenarioId);
      this.pre({ type: "selectStory", scenario, ...(save ? { save } : {}) });
      if (view.pre.kind === "awaitingDestination") store.putSave(view.pre.save);
    },
    answer(yes: boolean) {
      this.pre({ type: "answer", yes, now: view.now });
      if (view.pre.kind === "awaitingDestination") store.putSave(view.pre.save);
    },
    startNew() {
      this.pre({ type: "startNew", now: view.now });
      if (view.pre.kind === "awaitingDestination") store.putSave(view.pre.save);
    },
    startDriving() {
      if (view.pre.kind !== "ready" || view.drive) return;
      const step = startDrive(view.pre, view.now);
      view = { ...view, drive: step.state, etaMinutes: view.pre.etaMinutes };
      apply(step.effects);
    },
    elapse(ms: number) {
      if (view.drive?.phase !== "driving") return;
      const now = view.now + ms;
      const etaMinutes = Math.max(0, view.etaMinutes - ms / MINUTE);
      view = { ...view, now, etaMinutes };
      send({ type: "tick", now, etaMinutes });
    },
    congestion() {
      if (view.drive?.phase !== "driving") return;
      view = { ...view, etaMinutes: view.etaMinutes + CONGESTION_DELAY_MINUTES };
      send({ type: "congestion", now: view.now });
    },
    send,
    reset() {
      view = initialView();
    },
  };
}

export type Controller = ReturnType<typeof createController>;
