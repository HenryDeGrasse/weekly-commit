/**
 * Tests for PlanRecommendationCard component.
 *
 * Covers:
 *   - Renders recommendation with all fields
 *   - Dismiss button calls onDismiss with the correct suggestionId
 *   - Feedback buttons present
 *   - What-if summary renders before/after points and risk deltas
 *   - Apply suggestion button opens a confirmation dialog
 *   - Null whatIfResult handled gracefully (no crash, no summary shown)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { PlanRecommendationCard } from "../components/ai/PlanRecommendationCard.js";
import type { PlanRecommendation } from "../api/recommendationApi.js";

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock("../api/aiHooks.js", () => ({
  useAiApi: vi.fn(() => ({ recordFeedback: vi.fn() })),
  usePlanEvidence: vi.fn(() => ({ data: undefined, loading: false, error: null })),
}));

// ── Fixtures ──────────────────────────────────────────────────────────────────

const OVERCOMMIT_REC: PlanRecommendation = {
  suggestionId: "sugg-1",
  riskType: "OVERCOMMIT",
  description: "You have committed 14 points against a budget of 10.",
  suggestedAction: "Remove or downsize two PAWN-level commits.",
  whatIfResult: {
    available: true,
    currentState: {
      totalPoints: 14,
      capacityBudget: 10,
      riskSignals: ["OVERCOMMIT"],
      rcdoCoverage: {},
    },
    projectedState: {
      totalPoints: 9,
      capacityBudget: 10,
      riskSignals: [],
      rcdoCoverage: {},
    },
    capacityDelta: -5,
    rcdoCoverageChanges: null,
    riskDelta: {
      newRisks: [],
      resolvedRisks: ["OVERCOMMIT"],
    },
    narrative: "Removing two PAWN commits brings you within budget.",
    recommendation: null,
  },
  narrative: "OVERCOMMIT risk detected. Consider reducing scope.",
  confidence: "HIGH",
  available: true,
};

const BLOCKED_REC: PlanRecommendation = {
  suggestionId: "sugg-2",
  riskType: "BLOCKED_CRITICAL",
  description: "KING commit is linked to a BLOCKED ticket.",
  suggestedAction: "Escalate or unblock the ticket immediately.",
  whatIfResult: null,
  narrative: "Blocked critical item needs immediate action.",
  confidence: "MEDIUM",
  available: true,
};

// ── Render helper ─────────────────────────────────────────────────────────────

function renderCard(
  rec: PlanRecommendation,
  onDismiss = vi.fn(),
) {
  return render(
    <MockHostProvider>
      <PlanRecommendationCard recommendation={rec} onDismiss={onDismiss} />
    </MockHostProvider>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("PlanRecommendationCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Basic rendering ────────────────────────────────────────────────────────

  it("renders the card container with the correct testid", () => {
    renderCard(OVERCOMMIT_REC);
    expect(
      screen.getByTestId("recommendation-card-sugg-1"),
    ).toBeInTheDocument();
  });

  it("renders the risk type badge", () => {
    renderCard(OVERCOMMIT_REC);
    expect(
      screen.getByTestId("recommendation-risk-badge-sugg-1"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("recommendation-risk-badge-sugg-1"),
    ).toHaveTextContent("Overcommit");
  });

  it("renders the description", () => {
    renderCard(OVERCOMMIT_REC);
    expect(
      screen.getByTestId("recommendation-description-sugg-1"),
    ).toHaveTextContent("You have committed 14 points");
  });

  it("renders the suggested action", () => {
    renderCard(OVERCOMMIT_REC);
    expect(
      screen.getByTestId("recommendation-action-sugg-1"),
    ).toHaveTextContent("Remove or downsize two PAWN-level commits.");
  });

  it("renders the narrative text", () => {
    renderCard(OVERCOMMIT_REC);
    expect(
      screen.getByTestId("recommendation-narrative-sugg-1"),
    ).toHaveTextContent("OVERCOMMIT risk detected");
  });

  it("renders the confidence badge", () => {
    renderCard(OVERCOMMIT_REC);
    expect(screen.getByTestId("confidence-badge")).toBeInTheDocument();
    expect(screen.getByTestId("confidence-badge")).toHaveAttribute(
      "data-tier",
      "HIGH",
    );
  });

  // ── Dismiss ────────────────────────────────────────────────────────────────

  it("renders a dismiss button", () => {
    renderCard(OVERCOMMIT_REC);
    expect(
      screen.getByTestId("recommendation-dismiss-btn-sugg-1"),
    ).toBeInTheDocument();
  });

  it("calls onDismiss with the suggestionId when dismiss is clicked", () => {
    const onDismiss = vi.fn();
    renderCard(OVERCOMMIT_REC, onDismiss);
    fireEvent.click(screen.getByTestId("recommendation-dismiss-btn-sugg-1"));
    expect(onDismiss).toHaveBeenCalledWith("sugg-1");
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  // ── Feedback buttons ───────────────────────────────────────────────────────

  it("renders AI feedback accept button", () => {
    renderCard(OVERCOMMIT_REC);
    expect(screen.getByTestId("ai-feedback-accept")).toBeInTheDocument();
  });

  it("renders AI feedback dismiss button", () => {
    renderCard(OVERCOMMIT_REC);
    expect(screen.getByTestId("ai-feedback-dismiss")).toBeInTheDocument();
  });

  // ── What-if summary ────────────────────────────────────────────────────────

  it("renders the what-if summary when whatIfResult is available", () => {
    renderCard(OVERCOMMIT_REC);
    expect(screen.getByTestId("what-if-summary")).toBeInTheDocument();
  });

  it("shows before/after point counts in the what-if summary", () => {
    renderCard(OVERCOMMIT_REC);
    expect(screen.getByTestId("what-if-points-before")).toHaveTextContent(
      "14 pts",
    );
    expect(screen.getByTestId("what-if-points-after")).toHaveTextContent(
      "9 pts",
    );
  });

  it("shows the capacity delta", () => {
    renderCard(OVERCOMMIT_REC);
    expect(screen.getByTestId("what-if-delta")).toHaveTextContent("-5");
  });

  it("shows resolved risks in the what-if summary", () => {
    renderCard(OVERCOMMIT_REC);
    expect(
      screen.getByTestId("what-if-resolved-risks"),
    ).toHaveTextContent("OVERCOMMIT");
  });

  it("does NOT render what-if summary when whatIfResult is null", () => {
    renderCard(BLOCKED_REC);
    expect(screen.queryByTestId("what-if-summary")).not.toBeInTheDocument();
  });

  it("does not crash when whatIfResult is null", () => {
    expect(() => renderCard(BLOCKED_REC)).not.toThrow();
  });

  // ── Apply suggestion dialog ────────────────────────────────────────────────

  it("renders the Apply suggestion button", () => {
    renderCard(OVERCOMMIT_REC);
    expect(
      screen.getByTestId("recommendation-apply-btn-sugg-1"),
    ).toBeInTheDocument();
  });

  it("shows a confirmation dialog when Apply suggestion is clicked", () => {
    renderCard(OVERCOMMIT_REC);
    fireEvent.click(
      screen.getByTestId("recommendation-apply-btn-sugg-1"),
    );
    expect(
      screen.getByTestId("apply-suggestion-dialog"),
    ).toBeInTheDocument();
  });

  it("closes the dialog when Close is clicked", () => {
    renderCard(OVERCOMMIT_REC);
    fireEvent.click(screen.getByTestId("recommendation-apply-btn-sugg-1"));
    expect(screen.getByTestId("apply-suggestion-dialog")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("apply-suggestion-close-btn"));
    expect(
      screen.queryByTestId("apply-suggestion-dialog"),
    ).not.toBeInTheDocument();
  });

  // ── BLOCKED_CRITICAL — no what-if ─────────────────────────────────────────

  it("renders BLOCKED_CRITICAL card correctly", () => {
    renderCard(BLOCKED_REC);
    expect(
      screen.getByTestId("recommendation-card-sugg-2"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("recommendation-risk-badge-sugg-2"),
    ).toHaveTextContent("Blocked Critical");
    expect(
      screen.getByTestId("recommendation-description-sugg-2"),
    ).toHaveTextContent("KING commit is linked to a BLOCKED ticket");
  });

  it("renders dismiss button for BLOCKED_CRITICAL", () => {
    renderCard(BLOCKED_REC);
    fireEvent.click(
      screen.getByTestId("recommendation-dismiss-btn-sugg-2"),
    );
    // onDismiss defaults to vi.fn(); just verify no crash
    expect(
      screen.queryByTestId("recommendation-card-sugg-2"),
    ).toBeInTheDocument();
  });
});
