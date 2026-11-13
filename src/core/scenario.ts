export type ScenarioSource = "custom" | "builtin";

export interface Persona {
  name: string;
  tone: string;
  reactions: {
    harshAccel: string;
    harshBrake: string;
    harshTurn: string;
    congestion: string;
  };
}

export type RouteHint = "detour" | "keep";

export interface Choice {
  id: string;
  label: string;
  reply: string;
  routeHint?: RouteHint;
}

export interface Beat {
  id: string;
  line: string;
  choices?: Choice[];
}

export interface Chapter {
  id: string;
  title: string;
  opening: string;
  beats: Beat[];
  cliffhanger: string;
  finale: string;
}

export interface Scenario {
  id: string;
  source: ScenarioSource;
  title: string;
  premise: string;
  persona: Persona;
  chapters: Chapter[];
}

export interface Cursor {
  chapter: number;
  beat: number;
}

export interface SaveData {
  scenarioId: string;
  cursor: Cursor;
  flags: Record<string, string>;
  updatedAt: number;
}

export const START: Cursor = { chapter: 0, beat: 0 };

export function totalBeats(scenario: Scenario): number {
  return scenario.chapters.reduce((sum, c) => sum + c.beats.length, 0);
}

export function beatsBefore(scenario: Scenario, cursor: Cursor): number {
  let n = 0;
  for (let i = 0; i < cursor.chapter && i < scenario.chapters.length; i++) {
    n += scenario.chapters[i]!.beats.length;
  }
  return n + cursor.beat;
}

export function isFinished(scenario: Scenario, cursor: Cursor): boolean {
  return cursor.chapter >= scenario.chapters.length;
}

export function advance(scenario: Scenario, cursor: Cursor, played: number): Cursor {
  const chapter = scenario.chapters[cursor.chapter];
  if (!chapter) return cursor;
  const beat = cursor.beat + played;
  return beat >= chapter.beats.length ? { chapter: cursor.chapter + 1, beat: 0 } : { chapter: cursor.chapter, beat };
}
