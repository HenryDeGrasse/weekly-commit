/**
 * Tests for QueryAnswerCard component.
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { QueryAnswerCard } from "../components/ai/QueryAnswerCard.js";
import type { RagSource } from "../api/ragApi.js";

// Mock AI hooks to prevent real API calls from AiFeedbackButtons
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

const SUGGESTION_ID = "sugg-1";

const commitSource: RagSource = {
  entityType: "commit",
  entityId: "commit-abc",
  weekStartDate: "2025-01-06",
  snippet: "Deploy the new API gateway",
};

const ticketSource: RagSource = {
  entityType: "ticket",
  entityId: "ticket-xyz",
  weekStartDate: "2025-01-06",
  snippet: "Fix memory leak in event loop",
};

const planSummarySource: RagSource = {
  entityType: "plan_summary",
  entityId: "plan-001",
  weekStartDate: "2025-01-06",
};

function renderCard(props: {
  answer?: string;
  sources?: RagSource[];
  confidence?: number;
  suggestionId?: string;
}) {
  return render(
    <MockHostProvider>
      <QueryAnswerCard
        answer={props.answer ?? "The team completed 8 out of 10 commits."}
        sources={props.sources ?? []}
        confidence={props.confidence ?? 0.85}
        suggestionId={props.suggestionId ?? SUGGESTION_ID}
      />
    </MockHostProvider>,
  );
}

describe("QueryAnswerCard", () => {
  // ── answer text ──────────────────────────────────────────────────────────

  it("renders the answer text", () => {
    renderCard({ answer: "The team achieved all KING commits this week." });
    expect(
      screen.getByTestId("rag-answer-text"),
    ).toHaveTextContent(/The team achieved all.*KING.*commits this week/);
  });

  it("renders the AI Answer heading", () => {
    renderCard({});
    expect(screen.getByText("AI Answer")).toBeInTheDocument();
  });

  it("renders bold text as strong elements", () => {
    renderCard({ answer: "**Alice Chen** did great work." });
    const answerEl = screen.getByTestId("rag-answer-text");
    const strongEls = answerEl.querySelectorAll("strong");
    expect(strongEls.length).toBeGreaterThanOrEqual(1);
    expect(strongEls[0]!.textContent).toBe("Alice Chen");
  });

  it("renders paragraphs from double newlines", () => {
    renderCard({ answer: "First paragraph.\n\nSecond paragraph." });
    const answerEl = screen.getByTestId("rag-answer-text");
    const paragraphs = answerEl.querySelectorAll("p");
    expect(paragraphs.length).toBe(2);
  });

  it("renders chess piece names with glyphs", () => {
    renderCard({ answer: "The KING commit was critical." });
    const answerEl = screen.getByTestId("rag-answer-text");
    expect(answerEl.textContent).toContain("♔");
    expect(answerEl.textContent).toContain("KING");
  });

  it("renders commit titles with monospace styling", () => {
    renderCard({ answer: 'She worked on "Deploy the API gateway" last week.' });
    const answerEl = screen.getByTestId("rag-answer-text");
    expect(answerEl.textContent).toContain("Deploy the API gateway");
  });

  it("renders metric values with emphasis", () => {
    renderCard({ answer: "The team achieved 42pts this week, an 85% rate." });
    const answerEl = screen.getByTestId("rag-answer-text");
    expect(answerEl.textContent).toContain("42pts");
    expect(answerEl.textContent).toContain("85%");
  });

  // ── confidence indicator ─────────────────────────────────────────────────

  it("shows High confidence for score >= 0.8", () => {
    renderCard({ confidence: 0.9 });
    const indicator = screen.getByTestId("confidence-indicator");
    expect(indicator).toHaveTextContent("High");
    expect(indicator).toHaveTextContent("90%");
  });

  it("shows Medium confidence for score >= 0.5 and < 0.8", () => {
    renderCard({ confidence: 0.65 });
    const indicator = screen.getByTestId("confidence-indicator");
    expect(indicator).toHaveTextContent("Medium");
    expect(indicator).toHaveTextContent("65%");
  });

  it("shows Low confidence for score < 0.5", () => {
    renderCard({ confidence: 0.3 });
    const indicator = screen.getByTestId("confidence-indicator");
    expect(indicator).toHaveTextContent("Low");
    expect(indicator).toHaveTextContent("30%");
  });

  it("renders confidence bar with correct width", () => {
    renderCard({ confidence: 0.75 });
    const bar = screen.getByTestId("confidence-bar");
    expect(bar).toHaveStyle({ width: "75%" });
  });

  // ── sources ──────────────────────────────────────────────────────────────

  it("renders sources toggle when sources are provided", () => {
    renderCard({ sources: [commitSource] });
    expect(screen.getByTestId("rag-sources-section")).toBeInTheDocument();
    expect(screen.getByTestId("rag-sources-toggle")).toBeInTheDocument();
  });

  it("does not render sources section when sources array is empty", () => {
    renderCard({ sources: [] });
    expect(
      screen.queryByTestId("rag-sources-section"),
    ).not.toBeInTheDocument();
  });

  it("shows sources after clicking toggle", () => {
    renderCard({ sources: [commitSource, ticketSource] });
    // Sources hidden initially
    expect(screen.queryByTestId(`rag-source-${commitSource.entityId}`)).not.toBeInTheDocument();
    // Click toggle
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    // Sources now visible
    expect(screen.getByTestId(`rag-source-${commitSource.entityId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`rag-source-${ticketSource.entityId}`)).toBeInTheDocument();
  });

  it("renders entity type badge for each source", () => {
    renderCard({ sources: [commitSource] });
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    expect(screen.getByText("commit")).toBeInTheDocument();
  });

  it("renders ticket entity type badge for ticket source", () => {
    renderCard({ sources: [ticketSource] });
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    expect(screen.getByText("ticket")).toBeInTheDocument();
  });

  it("renders plan_summary entity type as 'plan summary'", () => {
    renderCard({ sources: [planSummarySource] });
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    expect(screen.getByText("plan summary")).toBeInTheDocument();
  });

  it("renders week start date when provided", () => {
    renderCard({ sources: [commitSource] });
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    expect(screen.getByText("w/2025-01-06")).toBeInTheDocument();
  });

  it("renders snippet text when provided", () => {
    renderCard({ sources: [commitSource] });
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    expect(
      screen.getByText(/Deploy the new API gateway/),
    ).toBeInTheDocument();
  });

  it("renders a link for ticket entity type", () => {
    renderCard({ sources: [ticketSource] });
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    const link = screen.getByTestId(
      `rag-source-link-${ticketSource.entityId}`,
    );
    expect(link).toHaveAttribute("href", "/tickets");
  });

  it("does not render a link for commit entity type", () => {
    renderCard({ sources: [commitSource] });
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    expect(
      screen.queryByTestId(`rag-source-link-${commitSource.entityId}`),
    ).not.toBeInTheDocument();
  });

  // ── feedback buttons ─────────────────────────────────────────────────────

  it("renders AI feedback accept button", () => {
    renderCard({});
    expect(screen.getByTestId("ai-feedback-accept")).toBeInTheDocument();
  });

  it("renders AI feedback dismiss button", () => {
    renderCard({});
    expect(screen.getByTestId("ai-feedback-dismiss")).toBeInTheDocument();
  });

  // ── multiple sources ─────────────────────────────────────────────────────

  it("handles multiple sources correctly", () => {
    renderCard({
      sources: [commitSource, ticketSource, planSummarySource],
    });
    fireEvent.click(screen.getByTestId("rag-sources-toggle"));
    expect(screen.getByTestId(`rag-source-${commitSource.entityId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`rag-source-${ticketSource.entityId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`rag-source-${planSummarySource.entityId}`)).toBeInTheDocument();
  });
});
