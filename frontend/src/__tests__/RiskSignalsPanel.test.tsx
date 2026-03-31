/**
 * Tests for RiskSignalsPanel component.
 *
 * Covers:
 *   - No planId → "Select a member's plan" placeholder
 *   - Loading state → skeleton
 *   - Error state → error message
 *   - aiAvailable=false → unavailable message
 *   - Zero signals → clear message
 *   - Signals present → renders cards with badge + rationale
 *   - Signal count label (singular and plural)
 *   - "Show evidence" toggle button renders when signals present
 *   - Clicking toggle shows loading skeleton while evidence loads
 *   - Clicking toggle shows EvidenceDrawer when evidence data arrives
 *   - Clicking toggle a second time hides the drawer
 *   - Toggle button does NOT render on clear/empty/unavailable states
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { RiskSignalsPanel } from "../components/ai/RiskSignalsPanel.js";
import type { PlanRiskSignalsResponse, RiskSignal } from "../api/aiApi.js";
import { ApiRequestError } from "../api/client.js";

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock("../api/aiHooks.js", () => ({
  useRiskSignals: vi.fn(() => ({
    data: undefined,
    loading: true,
    error: null,
    refetch: vi.fn(),
  })),
  useAiApi: vi.fn(() => ({ recordFeedback: vi.fn() })),
  useAiStatus: vi.fn(() => ({
    data: { available: true },
    loading: false,
    error: null,
  })),
  usePlanEvidence: vi.fn(() => ({ data: undefined, loading: false, error: null })),
}));

import * as aiHooks from "../api/aiHooks.js";

// ── Sample data ───────────────────────────────────────────────────────────────

function makeSignal(
  id: string,
  signalType: string,
  rationale = "Detected pattern.",
): RiskSignal {
  return {
    id,
    signalType,
    rationale,
    planId: "plan-abc",
    commitId: null,
    createdAt: "2025-01-06T08:00:00Z",
  };
}

function available(signals: RiskSignal[]): PlanRiskSignalsResponse {
  return { aiAvailable: true, planId: "plan-abc", signals };
}

function makeError(): ApiRequestError {
  return new ApiRequestError({ status: 500, message: "Server error" });
}

const sampleEvidence = {
  sqlFacts: {
    userDisplayName: "Alice",
    teamName: "Bravo",
    weekStart: "2025-01-06",
    planState: "LOCKED",
    capacityBudget: 10,
    totalPlannedPoints: 8,
    totalAchievedPoints: 6,
    commitCount: 4,
    carryForwardCount: 1,
    scopeChangeCount: 0,
    lockCompliance: true,
    reconcileCompliance: true,
    chessDistribution: { KING: 1, QUEEN: 2 },
  },
  lineage: null,
  semanticMatches: [],
  riskFeatures: null,
};

// ── Render helper ─────────────────────────────────────────────────────────────

function renderPanel(planId: string | null = "plan-abc") {
  return render(
    <MockHostProvider>
      <RiskSignalsPanel planId={planId} />
    </MockHostProvider>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("RiskSignalsPanel", () => {
  beforeEach(() => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.usePlanEvidence).mockReturnValue({
      data: undefined,
      loading: false,
      error: null,
    });
  });

  // ── No planId ──────────────────────────────────────────────────────────

  it("shows placeholder when no planId provided", () => {
    renderPanel(null);
    expect(screen.getByTestId("risk-signals-no-plan")).toBeInTheDocument();
    expect(screen.getByTestId("risk-signals-no-plan")).toHaveTextContent(
      "Select a member's plan",
    );
  });

  it("does not show evidence toggle when no planId", () => {
    renderPanel(null);
    expect(
      screen.queryByTestId("risk-evidence-toggle"),
    ).not.toBeInTheDocument();
  });

  // ── Loading state ──────────────────────────────────────────────────────

  it("shows loading skeleton while fetching", () => {
    renderPanel();
    expect(screen.getByTestId("risk-signals-loading")).toBeInTheDocument();
  });

  // ── Error state ────────────────────────────────────────────────────────

  it("shows error message on fetch failure", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: undefined,
      loading: false,
      error: makeError(),
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByTestId("risk-signals-error")).toBeInTheDocument();
    expect(screen.getByTestId("risk-signals-error")).toHaveTextContent(
      "Failed to load risk signals",
    );
  });

  // ── AI unavailable ─────────────────────────────────────────────────────

  it("shows unavailable message when aiAvailable=false", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: { aiAvailable: false, planId: "plan-abc", signals: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(
      screen.getByTestId("risk-signals-unavailable"),
    ).toBeInTheDocument();
  });

  it("does not show evidence toggle when unavailable", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: { aiAvailable: false, planId: "plan-abc", signals: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(
      screen.queryByTestId("risk-evidence-toggle"),
    ).not.toBeInTheDocument();
  });

  // ── Empty / clear state ────────────────────────────────────────────────

  it("shows clear message when no risk signals", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByTestId("risk-signals-clear")).toBeInTheDocument();
    expect(screen.getByTestId("risk-signals-clear")).toHaveTextContent(
      "No risk signals detected",
    );
  });

  it("does not show evidence toggle when no signals", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(
      screen.queryByTestId("risk-evidence-toggle"),
    ).not.toBeInTheDocument();
  });

  // ── Signal cards ───────────────────────────────────────────────────────

  it("renders signal card for each signal", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([
        makeSignal("s-1", "OVERCOMMIT", "Points exceed capacity."),
        makeSignal("s-2", "REPEATED_CARRY_FORWARD", "Three-week carry streak."),
      ]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByTestId("risk-signal-s-1")).toBeInTheDocument();
    expect(screen.getByTestId("risk-signal-s-2")).toBeInTheDocument();
  });

  it("renders signal rationale text", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "OVERCOMMIT", "Points exceed capacity.")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByTestId("risk-signal-s-1")).toHaveTextContent(
      "Points exceed capacity.",
    );
  });

  it("renders signal type badge", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "BLOCKED_CRITICAL")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByTestId("risk-signal-s-1")).toHaveTextContent(
      "BLOCKED_CRITICAL",
    );
  });

  // ── Signal count label ─────────────────────────────────────────────────

  it("shows singular label for 1 signal", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "OVERCOMMIT")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByTestId("risk-signals-list")).toHaveTextContent(
      "1 Risk Signal",
    );
  });

  it("shows plural label for multiple signals", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([
        makeSignal("s-1", "OVERCOMMIT"),
        makeSignal("s-2", "SCOPE_VOLATILITY"),
      ]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByTestId("risk-signals-list")).toHaveTextContent(
      "2 Risk Signals",
    );
  });

  // ── Evidence toggle ────────────────────────────────────────────────────

  it("shows evidence toggle button when signals present", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "OVERCOMMIT")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByTestId("risk-evidence-toggle")).toBeInTheDocument();
    expect(screen.getByTestId("risk-evidence-toggle")).toHaveTextContent(
      "Show evidence",
    );
  });

  it("shows loading skeleton while evidence is fetching", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "OVERCOMMIT")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.usePlanEvidence).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
    });

    renderPanel();
    fireEvent.click(screen.getByTestId("risk-evidence-toggle"));

    // Skeleton renders while loading — no drawer content yet
    expect(screen.queryByText("Facts")).not.toBeInTheDocument();
  });

  it("renders evidence drawer content when evidence loads", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "OVERCOMMIT")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.usePlanEvidence).mockReturnValue({
      data: { available: true, evidence: sampleEvidence },
      loading: false,
      error: null,
    });

    renderPanel();
    fireEvent.click(screen.getByTestId("risk-evidence-toggle"));

    // EvidenceDrawer renders SQL facts section
    expect(screen.getByText("Facts")).toBeInTheDocument();
  });

  it("changes toggle label to 'Hide evidence' when open", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "OVERCOMMIT")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel();
    fireEvent.click(screen.getByTestId("risk-evidence-toggle"));

    expect(screen.getByTestId("risk-evidence-toggle")).toHaveTextContent(
      "Hide evidence",
    );
  });

  it("hides evidence drawer when toggle clicked a second time", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "OVERCOMMIT")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.usePlanEvidence).mockReturnValue({
      data: { available: true, evidence: sampleEvidence },
      loading: false,
      error: null,
    });

    renderPanel();
    fireEvent.click(screen.getByTestId("risk-evidence-toggle")); // open
    fireEvent.click(screen.getByTestId("risk-evidence-toggle")); // close

    expect(screen.queryByText("Facts")).not.toBeInTheDocument();
    expect(screen.getByTestId("risk-evidence-toggle")).toHaveTextContent(
      "Show evidence",
    );
  });

  it("passes planId to usePlanEvidence only when toggle is open", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([makeSignal("s-1", "OVERCOMMIT")]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPanel("plan-xyz");

    // Before toggle: usePlanEvidence called with enabled=false
    expect(vi.mocked(aiHooks.usePlanEvidence)).toHaveBeenCalledWith(
      "plan-xyz",
      false,
    );

    fireEvent.click(screen.getByTestId("risk-evidence-toggle"));

    // After toggle: usePlanEvidence called with enabled=true
    expect(vi.mocked(aiHooks.usePlanEvidence)).toHaveBeenCalledWith(
      "plan-xyz",
      true,
    );
  });
});
