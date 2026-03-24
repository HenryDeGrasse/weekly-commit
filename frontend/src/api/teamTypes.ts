/**
 * TypeScript types for Team API request/response payloads.
 * Mirror the backend DTOs from TeamController / ManagerReviewService.
 */

import type { PlanState, ChessPiece, CommitOutcome } from "./planTypes.js";

export type { PlanState, ChessPiece, CommitOutcome };

export type ExceptionSeverity = "HIGH" | "MEDIUM" | "LOW";

export type ExceptionType =
  | "MISSED_LOCK"
  | "AUTO_LOCKED"
  | "MISSED_RECONCILE"
  | "OVER_BUDGET"
  | "REPEATED_CARRY_FORWARD"
  | "POST_LOCK_SCOPE_INCREASE"
  | "KING_CHANGED_POST_LOCK"
  | "HIGH_SCOPE_VOLATILITY";

export type TicketStatus =
  | "TODO"
  | "IN_PROGRESS"
  | "DONE"
  | "CANCELED"
  | "BLOCKED";

/** Single commit in a member's team-week view — mirrors CommitResponse Java record. */
export interface MemberCommitView {
  readonly id: string;
  readonly title: string;
  readonly chessPiece: ChessPiece;
  readonly estimatePoints: number | null;
  readonly priorityOrder: number;
  readonly rcdoNodeId: string | null;
  readonly carryForwardStreak: number;
  readonly outcome: CommitOutcome | null;
}

/**
 * Summary of a single team member's weekly plan, as seen by a manager.
 * Mirrors MemberWeekView Java record.
 */
export interface MemberWeekView {
  readonly userId: string;
  readonly displayName: string;
  readonly planId: string | null;
  readonly planState: PlanState | null;
  readonly capacityBudgetPoints: number;
  readonly totalCommittedPoints: number;
  readonly commits: MemberCommitView[];
}

/** Compliance status for a single team member. Mirrors MemberComplianceSummary Java record. */
export interface MemberComplianceSummary {
  readonly userId: string;
  readonly displayName: string;
  readonly lockCompliant: boolean;
  readonly reconcileCompliant: boolean;
  readonly autoLocked: boolean;
  readonly planState: PlanState | null;
  readonly hasPlan: boolean;
}

/** RCDO rollup entry — aggregate points/commits for one RCDO node. Mirrors RcdoRollupEntry. */
export interface RcdoRollupEntry {
  readonly rcdoNodeId: string;
  readonly commitCount: number;
  readonly totalPoints: number;
}

/** Chess-piece distribution entry across the team week. Mirrors ChessDistributionEntry. */
export interface ChessDistributionEntry {
  readonly chessPiece: ChessPiece;
  readonly commitCount: number;
  readonly totalPoints: number;
}

/** Uncommitted ticket summary — a work item with no linked commit. Mirrors UncommittedTicketSummary. */
export interface UncommittedTicketSummary {
  readonly id: string;
  readonly key: string;
  readonly title: string;
  readonly status: TicketStatus;
  readonly assigneeUserId: string | null;
  readonly teamId: string;
  readonly rcdoNodeId: string | null;
  readonly estimatePoints: number | null;
  readonly targetWeekStartDate: string | null;
}

/**
 * Full aggregated team weekly view.
 * Mirrors TeamWeekViewResponse Java record.
 */
export interface TeamWeekViewResponse {
  readonly teamId: string;
  readonly teamName: string;
  readonly weekStart: string;
  readonly memberViews: MemberWeekView[];
  /** Peer-filtered views (for IC callers). Empty for manager callers. */
  readonly peerViews: unknown[];
  readonly uncommittedAssignedTickets: UncommittedTicketSummary[];
  readonly uncommittedUnassignedTickets: UncommittedTicketSummary[];
  readonly rcdoRollup: RcdoRollupEntry[];
  readonly chessDistribution: ChessDistributionEntry[];
  readonly complianceSummary: MemberComplianceSummary[];
}

/** Manager-review exception. Mirrors ExceptionResponse Java record. */
export interface ExceptionResponse {
  readonly id: string;
  readonly teamId: string;
  readonly planId: string | null;
  readonly userId: string;
  readonly exceptionType: ExceptionType;
  readonly severity: ExceptionSeverity;
  readonly description: string;
  readonly weekStartDate: string;
  readonly resolved: boolean;
  readonly resolution: string | null;
  readonly resolvedAt: string | null;
  readonly resolvedById: string | null;
  readonly createdAt: string;
}

/** Manager comment on a plan or commit. Mirrors CommentResponse Java record. */
export interface CommentResponse {
  readonly id: string;
  readonly planId: string | null;
  readonly commitId: string | null;
  readonly authorUserId: string;
  readonly content: string;
  readonly createdAt: string;
}

// ── Request payloads ──────────────────────────────────────────────────────────

/** PUT /api/exceptions/{id}/resolve request body. */
export interface ResolveExceptionPayload {
  readonly resolverId: string;
  readonly resolution: string;
}

/** POST /api/comments request body. Exactly one of planId/commitId must be set. */
export interface AddCommentPayload {
  readonly managerId: string;
  readonly planId?: string;
  readonly commitId?: string;
  readonly text: string;
}

/** PUT /api/tickets/{id} — partial update for quick-assign. */
export interface QuickAssignPayload {
  readonly assigneeUserId: string;
}
