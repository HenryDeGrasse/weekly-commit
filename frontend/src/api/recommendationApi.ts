/**
 * Plan recommendation API client functions.
 * Covers GET /api/ai/plans/{planId}/recommendations (read-only, stable IDs)
 * and POST /api/ai/plans/{planId}/recommendations/refresh (re-generation).
 */
import type { ApiClient } from "./client.js";
import type { WhatIfResponse } from "./whatIfApi.js";

// ── Types ─────────────────────────────────────────────────────────────────────

/**
 * A single plan recommendation returned by the backend.
 * Matches the backend {@code PlanRecommendationResponse} record.
 */
export interface PlanRecommendation {
  /**
   * Stable suggestion row UUID. Used for:
   *  - AiFeedbackButtons (feedback POST endpoint)
   *  - Per-recommendation dismissal tracking in localStorage
   */
  suggestionId: string;
  /** Risk signal type that triggered the recommendation, e.g. "OVERCOMMIT". */
  riskType: string;
  /** Human-readable description of the detected risk. */
  description: string;
  /** Suggested action to address the risk. */
  suggestedAction: string;
  /**
   * What-if simulation result. {@code null} when simulation failed or was
   * not applicable (e.g. BLOCKED_CRITICAL / SCOPE_VOLATILITY signals).
   * The frontend must handle null gracefully.
   */
  whatIfResult: WhatIfResponse | null;
  /** Combined narrative from the risk signal and optional what-if result. */
  narrative: string;
  /**
   * Confidence tier name: "HIGH" | "MEDIUM" | "LOW" | "INSUFFICIENT".
   */
  confidence: string;
  /** {@code true} when this is a fully populated response. */
  available: boolean;
}

// ── API factory ───────────────────────────────────────────────────────────────

export function createRecommendationApi(client: ApiClient, actorUserId: string) {
  const actorHeader = { "X-Actor-User-Id": actorUserId };

  return {
    /**
     * GET /api/ai/plans/{planId}/recommendations
     *
     * Reads persisted recommendations — does not trigger re-generation.
     * Suggestion IDs are stable across page reloads.
     */
    getRecommendations: (planId: string): Promise<PlanRecommendation[]> =>
      client.get(`/ai/plans/${encodeURIComponent(planId)}/recommendations`, {
        headers: actorHeader,
      }),

    /**
     * POST /api/ai/plans/{planId}/recommendations/refresh
     *
     * Triggers re-generation: deletes stale rows, runs the
     * risk → what-if → recommendation pipeline, persists and returns results.
     * Call when the plan changes or the user requests updated recommendations.
     */
    refreshRecommendations: (planId: string): Promise<PlanRecommendation[]> =>
      client.post(
        `/ai/plans/${encodeURIComponent(planId)}/recommendations/refresh`,
        {},
        { headers: actorHeader },
      ),
  };
}

export type RecommendationApi = ReturnType<typeof createRecommendationApi>;
