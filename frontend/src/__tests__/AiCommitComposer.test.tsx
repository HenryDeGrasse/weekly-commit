/**
 * Tests for the AiCommitComposer component.
 *
 * Covers:
 *   - Phase 1: freeform textarea renders; Generate button disabled when input empty
 *   - Phase 1: Generate button disabled when AI unavailable
 *   - Phase 1: 'Switch to manual form' link calls onSwitchToManual
 *   - Phase 2: AI draft displayed (title, chess piece, RCDO, estimate, success criteria)
 *   - Phase 2: Accept & Create calls onSubmit with AI-suggested values
 *   - Phase 2: Chess piece limit warning when AI suggests a blocked piece
 *   - Phase 2: Clear RCDO removes the RCDO field
 *   - Phase 2: 'Switch to manual form' button passes pre-filled values
 *   - Phase 2: Try again resets to Phase 1
 *   - Error state: generate failure shows error message
 *   - Error state: submit failure shows error message
 *   - AI unavailable: generate button disabled with aria title
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { AiCommitComposer } from "../components/ai/AiCommitComposer.js";
import type { CommitResponse, CreateCommitPayload } from "../api/planTypes.js";

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock("../api/aiHooks.js", () => ({
  useAiApi: vi.fn(),
  useAiStatus: vi.fn(),
  useAutoReconcileAssist: vi.fn(() => ({ data: undefined, loading: false, error: null })),
  useManagerAiSummary: vi.fn(() => ({ data: undefined, loading: false, error: null })),
}));

import * as aiHooks from "../api/aiHooks.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makeCommit(id: string, overrides: Partial<CommitResponse> = {}): CommitResponse {
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

const DRAFT_ASSIST_RESPONSE = {
  aiAvailable: true,
  suggestionId: "sugg-draft-1",
  suggestedTitle: "Migrate auth service to OAuth 2.0",
  suggestedDescription: "Critical for enterprise launch",
  suggestedSuccessCriteria: "All enterprise customers can authenticate via OAuth 2.0",
  suggestedEstimatePoints: 5,
  suggestedChessPiece: "KING",
  rationale: "Mission-critical work for next week's launch.",
};

const RCDO_SUGGEST_RESPONSE = {
  aiAvailable: true,
  suggestionAvailable: true,
  suggestionId: "sugg-rcdo-1",
  suggestedRcdoNodeId: "rcdo-node-1",
  rcdoTitle: "Enterprise Growth > Auth Modernization",
  confidence: 0.87,
  rationale: "Keyword match on auth service modernization",
};

const MOCK_AI_API = {
  commitDraftAssist: vi.fn().mockResolvedValue(DRAFT_ASSIST_RESPONSE),
  commitFromFreeform: vi.fn().mockResolvedValue(DRAFT_ASSIST_RESPONSE),
  rcdoSuggest: vi.fn().mockResolvedValue(RCDO_SUGGEST_RESPONSE),
  recordFeedback: vi.fn().mockResolvedValue(undefined),
  getStatus: vi.fn(),
  commitLint: vi.fn(),
  getRiskSignals: vi.fn(),
  reconcileAssist: vi.fn(),
  getTeamAiSummary: vi.fn(),
};

// ── Render helper ─────────────────────────────────────────────────────────────

function renderComposer(opts: {
  existingCommits?: CommitResponse[];
  onSubmit?: (payload: CreateCommitPayload) => Promise<void>;
  onSwitchToManual?: (preFilled: Partial<CreateCommitPayload>) => void;
  onCancel?: () => void;
} = {}) {
  const onSubmit = opts.onSubmit ?? vi.fn().mockResolvedValue(undefined);
  const onSwitchToManual = opts.onSwitchToManual ?? vi.fn();
  const onCancel = opts.onCancel ?? vi.fn();

  render(
    <MockHostProvider>
      <AiCommitComposer
        planId="plan-1"
        existingCommits={opts.existingCommits ?? []}
        onSubmit={onSubmit}
        onSwitchToManual={onSwitchToManual}
        onCancel={onCancel}
      />
    </MockHostProvider>,
  );

  return { onSubmit, onSwitchToManual, onCancel };
}

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.mocked(aiHooks.useAiApi).mockReturnValue(
    MOCK_AI_API as ReturnType<typeof aiHooks.useAiApi>,
  );
  vi.mocked(aiHooks.useAiStatus).mockReturnValue({
    data: {
      available: true,
      aiEnabled: true,
      providerName: "openrouter",
      providerVersion: "1",
    },
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  vi.mocked(MOCK_AI_API.commitDraftAssist).mockResolvedValue(DRAFT_ASSIST_RESPONSE);
  vi.mocked(MOCK_AI_API.commitFromFreeform).mockResolvedValue(DRAFT_ASSIST_RESPONSE);
  vi.mocked(MOCK_AI_API.rcdoSuggest).mockResolvedValue(RCDO_SUGGEST_RESPONSE);
});

// ── Tests: dialog and close ───────────────────────────────────────────────────

describe("AiCommitComposer — dialog", () => {
  it("renders with the ai-commit-composer testid", () => {
    renderComposer();
    expect(screen.getByTestId("ai-commit-composer")).toBeInTheDocument();
  });

  it("has role=dialog and aria-label", () => {
    renderComposer();
    expect(screen.getByRole("dialog")).toHaveAttribute(
      "aria-label",
      "AI Commit Composer",
    );
  });

  it("calls onCancel when close button is clicked", () => {
    const { onCancel } = renderComposer();
    fireEvent.click(screen.getByTestId("ai-composer-close-btn"));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});

// ── Tests: Phase 1 freeform input ─────────────────────────────────────────────

describe("AiCommitComposer — Phase 1 freeform input", () => {
  it("renders the freeform textarea", () => {
    renderComposer();
    expect(screen.getByTestId("ai-composer-freeform-input")).toBeInTheDocument();
  });

  it("renders the Generate Commit button", () => {
    renderComposer();
    expect(screen.getByTestId("ai-composer-generate-btn")).toBeInTheDocument();
  });

  it("Generate button is disabled when textarea is empty", () => {
    renderComposer();
    expect(screen.getByTestId("ai-composer-generate-btn")).toBeDisabled();
  });

  it("Generate button is enabled when textarea has content and AI is available", () => {
    renderComposer();
    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Working on auth migration" },
    });
    expect(screen.getByTestId("ai-composer-generate-btn")).not.toBeDisabled();
  });

  it("Generate button is disabled when AI is unavailable even with text", () => {
    vi.mocked(aiHooks.useAiStatus).mockReturnValue({
      data: { available: false, aiEnabled: true, providerName: "stub", providerVersion: "0" },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderComposer();
    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Working on auth migration" },
    });
    expect(screen.getByTestId("ai-composer-generate-btn")).toBeDisabled();
  });

  it("shows 'Switch to manual form' link", () => {
    renderComposer();
    expect(screen.getByTestId("ai-composer-switch-manual-link")).toBeInTheDocument();
  });

  it("calls onSwitchToManual when 'Switch to manual form' link is clicked", () => {
    const { onSwitchToManual } = renderComposer();
    fireEvent.click(screen.getByTestId("ai-composer-switch-manual-link"));
    expect(onSwitchToManual).toHaveBeenCalledTimes(1);
  });
});

// ── Tests: Phase 2 AI draft ───────────────────────────────────────────────────

describe("AiCommitComposer — Phase 2 AI draft", () => {
  async function renderAndGenerate(opts = {}) {
    const callbacks = renderComposer(opts);
    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Migrate auth service to OAuth 2.0 tokens for enterprise customers" },
    });
    fireEvent.click(screen.getByTestId("ai-composer-generate-btn"));
    await waitFor(() =>
      expect(screen.getByTestId("ai-composer-draft")).toBeInTheDocument(),
    );
    return callbacks;
  }

  it("shows the AI draft panel after generation", async () => {
    await renderAndGenerate();
    expect(screen.getByTestId("ai-composer-draft")).toBeInTheDocument();
  });

  it("shows the AI-suggested title in an editable field", async () => {
    await renderAndGenerate();
    const titleField = screen.getByTestId(
      "ai-composer-title-field",
    ) as HTMLInputElement;
    expect(titleField.value).toBe(DRAFT_ASSIST_RESPONSE.suggestedTitle);
  });

  it("shows the AI-suggested chess piece in a select", async () => {
    await renderAndGenerate();
    const select = screen.getByTestId(
      "ai-composer-chess-piece-select",
    ) as HTMLSelectElement;
    expect(select.value).toBe(DRAFT_ASSIST_RESPONSE.suggestedChessPiece);
  });

  it("shows the AI-suggested estimate as an active button", async () => {
    await renderAndGenerate();
    const estimateBtn = screen.getByTestId(
      `ai-composer-estimate-${DRAFT_ASSIST_RESPONSE.suggestedEstimatePoints}`,
    );
    expect(estimateBtn).toHaveAttribute("aria-pressed", "true");
  });

  it("shows the AI-suggested RCDO title", async () => {
    await renderAndGenerate();
    expect(screen.getByTestId("ai-composer-rcdo-title")).toHaveTextContent(
      RCDO_SUGGEST_RESPONSE.rcdoTitle,
    );
  });

  it("shows the AI-suggested success criteria in an editable textarea", async () => {
    await renderAndGenerate();
    const criteria = screen.getByTestId(
      "ai-composer-criteria-field",
    ) as HTMLTextAreaElement;
    expect(criteria.value).toBe(DRAFT_ASSIST_RESPONSE.suggestedSuccessCriteria);
  });

  it("Accept & Create button is enabled when draft is ready", async () => {
    await renderAndGenerate();
    expect(screen.getByTestId("ai-composer-accept-btn")).not.toBeDisabled();
  });

  it("calls onSubmit with AI-suggested values when Accept & Create is clicked", async () => {
    const { onSubmit } = await renderAndGenerate();
    fireEvent.click(screen.getByTestId("ai-composer-accept-btn"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        title: DRAFT_ASSIST_RESPONSE.suggestedTitle,
        chessPiece: DRAFT_ASSIST_RESPONSE.suggestedChessPiece,
        estimatePoints: DRAFT_ASSIST_RESPONSE.suggestedEstimatePoints,
        rcdoNodeId: RCDO_SUGGEST_RESPONSE.suggestedRcdoNodeId,
        successCriteria: DRAFT_ASSIST_RESPONSE.suggestedSuccessCriteria,
      }),
    );
  });

  it("calls onSwitchToManual with pre-filled values when 'Switch to manual form' is clicked", async () => {
    const { onSwitchToManual } = await renderAndGenerate();
    fireEvent.click(screen.getByTestId("ai-composer-switch-manual-btn"));
    expect(onSwitchToManual).toHaveBeenCalledTimes(1);
    expect(onSwitchToManual).toHaveBeenCalledWith(
      expect.objectContaining({
        title: DRAFT_ASSIST_RESPONSE.suggestedTitle,
        chessPiece: DRAFT_ASSIST_RESPONSE.suggestedChessPiece,
        estimatePoints: DRAFT_ASSIST_RESPONSE.suggestedEstimatePoints,
        rcdoNodeId: RCDO_SUGGEST_RESPONSE.suggestedRcdoNodeId,
      }),
    );
  });

  it("clears RCDO suggestion when Clear RCDO button is clicked", async () => {
    await renderAndGenerate();
    expect(screen.getByTestId("ai-composer-rcdo-title")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("ai-composer-clear-rcdo"));
    expect(screen.queryByTestId("ai-composer-rcdo-title")).not.toBeInTheDocument();
  });

  it("resets to Phase 1 when 'Try again' is clicked", async () => {
    await renderAndGenerate();
    expect(screen.getByTestId("ai-composer-draft")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("ai-composer-regenerate-btn"));
    expect(screen.queryByTestId("ai-composer-draft")).not.toBeInTheDocument();
    expect(screen.getByTestId("ai-composer-freeform-input")).toBeInTheDocument();
  });
});

// ── Tests: chess piece limit warning ─────────────────────────────────────────

describe("AiCommitComposer — chess piece limit warning", () => {
  it("shows warning when AI suggests KING but one already exists", async () => {
    const existingKing = makeCommit("c-king", { chessPiece: "KING" });
    renderComposer({ existingCommits: [existingKing] });

    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Critical auth work" },
    });
    fireEvent.click(screen.getByTestId("ai-composer-generate-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("ai-composer-draft")).toBeInTheDocument(),
    );

    // DRAFT_ASSIST_RESPONSE suggests KING, existingKing already uses it → warning
    expect(screen.getByTestId("ai-composer-chess-warning")).toBeInTheDocument();
    expect(screen.getByTestId("ai-composer-chess-warning")).toHaveTextContent(
      "Max 1 King already used this week",
    );
  });

  it("disables Accept & Create button when chess piece is at limit", async () => {
    const existingKing = makeCommit("c-king", { chessPiece: "KING" });
    renderComposer({ existingCommits: [existingKing] });

    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Critical auth work" },
    });
    fireEvent.click(screen.getByTestId("ai-composer-generate-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("ai-composer-draft")).toBeInTheDocument(),
    );

    expect(screen.getByTestId("ai-composer-accept-btn")).toBeDisabled();
  });
});

// ── Tests: error states ───────────────────────────────────────────────────────

describe("AiCommitComposer — error states", () => {
  it("shows error message when generate fails", async () => {
    vi.mocked(MOCK_AI_API.commitFromFreeform).mockRejectedValue(
      new Error("Network error"),
    );
    renderComposer();
    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Some work description" },
    });
    fireEvent.click(screen.getByTestId("ai-composer-generate-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("ai-composer-error")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("ai-composer-error")).toHaveTextContent(
      "Failed to generate commit",
    );
  });

  it("shows error when AI is unavailable (aiAvailable=false response)", async () => {
    vi.mocked(MOCK_AI_API.commitFromFreeform).mockResolvedValue({
      aiAvailable: false,
      suggestionId: null,
    });
    renderComposer();
    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Some work description" },
    });
    fireEvent.click(screen.getByTestId("ai-composer-generate-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("ai-composer-error")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("ai-composer-error")).toHaveTextContent(
      "AI is currently unavailable",
    );
  });

  it("shows submit error when onSubmit throws", async () => {
    const failingSubmit = vi
      .fn()
      .mockRejectedValue(new Error("Server error on create"));
    renderComposer({ onSubmit: failingSubmit });

    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Some work" },
    });
    fireEvent.click(screen.getByTestId("ai-composer-generate-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("ai-composer-draft")).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByTestId("ai-composer-accept-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("ai-composer-submit-error")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("ai-composer-submit-error")).toHaveTextContent(
      "Server error on create",
    );
  });
});

// ── Tests: RCDO suggest failure is non-fatal ─────────────────────────────────

describe("AiCommitComposer — RCDO suggest failure is non-fatal", () => {
  it("still shows draft when rcdoSuggest fails; no RCDO field shown", async () => {
    vi.mocked(MOCK_AI_API.rcdoSuggest).mockRejectedValue(new Error("RCDO unavailable"));
    renderComposer();

    fireEvent.change(screen.getByTestId("ai-composer-freeform-input"), {
      target: { value: "Some work" },
    });
    fireEvent.click(screen.getByTestId("ai-composer-generate-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("ai-composer-draft")).toBeInTheDocument(),
    );

    // RCDO field should not be shown since suggestion failed
    expect(screen.queryByTestId("ai-composer-rcdo-title")).not.toBeInTheDocument();
    // But title and chess piece should still appear
    expect(screen.getByTestId("ai-composer-title-field")).toBeInTheDocument();
  });
});
