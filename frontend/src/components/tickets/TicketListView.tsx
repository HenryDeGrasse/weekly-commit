/**
 * TicketListView — sortable, paginated table of tickets.
 *
 * Columns: key, title, status, assignee, team, priority, RCDO, target week.
 * Filters are applied externally (passed via props from Tickets route).
 * Sortable columns trigger `onSortChange`.
 */
import type {
  TicketSummaryResponse,
  TicketStatus,
  TicketPriority,
} from "../../api/ticketTypes.js";
import {
  TICKET_STATUS_LABELS,
  TICKET_PRIORITY_LABELS,
} from "../../api/ticketTypes.js";

export type TicketSortColumn =
  | "key"
  | "title"
  | "status"
  | "priority"
  | "updatedAt";

export interface TicketListViewProps {
  readonly tickets: TicketSummaryResponse[];
  readonly total: number;
  readonly page: number;
  readonly pageSize: number;
  readonly sortBy: TicketSortColumn;
  readonly sortDir: "asc" | "desc";
  readonly loading: boolean;
  readonly onPageChange: (page: number) => void;
  readonly onSortChange: (col: TicketSortColumn, dir: "asc" | "desc") => void;
  readonly onSelectTicket: (ticketId: string) => void;
  /** Labels for RCDO nodes by id. */
  readonly rcdoLabels?: Record<string, string>;
}

const STATUS_BADGE_STYLES: Record<TicketStatus, React.CSSProperties> = {
  TODO: { background: "#f1f5f9", color: "#475569" },
  IN_PROGRESS: { background: "#dbeafe", color: "#1e40af" },
  DONE: { background: "#d1fae5", color: "#065f46" },
  CANCELED: { background: "#f1f5f9", color: "#94a3b8" },
  BLOCKED: { background: "#fee2e2", color: "#991b1b" },
};

const PRIORITY_BADGE_STYLES: Record<TicketPriority, React.CSSProperties> = {
  CRITICAL: { background: "#fee2e2", color: "#991b1b", fontWeight: 700 },
  HIGH: { background: "#fef3c7", color: "#92400e" },
  MEDIUM: { background: "#eff6ff", color: "#1e40af" },
  LOW: { background: "#f1f5f9", color: "#475569" },
};

function StatusBadge({ status }: { readonly status: TicketStatus }) {
  return (
    <span
      style={{
        ...STATUS_BADGE_STYLES[status],
        padding: "2px 8px",
        borderRadius: "999px",
        fontSize: "0.7rem",
        fontWeight: 600,
        display: "inline-block",
        whiteSpace: "nowrap",
      }}
    >
      {TICKET_STATUS_LABELS[status]}
    </span>
  );
}

function PriorityBadge({ priority }: { readonly priority: TicketPriority }) {
  return (
    <span
      style={{
        ...PRIORITY_BADGE_STYLES[priority],
        padding: "2px 8px",
        borderRadius: "999px",
        fontSize: "0.7rem",
        display: "inline-block",
        whiteSpace: "nowrap",
      }}
    >
      {TICKET_PRIORITY_LABELS[priority]}
    </span>
  );
}

function SortIndicator({
  col,
  active,
  dir,
}: {
  readonly col: TicketSortColumn;
  readonly active: boolean;
  readonly dir: "asc" | "desc";
}) {
  void col;
  if (!active) return <span style={{ opacity: 0.3 }}>↕</span>;
  return <span>{dir === "asc" ? "↑" : "↓"}</span>;
}

const SORTABLE_COLUMNS: { col: TicketSortColumn; label: string }[] = [
  { col: "key", label: "Key" },
  { col: "title", label: "Title" },
  { col: "status", label: "Status" },
  { col: "priority", label: "Priority" },
  { col: "updatedAt", label: "Updated" },
];

export function TicketListView({
  tickets,
  total,
  page,
  pageSize,
  sortBy,
  sortDir,
  loading,
  onPageChange,
  onSortChange,
  onSelectTicket,
  rcdoLabels = {},
}: TicketListViewProps) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  function handleSortClick(col: TicketSortColumn) {
    if (sortBy === col) {
      onSortChange(col, sortDir === "asc" ? "desc" : "asc");
    } else {
      onSortChange(col, "asc");
    }
  }

  const thStyle: React.CSSProperties = {
    padding: "0.5rem 0.625rem",
    textAlign: "left",
    fontSize: "0.75rem",
    fontWeight: 700,
    textTransform: "uppercase",
    letterSpacing: "0.04em",
    color: "var(--color-text-muted)",
    borderBottom: "2px solid var(--color-border)",
    whiteSpace: "nowrap",
    cursor: "pointer",
    userSelect: "none",
  };
  const tdStyle: React.CSSProperties = {
    padding: "0.5rem 0.625rem",
    fontSize: "0.875rem",
    borderBottom: "1px solid var(--color-border)",
    verticalAlign: "middle",
  };

  return (
    <div data-testid="ticket-list-view" style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
      {loading && (
        <div
          role="status"
          aria-label="Loading tickets"
          data-testid="ticket-list-loading"
          style={{ color: "var(--color-text-muted)", fontSize: "0.875rem", padding: "0.5rem" }}
        >
          Loading tickets…
        </div>
      )}

      {!loading && tickets.length === 0 && (
        <div
          data-testid="ticket-list-empty"
          style={{
            padding: "2rem",
            textAlign: "center",
            color: "var(--color-text-muted)",
            fontSize: "0.9rem",
          }}
        >
          No tickets found matching your filters.
        </div>
      )}

      {!loading && tickets.length > 0 && (
        <div style={{ overflowX: "auto" }}>
          <table
            data-testid="ticket-list-table"
            style={{
              width: "100%",
              borderCollapse: "collapse",
              fontSize: "0.875rem",
            }}
          >
            <thead>
              <tr>
                {SORTABLE_COLUMNS.map(({ col, label }) => (
                  <th
                    key={col}
                    style={thStyle}
                    onClick={() => handleSortClick(col)}
                    data-testid={`sort-col-${col}`}
                    aria-sort={
                      sortBy === col
                        ? sortDir === "asc"
                          ? "ascending"
                          : "descending"
                        : "none"
                    }
                  >
                    {label}{" "}
                    <SortIndicator
                      col={col}
                      active={sortBy === col}
                      dir={sortDir}
                    />
                  </th>
                ))}
                <th style={thStyle}>Assignee</th>
                <th style={thStyle}>Team</th>
                <th style={thStyle}>RCDO</th>
                <th style={thStyle}>Target Week</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((ticket) => (
                <tr
                  key={ticket.id}
                  data-testid={`ticket-row-${ticket.id}`}
                  onClick={() => onSelectTicket(ticket.id)}
                  style={{
                    cursor: "pointer",
                    transition: "background 0.1s",
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLTableRowElement).style.background =
                      "var(--color-background)";
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLTableRowElement).style.background =
                      "";
                  }}
                >
                  <td style={tdStyle}>
                    <span
                      data-testid={`ticket-key-${ticket.id}`}
                      style={{ fontFamily: "monospace", fontSize: "0.8rem", fontWeight: 600 }}
                    >
                      {ticket.key}
                    </span>
                  </td>
                  <td style={{ ...tdStyle, maxWidth: "250px" }}>
                    <span
                      data-testid={`ticket-title-${ticket.id}`}
                      style={{
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                        display: "block",
                      }}
                    >
                      {ticket.title}
                    </span>
                  </td>
                  <td style={tdStyle}>
                    <StatusBadge status={ticket.status} />
                  </td>
                  <td style={tdStyle}>
                    <PriorityBadge priority={ticket.priority} />
                  </td>
                  <td style={{ ...tdStyle, fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                    {new Date(ticket.updatedAt).toLocaleDateString()}
                  </td>
                  <td style={{ ...tdStyle, fontSize: "0.8rem" }}>
                    {ticket.assigneeUserId ?? (
                      <span style={{ color: "var(--color-text-muted)" }}>—</span>
                    )}
                  </td>
                  <td style={{ ...tdStyle, fontSize: "0.8rem" }}>
                    {ticket.teamId}
                  </td>
                  <td style={{ ...tdStyle, fontSize: "0.75rem", color: "var(--color-text-muted)" }}>
                    {ticket.rcdoNodeId
                      ? (rcdoLabels[ticket.rcdoNodeId] ?? ticket.rcdoNodeId)
                      : "—"}
                  </td>
                  <td style={{ ...tdStyle, fontSize: "0.8rem" }}>
                    {ticket.targetWeekStartDate ?? (
                      <span style={{ color: "var(--color-text-muted)" }}>—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {total > pageSize && (
        <div
          data-testid="ticket-list-pagination"
          style={{
            display: "flex",
            alignItems: "center",
            gap: "0.5rem",
            justifyContent: "flex-end",
            flexWrap: "wrap",
          }}
        >
          <span style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
            {(page - 1) * pageSize + 1}–
            {Math.min(page * pageSize, total)} of {total}
          </span>
          <button
            type="button"
            onClick={() => onPageChange(page - 1)}
            disabled={page <= 1}
            data-testid="ticket-page-prev"
            style={{
              padding: "0.3rem 0.625rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              background: "var(--color-surface)",
              cursor: page <= 1 ? "not-allowed" : "pointer",
              fontFamily: "inherit",
              fontSize: "0.8rem",
            }}
          >
            ◀ Prev
          </button>
          <span
            data-testid="ticket-page-indicator"
            style={{ fontSize: "0.8rem" }}
          >
            {page} / {totalPages}
          </span>
          <button
            type="button"
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages}
            data-testid="ticket-page-next"
            style={{
              padding: "0.3rem 0.625rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              background: "var(--color-surface)",
              cursor: page >= totalPages ? "not-allowed" : "pointer",
              fontFamily: "inherit",
              fontSize: "0.8rem",
            }}
          >
            Next ▶
          </button>
        </div>
      )}
    </div>
  );
}
