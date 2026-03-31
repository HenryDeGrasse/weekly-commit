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
  readonly workItemKey?: string;
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
  readonly workItemKey?: string;
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
  readonly workItemKey?: string;
  readonly estimatePoints?: EstimatePoints;
  readonly successCriteria?: string;
}

// ── Lock types ────────────────────────────────────────────────────────────────

/** A single hard-validation failure returned by POST /api/plans/{id}/lock. */
export interface LockValidationError {
  readonly field: string;
  readonly message: string;
}

/**
 * Response from POST /api/plans/{id}/lock.
 * success=false → errors contains the hard validation failures.
 */
export interface LockResponse {
  readonly success: boolean;
  readonly plan: PlanWithCommitsResponse | null;
  readonly errors: LockValidationError[];
}

// ── Scope-change types ────────────────────────────────────────────────────────

export type ScopeChangeCategory =
  | "COMMIT_ADDED"
  | "COMMIT_REMOVED"
  | "ESTIMATE_CHANGED"
  | "CHESS_PIECE_CHANGED"
  | "RCDO_CHANGED"
  | "PRIORITY_CHANGED";

export type ScopeChangeAction = "ADD" | "REMOVE" | "EDIT";

/** Single scope-change event — mirrors ScopeChangeEventResponse Java record. */
export interface ScopeChangeEventResponse {
  readonly id: string;
  readonly planId: string;
  readonly commitId: string | null;
  readonly category: ScopeChangeCategory;
  readonly changedByUserId: string | null;
  readonly reason: string;
  readonly previousValue: string | null;
  readonly newValue: string | null;
  readonly createdAt: string;
}

/** Timeline response — mirrors ScopeChangeTimelineResponse Java record. */
export interface ScopeChangeTimelineResponse {
  readonly events: ScopeChangeEventResponse[];
  readonly managerExceptions: unknown[];
}

/** Request body for POST /api/plans/{id}/scope-changes. */
export interface ScopeChangePayload {
  readonly action: ScopeChangeAction;
  readonly reason: string;
  readonly commitId?: string;
  readonly commitData?: CreateCommitPayload;
  readonly changes?: UpdateCommitPayload;
}

// ── Reconciliation types ──────────────────────────────────────────────────────

/** Per-commit reconciliation view — mirrors ReconcileCommitView Java record. */
export interface ReconcileCommitView {
  readonly commitId: string;
  readonly currentTitle: string;
  readonly currentChessPiece: ChessPiece;
  readonly currentEstimatePoints: number | null;
  readonly currentOutcome: CommitOutcome | null;
  readonly currentOutcomeNotes: string | null;
  /** Baseline snapshot values (null for post-lock-added commits). */
  readonly baselineSnapshot: Record<string, unknown> | null;
  readonly scopeChanges: ScopeChangeEventResponse[];
  readonly linkedTicketStatus: string | null;
  readonly addedPostLock: boolean;
  readonly removedPostLock: boolean;
}

/** Full reconciliation view response — mirrors ReconciliationViewResponse. */
export interface ReconciliationViewResponse {
  readonly plan: PlanResponse;
  readonly commits: ReconcileCommitView[];
  readonly baselineTotalPoints: number;
  readonly currentTotalPoints: number;
  readonly commitCount: number;
  readonly outcomesSetCount: number;
}

/** Request body for PUT /api/plans/{id}/commits/{commitId}/outcome. */
export interface SetOutcomePayload {
  readonly outcome: CommitOutcome;
  readonly notes?: string | undefined;
}

// ── Carry-forward types ───────────────────────────────────────────────────────

export type CarryForwardReason =
  | "BLOCKED_BY_DEPENDENCY"
  | "SCOPE_EXPANDED"
  | "REPRIORITIZED"
  | "RESOURCE_UNAVAILABLE"
  | "TECHNICAL_OBSTACLE"
  | "EXTERNAL_DELAY"
  | "UNDERESTIMATED"
  | "STILL_IN_PROGRESS";

/** Request body for POST /api/plans/{planId}/commits/{commitId}/carry-forward. */
export interface CarryForwardPayload {
  readonly targetWeekStart: string;
  readonly reason: CarryForwardReason;
  readonly reasonText?: string | undefined;
}
