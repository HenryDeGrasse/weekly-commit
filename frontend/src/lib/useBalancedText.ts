/**
 * useBalancedText — uses @chenglou/pretext to compute pixel-tight
 * shrinkwrap widths for text blocks so line lengths are visually balanced
 * instead of ragged-right.
 *
 * How it works:
 *   1. `prepare()` segments the text and measures via canvas (one-time, ~0.04ms per string)
 *   2. `layout()` counts lines at a candidate width via pure arithmetic (~0.0002ms)
 *   3. Binary-search the narrowest width that keeps the same line count as the full-width layout
 *   4. `walkLineRanges()` finds the actual widest line at that width — the balanced max-width
 *
 * The result is a CSS `maxWidth` value that makes every line roughly the same
 * length. DOM still handles all rendering, selection, and accessibility.
 *
 * Requires a named font (not `system-ui`) for accurate canvas measurement.
 * See: https://github.com/chenglou/pretext#caveats
 */
import { useState, useEffect, useRef } from "react";
import {
  prepareWithSegments,
  layout,
  walkLineRanges,
} from "@chenglou/pretext";

// ── Core measurement (non-React) ──────────────────────────────────────────────

/**
 * Compute the balanced max-width for a text block.
 *
 * Returns the tightest pixel width that wraps the text into the same number of
 * lines as it would at `maxWidth`, eliminating the short trailing line that
 * makes ragged-right text look uneven.
 *
 * @param text       The plain text content.
 * @param font       CSS font shorthand (e.g. `'14px Inter'`). Must be a named font.
 * @param maxWidth   The container's available content width in px.
 * @param lineHeight The line-height in px (must match CSS).
 * @returns          The balanced max-width in px, or `maxWidth` if text fits on one line.
 */
export function getBalancedWidth(
  text: string,
  font: string,
  maxWidth: number,
  lineHeight: number,
): number {
  if (!text || maxWidth <= 0) return maxWidth;

  const prepared = prepareWithSegments(text, font);
  const baseline = layout(prepared, maxWidth, lineHeight);

  // Single line or empty — no balancing needed
  if (baseline.lineCount <= 1) return maxWidth;

  // Binary-search the narrowest width that keeps the same line count
  let lo = 1;
  let hi = Math.ceil(maxWidth);
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (layout(prepared, mid, lineHeight).lineCount <= baseline.lineCount) {
      hi = mid;
    } else {
      lo = mid + 1;
    }
  }

  // Get the actual widest line at that threshold — the pixel-tight balanced width
  let widest = 0;
  walkLineRanges(prepared, lo, (line) => {
    if (line.width > widest) widest = line.width;
  });

  // Ceil + 1px safety margin (sub-pixel rounding between canvas and DOM)
  return Math.ceil(widest) + 1;
}

// ── Batch helper ──────────────────────────────────────────────────────────────

export interface BalancedTextEntry {
  text: string;
  maxWidth: number | undefined;
}

/**
 * Compute balanced widths for a batch of texts. Shares the `prepare()` cost
 * within one synchronous pass so the browser only does one canvas warm-up.
 */
export function getBalancedWidths(
  entries: { text: string; containerWidth: number }[],
  font: string,
  lineHeight: number,
): BalancedTextEntry[] {
  return entries.map(({ text, containerWidth }) => ({
    text,
    maxWidth:
      text && containerWidth > 0
        ? getBalancedWidth(text, font, containerWidth, lineHeight)
        : undefined,
  }));
}

// ── React hook ────────────────────────────────────────────────────────────────

/**
 * React hook that measures a container's width and computes balanced max-widths
 * for a list of text strings. Updates on resize.
 *
 * @param texts       Array of text strings to balance.
 * @param font        CSS font shorthand — must be a named font, not `system-ui`.
 * @param lineHeight  Line height in px matching the CSS `line-height`.
 * @returns           `{ ref, widths }` — attach `ref` to the container div.
 *                    `widths` is a `Map<string, number>` from text → balanced max-width.
 */
export function useBalancedText(
  texts: string[],
  font: string,
  lineHeight: number,
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
        next.set(text, getBalancedWidth(text, font, containerWidth, lineHeight));
      }
      setWidths(next);
    }

    // Initial measurement (after fonts are likely loaded)
    if (document.fonts?.ready) {
      document.fonts.ready.then(measure);
    } else {
      measure();
    }

    // Re-measure on resize (ResizeObserver may be unavailable in test envs)
    if (typeof ResizeObserver !== "undefined") {
      const observer = new ResizeObserver(measure);
      observer.observe(container);
      return () => observer.disconnect();
    }

    return undefined;
  }, [texts, font, lineHeight]);

  return { ref, widths };
}
