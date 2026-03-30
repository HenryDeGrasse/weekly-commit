/**
 * Tests for PreLockValidationPanel component.
 *
 * Covers:
 *   - Shows "all clear" state when no errors or warnings
 *   - Renders hard errors section when errors present
 *   - Lock is blocked when hard errors exist (no continue button)
 *   - Shows soft warnings (derived from commits)
 *   - Loading state
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { PreLockValidationPanel } from "../components/lock/PreLockValidationPanel.js";
import { AiLintPanel } from "../components/ai/AiLintPanel.js";
import type { LockValidationError, CommitResponse } from "../api/planTypes.js";
import { useAiStatus } from "../api/aiHooks.js";

// Mock the AI hooks so they don't make real API calls
vi.mock("../api/aiHooks.js", () => ({
  useAiApi: vi.fn(() => ({ commitLint: vi.fn(), getStatus: vi.fn() })),
  useAiStatus: vi.fn(() => ({ data: { available: false, providerName: "stub" }, loading: false, error: null })),
}));

function makeCommit(
  id: string,
  overrides: Partial<CommitResponse> = {},
): CommitResponse {
  return {
    id,
    planId: "plan-1",
    ownerUserId: "user-1",
    title: `Commit ${id}`,
    chessPiece: "PAWN",
    priorityOrder: 1,
    carryForwardStreak: 0,
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

const twoCommits: CommitResponse[] = [
  makeCommit("c-1", {
    chessPiece: "KING",
    estimatePoints: 3,
    rcdoNodeId: "r1",
    successCriteria: "Ship the critical deliverable",
  }),
  makeCommit("c-2", { chessPiece: "PAWN", estimatePoints: 2, rcdoNodeId: "r2" }),
];

function renderPanel(props: { errors: LockValidationError[]; commits: CommitResponse[]; isLoading?: boolean }) {
  return render(
    <MockHostProvider>
      <PreLockValidationPanel {...props} />
    </MockHostProvider>,
  );
}

describe("PreLockValidationPanel", () => {
  it("shows all-clear state when no errors and no warnings", () => {
    renderPanel({ errors: [], commits: twoCommits });
    expect(screen.getByTestId("pre-lock-validation-panel")).toBeInTheDocument();
    expect(screen.getByTestId("pre-lock-validation-ok")).toBeInTheDocument();
    expect(screen.queryByTestId("pre-lock-hard-errors")).not.toBeInTheDocument();
    expect(screen.queryByTestId("pre-lock-soft-warnings")).not.toBeInTheDocument();
  });

  it("shows loading state when isLoading=true", () => {
    renderPanel({ errors: [], commits: [], isLoading: true });
    expect(screen.getByTestId("pre-lock-validation-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("pre-lock-validation-ok")).not.toBeInTheDocument();
  });

  it("renders hard errors section with error items", () => {
    const errors: LockValidationError[] = [
      { field: "commit[c-1].rcdoNodeId", message: "Primary RCDO link is required" },
      { field: "commit[c-2].estimatePoints", message: "Estimate points are required" },
    ];
    render(<MockHostProvider><PreLockValidationPanel errors={errors} commits={twoCommits} /></MockHostProvider>);

    expect(screen.getByTestId("pre-lock-hard-errors")).toBeInTheDocument();
    const errorItems = screen.getAllByTestId("hard-error-item");
    expect(errorItems).toHaveLength(2);
    expect(errorItems[0]).toHaveTextContent("Primary RCDO link is required");
    expect(errorItems[1]).toHaveTextContent("Estimate points are required");
  });

  it("does not show all-clear when hard errors present", () => {
    const errors: LockValidationError[] = [
      { field: "commits", message: "At least one commit is required" },
    ];
    render(<MockHostProvider><PreLockValidationPanel errors={errors} commits={[]} /></MockHostProvider>);
    expect(screen.queryByTestId("pre-lock-validation-ok")).not.toBeInTheDocument();
  });

  it("shows soft warning for >8 commits", () => {
    const manyCommits = Array.from({ length: 9 }, (_, i) =>
      makeCommit(`c-${i}`, { rcdoNodeId: `r${i}`, estimatePoints: 1 }),
    );
    render(<MockHostProvider><PreLockValidationPanel errors={[]} commits={manyCommits} /></MockHostProvider>);
    expect(screen.getByTestId("pre-lock-soft-warnings")).toBeInTheDocument();
    const items = screen.getAllByTestId("soft-warning-item");
    expect(items.some((el) => el.textContent?.includes("9 commits"))).toBe(true);
  });

  it("shows soft warning for pawn-heavy commits (>40%)", () => {
    const commits: CommitResponse[] = [
      makeCommit("c-1", {
        chessPiece: "KING",
        estimatePoints: 2,
        rcdoNodeId: "r1",
        successCriteria: "Complete the must-win goal",
      }),
      makeCommit("c-2", { chessPiece: "PAWN", estimatePoints: 8, rcdoNodeId: "r2" }),
    ];
    render(<MockHostProvider><PreLockValidationPanel errors={[]} commits={commits} /></MockHostProvider>);
    expect(screen.getByTestId("pre-lock-soft-warnings")).toBeInTheDocument();
    const items = screen.getAllByTestId("soft-warning-item");
    expect(items.some((el) => el.textContent?.includes("80%"))).toBe(true);
  });

  it("shows hard error for commits missing RCDO", () => {
    const commits: CommitResponse[] = [
      makeCommit("c-1", { chessPiece: "KING", estimatePoints: 3 }), // no rcdoNodeId
    ];
    render(<MockHostProvider><PreLockValidationPanel errors={[]} commits={commits} /></MockHostProvider>);
    expect(screen.getByTestId("pre-lock-hard-errors")).toBeInTheDocument();
    expect(screen.getByText(/Primary RCDO link is required at lock time/)).toBeInTheDocument();
  });

  it("renders both hard errors and soft warnings simultaneously", () => {
    const errors: LockValidationError[] = [
      { field: "commits.king", message: "Maximum 1 King commit per week" },
    ];
    const commits = Array.from({ length: 9 }, (_, i) =>
      makeCommit(`c-${i}`, { rcdoNodeId: `r${i}`, estimatePoints: 1 }),
    );
    render(<MockHostProvider><PreLockValidationPanel errors={errors} commits={commits} /></MockHostProvider>);
    expect(screen.getByTestId("pre-lock-hard-errors")).toBeInTheDocument();
    expect(screen.getByTestId("pre-lock-soft-warnings")).toBeInTheDocument();
  });

  it("does not render the AI lint panel", () => {
    renderPanel({ errors: [], commits: twoCommits });
    expect(screen.queryByTestId("ai-lint-unavailable")).not.toBeInTheDocument();
    expect(screen.queryByTestId("ai-lint-run-btn")).not.toBeInTheDocument();
  });
});

// ── AiLintPanel direct unit tests (manual vs auto mode) ──────────────────────

describe("AiLintPanel — autoRun=false (manual mode)", () => {
  it("shows the Run button when AI is available and autoRun is false", () => {
    // Override the status mock to say AI is available
    vi.mocked(useAiStatus).mockReturnValueOnce({
      data: { available: true, aiEnabled: true, providerName: "openrouter", providerVersion: "1" },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <MockHostProvider>
        <AiLintPanel planId="plan-1" userId="user-1" />
      </MockHostProvider>,
    );

    // Manual mode (autoRun=false): the button must be present
    expect(screen.getByTestId("ai-lint-run-btn")).toBeInTheDocument();
    expect(screen.queryByTestId("ai-lint-unavailable")).not.toBeInTheDocument();
  });

  it("shows unavailable state when AI is unavailable and autoRun is false", () => {
    // Default mock already returns available=false
    render(
      <MockHostProvider>
        <AiLintPanel planId="plan-1" userId="user-1" />
      </MockHostProvider>,
    );
    expect(screen.getByTestId("ai-lint-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("ai-lint-run-btn")).not.toBeInTheDocument();
  });
});

describe("AiLintPanel — autoRun=true (automatic mode)", () => {
  it("does not show the Run button when autoRun=true and AI is unavailable", () => {
    // Default mock: available=false
    render(
      <MockHostProvider>
        <AiLintPanel planId="plan-1" userId="user-1" autoRun />
      </MockHostProvider>,
    );
    // Unavailable state shows immediately — no button
    expect(screen.getByTestId("ai-lint-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("ai-lint-run-btn")).not.toBeInTheDocument();
  });

  it("calls commitLint automatically when AI is available and autoRun=true", async () => {
    const mockCommitLint = vi.fn().mockResolvedValue({
      aiAvailable: true,
      hardValidation: [],
      softGuidance: [{ code: "VAGUE_TITLE", message: "Title could be more specific" }],
    });

    vi.mocked(useAiStatus).mockReturnValue({
      data: { available: true, aiEnabled: true, providerName: "openrouter", providerVersion: "1" },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { useAiApi } = await import("../api/aiHooks.js");
    vi.mocked(useAiApi).mockReturnValue({ commitLint: mockCommitLint, getRiskSignals: vi.fn(), getStatus: vi.fn(), commitDraftAssist: vi.fn(), commitFromFreeform: vi.fn(), reconcileAssist: vi.fn(), recordFeedback: vi.fn(), getTeamAiSummary: vi.fn(), rcdoSuggest: vi.fn() });

    render(
      <MockHostProvider>
        <AiLintPanel planId="plan-1" userId="user-1" autoRun />
      </MockHostProvider>,
    );

    // commitLint should have been called automatically (no button click)
    await waitFor(() => expect(mockCommitLint).toHaveBeenCalledWith({ userId: "user-1", planId: "plan-1" }));

    // Results panel should appear with soft guidance
    await waitFor(() => expect(screen.getByTestId("ai-lint-soft")).toBeInTheDocument());
  });
});
