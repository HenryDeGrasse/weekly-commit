import type {
  CarryForwardReason,
  ChessPiece,
  CommitOutcome,
  PlanState,
  RcdoNodeStatus,
  RcdoNodeType,
  ScopeChangeCategory,
  TicketStatus,
} from "./enums.js";
import type { EstimatePoints } from "./constants.js";

/** One plan per user per week; the top-level weekly planning object. */
export interface WeeklyPlan {
  readonly id: string;
  readonly ownerUserId: string;
  readonly teamId: string;
  /** ISO date: yyyy-MM-dd (Monday). */
  readonly weekStartDate: string;
  state: PlanState;
  /** ISO datetime for manual-lock deadline. */
  readonly lockDeadline: string;
  /** ISO datetime for reconciliation deadline. */
  readonly reconcileDeadline: string;
  capacityBudgetPoints: number;
  isCompliant: boolean;
  systemLockedWithErrors: boolean;
  lockSnapshotId?: string;
  reconcileSnapshotId?: string;
  readonly createdAt: string;
  updatedAt: string;
}

/** A single weekly work commitment inside a plan. */
export interface Commit {
  readonly id: string;
  readonly planId: string;
  readonly ownerUserId: string;
  title: string;
  description?: string;
  chessPiece: ChessPiece;
  priorityOrder: number;
  /** Required at lock; the primary RCDO node linked. */
  rcdoNodeId?: string;
  ticketId?: string;
  /** Required at lock; Fibonacci point estimate. */
  estimatePoints?: EstimatePoints;
  /** Required for KING and QUEEN chess pieces. */
  successCriteria?: string;
  outcome?: CommitOutcome;
  outcomeNotes?: string;
  carryForwardSourceId?: string;
  carryForwardStreak: number;
  readonly createdAt: string;
  updatedAt: string;
}

/** A node in the Rally Cry → Defining Objective → Outcome hierarchy. */
export interface RcdoNode {
  readonly id: string;
  nodeType: RcdoNodeType;
  status: RcdoNodeStatus;
  parentId?: string;
  title: string;
  description?: string;
  ownerTeamId?: string;
  ownerUserId?: string;
  readonly createdAt: string;
  updatedAt: string;
}

/** A durable native work item (ticket) that may span multiple weeks. */
export interface Ticket {
  readonly id: string;
  readonly key: string;
  title: string;
  description?: string;
  status: TicketStatus;
  assigneeUserId?: string;
  readonly reporterUserId: string;
  readonly teamId: string;
  estimatePoints?: EstimatePoints;
  rcdoNodeId?: string;
  /** ISO date: yyyy-MM-dd of the target week Monday. */
  targetWeekStartDate?: string;
  readonly createdAt: string;
  updatedAt: string;
}

/** Immutable baseline snapshot captured at plan lock time. */
export interface LockSnapshot {
  readonly id: string;
  readonly planId: string;
  readonly lockedAt: string;
  readonly lockedBySystem: boolean;
  /** Denormalized JSON payload for historical replay. */
  readonly snapshotPayload: string;
}

/** A structured event recording a change made after plan lock. */
export interface ScopeChangeEvent {
  readonly id: string;
  readonly planId: string;
  commitId?: string;
  readonly category: ScopeChangeCategory;
  readonly changedByUserId: string;
  readonly reason: string;
  previousValue?: string;
  newValue?: string;
  readonly createdAt: string;
}

/** Immutable actual snapshot captured at reconciliation time. */
export interface ReconcileSnapshot {
  readonly id: string;
  readonly planId: string;
  readonly reconciledAt: string;
  /** Denormalized JSON payload for historical replay. */
  readonly snapshotPayload: string;
}

/** Provenance link from a source commit to its carry-forward target. */
export interface CarryForwardLink {
  readonly id: string;
  readonly sourceCommitId: string;
  readonly targetCommitId: string;
  readonly reason: CarryForwardReason;
  reasonNotes?: string;
  readonly createdAt: string;
}

/** A stored AI suggestion with mandatory rationale and audit metadata. */
export interface AiSuggestion {
  readonly id: string;
  planId?: string;
  commitId?: string;
  readonly suggestionType: string;
  readonly prompt: string;
  readonly rationale: string;
  readonly suggestionPayload: string;
  readonly modelVersion: string;
  accepted?: boolean;
  dismissed?: boolean;
  readonly createdAt: string;
}

/** Manager comment on a plan or commit. */
export interface ManagerComment {
  readonly id: string;
  planId?: string;
  commitId?: string;
  readonly authorUserId: string;
  content: string;
  readonly createdAt: string;
  updatedAt: string;
}

/** Per-user weekly capacity override set by a manager. */
export interface CapacityOverride {
  readonly id: string;
  readonly userId: string;
  /** ISO date: yyyy-MM-dd of the target week Monday. */
  readonly weekStartDate: string;
  budgetPoints: number;
  reason?: string;
  readonly setByManagerId: string;
  readonly createdAt: string;
}

/** An in-app notification for a user. */
export interface Notification {
  readonly id: string;
  readonly recipientUserId: string;
  readonly notificationType: string;
  readonly title: string;
  readonly body: string;
  referenceId?: string;
  referenceType?: string;
  read: boolean;
  readonly createdAt: string;
}
