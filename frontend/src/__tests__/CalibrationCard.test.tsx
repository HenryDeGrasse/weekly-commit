/**
 * Tests for CalibrationCard component.
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { CalibrationCard } from "../components/ai/CalibrationCard.js";
import type { CalibrationProfile } from "../api/calibrationApi.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

const SUFFICIENT_PROFILE: CalibrationProfile = {
  available: true,
  overallAchievementRate: 0.82,
  chessPieceAchievementRates: { KING: 0.9, QUEEN: 0.75, PAWN: 0.6 },
  carryForwardProbability: 0.25,
  weeksOfData: 12,
  avgEstimateByPiece: { KING: 3, QUEEN: 2, PAWN: 1 },
  confidenceTier: "HIGH",
};

const INSUFFICIENT_PROFILE: CalibrationProfile = {
  available: false,
  overallAchievementRate: 0,
  chessPieceAchievementRates: {},
  carryForwardProbability: 0,
  weeksOfData: 3,
  avgEstimateByPiece: {},
  confidenceTier: "INSUFFICIENT",
};

const SINGLE_WEEK_PROFILE: CalibrationProfile = {
  ...SUFFICIENT_PROFILE,
  weeksOfData: 1,
};

// ── Render helper ─────────────────────────────────────────────────────────────

function renderCard(profile: CalibrationProfile, loading = false) {
  return render(
    <MockHostProvider>
      <CalibrationCard profile={profile} loading={loading} />
    </MockHostProvider>,
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("CalibrationCard", () => {
  // ── Container ──────────────────────────────────────────────────────────────

  it("renders the calibration card container", () => {
    renderCard(SUFFICIENT_PROFILE);
    expect(screen.getByTestId("calibration-card")).toBeInTheDocument();
  });

  // ── Loading state ──────────────────────────────────────────────────────────

  it("shows skeleton when loading=true", () => {
    renderCard(SUFFICIENT_PROFILE, true);
    expect(screen.getByTestId("calibration-card-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("calibration-card")).not.toBeInTheDocument();
  });

  it("shows card (not skeleton) when loading=false", () => {
    renderCard(SUFFICIENT_PROFILE, false);
    expect(screen.getByTestId("calibration-card")).toBeInTheDocument();
    expect(
      screen.queryByTestId("calibration-card-skeleton"),
    ).not.toBeInTheDocument();
  });

  // ── Sufficient data ────────────────────────────────────────────────────────

  it("renders overall achievement rate bar", () => {
    renderCard(SUFFICIENT_PROFILE);
    expect(screen.getByTestId("calibration-overall-rate")).toBeInTheDocument();
    expect(screen.getByTestId("calibration-overall-rate")).toHaveTextContent(
      "82%",
    );
  });

  it("renders per-chess-piece achievement rates", () => {
    renderCard(SUFFICIENT_PROFILE);
    expect(
      screen.getByTestId("calibration-piece-rate-king"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("calibration-piece-rate-king"),
    ).toHaveTextContent("90%");
    expect(
      screen.getByTestId("calibration-piece-rate-queen"),
    ).toHaveTextContent("75%");
    expect(
      screen.getByTestId("calibration-piece-rate-pawn"),
    ).toHaveTextContent("60%");
  });

  it("renders carry-forward probability", () => {
    renderCard(SUFFICIENT_PROFILE);
    expect(
      screen.getByTestId("calibration-carry-forward"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("calibration-carry-forward")).toHaveTextContent(
      "25%",
    );
  });

  it("renders weeks of data label for plural weeks", () => {
    renderCard(SUFFICIENT_PROFILE);
    expect(screen.getByTestId("calibration-weeks-label")).toHaveTextContent(
      "Based on 12 weeks of data",
    );
  });

  it("renders confidence badge", () => {
    renderCard(SUFFICIENT_PROFILE);
    expect(screen.getByTestId("confidence-badge")).toBeInTheDocument();
  });

  it("renders HIGH confidence badge for HIGH tier", () => {
    renderCard(SUFFICIENT_PROFILE);
    const badge = screen.getByTestId("confidence-badge");
    expect(badge).toHaveAttribute("data-tier", "HIGH");
    expect(badge).toHaveTextContent("High");
  });

  // ── Insufficient data ──────────────────────────────────────────────────────

  it("shows insufficient message when profile.available is false", () => {
    renderCard(INSUFFICIENT_PROFILE);
    expect(
      screen.getByTestId("calibration-insufficient-msg"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("calibration-insufficient-msg"),
    ).toHaveTextContent("Not enough data yet — needs 8+ weeks");
  });

  it("still renders weeks label for insufficient profile", () => {
    renderCard(INSUFFICIENT_PROFILE);
    expect(screen.getByTestId("calibration-weeks-label")).toHaveTextContent(
      "Based on 3 weeks of data",
    );
  });

  it("does not show achievement rate bars for insufficient profile", () => {
    renderCard(INSUFFICIENT_PROFILE);
    expect(
      screen.queryByTestId("calibration-overall-rate"),
    ).not.toBeInTheDocument();
  });

  it("does not show carry-forward for insufficient profile", () => {
    renderCard(INSUFFICIENT_PROFILE);
    expect(
      screen.queryByTestId("calibration-carry-forward"),
    ).not.toBeInTheDocument();
  });

  // ── Singular week label ────────────────────────────────────────────────────

  it("renders singular 'week' for exactly 1 week of data", () => {
    renderCard(SINGLE_WEEK_PROFILE);
    expect(screen.getByTestId("calibration-weeks-label")).toHaveTextContent(
      "Based on 1 week of data",
    );
  });
});
