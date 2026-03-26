/**
 * RAG (Retrieval-Augmented Generation) API client functions.
 * Covers the semantic query endpoint and AI insight retrieval endpoints.
 */
import type { ApiClient } from "./client.js";

// ── Types ─────────────────────────────────────────────────────────────────────

export interface RagQueryRequest {
  question: string;
  teamId?: string;
  userId?: string;
}

export interface RagSource {
  entityType: string;
  entityId: string;
  weekStartDate?: string;
  snippet?: string;
}

export interface RagQueryResponse {
  aiAvailable: boolean;
  answer?: string;
  sources?: RagSource[];
  confidence?: number;
  suggestionId?: string;
}

export interface InsightCard {
  suggestionId: string;
  insightText: string;
  severity: string;
  sourceEntityIds: string[];
  actionSuggestion: string;
  createdAt: string;
}

export interface InsightListResponse {
  aiAvailable: boolean;
  insights: InsightCard[];
}

// ── API factory ───────────────────────────────────────────────────────────────

export function createRagApi(client: ApiClient, _actorUserId: string) {
  return {
    /**
     * POST /api/ai/query — execute a semantic RAG query against indexed planning
     * data. Returns an AI-synthesised answer with source citations.
     */
    submitQuery: (req: RagQueryRequest): Promise<RagQueryResponse> =>
      client.post("/ai/query", req),

    /**
     * GET /api/teams/{id}/week/{weekStart}/ai-insights — retrieve persisted
     * TEAM_INSIGHT rows for the given team and week.
     *
     * @param teamId    team UUID string
     * @param weekStart ISO date string (YYYY-MM-DD) for the Monday of the week
     */
    getTeamInsights: (
      teamId: string,
      weekStart: string,
    ): Promise<InsightListResponse> =>
      client.get(
        `/teams/${encodeURIComponent(teamId)}/week/${encodeURIComponent(weekStart)}/ai-insights`,
      ),

    /**
     * GET /api/plans/{id}/ai-insights — retrieve persisted PERSONAL_INSIGHT rows
     * for the given plan.
     *
     * @param planId plan UUID string
     */
    getPlanInsights: (planId: string): Promise<InsightListResponse> =>
      client.get(`/plans/${encodeURIComponent(planId)}/ai-insights`),
  };
}

export type RagApi = ReturnType<typeof createRagApi>;
