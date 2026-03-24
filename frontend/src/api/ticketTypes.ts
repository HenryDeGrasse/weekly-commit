/**
 * TypeScript types for the Ticket API request/response payloads.
 * Mirror the backend DTO records from TicketController.
 */

import type { TicketStatus } from "./teamTypes.js";
import type { ChessPiece, EstimatePoints, CommitOutcome } from "./planTypes.js";

export type { TicketStatus };
export type TicketPriority = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

/** Valid status transitions from a given status. */
export const TICKET_STATUS_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  TODO: ["IN_PROGRESS", "CANCELED"],
  IN_PROGRESS: ["DONE", "BLOCKED", "CANCELED", "TODO"],
  BLOCKED: ["IN_PROGRESS", "CANCELED", "TODO"],
  DONE: [],
  CANCELED: ["TODO"],
};

export const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
  TODO: "To Do",
  IN_PROGRESS: "In Progress",
  DONE: "Done",
  CANCELED: "Canceled",
  BLOCKED: "Blocked",
};

export const TICKET_PRIORITY_LABELS: Record<TicketPriority, string> = {
  CRITICAL: "Critical",
  HIGH: "High",
  MEDIUM: "Medium",
  LOW: "Low",
};

/** Single status history entry on a ticket. */
export interface TicketStatusHistory {
  readonly id: string;
  readonly ticketId: string;
  readonly fromStatus: TicketStatus | null;
  readonly toStatus: TicketStatus;
  readonly changedByUserId: string;
  readonly changedAt: string;
  readonly note: string | null;
}

/** A linked commit entry on a ticket detail. */
export interface LinkedCommitEntry {
  readonly commitId: string;
  readonly planId: string;
  readonly commitTitle: string;
  readonly chessPiece: ChessPiece;
  readonly estimatePoints: number | null;
  readonly weekStartDate: string;
  readonly outcome: CommitOutcome | null;
}

/** Full ticket detail response. Mirrors TicketResponse Java record. */
export interface TicketResponse {
  readonly id: string;
  readonly key: string;
  readonly title: string;
  readonly description: string | null;
  readonly status: TicketStatus;
  readonly priority: TicketPriority;
  readonly assigneeUserId: string | null;
  readonly reporterUserId: string;
  readonly teamId: string;
  readonly rcdoNodeId: string | null;
  readonly estimatePoints: EstimatePoints | null;
  readonly targetWeekStartDate: string | null;
  readonly statusHistory: TicketStatusHistory[];
  readonly linkedCommits: LinkedCommitEntry[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Compact ticket list item. Mirrors TicketSummary Java record. */
export interface TicketSummaryResponse {
  readonly id: string;
  readonly key: string;
  readonly title: string;
  readonly status: TicketStatus;
  readonly priority: TicketPriority;
  readonly assigneeUserId: string | null;
  readonly teamId: string;
  readonly rcdoNodeId: string | null;
  readonly estimatePoints: EstimatePoints | null;
  readonly targetWeekStartDate: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Paginated ticket list. Mirrors PagedTicketResponse Java record. */
export interface PagedTicketResponse {
  readonly items: TicketSummaryResponse[];
  readonly total: number;
  readonly page: number;
  readonly pageSize: number;
}

/** POST /api/tickets request body. */
export interface CreateTicketPayload {
  readonly title: string;
  readonly description?: string;
  readonly status?: TicketStatus;
  readonly priority: TicketPriority;
  readonly assigneeUserId?: string;
  readonly reporterUserId: string;
  readonly teamId: string;
  readonly rcdoNodeId?: string;
  readonly estimatePoints?: EstimatePoints;
  readonly targetWeekStartDate?: string;
}

/** PUT /api/tickets/{id} request body — partial update. */
export interface UpdateTicketPayload {
  readonly title?: string;
  readonly description?: string;
  readonly status?: TicketStatus;
  readonly priority?: TicketPriority;
  readonly assigneeUserId?: string;
  readonly teamId?: string;
  readonly rcdoNodeId?: string;
  readonly estimatePoints?: EstimatePoints;
  readonly targetWeekStartDate?: string;
}

/** Query params for GET /api/tickets. */
export interface TicketListParams {
  readonly status?: TicketStatus;
  readonly assigneeUserId?: string;
  readonly teamId?: string;
  readonly rcdoNodeId?: string;
  readonly targetWeek?: string;
  readonly priority?: TicketPriority;
  readonly page?: number;
  readonly pageSize?: number;
  readonly sortBy?: "key" | "title" | "status" | "priority" | "updatedAt";
  readonly sortDir?: "asc" | "desc";
}

// ── Plan history types ──────────────────────────────────────────────────────

/** Per-week summary entry for plan history. Mirrors WeeklyPlanHistoryEntry Java record. */
export interface WeeklyPlanHistoryEntry {
  readonly planId: string;
  readonly weekStartDate: string;
  readonly planState: "DRAFT" | "LOCKED" | "RECONCILING" | "RECONCILED";
  readonly compliant: boolean;
  readonly commitCount: number;
  readonly plannedPoints: number;
  readonly achievedPoints: number;
  readonly carryForwardCount: number;
}

/** Carry-forward lineage node — one step in the provenance chain. */
export interface CarryForwardNode {
  readonly commitId: string;
  readonly planId: string;
  readonly weekStartDate: string;
  readonly title: string;
  readonly outcome: CommitOutcome | null;
  readonly streak: number;
}

/** Full carry-forward lineage chain. Mirrors CarryForwardLineageResponse Java record. */
export interface CarryForwardLineageResponse {
  readonly currentCommitId: string;
  readonly chain: CarryForwardNode[];
}

// ── Team history types ──────────────────────────────────────────────────────

/** One row in the team history trend table. Mirrors TeamWeekHistoryEntry Java record. */
export interface TeamWeekHistoryEntry {
  readonly weekStartDate: string;
  readonly memberCount: number;
  readonly complianceRate: number;
  readonly plannedPoints: number;
  readonly achievedPoints: number;
  readonly carryForwardRate: number;
  readonly exceptionCount: number;
}

/** Team history response. Mirrors TeamHistoryResponse Java record. */
export interface TeamHistoryResponse {
  readonly teamId: string;
  readonly entries: TeamWeekHistoryEntry[];
}

// ── Filter preset types ──────────────────────────────────────────────────────

/** A saved filter preset. */
export interface FilterPreset {
  readonly id: string;
  readonly name: string;
  readonly params: Record<string, string>;
}
