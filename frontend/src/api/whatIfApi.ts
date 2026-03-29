/**
 * What-If Planner API client functions.
 * Simulates hypothetical plan mutations in-memory and returns structured
 * impact analysis with an optional LLM narrative.
 */
import type { ApiClient } from "./client.js";

// ── Types ─────────────────────────────────────────────────────────────────────

export type WhatIfMutationAction =
  | "ADD_COMMIT"
  | "REMOVE_COMMIT"
  | "MODIFY_COMMIT";

export interface WhatIfMutation {
  action: WhatIfMutationAction;
  /** Commit id to remove or modify (null for ADD_COMMIT). */
  commitId?: string;
  /** Draft title (used for ADD_COMMIT or MODIFY_COMMIT). */
  title?: string;
  /** Chess piece name as plain string (e.g. "KING"). */
  chessPiece?: string;
  /** Estimated story points. */
  estimatePoints?: number;
  /** RCDO node id. */
  rcdoNodeId?: string;
}

export interface WhatIfRequest {
  planId: string;
  userId: string;
  hypotheticalChanges: WhatIfMutation[];
}

/** Snapshot of plan state at a point in time. */
export interface PlanSnapshot {
  totalPoints: number;
  capacityBudget: number;
  /** Active risk signal types, e.g. "OVERCOMMIT". */
  riskSignals: string[];
  /** Map of RCDO node id → total planned points for that node. */
  rcdoCoverage: Record<string, number>;
}

/** Change in story-point coverage for a single RCDO node. */
export interface RcdoCoverageChange {
  rcdoNodeId: string;
  /** RCDO node title (null if not resolved). */
  rcdoTitle?: string | null;
  beforePoints: number;
  afterPoints: number;
}

/** Risk signals added or resolved by mutations. */
export interface RiskDelta {
  newRisks: string[];
  resolvedRisks: string[];
}

/** Response from POST /api/ai/what-if. */
export interface WhatIfResponse {
  /** false when the simulation is unavailable. */
  available: boolean;
  /** Plan state before mutations. */
  currentState: PlanSnapshot | null;
  /** Plan state after mutations. */
  projectedState: PlanSnapshot | null;
  /** Change in total points: projected − current. */
  capacityDelta: number;
  /** Per-RCDO-node point changes. */
  rcdoCoverageChanges: RcdoCoverageChange[] | null;
  /** Risk signal additions and removals. */
  riskDelta: RiskDelta | null;
  /** LLM narrative (null when AI is unavailable). */
  narrative?: string | null;
  /** LLM recommendation (null when AI is unavailable). */
  recommendation?: string | null;
}

// ── API factory ───────────────────────────────────────────────────────────────

export function createWhatIfApi(client: ApiClient, _actorUserId: string) {
  return {
    /**
     * POST /api/ai/what-if — simulates hypothetical commit mutations in-memory
     * and returns structured before/after impact analysis. Always returns
     * structured data; narrative/recommendation are null when AI is down.
     */
    simulate: (req: WhatIfRequest): Promise<WhatIfResponse> =>
      client.post("/ai/what-if", req),
  };
}

export type WhatIfApi = ReturnType<typeof createWhatIfApi>;
