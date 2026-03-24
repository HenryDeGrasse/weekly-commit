import { describe, expect, it } from "vitest";
import { ChessPiece } from "./enums.js";
import {
  isChessPieceWithinLimit,
  isOverSoftCommitLimit,
  isPawnHeavy,
  isValidEstimate,
} from "./validators.js";

describe("isValidEstimate", () => {
  it("accepts each valid Fibonacci point value", () => {
    expect(isValidEstimate(1)).toBe(true);
    expect(isValidEstimate(2)).toBe(true);
    expect(isValidEstimate(3)).toBe(true);
    expect(isValidEstimate(5)).toBe(true);
    expect(isValidEstimate(8)).toBe(true);
  });

  it("rejects non-Fibonacci values", () => {
    expect(isValidEstimate(0)).toBe(false);
    expect(isValidEstimate(4)).toBe(false);
    expect(isValidEstimate(6)).toBe(false);
    expect(isValidEstimate(7)).toBe(false);
    expect(isValidEstimate(10)).toBe(false);
  });

  it("rejects negative values", () => {
    expect(isValidEstimate(-1)).toBe(false);
  });
});

describe("isChessPieceWithinLimit", () => {
  it("allows King when no Kings exist", () => {
    expect(isChessPieceWithinLimit(ChessPiece.KING, [])).toBe(true);
  });

  it("blocks a second King commit", () => {
    expect(
      isChessPieceWithinLimit(ChessPiece.KING, [ChessPiece.KING]),
    ).toBe(false);
  });

  it("allows first Queen", () => {
    expect(isChessPieceWithinLimit(ChessPiece.QUEEN, [])).toBe(true);
  });

  it("allows second Queen", () => {
    expect(
      isChessPieceWithinLimit(ChessPiece.QUEEN, [ChessPiece.QUEEN]),
    ).toBe(true);
  });

  it("blocks a third Queen commit", () => {
    expect(
      isChessPieceWithinLimit(ChessPiece.QUEEN, [
        ChessPiece.QUEEN,
        ChessPiece.QUEEN,
      ]),
    ).toBe(false);
  });

  it("places no cap on Rook commits", () => {
    const tenRooks: ChessPiece[] = Array.from(
      { length: 10 },
      () => ChessPiece.ROOK,
    );
    expect(isChessPieceWithinLimit(ChessPiece.ROOK, tenRooks)).toBe(true);
  });

  it("places no cap on Pawn commits", () => {
    expect(
      isChessPieceWithinLimit(ChessPiece.PAWN, [
        ChessPiece.PAWN,
        ChessPiece.PAWN,
        ChessPiece.PAWN,
      ]),
    ).toBe(true);
  });
});

describe("isOverSoftCommitLimit", () => {
  it("returns false at or below 8 commits", () => {
    expect(isOverSoftCommitLimit(0)).toBe(false);
    expect(isOverSoftCommitLimit(8)).toBe(false);
  });

  it("returns true above 8 commits", () => {
    expect(isOverSoftCommitLimit(9)).toBe(true);
    expect(isOverSoftCommitLimit(20)).toBe(true);
  });
});

describe("isPawnHeavy", () => {
  it("returns false when total points is zero", () => {
    expect(isPawnHeavy(0, 0)).toBe(false);
  });

  it("returns false when Pawn fraction is at or below the threshold", () => {
    // 4/10 = 0.4, which is exactly the threshold — should NOT warn
    expect(isPawnHeavy(4, 10)).toBe(false);
  });

  it("returns true when Pawn fraction exceeds the threshold", () => {
    // 5/10 = 0.5 > 0.4
    expect(isPawnHeavy(5, 10)).toBe(true);
  });
});
