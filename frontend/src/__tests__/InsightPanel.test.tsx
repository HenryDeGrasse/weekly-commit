/**
 * Tests for InsightPanel component.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { InsightPanel } from "../components/ai/InsightPanel.js";
import type { InsightListResponse } from "../api/ragApi.js";
import { ApiRequestError } from "../api/client.js";

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock("../api/ragHooks.js", () => ({
  useTeamInsights: vi.fn(() => ({
    data: undefined,
    loading: true,
    error: null,
    refetch: vi.fn(),
  })),
  usePlanInsights: vi.fn(() => ({
    data: undefined,
    loading: true,
    error: null,
    refetch: vi.fn(),
  })),
  useSemanticQuery: vi.fn(),
  useRagApi: vi.fn(),
}));

vi.mock("../api/aiHooks.js", () => ({
  useAiApi: vi.fn(() => ({ recordFeedback: vi.fn() })),
  useAiStatus: vi.fn(() => ({
    data: { available: false },
    loading: false,
    error: null,
  })),
  usePlanEvidence: vi.fn(() => ({ data: undefined, loading: false, error: null })),
}));

import * as ragHooks from "../api/ragHooks.js";
import * as aiHooks from "../api/aiHooks.js";

// ── Sample data ───────────────────────────────────────────────────────────────

const sampleInsight1 = {
  suggestionId: "s-1",
  insightText: "Team shows high carry-forward rate.",
  severity: "HIGH",
  sourceEntityIds: ["plan-abc"],
  actionSuggestion: "Review team capacity.",
  createdAt: "2025-01-06T08:00:00Z",
};

const sampleInsight2 = {
  suggestionId: "s-2",
  insightText: "Scope volatility detected post-lock.",
  severity: "MEDIUM",
  sourceEntityIds: [],
  actionSuggestion: "Stabilise scope earlier in the week.",
  createdAt: "2025-01-06T08:01:00Z",
};

const sampleInsight3 = {
  suggestionId: "s-3",
  insightText: "Good KING commit delivery.",
  severity: "LOW",
  sourceEntityIds: [],
  actionSuggestion: "Keep it up!",
  createdAt: "2025-01-06T08:02:00Z",
};

function available(insights = [sampleInsight1, sampleInsight2]): InsightListResponse {
  return { aiAvailable: true, insights };
}

function makeError(): ApiRequestError {
  return new ApiRequestError({ status: 500, message: "Server error" });
}

// ── Render helpers ────────────────────────────────────────────────────────────

function renderTeam(teamId = "team-1", weekStart = "2025-01-06") {
  return render(
    <MockHostProvider>
      <InsightPanel mode="team" teamId={teamId} weekStart={weekStart} />
    </MockHostProvider>,
  );
}

function renderPersonal(planId = "plan-1") {
  return render(
    <MockHostProvider>
      <InsightPanel mode="personal" planId={planId} />
    </MockHostProvider>,
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("InsightPanel", () => {
  beforeEach(() => {
    // Reset to default (loading) state
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
  });

  // ── Outer wrapper ──────────────────────────────────────────────────────

  it("always renders the insight-panel wrapper", () => {
    renderTeam();
    expect(screen.getByTestId("insight-panel")).toBeInTheDocument();
  });

  // ── Loading state ──────────────────────────────────────────────────────

  it("shows skeleton loading state for team mode", () => {
    renderTeam();
    expect(screen.getByTestId("insight-panel-loading")).toBeInTheDocument();
  });

  it("shows skeleton loading state for personal mode", () => {
    renderPersonal();
    expect(screen.getByTestId("insight-panel-loading")).toBeInTheDocument();
  });

  // ── Team mode: insight cards ───────────────────────────────────────────

  it("renders insight cards for team mode", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: available(),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.getByTestId("insight-card-s-1")).toBeInTheDocument();
    expect(screen.getByTestId("insight-card-s-2")).toBeInTheDocument();
  });

  it("renders insight text for team mode", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: available(),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.getByTestId("insight-text-s-1")).toHaveTextContent(
      "Team shows high carry-forward rate.",
    );
  });

  it("renders action suggestion for team mode", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: available(),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.getByTestId("insight-action-s-1")).toHaveTextContent(
      "Review team capacity.",
    );
  });

  // ── Personal mode: insight cards ───────────────────────────────────────

  it("renders insight cards for personal mode", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: available([sampleInsight3]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPersonal();

    expect(screen.getByTestId("insight-card-s-3")).toBeInTheDocument();
    expect(screen.getByTestId("insight-text-s-3")).toHaveTextContent(
      "Good KING commit delivery.",
    );
  });

  // ── Severity badges ────────────────────────────────────────────────────

  it("renders HIGH severity badge", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: available([sampleInsight1]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.getByTestId("insight-severity-s-1")).toHaveTextContent("High");
  });

  it("renders MEDIUM severity badge", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: available([sampleInsight2]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.getByTestId("insight-severity-s-2")).toHaveTextContent(
      "Medium",
    );
  });

  it("renders LOW severity badge", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: available([sampleInsight3]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPersonal();

    expect(screen.getByTestId("insight-severity-s-3")).toHaveTextContent("Low");
  });

  // ── Source entity IDs are internal — not rendered to users ───────────────

  it("does not render raw source entity ID UUIDs", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: available([sampleInsight1]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.queryByTestId("insight-sources-s-1")).not.toBeInTheDocument();
    expect(screen.queryByText("plan-abc")).not.toBeInTheDocument();
  });

  // ── AiFeedbackButtons ──────────────────────────────────────────────────

  it("renders AI feedback buttons for each insight card", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: available([sampleInsight1, sampleInsight2]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    // Each card should have accept + dismiss buttons
    const acceptBtns = screen.getAllByTestId("ai-feedback-accept");
    const dismissBtns = screen.getAllByTestId("ai-feedback-dismiss");
    expect(acceptBtns).toHaveLength(2);
    expect(dismissBtns).toHaveLength(2);
  });

  // ── Empty state ────────────────────────────────────────────────────────

  it("shows empty state when no insights returned (team)", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.getByTestId("insight-panel-empty")).toBeInTheDocument();
    expect(screen.getByTestId("insight-panel-empty")).toHaveTextContent(
      "No AI insights available for this period.",
    );
  });

  it("renders empty state message when no insights returned (personal)", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPersonal();

    expect(screen.getByTestId("insight-panel-empty")).toBeInTheDocument();
    expect(screen.getByTestId("insight-panel-empty")).toHaveTextContent(
      "No insights available for this period.",
    );
  });

  // ── AI unavailable ─────────────────────────────────────────────────────

  it("shows unavailable message when aiAvailable=false (team)", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: { aiAvailable: false, insights: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(
      screen.getByTestId("insight-panel-unavailable"),
    ).toBeInTheDocument();
  });

  it("renders nothing when aiAvailable=false (personal)", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: false, insights: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPersonal();

    expect(
      screen.queryByTestId("insight-panel-unavailable"),
    ).not.toBeInTheDocument();
  });

  it("does not render cards when AI unavailable", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: { aiAvailable: false, insights: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.queryByTestId("insight-card-s-1")).not.toBeInTheDocument();
  });

  // ── Error state ────────────────────────────────────────────────────────

  it("shows error message on fetch failure (team)", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: undefined,
      loading: false,
      error: makeError(),
      refetch: vi.fn(),
    });

    renderTeam();

    expect(screen.getByTestId("insight-panel-error")).toBeInTheDocument();
    expect(screen.getByTestId("insight-panel-error")).toHaveTextContent(
      "Failed to load insights",
    );
  });

  it("shows error message on fetch failure (personal)", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: undefined,
      loading: false,
      error: makeError(),
      refetch: vi.fn(),
    });

    renderPersonal();

    expect(screen.getByTestId("insight-panel-error")).toBeInTheDocument();
  });

  // ── Evidence toggle (personal mode only) ──────────────────────────────

  it("shows evidence toggle button when personal insights are loaded", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [sampleInsight1] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPersonal("plan-1");

    expect(screen.getByTestId("insight-evidence-toggle")).toBeInTheDocument();
    expect(screen.getByTestId("insight-evidence-toggle")).toHaveTextContent(
      "Show evidence",
    );
  });

  it("does not show evidence toggle for team mode", () => {
    vi.mocked(ragHooks.useTeamInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [sampleInsight1] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderTeam();

    expect(
      screen.queryByTestId("insight-evidence-toggle"),
    ).not.toBeInTheDocument();
  });

  it("does not show evidence toggle when no planId provided", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [sampleInsight1] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <MockHostProvider>
        <InsightPanel mode="personal" />
      </MockHostProvider>,
    );

    expect(
      screen.queryByTestId("insight-evidence-toggle"),
    ).not.toBeInTheDocument();
  });

  it("shows loading skeleton while evidence is fetching", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [sampleInsight1] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.usePlanEvidence).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
    });

    renderPersonal("plan-1");
    fireEvent.click(screen.getByTestId("insight-evidence-toggle"));

    // Skeleton renders while loading — drawer content not yet visible
    expect(screen.queryByText("Facts")).not.toBeInTheDocument();
  });

  it("renders evidence drawer content when evidence loads", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [sampleInsight1] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.usePlanEvidence).mockReturnValue({
      data: {
        available: true,
        evidence: {
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
            chessDistribution: { KING: 1 },
          },
          lineage: null,
          semanticMatches: [],
          riskFeatures: null,
        },
      },
      loading: false,
      error: null,
    });

    renderPersonal("plan-1");
    fireEvent.click(screen.getByTestId("insight-evidence-toggle"));

    // EvidenceDrawer renders SQL facts section header
    expect(screen.getByText("Facts")).toBeInTheDocument();
  });

  it("changes toggle label to 'Hide evidence' when open", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [sampleInsight1] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPersonal("plan-1");
    fireEvent.click(screen.getByTestId("insight-evidence-toggle"));

    expect(screen.getByTestId("insight-evidence-toggle")).toHaveTextContent(
      "Hide evidence",
    );
  });

  it("hides evidence drawer when toggle clicked a second time", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [sampleInsight1] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.usePlanEvidence).mockReturnValue({
      data: {
        available: true,
        evidence: {
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
            chessDistribution: {},
          },
          lineage: null,
          semanticMatches: [],
          riskFeatures: null,
        },
      },
      loading: false,
      error: null,
    });

    renderPersonal("plan-1");
    fireEvent.click(screen.getByTestId("insight-evidence-toggle")); // open
    fireEvent.click(screen.getByTestId("insight-evidence-toggle")); // close

    expect(screen.queryByText("Facts")).not.toBeInTheDocument();
    expect(screen.getByTestId("insight-evidence-toggle")).toHaveTextContent(
      "Show evidence",
    );
  });

  it("passes planId to usePlanEvidence only when toggle is open", () => {
    vi.mocked(ragHooks.usePlanInsights).mockReturnValue({
      data: { aiAvailable: true, insights: [sampleInsight1] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPersonal("plan-42");

    // Before toggle: usePlanEvidence called with enabled=false
    expect(vi.mocked(aiHooks.usePlanEvidence)).toHaveBeenCalledWith(
      "plan-42",
      false,
    );

    fireEvent.click(screen.getByTestId("insight-evidence-toggle"));

    // After toggle: usePlanEvidence called with enabled=true
    expect(vi.mocked(aiHooks.usePlanEvidence)).toHaveBeenCalledWith(
      "plan-42",
      true,
    );
  });
});
