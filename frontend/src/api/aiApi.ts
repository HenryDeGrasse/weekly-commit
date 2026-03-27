/**
 * AI API client functions for all AI-powered endpoints.
 */
import type { ApiClient } from "./client.js";

// ── Types ─────────────────────────────────────────────────────────────────────

export interface AiStatusResponse {
  aiEnabled: boolean;
  providerName: string;
  providerVersion: string;
  available: boolean;
}

export interface CommitDraftAssistRequest {
  userId: string;
  planId: string;
  commitId?: string | undefined;
  currentTitle: string;
  currentDescription?: string | undefined;
  currentSuccessCriteria?: string | undefined;
  currentEstimatePoints?: number | undefined;
  chessPiece?: string | undefined;
}

export interface CommitDraftAssistResponse {
  aiAvailable: boolean;
  suggestionId?: string;
  suggestedTitle?: string | null;
  suggestedDescription?: string | null;
  suggestedSuccessCriteria?: string | null;
  suggestedEstimatePoints?: number | null;
  /** Suggested chess piece. Non-null only when chessPiece was absent in the request. */
  suggestedChessPiece?: string | null;
  rationale?: string;
}

export interface CommitLintRequest {
  userId: string;
  planId: string;
}

export interface LintMessage {
  code: string;
  message: string;
  commitId?: string;
}

export interface CommitLintResponse {
  aiAvailable: boolean;
  suggestionId?: string;
  hardValidation: LintMessage[];
  softGuidance: LintMessage[];
  rationale?: string;
}

export interface RiskSignal {
  id: string;
  signalType: string;
  rationale: string;
  planId: string;
  commitId?: string | null;
  createdAt: string;
}

export interface PlanRiskSignalsResponse {
  aiAvailable: boolean;
  planId: string;
  signals: RiskSignal[];
}

// ── Reconcile Assist types ─────────────────────────────────────────────────

export interface ReconcileAssistRequest {
  planId: string;
  userId: string;
}

export interface CommitOutcomeSuggestion {
  commitId: string;
  commitTitle: string;
  suggestedOutcome: string;
  rationale: string;
}

export interface CarryForwardRecommendation {
  commitId: string;
  commitTitle: string;
  rationale: string;
}

export interface ReconcileAssistResponse {
  aiAvailable: boolean;
  suggestionId?: string;
  likelyOutcomes: CommitOutcomeSuggestion[];
  draftSummary?: string | null;
  carryForwardRecommendations: CarryForwardRecommendation[];
}

export type FeedbackAction = "ACCEPTED" | "DISMISSED" | "EDITED";

export interface AiFeedbackRequest {
  suggestionId: string;
  userId: string;
  action: FeedbackAction;
  notes?: string;
}

// ── Manager AI Summary types ──────────────────────────────────────────────────

export interface ManagerAiSummaryResponse {
  aiAvailable: boolean;
  /** Stored suggestion id for feedback (null when unavailable). */
  suggestionId?: string | null;
  teamId: string;
  weekStart: string;
  /** Prose summary of the team week. Null when aiAvailable=false. */
  summaryText?: string | null;
  /** RCDO branch titles with highest planned commitment this week. */
  topRcdoBranches: string[];
  /** Unresolved exception ids (cited objects). */
  unresolvedExceptionIds: string[];
  /** Textual carry-forward patterns (cited from plan data). */
  carryForwardPatterns: string[];
  /** Work item ids for King/Queen commits currently BLOCKED (cited objects). */
  criticalBlockedItemIds: string[];
  modelVersion?: string | null;
}

// ── API factory ───────────────────────────────────────────────────────────────

export function createAiApi(client: ApiClient, actorUserId: string) {
  const actorHeader = { "X-Actor-User-Id": actorUserId };

  return {
    /** GET /api/ai/status — provider health/availability check. */
    getStatus: (): Promise<AiStatusResponse> =>
      client.get("/ai/status"),

    /** POST /api/ai/commit-draft-assist — get draft improvement suggestions. */
    commitDraftAssist: (
      request: CommitDraftAssistRequest,
    ): Promise<CommitDraftAssistResponse> =>
      client.post("/ai/commit-draft-assist", request),

    /** POST /api/ai/commit-lint — run commit quality lint on a plan. */
    commitLint: (request: CommitLintRequest): Promise<CommitLintResponse> =>
      client.post("/ai/commit-lint", request),

    /** GET /api/plans/{id}/risk-signals — risk signals for a plan. */
    getRiskSignals: (planId: string): Promise<PlanRiskSignalsResponse> =>
      client.get(`/plans/${encodeURIComponent(planId)}/risk-signals`, {
        headers: actorHeader,
      }),

    /** POST /api/ai/reconcile-assist — get reconciliation suggestions. */
    reconcileAssist: (
      request: ReconcileAssistRequest,
    ): Promise<ReconcileAssistResponse> =>
      client.post("/ai/reconcile-assist", request),

    /** POST /api/ai/feedback — record thumbs up/down on a suggestion. */
    recordFeedback: (request: AiFeedbackRequest): Promise<void> =>
      client.post("/ai/feedback", request),

    /**
     * GET /api/teams/{id}/week/{weekStart}/ai-summary —
     * AI-generated manager team summary for the given team and week.
     *
     * @param teamId    team UUID string
     * @param weekStart ISO date string (YYYY-MM-DD) for the Monday of the week
     */
    getTeamAiSummary: (
      teamId: string,
      weekStart: string,
    ): Promise<ManagerAiSummaryResponse> =>
      client.get(
        `/teams/${encodeURIComponent(teamId)}/week/${encodeURIComponent(weekStart)}/ai-summary`,
        { headers: actorHeader },
      ),
  };
}

export type AiApi = ReturnType<typeof createAiApi>;
