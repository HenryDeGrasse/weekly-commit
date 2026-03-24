/**
 * My Week — personal weekly plan view.
 * Route: /weekly/my-week
 *
 * Features:
 *   - Auto-selects current week plan (creates one if missing).
 *   - Week selector to navigate between weeks.
 *   - Plan state badge (DRAFT / LOCKED / RECONCILING / RECONCILED).
 *   - Lock action button (DRAFT state only).
 *   - Commit list with drag-and-drop reorder.
 *   - Capacity meter (points vs budget).
 *   - Soft-warnings panel.
 *   - CommitForm modal for adding / editing commits.
 */
import { useState, useCallback, useMemo } from "react";
import { useCurrentPlan, usePlanApi } from "../api/planHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import { CommitList } from "../components/myweek/CommitList.js";
import { CommitForm } from "../components/myweek/CommitForm.js";
import { CapacityMeter } from "../components/myweek/CapacityMeter.js";
import { SoftWarningsPanel } from "../components/myweek/SoftWarningsPanel.js";
import type { CommitResponse, PlanState } from "../api/planTypes.js";

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

  const planApi = usePlanApi();
  const {
    data: planData,
    loading: planLoading,
    error: planError,
    refetch: refetchPlan,
  } = useCurrentPlan(weekStartDate);
  const { data: rcdoTree } = useRcdoTree();

  // Form / dialog state
  type FormMode = "create" | "edit" | null;
  const [formMode, setFormMode] = useState<FormMode>(null);
  const [editingCommit, setEditingCommit] = useState<CommitResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CommitResponse | null>(null);

  // In-flight / error state
  const [actionError, setActionError] = useState<string | null>(null);
  const [lockLoading, setLockLoading] = useState(false);

  const plan = planData?.plan;
  const commits = useMemo(() => planData?.commits ?? [], [planData]);
  const isDraft = plan?.state === "DRAFT";

  // ── Action handlers ──────────────────────────────────────────────────

  const handleOpenCreate = useCallback(() => {
    setFormMode("create");
    setEditingCommit(null);
    setActionError(null);
  }, []);

  const handleOpenEdit = useCallback((commit: CommitResponse) => {
    setFormMode("edit");
    setEditingCommit(commit);
    setActionError(null);
  }, []);

  const handleFormCancel = useCallback(() => {
    setFormMode(null);
    setEditingCommit(null);
  }, []);

  const handleCreateCommit = useCallback(
    async (payload: Parameters<typeof planApi.createCommit>[1]) => {
      if (!plan) return;
      // Let the error propagate; CommitForm catches it and shows the message.
      await planApi.createCommit(plan.id, payload);
      refetchPlan();
      setFormMode(null);
    },
    [plan, planApi, refetchPlan],
  );

  const handleUpdateCommit = useCallback(
    async (payload: Parameters<typeof planApi.updateCommit>[2]) => {
      if (!plan || !editingCommit) return;
      // Let the error propagate; CommitForm catches it and shows the message.
      await planApi.updateCommit(plan.id, editingCommit.id, payload);
      refetchPlan();
      setFormMode(null);
      setEditingCommit(null);
    },
    [plan, editingCommit, planApi, refetchPlan],
  );

  const handleDeleteRequest = useCallback((commitId: string) => {
    const commit = commits.find((c) => c.id === commitId);
    if (commit) setDeleteTarget(commit);
  }, [commits]);

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

  const handleLock = useCallback(async () => {
    if (!plan) return;
    setLockLoading(true);
    setActionError(null);
    try {
      await planApi.lockPlan(plan.id);
      refetchPlan();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Lock failed");
    } finally {
      setLockLoading(false);
    }
  }, [plan, planApi, refetchPlan]);

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

        {/* Add commit button */}
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
                onClick={handleLock}
                disabled={lockLoading}
                data-testid="lock-plan-btn"
              >
                {lockLoading ? "Locking…" : "🔒 Lock Plan"}
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
                  href="/weekly/reconcile"
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
        />
      )}

      {/* CommitForm modal */}
      {formMode === "create" && plan && (
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

      {/* Delete confirm dialog */}
      {deleteTarget && (
        <DeleteConfirmDialog
          title={deleteTarget.title}
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
}
