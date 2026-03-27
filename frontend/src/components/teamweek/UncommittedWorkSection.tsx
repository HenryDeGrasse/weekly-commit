/**
 * UncommittedWorkSection — assigned-but-uncommitted and unassigned tickets.
 */
import { useState } from "react";
import { AlertTriangle, AlertCircle } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { cn } from "../../lib/utils.js";
import type { UncommittedTicketSummary, TicketStatus } from "../../api/teamTypes.js";

const STATUS_VARIANT: Record<TicketStatus, "default" | "primary" | "success" | "danger"> = {
  TODO: "default", IN_PROGRESS: "primary", DONE: "success", CANCELED: "default", BLOCKED: "danger",
};

export interface UncommittedWorkSectionProps {
  readonly assignedTickets: UncommittedTicketSummary[];
  readonly unassignedTickets: UncommittedTicketSummary[];
  readonly onQuickAssign?: (ticketId: string, assigneeUserId: string) => Promise<void>;
}

const thCls = "px-3 py-1.5 text-left text-[0.65rem] font-semibold uppercase tracking-wider text-muted border-b border-border";
const tdCls = "px-3 py-2 text-sm border-b border-border";

function TicketRow({ ticket, testIdPrefix }: { ticket: UncommittedTicketSummary; testIdPrefix: string }) {
  return (
    <tr data-testid={`${testIdPrefix}-ticket-${ticket.id}`}>
      <td className={tdCls}><span className="font-mono text-xs font-bold text-primary">{ticket.key}</span></td>
      <td className={tdCls}>
        {ticket.title}
        {ticket.estimatePoints != null && (
          <Badge variant="primary" className="ml-1.5 text-[0.65rem]">{ticket.estimatePoints}pt</Badge>
        )}
      </td>
      <td className={cn(tdCls, "text-xs text-muted")}>{ticket.assigneeUserId ?? <em>Unassigned</em>}</td>
      <td className={tdCls}><Badge variant={STATUS_VARIANT[ticket.status]}>{ticket.status}</Badge></td>
      <td className={cn(tdCls, "text-xs text-muted")}>{ticket.targetWeekStartDate ?? "—"}</td>
    </tr>
  );
}

function QuickAssignRow({ ticket, onAssign }: { ticket: UncommittedTicketSummary; onAssign?: (ticketId: string, assigneeUserId: string) => Promise<void> }) {
  const [userId, setUserId] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleAssign = async () => {
    if (!userId.trim() || !onAssign) return;
    setSaving(true); setError(null);
    try { await onAssign(ticket.id, userId.trim()); }
    catch (err) { setError(err instanceof Error ? err.message : "Assign failed"); setSaving(false); }
  };

  return (
    <tr data-testid={`unassigned-ticket-${ticket.id}`}>
      <td className={tdCls}><span className="font-mono text-xs font-bold text-primary">{ticket.key}</span></td>
      <td className={tdCls}>
        {ticket.title}
        {ticket.estimatePoints != null && <Badge variant="primary" className="ml-1.5 text-[0.65rem]">{ticket.estimatePoints}pt</Badge>}
      </td>
      <td className={cn(tdCls, "text-xs text-muted")}><em>Unassigned</em></td>
      <td className={tdCls}><Badge variant={STATUS_VARIANT[ticket.status]}>{ticket.status}</Badge></td>
      <td className={cn(tdCls, "text-xs text-muted")}>{ticket.targetWeekStartDate ?? "—"}</td>
      {onAssign && (
        <td className={tdCls}>
          <div className="flex gap-1.5 items-center">
            <input type="text" value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="User ID" data-testid={`quick-assign-input-${ticket.id}`}
              className="h-7 w-[100px] rounded-default border border-border bg-surface px-2 text-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50" />
            <Button variant="primary" size="sm" onClick={() => void handleAssign()} disabled={!userId.trim() || saving} data-testid={`quick-assign-btn-${ticket.id}`} className="h-7 px-2 text-xs">
              {saving ? "…" : "Assign"}
            </Button>
            {error && <span className="text-[0.65rem] text-danger">{error}</span>}
          </div>
        </td>
      )}
    </tr>
  );
}

function TableHeader({ showAssignCol }: { showAssignCol: boolean }) {
  return (
    <tr className="bg-background">
      <th className={thCls}>Key</th>
      <th className={thCls}>Title</th>
      <th className={thCls}>Assignee</th>
      <th className={thCls}>Status</th>
      <th className={thCls}>Week</th>
      {showAssignCol && <th className={thCls}>Assign</th>}
    </tr>
  );
}

export function UncommittedWorkSection({ assignedTickets, unassignedTickets, onQuickAssign }: UncommittedWorkSectionProps) {
  const isEmpty = assignedTickets.length === 0 && unassignedTickets.length === 0;

  if (isEmpty) {
    return (
      <section aria-labelledby="uncommitted-heading" data-testid="uncommitted-work-section">
        <h3 id="uncommitted-heading" className="m-0 mb-3 text-sm font-bold">Uncommitted Work</h3>
        <p data-testid="no-uncommitted-work" className="text-sm text-foreground font-semibold">✓ No uncommitted work for this week.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby="uncommitted-heading" data-testid="uncommitted-work-section">
      <h3 id="uncommitted-heading" className="m-0 mb-3 text-sm font-bold">Uncommitted Work</h3>
      <div className="flex flex-col gap-4">
        {/* Assigned but no commit */}
        <div data-testid="assigned-uncommitted-section" className="rounded-default border border-border bg-surface overflow-hidden">
          <div className="flex items-center justify-between px-3 py-2.5 bg-neutral-100 border-b border-border">
            <span className="flex items-center gap-1.5 text-xs font-semibold text-foreground">
              <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
              Assigned — no commit planned
            </span>
            <span data-testid="assigned-uncommitted-count" className="text-xs font-bold text-foreground">{assignedTickets.length}</span>
          </div>
          {assignedTickets.length === 0 ? (
            <p className="m-0 px-3 py-2 text-xs text-muted">None.</p>
          ) : (
            <table data-testid="assigned-uncommitted-table" className="w-full border-collapse" aria-label="Assigned tickets without commits">
              <thead><TableHeader showAssignCol={false} /></thead>
              <tbody>{assignedTickets.map((t) => <TicketRow key={t.id} ticket={t} testIdPrefix="assigned" />)}</tbody>
            </table>
          )}
        </div>

        {/* Unassigned */}
        <div data-testid="unassigned-section" className="rounded-default border border-border bg-surface overflow-hidden">
          <div className="flex items-center justify-between px-3 py-2.5 bg-neutral-200 border-b border-border">
            <span className="flex items-center gap-1.5 text-xs font-semibold text-foreground">
              <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
              Unassigned — targeted this week
            </span>
            <span data-testid="unassigned-count" className="text-xs font-bold text-foreground">{unassignedTickets.length}</span>
          </div>
          {unassignedTickets.length === 0 ? (
            <p className="m-0 px-3 py-2 text-xs text-muted">None.</p>
          ) : (
            <table data-testid="unassigned-tickets-table" className="w-full border-collapse" aria-label="Unassigned tickets">
              <thead><TableHeader showAssignCol={onQuickAssign !== undefined} /></thead>
              <tbody>{unassignedTickets.map((t) => <QuickAssignRow key={t.id} ticket={t} {...(onQuickAssign ? { onAssign: onQuickAssign } : {})} />)}</tbody>
            </table>
          )}
        </div>
      </div>
    </section>
  );
}
