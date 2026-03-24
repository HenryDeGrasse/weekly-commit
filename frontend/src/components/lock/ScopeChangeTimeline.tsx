/**
 * ScopeChangeTimeline — chronological list of all post-lock scope changes.
 */
import type { ScopeChangeEventResponse, ScopeChangeCategory } from "../../api/planTypes.js";

const CATEGORY_LABELS: Record<ScopeChangeCategory, string> = {
  COMMIT_ADDED: "Commit Added",
  COMMIT_REMOVED: "Commit Removed",
  ESTIMATE_CHANGED: "Estimate Changed",
  CHESS_PIECE_CHANGED: "Chess Piece Changed",
  RCDO_CHANGED: "RCDO Changed",
  PRIORITY_CHANGED: "Priority Changed",
};

const CATEGORY_COLORS: Record<ScopeChangeCategory, string> = {
  COMMIT_ADDED: "#15803d",
  COMMIT_REMOVED: "#991b1b",
  ESTIMATE_CHANGED: "#1e40af",
  CHESS_PIECE_CHANGED: "#1e40af",
  RCDO_CHANGED: "#6b21a8",
  PRIORITY_CHANGED: "#92400e",
};

const CATEGORY_ICONS: Record<ScopeChangeCategory, string> = {
  COMMIT_ADDED: "+",
  COMMIT_REMOVED: "−",
  ESTIMATE_CHANGED: "~",
  CHESS_PIECE_CHANGED: "♟",
  RCDO_CHANGED: "🎯",
  PRIORITY_CHANGED: "↕",
};

export interface ScopeChangeTimelineProps {
  readonly events: ScopeChangeEventResponse[];
}

export function ScopeChangeTimeline({ events }: ScopeChangeTimelineProps) {
  if (events.length === 0) {
    return (
      <div
        data-testid="scope-change-timeline-empty"
        style={{
          padding: "0.75rem",
          fontSize: "0.875rem",
          color: "var(--color-text-muted)",
          textAlign: "center",
        }}
      >
        No scope changes recorded.
      </div>
    );
  }

  return (
    <ol
      data-testid="scope-change-timeline"
      aria-label="Scope change timeline"
      style={{
        listStyle: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
      }}
    >
      {events.map((event) => {
        const color = CATEGORY_COLORS[event.category] ?? "#374151";
        const icon = CATEGORY_ICONS[event.category] ?? "·";
        const label = CATEGORY_LABELS[event.category] ?? event.category;
        const ts = new Date(event.createdAt).toLocaleString("en-US", {
          month: "short",
          day: "numeric",
          hour: "numeric",
          minute: "2-digit",
        });

        return (
          <li
            key={event.id}
            data-testid={`scope-change-event-${event.id}`}
            style={{
              background: "var(--color-surface)",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              padding: "0.625rem 0.75rem",
              display: "flex",
              gap: "0.625rem",
              alignItems: "flex-start",
            }}
          >
            {/* Type badge */}
            <span
              style={{
                flexShrink: 0,
                width: "1.5rem",
                height: "1.5rem",
                borderRadius: "50%",
                background: color,
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "0.75rem",
                fontWeight: 700,
              }}
              aria-hidden="true"
            >
              {icon}
            </span>

            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "0.5rem",
                  flexWrap: "wrap",
                }}
              >
                <span
                  style={{
                    fontWeight: 600,
                    fontSize: "0.8rem",
                    color,
                  }}
                >
                  {label}
                </span>
                <span
                  style={{
                    fontSize: "0.75rem",
                    color: "var(--color-text-muted)",
                  }}
                >
                  {ts}
                </span>
              </div>

              <p
                style={{
                  margin: "0.25rem 0 0",
                  fontSize: "0.8rem",
                  color: "var(--color-text-muted)",
                }}
              >
                {event.reason}
              </p>

              {event.previousValue != null && event.newValue != null && (
                <div
                  style={{
                    display: "flex",
                    gap: "0.375rem",
                    marginTop: "0.25rem",
                    alignItems: "center",
                    fontSize: "0.75rem",
                    flexWrap: "wrap",
                  }}
                >
                  <span
                    style={{
                      padding: "1px 6px",
                      background: "#fef2f2",
                      color: "#991b1b",
                      borderRadius: "3px",
                    }}
                  >
                    {event.previousValue}
                  </span>
                  <span style={{ color: "var(--color-text-muted)" }}>→</span>
                  <span
                    style={{
                      padding: "1px 6px",
                      background: "#f0fdf4",
                      color: "#15803d",
                      borderRadius: "3px",
                    }}
                  >
                    {event.newValue}
                  </span>
                </div>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
