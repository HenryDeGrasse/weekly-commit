import {
  VALID_ESTIMATE_POINTS,
  MAX_KING_PER_WEEK,
  MAX_QUEEN_PER_WEEK,
  SOFT_MAX_COMMITS,
  PAWN_POINT_WARNING_THRESHOLD,
} from "./constants.js";
import type { EstimatePoints } from "./constants.js";
import { ChessPiece } from "./enums.js";

/**
 * Returns true when `points` is a valid Fibonacci estimate value
 * (1, 2, 3, 5, or 8).
 */
export function isValidEstimate(points: number): points is EstimatePoints {
  return (VALID_ESTIMATE_POINTS as readonly number[]).includes(points);
}

/**
 * Returns true when adding `piece` to the week does not violate the
 * hard guardrails:
 * - KING: max 1 per week
 * - QUEEN: max 2 per week
 * - All others: unlimited
 */
export function isChessPieceWithinLimit(
  piece: ChessPiece,
  existingPieces: readonly ChessPiece[],
): boolean {
  if (piece === ChessPiece.KING) {
    const kingCount = existingPieces.filter((p) => p === ChessPiece.KING)
      .length;
    return kingCount < MAX_KING_PER_WEEK;
  }
  if (piece === ChessPiece.QUEEN) {
    const queenCount = existingPieces.filter((p) => p === ChessPiece.QUEEN)
      .length;
    return queenCount < MAX_QUEEN_PER_WEEK;
  }
  return true;
}

/**
 * Returns true when the commit count exceeds the soft maximum.
 * Used to trigger a UI warning, not a hard block.
 */
export function isOverSoftCommitLimit(commitCount: number): boolean {
  return commitCount > SOFT_MAX_COMMITS;
}

/**
 * Returns true when Pawn-piece points represent more than the warning
 * threshold fraction of total planned points.
 */
export function isPawnHeavy(pawnPoints: number, totalPoints: number): boolean {
  if (totalPoints === 0) {
    return false;
  }
  return pawnPoints / totalPoints > PAWN_POINT_WARNING_THRESHOLD;
}
