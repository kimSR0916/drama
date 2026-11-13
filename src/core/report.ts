import { beatsBefore, totalBeats, type Cursor, type Scenario } from "./scenario";

export type HarshKind = "accel" | "brake" | "turn";

export const HARSH_PENALTY = 5;
export const SAFE_DRIVER_SCORE = 95;

export interface RewardCard {
  kind: "chapter" | "safety";
  title: string;
}

export interface DriveReport {
  safetyScore: number;
  harsh: Record<HarshKind, number>;
  playedBeats: number;
  progressPercent: number;
  durationMinutes: number;
  reward: RewardCard | null;
}

export interface ReportInput {
  scenario: Scenario;
  before: Cursor;
  after: Cursor;
  playedBeats: number;
  harsh: Record<HarshKind, number>;
  startedAt: number;
  endedAt: number;
}

export function buildReport(input: ReportInput): DriveReport {
  const { scenario, before, after, harsh } = input;
  const harshTotal = harsh.accel + harsh.brake + harsh.turn;
  const safetyScore = Math.max(0, 100 - HARSH_PENALTY * harshTotal);
  const total = totalBeats(scenario);
  const progressPercent = total === 0 ? 100 : Math.round((beatsBefore(scenario, after) / total) * 100);
  const finishedChapter = after.chapter > before.chapter ? scenario.chapters[before.chapter] : undefined;
  const reward: RewardCard | null = finishedChapter
    ? { kind: "chapter", title: `${finishedChapter.title} 클리어` }
    : safetyScore >= SAFE_DRIVER_SCORE
      ? { kind: "safety", title: "무사고 드라이버" }
      : null;
  return {
    safetyScore,
    harsh,
    playedBeats: input.playedBeats,
    progressPercent,
    durationMinutes: Math.max(0, Math.round((input.endedAt - input.startedAt) / 60_000)),
    reward,
  };
}
