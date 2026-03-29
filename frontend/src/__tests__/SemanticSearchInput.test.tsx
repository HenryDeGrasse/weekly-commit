/**
 * Tests for SemanticSearchInput component.
 *
 * The component now uses useStreamingRagQuery instead of the batch
 * useSemanticQuery hook. Tests are updated to reflect the streaming interface.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { SemanticSearchInput } from "../components/ai/SemanticSearchInput.js";

// ── Mocks ────────────────────────────────────────────────────────────────────

const mockStartStream = vi.fn<(question: string) => void>();
const mockCancel = vi.fn<() => void>();

// Default streaming hook state — overridden per test via mockReturnValue
vi.mock("../api/ragStreamHooks.js", () => ({
  useStreamingRagQuery: vi.fn(() => ({
    answer: "",
    sources: [],
    confidence: 0,
    isStreaming: false,
    error: null,
    startStream: mockStartStream,
    cancel: mockCancel,
  })),
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
import * as ragStreamHooks from "../api/ragStreamHooks.js";

// ── Helper ────────────────────────────────────────────────────────────────────

type StreamState = {
  answer?: string;
  sources?: { entityType: string; entityId: string }[];
  confidence?: number;
  isStreaming?: boolean;
  error?: string | null;
};

function setHookState(overrides: StreamState) {
  vi.mocked(ragStreamHooks.useStreamingRagQuery).mockReturnValue({
    answer: "",
    sources: [],
    confidence: 0,
    isStreaming: false,
    error: null,
    startStream: mockStartStream,
    cancel: mockCancel,
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
    mockStartStream.mockReset();
    mockCancel.mockReset();
    // Reset to default (idle) state before each test
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

  // ── Stream trigger ──────────────────────────────────────────────────────

  it("calls startStream with the question when form is submitted", async () => {
    renderInput();

    fireEvent.change(screen.getByTestId("semantic-search-input"), {
      target: { value: "How many KING commits this week?" },
    });
    fireEvent.click(screen.getByTestId("semantic-search-submit"));

    await waitFor(() => {
      expect(mockStartStream).toHaveBeenCalledWith(
        "How many KING commits this week?",
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
    expect(mockStartStream).not.toHaveBeenCalled();
  });

  it("clicking a suggested question calls startStream", async () => {
    renderInput();

    const suggestedBtn = screen.getByTestId(
      "suggested-q-what-did-the-team-co",
    );
    fireEvent.click(suggestedBtn);

    await waitFor(() => {
      expect(mockStartStream).toHaveBeenCalled();
    });
  });

  // ── Loading state ───────────────────────────────────────────────────────

  it("shows loading indicator while streaming is in-flight and no answer yet", () => {
    // isStreaming=true + no answer = loading
    setHookState({ isStreaming: true, answer: "" });
    renderInput();

    expect(screen.getByTestId("semantic-search-loading")).toBeInTheDocument();
    expect(screen.getByTestId("semantic-search-loading")).toHaveTextContent(
      /Searching/i,
    );
  });

  it("disables input while loading", () => {
    setHookState({ isStreaming: true, answer: "" });
    renderInput();

    expect(screen.getByTestId("semantic-search-input")).toBeDisabled();
  });

  it("does not show loading when idle", () => {
    renderInput();
    expect(
      screen.queryByTestId("semantic-search-loading"),
    ).not.toBeInTheDocument();
  });

  it("does not show loading indicator once answer content arrives", () => {
    // isStreaming=true but already has answer content — no longer "loading"
    setHookState({ isStreaming: true, answer: "The team" });
    renderInput();

    expect(
      screen.queryByTestId("semantic-search-loading"),
    ).not.toBeInTheDocument();
  });

  // ── Error state ─────────────────────────────────────────────────────────

  it("shows error message when streaming fails", () => {
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

  it("does not show error when answer is available", () => {
    setHookState({ answer: "The team shipped X." });
    renderInput();

    expect(
      screen.queryByTestId("semantic-search-error"),
    ).not.toBeInTheDocument();
  });

  // ── Answer display ──────────────────────────────────────────────────────

  it("renders QueryAnswerCard when answer is available", () => {
    setHookState({ answer: "The team completed 8 of 10 commits last week." });
    renderInput();

    expect(screen.getByTestId("query-answer-card")).toBeInTheDocument();
    expect(screen.getByTestId("rag-answer-text")).toHaveTextContent(
      "The team completed 8 of 10 commits last week.",
    );
  });

  it("renders QueryAnswerCard even while still streaming (incremental display)", () => {
    setHookState({ isStreaming: true, answer: "Partial answer so far" });
    renderInput();

    expect(screen.getByTestId("query-answer-card")).toBeInTheDocument();
  });

  it("shows typing indicator while streaming", () => {
    setHookState({ isStreaming: true, answer: "Partial" });
    renderInput();

    expect(
      screen.getByTestId("streaming-typing-indicator"),
    ).toBeInTheDocument();
  });

  it("does not show typing indicator after stream completes", () => {
    setHookState({ isStreaming: false, answer: "Complete answer" });
    renderInput();

    expect(
      screen.queryByTestId("streaming-typing-indicator"),
    ).not.toBeInTheDocument();
  });

  it("does not render QueryAnswerCard before first query", () => {
    renderInput();
    expect(screen.queryByTestId("query-answer-card")).not.toBeInTheDocument();
  });

  it("does not render QueryAnswerCard when there is an error", () => {
    setHookState({ error: "AI unavailable" });
    renderInput();

    expect(screen.queryByTestId("query-answer-card")).not.toBeInTheDocument();
    expect(screen.getByTestId("semantic-search-error")).toBeInTheDocument();
  });

  it("does not render feedback buttons in streaming mode", () => {
    setHookState({
      answer: "Test answer",
      confidence: 0.75,
    });
    renderInput();

    expect(screen.queryByTestId("ai-feedback-accept")).not.toBeInTheDocument();
    expect(screen.queryByTestId("ai-feedback-dismiss")).not.toBeInTheDocument();
  });
});
