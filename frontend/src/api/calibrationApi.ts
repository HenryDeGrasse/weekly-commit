/**
 * Calibration API client functions.
 * Covers the GET /api/ai/calibration/{userId} endpoint.
 */
import type { ApiClient } from "./client.js";

// ── Types ─────────────────────────────────────────────────────────────────────

/**
 * Rolling calibration profile for a user.
 * Matches the backend {@code CalibrationProfileResponse} DTO.
 */
export interface CalibrationProfile {
  /** {@code false} when there is insufficient history (< 8 weeks) to produce a profile. */
  available: boolean;
  /** Overall achievement rate [0.0–1.0]; {@code 0.0} when unavailable. */
  overallAchievementRate: number;
  /**
   * Per-chess-piece achievement rates, keyed by piece name
   * (KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN).
   */
  chessPieceAchievementRates: Record<string, number>;
  /** Fraction of commits carried forward [0.0–1.0]. */
  carryForwardProbability: number;
  /** Number of weeks of history used for calibration; {@code 0} when unavailable. */
  weeksOfData: number;
  /** Average estimate points per chess piece, keyed by piece name. */
  avgEstimateByPiece: Record<string, number>;
  /** Data-sufficiency tier: "HIGH" | "MEDIUM" | "LOW" | "INSUFFICIENT". */
  confidenceTier: string;
}

// ── API factory ───────────────────────────────────────────────────────────────

export function createCalibrationApi(client: ApiClient) {
  return {
    /**
     * GET /api/ai/calibration/{userId}
     *
     * Returns the rolling calibration profile for the given user.
     * {@code available} is {@code false} when fewer than 8 weeks of data exist.
     */
    fetchCalibration: (userId: string): Promise<CalibrationProfile> =>
      client.get(`/ai/calibration/${encodeURIComponent(userId)}`),
  };
}

export type CalibrationApi = ReturnType<typeof createCalibrationApi>;
