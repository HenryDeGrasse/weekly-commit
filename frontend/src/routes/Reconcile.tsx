/**
 * ReconcilePage — post-lock outcome recording and scope-change review.
 * Route: /weekly/reconcile/:planId?
 *
 * Features:
 *   - Two-column comparison: baseline snapshot (left) vs current (right).
 *   - Outcome selector per commit.
 *   - Outcome notes (required for PARTIALLY_ACHIEVED, NOT_ACHIEVED, CANCELED).
 *   - Auto-achieve indicator for linked DONE tickets.
 *   - Carry-forward checkbox + dialog for NOT_ACHIEVED / PARTIALLY_ACHIEVED.
 *   - Scope change summary section.
 *   - Submit reconciliation button (validates completeness).
 *   - Submit confirmation summary dialog.
 */
import { useState, useCallback, useMemo } from "react";
import { useParams } from "react-router-dom";
import { usePlanApi } from "../api/planHooks.js";
import { useQuery } from "../api/hooks.js";
import type {
  ReconciliationViewResponse,
  ReconcileCommitView,
  CommitOutcome,
  ChessPiece,
  CarryForwardReason,
} from "../api/planTypes.js";
import { OutcomeSelector } from "../components/reconcile/OutcomeSelector.js";
import { CarryForwardDialog } from "../components/reconcile/CarryForwardDialog.js";
import { ScopeChangeTimeline } from "../components/lock/ScopeChangeTimeline.js";

// ── Chess piece display ───────────────────────────────────────────────────────

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔",
  QUEEN: "♕",
  ROOK: "♖",
  BISHOP: "♗",
  KNIGHT: "♘",
  PAWN: "♙",
};

// ── Outcome notes required for ────────────────────────────────────────────────

const NOTES_REQUIRED: Set<CommitOutcome> = new Set([
  "PARTIALLY_ACHIEVED",
  "NOT_ACHIEVED",
  "CANCELED",
]);

const CARRY_FORWARD_ELIGIBLE: Set<CommitOutcome> = new Set([
  "NOT_ACHIEVED",
  "PARTIALLY_ACHIEVED",
]);

// ── useReconcileView hook ─────────────────────────────────────────────────────

function useReconcileView(planId: string | undefined) {
  const api = usePlanApi();
  return useQuery<ReconciliationViewResponse>(
    `reconcile-${planId ?? "none"}`,
    () => {
      if (!planId) return Promise.reject(new Error("No plan ID"));
      return api.getReconciliationView(planId);
    },
  );
}

// ── BaselineColumn ────────────────────────────────────────────────────────────

function BaselineColumn({
  commitView,
}: {
  readonly commitView: ReconcileCommitView;
}) {
  const baseline = commitView.baselineSnapshot;

  if (commitView.addedPostLock) {
    return (
      <div
        data-testid={`baseline-col-${commitView.commitId}`}
        style={{
          padding: "0.625rem",
          background: "#f0fdf4",
          borderRadius: "var(--border-radius)",
          fontSize: "0.8rem",
          color: "#15803d",
          fontStyle: "italic",
        }}
      >
        Added post-lock — no baseline
      </div>
    );
  }

  if (!baseline) {
    return (
      <div
        data-testid={`baseline-col-${commitView.commitId}`}
        style={{
          padding: "0.625rem",
          color: "var(--color-text-muted)",
          fontSize: "0.8rem",
          fontStyle: "italic",
        }}
      >
        Baseline not available
      </div>
    );
  }

  const baselineChessPiece = baseline.chessPiece as ChessPiece | null;
  const baselineTitle = baseline.title as string | null;
  const baselineEstimate = baseline.estimatePoints as number | null;
  const baselineRcdo = baseline.rcdoNodeId as string | null;

  // Detect changes for highlighting
  const titleChanged =
    baselineTitle != null && baselineTitle !== commitView.currentTitle;
  const pieceChanged =
    baselineChessPiece != null &&
    baselineChessPiece !== commitView.currentChessPiece;
  const estimateChanged =
    baselineEstimate != null &&
    baselineEstimate !== commitView.currentEstimatePoints;

  return (
    <div
      data-testid={`baseline-col-${commitView.commitId}`}
      style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}
    >
      {/* Title */}
      <div
        style={{
          fontSize: "0.875rem",
          fontWeight: 600,
          color: titleChanged ? "#991b1b" : "var(--color-text)",
          background: titleChanged ? "#fef2f2" : "transparent",
          borderRadius: "3px",
          padding: titleChanged ? "2px 4px" : 0,
        }}
        data-testid={`baseline-title-${commitView.commitId}`}
      >
        {baselineTitle ?? "—"}
      </div>

      {/* Chess piece */}
      {baselineChessPiece && (
        <div
          style={{
            fontSize: "0.8rem",
            color: pieceChanged ? "#991b1b" : "var(--color-text-muted)",
            background: pieceChanged ? "#fef2f2" : "transparent",
            borderRadius: "3px",
            padding: pieceChanged ? "1px 4px" : 0,
          }}
          data-testid={`baseline-piece-${commitView.commitId}`}
        >
          {CHESS_PIECE_ICONS[baselineChessPiece]} {baselineChessPiece}
        </div>
      )}

      {/* Estimate */}
      {baselineEstimate != null && (
        <div
          style={{
            fontSize: "0.8rem",
            color: estimateChanged ? "#991b1b" : "var(--color-text-muted)",
            background: estimateChanged ? "#fef2f2" : "transparent",
            borderRadius: "3px",
            padding: estimateChanged ? "1px 4px" : 0,
          }}
          data-testid={`baseline-estimate-${commitView.commitId}`}
        >
          {baselineEstimate} pts
        </div>
      )}

      {/* RCDO */}
      {baselineRcdo && (
        <div
          style={{
            fontSize: "0.75rem",
            color: "var(--color-text-muted)",
            fontFamily: "monospace",
          }}
          data-testid={`baseline-rcdo-${commitView.commitId}`}
        >
          🎯 {baselineRcdo.slice(0, 8)}…
        </div>
      )}
    </div>
  );
}

// ── CurrentColumn ─────────────────────────────────────────────────────────────

function CurrentColumn({
  commitView,
}: {
  readonly commitView: ReconcileCommitView;
}) {
  const baseline = commitView.baselineSnapshot;

  const titleChanged =
    baseline != null &&
    baseline.title != null &&
    baseline.title !== commitView.currentTitle;
  const pieceChanged =
    baseline != null &&
    baseline.chessPiece != null &&
    baseline.chessPiece !== commitView.currentChessPiece;
  const estimateChanged =
    baseline != null &&
    baseline.estimatePoints != null &&
    baseline.estimatePoints !== commitView.currentEstimatePoints;

  return (
    <div
      data-testid={`current-col-${commitView.commitId}`}
      style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}
    >
      {/* Title */}
      <div
        style={{
          fontSize: "0.875rem",
          fontWeight: 600,
          color: titleChanged ? "#15803d" : "var(--color-text)",
          background: titleChanged ? "#f0fdf4" : "transparent",
          borderRadius: "3px",
          padding: titleChanged ? "2px 4px" : 0,
        }}
        data-testid={`current-title-${commitView.commitId}`}
      >
        {commitView.currentTitle}
      </div>

      {/* Chess piece */}
      <div
        style={{
          fontSize: "0.8rem",
          color: pieceChanged ? "#15803d" : "var(--color-text-muted)",
          background: pieceChanged ? "#f0fdf4" : "transparent",
          borderRadius: "3px",
          padding: pieceChanged ? "1px 4px" : 0,
        }}
        data-testid={`current-piece-${commitView.commitId}`}
      >
        {CHESS_PIECE_ICONS[commitView.currentChessPiece]}{" "}
        {commitView.currentChessPiece}
      </div>

      {/* Estimate */}
      {commitView.currentEstimatePoints != null && (
        <div
          style={{
            fontSize: "0.8rem",
            color: estimateChanged ? "#15803d" : "var(--color-text-muted)",
            background: estimateChanged ? "#f0fdf4" : "transparent",
            borderRadius: "3px",
            padding: estimateChanged ? "1px 4px" : 0,
          }}
          data-testid={`current-estimate-${commitView.commitId}`}
        >
          {commitView.currentEstimatePoints} pts
        </div>
      )}

      {/* Linked ticket status */}
      {commitView.linkedTicketStatus && (
        <div
          data-testid={`ticket-status-${commitView.commitId}`}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "0.25rem",
            padding: "2px 8px",
            borderRadius: "999px",
            fontSize: "0.75rem",
            fontWeight: 600,
            background:
              commitView.linkedTicketStatus === "DONE" ? "#d1fae5" : "#e0e7ff",
            color:
              commitView.linkedTicketStatus === "DONE" ? "#065f46" : "#3730a3",
          }}
        >
          🎫 {commitView.linkedTicketStatus}
        </div>
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

function ReconcileCommitRow({
  commitView,
  outcome,
  notes,
  carryForward,
  notesError,
  isReadOnly,
  onOutcomeChange,
  onNotesChange,
  onCarryForwardChange,
}: ReconcileCommitRowProps) {
  const needsNotes = outcome != null && NOTES_REQUIRED.has(outcome);
  const canCarryForward =
    !isReadOnly &&
    outcome != null &&
    CARRY_FORWARD_ELIGIBLE.has(outcome);
  const isAutoAchieved =
    commitView.linkedTicketStatus === "DONE" &&
    outcome === "ACHIEVED" &&
    commitView.currentOutcome === "ACHIEVED";

  return (
    <div
      data-testid={`reconcile-commit-row-${commitView.commitId}`}
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
        borderRadius: "var(--border-radius)",
        overflow: "hidden",
      }}
    >
      {/* Row header */}
      <div
        style={{
          padding: "0.625rem 0.75rem",
          background: "#f8fafc",
          borderBottom: "1px solid var(--color-border)",
          display: "flex",
          alignItems: "center",
          gap: "0.5rem",
          flexWrap: "wrap",
        }}
      >
        <span style={{ fontWeight: 600, fontSize: "0.875rem", flex: 1 }}>
          {CHESS_PIECE_ICONS[commitView.currentChessPiece]}{" "}
          {commitView.currentTitle}
        </span>
        {commitView.addedPostLock && (
          <span
            data-testid={`added-post-lock-badge-${commitView.commitId}`}
            style={{
              padding: "2px 8px",
              borderRadius: "999px",
              fontSize: "0.7rem",
              fontWeight: 700,
              background: "#dcfce7",
              color: "#166534",
            }}
          >
            + Post-lock
          </span>
        )}
        {commitView.removedPostLock && (
          <span
            data-testid={`removed-post-lock-badge-${commitView.commitId}`}
            style={{
              padding: "2px 8px",
              borderRadius: "999px",
              fontSize: "0.7rem",
              fontWeight: 700,
              background: "#fee2e2",
              color: "#991b1b",
            }}
          >
            − Removed
          </span>
        )}
        {isAutoAchieved && (
          <span
            data-testid={`auto-achieved-badge-${commitView.commitId}`}
            style={{
              padding: "2px 8px",
              borderRadius: "999px",
              fontSize: "0.7rem",
              fontWeight: 700,
              background: "#d1fae5",
              color: "#065f46",
            }}
          >
            ✅ Auto-achieved (ticket Done)
          </span>
        )}
      </div>

      {/* Baseline vs current columns */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: 0,
          borderBottom: "1px solid var(--color-border)",
        }}
      >
        <div
          style={{
            padding: "0.625rem 0.75rem",
            borderRight: "1px solid var(--color-border)",
          }}
        >
          <p
            style={{
              margin: "0 0 0.375rem",
              fontSize: "0.7rem",
              fontWeight: 600,
              color: "var(--color-text-muted)",
              textTransform: "uppercase",
              letterSpacing: "0.05em",
            }}
          >
            Baseline (at lock)
          </p>
          <BaselineColumn commitView={commitView} />
        </div>
        <div style={{ padding: "0.625rem 0.75rem" }}>
          <p
            style={{
              margin: "0 0 0.375rem",
              fontSize: "0.7rem",
              fontWeight: 600,
              color: "var(--color-text-muted)",
              textTransform: "uppercase",
              letterSpacing: "0.05em",
            }}
          >
            Current
          </p>
          <CurrentColumn commitView={commitView} />
        </div>
      </div>

      {/* Outcome selector */}
      <div
        style={{
          padding: "0.75rem",
          borderBottom:
            needsNotes || canCarryForward
              ? "1px solid var(--color-border)"
              : undefined,
        }}
      >
        <OutcomeSelector
          commitId={commitView.commitId}
          value={outcome}
          onChange={onOutcomeChange}
          disabled={isReadOnly}
        />
      </div>

      {/* Notes textarea */}
      {needsNotes && (
        <div
          style={{
            padding: "0.75rem",
            borderBottom: canCarryForward
              ? "1px solid var(--color-border)"
              : undefined,
          }}
        >
          <label
            htmlFor={`outcome-notes-${commitView.commitId}`}
            style={{
              display: "block",
              marginBottom: "0.375rem",
              fontSize: "0.8rem",
              fontWeight: 600,
            }}
          >
            Outcome notes{" "}
            <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>
              *
            </span>
          </label>
          <textarea
            id={`outcome-notes-${commitView.commitId}`}
            value={notes}
            onChange={(e) => onNotesChange(e.target.value)}
            rows={2}
            disabled={isReadOnly}
            data-testid={`outcome-notes-${commitView.commitId}`}
            aria-required="true"
            aria-describedby={
              notesError ? `notes-error-${commitView.commitId}` : undefined
            }
            style={{
              width: "100%",
              padding: "0.5rem",
              border: `1px solid ${notesError ? "var(--color-danger)" : "var(--color-border)"}`,
              borderRadius: "var(--border-radius)",
              fontSize: "0.875rem",
              fontFamily: "inherit",
              boxSizing: "border-box",
              resize: "vertical",
            }}
            placeholder="Explain what happened…"
          />
          {notesError && (
            <span
              id={`notes-error-${commitView.commitId}`}
              role="alert"
              style={{
                display: "block",
                color: "var(--color-danger)",
                fontSize: "0.75rem",
                marginTop: "0.25rem",
              }}
            >
              {notesError}
            </span>
          )}
        </div>
      )}

      {/* Carry-forward checkbox */}
      {canCarryForward && (
        <div style={{ padding: "0.625rem 0.75rem" }}>
          <label
            data-testid={`carry-forward-checkbox-label-${commitView.commitId}`}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.5rem",
              cursor: "pointer",
              fontSize: "0.875rem",
            }}
          >
            <input
              type="checkbox"
              checked={carryForward}
              onChange={(e) => onCarryForwardChange(e.target.checked)}
              data-testid={`carry-forward-checkbox-${commitView.commitId}`}
              style={{ width: "1rem", height: "1rem" }}
            />
            <span>🔁 Carry this commit forward to next week</span>
          </label>
        </div>
      )}
    </div>
  );
}

// ── Submit Confirmation Dialog ────────────────────────────────────────────────

function SubmitConfirmDialog({
  achievedCount,
  partialCount,
  notAchievedCount,
  canceledCount,
  onConfirm,
  onCancel,
  isSubmitting,
}: {
  readonly achievedCount: number;
  readonly partialCount: number;
  readonly notAchievedCount: number;
  readonly canceledCount: number;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
  readonly isSubmitting: boolean;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="reconcile-submit-title"
      data-testid="reconcile-submit-dialog"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1400,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.45)",
      }}
    >
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "1.5rem",
          width: "min(420px, 96vw)",
          boxShadow: "0 8px 32px rgba(0,0,0,0.2)",
        }}
      >
        <h3
          id="reconcile-submit-title"
          style={{ margin: "0 0 0.75rem", fontSize: "1rem" }}
        >
          Submit Reconciliation?
        </h3>
        <p
          style={{
            margin: "0 0 1rem",
            fontSize: "0.875rem",
            color: "var(--color-text-muted)",
          }}
        >
          This will finalise the week and create an immutable snapshot. This
          cannot be undone.
        </p>

        {/* Summary table */}
        <div
          data-testid="reconcile-submit-summary"
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: "0.5rem",
            marginBottom: "1.25rem",
          }}
        >
          {[
            { label: "Achieved", count: achievedCount, color: "#15803d", bg: "#f0fdf4" },
            { label: "Partial", count: partialCount, color: "#92400e", bg: "#fffbeb" },
            { label: "Not Achieved", count: notAchievedCount, color: "#991b1b", bg: "#fef2f2" },
            { label: "Canceled", count: canceledCount, color: "#374151", bg: "#f3f4f6" },
          ].map(({ label, count, color, bg }) => (
            <div
              key={label}
              style={{
                background: bg,
                borderRadius: "var(--border-radius)",
                padding: "0.5rem 0.75rem",
                textAlign: "center",
              }}
            >
              <div style={{ fontSize: "1.25rem", fontWeight: 700, color }}>
                {count}
              </div>
              <div style={{ fontSize: "0.75rem", color }}>
                {label}
              </div>
            </div>
          ))}
        </div>

        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}>
          <button
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
            data-testid="reconcile-submit-cancel"
            style={{
              padding: "0.5rem 1rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              background: "var(--color-surface)",
              cursor: isSubmitting ? "not-allowed" : "pointer",
              fontFamily: "inherit",
              fontSize: "0.875rem",
            }}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isSubmitting}
            data-testid="reconcile-submit-confirm"
            style={{
              padding: "0.5rem 1.25rem",
              border: "none",
              borderRadius: "var(--border-radius)",
              background: "var(--color-primary)",
              color: "#fff",
              cursor: isSubmitting ? "not-allowed" : "pointer",
              fontFamily: "inherit",
              fontSize: "0.875rem",
              fontWeight: 600,
            }}
          >
            {isSubmitting ? "Submitting…" : "Confirm Submission"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── ReconcilePage ─────────────────────────────────────────────────────────────

export default function ReconcilePage() {
  const { planId: planIdParam } = useParams<{ planId?: string }>();

  // Allow linking from MyWeek page via URL param or fall back to a default message
  const planId = planIdParam;

  const planApi = usePlanApi();
  const { data, loading, error, refetch } = useReconcileView(planId);

  // Local state for outcome edits (commitId → outcome/notes)
  const [localOutcomes, setLocalOutcomes] = useState<
    Record<string, CommitOutcome>
  >({});
  const [localNotes, setLocalNotes] = useState<Record<string, string>>({});
  const [notesErrors, setNotesErrors] = useState<Record<string, string>>({});
  const [carryForwardSet, setCarryForwardSet] = useState<Set<string>>(
    new Set(),
  );
  const [carryForwardTarget, setCarryForwardTarget] = useState<string | null>(
    null,
  );

  const [showSubmitDialog, setShowSubmitDialog] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [savingOutcome, setSavingOutcome] = useState<string | null>(null);
  const [carryForwardLoading, setCarryForwardLoading] = useState(false);

  const isReadOnly = data?.plan.state === "RECONCILED";

  // Merge server outcomes into local state on first load
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

  // ── Outcome change handler ─────────────────────────────────────────────

  const handleOutcomeChange = useCallback(
    async (commitId: string, outcome: CommitOutcome) => {
      if (isReadOnly || !data) return;

      setLocalOutcomes((prev) => ({ ...prev, [commitId]: outcome }));
      // Clear notes error if any
      setNotesErrors((prev) => {
        const next = { ...prev };
        delete next[commitId];
        return next;
      });

      // If notes are required for old outcome but not new, clear notes
      if (!NOTES_REQUIRED.has(outcome)) {
        setLocalNotes((prev) => ({ ...prev, [commitId]: "" }));
      }

      // If not eligible for carry-forward, uncheck
      if (!CARRY_FORWARD_ELIGIBLE.has(outcome)) {
        setCarryForwardSet((prev) => {
          const next = new Set(prev);
          next.delete(commitId);
          return next;
        });
      }

      // Optimistically save if notes are not needed
      if (!NOTES_REQUIRED.has(outcome)) {
        setSavingOutcome(commitId);
        try {
          await planApi.setCommitOutcome(data.plan.id, commitId, {
            outcome,
          });
          refetch();
        } catch {
          // Revert on error
          setLocalOutcomes((prev) => {
            const next = { ...prev };
            delete next[commitId];
            return next;
          });
        } finally {
          setSavingOutcome(null);
        }
      }
    },
    [isReadOnly, data, planApi, refetch],
  );

  // ── Notes save on blur ────────────────────────────────────────────────

  const handleNotesSave = useCallback(
    async (commitId: string) => {
      if (isReadOnly || !data) return;
      const outcome = effectiveOutcomes[commitId];
      if (!outcome || !NOTES_REQUIRED.has(outcome)) return;

      const notes = effectiveNotes[commitId] ?? "";
      if (!notes.trim()) {
        setNotesErrors((prev) => ({
          ...prev,
          [commitId]: "Notes are required for this outcome",
        }));
        return;
      }

      setSavingOutcome(commitId);
      try {
        await planApi.setCommitOutcome(data.plan.id, commitId, {
          outcome,
          notes,
        });
        refetch();
        setNotesErrors((prev) => {
          const next = { ...prev };
          delete next[commitId];
          return next;
        });
      } catch {
        // ignore — user can retry on submit
      } finally {
        setSavingOutcome(null);
      }
    },
    [isReadOnly, data, planApi, effectiveOutcomes, effectiveNotes, refetch],
  );

  // ── Submit validation ─────────────────────────────────────────────────

  function validateBeforeSubmit(): boolean {
    if (!data) return false;
    const newErrors: Record<string, string> = {};
    let valid = true;

    for (const cv of data.commits) {
      const outcome = effectiveOutcomes[cv.commitId];
      if (!outcome) {
        newErrors[cv.commitId] = "Outcome is required";
        valid = false;
        continue;
      }
      if (NOTES_REQUIRED.has(outcome)) {
        const notes = effectiveNotes[cv.commitId] ?? "";
        if (!notes.trim()) {
          newErrors[cv.commitId] = "Notes are required for this outcome";
          valid = false;
        }
      }
    }

    setNotesErrors(newErrors);
    return valid;
  }

  // ── Submit handler ────────────────────────────────────────────────────

  const handleSubmit = useCallback(async () => {
    if (!data) return;
    setSubmitLoading(true);
    setSubmitError(null);
    try {
      // First save all unsaved outcomes with notes
      for (const cv of data.commits) {
        const outcome = effectiveOutcomes[cv.commitId];
        if (!outcome) continue;
        const notes = effectiveNotes[cv.commitId] ?? "";
        if (NOTES_REQUIRED.has(outcome) && notes.trim()) {
          await planApi.setCommitOutcome(data.plan.id, cv.commitId, {
            outcome,
            notes,
          });
        }
      }
      await planApi.submitReconciliation(data.plan.id);
      setShowSubmitDialog(false);
      refetch();
    } catch (err) {
      setSubmitError(
        err instanceof Error ? err.message : "Submission failed",
      );
    } finally {
      setSubmitLoading(false);
    }
  }, [data, effectiveOutcomes, effectiveNotes, planApi, refetch]);

  // ── Carry forward ─────────────────────────────────────────────────────

  const handleCarryForwardConfirm = useCallback(
    async (
      targetWeekStart: string,
      reason: CarryForwardReason,
      reasonText?: string,
    ) => {
      if (!data || !carryForwardTarget) return;
      setCarryForwardLoading(true);
      try {
        await planApi.carryForward(data.plan.id, carryForwardTarget, {
          targetWeekStart,
          reason,
          ...(reasonText !== undefined ? { reasonText } : {}),
        });
        setCarryForwardTarget(null);
        setCarryForwardSet((prev) => {
          const next = new Set(prev);
          next.delete(carryForwardTarget);
          return next;
        });
        refetch();
      } catch (err) {
        setSubmitError(
          err instanceof Error ? err.message : "Carry-forward failed",
        );
        setCarryForwardTarget(null);
      } finally {
        setCarryForwardLoading(false);
      }
    },
    [data, carryForwardTarget, planApi, refetch],
  );

  // ── Derived summary ───────────────────────────────────────────────────

  const outcomeSummary = useMemo(() => {
    const counts = { ACHIEVED: 0, PARTIALLY_ACHIEVED: 0, NOT_ACHIEVED: 0, CANCELED: 0 };
    for (const cv of data?.commits ?? []) {
      const o = effectiveOutcomes[cv.commitId];
      if (o) counts[o] = (counts[o] ?? 0) + 1;
    }
    return counts;
  }, [data, effectiveOutcomes]);

  const allScopeEvents = useMemo(
    () => data?.commits.flatMap((cv) => cv.scopeChanges) ?? [],
    [data],
  );

  // ── Render ────────────────────────────────────────────────────────────

  if (!planId) {
    return (
      <div className="route-page" data-testid="page-reconcile">
        <h2>Reconcile</h2>
        <p style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}>
          No plan selected. Navigate to{" "}
          <a href="/weekly/my-week" style={{ color: "var(--color-primary)" }}>
            My Week
          </a>{" "}
          and use the Reconcile link when your plan is LOCKED or RECONCILING.
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="route-page" data-testid="page-reconcile">
        <h2>Reconcile</h2>
        <div
          role="status"
          aria-label="Loading reconciliation view"
          data-testid="reconcile-loading"
          style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}
        >
          Loading…
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="route-page" data-testid="page-reconcile">
        <h2>Reconcile</h2>
        <div
          role="alert"
          data-testid="reconcile-error"
          style={{
            color: "var(--color-danger)",
            background: "#fef2f2",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem",
            fontSize: "0.875rem",
          }}
        >
          Failed to load reconciliation view: {error.message}
        </div>
      </div>
    );
  }

  if (!data) {
    return null;
  }

  const canSubmit =
    !isReadOnly &&
    data.plan.state === "RECONCILING" &&
    data.commits.every(
      (cv) =>
        effectiveOutcomes[cv.commitId] != null &&
        (!NOTES_REQUIRED.has(effectiveOutcomes[cv.commitId]!) ||
          effectiveNotes[cv.commitId]?.trim()),
    );

  const carryForwardCommit = carryForwardTarget
    ? data.commits.find((cv) => cv.commitId === carryForwardTarget)
    : null;

  return (
    <div
      className="route-page"
      data-testid="page-reconcile"
      style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}
    >
      {/* Header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: "0.5rem",
        }}
      >
        <h2 style={{ margin: 0, fontSize: "1.25rem" }}>Reconcile</h2>
        <span
          data-testid="reconcile-plan-state"
          style={{
            padding: "2px 10px",
            borderRadius: "999px",
            fontSize: "0.75rem",
            fontWeight: 700,
            background:
              data.plan.state === "RECONCILED" ? "#d1fae5" : "#fde68a",
            color:
              data.plan.state === "RECONCILED" ? "#065f46" : "#78350f",
          }}
        >
          {data.plan.state}
        </span>
      </div>

      {/* Stats bar */}
      <div
        data-testid="reconcile-stats-bar"
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "0.75rem 1rem",
          display: "flex",
          gap: "1.5rem",
          flexWrap: "wrap",
          fontSize: "0.875rem",
        }}
      >
        <div>
          <span style={{ fontWeight: 700 }}>{data.commitCount}</span>{" "}
          <span style={{ color: "var(--color-text-muted)" }}>commits</span>
        </div>
        <div>
          <span style={{ fontWeight: 700 }}>{data.outcomesSetCount}</span>{" "}
          <span style={{ color: "var(--color-text-muted)" }}>
            outcomes set
          </span>
        </div>
        <div>
          <span style={{ fontWeight: 700 }}>
            {data.baselineTotalPoints} pts
          </span>{" "}
          <span style={{ color: "var(--color-text-muted)" }}>baseline</span>
        </div>
        <div>
          <span style={{ fontWeight: 700 }}>
            {data.currentTotalPoints} pts
          </span>{" "}
          <span style={{ color: "var(--color-text-muted)" }}>current</span>
        </div>
      </div>

      {/* Action error */}
      {submitError && (
        <div
          role="alert"
          data-testid="reconcile-submit-error"
          style={{
            color: "var(--color-danger)",
            background: "#fef2f2",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem",
            fontSize: "0.875rem",
          }}
        >
          {submitError}
        </div>
      )}

      {/* Read-only banner */}
      {isReadOnly && (
        <div
          data-testid="reconcile-readonly-banner"
          style={{
            background: "#f0fdf4",
            border: "1px solid #86efac",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem",
            fontSize: "0.875rem",
            color: "#15803d",
          }}
        >
          ✅ This plan has been fully reconciled and is now read-only.
        </div>
      )}

      {/* Commit rows */}
      <div
        data-testid="reconcile-commit-list"
        style={{ display: "flex", flexDirection: "column", gap: "0.875rem" }}
      >
        {data.commits.map((cv) => {
          const commitId = cv.commitId;
          const outcome = effectiveOutcomes[commitId] ?? null;
          const notes = effectiveNotes[commitId] ?? "";
          const saving = savingOutcome === commitId;

          return (
            <div key={commitId} style={{ position: "relative" }}>
              {saving && (
                <div
                  data-testid={`saving-indicator-${commitId}`}
                  style={{
                    position: "absolute",
                    top: "0.375rem",
                    right: "0.375rem",
                    fontSize: "0.7rem",
                    color: "var(--color-text-muted)",
                    fontStyle: "italic",
                  }}
                >
                  Saving…
                </div>
              )}
              <ReconcileCommitRow
                commitView={cv}
                outcome={outcome}
                notes={notes}
                carryForward={carryForwardSet.has(commitId)}
                notesError={notesErrors[commitId] ?? null}
                isReadOnly={isReadOnly}
                onOutcomeChange={(o) => void handleOutcomeChange(commitId, o)}
                onNotesChange={(n) =>
                  setLocalNotes((prev) => ({ ...prev, [commitId]: n }))
                }
                onCarryForwardChange={(checked) => {
                  if (checked) {
                    setCarryForwardTarget(commitId);
                  }
                  setCarryForwardSet((prev) => {
                    const next = new Set(prev);
                    if (checked) next.add(commitId);
                    else next.delete(commitId);
                    return next;
                  });
                }}
              />
              {/* Notes save-on-blur trigger */}
              {!isReadOnly && outcome && NOTES_REQUIRED.has(outcome) && (
                <button
                  type="button"
                  onClick={() => void handleNotesSave(commitId)}
                  data-testid={`save-notes-${commitId}`}
                  style={{
                    marginTop: "0.25rem",
                    padding: "0.25rem 0.625rem",
                    border: "1px solid var(--color-border)",
                    borderRadius: "var(--border-radius)",
                    background: "var(--color-surface)",
                    cursor: "pointer",
                    fontSize: "0.75rem",
                    fontFamily: "inherit",
                    color: "var(--color-text-muted)",
                  }}
                >
                  Save notes
                </button>
              )}
            </div>
          );
        })}
      </div>

      {/* Scope change summary */}
      {allScopeEvents.length > 0 && (
        <section aria-labelledby="scope-changes-heading">
          <h3
            id="scope-changes-heading"
            style={{ margin: "0 0 0.625rem", fontSize: "1rem" }}
          >
            Post-lock Scope Changes
          </h3>
          <ScopeChangeTimeline events={allScopeEvents} />
        </section>
      )}

      {/* Submit section */}
      {!isReadOnly && (
        <div
          data-testid="reconcile-submit-section"
          style={{
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            padding: "1rem",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: "0.75rem",
          }}
        >
          <div>
            <p style={{ margin: 0, fontWeight: 600, fontSize: "0.875rem" }}>
              Ready to submit?
            </p>
            <p
              style={{
                margin: "0.25rem 0 0",
                fontSize: "0.8rem",
                color: "var(--color-text-muted)",
              }}
            >
              {data.outcomesSetCount} / {data.commitCount} outcomes set.
              All commits must have outcomes before submitting.
            </p>
          </div>
          <button
            type="button"
            disabled={!canSubmit}
            onClick={() => {
              if (validateBeforeSubmit()) {
                setShowSubmitDialog(true);
              }
            }}
            data-testid="reconcile-submit-btn"
            style={{
              padding: "0.625rem 1.5rem",
              border: "none",
              borderRadius: "var(--border-radius)",
              background: canSubmit
                ? "var(--color-primary)"
                : "var(--color-border)",
              color: canSubmit ? "#fff" : "var(--color-text-muted)",
              cursor: canSubmit ? "pointer" : "not-allowed",
              fontFamily: "inherit",
              fontSize: "0.875rem",
              fontWeight: 600,
            }}
          >
            Submit Reconciliation
          </button>
        </div>
      )}

      {/* Submit confirmation dialog */}
      {showSubmitDialog && (
        <SubmitConfirmDialog
          achievedCount={outcomeSummary.ACHIEVED}
          partialCount={outcomeSummary.PARTIALLY_ACHIEVED}
          notAchievedCount={outcomeSummary.NOT_ACHIEVED}
          canceledCount={outcomeSummary.CANCELED}
          onConfirm={() => void handleSubmit()}
          onCancel={() => setShowSubmitDialog(false)}
          isSubmitting={submitLoading}
        />
      )}

      {/* Carry-forward dialog */}
      {carryForwardTarget && carryForwardCommit && (
        <CarryForwardDialog
          commit={{
            id: carryForwardCommit.commitId,
            planId: data.plan.id,
            ownerUserId: data.plan.ownerUserId,
            title: carryForwardCommit.currentTitle,
            chessPiece: carryForwardCommit.currentChessPiece,
            priorityOrder: 1,
            carryForwardStreak: 0,
            createdAt: "",
            updatedAt: "",
          }}
          onConfirm={(targetWeekStart, reason, reasonText) =>
            void handleCarryForwardConfirm(targetWeekStart, reason, reasonText)
          }
          onCancel={() => {
            setCarryForwardTarget(null);
            setCarryForwardSet((prev) => {
              const next = new Set(prev);
              next.delete(carryForwardTarget);
              return next;
            });
          }}
          isSubmitting={carryForwardLoading}
        />
      )}
    </div>
  );
}
