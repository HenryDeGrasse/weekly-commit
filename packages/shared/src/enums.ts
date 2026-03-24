/** Weekly plan lifecycle state. */
export enum PlanState {
  DRAFT = "DRAFT",
  LOCKED = "LOCKED",
  RECONCILING = "RECONCILING",
  RECONCILED = "RECONCILED",
}

/** Chess-piece taxonomy for commit prioritization. */
export enum ChessPiece {
  KING = "KING",
  QUEEN = "QUEEN",
  ROOK = "ROOK",
  BISHOP = "BISHOP",
  KNIGHT = "KNIGHT",
  PAWN = "PAWN",
}

/** Outcome recorded at reconciliation time. */
export enum CommitOutcome {
  ACHIEVED = "ACHIEVED",
  PARTIALLY_ACHIEVED = "PARTIALLY_ACHIEVED",
  NOT_ACHIEVED = "NOT_ACHIEVED",
  CANCELED = "CANCELED",
}

/** Level in the RCDO strategy hierarchy. */
export enum RcdoNodeType {
  RALLY_CRY = "RALLY_CRY",
  DEFINING_OBJECTIVE = "DEFINING_OBJECTIVE",
  OUTCOME = "OUTCOME",
}

/** Lifecycle status of an RCDO node. */
export enum RcdoNodeStatus {
  DRAFT = "DRAFT",
  ACTIVE = "ACTIVE",
  ARCHIVED = "ARCHIVED",
}

/** Native work-item (ticket) workflow statuses. */
export enum TicketStatus {
  BACKLOG = "BACKLOG",
  READY = "READY",
  IN_PROGRESS = "IN_PROGRESS",
  BLOCKED = "BLOCKED",
  DONE = "DONE",
  CANCELED = "CANCELED",
}

/** Categories for post-lock scope change events. */
export enum ScopeChangeCategory {
  COMMIT_ADDED = "COMMIT_ADDED",
  COMMIT_REMOVED = "COMMIT_REMOVED",
  ESTIMATE_CHANGED = "ESTIMATE_CHANGED",
  CHESS_PIECE_CHANGED = "CHESS_PIECE_CHANGED",
  RCDO_CHANGED = "RCDO_CHANGED",
  PRIORITY_CHANGED = "PRIORITY_CHANGED",
}

/** Controlled reason list for explicit carry-forward. */
export enum CarryForwardReason {
  BLOCKED_BY_DEPENDENCY = "BLOCKED_BY_DEPENDENCY",
  SCOPE_EXPANDED = "SCOPE_EXPANDED",
  REPRIORITIZED = "REPRIORITIZED",
  RESOURCE_UNAVAILABLE = "RESOURCE_UNAVAILABLE",
  TECHNICAL_OBSTACLE = "TECHNICAL_OBSTACLE",
  EXTERNAL_DELAY = "EXTERNAL_DELAY",
  UNDERESTIMATED = "UNDERESTIMATED",
  STILL_IN_PROGRESS = "STILL_IN_PROGRESS",
}
