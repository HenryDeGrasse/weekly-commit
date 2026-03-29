/**
 * useBalancedText — DOM-free text measurement for balanced line widths.
 *
 * Computes pixel-tight "shrinkwrap" widths so multiline text blocks have
 * roughly equal line lengths instead of a ragged short trailing line.
 *
 * Architecture (inspired by chenglou/pretext's two-phase model):
 *   1. **prepare** — split text into words, measure each via a shared
 *      offscreen canvas. One-time cost, cached by (text, font) pair.
 *   2. **layout** — walk cached word widths with pure arithmetic to count
 *      lines at any given max-width. ~0.002ms per call — cheap enough to
 *      binary-search 50 candidate widths in <0.1ms.
 *   3. **shrinkwrap** — binary-search the narrowest width that keeps the
 *      same line count, then find the widest line at that width.
 *
 * The result is a CSS `maxWidth` value applied to a regular DOM element.
 * All rendering, selection, and accessibility stay in the browser.
 *
 * Scope: designed for short-to-medium English paragraphs (AI insight cards).
 * Does NOT handle CJK line-breaking, bidi, soft hyphens, or emoji width
 * correction. For those, use a full library like @chenglou/pretext.
 */
import { useState, useEffect, useRef } from "react";

// ── Canvas measurement layer ──────────────────────────────────────────────────

/** Lazily-created shared offscreen canvas for text measurement. */
let _measureCtx: CanvasRenderingContext2D | null = null;

function getMeasureCtx(): CanvasRenderingContext2D | null {
  if (_measureCtx) return _measureCtx;
  if (typeof document === "undefined") return null;
  const canvas = document.createElement("canvas");
  _measureCtx = canvas.getContext("2d");
  return _measureCtx;
}

// ── Prepared text ─────────────────────────────────────────────────────────────

interface PreparedText {
  /** Measured width of each word (includes trailing space except last). */
  wordWidths: number[];
  /** Width of a single space character in this font. */
  spaceWidth: number;
}

/** Split-and-measure cache keyed by `font\0text`. */
const prepareCache = new Map<string, PreparedText>();

/**
 * Segment text into words and measure each via canvas.
 * Cached — repeated calls with the same (text, font) pair return instantly.
 */
function prepare(text: string, font: string): PreparedText | null {
  const key = `${font}\0${text}`;
  const cached = prepareCache.get(key);
  if (cached) return cached;

  const ctx = getMeasureCtx();
  if (!ctx) return null;

  ctx.font = font;

  const spaceWidth = ctx.measureText(" ").width;
  // Split on whitespace runs — matches CSS `white-space: normal` collapsing.
  const words = text.split(/\s+/).filter(Boolean);
  const wordWidths = words.map((w) => ctx.measureText(w).width);

  const prepared: PreparedText = { wordWidths, spaceWidth };
  prepareCache.set(key, prepared);
  return prepared;
}

// ── Layout (pure arithmetic) ──────────────────────────────────────────────────

interface LayoutResult {
  lineCount: number;
  /** Width of the widest line. */
  maxLineWidth: number;
}

/**
 * Greedy line-break over cached word widths. No DOM, no canvas calls.
 *
 * Matches CSS `white-space: normal; overflow-wrap: break-word` semantics:
 * - A word that exceeds `maxWidth` on its own still occupies one line
 *   (we don't break inside words — not needed for typical insight text).
 * - Trailing spaces hang past the line edge (don't trigger wraps).
 */
function layoutLines(prepared: PreparedText, maxWidth: number): LayoutResult {
  const { wordWidths, spaceWidth } = prepared;
  if (wordWidths.length === 0) return { lineCount: 1, maxLineWidth: 0 };

  let lineCount = 1;
  let lineWidth = 0;
  let maxLineWidth = 0;

  for (let i = 0; i < wordWidths.length; i++) {
    const wordW = wordWidths[i]!;
    if (i === 0) {
      // First word always goes on the first line.
      lineWidth = wordW;
    } else {
      const candidate = lineWidth + spaceWidth + wordW;
      if (candidate > maxWidth) {
        // Wrap — commit the current line width, start a new line.
        if (lineWidth > maxLineWidth) maxLineWidth = lineWidth;
        lineCount++;
        lineWidth = wordW;
      } else {
        lineWidth = candidate;
      }
    }
  }
  // Commit the last line.
  if (lineWidth > maxLineWidth) maxLineWidth = lineWidth;

  return { lineCount, maxLineWidth };
}

// ── Public: getBalancedWidth ──────────────────────────────────────────────────

/**
 * Compute the balanced max-width for a text block.
 *
 * Returns the tightest pixel width that wraps the text into the same number
 * of lines as it would at `maxWidth`, making all lines roughly equal length.
 *
 * @param text     Plain text content.
 * @param font     CSS font shorthand (e.g. `'14px Inter'`). Must be a named font.
 * @param maxWidth Container's available content width in px.
 * @returns        Balanced max-width in px, or `maxWidth` if text is single-line / unmeasurable.
 */
export function getBalancedWidth(
  text: string,
  font: string,
  maxWidth: number,
): number {
  if (!text || maxWidth <= 0) return maxWidth;

  const prepared = prepare(text, font);
  if (!prepared) return maxWidth;

  const baseline = layoutLines(prepared, maxWidth);

  // Single line — no balancing needed.
  if (baseline.lineCount <= 1) return maxWidth;

  // Binary-search the narrowest width that keeps the same line count.
  let lo = 1;
  let hi = Math.ceil(maxWidth);
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (layoutLines(prepared, mid).lineCount <= baseline.lineCount) {
      hi = mid;
    } else {
      lo = mid + 1;
    }
  }

  // Get the actual widest line at that threshold.
  const tight = layoutLines(prepared, lo);

  // Ceil + 1px safety margin for sub-pixel rounding between canvas and DOM.
  return Math.ceil(tight.maxLineWidth) + 1;
}

// ── React hook ────────────────────────────────────────────────────────────────

/**
 * React hook that measures a container's width and computes balanced max-widths
 * for a list of text strings. Re-measures on resize via `ResizeObserver`.
 *
 * @param texts      Array of text strings to balance.
 * @param font       CSS font shorthand — must be a named font, not `system-ui`.
 * @returns          `{ ref, widths }` — attach `ref` to the container div.
 *                   `widths` is a `Map<string, number>` from text → balanced max-width.
 */
export function useBalancedText(
  texts: string[],
  font: string,
): {
  ref: React.RefObject<HTMLDivElement>;
  widths: Map<string, number>;
} {
  const ref = useRef<HTMLDivElement>(null!);
  const [widths, setWidths] = useState<Map<string, number>>(() => new Map());

  useEffect(() => {
    const container = ref.current;
    if (!container || texts.length === 0) {
      setWidths(new Map());
      return;
    }

    function measure() {
      const containerWidth = container!.clientWidth;
      if (containerWidth <= 0) return;

      const next = new Map<string, number>();
      for (const text of texts) {
        if (!text) continue;
        next.set(text, getBalancedWidth(text, font, containerWidth));
      }
      setWidths(next);
    }

    // Initial measurement after fonts are loaded.
    if (document.fonts?.ready) {
      document.fonts.ready.then(measure);
    } else {
      measure();
    }

    // Re-measure on container resize (unavailable in jsdom test env).
    if (typeof ResizeObserver !== "undefined") {
      const observer = new ResizeObserver(measure);
      observer.observe(container);
      return () => observer.disconnect();
    }

    return undefined;
  }, [texts, font]);

  return { ref, widths };
}
