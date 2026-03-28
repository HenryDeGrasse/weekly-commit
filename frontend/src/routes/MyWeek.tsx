/**
 * My Week — personal weekly plan view.
 * Route: /weekly/my-week
 */
import { useState, useCallback, useMemo, Component, type ErrorInfo, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight, Lock, AlertTriangle, Check, X, ArrowRight, Sparkles } from "lucide-react";
import { Button } from "../components/ui/Button.js";
import { Badge } from "../components/ui/Badge.js";
import { Card, CardHeader, CardTitle, CardContent } from "../components/ui/Card.js";
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "../components/ui/Dialog.js";
import { useCurrentPlan, usePlanApi } from "../api/planHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import { usePlanHistory, useCarryForwardLineage, useTicketApi } from "../api/ticketHooks.js";
import { PlanHistoryView } from "../components/myweek/PlanHistoryView.js";
import { CarryForwardLineageView } from "../components/myweek/CarryForwardLineageView.js";
import { TicketForm } from "../components/tickets/TicketForm.js";
import { useHostBridge } from "../host/HostProvider.js";
import type { CreateTicketPayload } from "../api/ticketTypes.js";
import { CommitList } from "../components/myweek/CommitList.js";
import { CommitForm } from "../components/myweek/CommitForm.js";
import { CapacityMeter } from "../components/myweek/CapacityMeter.js";
import { SoftWarningsPanel } from "../components/myweek/SoftWarningsPanel.js";
import { PreLockValidationPanel } from "../components/lock/PreLockValidationPanel.js";
import { LockConfirmDialog } from "../components/lock/LockConfirmDialog.js";
import { ScopeChangeDialog } from "../components/lock/ScopeChangeDialog.js";
import { ScopeChangeTimeline } from "../components/lock/ScopeChangeTimeline.js";
import { getEffectivePreLockErrors } from "../components/lock/lockValidation.js";
import { InsightPanel } from "../components/ai/InsightPanel.js";
import { AiLintPanel } from "../components/ai/AiLintPanel.js";
import { AiCommitComposer } from "../components/ai/AiCommitComposer.js";
import { ProactiveRiskBanner } from "../components/ai/ProactiveRiskBanner.js";
import { useAiStatus } from "../api/aiHooks.js";
import type {
  CommitResponse, PlanState, LockValidationError,
  ScopeChangeEventResponse, UpdateCommitPayload, CreateCommitPayload,
} from "../api/planTypes.js";

// ── Helpers ────────────────────────────────────────────────────────────────

function getWeekStartDate(offsetWeeks = 0): string {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const daysToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const monday = new Date(now);
  monday.setDate(now.getDate() + daysToMonday + offsetWeeks * 7);
  return monday.toISOString().slice(0, 10);
}

function formatWeekLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  return `Week of ${d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}`;
}

// ── PlanStateBadge ──────────────────────────────────────────────────────────

const STATE_VARIANT: Record<PlanState, "draft" | "locked" | "reconciling" | "reconciled"> = {
  DRAFT: "draft", LOCKED: "locked", RECONCILING: "reconciling", RECONCILED: "reconciled",
};

function PlanStateBadge({ state }: { readonly state: PlanState }) {
  return <Badge data-testid="plan-state-badge" variant={STATE_VARIANT[state]}>{state}</Badge>;
}

// ── ComplianceBadge ─────────────────────────────────────────────────────────

function ComplianceBadge({ compliant }: { readonly compliant: boolean }) {
  return compliant ? (
    <span data-testid="compliance-badge-ok" title="Plan meets all lock requirements" className="flex items-center gap-1 text-sm font-medium text-success">
      <Check className="h-3.5 w-3.5" aria-hidden="true" />Compliant
    </span>
  ) : (
    <span data-testid="compliance-badge-warn" title="Plan has unresolved compliance issues" className="flex items-center gap-1 text-sm font-medium text-warning">
      <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />Not compliant
    </span>
  );
}

// ── DeleteConfirmDialog ─────────────────────────────────────────────────────

function DeleteConfirmDialog({ title, onConfirm, onCancel }: { readonly title: string; readonly onConfirm: () => void; readonly onCancel: () => void }) {
  return (
    <Dialog open onClose={onCancel} aria-label="Delete commit" data-testid="delete-confirm-dialog">
      <DialogHeader>
        <DialogTitle>Delete commit?</DialogTitle>
        <DialogDescription>
          Are you sure you want to delete <strong>"{title}"</strong>? This cannot be undone.
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button variant="danger" onClick={onConfirm} data-testid="delete-confirm-btn">Delete</Button>
      </DialogFooter>
    </Dialog>
  );
}

class AiErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean }> {
  state = { hasError: false };
  static getDerivedStateFromError() { return { hasError: true }; }
  componentDidCatch(_: Error, __: ErrorInfo) { /* silently suppress */ }
  render() { return this.state.hasError ? null : this.props.children; }
}

// ── MyWeek Page ─────────────────────────────────────────────────────────────

export default function MyWeek() {
  const [weekOffset, setWeekOffset] = useState(0);
  const weekStartDate = getWeekStartDate(weekOffset);

  const bridge = useHostBridge();
  const currentUserId = bridge.context.authenticatedUser.id;

  const planApi = usePlanApi();
  const { data: planData, loading: planLoading, error: planError, refetch: refetchPlan } = useCurrentPlan(weekStartDate);
  const { data: rcdoTree } = useRcdoTree();

  const [showHistory, setShowHistory] = useState(false);
  const { data: planHistory, loading: historyLoading } = usePlanHistory(showHistory ? currentUserId : null);
  const [lineageCommitId, setLineageCommitId] = useState<string | null>(null);
  const { data: lineage, loading: lineageLoading } = useCarryForwardLineage(lineageCommitId);

  const ticketApi = useTicketApi();
  const [ticketFormVisible, setTicketFormVisible] = useState(false);
  const [ticketFormInitialValues, setTicketFormInitialValues] = useState<Partial<CreateTicketPayload>>({});

  type FormMode = "create" | "edit" | null;
  const [formMode, setFormMode] = useState<FormMode>(null);
  const [editingCommit, setEditingCommit] = useState<CommitResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CommitResponse | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [lockLoading, setLockLoading] = useState(false);
  const [showPreLockValidation, setShowPreLockValidation] = useState(false);
  const [lockValidationErrors, setLockValidationErrors] = useState<LockValidationError[]>([]);
  const [showLockConfirm, setShowLockConfirm] = useState(false);

  type ScopeChangeMode = "add" | "edit" | "remove" | null;
  const [scopeChangeMode, setScopeChangeMode] = useState<ScopeChangeMode>(null);
  const [scopeChangeTargetCommit, setScopeChangeTargetCommit] = useState<CommitResponse | null>(null);
  const [pendingEditPayload, setPendingEditPayload] = useState<UpdateCommitPayload | null>(null);
  const [pendingCreatePayload, setPendingCreatePayload] = useState<CreateCommitPayload | null>(null);
  const [scopeChangeSaving, setScopeChangeSaving] = useState(false);
  const [scopeChangeEvents, setScopeChangeEvents] = useState<ScopeChangeEventResponse[]>([]);
  const [showTimeline, setShowTimeline] = useState(false);

  const aiAssistanceEnabled = bridge.context.featureFlags.aiAssistanceEnabled;
  const { data: aiStatus } = useAiStatus();
  /** True when the feature flag is on AND the provider is reachable. */
  const aiComposerEnabled = aiAssistanceEnabled && (aiStatus?.available ?? false);

  const [showAiComposer, setShowAiComposer] = useState(false);
  /** Monotonically increasing counter — bumped after every commit CRUD to trigger lint re-run. */
  const [lintRefreshKey, setLintRefreshKey] = useState(0);
  /** Pre-filled values passed from AI composer → CommitForm when user switches to manual. */
  const [commitFormInitialValues, setCommitFormInitialValues] = useState<
    Partial<CreateCommitPayload>
  >({});

  const plan = planData?.plan;
  const commits = useMemo(() => planData?.commits ?? [], [planData]);
  const effectivePreLockErrors = useMemo(() => getEffectivePreLockErrors(commits, lockValidationErrors), [commits, lockValidationErrors]);
  const isDraft = plan?.state === "DRAFT";
  const isLocked = plan?.state === "LOCKED";

  // ── Handlers ──────────────────────────────────────────────────────────────

  /** Open AI composer (when AI is available) or CommitForm directly. */
  const handleOpenCreate = useCallback(() => {
    if (isLocked) {
      if (aiComposerEnabled) {
        // Post-lock: open AI composer first; the payload will flow through
        // handleCreateCommit which triggers the scope-change dialog.
        setShowAiComposer(true);
        setCommitFormInitialValues({});
      } else {
        setScopeChangeMode("add");
        setScopeChangeTargetCommit(null);
        setPendingCreatePayload(null);
      }
    } else if (aiComposerEnabled) {
      setShowAiComposer(true);
      setCommitFormInitialValues({});
    } else {
      setFormMode("create");
      setEditingCommit(null);
    }
    setActionError(null);
  }, [isLocked, aiComposerEnabled]);

  /** Always open the manual CommitForm, bypassing the AI composer. */
  const handleOpenManualCreate = useCallback(() => {
    setCommitFormInitialValues({});
    setFormMode("create");
    setEditingCommit(null);
    setActionError(null);
  }, []);

  const handleOpenEdit = useCallback((commit: CommitResponse) => {
    setFormMode("edit"); setEditingCommit(commit); setActionError(null);
  }, []);

  const handleFormCancel = useCallback(() => {
    setFormMode(null);
    setEditingCommit(null);
    setCommitFormInitialValues({});
  }, []);

  const handleCreateCommit = useCallback(async (payload: CreateCommitPayload) => {
    if (!plan) return;
    if (isLocked) { setPendingCreatePayload(payload); setScopeChangeMode("add"); setScopeChangeTargetCommit(null); setFormMode(null); return; }
    await planApi.createCommit(plan.id, payload);
    refetchPlan(); setFormMode(null); setLintRefreshKey((k) => k + 1);
  }, [plan, isLocked, planApi, refetchPlan]);

  const handleUpdateCommit = useCallback(async (payload: UpdateCommitPayload) => {
    if (!plan || !editingCommit) return;
    if (isLocked) { setPendingEditPayload(payload); setScopeChangeMode("edit"); setScopeChangeTargetCommit(editingCommit); setFormMode(null); setEditingCommit(null); return; }
    await planApi.updateCommit(plan.id, editingCommit.id, payload);
    refetchPlan(); setFormMode(null); setEditingCommit(null); setLintRefreshKey((k) => k + 1);
  }, [plan, editingCommit, isLocked, planApi, refetchPlan]);

  const handleDeleteRequest = useCallback((commitId: string) => {
    const commit = commits.find((c) => c.id === commitId);
    if (!commit) return;
    if (isLocked) { setScopeChangeMode("remove"); setScopeChangeTargetCommit(commit); }
    else { setDeleteTarget(commit); }
  }, [commits, isLocked]);

  const handleDeleteConfirm = useCallback(async () => {
    if (!plan || !deleteTarget) return;
    try { await planApi.deleteCommit(plan.id, deleteTarget.id); refetchPlan(); setLintRefreshKey((k) => k + 1); }
    catch (err) { setActionError(err instanceof Error ? err.message : "Delete failed"); }
    finally { setDeleteTarget(null); }
  }, [plan, deleteTarget, planApi, refetchPlan]);

  const handleReorder = useCallback(async (orderedIds: string[]) => {
    if (!plan) return;
    try { await planApi.reorderCommits(plan.id, orderedIds); refetchPlan(); setLintRefreshKey((k) => k + 1); }
    catch (err) { setActionError(err instanceof Error ? err.message : "Reorder failed"); }
  }, [plan, planApi, refetchPlan]);

  const handleCreateTicketFromCommit = useCallback((commit: CommitResponse) => {
    const teamId = bridge.context.currentTeam?.id;
    setTicketFormInitialValues({
      title: commit.title,
      ...(commit.description ? { description: commit.description } : {}),
      ...(commit.rcdoNodeId ? { rcdoNodeId: commit.rcdoNodeId } : {}),
      ...(commit.estimatePoints != null ? { estimatePoints: commit.estimatePoints } : {}),
      reporterUserId: currentUserId,
      ...(teamId ? { teamId } : {}),
    });
    setTicketFormVisible(true);
  }, [currentUserId, bridge]);

  const handleTicketFormSubmit = useCallback(async (payload: CreateTicketPayload) => {
    await ticketApi.createTicket(payload);
    setTicketFormVisible(false); setTicketFormInitialValues({});
  }, [ticketApi]);

  const handleLockButtonClick = useCallback(() => {
    setShowPreLockValidation(true); setLockValidationErrors([]); setShowLockConfirm(false);
  }, []);

  const handlePreLockContinue = useCallback(() => { setShowLockConfirm(true); }, []);

  const handleLock = useCallback(async () => {
    if (!plan) return;
    setLockLoading(true); setActionError(null);
    try {
      const response = await planApi.lockPlan(plan.id);
      if (!response.success) { setLockValidationErrors(response.errors ?? []); setShowLockConfirm(false); return; }
      setShowPreLockValidation(false); setShowLockConfirm(false); setLockValidationErrors([]); refetchPlan();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Lock failed"); setShowLockConfirm(false);
    } finally { setLockLoading(false); }
  }, [plan, planApi, refetchPlan]);

  const handleScopeChangeConfirm = useCallback(async (reason: string) => {
    if (!plan) return;
    setScopeChangeSaving(true); setActionError(null);
    try {
      if (scopeChangeMode === "add" && pendingCreatePayload) {
        const result = await planApi.applyScopeChange(plan.id, { action: "ADD", reason, commitData: pendingCreatePayload });
        setScopeChangeEvents(result.events);
      } else if (scopeChangeMode === "remove" && scopeChangeTargetCommit) {
        const result = await planApi.applyScopeChange(plan.id, { action: "REMOVE", reason, commitId: scopeChangeTargetCommit.id });
        setScopeChangeEvents(result.events);
      } else if (scopeChangeMode === "edit" && scopeChangeTargetCommit && pendingEditPayload) {
        const result = await planApi.applyScopeChange(plan.id, { action: "EDIT", reason, commitId: scopeChangeTargetCommit.id, changes: pendingEditPayload });
        setScopeChangeEvents(result.events);
      }
      refetchPlan();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Scope change failed");
    } finally {
      setScopeChangeSaving(false); setScopeChangeMode(null); setScopeChangeTargetCommit(null);
      setPendingEditPayload(null); setPendingCreatePayload(null);
    }
  }, [plan, scopeChangeMode, pendingCreatePayload, pendingEditPayload, scopeChangeTargetCommit, planApi, refetchPlan]);

  const handleScopeChangeCancel = useCallback(() => {
    setScopeChangeMode(null); setScopeChangeTargetCommit(null); setPendingEditPayload(null); setPendingCreatePayload(null);
  }, []);

  const handleLoadTimeline = useCallback(async () => {
    if (!plan) return;
    try { const result = await planApi.getScopeChangeTimeline(plan.id); setScopeChangeEvents(result.events); setShowTimeline(true); }
    catch { /* non-critical */ }
  }, [plan, planApi]);

  function buildRcdoLabels(): Record<string, string> {
    const map: Record<string, string> = {};
    function traverse(nodes: typeof rcdoTree, path: string[] = []) {
      if (!nodes) return;
      for (const n of nodes) { const nextPath = [...path, n.title]; map[n.id] = nextPath.join(" > "); traverse(n.children, nextPath); }
    }
    traverse(rcdoTree ?? []);
    return map;
  }
  const rcdoLabels = buildRcdoLabels();

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col gap-4" data-testid="page-my-week">
      {/* Page header */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="m-0 text-xl font-bold">My Week</h2>
        {isDraft && (
          <div className="flex items-center gap-2">
            <Button
              variant="primary"
              onClick={handleOpenCreate}
              data-testid="add-commit-btn"
            >
              {aiComposerEnabled ? (
                <>
                  <Sparkles className="h-3.5 w-3.5 mr-1" aria-hidden="true" />
                  Add Commit
                </>
              ) : (
                "+ Add Commit"
              )}
            </Button>
            {aiComposerEnabled && (
              <button
                type="button"
                onClick={handleOpenManualCreate}
                className="text-xs text-muted hover:text-foreground underline cursor-pointer bg-transparent border-none p-0"
                data-testid="add-commit-manually-btn"
              >
                + Add manually
              </button>
            )}
          </div>
        )}
      </div>

      {/* Week selector */}
      <div data-testid="week-selector" className="flex items-center gap-2 flex-wrap">
        <Button variant="secondary" size="sm" onClick={() => setWeekOffset((o) => o - 1)} aria-label="Previous week" data-testid="prev-week-btn">
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span data-testid="week-label" className="font-semibold text-sm min-w-[14rem] text-center">{formatWeekLabel(weekStartDate)}</span>
        <Button variant="secondary" size="sm" onClick={() => setWeekOffset((o) => o + 1)} aria-label="Next week" data-testid="next-week-btn">
          <ChevronRight className="h-4 w-4" />
        </Button>
        {weekOffset !== 0 && (
          <Button variant="secondary" size="sm" onClick={() => setWeekOffset(0)} data-testid="current-week-btn">Today</Button>
        )}
      </div>

      {/* Loading */}
      {planLoading && (
        <div role="status" aria-label="Loading plan" data-testid="plan-loading" className="text-sm text-muted">Loading plan…</div>
      )}

      {/* Error */}
      {planError && (
        <div role="alert" data-testid="plan-error" className="rounded-default border border-neutral-300 bg-neutral-100 px-3 py-2.5 text-sm text-foreground font-semibold">
          Failed to load plan: {planError.message}
        </div>
      )}

      {/* Plan header */}
      {plan && (
        <Card data-testid="plan-header">
          <CardHeader>
            <div className="flex items-center gap-2.5">
              <PlanStateBadge state={plan.state} />
              <ComplianceBadge compliant={plan.compliant} />
            </div>
            <div className="flex items-center gap-2">
              {actionError && (
                <span role="alert" data-testid="plan-action-error" className="text-xs text-foreground font-semibold">{actionError}</span>
              )}
              {isDraft && (
                <Button variant="primary" size="sm" onClick={handleLockButtonClick} disabled={lockLoading} data-testid="lock-plan-btn">
                  <Lock className="h-3.5 w-3.5" />
                  {lockLoading ? "Locking…" : "Lock Plan"}
                </Button>
              )}
              {isLocked && (
                <Button variant="primary" size="sm" onClick={handleOpenCreate} data-testid="post-lock-add-commit-btn">
                  {aiComposerEnabled && <Sparkles className="h-3.5 w-3.5 mr-1" aria-hidden="true" />}
                  + Add Commit
                </Button>
              )}
              {(plan.state === "LOCKED" || plan.state === "RECONCILING") && (
                <Link to={`/weekly/reconcile/${plan.id}`} data-testid="reconcile-hint">
                  <Button variant="primary" size="sm" type="button">
                    <ArrowRight className="h-3.5 w-3.5 mr-1" />
                    {plan.state === "RECONCILING" ? "Continue Reconciliation" : "Reconcile This Week"}
                  </Button>
                </Link>
              )}
            </div>
          </CardHeader>
        </Card>
      )}

      {/* Proactive risk banners — shown only while LOCKED so critical signals are immediately visible */}
      {aiAssistanceEnabled && isLocked && plan && (
        <AiErrorBoundary>
          <ProactiveRiskBanner planId={plan.id} />
        </AiErrorBoundary>
      )}

      {/* Personal AI insights */}
      {aiAssistanceEnabled && plan && (
        <InsightPanel mode="personal" planId={plan.id} />
      )}

      {/* Auto-lock system banner */}
      {plan?.systemLockedWithErrors && (
        <div data-testid="auto-lock-banner" role="alert" className="flex items-start gap-2 rounded-default border border-neutral-300 bg-neutral-100 px-4 py-3 text-sm text-foreground">
          <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0 text-neutral-500" aria-hidden="true" />
          <span><strong>System-locked with errors</strong> — this plan was automatically locked at the deadline. Some validation issues were present at lock time. Please review and reconcile.</span>
        </div>
      )}

      {/* Pre-lock validation panel */}
      {isDraft && showPreLockValidation && (
        <Card data-testid="pre-lock-validation-section">
          <CardHeader>
            <CardTitle>Pre-lock Validation</CardTitle>
            <Button variant="secondary" size="sm" type="button" onClick={() => setShowPreLockValidation(false)}>
              <X className="h-3.5 w-3.5" />Cancel
            </Button>
          </CardHeader>
          <CardContent>
            <PreLockValidationPanel errors={lockValidationErrors} commits={commits} />
            {effectivePreLockErrors.length === 0 && (
              <div className="mt-3.5 flex justify-end">
                <Button variant="primary" onClick={handlePreLockContinue} data-testid="pre-lock-continue-btn">
                  Continue to Lock →
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Capacity meter */}
      {plan && <CapacityMeter commits={commits} budgetPoints={plan.capacityBudgetPoints} isManagerOverride={plan.capacityBudgetPoints !== 10} />}

      {/* Soft warnings */}
      {commits.length > 0 && <SoftWarningsPanel commits={commits} />}

      {/* Inline AI commit quality hints — auto-runs in DRAFT so feedback appears without any extra click */}
      {aiAssistanceEnabled && isDraft && plan && commits.length > 0 && (
        <div data-testid="inline-ai-lint-panel">
          <AiErrorBoundary>
            <AiLintPanel planId={plan.id} userId={currentUserId} autoRun refreshKey={lintRefreshKey} />
          </AiErrorBoundary>
        </div>
      )}

      {/* Commit list */}
      {plan && (
        <CommitList
          commits={commits}
          planState={plan.state}
          onReorder={handleReorder}
          onEdit={handleOpenEdit}
          onDelete={handleDeleteRequest}
          rcdoLabels={rcdoLabels}
          onViewLineage={(commitId) => setLineageCommitId(commitId)}
          onCreateTicket={handleCreateTicketFromCommit}
        />
      )}

      {/* Scope change timeline */}
      {isLocked && scopeChangeEvents.length > 0 && (
        <section aria-labelledby="scope-timeline-heading">
          <h3 id="scope-timeline-heading" className="m-0 mb-2.5 text-sm font-bold">Post-lock Changes</h3>
          <ScopeChangeTimeline events={scopeChangeEvents} />
        </section>
      )}
      {isLocked && !showTimeline && (
        <Button variant="secondary" size="sm" type="button" onClick={() => void handleLoadTimeline()} data-testid="load-scope-timeline-btn" className="self-start">
          View scope-change history
        </Button>
      )}

      {/* AI Commit Composer — works for both DRAFT (direct create) and LOCKED (flows through scope-change) */}
      {showAiComposer && plan && (isDraft || isLocked) && (
        <AiErrorBoundary>
          <AiCommitComposer
            planId={plan.id}
            existingCommits={commits}
            onSubmit={async (payload) => {
              await handleCreateCommit(payload);
              setShowAiComposer(false);
            }}
            onSwitchToManual={(preFilled) => {
              setShowAiComposer(false);
              setCommitFormInitialValues(preFilled);
              setFormMode("create");
              setEditingCommit(null);
            }}
            onCancel={() => setShowAiComposer(false)}
          />
        </AiErrorBoundary>
      )}

      {/* CommitForm modals */}
      {formMode === "create" && plan && !isLocked && (
        <CommitForm
          mode="create"
          planId={plan.id}
          rcdoTree={rcdoTree ?? []}
          existingCommits={commits}
          onSubmit={handleCreateCommit}
          onCancel={handleFormCancel}
          initialValues={commitFormInitialValues}
        />
      )}
      {formMode === "edit" && plan && editingCommit && (
        <CommitForm mode="edit" commit={editingCommit} rcdoTree={rcdoTree ?? []} existingCommits={commits} onSubmit={handleUpdateCommit} onCancel={handleFormCancel} />
      )}
      {scopeChangeMode === "add" && !pendingCreatePayload && plan && (
        <CommitForm mode="create" planId={plan.id} rcdoTree={rcdoTree ?? []} existingCommits={commits} onSubmit={async (payload) => { setPendingCreatePayload(payload); }} onCancel={handleScopeChangeCancel} />
      )}

      {/* Delete confirm */}
      {deleteTarget && !isLocked && (
        <DeleteConfirmDialog title={deleteTarget.title} onConfirm={handleDeleteConfirm} onCancel={() => setDeleteTarget(null)} />
      )}

      {/* Ticket form */}
      {ticketFormVisible && (
        <TicketForm mode="create" initialValues={ticketFormInitialValues} currentUserId={currentUserId} {...(bridge.context.currentTeam ? { currentTeamId: bridge.context.currentTeam.id } : {})} rcdoTree={rcdoTree ?? []} onSubmit={handleTicketFormSubmit} onCancel={() => { setTicketFormVisible(false); setTicketFormInitialValues({}); }} />
      )}

      {/* Lock confirm */}
      {showLockConfirm && plan && (
        <LockConfirmDialog commits={commits} capacityBudgetPoints={plan.capacityBudgetPoints} weekLabel={formatWeekLabel(weekStartDate)} onConfirm={() => void handleLock()} onCancel={() => setShowLockConfirm(false)} isLocking={lockLoading} />
      )}

      {/* Scope change dialogs */}
      {scopeChangeMode === "add" && pendingCreatePayload && (
        <ScopeChangeDialog action="ADD" commit={null} newCommitTitle={pendingCreatePayload.title} onConfirm={(reason) => void handleScopeChangeConfirm(reason)} onCancel={handleScopeChangeCancel} isSubmitting={scopeChangeSaving} />
      )}
      {scopeChangeMode === "edit" && scopeChangeTargetCommit && pendingEditPayload && (
        <ScopeChangeDialog action="EDIT" commit={scopeChangeTargetCommit} proposedChanges={pendingEditPayload as Partial<CommitResponse>} onConfirm={(reason) => void handleScopeChangeConfirm(reason)} onCancel={handleScopeChangeCancel} isSubmitting={scopeChangeSaving} />
      )}
      {scopeChangeMode === "remove" && scopeChangeTargetCommit && (
        <ScopeChangeDialog action="REMOVE" commit={scopeChangeTargetCommit} onConfirm={(reason) => void handleScopeChangeConfirm(reason)} onCancel={handleScopeChangeCancel} isSubmitting={scopeChangeSaving} />
      )}

      {/* Plan History */}
      <Card>
        <CardHeader>
          <CardTitle>Plan History</CardTitle>
          <Button variant="secondary" size="sm" type="button" onClick={() => setShowHistory((s) => !s)} data-testid="toggle-plan-history-btn">
            {showHistory ? "Hide" : "Show history"}
          </Button>
        </CardHeader>
        {showHistory && (
          <CardContent>
            <PlanHistoryView entries={planHistory ?? []} loading={historyLoading} />
          </CardContent>
        )}
      </Card>

      {/* Carry-forward lineage */}
      {lineageCommitId && (
        <Card data-testid="cf-lineage-section">
          <CardHeader>
            <CardTitle>Carry-forward Lineage</CardTitle>
            <Button variant="ghost" size="icon" type="button" onClick={() => setLineageCommitId(null)} aria-label="Close lineage" className="h-7 w-7">
              <X className="h-4 w-4" />
            </Button>
          </CardHeader>
          <CardContent>
            {lineage && <CarryForwardLineageView lineage={lineage} loading={lineageLoading} />}
            {lineageLoading && !lineage && (
              <div data-testid="cf-lineage-loading" role="status" className="text-sm text-muted">Loading lineage…</div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
