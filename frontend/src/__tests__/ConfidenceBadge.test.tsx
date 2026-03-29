/**
 * Tests for ConfidenceBadge component.
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { ConfidenceBadge } from "../components/ai/ConfidenceBadge.js";

// ── Render helper ─────────────────────────────────────────────────────────────

function renderBadge(tier: string) {
  return render(
    <MockHostProvider>
      <ConfidenceBadge tier={tier} />
    </MockHostProvider>,
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("ConfidenceBadge", () => {
  // ── HIGH ───────────────────────────────────────────────────────────────────

  it("renders a badge for HIGH tier with correct label", () => {
    renderBadge("HIGH");
    const badge = screen.getByTestId("confidence-badge");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent("High");
  });

  it("sets data-tier attribute for HIGH", () => {
    renderBadge("HIGH");
    expect(screen.getByTestId("confidence-badge")).toHaveAttribute(
      "data-tier",
      "HIGH",
    );
  });

  // ── MEDIUM ─────────────────────────────────────────────────────────────────

  it("renders a badge for MEDIUM tier with correct label", () => {
    renderBadge("MEDIUM");
    const badge = screen.getByTestId("confidence-badge");
    expect(badge).toHaveTextContent("Medium");
    expect(badge).toHaveAttribute("data-tier", "MEDIUM");
  });

  // ── LOW ────────────────────────────────────────────────────────────────────

  it("renders a badge for LOW tier with correct label", () => {
    renderBadge("LOW");
    const badge = screen.getByTestId("confidence-badge");
    expect(badge).toHaveTextContent("Low");
    expect(badge).toHaveAttribute("data-tier", "LOW");
  });

  // ── INSUFFICIENT ───────────────────────────────────────────────────────────

  it("renders a badge for INSUFFICIENT tier with correct label", () => {
    renderBadge("INSUFFICIENT");
    const badge = screen.getByTestId("confidence-badge");
    expect(badge).toHaveTextContent("Insufficient data");
    expect(badge).toHaveAttribute("data-tier", "INSUFFICIENT");
  });

  // ── Case-insensitivity ─────────────────────────────────────────────────────

  it("is case-insensitive — lowercase 'high' renders same as 'HIGH'", () => {
    renderBadge("high");
    const badge = screen.getByTestId("confidence-badge");
    expect(badge).toHaveTextContent("High");
    expect(badge).toHaveAttribute("data-tier", "HIGH");
  });

  it("is case-insensitive — mixed case 'Medium' renders same as 'MEDIUM'", () => {
    renderBadge("Medium");
    const badge = screen.getByTestId("confidence-badge");
    expect(badge).toHaveTextContent("Medium");
    expect(badge).toHaveAttribute("data-tier", "MEDIUM");
  });

  // ── Tooltip ────────────────────────────────────────────────────────────────

  it("renders a tooltip element", () => {
    renderBadge("HIGH");
    const tooltip = screen.getByRole("tooltip");
    expect(tooltip).toBeInTheDocument();
  });

  it("tooltip contains informative text for HIGH tier", () => {
    renderBadge("HIGH");
    const tooltip = screen.getByRole("tooltip");
    expect(tooltip).toHaveTextContent(/12\+.*weeks/i);
  });

  it("tooltip contains informative text for INSUFFICIENT tier", () => {
    renderBadge("INSUFFICIENT");
    const tooltip = screen.getByRole("tooltip");
    expect(tooltip).toHaveTextContent(/8\+.*weeks/i);
  });
});
