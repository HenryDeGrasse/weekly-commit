/**
 * ExceptionQueueSection — filterable list of manager-review exceptions.
 */
import { useState } from "react";
import { Check, MessageSquare } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { cn } from "../../lib/utils.js";
import type { ExceptionResponse, ExceptionSeverity, ExceptionType, AddCommentPayload, ResolveExceptionPayload } from "../../api/teamTypes.js";

const SEVERITY_CLS: Record<ExceptionSeverity, string> = {
  HIGH: "bg-foreground text-background",
  MEDIUM: "bg-foreground/50 text-background",
  LOW: "bg-foreground/15 text-foreground",
};
const SEVERITY_LABELS: Record<ExceptionSeverity, string> = { HIGH: "HIGH", MEDIUM: "MED", LOW: "LOW" };
const SEVERITY_ORDER: Record<ExceptionSeverity, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };
const EXCEPTION_TYPE_LABELS: Record<ExceptionType, string> = {
  MISSED_LOCK: "Missed Lock", AUTO_LOCKED: "Auto-Locked", MISSED_RECONCILE: "Missed Reconcile",
  OVER_BUDGET: "Over Budget", REPEATED_CARRY_FORWARD: "Repeated Carry-Forward",
  POST_LOCK_SCOPE_INCREASE: "Post-Lock Scope Increase", KING_CHANGED_POST_LOCK: "King Changed Post-Lock",
  HIGH_SCOPE_VOLATILITY: "High Scope Volatility",
};

type FilterMode = "all" | "unresolved" | "resolved";

export interface ExceptionQueueSectionProps {
  readonly exceptions: ExceptionResponse[];
  readonly actorUserId: string;
  readonly onResolve?: (exceptionId: string, payload: ResolveExceptionPayload) => Promise<void>;
  readonly onAddComment?: (payload: AddCommentPayload) => Promise<void>;
}

function ResolveDialog({ exception, onConfirm, onCancel }: { exception: ExceptionResponse; onConfirm: (r: string) => Promise<void>; onCancel: () => void }) {
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const handleSubmit = async () => {
    if (!note.trim()) return;
    setSaving(true); setError(null);
    try { await onConfirm(note.trim()); } catch (err) { setError(err instanceof Error ? err.message : "Failed"); setSaving(false); }
  };
  return (
    <div role="dialog" aria-modal="true" aria-label="Resolve exception" data-testid="resolve-exception-dialog"
      className="fixed inset-0 z-[1100] flex items-center justify-center bg-black/35">
      <div className="w-full max-w-[420px] rounded-lg border border-border bg-surface p-6 shadow-xl flex flex-col gap-4">
        <h3 className="m-0 text-base font-semibold">Resolve Exception</h3>
        <p className="m-0 text-sm text-muted">{EXCEPTION_TYPE_LABELS[exception.exceptionType]}: {exception.description}</p>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="resolution-note" className="text-xs font-semibold">
            Resolution note <span className="text-danger">*</span>
          </label>
          <textarea id="resolution-note" data-testid="resolution-note-input" value={note} onChange={(e) => setNote(e.target.value)} rows={3} placeholder="Describe how this exception was resolved…" className="w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50" />
        </div>
        {error && <p role="alert" className="m-0 text-xs text-danger">{error}</p>}
        <div className="flex gap-2 justify-end">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button variant="success" onClick={() => void handleSubmit()} disabled={!note.trim() || saving} data-testid="resolve-exception-confirm">
            {saving ? "Resolving…" : "Mark Resolved"}
          </Button>
        </div>
      </div>
    </div>
  );
}

function CommentDialog({ exception, onConfirm, onCancel }: { exception: ExceptionResponse; onConfirm: (t: string) => Promise<void>; onCancel: () => void }) {
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const handleSubmit = async () => {
    if (!text.trim()) return;
    setSaving(true); setError(null);
    try { await onConfirm(text.trim()); } catch (err) { setError(err instanceof Error ? err.message : "Failed"); setSaving(false); }
  };
  return (
    <div role="dialog" aria-modal="true" aria-label="Add comment" data-testid="add-comment-dialog"
      className="fixed inset-0 z-[1100] flex items-center justify-center bg-black/35">
      <div className="w-full max-w-[420px] rounded-lg border border-border bg-surface p-6 shadow-xl flex flex-col gap-4">
        <h3 className="m-0 text-base font-semibold">Add Comment</h3>
        {exception.planId && <p className="m-0 text-xs text-muted">Comment on plan: <code className="text-xs">{exception.planId}</code></p>}
        <div className="flex flex-col gap-1.5">
          <label htmlFor="comment-text" className="text-xs font-semibold">Comment <span className="text-danger">*</span></label>
          <textarea id="comment-text" data-testid="comment-text-input" value={text} onChange={(e) => setText(e.target.value)} rows={3} placeholder="Add your manager comment…" className="w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50" />
        </div>
        {error && <p role="alert" className="m-0 text-xs text-danger">{error}</p>}
        <div className="flex gap-2 justify-end">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button variant="primary" onClick={() => void handleSubmit()} disabled={!text.trim() || saving} data-testid="add-comment-confirm">
            {saving ? "Posting…" : "Post Comment"}
          </Button>
        </div>
      </div>
    </div>
  );
}

export function ExceptionQueueSection({ exceptions, actorUserId, onResolve, onAddComment }: ExceptionQueueSectionProps) {
  const [filter, setFilter] = useState<FilterMode>("unresolved");
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [commentingId, setCommentingId] = useState<string | null>(null);

  const filtered = exceptions.filter((e) => filter === "all" ? true : filter === "unresolved" ? !e.resolved : e.resolved).slice().sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]);
  const unresolvedCount = exceptions.filter((e) => !e.resolved).length;
  const resolvingException = resolvingId ? exceptions.find((e) => e.id === resolvingId) : null;
  const commentingException = commentingId ? exceptions.find((e) => e.id === commentingId) : null;

  return (
    <section aria-labelledby="exception-queue-heading" data-testid="exception-queue-section">
      <h3 id="exception-queue-heading" className="m-0 mb-3 flex items-center gap-2 text-sm font-bold">
        Exception Queue
        {unresolvedCount > 0 && (
          <span data-testid="exception-queue-badge" className="text-[0.65rem] font-bold px-1.5 py-px rounded-full bg-foreground text-background">{unresolvedCount}</span>
        )}
      </h3>

      <div className="flex gap-1 mb-3" role="tablist" aria-label="Exception filter">
        {(["all", "unresolved", "resolved"] as FilterMode[]).map((mode) => (
          <button key={mode} type="button" role="tab" aria-selected={filter === mode} onClick={() => setFilter(mode)} data-testid={`exception-filter-${mode}`}
            className={cn("px-3.5 py-1.5 rounded-default border text-xs font-medium transition-colors", filter === mode ? "bg-foreground text-background border-foreground" : "bg-surface text-foreground border-border hover:bg-muted-bg")}>
            {mode.charAt(0).toUpperCase() + mode.slice(1)}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <p data-testid="no-exceptions-message" className={cn("text-sm", filter === "unresolved" ? "text-foreground font-semibold" : "text-muted")}>
          {filter === "unresolved" ? "✓ No open exceptions." : "No exceptions to show."}
        </p>
      ) : (
        <div data-testid="exception-list" className="rounded-default border border-border bg-surface overflow-hidden">
          {filtered.map((exception) => (
            <div key={exception.id} data-testid={`exception-item-${exception.id}`}
              className={cn("border-b border-border p-3 last:border-b-0", exception.resolved && "opacity-70")}>
              <div className="flex items-start justify-between gap-3 flex-wrap">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1.5">
                    <span data-testid={`exception-severity-${exception.id}`} className={cn("text-[0.65rem] font-bold px-1.5 py-px rounded-full", SEVERITY_CLS[exception.severity])}>
                      {SEVERITY_LABELS[exception.severity]}
                    </span>
                    <span data-testid={`exception-type-${exception.id}`} className="text-xs font-semibold">{EXCEPTION_TYPE_LABELS[exception.exceptionType]}</span>
                    {exception.resolved && <Badge data-testid={`exception-resolved-badge-${exception.id}`} variant="success">Resolved</Badge>}
                  </div>
                  <p className="m-0 mb-1 text-xs">{exception.description}</p>
                  <div className="flex gap-3 text-[0.65rem] text-muted flex-wrap">
                    <span data-testid={`exception-user-${exception.id}`}>{exception.displayName ?? exception.userId}</span>
                    <span data-testid={`exception-date-${exception.id}`}>{new Date(exception.createdAt).toLocaleDateString()}</span>
                  </div>
                  {exception.resolved && exception.resolution && (
                    <div data-testid={`exception-resolution-${exception.id}`} className="mt-1.5 px-2.5 py-1.5 rounded-default bg-foreground/8 text-xs text-foreground">
                      ✓ {exception.resolution}
                    </div>
                  )}
                </div>
                {!exception.resolved && (
                  <div className="flex gap-1.5 shrink-0">
                    {onResolve && (
                      <Button variant="ghost" size="sm" onClick={() => setResolvingId(exception.id)} data-testid={`resolve-btn-${exception.id}`} className="h-7 px-2 text-xs border border-foreground/30 text-foreground hover:bg-foreground/5">
                        <Check className="h-3 w-3" />Resolve
                      </Button>
                    )}
                    {onAddComment && exception.planId && (
                      <Button variant="secondary" size="sm" onClick={() => setCommentingId(exception.id)} data-testid={`comment-btn-${exception.id}`} className="h-7 px-2 text-xs">
                        <MessageSquare className="h-3 w-3" />Comment
                      </Button>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {resolvingException && (
        <ResolveDialog exception={resolvingException} onConfirm={async (resolution) => { await onResolve!(resolvingException.id, { resolverId: actorUserId, resolution }); setResolvingId(null); }} onCancel={() => setResolvingId(null)} />
      )}
      {commentingException && (
        <CommentDialog exception={commentingException} onConfirm={(text) => onAddComment!({ managerId: actorUserId, ...(commentingException.planId ? { planId: commentingException.planId } : {}), text })} onCancel={() => setCommentingId(null)} />
      )}
    </section>
  );
}
