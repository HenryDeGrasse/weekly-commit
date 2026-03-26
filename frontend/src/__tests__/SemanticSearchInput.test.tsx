/**
 * Tests for SemanticSearchInput component.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { SemanticSearchInput } from "../components/ai/SemanticSearchInput.js";
import type { RagQueryResponse } from "../api/ragApi.js";

// ── Mocks ────────────────────────────────────────────────────────────────────

const mockMutate = vi.fn<
  (question: string, teamId?: string, userId?: string) => Promise<void>
>();

// Default hook state — overridden per test via mockReturnValue
vi.mock("../api/ragHooks.js", () => ({
  useSemanticQuery: vi.fn(() => ({
    mutate: mockMutate,
    data: undefined,
    loading: false,
    error: null,
  })),
  useRagApi: vi.fn(),
  useTeamInsights: vi.fn(),
  usePlanInsights: vi.fn(),
}));

// AiFeedbackButtons inside QueryAnswerCard also needs aiHooks mocked
vi.mock("../api/aiHooks.js", () => ({
  useAiApi: vi.fn(() => ({
    recordFeedback: vi.fn(),
    getStatus: vi.fn(),
    commitLint: vi.fn(),
  })),
  useAiStatus: vi.fn(() => ({
    data: { available: false, providerName: "stub" },
    loading: false,
    error: null,
  })),
}));

// Import the mocked module so we can reconfigure per test
import * as ragHooks from "../api/ragHooks.js";

// ── Helper ────────────────────────────────────────────────────────────────────

function setHookState(
  overrides: Partial<{
    data: RagQueryResponse | undefined;
    loading: boolean;
    error: string | null;
  }>,
) {
  vi.mocked(ragHooks.useSemanticQuery).mockReturnValue({
    mutate: mockMutate,
    data: undefined,
    loading: false,
    error: null,
    ...overrides,
  });
}

function renderInput(props: { teamId?: string; userId?: string } = {}) {
  return render(
    <MockHostProvider>
      <SemanticSearchInput {...props} />
    </MockHostProvider>,
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("SemanticSearchInput", () => {
  beforeEach(() => {
    mockMutate.mockReset();
    // Reset to default state before each test
    setHookState({});
  });

  // ── Render ─────────────────────────────────────────────────────────────

  it("renders the search input", () => {
    renderInput();
    expect(screen.getByTestId("semantic-search-input")).toBeInTheDocument();
  });

  it("renders the submit button", () => {
    renderInput();
    expect(screen.getByTestId("semantic-search-submit")).toBeInTheDocument();
  });

  it("submit button is disabled when input is empty", () => {
    renderInput();
    expect(screen.getByTestId("semantic-search-submit")).toBeDisabled();
  });

  it("submit button is enabled after typing a question", () => {
    renderInput();
    fireEvent.change(screen.getByTestId("semantic-search-input"), {
      target: { value: "What did we ship last week?" },
    });
    expect(screen.getByTestId("semantic-search-submit")).not.toBeDisabled();
  });

  // ── Mutation trigger ────────────────────────────────────────────────────

  it("calls mutate with the question when form is submitted", async () => {
    mockMutate.mockResolvedValue(undefined);
    renderInput();

    fireEvent.change(screen.getByTestId("semantic-search-input"), {
      target: { value: "How many KING commits this week?" },
    });
    fireEvent.click(screen.getByTestId("semantic-search-submit"));

    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalledWith(
        "How many KING commits this week?",
        undefined,
        undefined,
      );
    });
  });

  it("passes teamId and userId to mutate", async () => {
    mockMutate.mockResolvedValue(undefined);
    renderInput({ teamId: "team-1", userId: "user-1" });

    fireEvent.change(screen.getByTestId("semantic-search-input"), {
      target: { value: "Show me carry-forwards" },
    });
    fireEvent.click(screen.getByTestId("semantic-search-submit"));

    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalledWith(
        "Show me carry-forwards",
        "team-1",
        "user-1",
      );
    });
  });

  it("does not submit when question is whitespace only", () => {
    renderInput();
    fireEvent.change(screen.getByTestId("semantic-search-input"), {
      target: { value: "   " },
    });
    fireEvent.submit(
      screen.getByTestId("semantic-search-input").closest("form")!,
    );
    expect(mockMutate).not.toHaveBeenCalled();
  });

  // ── Loading state ───────────────────────────────────────────────────────

  it("shows loading indicator while query is in-flight", () => {
    setHookState({ loading: true });
    renderInput();

    expect(screen.getByTestId("semantic-search-loading")).toBeInTheDocument();
    expect(screen.getByTestId("semantic-search-loading")).toHaveTextContent(
      /Searching/i,
    );
  });

  it("disables input while loading", () => {
    setHookState({ loading: true });
    renderInput();

    expect(screen.getByTestId("semantic-search-input")).toBeDisabled();
  });

  it("does not show loading when idle", () => {
    renderInput();
    expect(
      screen.queryByTestId("semantic-search-loading"),
    ).not.toBeInTheDocument();
  });

  // ── Error state ─────────────────────────────────────────────────────────

  it("shows error message when query fails", () => {
    setHookState({ error: "Connection refused" });
    renderInput();

    expect(screen.getByTestId("semantic-search-error")).toBeInTheDocument();
    expect(screen.getByTestId("semantic-search-error")).toHaveTextContent(
      "Connection refused",
    );
  });

  it("shows retry button in error state", () => {
    setHookState({ error: "Timeout" });
    renderInput();

    expect(screen.getByTestId("semantic-search-retry")).toBeInTheDocument();
  });

  it("does not show error when query succeeds", () => {
    setHookState({
      data: { aiAvailable: true, answer: "Some answer", sources: [], confidence: 0.8, suggestionId: "s-1" },
    });
    renderInput();

    expect(
      screen.queryByTestId("semantic-search-error"),
    ).not.toBeInTheDocument();
  });

  // ── Answer display ──────────────────────────────────────────────────────

  it("renders QueryAnswerCard when answer is available", () => {
    setHookState({
      data: {
        aiAvailable: true,
        answer: "The team completed 8 of 10 commits last week.",
        sources: [],
        confidence: 0.9,
        suggestionId: "s-1",
      },
    });
    renderInput();

    expect(screen.getByTestId("query-answer-card")).toBeInTheDocument();
    expect(screen.getByTestId("rag-answer-text")).toHaveTextContent(
      "The team completed 8 of 10 commits last week.",
    );
  });

  it("does not render QueryAnswerCard when AI is unavailable", () => {
    setHookState({ data: { aiAvailable: false } });
    renderInput();

    expect(screen.queryByTestId("query-answer-card")).not.toBeInTheDocument();
    expect(
      screen.getByTestId("semantic-search-unavailable"),
    ).toBeInTheDocument();
  });

  it("does not render QueryAnswerCard before first query", () => {
    renderInput();
    expect(screen.queryByTestId("query-answer-card")).not.toBeInTheDocument();
  });

  it("renders feedback buttons inside the answer card", () => {
    setHookState({
      data: {
        aiAvailable: true,
        answer: "Test answer",
        sources: [],
        confidence: 0.75,
        suggestionId: "s-2",
      },
    });
    renderInput();

    expect(screen.getByTestId("ai-feedback-accept")).toBeInTheDocument();
    expect(screen.getByTestId("ai-feedback-dismiss")).toBeInTheDocument();
  });
});
