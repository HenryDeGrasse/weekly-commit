/**
 * Unit tests for the ManagerAiSummaryCard component.
 *
 * Covers:
 *   - Loading skeleton state
 *   - Error state
 *   - AI unavailable state (aiAvailable=false)
 *   - Full data rendering: summary text, RCDO branches, carry-forward patterns,
 *     unresolved exception count, critical blocked item count
 *   - AiFeedbackButtons presence when suggestionId is provided
 *   - Partial data (empty lists) renders without crashing
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { ManagerAiSummaryCard } from "../components/ai/ManagerAiSummaryCard.js";
import type { ManagerAiSummaryResponse } from "../api/aiApi.js";

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock("../api/aiHooks.js", () => ({
  useAiApi: vi.fn(() => ({ recordFeedback: vi.fn() })),
  useManagerAiSummary: vi.fn(),
}));

import { useManagerAiSummary } from "../api/aiHooks.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

const fullSummary: ManagerAiSummaryResponse = {
  aiAvailable: true,
  suggestionId: "suggestion-abc-123",
  teamId: "team-1",
  weekStart: "2026-03-24",
  summaryText:
    "The team committed 48 points across 3 members this week. Focus is on Enterprise Sales with strong King coverage.",
  topRcdoBranches: ["Enterprise Sales", "Product Growth"],
  unresolvedExceptionIds: ["exc-1", "exc-2", "exc-3"],
  carryForwardPatterns: [
    "Alice has carried forward 'Integration testing' for 2 consecutive weeks.",
    "Bob has 1 item carried forward.",
  ],
  criticalBlockedItemIds: ["ticket-king-1"],
  modelVersion: "openai/gpt-4o",
};

function renderCard(props: { teamId?: string; weekStart?: string } = {}) {
  return render(
    <MockHostProvider>
      <ManagerAiSummaryCard
        teamId={props.teamId ?? "team-1"}
        weekStart={props.weekStart ?? "2026-03-24"}
      />
    </MockHostProvider>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("ManagerAiSummaryCard — loading state", () => {
  it("renders skeleton while loading", () => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
    renderCard();
    expect(screen.getByTestId("manager-ai-summary-loading")).toBeInTheDocument();
  });
});

describe("ManagerAiSummaryCard — error state", () => {
  it("renders error message when request fails", () => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: undefined,
      loading: false,
      error: {
        name: "ApiRequestError",
        message: "Network error",
        status: 0,
      } as never,
      refetch: vi.fn(),
    });
    renderCard();
    const el = screen.getByTestId("manager-ai-summary-error");
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent("Network error");
  });
});

describe("ManagerAiSummaryCard — AI unavailable", () => {
  it("renders unavailable message when aiAvailable=false", () => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: {
        aiAvailable: false,
        teamId: "team-1",
        weekStart: "2026-03-24",
        topRcdoBranches: [],
        unresolvedExceptionIds: [],
        carryForwardPatterns: [],
        criticalBlockedItemIds: [],
      },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderCard();
    expect(screen.getByTestId("manager-ai-summary-unavailable")).toBeInTheDocument();
  });

  it("renders unavailable message when data is undefined and not loading", () => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: undefined,
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderCard();
    // No error, not loading, no data — shows unavailable
    expect(screen.getByTestId("manager-ai-summary-unavailable")).toBeInTheDocument();
  });
});

describe("ManagerAiSummaryCard — full data rendering", () => {
  beforeEach(() => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: fullSummary,
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
  });

  it("renders the summary card", () => {
    renderCard();
    expect(screen.getByTestId("manager-ai-summary-card")).toBeInTheDocument();
  });

  it("renders the AI-generated summary text", () => {
    renderCard();
    const el = screen.getByTestId("manager-ai-summary-text");
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent("The team committed 48 points");
  });

  it("renders top RCDO branches as badges", () => {
    renderCard();
    const el = screen.getByTestId("manager-ai-summary-rcdo-branches");
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent("Enterprise Sales");
    expect(el).toHaveTextContent("Product Growth");
  });

  it("renders carry-forward patterns as a list", () => {
    renderCard();
    const el = screen.getByTestId("manager-ai-summary-carry-forward");
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent("Alice has carried forward");
    expect(el).toHaveTextContent("Bob has 1 item carried forward");
  });

  it("renders unresolved exception count with correct number", () => {
    renderCard();
    const el = screen.getByTestId("manager-ai-summary-exception-count");
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent("3 unresolved exceptions");
  });

  it("renders critical blocked item count with correct number", () => {
    renderCard();
    const el = screen.getByTestId("manager-ai-summary-blocked-count");
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent("1 critical blocked item");
  });

  it("renders singular form for 1 exception", () => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: { ...fullSummary, unresolvedExceptionIds: ["exc-1"] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderCard();
    expect(screen.getByTestId("manager-ai-summary-exception-count")).toHaveTextContent(
      "1 unresolved exception",
    );
  });

  it("renders feedback buttons when suggestionId is provided", () => {
    renderCard();
    expect(screen.getByTestId("ai-feedback-accept")).toBeInTheDocument();
    expect(screen.getByTestId("ai-feedback-dismiss")).toBeInTheDocument();
  });
});

describe("ManagerAiSummaryCard — partial data (empty lists)", () => {
  it("renders without RCDO section when topRcdoBranches is empty", () => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: { ...fullSummary, topRcdoBranches: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderCard();
    expect(screen.queryByTestId("manager-ai-summary-rcdo-branches")).not.toBeInTheDocument();
  });

  it("renders without carry-forward section when carryForwardPatterns is empty", () => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: { ...fullSummary, carryForwardPatterns: [] },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderCard();
    expect(screen.queryByTestId("manager-ai-summary-carry-forward")).not.toBeInTheDocument();
  });

  it("renders without warnings section when no exceptions or blocked items", () => {
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: {
        ...fullSummary,
        unresolvedExceptionIds: [],
        criticalBlockedItemIds: [],
      },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderCard();
    expect(screen.queryByTestId("manager-ai-summary-warnings")).not.toBeInTheDocument();
  });

  it("does not render feedback buttons when suggestionId is absent", () => {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { suggestionId: _removed, ...withoutSuggestionId } = fullSummary;
    vi.mocked(useManagerAiSummary).mockReturnValue({
      data: withoutSuggestionId,
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderCard();
    expect(screen.queryByTestId("ai-feedback-accept")).not.toBeInTheDocument();
  });
});
