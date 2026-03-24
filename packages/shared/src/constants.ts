/** Valid Fibonacci-scale estimate point values. */
export const VALID_ESTIMATE_POINTS = [1, 2, 3, 5, 8] as const;

/** Union type of valid estimate point values. */
export type EstimatePoints = (typeof VALID_ESTIMATE_POINTS)[number];

/** Default weekly capacity budget for a full-time user (points). */
export const DEFAULT_WEEKLY_BUDGET = 10 as const;

/** Maximum King-piece commits allowed per user per week. */
export const MAX_KING_PER_WEEK = 1 as const;

/** Maximum Queen-piece commits allowed per user per week. */
export const MAX_QUEEN_PER_WEEK = 2 as const;

/** Soft warning threshold for total active commit count. */
export const SOFT_MAX_COMMITS = 8 as const;

/**
 * If Pawn-piece points represent more than this fraction of total planned
 * points, show a distribution warning.
 */
export const PAWN_POINT_WARNING_THRESHOLD = 0.4 as const;
