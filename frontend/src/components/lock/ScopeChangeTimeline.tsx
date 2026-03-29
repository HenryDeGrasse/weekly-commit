/**
 * ScopeChangeTimeline — chronological list of all post-lock scope changes.
 */
import { cn } from "../../lib/utils.js";
import type { ScopeChangeEventResponse, ScopeChangeCategory } from "../../api/planTypes.js";

const CATEGORY_LABELS: Record<ScopeChangeCategory, string> = {
  COMMIT_ADDED: "Commit Added",
  COMMIT_REMOVED: "Commit Removed",
  ESTIMATE_CHANGED: "Estimate Changed",
  CHESS_PIECE_CHANGED: "Chess Piece Changed",
  RCDO_CHANGED: "RCDO Changed",
  PRIORITY_CHANGED: "Priority Changed",
};

const CATEGORY_CLS: Record<ScopeChangeCategory, string> = {
  COMMIT_ADDED: "bg-neutral-800",
  COMMIT_REMOVED: "bg-neutral-600",
  ESTIMATE_CHANGED: "bg-neutral-500",
  CHESS_PIECE_CHANGED: "bg-neutral-500",
  RCDO_CHANGED: "bg-neutral-400",
  PRIORITY_CHANGED: "bg-neutral-700",
};

const CATEGORY_TEXT_CLS: Record<ScopeChangeCategory, string> = {
  COMMIT_ADDED: "text-foreground font-bold",
  COMMIT_REMOVED: "text-foreground underline",
  ESTIMATE_CHANGED: "text-foreground",
  CHESS_PIECE_CHANGED: "text-foreground",
  RCDO_CHANGED: "text-muted",
  PRIORITY_CHANGED: "text-foreground",
};

const CATEGORY_ICONS: Record<ScopeChangeCategory, string> = {
  COMMIT_ADDED: "+",
  COMMIT_REMOVED: "−",
  ESTIMATE_CHANGED: "~",
  CHESS_PIECE_CHANGED: "♟",
  RCDO_CHANGED: "◎",
  PRIORITY_CHANGED: "↕",
};

export interface ScopeChangeTimelineProps {
  readonly events: ScopeChangeEventResponse[];
}

export function ScopeChangeTimeline({ events }: ScopeChangeTimelineProps) {
  if (events.length === 0) {
    return (
      <div data-testid="scope-change-timeline-empty" className="py-3 text-center text-sm text-muted">
        No scope changes recorded.
      </div>
    );
  }

  return (
    <ol data-testid="scope-change-timeline" aria-label="Scope change timeline" className="list-none m-0 p-0 flex flex-col gap-2">
      {events.map((event) => {
        const badgeCls = CATEGORY_CLS[event.category] ?? "bg-neutral-500";
        const textCls = CATEGORY_TEXT_CLS[event.category] ?? "text-foreground";
        const icon = CATEGORY_ICONS[event.category] ?? "·";
        const label = CATEGORY_LABELS[event.category] ?? event.category;
        const ts = new Date(event.createdAt).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });

        return (
          <li key={event.id} data-testid={`scope-change-event-${event.id}`}
            className="flex gap-2.5 items-start rounded-default border border-border bg-surface p-2.5">
            <span className={cn("shrink-0 h-6 w-6 rounded-full flex items-center justify-center text-xs font-bold text-background", badgeCls)} aria-hidden="true">
              {icon}
            </span>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className={cn("text-xs font-semibold", textCls)}>{label}</span>
                <span className="text-xs text-muted">{ts}</span>
              </div>
              <p className="m-0 mt-1 text-xs text-muted">{event.reason}</p>
              {event.previousValue != null && event.newValue != null && (
                <div className="flex gap-1.5 mt-1 items-center text-xs flex-wrap">
                  <span className="font-mono px-1.5 py-px rounded bg-neutral-100 text-foreground line-through">{event.previousValue}</span>
                  <span className="text-muted">→</span>
                  <span className="font-mono px-1.5 py-px rounded bg-foreground/5 text-foreground font-semibold">{event.newValue}</span>
                </div>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
