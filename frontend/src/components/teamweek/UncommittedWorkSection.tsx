/**
 * UncommittedWorkSection — two sub-lists of tickets that need attention.
 *
 * List 1: Assigned tickets with no linked commit (member is responsible but
 *         hasn't planned the work).
 * List 2: Unassigned tickets targeted to this week (no owner, no plan).
 *
 * Quick-assign action allows assigning unassigned tickets to a team member
 * (calls PUT /api/tickets/{id} with assigneeUserId).
 */
import { useState } from "react";
import type { UncommittedTicketSummary, TicketStatus } from "../../api/teamTypes.js";

const STATUS_STYLES: Record<TicketStatus, { bg: string; color: string }> = {
  TODO: { bg: "#f3f4f6", color: "#374151" },
  IN_PROGRESS: { bg: "#dbeafe", color: "#1e40af" },
  DONE: { bg: "#d1fae5", color: "#065f46" },
  CANCELED: { bg: "#f3f4f6", color: "#6b7280" },
  BLOCKED: { bg: "#fee2e2", color: "#991b1b" },
};

export interface UncommittedWorkSectionProps {
  /** Tickets assigned to team members but with no linked commit in this week's plans. */
  readonly assignedTickets: UncommittedTicketSummary[];
  /** Tickets targeted to this week but with no assignee. */
  readonly unassignedTickets: UncommittedTicketSummary[];
  /**
   * Called when the manager quick-assigns an unassigned ticket.
   * The parent component calls the API and refreshes data.
   */
  readonly onQuickAssign?: (ticketId: string, assigneeUserId: string) => Promise<void>;
}

function TicketRow({
  ticket,
  testIdPrefix,
}: {
  readonly ticket: UncommittedTicketSummary;
  readonly testIdPrefix: string;
}) {
  const statusStyle = STATUS_STYLES[ticket.status] ?? { bg: "#f3f4f6", color: "#374151" };

  return (
    <tr
      data-testid={`${testIdPrefix}-ticket-${ticket.id}`}
      style={{ borderBottom: "1px solid var(--color-border)" }}
    >
      <td style={{ padding: "0.5rem 0.75rem" }}>
        <span
          style={{
            fontFamily: "monospace",
            fontSize: "0.75rem",
            fontWeight: 700,
            color: "var(--color-primary)",
          }}
        >
          {ticket.key}
        </span>
      </td>
      <td style={{ padding: "0.5rem 0.75rem", fontSize: "0.875rem" }}>
        {ticket.title}
        {ticket.estimatePoints != null && (
          <span
            style={{
              marginLeft: "0.375rem",
              fontSize: "0.7rem",
              padding: "1px 6px",
              borderRadius: "999px",
              background: "#e0e7ff",
              color: "#3730a3",
              fontWeight: 700,
            }}
          >
            {ticket.estimatePoints}pt
          </span>
        )}
      </td>
      <td style={{ padding: "0.5rem 0.75rem", fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
        {ticket.assigneeUserId ?? <em>Unassigned</em>}
      </td>
      <td style={{ padding: "0.5rem 0.75rem" }}>
        <span
          style={{
            fontSize: "0.7rem",
            fontWeight: 600,
            padding: "1px 6px",
            borderRadius: "999px",
            ...statusStyle,
          }}
        >
          {ticket.status}
        </span>
      </td>
      <td style={{ padding: "0.5rem 0.75rem", fontSize: "0.75rem", color: "var(--color-text-muted)" }}>
        {ticket.targetWeekStartDate ?? "—"}
      </td>
    </tr>
  );
}

function QuickAssignRow({
  ticket,
  onAssign,
}: {
  readonly ticket: UncommittedTicketSummary;
  readonly onAssign?: (ticketId: string, assigneeUserId: string) => Promise<void>;
}) {
  const [userId, setUserId] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const statusStyle = STATUS_STYLES[ticket.status] ?? { bg: "#f3f4f6", color: "#374151" };

  const handleAssign = async () => {
    if (!userId.trim() || !onAssign) return;
    setSaving(true);
    setError(null);
    try {
      await onAssign(ticket.id, userId.trim());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Assign failed");
    } finally {
      setSaving(false);
    }
  };

  return (
    <tr
      data-testid={`unassigned-ticket-${ticket.id}`}
      style={{ borderBottom: "1px solid var(--color-border)" }}
    >
      <td style={{ padding: "0.5rem 0.75rem" }}>
        <span style={{ fontFamily: "monospace", fontSize: "0.75rem", fontWeight: 700, color: "var(--color-primary)" }}>
          {ticket.key}
        </span>
      </td>
      <td style={{ padding: "0.5rem 0.75rem", fontSize: "0.875rem" }}>
        {ticket.title}
        {ticket.estimatePoints != null && (
          <span style={{ marginLeft: "0.375rem", fontSize: "0.7rem", padding: "1px 6px", borderRadius: "999px", background: "#e0e7ff", color: "#3730a3", fontWeight: 700 }}>
            {ticket.estimatePoints}pt
          </span>
        )}
      </td>
      <td style={{ padding: "0.5rem 0.75rem" }}>
        <em style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>Unassigned</em>
      </td>
      <td style={{ padding: "0.5rem 0.75rem" }}>
        <span style={{ fontSize: "0.7rem", fontWeight: 600, padding: "1px 6px", borderRadius: "999px", ...statusStyle }}>
          {ticket.status}
        </span>
      </td>
      <td style={{ padding: "0.5rem 0.75rem", fontSize: "0.75rem", color: "var(--color-text-muted)" }}>
        {ticket.targetWeekStartDate ?? "—"}
      </td>
      {onAssign && (
        <td style={{ padding: "0.5rem 0.75rem" }}>
          <div style={{ display: "flex", gap: "0.25rem", alignItems: "center" }}>
            <input
              type="text"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="User ID"
              data-testid={`quick-assign-input-${ticket.id}`}
              style={{
                padding: "0.25rem 0.5rem",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                fontSize: "0.75rem",
                width: "100px",
                fontFamily: "inherit",
              }}
            />
            <button
              type="button"
              onClick={() => void handleAssign()}
              disabled={!userId.trim() || saving}
              data-testid={`quick-assign-btn-${ticket.id}`}
              style={{
                padding: "0.25rem 0.5rem",
                border: "none",
                borderRadius: "var(--border-radius)",
                background: "var(--color-primary)",
                color: "#fff",
                fontSize: "0.75rem",
                cursor: userId.trim() && !saving ? "pointer" : "not-allowed",
                opacity: !userId.trim() || saving ? 0.5 : 1,
                fontFamily: "inherit",
              }}
            >
              {saving ? "…" : "Assign"}
            </button>
            {error && (
              <span style={{ fontSize: "0.7rem", color: "var(--color-danger)" }}>{error}</span>
            )}
          </div>
        </td>
      )}
    </tr>
  );
}

const TABLE_HEADERS = (showAssignCol: boolean) => (
  <tr style={{ background: "var(--color-background)", borderBottom: "1px solid var(--color-border)" }}>
    <th style={{ padding: "0.375rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--color-text-muted)" }}>Key</th>
    <th style={{ padding: "0.375rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--color-text-muted)" }}>Title</th>
    <th style={{ padding: "0.375rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--color-text-muted)" }}>Assignee</th>
    <th style={{ padding: "0.375rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--color-text-muted)" }}>Status</th>
    <th style={{ padding: "0.375rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--color-text-muted)" }}>Week</th>
    {showAssignCol && (
      <th style={{ padding: "0.375rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--color-text-muted)" }}>Assign</th>
    )}
  </tr>
);

export function UncommittedWorkSection({
  assignedTickets,
  unassignedTickets,
  onQuickAssign,
}: UncommittedWorkSectionProps) {
  const isEmpty = assignedTickets.length === 0 && unassignedTickets.length === 0;

  if (isEmpty) {
    return (
      <section aria-labelledby="uncommitted-heading" data-testid="uncommitted-work-section">
        <h3 id="uncommitted-heading" style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}>
          Uncommitted Work
        </h3>
        <p
          data-testid="no-uncommitted-work"
          style={{ color: "var(--color-success)", fontSize: "0.875rem", fontWeight: 600 }}
        >
          ✓ No uncommitted work for this week.
        </p>
      </section>
    );
  }

  return (
    <section aria-labelledby="uncommitted-heading" data-testid="uncommitted-work-section">
      <h3 id="uncommitted-heading" style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}>
        Uncommitted Work
      </h3>
      <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
        {/* Sub-list 1: Assigned but not linked */}
        <div
          data-testid="assigned-uncommitted-section"
          style={{
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              padding: "0.625rem 0.75rem",
              background: "#fef3c7",
              borderBottom: "1px solid var(--color-border)",
              fontSize: "0.8rem",
              fontWeight: 600,
              color: "#92400e",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <span>⚠ Assigned — no commit planned</span>
            <span
              data-testid="assigned-uncommitted-count"
              style={{ fontWeight: 700 }}
            >
              {assignedTickets.length}
            </span>
          </div>
          {assignedTickets.length === 0 ? (
            <p style={{ margin: 0, padding: "0.75rem", fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
              None.
            </p>
          ) : (
            <table
              data-testid="assigned-uncommitted-table"
              style={{ width: "100%", borderCollapse: "collapse" }}
              aria-label="Assigned tickets without commits"
            >
              <thead>{TABLE_HEADERS(false)}</thead>
              <tbody>
                {assignedTickets.map((ticket) => (
                  <TicketRow key={ticket.id} ticket={ticket} testIdPrefix="assigned" />
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Sub-list 2: Unassigned tickets */}
        <div
          data-testid="unassigned-section"
          style={{
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              padding: "0.625rem 0.75rem",
              background: "#fee2e2",
              borderBottom: "1px solid var(--color-border)",
              fontSize: "0.8rem",
              fontWeight: 600,
              color: "#991b1b",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <span>🚨 Unassigned — targeted this week</span>
            <span
              data-testid="unassigned-count"
              style={{ fontWeight: 700 }}
            >
              {unassignedTickets.length}
            </span>
          </div>
          {unassignedTickets.length === 0 ? (
            <p style={{ margin: 0, padding: "0.75rem", fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
              None.
            </p>
          ) : (
            <table
              data-testid="unassigned-tickets-table"
              style={{ width: "100%", borderCollapse: "collapse" }}
              aria-label="Unassigned tickets"
            >
              <thead>{TABLE_HEADERS(onQuickAssign !== undefined)}</thead>
              <tbody>
                {unassignedTickets.map((ticket) => (
                  <QuickAssignRow
                    key={ticket.id}
                    ticket={ticket}
                    {...(onQuickAssign ? { onAssign: onQuickAssign } : {})}
                  />
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </section>
  );
}
