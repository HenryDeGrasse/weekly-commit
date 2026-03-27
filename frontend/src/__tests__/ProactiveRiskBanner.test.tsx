/**
 * Tests for ProactiveRiskBanner component.
 *
 * Covers:
 *   - Critical signals (OVERCOMMIT, BLOCKED_CRITICAL, REPEATED_CARRY_FORWARD) render as banners
 *   - Non-critical signals (UNDERCOMMIT, SCOPE_VOLATILITY) are filtered out
 *   - Empty state: no output when no critical signals exist
 *   - AI unavailable state: no output
 *   - Loading state: no output
 *   - Error state: no output
 *   - AiFeedbackButtons present on each banner
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { ProactiveRiskBanner } from "../components/ai/ProactiveRiskBanner.js";
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
}));

import * as aiHooks from "../api/aiHooks.js";

// ── Sample data ───────────────────────────────────────────────────────────────

function makeSignal(
  id: string,
  signalType: string,
  rationale = `Rationale for ${signalType}`,
): RiskSignal {
  return {
    id,
    signalType,
    rationale,
    planId: "plan-1",
    createdAt: "2026-03-27T10:00:00Z",
  };
}

const overcommitSignal = makeSignal(
  "sig-1",
  "OVERCOMMIT",
  "Your committed points (14) exceed your budget (10) and your historical average (9).",
);

const blockedCriticalSignal = makeSignal(
  "sig-2",
  "BLOCKED_CRITICAL",
  "KING-level commit 'Launch critical feature' is linked to a ticket that has been BLOCKED for 3 days.",
);

const repeatedCfSignal = makeSignal(
  "sig-3",
  "REPEATED_CARRY_FORWARD",
  "This commit has been carried forward 3 weeks in a row.",
);

const undercommitSignal = makeSignal(
  "sig-4",
  "UNDERCOMMIT",
  "You have only committed 2 points against a 10-point budget.",
);

const scopeVolatilitySignal = makeSignal(
  "sig-5",
  "SCOPE_VOLATILITY",
  "High post-lock scope change activity detected.",
);

function available(signals: RiskSignal[]): PlanRiskSignalsResponse {
  return { aiAvailable: true, planId: "plan-1", signals };
}

function unavailable(): PlanRiskSignalsResponse {
  return { aiAvailable: false, planId: "plan-1", signals: [] };
}

// ── Render helper ─────────────────────────────────────────────────────────────

function renderBanner(planId = "plan-1") {
  return render(
    <MockHostProvider>
      <ProactiveRiskBanner planId={planId} />
    </MockHostProvider>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("ProactiveRiskBanner", () => {
  beforeEach(() => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
  });

  // ── Loading state ──────────────────────────────────────────────────────────

  it("renders nothing while loading", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
    const { container } = renderBanner();
    expect(container.firstChild).toBeNull();
  });

  // ── AI unavailable ─────────────────────────────────────────────────────────

  it("renders nothing when AI is unavailable", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: unavailable(),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    const { container } = renderBanner();
    expect(container.firstChild).toBeNull();
  });

  // ── Error state ────────────────────────────────────────────────────────────

  it("renders nothing when the fetch errors", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: undefined,
      loading: false,
      error: new ApiRequestError({ status: 500, message: "Server error" }),
      refetch: vi.fn(),
    });
    const { container } = renderBanner();
    expect(container.firstChild).toBeNull();
  });

  // ── Empty state ────────────────────────────────────────────────────────────

  it("renders nothing when there are no signals", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    const { container } = renderBanner();
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when all signals are non-critical types", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([undercommitSignal, scopeVolatilitySignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    const { container } = renderBanner();
    expect(container.firstChild).toBeNull();
  });

  // ── Critical signal rendering ──────────────────────────────────────────────

  it("renders the banner container when critical signals exist", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([overcommitSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("proactive-risk-banners")).toBeInTheDocument();
  });

  it("renders an OVERCOMMIT banner with rationale text", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([overcommitSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("risk-banner-sig-1")).toBeInTheDocument();
    expect(screen.getByTestId("risk-banner-rationale-sig-1")).toHaveTextContent(
      overcommitSignal.rationale,
    );
  });

  it("renders the signal type label for OVERCOMMIT", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([overcommitSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("risk-banner-type-sig-1")).toHaveTextContent(
      "OVERCOMMIT",
    );
  });

  it("renders a BLOCKED_CRITICAL banner", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([blockedCriticalSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("risk-banner-sig-2")).toBeInTheDocument();
    expect(
      screen.getByTestId("risk-banner-rationale-sig-2"),
    ).toHaveTextContent(blockedCriticalSignal.rationale);
  });

  it("renders a REPEATED_CARRY_FORWARD banner", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([repeatedCfSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("risk-banner-sig-3")).toBeInTheDocument();
  });

  it("renders multiple critical banners when multiple signals exist", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([overcommitSignal, blockedCriticalSignal, repeatedCfSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("risk-banner-sig-1")).toBeInTheDocument();
    expect(screen.getByTestId("risk-banner-sig-2")).toBeInTheDocument();
    expect(screen.getByTestId("risk-banner-sig-3")).toBeInTheDocument();
  });

  // ── Non-critical signal filtering ─────────────────────────────────────────

  it("does NOT render UNDERCOMMIT as a banner", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([undercommitSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    const { container } = renderBanner();
    expect(container.firstChild).toBeNull();
  });

  it("does NOT render SCOPE_VOLATILITY as a banner", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([scopeVolatilitySignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    const { container } = renderBanner();
    expect(container.firstChild).toBeNull();
  });

  it("shows only critical signals when mixed with non-critical", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([overcommitSignal, undercommitSignal, scopeVolatilitySignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    // Only OVERCOMMIT banner renders
    expect(screen.getByTestId("risk-banner-sig-1")).toBeInTheDocument();
    expect(screen.queryByTestId("risk-banner-sig-4")).not.toBeInTheDocument();
    expect(screen.queryByTestId("risk-banner-sig-5")).not.toBeInTheDocument();
  });

  // ── Action hints ───────────────────────────────────────────────────────────

  it("shows an action hint for OVERCOMMIT", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([overcommitSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("risk-banner-action-sig-1")).toHaveTextContent(
      "Consider reducing scope",
    );
  });

  it("shows an action hint for BLOCKED_CRITICAL", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([blockedCriticalSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("risk-banner-action-sig-2")).toHaveTextContent(
      "Resolve the blocked ticket",
    );
  });

  it("shows an action hint for REPEATED_CARRY_FORWARD", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([repeatedCfSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    expect(screen.getByTestId("risk-banner-action-sig-3")).toHaveTextContent(
      "broken down or deprioritised",
    );
  });

  // ── Accessibility ──────────────────────────────────────────────────────────

  it("each banner has role=alert", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([overcommitSignal, blockedCriticalSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    const alerts = screen.getAllByRole("alert");
    expect(alerts.length).toBeGreaterThanOrEqual(2);
  });

  // ── AiFeedbackButtons ──────────────────────────────────────────────────────

  it("renders AI feedback buttons for each critical signal", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: available([overcommitSignal, blockedCriticalSignal]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderBanner();
    const acceptBtns = screen.getAllByTestId("ai-feedback-accept");
    const dismissBtns = screen.getAllByTestId("ai-feedback-dismiss");
    expect(acceptBtns).toHaveLength(2);
    expect(dismissBtns).toHaveLength(2);
  });
});
