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
  };
}

export type AiApi = ReturnType<typeof createAiApi>;
