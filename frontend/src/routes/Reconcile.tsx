/**
 * ReconcilePage — post-lock outcome recording and scope-change review.
 * Route: /weekly/reconcile/:planId?
 */
import { useState, useCallback, useMemo } from "react";
import { useParams } from "react-router-dom";
import { Check } from "lucide-react";
import { Button } from "../components/ui/Button.js";
import { Badge } from "../components/ui/Badge.js";
import { Card, CardHeader, CardContent } from "../components/ui/Card.js";
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "../components/ui/Dialog.js";
import { cn } from "../lib/utils.js";
import { usePlanApi } from "../api/planHooks.js";
import { useQuery } from "../api/hooks.js";
import type { ReconciliationViewResponse, ReconcileCommitView, CommitOutcome, ChessPiece, CarryForwardReason } from "../api/planTypes.js";
import { OutcomeSelector } from "../components/reconcile/OutcomeSelector.js";
import { CarryForwardDialog } from "../components/reconcile/CarryForwardDialog.js";
import { ScopeChangeTimeline } from "../components/lock/ScopeChangeTimeline.js";

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = { KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙" };

const NOTES_REQUIRED: Set<CommitOutcome> = new Set(["PARTIALLY_ACHIEVED", "NOT_ACHIEVED", "CANCELED"]);
const CARRY_FORWARD_ELIGIBLE: Set<CommitOutcome> = new Set(["NOT_ACHIEVED", "PARTIALLY_ACHIEVED"]);

function useReconcileView(planId: string | undefined) {
  const api = usePlanApi();
  return useQuery<ReconciliationViewResponse>(`reconcile-${planId ?? "none"}`, () => {
    if (!planId) return Promise.reject(new Error("No plan ID"));
    return api.getReconciliationView(planId);
  });
}

// ── BaselineColumn ────────────────────────────────────────────────────────────

function BaselineColumn({ commitView }: { readonly commitView: ReconcileCommitView }) {
  const baseline = commitView.baselineSnapshot;

  if (commitView.addedPostLock) {
    return (
      <div data-testid={`baseline-col-${commitView.commitId}`} className="px-2.5 py-2 rounded-sm bg-emerald-50 text-xs text-emerald-700 italic">
        Added post-lock — no baseline
      </div>
    );
  }
  if (!baseline) {
    return <div data-testid={`baseline-col-${commitView.commitId}`} className="text-xs text-muted italic">Baseline not available</div>;
  }

  const baselineChessPiece = baseline.chessPiece as ChessPiece | null;
  const baselineTitle = baseline.title as string | null;
  const baselineEstimate = baseline.estimatePoints as number | null;
  const baselineRcdo = baseline.rcdoNodeId as string | null;
  const titleChanged = baselineTitle != null && baselineTitle !== commitView.currentTitle;
  const pieceChanged = baselineChessPiece != null && baselineChessPiece !== commitView.currentChessPiece;
  const estimateChanged = baselineEstimate != null && baselineEstimate !== commitView.currentEstimatePoints;

  return (
    <div data-testid={`baseline-col-${commitView.commitId}`} className="flex flex-col gap-1.5">
      <div data-testid={`baseline-title-${commitView.commitId}`} className={cn("text-sm font-semibold rounded-sm", titleChanged ? "bg-red-50 text-red-800 px-1 py-px" : "text-foreground")}>
        {baselineTitle ?? "—"}
      </div>
      {baselineChessPiece && (
        <div data-testid={`baseline-piece-${commitView.commitId}`} className={cn("text-xs rounded-sm", pieceChanged ? "bg-red-50 text-red-800 px-1 py-px" : "text-muted")}>
          {CHESS_PIECE_ICONS[baselineChessPiece]} {baselineChessPiece}
        </div>
      )}
      {baselineEstimate != null && (
        <div data-testid={`baseline-estimate-${commitView.commitId}`} className={cn("text-xs rounded-sm", estimateChanged ? "bg-red-50 text-red-800 px-1 py-px" : "text-muted")}>
          {baselineEstimate} pts
        </div>
      )}
      {baselineRcdo && (
        <div data-testid={`baseline-rcdo-${commitView.commitId}`} className="text-[0.7rem] text-muted font-mono">
          🎯 {baselineRcdo.slice(0, 8)}…
        </div>
      )}
    </div>
  );
}

// ── CurrentColumn ─────────────────────────────────────────────────────────────

function CurrentColumn({ commitView }: { readonly commitView: ReconcileCommitView }) {
  const baseline = commitView.baselineSnapshot;
  const titleChanged = baseline?.title != null && baseline.title !== commitView.currentTitle;
  const pieceChanged = baseline?.chessPiece != null && baseline.chessPiece !== commitView.currentChessPiece;
  const estimateChanged = baseline?.estimatePoints != null && baseline.estimatePoints !== commitView.currentEstimatePoints;

  return (
    <div data-testid={`current-col-${commitView.commitId}`} className="flex flex-col gap-1.5">
      <div data-testid={`current-title-${commitView.commitId}`} className={cn("text-sm font-semibold rounded-sm", titleChanged ? "bg-emerald-50 text-emerald-800 px-1 py-px" : "text-foreground")}>
        {commitView.currentTitle}
      </div>
      <div data-testid={`current-piece-${commitView.commitId}`} className={cn("text-xs rounded-sm", pieceChanged ? "bg-emerald-50 text-emerald-800 px-1 py-px" : "text-muted")}>
        {CHESS_PIECE_ICONS[commitView.currentChessPiece]} {commitView.currentChessPiece}
      </div>
      {commitView.currentEstimatePoints != null && (
        <div data-testid={`current-estimate-${commitView.commitId}`} className={cn("text-xs rounded-sm", estimateChanged ? "bg-emerald-50 text-emerald-800 px-1 py-px" : "text-muted")}>
          {commitView.currentEstimatePoints} pts
        </div>
      )}
      {commitView.linkedTicketStatus && (
        <span data-testid={`ticket-status-${commitView.commitId}`} className={cn("inline-flex items-center gap-1 px-2 py-px rounded-full text-[0.7rem] font-semibold", commitView.linkedTicketStatus === "DONE" ? "bg-emerald-100 text-emerald-800" : "bg-indigo-100 text-indigo-700")}>
          🎫 {commitView.linkedTicketStatus}
        </span>
      )}
    </div>
  );
}

// ── ReconcileCommitRow ────────────────────────────────────────────────────────

interface ReconcileCommitRowProps {
  readonly commitView: ReconcileCommitView;
  readonly outcome: CommitOutcome | null;
  readonly notes: string;
  readonly carryForward: boolean;
  readonly notesError: string | null;
  readonly isReadOnly: boolean;
  readonly onOutcomeChange: (outcome: CommitOutcome) => void;
  readonly onNotesChange: (notes: string) => void;
  readonly onCarryForwardChange: (checked: boolean) => void;
}

function ReconcileCommitRow({ commitView, outcome, notes, carryForward, notesError, isReadOnly, onOutcomeChange, onNotesChange, onCarryForwardChange }: ReconcileCommitRowProps) {
  const needsNotes = outcome != null && NOTES_REQUIRED.has(outcome);
  const canCarryForward = !isReadOnly && outcome != null && CARRY_FORWARD_ELIGIBLE.has(outcome);
  const isAutoAchieved = commitView.linkedTicketStatus === "DONE" && outcome === "ACHIEVED" && commitView.currentOutcome === "ACHIEVED";

  return (
    <div data-testid={`reconcile-commit-row-${commitView.commitId}`} className="rounded-default border border-border bg-surface overflow-hidden">
      {/* Row header */}
      <div className="px-3 py-2.5 bg-background border-b border-border flex items-center gap-2 flex-wrap">
        <span className="font-semibold text-sm flex-1">
          {CHESS_PIECE_ICONS[commitView.currentChessPiece]} {commitView.currentTitle}
        </span>
        {commitView.addedPostLock && <Badge data-testid={`added-post-lock-badge-${commitView.commitId}`} variant="success">+ Post-lock</Badge>}
        {commitView.removedPostLock && <Badge data-testid={`removed-post-lock-badge-${commitView.commitId}`} variant="danger">− Removed</Badge>}
        {isAutoAchieved && <Badge data-testid={`auto-achieved-badge-${commitView.commitId}`} variant="success">✅ Auto-achieved (ticket Done)</Badge>}
      </div>

      {/* Baseline vs current */}
      <div className="grid border-b border-border" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="px-3 py-2.5 border-r border-border">
          <p className="m-0 mb-1.5 text-[0.65rem] font-semibold uppercase tracking-wider text-muted">Baseline (at lock)</p>
          <BaselineColumn commitView={commitView} />
        </div>
        <div className="px-3 py-2.5">
          <p className="m-0 mb-1.5 text-[0.65rem] font-semibold uppercase tracking-wider text-muted">Current</p>
          <CurrentColumn commitView={commitView} />
        </div>
      </div>

      {/* Outcome selector */}
      <div className={cn("px-3 py-2.5", (needsNotes || canCarryForward) && "border-b border-border")}>
        <OutcomeSelector commitId={commitView.commitId} value={outcome} onChange={onOutcomeChange} disabled={isReadOnly} />
      </div>

      {/* Notes textarea */}
      {needsNotes && (
        <div className={cn("px-3 py-2.5 flex flex-col gap-1.5", canCarryForward && "border-b border-border")}>
          <label htmlFor={`outcome-notes-${commitView.commitId}`} className="text-xs font-semibold">
            Outcome notes <span aria-hidden="true" className="text-danger">*</span>
          </label>
          <textarea
            id={`outcome-notes-${commitView.commitId}`}
            value={notes}
            onChange={(e) => onNotesChange(e.target.value)}
            rows={2}
            disabled={isReadOnly}
            data-testid={`outcome-notes-${commitView.commitId}`}
            aria-required="true"
            aria-describedby={notesError ? `notes-error-${commitView.commitId}` : undefined}
            className={cn("w-full rounded-default border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50", notesError ? "border-danger" : "border-border")}
            placeholder="Explain what happened…"
          />
          {notesError && <span id={`notes-error-${commitView.commitId}`} role="alert" className="text-xs text-danger">{notesError}</span>}
        </div>
      )}

      {/* Carry-forward checkbox */}
      {canCarryForward && (
        <div className="px-3 py-2.5">
          <label data-testid={`carry-forward-checkbox-label-${commitView.commitId}`} className="flex items-center gap-2 cursor-pointer text-sm">
            <input type="checkbox" checked={carryForward} onChange={(e) => onCarryForwardChange(e.target.checked)} data-testid={`carry-forward-checkbox-${commitView.commitId}`} className="h-4 w-4" />
            <span>🔁 Carry this commit forward to next week</span>
          </label>
        </div>
      )}
    </div>
  );
}

// ── SubmitConfirmDialog ───────────────────────────────────────────────────────

function SubmitConfirmDialog({ achievedCount, partialCount, notAchievedCount, canceledCount, onConfirm, onCancel, isSubmitting }: {
  readonly achievedCount: number; readonly partialCount: number;
  readonly notAchievedCount: number; readonly canceledCount: number;
  readonly onConfirm: () => void; readonly onCancel: () => void; readonly isSubmitting: boolean;
}) {
  const summaryItems = [
    { label: "Achieved", count: achievedCount, cls: "bg-emerald-50 text-emerald-700" },
    { label: "Partial", count: partialCount, cls: "bg-amber-50 text-amber-700" },
    { label: "Not Achieved", count: notAchievedCount, cls: "bg-red-50 text-red-700" },
    { label: "Canceled", count: canceledCount, cls: "bg-slate-100 text-slate-600" },
  ];
  return (
    <Dialog open onClose={onCancel} aria-label="Submit Reconciliation" data-testid="reconcile-submit-dialog">
      <DialogHeader>
        <DialogTitle>Submit Reconciliation?</DialogTitle>
        <DialogDescription>This will finalise the week and create an immutable snapshot. This cannot be undone.</DialogDescription>
      </DialogHeader>
      <div data-testid="reconcile-submit-summary" className="mb-4 grid grid-cols-2 gap-2">
        {summaryItems.map(({ label, count, cls }) => (
          <div key={label} className={cn("rounded-default px-3 py-2.5 text-center", cls)}>
            <div className="text-xl font-bold">{count}</div>
            <div className="text-xs">{label}</div>
          </div>
        ))}
      </div>
      <DialogFooter>
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting} data-testid="reconcile-submit-cancel">Cancel</Button>
        <Button variant="primary" onClick={onConfirm} disabled={isSubmitting} data-testid="reconcile-submit-confirm">
          {isSubmitting ? "Submitting…" : "Confirm Submission"}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}

// ── ReconcilePage ─────────────────────────────────────────────────────────────

export default function ReconcilePage() {
  const { planId: planIdParam } = useParams<{ planId?: string }>();
  const planId = planIdParam;
  const planApi = usePlanApi();
  const { data, loading, error, refetch } = useReconcileView(planId);

  const [localOutcomes, setLocalOutcomes] = useState<Record<string, CommitOutcome>>({});
  const [localNotes, setLocalNotes] = useState<Record<string, string>>({});
  const [notesErrors, setNotesErrors] = useState<Record<string, string>>({});
  const [carryForwardSet, setCarryForwardSet] = useState<Set<string>>(new Set());
  const [carryForwardTarget, setCarryForwardTarget] = useState<string | null>(null);
  const [showSubmitDialog, setShowSubmitDialog] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [savingOutcome, setSavingOutcome] = useState<string | null>(null);
  const [carryForwardLoading, setCarryForwardLoading] = useState(false);
  const [openingReconcile, setOpeningReconcile] = useState(false);
  const [openError, setOpenError] = useState<string | null>(null);

  const isPlanLocked = error !== null && (error.message.includes("LOCKED") || (error as { status?: number }).status === 400);
  const isReadOnly = data?.plan.state === "RECONCILED";

  const handleOpenReconciliation = useCallback(async () => {
    if (!planId) return;
    setOpeningReconcile(true); setOpenError(null);
    try { await planApi.openReconciliation(planId); refetch(); }
    catch (err) { setOpenError(err instanceof Error ? err.message : "Failed to open reconciliation"); }
    finally { setOpeningReconcile(false); }
  }, [planId, planApi, refetch]);

  const effectiveOutcomes: Record<string, CommitOutcome | null> = useMemo(() => {
    const map: Record<string, CommitOutcome | null> = {};
    for (const cv of data?.commits ?? []) {
      const local = localOutcomes[cv.commitId];
      map[cv.commitId] = local !== undefined ? local : cv.currentOutcome;
    }
    return map;
  }, [data, localOutcomes]);

  const effectiveNotes: Record<string, string> = useMemo(() => {
    const map: Record<string, string> = {};
    for (const cv of data?.commits ?? []) {
      const local = localNotes[cv.commitId];
      map[cv.commitId] = local !== undefined ? local : (cv.currentOutcomeNotes ?? "");
    }
    return map;
  }, [data, localNotes]);

  const handleOutcomeChange = useCallback(async (commitId: string, outcome: CommitOutcome) => {
    if (isReadOnly || !data) return;
    setLocalOutcomes((prev) => ({ ...prev, [commitId]: outcome }));
    setNotesErrors((prev) => { const next = { ...prev }; delete next[commitId]; return next; });
    if (!NOTES_REQUIRED.has(outcome)) setLocalNotes((prev) => ({ ...prev, [commitId]: "" }));
    if (!CARRY_FORWARD_ELIGIBLE.has(outcome)) { setCarryForwardSet((prev) => { const next = new Set(prev); next.delete(commitId); return next; }); }
    if (!NOTES_REQUIRED.has(outcome)) {
      setSavingOutcome(commitId);
      try { await planApi.setCommitOutcome(data.plan.id, commitId, { outcome }); refetch(); }
      catch { setLocalOutcomes((prev) => { const next = { ...prev }; delete next[commitId]; return next; }); }
      finally { setSavingOutcome(null); }
    }
  }, [isReadOnly, data, planApi, refetch]);

  const handleNotesSave = useCallback(async (commitId: string) => {
    if (isReadOnly || !data) return;
    const outcome = effectiveOutcomes[commitId];
    if (!outcome || !NOTES_REQUIRED.has(outcome)) return;
    const notes = effectiveNotes[commitId] ?? "";
    if (!notes.trim()) { setNotesErrors((prev) => ({ ...prev, [commitId]: "Notes are required for this outcome" })); return; }
    setSavingOutcome(commitId);
    try {
      await planApi.setCommitOutcome(data.plan.id, commitId, { outcome, notes });
      refetch(); setNotesErrors((prev) => { const next = { ...prev }; delete next[commitId]; return next; });
    } catch { /* ignore */ } finally { setSavingOutcome(null); }
  }, [isReadOnly, data, planApi, effectiveOutcomes, effectiveNotes, refetch]);

  function validateBeforeSubmit(): boolean {
    if (!data) return false;
    const newErrors: Record<string, string> = {}; let valid = true;
    for (const cv of data.commits) {
      const outcome = effectiveOutcomes[cv.commitId];
      if (!outcome) { newErrors[cv.commitId] = "Outcome is required"; valid = false; continue; }
      if (NOTES_REQUIRED.has(outcome)) { const notes = effectiveNotes[cv.commitId] ?? ""; if (!notes.trim()) { newErrors[cv.commitId] = "Notes are required for this outcome"; valid = false; } }
    }
    setNotesErrors(newErrors);
    return valid;
  }

  const handleSubmit = useCallback(async () => {
    if (!data) return;
    setSubmitLoading(true); setSubmitError(null);
    try {
      for (const cv of data.commits) {
        const outcome = effectiveOutcomes[cv.commitId];
        if (!outcome) continue;
        const notes = effectiveNotes[cv.commitId] ?? "";
        if (NOTES_REQUIRED.has(outcome) && notes.trim()) await planApi.setCommitOutcome(data.plan.id, cv.commitId, { outcome, notes });
      }
      await planApi.submitReconciliation(data.plan.id);
      setShowSubmitDialog(false); refetch();
    } catch (err) { setSubmitError(err instanceof Error ? err.message : "Submission failed"); }
    finally { setSubmitLoading(false); }
  }, [data, effectiveOutcomes, effectiveNotes, planApi, refetch]);

  const handleCarryForwardConfirm = useCallback(async (targetWeekStart: string, reason: CarryForwardReason, reasonText?: string) => {
    if (!data || !carryForwardTarget) return;
    setCarryForwardLoading(true);
    try {
      await planApi.carryForward(data.plan.id, carryForwardTarget, { targetWeekStart, reason, ...(reasonText !== undefined ? { reasonText } : {}) });
      setCarryForwardTarget(null);
      setCarryForwardSet((prev) => { const next = new Set(prev); next.delete(carryForwardTarget); return next; });
      refetch();
    } catch (err) { setSubmitError(err instanceof Error ? err.message : "Carry-forward failed"); setCarryForwardTarget(null); }
    finally { setCarryForwardLoading(false); }
  }, [data, carryForwardTarget, planApi, refetch]);

  const outcomeSummary = useMemo(() => {
    const counts = { ACHIEVED: 0, PARTIALLY_ACHIEVED: 0, NOT_ACHIEVED: 0, CANCELED: 0 };
    for (const cv of data?.commits ?? []) { const o = effectiveOutcomes[cv.commitId]; if (o) counts[o] = (counts[o] ?? 0) + 1; }
    return counts;
  }, [data, effectiveOutcomes]);

  const allScopeEvents = useMemo(() => data?.commits.flatMap((cv) => cv.scopeChanges) ?? [], [data]);

  const canSubmit = !isReadOnly && data?.plan.state === "RECONCILING" && data.commits.every((cv) => effectiveOutcomes[cv.commitId] != null && (!NOTES_REQUIRED.has(effectiveOutcomes[cv.commitId]!) || effectiveNotes[cv.commitId]?.trim()));

  // ── Render ────────────────────────────────────────────────────────────────

  if (!planId) {
    return (
      <div data-testid="page-reconcile" className="flex flex-col gap-4">
        <h2 className="m-0 text-xl font-bold">Reconcile</h2>
        <p className="text-sm text-muted">
          No plan selected. Navigate to{" "}
          <a href="/weekly/my-week" className="text-primary hover:underline">My Week</a>
          {" "}and use the Reconcile link when your plan is LOCKED or RECONCILING.
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div data-testid="page-reconcile" className="flex flex-col gap-4">
        <h2 className="m-0 text-xl font-bold">Reconcile</h2>
        <div role="status" aria-label="Loading reconciliation view" data-testid="reconcile-loading" className="text-sm text-muted">Loading…</div>
      </div>
    );
  }

  if (error) {
    if (isPlanLocked) {
      return (
        <div data-testid="page-reconcile" className="flex flex-col gap-4">
          <h2 className="m-0 text-xl font-bold">Reconcile</h2>
          <Card data-testid="reconcile-locked-prompt" className="max-w-[480px]">
            <CardContent className="py-4">
              <p className="m-0 mb-3 font-semibold">🔒 Plan is locked — ready for reconciliation</p>
              <p className="m-0 mb-4 text-sm text-muted">The week is over. Open reconciliation to record outcomes for each commitment.</p>
              {openError && <p role="alert" className="text-sm text-danger mb-3">{openError}</p>}
              <Button variant="primary" onClick={() => void handleOpenReconciliation()} disabled={openingReconcile} data-testid="open-reconciliation-btn">
                {openingReconcile ? "Opening…" : "Open Reconciliation →"}
              </Button>
            </CardContent>
          </Card>
        </div>
      );
    }
    return (
      <div data-testid="page-reconcile" className="flex flex-col gap-4">
        <h2 className="m-0 text-xl font-bold">Reconcile</h2>
        <div role="alert" data-testid="reconcile-error" className="rounded-default border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-danger">
          Failed to load reconciliation view: {error.message}
        </div>
      </div>
    );
  }

  if (!data) return null;

  const carryForwardCommit = carryForwardTarget ? data.commits.find((cv) => cv.commitId === carryForwardTarget) : null;

  return (
    <div data-testid="page-reconcile" className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="m-0 text-xl font-bold">Reconcile</h2>
        <Badge data-testid="reconcile-plan-state" variant={data.plan.state === "RECONCILED" ? "reconciled" : "reconciling"}>
          {data.plan.state}
        </Badge>
      </div>

      {/* Stats bar */}
      <Card data-testid="reconcile-stats-bar">
        <CardContent className="py-3 flex gap-6 flex-wrap text-sm">
          <div><span className="font-bold">{data.commitCount}</span> <span className="text-muted">commits</span></div>
          <div><span className="font-bold">{data.outcomesSetCount}</span> <span className="text-muted">outcomes set</span></div>
          <div><span className="font-bold">{data.baselineTotalPoints} pts</span> <span className="text-muted">baseline</span></div>
          <div><span className="font-bold">{data.currentTotalPoints} pts</span> <span className="text-muted">current</span></div>
        </CardContent>
      </Card>

      {submitError && (
        <div role="alert" data-testid="reconcile-submit-error" className="rounded-default border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-danger">{submitError}</div>
      )}

      {isReadOnly && (
        <div data-testid="reconcile-readonly-banner" className="flex items-center gap-2 rounded-default border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          <Check className="h-4 w-4 shrink-0" aria-hidden="true" />
          This plan has been fully reconciled and is now read-only.
        </div>
      )}

      {/* Commit rows */}
      <div data-testid="reconcile-commit-list" className="flex flex-col gap-3.5">
        {data.commits.map((cv) => {
          const commitId = cv.commitId;
          const outcome = effectiveOutcomes[commitId] ?? null;
          const notes = effectiveNotes[commitId] ?? "";
          const saving = savingOutcome === commitId;
          return (
            <div key={commitId} className="relative">
              {saving && (
                <div data-testid={`saving-indicator-${commitId}`} className="absolute top-1.5 right-1.5 text-[0.65rem] text-muted italic">Saving…</div>
              )}
              <ReconcileCommitRow
                commitView={cv} outcome={outcome} notes={notes}
                carryForward={carryForwardSet.has(commitId)}
                notesError={notesErrors[commitId] ?? null}
                isReadOnly={isReadOnly}
                onOutcomeChange={(o) => void handleOutcomeChange(commitId, o)}
                onNotesChange={(n) => setLocalNotes((prev) => ({ ...prev, [commitId]: n }))}
                onCarryForwardChange={(checked) => {
                  if (checked) setCarryForwardTarget(commitId);
                  setCarryForwardSet((prev) => { const next = new Set(prev); if (checked) next.add(commitId); else next.delete(commitId); return next; });
                }}
              />
              {!isReadOnly && outcome && NOTES_REQUIRED.has(outcome) && (
                <Button variant="secondary" size="sm" type="button" onClick={() => void handleNotesSave(commitId)} data-testid={`save-notes-${commitId}`} className="mt-1">
                  Save notes
                </Button>
              )}
            </div>
          );
        })}
      </div>

      {/* Scope changes */}
      {allScopeEvents.length > 0 && (
        <section aria-labelledby="scope-changes-heading">
          <h3 id="scope-changes-heading" className="m-0 mb-2.5 text-base font-semibold">Post-lock Scope Changes</h3>
          <ScopeChangeTimeline events={allScopeEvents} />
        </section>
      )}

      {/* Submit section */}
      {!isReadOnly && (
        <Card data-testid="reconcile-submit-section">
          <CardHeader>
            <div>
              <p className="m-0 font-semibold text-sm">Ready to submit?</p>
              <p className="m-0 mt-1 text-xs text-muted">{data.outcomesSetCount} / {data.commitCount} outcomes set. All commits must have outcomes before submitting.</p>
            </div>
            <Button
              variant={canSubmit ? "primary" : "secondary"}
              disabled={!canSubmit}
              onClick={() => { if (validateBeforeSubmit()) setShowSubmitDialog(true); }}
              data-testid="reconcile-submit-btn"
            >
              Submit Reconciliation
            </Button>
          </CardHeader>
        </Card>
      )}

      {showSubmitDialog && (
        <SubmitConfirmDialog achievedCount={outcomeSummary.ACHIEVED} partialCount={outcomeSummary.PARTIALLY_ACHIEVED} notAchievedCount={outcomeSummary.NOT_ACHIEVED} canceledCount={outcomeSummary.CANCELED} onConfirm={() => void handleSubmit()} onCancel={() => setShowSubmitDialog(false)} isSubmitting={submitLoading} />
      )}

      {carryForwardTarget && carryForwardCommit && (
        <CarryForwardDialog
          commit={{ id: carryForwardCommit.commitId, planId: data.plan.id, ownerUserId: data.plan.ownerUserId, title: carryForwardCommit.currentTitle, chessPiece: carryForwardCommit.currentChessPiece, priorityOrder: 1, carryForwardStreak: 0, createdAt: "", updatedAt: "" }}
          onConfirm={(targetWeekStart, reason, reasonText) => void handleCarryForwardConfirm(targetWeekStart, reason, reasonText)}
          onCancel={() => {
            setCarryForwardTarget(null);
            setCarryForwardSet((prev) => { const next = new Set(prev); next.delete(carryForwardTarget); return next; });
          }}
          isSubmitting={carryForwardLoading}
        />
      )}
    </div>
  );
}
