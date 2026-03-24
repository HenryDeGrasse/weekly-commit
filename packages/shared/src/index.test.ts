import { describe, expect, it } from "vitest";
import {
  ChessPiece,
  DEFAULT_WEEKLY_BUDGET,
  PlanState,
  VALID_ESTIMATE_POINTS,
  isValidEstimate,
} from "./index.js";

describe("shared package index exports", () => {
  it("exports PlanState enum values", () => {
    expect(PlanState.DRAFT).toBe("DRAFT");
    expect(PlanState.LOCKED).toBe("LOCKED");
    expect(PlanState.RECONCILING).toBe("RECONCILING");
    expect(PlanState.RECONCILED).toBe("RECONCILED");
  });

  it("exports ChessPiece enum values", () => {
    expect(ChessPiece.KING).toBe("KING");
    expect(ChessPiece.PAWN).toBe("PAWN");
  });

  it("exports VALID_ESTIMATE_POINTS constant", () => {
    expect(VALID_ESTIMATE_POINTS).toStrictEqual([1, 2, 3, 5, 8]);
  });

  it("exports DEFAULT_WEEKLY_BUDGET constant", () => {
    expect(DEFAULT_WEEKLY_BUDGET).toBe(10);
  });

  it("re-exports isValidEstimate validator", () => {
    expect(isValidEstimate(5)).toBe(true);
    expect(isValidEstimate(4)).toBe(false);
  });
});
