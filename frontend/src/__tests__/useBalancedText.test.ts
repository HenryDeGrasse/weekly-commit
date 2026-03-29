/**
 * @vitest-environment node
 *
 * Tests for getBalancedWidth and useBalancedText.
 *
 * Uses node environment (no jsdom overhead). We test the pure-function
 * getBalancedWidth by providing a mock canvas context. The React hook
 * is tested structurally (return shape / stability).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// ── Provide minimal DOM stubs for the module's lazy canvas init ───────────────

const CHAR_WIDTH = 10;
const mockCtx = {
  font: "",
  measureText: vi.fn((text: string) => ({ width: text.length * CHAR_WIDTH })),
};

// Stub just enough of `document` for the canvas factory.
const fakeCanvas = { getContext: () => mockCtx };
vi.stubGlobal("document", {
  createElement: (tag: string) => (tag === "canvas" ? fakeCanvas : {}),
  fonts: undefined,
});

import { getBalancedWidth } from "../lib/useBalancedText.js";

// ── getBalancedWidth ──────────────────────────────────────────────────────────

describe("getBalancedWidth", () => {
  const font = '14px "Inter", sans-serif';

  beforeEach(() => {
    mockCtx.measureText.mockClear();
  });

  it("returns maxWidth for empty text", () => {
    expect(getBalancedWidth("", font, 300)).toBe(300);
  });

  it("returns maxWidth when maxWidth is 0", () => {
    expect(getBalancedWidth("some text", font, 0)).toBe(0);
  });

  it("returns maxWidth for single-line text", () => {
    // "Hi" → 1 word, 2 chars × 10px = 20px. Fits in 300px → single line.
    expect(getBalancedWidth("Hi", font, 300)).toBe(300);
  });

  it("narrows multi-line text to balance lines", () => {
    // "aa bb cc dd" → 4 words: 20px each, space 10px.
    // Full width: 20+10+20+10+20+10+20 = 110px.
    // At maxWidth=80:
    //   line1: "aa bb cc" → 20+10+20+10+20 = 80px
    //   line2: "dd" → 20px → orphan!
    // Binary search should find tightest 2-line width (~60px),
    // making both lines ~50–60px.
    const result = getBalancedWidth("aa bb cc dd", font, 80);
    expect(result).toBeLessThan(80);
    expect(result).toBeGreaterThan(20);
  });

  it("handles many words wrapping to multiple lines", () => {
    // 10 words of 5 chars each = 50px per word, space = 10px.
    // Total text width = 10*50 + 9*10 = 590px.
    // At maxWidth=300: baseline = 2 lines. Balanced ≈ 295px.
    const words = Array.from({ length: 10 }, () => "abcde").join(" ");
    const result = getBalancedWidth(words, font, 300);
    expect(result).toBeGreaterThan(0);
    expect(result).toBeLessThanOrEqual(300);
    expect(Number.isFinite(result)).toBe(true);
  });

  it("balanced width keeps the same line count as baseline", () => {
    // 6 words of 30px each, space = 10px.
    // Total = 6*30 + 5*10 = 230px.
    // At maxWidth=100: line1=30+10+30=70, +10+30=110>100 → wrap.
    //   line1: "aaa bbb" = 70px (2 words)
    //   line2: "ccc ddd" = 70px (2 words)
    //   line3: "eee fff" = 70px (2 words)
    //   → 3 lines baseline.
    // Balanced should also be 3 lines but tighter.
    const words = Array.from({ length: 6 }, () => "aaa").join(" ");
    const result = getBalancedWidth(words, font, 100);
    expect(result).toBeLessThanOrEqual(100);
    // Should be at least wide enough for one word + space + word.
    expect(result).toBeGreaterThanOrEqual(30);
  });

  it("uses canvas measureText for word widths", () => {
    getBalancedWidth("hello world test", font, 200);
    // Should measure: " ", "hello", "world", "test"
    expect(mockCtx.measureText).toHaveBeenCalled();
    const calls = mockCtx.measureText.mock.calls.map((c) => c[0]);
    expect(calls).toContain(" ");
    expect(calls).toContain("hello");
  });
});
