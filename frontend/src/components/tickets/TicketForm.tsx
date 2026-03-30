/**
 * TicketForm — modal form for creating or editing a native ticket.
 */
import { useState, type FormEvent } from "react";
import { X, ChevronDown, ChevronUp, Check } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Input } from "../ui/Input.js";
import { Select } from "../ui/Select.js";
import { RcdoTreeView } from "../rcdo/RcdoTreeView.js";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";
import type { CreateTicketPayload, TicketStatus, TicketPriority, TicketResponse } from "../../api/ticketTypes.js";
import type { EstimatePoints } from "../../api/planTypes.js";
import { TICKET_STATUS_LABELS, TICKET_PRIORITY_LABELS } from "../../api/ticketTypes.js";

const TICKET_STATUSES: TicketStatus[] = ["TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELED"];
const TICKET_PRIORITIES: TicketPriority[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
const ESTIMATE_POINTS: EstimatePoints[] = [1, 2, 3, 5, 8];

export interface TicketFormProps {
  readonly mode: "create" | "edit";
  readonly initialValues?: Partial<CreateTicketPayload>;
  readonly currentUserId: string;
  readonly currentTeamId?: string;
  /** RCDO tree for the picker. When provided, shows a browsable tree instead of a text input. */
  readonly rcdoTree?: RcdoTreeNode[];
  readonly onSubmit: (payload: CreateTicketPayload) => Promise<void>;
  readonly onCancel: () => void;
  readonly ticket?: TicketResponse;
}

function findNodeById(nodes: RcdoTreeNode[], id: string): RcdoTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findNodeById(node.children, id);
    if (found) return found;
  }
  return null;
}

const textareaCls = "w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:border-primary";

export function TicketForm({ mode, initialValues, currentUserId, currentTeamId, rcdoTree, onSubmit, onCancel, ticket }: TicketFormProps) {
  const [title, setTitle] = useState(ticket?.title ?? initialValues?.title ?? "");
  const [description, setDescription] = useState(ticket?.description ?? initialValues?.description ?? "");
  const [status, setStatus] = useState<TicketStatus>(ticket?.status ?? initialValues?.status ?? "TODO");
  const [priority, setPriority] = useState<TicketPriority>(ticket?.priority ?? initialValues?.priority ?? "MEDIUM");
  const [assignee, setAssignee] = useState(ticket?.assigneeUserId ?? initialValues?.assigneeUserId ?? "");
  const [reporter, setReporter] = useState(ticket?.reporterUserId ?? initialValues?.reporterUserId ?? currentUserId);
  const [teamId, setTeamId] = useState(ticket?.teamId ?? initialValues?.teamId ?? currentTeamId ?? "");
  const [estimatePoints, setEstimatePoints] = useState<EstimatePoints | "">(ticket?.estimatePoints ?? initialValues?.estimatePoints ?? "");
  const [rcdoNodeId, setRcdoNodeId] = useState(ticket?.rcdoNodeId ?? initialValues?.rcdoNodeId ?? "");
  const [targetWeek, setTargetWeek] = useState(ticket?.targetWeekStartDate ?? initialValues?.targetWeekStartDate ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [showRcdoPicker, setShowRcdoPicker] = useState(false);

  const hasRcdoTree = rcdoTree && rcdoTree.length > 0;
  const selectedRcdoNode = hasRcdoTree && rcdoNodeId ? findNodeById(rcdoTree, rcdoNodeId) : null;

  function validate(): boolean {
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = "Title is required.";
    if (!teamId.trim()) next.teamId = "Team is required.";
    if (!reporter.trim()) next.reporter = "Reporter is required.";
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true); setSubmitError(null);
    try {
      await onSubmit({
        title: title.trim(),
        ...(description.trim() ? { description: description.trim() } : {}),
        status, priority,
        ...(assignee.trim() ? { assigneeUserId: assignee.trim() } : {}),
        reporterUserId: reporter.trim(), teamId: teamId.trim(),
        ...(rcdoNodeId.trim() ? { rcdoNodeId: rcdoNodeId.trim() } : {}),
        ...(estimatePoints !== "" ? { estimatePoints: estimatePoints as EstimatePoints } : {}),
        ...(targetWeek ? { targetWeekStartDate: targetWeek } : {}),
      });
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "Failed to save ticket.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div role="dialog" aria-modal="true" aria-label={mode === "create" ? "Create ticket" : "Edit ticket"} data-testid="ticket-form-dialog"
      className="fixed inset-0 z-[1100] flex items-center justify-center bg-black/35 overflow-auto p-4">
      <div className="w-full max-w-[520px] rounded-lg border border-border bg-surface p-6 shadow-xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-5">
          <h3 className="m-0 text-base font-semibold">{mode === "create" ? "Create Ticket" : "Edit Ticket"}</h3>
          <Button variant="ghost" size="icon" type="button" onClick={onCancel} aria-label="Close" className="h-7 w-7">
            <X className="h-4 w-4" />
          </Button>
        </div>

        <form onSubmit={(e) => void handleSubmit(e)} noValidate className="flex flex-col gap-3.5">
          {/* Title */}
          <Input id="tf-title" label="Title *" type="text" value={title} onChange={(e) => setTitle(e.target.value)} data-testid="ticket-form-title" placeholder="Short descriptive title" required error={errors.title} errorTestId="ticket-form-title-error" />

          {/* Description */}
          <div className="flex flex-col gap-1.5">
            <label htmlFor="tf-desc" className="text-sm font-medium">Description</label>
            <textarea id="tf-desc" value={description} onChange={(e) => setDescription(e.target.value)} data-testid="ticket-form-description" className={textareaCls} placeholder="Optional details…" style={{ minHeight: "80px" }} />
          </div>

          {/* Status + Priority row */}
          <div className="flex gap-3 flex-wrap">
            <div className="flex-1 min-w-[120px]">
              <Select id="tf-status" label="Status" value={status} onChange={(e) => setStatus(e.target.value as TicketStatus)} data-testid="ticket-form-status">
                {TICKET_STATUSES.map((s) => <option key={s} value={s}>{TICKET_STATUS_LABELS[s]}</option>)}
              </Select>
            </div>
            <div className="flex-1 min-w-[120px]">
              <Select id="tf-priority" label="Priority *" value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority)} data-testid="ticket-form-priority">
                {TICKET_PRIORITIES.map((p) => <option key={p} value={p}>{TICKET_PRIORITY_LABELS[p]}</option>)}
              </Select>
            </div>
          </div>

          <Input id="tf-assignee" label="Assignee (User ID)" type="text" value={assignee} onChange={(e) => setAssignee(e.target.value)} data-testid="ticket-form-assignee" placeholder="Leave blank to leave unassigned" />
          <Input id="tf-reporter" label="Reporter *" type="text" value={reporter} onChange={(e) => setReporter(e.target.value)} data-testid="ticket-form-reporter" error={errors.reporter} />
          <Input id="tf-team" label="Team *" type="text" value={teamId} onChange={(e) => setTeamId(e.target.value)} data-testid="ticket-form-team" placeholder="Team ID" error={errors.teamId} errorTestId="ticket-form-team-error" />

          <div>
            <Select id="tf-points" label="Estimate Points (optional)" value={String(estimatePoints)} onChange={(e) => setEstimatePoints(e.target.value === "" ? "" : (Number(e.target.value) as EstimatePoints))} data-testid="ticket-form-estimate">
              <option value="">Unestimated</option>
              {ESTIMATE_POINTS.map((p) => <option key={p} value={p}>{p} {p === 1 ? "pt" : "pts"}</option>)}
            </Select>
          </div>

          {/* RCDO picker */}
          <div>
            <p className="text-sm font-medium mb-1.5">RCDO Link (optional)</p>
            {selectedRcdoNode && (
              <div data-testid="ticket-rcdo-selected" className="flex items-center gap-2 px-3 py-2 rounded-default border border-foreground/20 bg-foreground/5 mb-2 text-sm">
                <Check className="h-3.5 w-3.5 text-foreground shrink-0" aria-hidden="true" />
                <span className="flex-1 text-foreground">{selectedRcdoNode.title}</span>
                <Button variant="ghost" size="icon" type="button" onClick={() => setRcdoNodeId("")} aria-label="Clear RCDO link" className="h-6 w-6 text-muted">
                  <X className="h-3 w-3" />
                </Button>
              </div>
            )}
            {hasRcdoTree ? (
              <>
                <Button variant="secondary" size="sm" type="button" onClick={() => setShowRcdoPicker((v) => !v)} data-testid="ticket-rcdo-picker-toggle" aria-expanded={showRcdoPicker}>
                  {showRcdoPicker ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
                  {showRcdoPicker ? "Close RCDO picker" : "Browse RCDO nodes"}
                </Button>
                {showRcdoPicker && (
                  <div data-testid="ticket-rcdo-picker-panel" className="mt-2 rounded-default border border-border p-3 max-h-[250px] overflow-y-auto">
                    <RcdoTreeView
                      nodes={rcdoTree}
                      selectedId={rcdoNodeId || null}
                      onSelect={(id) => { setRcdoNodeId(id); setShowRcdoPicker(false); }}
                      statusFilter="active-only"
                      searchQuery=""
                    />
                  </div>
                )}
              </>
            ) : (
              <Input id="tf-rcdo" type="text" value={rcdoNodeId} onChange={(e) => setRcdoNodeId(e.target.value)} data-testid="ticket-form-rcdo" placeholder="RCDO node ID" />
            )}
          </div>
          <Input id="tf-target-week" label="Target Week (optional)" type="date" value={targetWeek} onChange={(e) => setTargetWeek(e.target.value)} data-testid="ticket-form-target-week" />

          {submitError && (
            <div role="alert" data-testid="ticket-form-submit-error" className="rounded-default border border-border bg-foreground/5 px-3 py-2 text-xs text-foreground font-semibold">{submitError}</div>
          )}

          <div className="flex gap-2 justify-end mt-1">
            <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
            <Button type="submit" variant="primary" disabled={saving} data-testid="ticket-form-submit">
              {saving ? (mode === "create" ? "Creating…" : "Saving…") : (mode === "create" ? "Create Ticket" : "Save Changes")}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
