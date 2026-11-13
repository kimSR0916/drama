import type { Beat, Cursor, Scenario } from "./scenario";

export const SHORT_TRIP_MINUTES = 10;

export type PacingMode = "short" | "full";

export interface DrivePlan {
  mode: PacingMode;
  opening: string;
  beats: Beat[];
  ending: string;
  completesChapter: boolean;
}

export function pacingMode(etaMinutes: number): PacingMode {
  return etaMinutes < SHORT_TRIP_MINUTES ? "short" : "full";
}

export function planDrive(scenario: Scenario, cursor: Cursor, etaMinutes: number): DrivePlan | null {
  const chapter = scenario.chapters[cursor.chapter];
  if (!chapter) return null;
  const mode = pacingMode(etaMinutes);
  const remaining = chapter.beats.slice(cursor.beat);
  const beats = mode === "short" ? remaining.slice(0, 1) : remaining;
  const completesChapter = beats.length === remaining.length;
  return {
    mode,
    opening: cursor.beat === 0 ? chapter.opening : `지난 이야기에 이어서, ${chapter.title}.`,
    beats,
    ending: completesChapter ? chapter.finale : chapter.cliffhanger,
    completesChapter,
  };
}
