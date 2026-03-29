/**
 * CollapsibleSection — a bordered section with a toggle header that persists
 * its open/close state to localStorage.
 *
 * Designed to wrap My Week sections (AI Lint Panel, AI Insights, etc.) so that
 * power users keep their preferred layout across page reloads.
 *
 * Accessibility:
 *   - Header button carries aria-expanded and aria-controls
 *   - Content div carries role="region" and aria-labelledby
 *
 * Height transition uses the CSS grid-template-rows trick (0fr ↔ 1fr) which
 * avoids the "height: auto" animation problem without JavaScript measurement.
 */
import { type ReactNode } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "../../lib/utils.js";
import { usePersistedState } from "./usePersistedState.js";

export interface CollapsibleSectionProps {
  /** Unique identifier — stored under `wc-section-{id}` in localStorage. */
  readonly id: string;
  /** Section heading text. */
  readonly title: string;
  /**
   * Whether the section is open on first visit (before localStorage has a
   * value). Defaults to `true`.
   */
  readonly defaultExpanded?: boolean;
  /** Optional badge rendered next to the title (e.g. "3 suggestions" count). */
  readonly badge?: ReactNode;
  readonly children: ReactNode;
  readonly className?: string;
}

export function CollapsibleSection({
  id,
  title,
  defaultExpanded = true,
  badge,
  children,
  className,
}: CollapsibleSectionProps) {
  const storageKey = `wc-section-${id}`;
  const headerId = `collapsible-header-${id}`;
  const contentId = `collapsible-content-${id}`;

  const [expanded, setExpanded] = usePersistedState<boolean>(
    storageKey,
    defaultExpanded,
  );

  return (
    <div
      className={cn("rounded-default border border-border bg-surface", className)}
      data-testid={`collapsible-section-${id}`}
    >
      {/* ── Toggle header ─────────────────────────────────────────────── */}
      <button
        id={headerId}
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        aria-expanded={expanded}
        aria-controls={contentId}
        className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-muted-bg/50"
        data-testid={`collapsible-header-${id}`}
      >
        <div className="flex min-w-0 items-center gap-2">
          <span className="truncate text-sm font-bold leading-none">{title}</span>
          {badge != null && <span className="shrink-0">{badge}</span>}
        </div>
        <ChevronDown
          className={cn(
            "h-4 w-4 shrink-0 text-muted transition-transform duration-200",
            expanded && "rotate-180",
          )}
          aria-hidden="true"
        />
      </button>

      {/* ── Collapsible content — grid 0fr/1fr height transition ─────── */}
      <div
        id={contentId}
        role="region"
        aria-labelledby={headerId}
        className="grid transition-[grid-template-rows] duration-200 ease-in-out"
        style={{ gridTemplateRows: expanded ? "1fr" : "0fr" }}
        data-testid={`collapsible-content-${id}`}
      >
        {/* overflow-hidden on the direct grid child is required for 0fr to collapse */}
        <div className="overflow-hidden">
          <div className="px-4 pb-3 pt-0">{children}</div>
        </div>
      </div>
    </div>
  );
}
