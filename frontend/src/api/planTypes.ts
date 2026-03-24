/**
 * TypeScript types for Plan and Commit API request/response payloads.
 * Mirror the backend DTO records from PlanController / CommitService.
 */

export type PlanState = "DRAFT" | "LOCKED" | "RECONCILING" | "RECONCILED";

export type ChessPiece =
  | "KING"
  | "QUEEN"
  | "ROOK"
  | "BISHOP"
  | "KNIGHT"
  | "PAWN";

export type CommitOutcome =
  | "ACHIEVED"
  | "PARTIALLY_ACHIEVED"
  | "NOT_ACHIEVED"
  | "CANCELED";

/** Valid Fibonacci-scale estimate point values (mirrors shared/constants.ts). */
export type EstimatePoints = 1 | 2 | 3 | 5 | 8;
export const VALID_ESTIMATE_POINTS: EstimatePoints[] = [1, 2, 3, 5, 8];

/** Plan header — mirrors PlanResponse Java record. */
export interface PlanResponse {
  readonly id: string;
  readonly ownerUserId: string;
  readonly teamId: string;
  /** ISO date: yyyy-MM-dd (Monday of the plan week). */
  readonly weekStartDate: string;
  readonly state: PlanState;
  /** ISO datetime — manual-lock deadline. */
  readonly lockDeadline: string;
  /** ISO datetime — reconciliation deadline. */
  readonly reconcileDeadline: string;
  readonly capacityBudgetPoints: number;
  readonly compliant: boolean;
  readonly systemLockedWithErrors: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Single commit — mirrors CommitResponse Java record. */
export interface CommitResponse {
  readonly id: string;
  readonly planId: string;
  readonly ownerUserId: string;
  readonly title: string;
  readonly description?: string;
  readonly chessPiece: ChessPiece;
  readonly priorityOrder: number;
  readonly rcdoNodeId?: string;
  readonly workItemId?: string;
  readonly estimatePoints?: EstimatePoints;
  readonly successCriteria?: string;
  readonly outcome?: CommitOutcome;
  readonly outcomeNotes?: string;
  readonly carryForwardStreak: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Combined response — mirrors PlanWithCommitsResponse Java record. */
export interface PlanWithCommitsResponse {
  readonly plan: PlanResponse;
  readonly commits: CommitResponse[];
  readonly totalPoints: number;
}

/** POST /api/plans request body — mirrors CreatePlanRequest Java record. */
export interface GetOrCreatePlanPayload {
  readonly userId: string;
  readonly weekStartDate?: string;
}

/** POST /api/plans/{planId}/commits request body — mirrors CreateCommitRequest. */
export interface CreateCommitPayload {
  readonly title: string;
  readonly chessPiece: ChessPiece;
  readonly description?: string;
  readonly rcdoNodeId?: string;
  readonly workItemId?: string;
  readonly estimatePoints?: EstimatePoints;
  readonly successCriteria?: string;
}

/**
 * PUT /api/plans/{planId}/commits/{commitId} request body.
 * Null/omitted fields are treated as "no-op" by the backend.
 */
export interface UpdateCommitPayload {
  readonly title?: string;
  readonly chessPiece?: ChessPiece;
  readonly description?: string;
  readonly rcdoNodeId?: string;
  readonly workItemId?: string;
  readonly estimatePoints?: EstimatePoints;
  readonly successCriteria?: string;
}
