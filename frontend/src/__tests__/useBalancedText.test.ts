/**
 * @vitest-environment node
 *
 * Tests for useBalancedText hook and getBalancedWidth utility.
 *
 * Pretext requires a real browser canvas + font engine, so we mock
 * the @chenglou/pretext module. Uses node environment to avoid jsdom
 * memory overhead from pretext's module-level canvas initialisation.
 */
import { describe, it, expect, vi } from "vitest";

// ── Mock @chenglou/pretext ────────────────────────────────────────────────────

// Simulate a text that is ~10px per character.
vi.mock("@chenglou/pretext", () => {
  const CHAR_WIDTH = 10;

  function prepareWithSegments(text: string, _font: string) {
    return { __text: text, __charWidth: CHAR_WIDTH };
  }

  function layout(
    prepared: { __text: string; __charWidth: number },
    maxWidth: number,
    _lineHeight: number,
  ) {
    const textWidth = prepared.__text.length * prepared.__charWidth;
    if (maxWidth <= 0) return { lineCount: 1, height: 0 };
    const lineCount = Math.max(1, Math.ceil(textWidth / maxWidth));
    return { lineCount, height: lineCount * _lineHeight };
  }

  function walkLineRanges(
    prepared: { __text: string; __charWidth: number },
    maxWidth: number,
    onLine: (line: { width: number }) => void,
  ) {
    const textWidth = prepared.__text.length * prepared.__charWidth;
    if (maxWidth <= 0) {
      onLine({ width: 0 });
      return 1;
    }
    const lineCount = Math.max(1, Math.ceil(textWidth / maxWidth));
    const avgWidth = textWidth / lineCount;
    for (let i = 0; i < lineCount; i++) {
      const remaining = textWidth - avgWidth * i;
      onLine({ width: Math.min(avgWidth, remaining) });
    }
    return lineCount;
  }

  return { prepareWithSegments, layout, walkLineRanges };
});

import { getBalancedWidth } from "../lib/useBalancedText.js";

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("getBalancedWidth", () => {
  const font = '14px "Inter", sans-serif';
  const lineHeight = 22;

  it("returns maxWidth for empty text", () => {
    expect(getBalancedWidth("", font, 300, lineHeight)).toBe(300);
  });

  it("returns maxWidth when maxWidth is 0", () => {
    expect(getBalancedWidth("some text", font, 0, lineHeight)).toBe(0);
  });

  it("returns maxWidth for single-line text (fits within maxWidth)", () => {
    // "Hi" = 2 chars × 10px = 20px, maxWidth = 300 → single line
    const result = getBalancedWidth("Hi", font, 300, lineHeight);
    expect(result).toBe(300);
  });

  it("returns a balanced width narrower than maxWidth for multi-line text", () => {
    // 50 chars × 10px = 500px of text, maxWidth = 300
    // At 300px → ceil(500/300) = 2 lines
    // Binary search finds tightest width for 2 lines → ~250px
    const text = "a".repeat(50);
    const result = getBalancedWidth(text, font, 300, lineHeight);
    expect(result).toBeGreaterThan(0);
    expect(result).toBeLessThanOrEqual(300);
  });

  it("returns a number (not NaN or Infinity)", () => {
    const result = getBalancedWidth("Some text content here", font, 400, lineHeight);
    expect(Number.isFinite(result)).toBe(true);
  });

  it("balanced width produces roughly equal line widths", () => {
    // 60 chars × 10px = 600px, maxWidth = 250
    // At 250px → ceil(600/250) = 3 lines
    // Balanced: each line ~200px (600/3)
    const text = "a".repeat(60);
    const result = getBalancedWidth(text, font, 250, lineHeight);
    // Should be around 200px (the tightest 3-line width)
    expect(result).toBeGreaterThanOrEqual(195);
    expect(result).toBeLessThanOrEqual(210);
  });
});
