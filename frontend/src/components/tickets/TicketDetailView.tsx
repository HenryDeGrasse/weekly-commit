/**
 * TicketDetailView — full ticket detail panel.
 *
 * Shows:
 *   - Ticket key, title, status with transition buttons
 *   - Description
 *   - Priority, assignee, reporter, team, RCDO path, estimate, target week
 *   - Status history timeline
 *   - Linked commits list
 *   - Assignment change input
 */
import { useState, useCallback } from "react";
import type {
  TicketResponse,
  TicketStatus,
  TicketStatusHistory,
  LinkedCommitEntry,
} from "../../api/ticketTypes.js";
import type { CommitOutcome } from "../../api/planTypes.js";
import {
  TICKET_STATUS_LABELS,
  TICKET_STATUS_TRANSITIONS,
  TICKET_PRIORITY_LABELS,
} from "../../api/ticketTypes.js";

export interface TicketDetailViewProps {
  readonly ticket: TicketResponse;
  /** Called when status transition is requested. May throw to surface error. */
  readonly onStatusTransition: (ticketId: string, newStatus: TicketStatus) => Promise<void>;
  /** Called when assignee is changed. */
  readonly onAssigneeChange: (ticketId: string, assigneeUserId: string) => Promise<void>;
  /** Optional RCDO path label. */
  readonly rcdoPath?: string | undefined;
  readonly onClose?: () => void;
  readonly onEdit?: () => void;
}

const STATUS_COLORS: Record<TicketStatus, { bg: string; color: string }> = {
  TODO: { bg: "#f1f5f9", color: "#475569" },
  IN_PROGRESS: { bg: "#dbeafe", color: "#1e40af" },
  DONE: { bg: "#d1fae5", color: "#065f46" },
  CANCELED: { bg: "#f1f5f9", color: "#94a3b8" },
  BLOCKED: { bg: "#fee2e2", color: "#991b1b" },
};

const OUTCOME_LABELS: Record<CommitOutcome, string> = {
  ACHIEVED: "✓ Achieved",
  PARTIALLY_ACHIEVED: "◑ Partial",
  NOT_ACHIEVED: "✗ Not achieved",
  CANCELED: "— Canceled",
};
const OUTCOME_COLORS: Record<CommitOutcome, string> = {
  ACHIEVED: "var(--color-success)",
  PARTIALLY_ACHIEVED: "var(--color-warning)",
  NOT_ACHIEVED: "var(--color-danger)",
  CANCELED: "var(--color-text-muted)",
};

function StatusHistoryTimeline({
  history,
}: {
  readonly history: TicketStatusHistory[];
}) {
  if (history.length === 0) {
    return (
      <p
        data-testid="status-history-empty"
        style={{ fontSize: "0.875rem", color: "var(--color-text-muted)" }}
      >
        No status changes recorded.
      </p>
    );
  }

  return (
    <ol
      data-testid="status-history-timeline"
      style={{
        listStyle: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
      }}
    >
      {[...history].reverse().map((entry) => (
        <li
          key={entry.id}
          data-testid={`history-entry-${entry.id}`}
          style={{
            display: "flex",
            alignItems: "flex-start",
            gap: "0.625rem",
            fontSize: "0.8rem",
          }}
        >
          <span
            style={{
              marginTop: "2px",
              width: "8px",
              height: "8px",
              borderRadius: "50%",
              background: "var(--color-primary)",
              flexShrink: 0,
            }}
          />
          <div>
            <span style={{ color: "var(--color-text-muted)" }}>
              {new Date(entry.changedAt).toLocaleString()}
            </span>{" "}
            —{" "}
            {entry.fromStatus ? (
              <>
                <strong>{TICKET_STATUS_LABELS[entry.fromStatus]}</strong>
                {" → "}
              </>
            ) : null}
            <strong>{TICKET_STATUS_LABELS[entry.toStatus]}</strong>
            {entry.note && (
              <span style={{ color: "var(--color-text-muted)" }}>
                {" "}({entry.note})
              </span>
            )}
          </div>
        </li>
      ))}
    </ol>
  );
}

function LinkedCommitsList({
  commits,
}: {
  readonly commits: LinkedCommitEntry[];
}) {
  if (commits.length === 0) {
    return (
      <p
        data-testid="linked-commits-empty"
        style={{ fontSize: "0.875rem", color: "var(--color-text-muted)" }}
      >
        No linked commits.
      </p>
    );
  }

  return (
    <ul
      data-testid="linked-commits-list"
      style={{
        listStyle: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: "0.375rem",
      }}
    >
      {commits.map((c) => (
        <li
          key={c.commitId}
          data-testid={`linked-commit-${c.commitId}`}
          style={{
            background: "var(--color-background)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            padding: "0.5rem 0.75rem",
            fontSize: "0.85rem",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: "0.375rem",
          }}
        >
          <div>
            <span style={{ fontWeight: 600 }}>{c.commitTitle}</span>
            <span
              style={{
                marginLeft: "0.5rem",
                fontSize: "0.75rem",
                color: "var(--color-text-muted)",
              }}
            >
              {c.weekStartDate} · {c.chessPiece}
              {c.estimatePoints != null ? ` · ${c.estimatePoints}pts` : ""}
            </span>
          </div>
          {c.outcome && (
            <span
              data-testid={`linked-commit-outcome-${c.commitId}`}
              style={{
                fontSize: "0.75rem",
                color: OUTCOME_COLORS[c.outcome],
                fontWeight: 600,
              }}
            >
              {OUTCOME_LABELS[c.outcome]}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

const sectionHeadingStyle: React.CSSProperties = {
  fontSize: "0.85rem",
  fontWeight: 700,
  textTransform: "uppercase",
  letterSpacing: "0.04em",
  color: "var(--color-text-muted)",
  margin: "0 0 0.5rem",
};

const metaRowStyle: React.CSSProperties = {
  display: "flex",
  gap: "0.375rem",
  fontSize: "0.875rem",
  alignItems: "baseline",
};

const metaLabelStyle: React.CSSProperties = {
  fontWeight: 600,
  minWidth: "100px",
  flexShrink: 0,
  color: "var(--color-text-muted)",
};

export function TicketDetailView({
  ticket,
  onStatusTransition,
  onAssigneeChange,
  rcdoPath,
  onClose,
  onEdit,
}: TicketDetailViewProps) {
  const [transitioning, setTransitioning] = useState<TicketStatus | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);
  const [assigneeInput, setAssigneeInput] = useState(ticket.assigneeUserId ?? "");
  const [assigneeSaving, setAssigneeSaving] = useState(false);
  const [assigneeError, setAssigneeError] = useState<string | null>(null);

  const nextStatuses = TICKET_STATUS_TRANSITIONS[ticket.status];

  const handleTransition = useCallback(
    async (newStatus: TicketStatus) => {
      setTransitioning(newStatus);
      setTransitionError(null);
      try {
        await onStatusTransition(ticket.id, newStatus);
      } catch (err) {
        setTransitionError(
          err instanceof Error ? err.message : "Status transition failed.",
        );
      } finally {
        setTransitioning(null);
      }
    },
    [ticket.id, onStatusTransition],
  );

  const handleAssigneeChange = useCallback(async () => {
    setAssigneeSaving(true);
    setAssigneeError(null);
    try {
      await onAssigneeChange(ticket.id, assigneeInput.trim());
    } catch (err) {
      setAssigneeError(
        err instanceof Error ? err.message : "Assignment change failed.",
      );
    } finally {
      setAssigneeSaving(false);
    }
  }, [ticket.id, assigneeInput, onAssigneeChange]);

  const { bg, color } = STATUS_COLORS[ticket.status];

  return (
    <div
      data-testid="ticket-detail-view"
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
        borderRadius: "var(--border-radius)",
        display: "flex",
        flexDirection: "column",
        gap: "1.25rem",
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: "1rem 1.25rem",
          borderBottom: "1px solid var(--color-border)",
          display: "flex",
          alignItems: "flex-start",
          justifyContent: "space-between",
          gap: "1rem",
          flexWrap: "wrap",
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem", flex: 1 }}>
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexWrap: "wrap" }}>
            <span
              data-testid="ticket-detail-key"
              style={{
                fontFamily: "monospace",
                fontSize: "0.85rem",
                fontWeight: 700,
                color: "var(--color-text-muted)",
              }}
            >
              {ticket.key}
            </span>
            <span
              data-testid="ticket-detail-status-badge"
              style={{
                background: bg,
                color,
                padding: "2px 9px",
                borderRadius: "999px",
                fontSize: "0.75rem",
                fontWeight: 700,
              }}
            >
              {TICKET_STATUS_LABELS[ticket.status]}
            </span>
          </div>
          <h2
            data-testid="ticket-detail-title"
            style={{ margin: 0, fontSize: "1.1rem", fontWeight: 700 }}
          >
            {ticket.title}
          </h2>
        </div>

        {/* Action buttons */}
        <div style={{ display: "flex", gap: "0.5rem", flexShrink: 0 }}>
          {onEdit && (
            <button
              type="button"
              onClick={onEdit}
              data-testid="ticket-detail-edit-btn"
              style={{
                padding: "0.375rem 0.75rem",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                background: "var(--color-surface)",
                cursor: "pointer",
                fontFamily: "inherit",
                fontSize: "0.8rem",
              }}
            >
              ✏ Edit
            </button>
          )}
          {onClose && (
            <button
              type="button"
              onClick={onClose}
              aria-label="Close ticket detail"
              data-testid="ticket-detail-close-btn"
              style={{
                padding: "0.375rem 0.5rem",
                border: "none",
                borderRadius: "var(--border-radius)",
                background: "none",
                cursor: "pointer",
                fontSize: "1rem",
                color: "var(--color-text-muted)",
              }}
            >
              ✕
            </button>
          )}
        </div>
      </div>

      <div style={{ padding: "0 1.25rem 1.25rem", display: "flex", flexDirection: "column", gap: "1.25rem" }}>
        {/* Status transitions */}
        {nextStatuses.length > 0 && (
          <div>
            <p style={sectionHeadingStyle}>Status Transitions</p>
            <div
              data-testid="status-transition-buttons"
              style={{ display: "flex", gap: "0.375rem", flexWrap: "wrap" }}
            >
              {nextStatuses.map((next) => (
                <button
                  key={next}
                  type="button"
                  onClick={() => void handleTransition(next)}
                  disabled={transitioning !== null}
                  data-testid={`transition-btn-${next.toLowerCase()}`}
                  style={{
                    padding: "0.375rem 0.875rem",
                    border: "1px solid var(--color-border)",
                    borderRadius: "var(--border-radius)",
                    background: STATUS_COLORS[next].bg,
                    color: STATUS_COLORS[next].color,
                    cursor: transitioning !== null ? "not-allowed" : "pointer",
                    fontFamily: "inherit",
                    fontSize: "0.8rem",
                    fontWeight: 600,
                  }}
                >
                  {transitioning === next
                    ? "…"
                    : `→ ${TICKET_STATUS_LABELS[next]}`}
                </button>
              ))}
            </div>
            {transitionError && (
              <span
                data-testid="transition-error"
                role="alert"
                style={{ fontSize: "0.8rem", color: "var(--color-danger)", marginTop: "0.375rem", display: "block" }}
              >
                {transitionError}
              </span>
            )}
          </div>
        )}

        {/* Description */}
        {ticket.description && (
          <div>
            <p style={sectionHeadingStyle}>Description</p>
            <p
              data-testid="ticket-detail-description"
              style={{ margin: 0, fontSize: "0.875rem", lineHeight: 1.6 }}
            >
              {ticket.description}
            </p>
          </div>
        )}

        {/* Metadata */}
        <div>
          <p style={sectionHeadingStyle}>Details</p>
          <div
            data-testid="ticket-detail-meta"
            style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}
          >
            <div style={metaRowStyle}>
              <span style={metaLabelStyle}>Priority</span>
              <span data-testid="ticket-detail-priority">
                {TICKET_PRIORITY_LABELS[ticket.priority]}
              </span>
            </div>
            <div style={metaRowStyle}>
              <span style={metaLabelStyle}>Reporter</span>
              <span data-testid="ticket-detail-reporter">{ticket.reporterUserId}</span>
            </div>
            <div style={metaRowStyle}>
              <span style={metaLabelStyle}>Team</span>
              <span data-testid="ticket-detail-team">{ticket.teamId}</span>
            </div>
            {ticket.rcdoNodeId && (
              <div style={metaRowStyle}>
                <span style={metaLabelStyle}>RCDO</span>
                <span data-testid="ticket-detail-rcdo">
                  {rcdoPath ?? ticket.rcdoNodeId}
                </span>
              </div>
            )}
            {ticket.estimatePoints != null && (
              <div style={metaRowStyle}>
                <span style={metaLabelStyle}>Estimate</span>
                <span data-testid="ticket-detail-estimate">
                  {ticket.estimatePoints} pts
                </span>
              </div>
            )}
            {ticket.targetWeekStartDate && (
              <div style={metaRowStyle}>
                <span style={metaLabelStyle}>Target Week</span>
                <span data-testid="ticket-detail-target-week">
                  {ticket.targetWeekStartDate}
                </span>
              </div>
            )}
            <div style={metaRowStyle}>
              <span style={metaLabelStyle}>Created</span>
              <span>{new Date(ticket.createdAt).toLocaleString()}</span>
            </div>
          </div>
        </div>

        {/* Assignment change */}
        <div>
          <p style={sectionHeadingStyle}>Assignment</p>
          <div
            data-testid="ticket-assignment-section"
            style={{ display: "flex", gap: "0.5rem", alignItems: "center", flexWrap: "wrap" }}
          >
            <input
              type="text"
              value={assigneeInput}
              onChange={(e) => setAssigneeInput(e.target.value)}
              placeholder="User ID (leave blank to unassign)"
              data-testid="ticket-assignee-input"
              style={{
                padding: "0.4rem 0.625rem",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                background: "var(--color-surface)",
                color: "var(--color-text)",
                fontFamily: "inherit",
                fontSize: "0.875rem",
                minWidth: "180px",
              }}
            />
            <button
              type="button"
              onClick={() => void handleAssigneeChange()}
              disabled={assigneeSaving}
              data-testid="ticket-assign-save-btn"
              style={{
                padding: "0.4rem 0.875rem",
                border: "none",
                borderRadius: "var(--border-radius)",
                background: "var(--color-primary)",
                color: "#fff",
                cursor: assigneeSaving ? "not-allowed" : "pointer",
                fontFamily: "inherit",
                fontSize: "0.875rem",
                fontWeight: 600,
              }}
            >
              {assigneeSaving ? "Saving…" : "Update"}
            </button>
          </div>
          {assigneeError && (
            <span
              data-testid="assignee-error"
              role="alert"
              style={{ fontSize: "0.8rem", color: "var(--color-danger)", marginTop: "0.375rem", display: "block" }}
            >
              {assigneeError}
            </span>
          )}
        </div>

        {/* Status history */}
        <div>
          <p style={sectionHeadingStyle}>Status History</p>
          <StatusHistoryTimeline history={ticket.statusHistory} />
        </div>

        {/* Linked commits */}
        <div>
          <p style={sectionHeadingStyle}>Linked Commits</p>
          <LinkedCommitsList commits={ticket.linkedCommits} />
        </div>
      </div>
    </div>
  );
}
