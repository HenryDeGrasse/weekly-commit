/**
 * TicketListView — sortable, paginated table of tickets.
 */
import { ChevronLeft, ChevronRight, ArrowUpDown, ArrowUp, ArrowDown } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { cn } from "../../lib/utils.js";
import type { TicketSummaryResponse, TicketStatus, TicketPriority } from "../../api/ticketTypes.js";
import { TICKET_STATUS_LABELS, TICKET_PRIORITY_LABELS } from "../../api/ticketTypes.js";

export type TicketSortColumn = "key" | "title" | "status" | "priority" | "updatedAt";

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
  readonly rcdoLabels?: Record<string, string>;
}

const STATUS_BADGE_VARIANT: Record<TicketStatus, "default" | "primary" | "success" | "danger"> = {
  TODO: "default", IN_PROGRESS: "primary", DONE: "success", CANCELED: "default", BLOCKED: "danger",
};
const PRIORITY_BADGE_VARIANT: Record<TicketPriority, "danger" | "warning" | "primary" | "default"> = {
  CRITICAL: "danger", HIGH: "warning", MEDIUM: "primary", LOW: "default",
};

const SORTABLE_COLUMNS: { col: TicketSortColumn; label: string }[] = [
  { col: "key", label: "Key" }, { col: "title", label: "Title" },
  { col: "status", label: "Status" }, { col: "priority", label: "Priority" }, { col: "updatedAt", label: "Updated" },
];

const thCls = "px-2.5 py-2 text-left text-[0.7rem] font-bold uppercase tracking-wider text-muted border-b-2 border-border whitespace-nowrap cursor-pointer select-none hover:text-foreground transition-colors";
const tdCls = "px-2.5 py-2 text-sm border-b border-border align-middle";

export function TicketListView({ tickets, total, page, pageSize, sortBy, sortDir, loading, onPageChange, onSortChange, onSelectTicket, rcdoLabels = {} }: TicketListViewProps) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  function handleSortClick(col: TicketSortColumn) {
    if (sortBy === col) { onSortChange(col, sortDir === "asc" ? "desc" : "asc"); }
    else { onSortChange(col, "asc"); }
  }

  return (
    <div data-testid="ticket-list-view" className="flex flex-col gap-3">
      {loading && <div role="status" aria-label="Loading tickets" data-testid="ticket-list-loading" className="text-sm text-muted px-2">Loading tickets…</div>}

      {!loading && tickets.length === 0 && (
        <div data-testid="ticket-list-empty" className="py-8 text-center text-sm text-muted">No tickets found matching your filters.</div>
      )}

      {!loading && tickets.length > 0 && (
        <div className="overflow-x-auto">
          <table data-testid="ticket-list-table" className="w-full border-collapse text-sm">
            <thead>
              <tr>
                {SORTABLE_COLUMNS.map(({ col, label }) => (
                  <th key={col} className={thCls} onClick={() => handleSortClick(col)} data-testid={`sort-col-${col}`} aria-sort={sortBy === col ? (sortDir === "asc" ? "ascending" : "descending") : "none"}>
                    <span className="inline-flex items-center gap-1">
                      {label}
                      {sortBy === col
                        ? sortDir === "asc" ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />
                        : <ArrowUpDown className="h-3 w-3 opacity-30" />}
                    </span>
                  </th>
                ))}
                <th className={thCls}>Assignee</th>
                <th className={thCls}>Team</th>
                <th className={thCls}>RCDO</th>
                <th className={thCls}>Target Week</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((ticket) => (
                <tr key={ticket.id} data-testid={`ticket-row-${ticket.id}`} onClick={() => onSelectTicket(ticket.id)} className="cursor-pointer hover:bg-background/80 transition-colors">
                  <td className={tdCls}><span data-testid={`ticket-key-${ticket.id}`} className="font-mono text-xs font-semibold">{ticket.key}</span></td>
                  <td className={cn(tdCls, "max-w-[250px]")}><span data-testid={`ticket-title-${ticket.id}`} className="block overflow-hidden text-ellipsis whitespace-nowrap">{ticket.title}</span></td>
                  <td className={tdCls}><Badge variant={STATUS_BADGE_VARIANT[ticket.status]}>{TICKET_STATUS_LABELS[ticket.status]}</Badge></td>
                  <td className={tdCls}><Badge variant={PRIORITY_BADGE_VARIANT[ticket.priority]}>{TICKET_PRIORITY_LABELS[ticket.priority]}</Badge></td>
                  <td className={cn(tdCls, "text-xs text-muted")}>{new Date(ticket.updatedAt).toLocaleDateString()}</td>
                  <td className={cn(tdCls, "text-xs")}>{ticket.assigneeUserId ?? <span className="text-muted">—</span>}</td>
                  <td className={cn(tdCls, "text-xs")}>{ticket.teamId}</td>
                  <td className={cn(tdCls, "text-xs text-muted")}>{ticket.rcdoNodeId ? (rcdoLabels[ticket.rcdoNodeId] ?? ticket.rcdoNodeId) : "—"}</td>
                  <td className={cn(tdCls, "text-xs")}>{ticket.targetWeekStartDate ?? <span className="text-muted">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {total > pageSize && (
        <div data-testid="ticket-list-pagination" className="flex items-center gap-2 justify-end flex-wrap">
          <span className="text-xs text-muted">{(page - 1) * pageSize + 1}–{Math.min(page * pageSize, total)} of {total}</span>
          <Button variant="secondary" size="sm" onClick={() => onPageChange(page - 1)} disabled={page <= 1} data-testid="ticket-page-prev">
            <ChevronLeft className="h-3.5 w-3.5" />Prev
          </Button>
          <span data-testid="ticket-page-indicator" className="text-xs">{page} / {totalPages}</span>
          <Button variant="secondary" size="sm" onClick={() => onPageChange(page + 1)} disabled={page >= totalPages} data-testid="ticket-page-next">
            Next<ChevronRight className="h-3.5 w-3.5" />
          </Button>
        </div>
      )}
    </div>
  );
}
