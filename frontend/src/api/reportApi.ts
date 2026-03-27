/**
 * Report API calls mapping to ReportController endpoints.
 * All report data comes from pre-computed derived read-model tables.
 */
import type { ApiClient } from "./client.js";

// ── Response types ──────────────────────────────────────────────────────────

export interface PlannedVsAchievedEntry {
  readonly teamId: string;
  readonly weekStart: string;
  readonly totalPlannedPoints: number;
  readonly totalAchievedPoints: number;
  readonly memberCount: number;
  readonly reconciledCount: number;
}

export interface CarryForwardReportEntry {
  readonly userId: string;
  readonly weekStart: string;
  readonly commitCount: number;
  readonly carryForwardCount: number;
  readonly carryForwardRate: number;
}

export interface ComplianceReportEntry {
  readonly userId: string;
  readonly weekStart: string;
  readonly lockOnTime: boolean;
  readonly lockLate: boolean;
  readonly autoLocked: boolean;
  readonly reconcileOnTime: boolean;
  readonly reconcileLate: boolean;
  readonly reconcileMissed: boolean;
}

export interface ChessDistributionReportEntry {
  readonly teamId: string;
  readonly weekStart: string;
  readonly distribution: Record<string, number>;
}

export interface ScopeChangeReportEntry {
  readonly userId: string;
  readonly weekStart: string;
  readonly scopeChangeCount: number;
}

export interface RcdoCoverageReportEntry {
  readonly rcdoNodeId: string;
  readonly weekStart: string;
  readonly plannedPoints: number;
  readonly achievedPoints: number;
  readonly teamContributionBreakdown: Record<string, number>;
}

export interface AiAcceptanceReportEntry {
  readonly totalSuggestions: number;
  readonly totalFeedbackGiven: number;
  readonly acceptedCount: number;
  readonly dismissedCount: number;
  readonly acceptanceRate: number;
}

export interface ExceptionAgingEntry {
  readonly exceptionId: string;
  readonly teamId: string;
  readonly userId: string;
  readonly exceptionType: string;
  readonly severity: string;
  readonly weekStartDate: string;
  readonly createdAt: string;
  readonly ageInHours: number;
}

// ── API factory ─────────────────────────────────────────────────────────────

function qs(params: Record<string, string>): string {
  return Object.entries(params)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join("&");
}

export function createReportApi(client: ApiClient) {
  return {
    getPlannedVsAchieved: (
      teamId: string,
      weekStart: string,
      weekEnd: string,
    ): Promise<PlannedVsAchievedEntry[]> =>
      client.get(
        `/reports/planned-vs-achieved?${qs({ teamId, weekStart, weekEnd })}`,
      ),

    getCarryForward: (
      teamId: string,
      weekStart: string,
      weekEnd: string,
    ): Promise<CarryForwardReportEntry[]> =>
      client.get(
        `/reports/carry-forward?${qs({ teamId, weekStart, weekEnd })}`,
      ),

    getCompliance: (
      teamId: string,
      weekStart: string,
      weekEnd: string,
    ): Promise<ComplianceReportEntry[]> =>
      client.get(
        `/reports/compliance?${qs({ teamId, weekStart, weekEnd })}`,
      ),

    getChessDistribution: (
      teamId: string,
      weekStart: string,
    ): Promise<ChessDistributionReportEntry> =>
      client.get(
        `/reports/chess-distribution?${qs({ teamId, weekStart })}`,
      ),

    getScopeChanges: (
      teamId: string,
      weekStart: string,
      weekEnd: string,
    ): Promise<ScopeChangeReportEntry[]> =>
      client.get(
        `/reports/scope-changes?${qs({ teamId, weekStart, weekEnd })}`,
      ),

    getAiAcceptance: (): Promise<AiAcceptanceReportEntry> =>
      client.get("/reports/ai-acceptance"),

    getExceptionAging: (teamId: string): Promise<ExceptionAgingEntry[]> =>
      client.get(`/reports/exception-aging?${qs({ teamId })}`),
  };
}

export type ReportApi = ReturnType<typeof createReportApi>;
