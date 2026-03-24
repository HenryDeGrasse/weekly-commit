/**
 * My Week — personal weekly plan view.
 * Route: /weekly/my-week
 *
 * Features:
 *   - Auto-selects current week plan (creates one if missing).
 *   - Week selector to navigate between weeks.
 *   - Plan state badge (DRAFT / LOCKED / RECONCILING / RECONCILED).
 *   - Pre-lock validation panel (hard errors + soft warnings).
 *   - Lock confirmation dialog.
 *   - Auto-lock system banner.
 *   - Lock action button (DRAFT state only).
 *   - Commit list with drag-and-drop reorder.
 *   - Post-lock scope change dialog (LOCKED state).
 *   - Scope change timeline (LOCKED state).
 *   - Capacity meter (points vs budget).
 *   - Soft-warnings panel.
 *   - CommitForm modal for adding / editing commits.
 */
import { useState, useCallback, useMemo } from "react";
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
import type {
  CommitResponse,
  PlanState,
  LockValidationError,
  ScopeChangeEventResponse,
  UpdateCommitPayload,
  CreateCommitPayload,
} from "../api/planTypes.js";

// ── Week date helpers ─────────────────────────────────────────────────────────

/** Returns the ISO date (yyyy-MM-dd) for the Monday of the week at `offsetWeeks` from today. */
function getWeekStartDate(offsetWeeks = 0): string {
  const now = new Date();
  const dayOfWeek = now.getDay(); // 0=Sunday
  const daysToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const monday = new Date(now);
  monday.setDate(now.getDate() + daysToMonday + offsetWeeks * 7);
  return monday.toISOString().slice(0, 10);
}

/** Formats a yyyy-MM-dd week-start date as "Week of Mar 24, 2026". */
function formatWeekLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  return `Week of ${d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  })}`;
}

// ── PlanStateBadge ────────────────────────────────────────────────────────────

const STATE_BADGE_STYLES: Record<
  PlanState,
  { background: string; color: string }
> = {
  DRAFT: { background: "#fef3c7", color: "#92400e" },
  LOCKED: { background: "#dbeafe", color: "#1e40af" },
  RECONCILING: { background: "#fde68a", color: "#78350f" },
  RECONCILED: { background: "#d1fae5", color: "#065f46" },
};

function PlanStateBadge({ state }: { readonly state: PlanState }) {
  const { background, color } = STATE_BADGE_STYLES[state];
  return (
    <span
      data-testid="plan-state-badge"
      style={{
        padding: "2px 10px",
        borderRadius: "999px",
        fontSize: "0.75rem",
        fontWeight: 700,
        letterSpacing: "0.03em",
        background,
        color,
      }}
    >
      {state}
    </span>
  );
}

// ── ComplianceBadge ───────────────────────────────────────────────────────────

function ComplianceBadge({ compliant }: { readonly compliant: boolean }) {
  return compliant ? (
    <span
      data-testid="compliance-badge-ok"
      title="Plan meets all lock requirements"
      style={{ fontSize: "0.85rem", color: "var(--color-success)" }}
    >
      ✓ Compliant
    </span>
  ) : (
    <span
      data-testid="compliance-badge-warn"
      title="Plan has unresolved compliance issues"
      style={{ fontSize: "0.85rem", color: "var(--color-warning)" }}
    >
      ⚠ Not compliant
    </span>
  );
}

// ── DeleteConfirmDialog ───────────────────────────────────────────────────────

function DeleteConfirmDialog({
  title,
  onConfirm,
  onCancel,
}: {
  readonly title: string;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Delete commit"
      data-testid="delete-confirm-dialog"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1100,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.35)",
      }}
    >
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "1.5rem",
          width: "min(400px, 92vw)",
          boxShadow: "0 8px 32px rgba(0,0,0,0.18)",
        }}
      >
        <h3 style={{ margin: "0 0 0.75rem", fontSize: "1rem" }}>
          Delete commit?
        </h3>
        <p style={{ margin: "0 0 1.25rem", fontSize: "0.875rem" }}>
          Are you sure you want to delete <strong>"{title}"</strong>? This
          cannot be undone.
        </p>
        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}>
          <button
            type="button"
            onClick={onCancel}
            style={{
              padding: "0.5rem 1rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              background: "var(--color-surface)",
              cursor: "pointer",
              fontFamily: "inherit",
            }}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            data-testid="delete-confirm-btn"
            style={{
              padding: "0.5rem 1rem",
              border: "none",
              borderRadius: "var(--border-radius)",
              background: "var(--color-danger)",
              color: "#fff",
              cursor: "pointer",
              fontFamily: "inherit",
            }}
          >
            Delete
          </button>
        </div>
      </div>
    </div>
  );
}

// ── MyWeek Page ───────────────────────────────────────────────────────────────

export default function MyWeek() {
  // Week navigation state (0 = current week, -1 = previous, +1 = next, etc.)
  const [weekOffset, setWeekOffset] = useState(0);
  const weekStartDate = getWeekStartDate(weekOffset);

  const bridge = useHostBridge();
  const currentUserId = bridge.context.authenticatedUser.id;

  const planApi = usePlanApi();
  const {
    data: planData,
    loading: planLoading,
    error: planError,
    refetch: refetchPlan,
  } = useCurrentPlan(weekStartDate);
  const { data: rcdoTree } = useRcdoTree();

  // Plan history and carry-forward lineage
  const [showHistory, setShowHistory] = useState(false);
  const { data: planHistory, loading: historyLoading } = usePlanHistory(
    showHistory ? currentUserId : null,
  );
  const [lineageCommitId, setLineageCommitId] = useState<string | null>(null);
  const { data: lineage, loading: lineageLoading } = useCarryForwardLineage(lineageCommitId);

  // Form / dialog state
  const ticketApi = useTicketApi();

  // Create-from-commit state
  const [ticketFormVisible, setTicketFormVisible] = useState(false);
  const [ticketFormInitialValues, setTicketFormInitialValues] = useState<
    Partial<CreateTicketPayload>
  >({});

  type FormMode = "create" | "edit" | null;
  const [formMode, setFormMode] = useState<FormMode>(null);
  const [editingCommit, setEditingCommit] = useState<CommitResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CommitResponse | null>(null);

  // In-flight / error state
  const [actionError, setActionError] = useState<string | null>(null);
  const [lockLoading, setLockLoading] = useState(false);

  // Lock flow state
  const [showPreLockValidation, setShowPreLockValidation] = useState(false);
  const [lockValidationErrors, setLockValidationErrors] = useState<LockValidationError[]>([]);
  const [showLockConfirm, setShowLockConfirm] = useState(false);

  // Scope change dialog state (post-lock edits)
  type ScopeChangeMode = "add" | "edit" | "remove" | null;
  const [scopeChangeMode, setScopeChangeMode] = useState<ScopeChangeMode>(null);
  const [scopeChangeTargetCommit, setScopeChangeTargetCommit] = useState<CommitResponse | null>(null);
  const [pendingEditPayload, setPendingEditPayload] = useState<UpdateCommitPayload | null>(null);
  const [pendingCreatePayload, setPendingCreatePayload] = useState<CreateCommitPayload | null>(null);
  const [scopeChangeSaving, setScopeChangeSaving] = useState(false);

  // Scope change timeline
  const [scopeChangeEvents, setScopeChangeEvents] = useState<ScopeChangeEventResponse[]>([]);
  const [showTimeline, setShowTimeline] = useState(false);

  const plan = planData?.plan;
  const commits = useMemo(() => planData?.commits ?? [], [planData]);
  const effectivePreLockErrors = useMemo(
    () => getEffectivePreLockErrors(commits, lockValidationErrors),
    [commits, lockValidationErrors],
  );
  const isDraft = plan?.state === "DRAFT";
  const isLocked = plan?.state === "LOCKED";

  // ── Action handlers ──────────────────────────────────────────────────

  const handleOpenCreate = useCallback(() => {
    if (isLocked) {
      // Post-lock: open scope change dialog for ADD
      setScopeChangeMode("add");
      setScopeChangeTargetCommit(null);
      setPendingCreatePayload(null);
    } else {
      setFormMode("create");
      setEditingCommit(null);
    }
    setActionError(null);
  }, [isLocked]);

  const handleOpenEdit = useCallback((commit: CommitResponse) => {
    if (isLocked) {
      // Will be triggered after CommitForm submits: show scope change dialog
      setFormMode("edit");
      setEditingCommit(commit);
    } else {
      setFormMode("edit");
      setEditingCommit(commit);
    }
    setActionError(null);
  }, [isLocked]);

  const handleFormCancel = useCallback(() => {
    setFormMode(null);
    setEditingCommit(null);
  }, []);

  const handleCreateCommit = useCallback(
    async (payload: CreateCommitPayload) => {
      if (!plan) return;
      if (isLocked) {
        // Post-lock: show scope change dialog with reason capture
        setPendingCreatePayload(payload);
        setScopeChangeMode("add");
        setScopeChangeTargetCommit(null);
        setFormMode(null);
        return;
      }
      // Let the error propagate; CommitForm catches it and shows the message.
      await planApi.createCommit(plan.id, payload);
      refetchPlan();
      setFormMode(null);
    },
    [plan, isLocked, planApi, refetchPlan],
  );

  const handleUpdateCommit = useCallback(
    async (payload: UpdateCommitPayload) => {
      if (!plan || !editingCommit) return;
      if (isLocked) {
        // Post-lock: show scope change dialog
        setPendingEditPayload(payload);
        setScopeChangeMode("edit");
        setScopeChangeTargetCommit(editingCommit);
        setFormMode(null);
        setEditingCommit(null);
        return;
      }
      // Let the error propagate; CommitForm catches it and shows the message.
      await planApi.updateCommit(plan.id, editingCommit.id, payload);
      refetchPlan();
      setFormMode(null);
      setEditingCommit(null);
    },
    [plan, editingCommit, isLocked, planApi, refetchPlan],
  );

  const handleDeleteRequest = useCallback((commitId: string) => {
    const commit = commits.find((c) => c.id === commitId);
    if (!commit) return;
    if (isLocked) {
      // Post-lock: show scope change REMOVE dialog
      setScopeChangeMode("remove");
      setScopeChangeTargetCommit(commit);
    } else {
      setDeleteTarget(commit);
    }
  }, [commits, isLocked]);

  const handleDeleteConfirm = useCallback(async () => {
    if (!plan || !deleteTarget) return;
    try {
      await planApi.deleteCommit(plan.id, deleteTarget.id);
      refetchPlan();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Delete failed");
    } finally {
      setDeleteTarget(null);
    }
  }, [plan, deleteTarget, planApi, refetchPlan]);

  const handleReorder = useCallback(
    async (orderedIds: string[]) => {
      if (!plan) return;
      try {
        await planApi.reorderCommits(plan.id, orderedIds);
        refetchPlan();
      } catch (err) {
        setActionError(err instanceof Error ? err.message : "Reorder failed");
      }
    },
    [plan, planApi, refetchPlan],
  );

  // ── Create ticket from commit ────────────────────────────────────────

  const handleCreateTicketFromCommit = useCallback(
    (commit: CommitResponse) => {
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
    },
    [currentUserId, bridge],
  );

  const handleTicketFormSubmit = useCallback(
    async (payload: CreateTicketPayload) => {
      await ticketApi.createTicket(payload);
      setTicketFormVisible(false);
      setTicketFormInitialValues({});
    },
    [ticketApi],
  );

  // ── Lock flow ────────────────────────────────────────────────────────

  const handleLockButtonClick = useCallback(() => {
    setShowPreLockValidation(true);
    setLockValidationErrors([]);
    setShowLockConfirm(false);
  }, []);

  const handlePreLockContinue = useCallback(() => {
    setShowLockConfirm(true);
  }, []);

  const handleLock = useCallback(async () => {
    if (!plan) return;
    setLockLoading(true);
    setActionError(null);
    try {
      const response = await planApi.lockPlan(plan.id);
      if (!response.success) {
        // Hard validation failed — show errors in validation panel
        setLockValidationErrors(response.errors ?? []);
        setShowLockConfirm(false);
        return;
      }
      setShowPreLockValidation(false);
      setShowLockConfirm(false);
      setLockValidationErrors([]);
      refetchPlan();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Lock failed");
      setShowLockConfirm(false);
    } finally {
      setLockLoading(false);
    }
  }, [plan, planApi, refetchPlan]);

  // ── Scope change handlers (post-lock) ────────────────────────────────

  const handleScopeChangeConfirm = useCallback(
    async (reason: string) => {
      if (!plan) return;
      setScopeChangeSaving(true);
      setActionError(null);
      try {
        if (scopeChangeMode === "add" && pendingCreatePayload) {
          const result = await planApi.applyScopeChange(plan.id, {
            action: "ADD",
            reason,
            commitData: pendingCreatePayload,
          });
          setScopeChangeEvents(result.events);
        } else if (scopeChangeMode === "remove" && scopeChangeTargetCommit) {
          const result = await planApi.applyScopeChange(plan.id, {
            action: "REMOVE",
            reason,
            commitId: scopeChangeTargetCommit.id,
          });
          setScopeChangeEvents(result.events);
        } else if (scopeChangeMode === "edit" && scopeChangeTargetCommit && pendingEditPayload) {
          const result = await planApi.applyScopeChange(plan.id, {
            action: "EDIT",
            reason,
            commitId: scopeChangeTargetCommit.id,
            changes: pendingEditPayload,
          });
          setScopeChangeEvents(result.events);
        }
        refetchPlan();
      } catch (err) {
        setActionError(
          err instanceof Error ? err.message : "Scope change failed",
        );
      } finally {
        setScopeChangeSaving(false);
        setScopeChangeMode(null);
        setScopeChangeTargetCommit(null);
        setPendingEditPayload(null);
        setPendingCreatePayload(null);
      }
    },
    [
      plan,
      scopeChangeMode,
      pendingCreatePayload,
      pendingEditPayload,
      scopeChangeTargetCommit,
      planApi,
      refetchPlan,
    ],
  );

  const handleScopeChangeCancel = useCallback(() => {
    setScopeChangeMode(null);
    setScopeChangeTargetCommit(null);
    setPendingEditPayload(null);
    setPendingCreatePayload(null);
  }, []);

  // Load scope change timeline when plan becomes LOCKED
  const handleLoadTimeline = useCallback(async () => {
    if (!plan) return;
    try {
      const result = await planApi.getScopeChangeTimeline(plan.id);
      setScopeChangeEvents(result.events);
      setShowTimeline(true);
    } catch {
      // non-critical
    }
  }, [plan, planApi]);

  // ── RCDO label map ───────────────────────────────────────────────────

  function buildRcdoLabels(): Record<string, string> {
    const map: Record<string, string> = {};
    function traverse(nodes: typeof rcdoTree, path: string[] = []) {
      if (!nodes) return;
      for (const n of nodes) {
        const nextPath = [...path, n.title];
        map[n.id] = nextPath.join(" > ");
        traverse(n.children, nextPath);
      }
    }
    traverse(rcdoTree ?? []);
    return map;
  }
  const rcdoLabels = buildRcdoLabels();

  // ── Styles ───────────────────────────────────────────────────────────

  const btnPrimary: React.CSSProperties = {
    padding: "0.5rem 1rem",
    border: "none",
    borderRadius: "var(--border-radius)",
    background: "var(--color-primary)",
    color: "#fff",
    cursor: "pointer",
    fontFamily: "inherit",
    fontSize: "0.875rem",
    fontWeight: 600,
  };

  const btnSecondary: React.CSSProperties = {
    padding: "0.5rem 1rem",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    background: "var(--color-surface)",
    color: "var(--color-text)",
    cursor: "pointer",
    fontFamily: "inherit",
    fontSize: "0.875rem",
  };

  const btnNav: React.CSSProperties = {
    padding: "0.375rem 0.625rem",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    background: "var(--color-surface)",
    cursor: "pointer",
    fontFamily: "inherit",
    fontSize: "0.875rem",
    color: "var(--color-text)",
  };

  // ── Render ──────────────────────────────────────────────────────────

  return (
    <div
      className="route-page"
      data-testid="page-my-week"
      style={{ display: "flex", flexDirection: "column", gap: "1rem" }}
    >
      {/* Page header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: "0.5rem",
        }}
      >
        <h2 style={{ margin: 0, fontSize: "1.25rem" }}>My Week</h2>

        {/* Add commit button — shown in header only for DRAFT state */}
        {isDraft && (
          <button
            style={btnPrimary}
            onClick={handleOpenCreate}
            data-testid="add-commit-btn"
          >
            + Add Commit
          </button>
        )}
      </div>

      {/* Week selector */}
      <div
        data-testid="week-selector"
        style={{
          display: "flex",
          alignItems: "center",
          gap: "0.5rem",
          flexWrap: "wrap",
        }}
      >
        <button
          style={btnNav}
          onClick={() => setWeekOffset((o) => o - 1)}
          aria-label="Previous week"
          data-testid="prev-week-btn"
        >
          ◀
        </button>
        <span
          data-testid="week-label"
          style={{ fontWeight: 600, fontSize: "0.9rem", minWidth: "14rem", textAlign: "center" }}
        >
          {formatWeekLabel(weekStartDate)}
        </span>
        <button
          style={btnNav}
          onClick={() => setWeekOffset((o) => o + 1)}
          aria-label="Next week"
          data-testid="next-week-btn"
        >
          ▶
        </button>
        {weekOffset !== 0 && (
          <button
            style={btnSecondary}
            onClick={() => setWeekOffset(0)}
            data-testid="current-week-btn"
          >
            Today
          </button>
        )}
      </div>

      {/* Loading state */}
      {planLoading && (
        <div
          role="status"
          aria-label="Loading plan"
          data-testid="plan-loading"
          style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}
        >
          Loading plan…
        </div>
      )}

      {/* Error state */}
      {planError && (
        <div
          role="alert"
          data-testid="plan-error"
          style={{
            color: "var(--color-danger)",
            background: "#fef2f2",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem",
            fontSize: "0.875rem",
          }}
        >
          Failed to load plan: {planError.message}
        </div>
      )}

      {/* Plan header */}
      {plan && (
        <div
          data-testid="plan-header"
          style={{
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem 1rem",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: "0.5rem",
          }}
        >
          <div
            style={{ display: "flex", alignItems: "center", gap: "0.625rem" }}
          >
            <PlanStateBadge state={plan.state} />
            <ComplianceBadge compliant={plan.compliant} />
          </div>

          <div
            style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}
          >
            {actionError && (
              <span
                role="alert"
                data-testid="plan-action-error"
                style={{
                  color: "var(--color-danger)",
                  fontSize: "0.8rem",
                }}
              >
                {actionError}
              </span>
            )}

            {/* Lock button — DRAFT only */}
            {isDraft && (
              <button
                style={btnPrimary}
                onClick={handleLockButtonClick}
                disabled={lockLoading}
                data-testid="lock-plan-btn"
              >
                {lockLoading ? "Locking…" : "🔒 Lock Plan"}
              </button>
            )}

            {/* Post-lock Add Commit button — LOCKED state */}
            {isLocked && (
              <button
                style={btnPrimary}
                onClick={handleOpenCreate}
                data-testid="post-lock-add-commit-btn"
              >
                + Add Commit
              </button>
            )}

            {/* Reconcile action hint — LOCKED / RECONCILING */}
            {(plan.state === "LOCKED" || plan.state === "RECONCILING") && (
              <span
                data-testid="reconcile-hint"
                style={{
                  fontSize: "0.85rem",
                  color: "var(--color-text-muted)",
                }}
              >
                Go to{" "}
                <a
                  href={`/weekly/reconcile/${plan.id}`}
                  style={{ color: "var(--color-primary)" }}
                >
                  Reconcile
                </a>{" "}
                to close this week.
              </span>
            )}
          </div>
        </div>
      )}

      {/* Auto-lock system banner */}
      {plan?.systemLockedWithErrors && (
        <div
          data-testid="auto-lock-banner"
          role="alert"
          style={{
            background: "#fffbeb",
            border: "1px solid #fde68a",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem 1rem",
            fontSize: "0.875rem",
            color: "#92400e",
          }}
        >
          <strong>⚠ System-locked with errors</strong> — this plan was
          automatically locked at the deadline. Some validation issues were
          present at lock time. Please review and reconcile.
        </div>
      )}

      {/* Pre-lock validation panel */}
      {isDraft && showPreLockValidation && (
        <div
          data-testid="pre-lock-validation-section"
          style={{
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            padding: "1rem",
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: "0.75rem",
              flexWrap: "wrap",
              gap: "0.5rem",
            }}
          >
            <h3 style={{ margin: 0, fontSize: "0.95rem" }}>
              Pre-lock Validation
            </h3>
            <button
              type="button"
              onClick={() => setShowPreLockValidation(false)}
              style={{
                ...btnSecondary,
                padding: "0.25rem 0.625rem",
                fontSize: "0.8rem",
              }}
            >
              ✕ Cancel
            </button>
          </div>
          <PreLockValidationPanel
            errors={lockValidationErrors}
            commits={commits}
          />
          {/* Only allow continuing if no hard errors */}
          {effectivePreLockErrors.length === 0 && (
            <div
              style={{
                marginTop: "0.875rem",
                display: "flex",
                justifyContent: "flex-end",
              }}
            >
              <button
                style={btnPrimary}
                onClick={handlePreLockContinue}
                data-testid="pre-lock-continue-btn"
              >
                Continue to Lock →
              </button>
            </div>
          )}
        </div>
      )}

      {/* Capacity meter */}
      {plan && (
        <CapacityMeter
          commits={commits}
          budgetPoints={plan.capacityBudgetPoints}
          isManagerOverride={plan.capacityBudgetPoints !== 10}
        />
      )}

      {/* Soft warnings */}
      {commits.length > 0 && <SoftWarningsPanel commits={commits} />}

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

      {/* Scope change timeline (LOCKED state) */}
      {isLocked && scopeChangeEvents.length > 0 && (
        <section aria-labelledby="scope-timeline-heading">
          <h3
            id="scope-timeline-heading"
            style={{ margin: "0 0 0.625rem", fontSize: "0.95rem" }}
          >
            Post-lock Changes
          </h3>
          <ScopeChangeTimeline events={scopeChangeEvents} />
        </section>
      )}

      {/* Load timeline toggle */}
      {isLocked && !showTimeline && (
        <button
          type="button"
          onClick={() => void handleLoadTimeline()}
          data-testid="load-scope-timeline-btn"
          style={{
            ...btnSecondary,
            fontSize: "0.8rem",
            padding: "0.375rem 0.75rem",
            alignSelf: "flex-start",
          }}
        >
          View scope-change history
        </button>
      )}

      {/* CommitForm modal — DRAFT mode */}
      {formMode === "create" && plan && !isLocked && (
        <CommitForm
          mode="create"
          rcdoTree={rcdoTree ?? []}
          existingCommits={commits}
          onSubmit={handleCreateCommit}
          onCancel={handleFormCancel}
        />
      )}
      {formMode === "edit" && plan && editingCommit && (
        <CommitForm
          mode="edit"
          commit={editingCommit}
          rcdoTree={rcdoTree ?? []}
          existingCommits={commits}
          onSubmit={handleUpdateCommit}
          onCancel={handleFormCancel}
        />
      )}

      {/* CommitForm modal — LOCKED mode (add) */}
      {scopeChangeMode === "add" && !pendingCreatePayload && plan && (
        <CommitForm
          mode="create"
          rcdoTree={rcdoTree ?? []}
          existingCommits={commits}
          onSubmit={async (payload) => {
            setPendingCreatePayload(payload);
          }}
          onCancel={handleScopeChangeCancel}
        />
      )}

      {/* Delete confirm dialog — DRAFT only */}
      {deleteTarget && !isLocked && (
        <DeleteConfirmDialog
          title={deleteTarget.title}
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      {/* Create ticket from commit — TicketForm modal */}
      {ticketFormVisible && (
        <TicketForm
          mode="create"
          initialValues={ticketFormInitialValues}
          currentUserId={currentUserId}
          {...(bridge.context.currentTeam ? { currentTeamId: bridge.context.currentTeam.id } : {})}
          onSubmit={handleTicketFormSubmit}
          onCancel={() => {
            setTicketFormVisible(false);
            setTicketFormInitialValues({});
          }}
        />
      )}

      {/* Lock confirmation dialog */}
      {showLockConfirm && plan && (
        <LockConfirmDialog
          commits={commits}
          capacityBudgetPoints={plan.capacityBudgetPoints}
          weekLabel={formatWeekLabel(weekStartDate)}
          onConfirm={() => void handleLock()}
          onCancel={() => setShowLockConfirm(false)}
          isLocking={lockLoading}
        />
      )}

      {/* Scope change dialog — ADD (after CommitForm submitted) */}
      {scopeChangeMode === "add" && pendingCreatePayload && (
        <ScopeChangeDialog
          action="ADD"
          commit={null}
          newCommitTitle={pendingCreatePayload.title}
          onConfirm={(reason) => void handleScopeChangeConfirm(reason)}
          onCancel={handleScopeChangeCancel}
          isSubmitting={scopeChangeSaving}
        />
      )}

      {/* Scope change dialog — EDIT */}
      {scopeChangeMode === "edit" && scopeChangeTargetCommit && pendingEditPayload && (
        <ScopeChangeDialog
          action="EDIT"
          commit={scopeChangeTargetCommit}
          proposedChanges={pendingEditPayload as Partial<CommitResponse>}
          onConfirm={(reason) => void handleScopeChangeConfirm(reason)}
          onCancel={handleScopeChangeCancel}
          isSubmitting={scopeChangeSaving}
        />
      )}

      {/* Scope change dialog — REMOVE */}
      {scopeChangeMode === "remove" && scopeChangeTargetCommit && (
        <ScopeChangeDialog
          action="REMOVE"
          commit={scopeChangeTargetCommit}
          onConfirm={(reason) => void handleScopeChangeConfirm(reason)}
          onCancel={handleScopeChangeCancel}
          isSubmitting={scopeChangeSaving}
        />
      )}

      {/* ── Plan History section ─────────────────────────────────────── */}
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "0.75rem 1rem",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: "0.5rem",
          }}
        >
          <h3 style={{ margin: 0, fontSize: "0.95rem", fontWeight: 700 }}>
            Plan History
          </h3>
          <button
            type="button"
            onClick={() => setShowHistory((s) => !s)}
            data-testid="toggle-plan-history-btn"
            style={{
              ...btnSecondary,
              padding: "0.3rem 0.75rem",
              fontSize: "0.8rem",
            }}
          >
            {showHistory ? "Hide" : "Show history"}
          </button>
        </div>

        {showHistory && (
          <div style={{ marginTop: "0.875rem" }}>
            <PlanHistoryView
              entries={planHistory ?? []}
              loading={historyLoading}
            />
          </div>
        )}
      </div>

      {/* ── Carry-forward lineage (when a commit with CF is selected) ── */}
      {lineageCommitId && (
        <div
          data-testid="cf-lineage-section"
          style={{
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            padding: "0.875rem 1rem",
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: "0.75rem",
              flexWrap: "wrap",
              gap: "0.5rem",
            }}
          >
            <h3 style={{ margin: 0, fontSize: "0.9rem" }}>
              Carry-forward Lineage
            </h3>
            <button
              type="button"
              onClick={() => setLineageCommitId(null)}
              aria-label="Close lineage"
              style={{
                background: "none",
                border: "none",
                cursor: "pointer",
                fontSize: "1rem",
                color: "var(--color-text-muted)",
              }}
            >
              ✕
            </button>
          </div>
          {lineage && (
            <CarryForwardLineageView lineage={lineage} loading={lineageLoading} />
          )}
          {lineageLoading && !lineage && (
            <div
              data-testid="cf-lineage-loading"
              role="status"
              style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}
            >
              Loading lineage…
            </div>
          )}
        </div>
      )}
    </div>
  );
}
