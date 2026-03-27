/**
 * TicketDetailView — full ticket detail panel.
 */
import { useState, useCallback } from "react";
import { X, Pencil } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { cn } from "../../lib/utils.js";
import type { TicketResponse, TicketStatus, TicketStatusHistory, LinkedCommitEntry } from "../../api/ticketTypes.js";
import type { TeamMember } from "../../api/teamTypes.js";
import type { CommitOutcome } from "../../api/planTypes.js";
import { TICKET_STATUS_LABELS, TICKET_STATUS_TRANSITIONS, TICKET_PRIORITY_LABELS } from "../../api/ticketTypes.js";

export interface TicketDetailViewProps {
  readonly ticket: TicketResponse;
  readonly onStatusTransition: (ticketId: string, newStatus: TicketStatus) => Promise<void>;
  readonly onAssigneeChange: (ticketId: string, assigneeUserId: string) => Promise<void>;
  /** Team members for the assignee dropdown. Falls back to text input when empty/undefined. */
  readonly teamMembers?: readonly TeamMember[];
  readonly rcdoPath?: string;
  readonly onClose?: () => void;
  readonly onEdit?: () => void;
}

const STATUS_BADGE_VARIANT: Record<TicketStatus, "default" | "primary" | "success" | "danger"> = {
  TODO: "default", IN_PROGRESS: "primary", DONE: "success", CANCELED: "default", BLOCKED: "danger",
};

const OUTCOME_LABELS: Record<CommitOutcome, string> = {
  ACHIEVED: "✓ Achieved", PARTIALLY_ACHIEVED: "◑ Partial", NOT_ACHIEVED: "✗ Not achieved", CANCELED: "— Canceled",
};
const OUTCOME_CLS: Record<CommitOutcome, string> = {
  ACHIEVED: "text-success", PARTIALLY_ACHIEVED: "text-warning", NOT_ACHIEVED: "text-danger", CANCELED: "text-muted",
};

const shCls = "text-[0.75rem] font-bold uppercase tracking-wider text-muted m-0 mb-2";
const metaRowCls = "flex gap-2 text-sm items-baseline";
const metaLabelCls = "font-semibold min-w-[100px] shrink-0 text-muted";

function StatusHistoryTimeline({ history }: { history: TicketStatusHistory[] }) {
  if (history.length === 0) {
    return <p data-testid="status-history-empty" className="text-sm text-muted">No status changes recorded.</p>;
  }
  return (
    <ol data-testid="status-history-timeline" className="list-none m-0 p-0 flex flex-col gap-2">
      {[...history].reverse().map((entry) => (
        <li key={entry.id} data-testid={`history-entry-${entry.id}`} className="flex items-start gap-2.5 text-xs">
          <span className="mt-1 h-2 w-2 rounded-full bg-primary shrink-0" />
          <div>
            <span className="text-muted">{new Date(entry.changedAt).toLocaleString()}</span>
            {" — "}
            {entry.fromStatus ? <><strong>{TICKET_STATUS_LABELS[entry.fromStatus]}</strong>{" → "}</> : null}
            <strong>{TICKET_STATUS_LABELS[entry.toStatus]}</strong>
            {entry.note && <span className="text-muted"> ({entry.note})</span>}
          </div>
        </li>
      ))}
    </ol>
  );
}

function LinkedCommitsList({ commits }: { commits: LinkedCommitEntry[] }) {
  if (commits.length === 0) {
    return <p data-testid="linked-commits-empty" className="text-sm text-muted">No linked commits.</p>;
  }
  return (
    <ul data-testid="linked-commits-list" className="list-none m-0 p-0 flex flex-col gap-1.5">
      {commits.map((c) => (
        <li key={c.commitId} data-testid={`linked-commit-${c.commitId}`}
          className="rounded-default border border-border bg-background px-3 py-2 text-sm flex justify-between items-center flex-wrap gap-1.5">
          <div>
            <span className="font-semibold">{c.commitTitle}</span>
            <span className="ml-2 text-xs text-muted">{c.weekStartDate} · {c.chessPiece}{c.estimatePoints != null ? ` · ${c.estimatePoints}pts` : ""}</span>
          </div>
          {c.outcome && (
            <span data-testid={`linked-commit-outcome-${c.commitId}`} className={cn("text-xs font-semibold", OUTCOME_CLS[c.outcome])}>
              {OUTCOME_LABELS[c.outcome]}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

export function TicketDetailView({ ticket, onStatusTransition, onAssigneeChange, teamMembers, rcdoPath, onClose, onEdit }: TicketDetailViewProps) {
  const [transitioning, setTransitioning] = useState<TicketStatus | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);
  const [assigneeInput, setAssigneeInput] = useState(ticket.assigneeUserId ?? "");
  const [assigneeSaving, setAssigneeSaving] = useState(false);
  const [assigneeError, setAssigneeError] = useState<string | null>(null);

  const nextStatuses = TICKET_STATUS_TRANSITIONS[ticket.status];

  const handleTransition = useCallback(async (newStatus: TicketStatus) => {
    setTransitioning(newStatus); setTransitionError(null);
    try { await onStatusTransition(ticket.id, newStatus); }
    catch (err) { setTransitionError(err instanceof Error ? err.message : "Status transition failed."); }
    finally { setTransitioning(null); }
  }, [ticket.id, onStatusTransition]);

  const handleAssigneeChange = useCallback(async () => {
    setAssigneeSaving(true); setAssigneeError(null);
    try { await onAssigneeChange(ticket.id, assigneeInput.trim()); }
    catch (err) { setAssigneeError(err instanceof Error ? err.message : "Assignment change failed."); }
    finally { setAssigneeSaving(false); }
  }, [ticket.id, assigneeInput, onAssigneeChange]);

  return (
    <div data-testid="ticket-detail-view" className="rounded-default border border-border bg-surface flex flex-col gap-0 overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b border-border flex items-start justify-between gap-4 flex-wrap">
        <div className="flex flex-col gap-1.5 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span data-testid="ticket-detail-key" className="font-mono text-xs font-bold text-muted">{ticket.key}</span>
            <Badge variant={STATUS_BADGE_VARIANT[ticket.status]} data-testid="ticket-detail-status-badge">
              {TICKET_STATUS_LABELS[ticket.status]}
            </Badge>
          </div>
          <h2 data-testid="ticket-detail-title" className="m-0 text-lg font-bold">{ticket.title}</h2>
        </div>
        <div className="flex gap-2 shrink-0">
          {onEdit && (
            <Button variant="secondary" size="sm" onClick={onEdit} data-testid="ticket-detail-edit-btn">
              <Pencil className="h-3 w-3" />Edit
            </Button>
          )}
          {onClose && (
            <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close ticket detail" data-testid="ticket-detail-close-btn" className="h-8 w-8">
              <X className="h-4 w-4" />
            </Button>
          )}
        </div>
      </div>

      <div className="px-5 py-4 flex flex-col gap-5">
        {/* Status transitions */}
        {nextStatuses.length > 0 && (
          <div>
            <p className={shCls}>Status Transitions</p>
            <div data-testid="status-transition-buttons" className="flex gap-1.5 flex-wrap">
              {nextStatuses.map((next) => (
                <button key={next} type="button" onClick={() => void handleTransition(next)} disabled={transitioning !== null} data-testid={`transition-btn-${next.toLowerCase()}`}
                  className={cn(
                    "px-3.5 py-1.5 rounded-full border text-xs font-semibold transition-opacity",
                    transitioning !== null && "cursor-not-allowed opacity-60",
                    next === "DONE" ? "border-foreground/30 bg-foreground/5 text-foreground font-bold" :
                    next === "BLOCKED" ? "border-neutral-300 bg-neutral-100 text-foreground" :
                    next === "CANCELED" ? "border-neutral-200 bg-neutral-100 text-neutral-600" :
                    "border-border bg-background text-foreground",
                  )}>
                  {transitioning === next ? "…" : `→ ${TICKET_STATUS_LABELS[next]}`}
                </button>
              ))}
            </div>
            {transitionError && <span data-testid="transition-error" role="alert" className="mt-1 block text-xs text-danger">{transitionError}</span>}
          </div>
        )}

        {ticket.description && (
          <div>
            <p className={shCls}>Description</p>
            <p data-testid="ticket-detail-description" className="m-0 text-sm leading-relaxed">{ticket.description}</p>
          </div>
        )}

        <div>
          <p className={shCls}>Details</p>
          <div data-testid="ticket-detail-meta" className="flex flex-col gap-1.5">
            <div className={metaRowCls}><span className={metaLabelCls}>Priority</span><span data-testid="ticket-detail-priority">{TICKET_PRIORITY_LABELS[ticket.priority]}</span></div>
            <div className={metaRowCls}><span className={metaLabelCls}>Reporter</span><span data-testid="ticket-detail-reporter">{ticket.reporterDisplayName ?? ticket.reporterUserId}</span></div>
            <div className={metaRowCls}><span className={metaLabelCls}>Team</span><span data-testid="ticket-detail-team">{ticket.teamName ?? ticket.teamId}</span></div>
            {ticket.rcdoNodeId && <div className={metaRowCls}><span className={metaLabelCls}>RCDO</span><span data-testid="ticket-detail-rcdo">{rcdoPath ?? ticket.rcdoNodeId}</span></div>}
            {ticket.estimatePoints != null && <div className={metaRowCls}><span className={metaLabelCls}>Estimate</span><span data-testid="ticket-detail-estimate">{ticket.estimatePoints} pts</span></div>}
            {ticket.targetWeekStartDate && <div className={metaRowCls}><span className={metaLabelCls}>Target Week</span><span data-testid="ticket-detail-target-week">{ticket.targetWeekStartDate}</span></div>}
            <div className={metaRowCls}><span className={metaLabelCls}>Created</span><span>{new Date(ticket.createdAt).toLocaleString()}</span></div>
          </div>
        </div>

        <div>
          <p className={shCls}>Assignment</p>
          <div data-testid="ticket-assignment-section" className="flex gap-2 items-center flex-wrap">
            {teamMembers && teamMembers.length > 0 ? (
              <select
                value={assigneeInput}
                onChange={(e) => setAssigneeInput(e.target.value)}
                data-testid="ticket-assignee-input"
                className="h-9 rounded-default border border-border bg-surface px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 min-w-[220px]"
              >
                <option value="">Unassigned</option>
                {teamMembers.map((m) => (
                  <option key={m.id} value={m.id}>{m.displayName}</option>
                ))}
              </select>
            ) : (
              <input type="text" value={assigneeInput} onChange={(e) => setAssigneeInput(e.target.value)} placeholder="User ID (leave blank to unassign)" data-testid="ticket-assignee-input"
                className="h-9 rounded-default border border-border bg-surface px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 min-w-[180px]" />
            )}
            <Button variant="primary" size="sm" onClick={() => void handleAssigneeChange()} disabled={assigneeSaving} data-testid="ticket-assign-save-btn">
              {assigneeSaving ? "Saving…" : "Update"}
            </Button>
          </div>
          {assigneeError && <span data-testid="assignee-error" role="alert" className="mt-1 block text-xs text-danger">{assigneeError}</span>}
        </div>

        <div>
          <p className={shCls}>Status History</p>
          <StatusHistoryTimeline history={ticket.statusHistory} />
        </div>

        <div>
          <p className={shCls}>Linked Commits</p>
          <LinkedCommitsList commits={ticket.linkedCommits} />
        </div>
      </div>
    </div>
  );
}
