import type { DrivePlan } from "./pacing";
import type { PreDriveState } from "./predrive";
import { buildReport, type DriveReport, type HarshKind } from "./report";
import { advance, type Choice, type RouteHint, type SaveData, type Scenario } from "./scenario";

export const ARRIVAL_LEAD_MINUTES = 1;
export const REACTION_COOLDOWN_MS = 30_000;
export const CHOICE_TIMEOUT_MS = 20_000;
const MINUTE = 60_000;

export type Gear = "P" | "R" | "N" | "D";
export type EmergencyCause = "airbag" | "collision";
export type Speaker = "narrator" | "character";

export type DriveEvent =
  | { type: "tick"; now: number; etaMinutes: number }
  | { type: "harshDriving"; kind: HarshKind; now: number }
  | { type: "congestion"; now: number }
  | { type: "routeGuidance"; instruction: string; now: number }
  | { type: "driverInput"; choiceId: string; via: "voice" | "button"; now: number }
  | { type: "emergency"; cause: EmergencyCause; now: number }
  | { type: "gear"; gear: Gear; now: number };

export type Effect =
  | { kind: "speak"; speaker: Speaker; text: string }
  | { kind: "bgm"; on: boolean }
  | { kind: "stopAllAudio" }
  | { kind: "sos"; cause: EmergencyCause }
  | { kind: "showChoices"; choices: Choice[] }
  | { kind: "hideChoices" }
  | { kind: "reroute"; prefer: RouteHint }
  | { kind: "save"; data: SaveData }
  | { kind: "uxRestrictions"; active: boolean }
  | { kind: "showReport"; report: DriveReport };

interface PendingChoice {
  beatId: string;
  choices: Choice[];
  deadline: number;
}

interface Trip {
  scenario: Scenario;
  plan: DrivePlan;
  save: SaveData;
  startedAt: number;
  arrivalAt: number;
  played: number;
  lastBeatAt: number;
  lastReactionAt: number;
  harsh: Record<HarshKind, number>;
  flags: Record<string, string>;
  pending: PendingChoice | null;
}

export type DriveState =
  | { phase: "driving"; trip: Trip }
  | { phase: "arrived"; trip: Trip; save: SaveData }
  | { phase: "parked"; report: DriveReport }
  | { phase: "emergency"; cause: EmergencyCause };

export interface Step {
  state: DriveState;
  effects: Effect[];
}

type Ready = Extract<PreDriveState, { kind: "ready" }>;

export function startDrive(ready: Ready, now: number): Step {
  const trip: Trip = {
    scenario: ready.scenario,
    plan: ready.plan,
    save: ready.save,
    startedAt: now,
    arrivalAt: now + ready.etaMinutes * MINUTE,
    played: 0,
    lastBeatAt: now,
    lastReactionAt: -Infinity,
    harsh: { accel: 0, brake: 0, turn: 0 },
    flags: { ...ready.save.flags },
    pending: null,
  };
  return {
    state: { phase: "driving", trip },
    effects: [
      { kind: "uxRestrictions", active: true },
      { kind: "bgm", on: true },
      { kind: "speak", speaker: "narrator", text: ready.plan.opening },
    ],
  };
}

const say = (speaker: Speaker, text: string): Effect => ({ kind: "speak", speaker, text });

function playNextBeat(trip: Trip, now: number): Step {
  const beat = trip.plan.beats[trip.played];
  if (!beat) return { state: { phase: "driving", trip }, effects: [] };
  const next: Trip = { ...trip, played: trip.played + 1, lastBeatAt: now };
  const effects: Effect[] = [say("character", beat.line)];
  if (beat.choices && beat.choices.length > 0) {
    next.pending = { beatId: beat.id, choices: beat.choices, deadline: now + CHOICE_TIMEOUT_MS };
    effects.push({ kind: "showChoices", choices: beat.choices });
  }
  return { state: { phase: "driving", trip: next }, effects };
}

function resolveChoice(trip: Trip, choice: Choice): Step {
  const pending = trip.pending!;
  const effects: Effect[] = [{ kind: "hideChoices" }, say("character", choice.reply)];
  if (choice.routeHint) effects.push({ kind: "reroute", prefer: choice.routeHint });
  return {
    state: { phase: "driving", trip: { ...trip, pending: null, flags: { ...trip.flags, [pending.beatId]: choice.id } } },
    effects,
  };
}

function safeDefault(choices: Choice[]): Choice {
  return choices.find((c) => c.routeHint !== "detour") ?? choices[0]!;
}

function beatDue(trip: Trip, now: number): boolean {
  const remainingBeats = trip.plan.beats.length - trip.played;
  if (remainingBeats <= 0 || trip.pending) return false;
  const storyWindow = trip.arrivalAt - ARRIVAL_LEAD_MINUTES * MINUTE - trip.lastBeatAt;
  return now - trip.lastBeatAt >= storyWindow / (remainingBeats + 1);
}

function arrive(trip: Trip, now: number): Step {
  const effects: Effect[] = [];
  let settled = trip;
  if (trip.pending) {
    const step = resolveChoice(trip, safeDefault(trip.pending.choices));
    effects.push(...step.effects);
    if (step.state.phase === "driving") settled = step.state.trip;
  }
  const playedAll = settled.played === settled.plan.beats.length;
  const chapter = settled.scenario.chapters[settled.save.cursor.chapter];
  const ending = playedAll ? settled.plan.ending : (chapter?.cliffhanger ?? settled.plan.ending);
  const save: SaveData = {
    scenarioId: settled.scenario.id,
    cursor: advance(settled.scenario, settled.save.cursor, settled.played),
    flags: settled.flags,
    updatedAt: now,
  };
  effects.push(say("character", ending), { kind: "bgm", on: false }, { kind: "save", data: save });
  return { state: { phase: "arrived", trip: settled, save }, effects };
}

function park(trip: Trip, save: SaveData, now: number): Step {
  const report = buildReport({
    scenario: trip.scenario,
    before: trip.save.cursor,
    after: save.cursor,
    playedBeats: trip.played,
    harsh: trip.harsh,
    startedAt: trip.startedAt,
    endedAt: now,
  });
  return {
    state: { phase: "parked", report },
    effects: [
      { kind: "uxRestrictions", active: false },
      { kind: "showReport", report },
    ],
  };
}

function driving(trip: Trip, event: DriveEvent): Step {
  const stay = (t: Trip, effects: Effect[] = []): Step => ({ state: { phase: "driving", trip: t }, effects });
  switch (event.type) {
    case "tick": {
      const t = { ...trip, arrivalAt: event.now + event.etaMinutes * MINUTE };
      if (event.etaMinutes <= ARRIVAL_LEAD_MINUTES) return arrive(t, event.now);
      if (t.pending && event.now >= t.pending.deadline) return resolveChoice(t, safeDefault(t.pending.choices));
      return beatDue(t, event.now) ? playNextBeat(t, event.now) : stay(t);
    }
    case "harshDriving": {
      const t = { ...trip, harsh: { ...trip.harsh, [event.kind]: trip.harsh[event.kind] + 1 } };
      if (event.now - trip.lastReactionAt < REACTION_COOLDOWN_MS) return stay(t);
      const { reactions } = trip.scenario.persona;
      const line = { accel: reactions.harshAccel, brake: reactions.harshBrake, turn: reactions.harshTurn }[event.kind];
      return stay({ ...t, lastReactionAt: event.now }, [say("character", line)]);
    }
    case "congestion": {
      const reaction = say("character", trip.scenario.persona.reactions.congestion);
      if (trip.pending) return stay(trip, [reaction]);
      const step = playNextBeat(trip, event.now);
      return { state: step.state, effects: [reaction, ...step.effects] };
    }
    case "routeGuidance":
      return stay(trip, [say("character", `${trip.scenario.persona.name}: ${event.instruction}`)]);
    case "driverInput": {
      const choice = trip.pending?.choices.find((c) => c.id === event.choiceId);
      return choice ? resolveChoice(trip, choice) : stay(trip);
    }
    case "gear": {
      if (event.gear !== "P") return stay(trip);
      const arrived = arrive(trip, event.now);
      if (arrived.state.phase !== "arrived") return arrived;
      const parked = park(arrived.state.trip, arrived.state.save, event.now);
      return { state: parked.state, effects: [...arrived.effects, ...parked.effects] };
    }
    case "emergency":
      return emergency(event.cause);
  }
}

function emergency(cause: EmergencyCause): Step {
  return { state: { phase: "emergency", cause }, effects: [{ kind: "stopAllAudio" }, { kind: "sos", cause }] };
}

export function drive(state: DriveState, event: DriveEvent): Step {
  if (state.phase === "emergency") return { state, effects: [] };
  if (event.type === "emergency") return emergency(event.cause);
  switch (state.phase) {
    case "driving":
      return driving(state.trip, event);
    case "arrived":
      return event.type === "gear" && event.gear === "P" ? park(state.trip, state.save, event.now) : { state, effects: [] };
    case "parked":
      return { state, effects: [] };
  }
}
