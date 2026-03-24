/**
 * ScopeChangeDialog — captures reason for a post-lock scope change.
 *
 * Supports three actions:
 *   - ADD: show new commit details (informational).
 *   - REMOVE: show commit being removed + require reason.
 *   - EDIT: show before/after comparison + require reason.
 *
 * Reason category comes from the ScopeChangeCategory enum values relevant
 * for user-facing reasons (excluding system-generated categories).
 */
import { useState, type FormEvent } from "react";
import type {
  CommitResponse,
  ScopeChangeAction,
  ChessPiece,
  EstimatePoints,
} from "../../api/planTypes.js";

// ── Reason display labels ─────────────────────────────────────────────────────

const REASON_OPTIONS: { value: string; label: string }[] = [
  { value: "Priority shift — more important work emerged", label: "Priority shift — more important work emerged" },
  { value: "Scope expanded — original estimate was too small", label: "Scope expanded — original estimate was too small" },
  { value: "Blocked — external dependency", label: "Blocked — external dependency" },
  { value: "Resource unavailable", label: "Resource unavailable" },
  { value: "Technical obstacle discovered", label: "Technical obstacle discovered" },
  { value: "Business context changed", label: "Business context changed" },
  { value: "Error correction — wrong commit included at lock", label: "Error correction — wrong commit included at lock" },
  { value: "Other", label: "Other" },
];

const CHESS_PIECE_LABELS: Record<ChessPiece, string> = {
  KING: "♔ King",
  QUEEN: "♕ Queen",
  ROOK: "♖ Rook",
  BISHOP: "♗ Bishop",
  KNIGHT: "♘ Knight",
  PAWN: "♙ Pawn",
};

// ── Changed field display ─────────────────────────────────────────────────────

interface FieldChange {
  readonly field: string;
  readonly before: string;
  readonly after: string;
}

function computeChanges(
  before: CommitResponse,
  after: Partial<CommitResponse>,
): FieldChange[] {
  const changes: FieldChange[] = [];

  if (after.title !== undefined && after.title !== before.title) {
    changes.push({ field: "Title", before: before.title, after: after.title });
  }
  if (after.chessPiece !== undefined && after.chessPiece !== before.chessPiece) {
    changes.push({
      field: "Chess Piece",
      before: CHESS_PIECE_LABELS[before.chessPiece],
      after: CHESS_PIECE_LABELS[after.chessPiece as ChessPiece],
    });
  }
  if (after.estimatePoints !== undefined && after.estimatePoints !== before.estimatePoints) {
    changes.push({
      field: "Estimate",
      before: before.estimatePoints != null ? `${before.estimatePoints} pts` : "—",
      after: `${after.estimatePoints} pts`,
    });
  }
  if (after.rcdoNodeId !== undefined && after.rcdoNodeId !== before.rcdoNodeId) {
    changes.push({
      field: "RCDO",
      before: before.rcdoNodeId ?? "—",
      after: (after.rcdoNodeId as string) ?? "—",
    });
  }
  if (after.description !== undefined && after.description !== before.description) {
    changes.push({
      field: "Description",
      before: before.description ?? "—",
      after: (after.description as string) ?? "—",
    });
  }

  return changes;
}

// ── Props ─────────────────────────────────────────────────────────────────────

export interface ScopeChangeDialogProps {
  readonly action: ScopeChangeAction;
  /** The commit being modified (REMOVE / EDIT) or null for ADD. */
  readonly commit: CommitResponse | null;
  /** For EDIT: the proposed new values. */
  readonly proposedChanges?: Partial<CommitResponse>;
  /** For ADD: title of the new commit being added. */
  readonly newCommitTitle?: string;
  readonly onConfirm: (reason: string) => void;
  readonly onCancel: () => void;
  readonly isSubmitting?: boolean;
}

// ── ScopeChangeDialog ─────────────────────────────────────────────────────────

export function ScopeChangeDialog({
  action,
  commit,
  proposedChanges,
  newCommitTitle,
  onConfirm,
  onCancel,
  isSubmitting = false,
}: ScopeChangeDialogProps) {
  const [selectedReason, setSelectedReason] = useState("");
  const [reasonText, setReasonText] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);

  const fieldChanges =
    action === "EDIT" && commit && proposedChanges
      ? computeChanges(commit, proposedChanges)
      : [];

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const reason = selectedReason === "Other" ? reasonText.trim() : selectedReason;
    if (!reason) {
      setReasonError("Please select or enter a reason");
      return;
    }
    setReasonError(null);
    onConfirm(reason);
  }

  const actionLabels: Record<ScopeChangeAction, string> = {
    ADD: "Add Commit (Post-lock)",
    REMOVE: "Remove Commit (Post-lock)",
    EDIT: "Edit Commit (Post-lock)",
  };

  const actionColors: Record<ScopeChangeAction, string> = {
    ADD: "#15803d",
    REMOVE: "#991b1b",
    EDIT: "#1e40af",
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="scope-change-dialog-title"
      data-testid="scope-change-dialog"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1200,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.4)",
      }}
    >
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "1.5rem",
          width: "min(520px, 96vw)",
          maxHeight: "90vh",
          overflowY: "auto",
          boxShadow: "0 8px 32px rgba(0,0,0,0.2)",
        }}
      >
        {/* Header */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            marginBottom: "1rem",
          }}
        >
          <h3
            id="scope-change-dialog-title"
            style={{
              margin: 0,
              fontSize: "1rem",
              color: actionColors[action],
            }}
          >
            {actionLabels[action]}
          </h3>
          <button
            type="button"
            onClick={onCancel}
            aria-label="Close"
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              fontSize: "1.125rem",
              color: "var(--color-text-muted)",
            }}
          >
            ✕
          </button>
        </div>

        {/* Locked-plan notice */}
        <div
          data-testid="scope-change-locked-notice"
          style={{
            background: "#eff6ff",
            border: "1px solid #bfdbfe",
            borderRadius: "var(--border-radius)",
            padding: "0.625rem 0.75rem",
            marginBottom: "1rem",
            fontSize: "0.8rem",
            color: "#1e40af",
          }}
        >
          🔒 This plan is <strong>locked</strong>. This change will be recorded
          in the scope-change timeline with your reason.
        </div>

        {/* What's changing */}
        {action === "ADD" && newCommitTitle && (
          <div
            data-testid="scope-change-add-preview"
            style={{
              background: "#f0fdf4",
              border: "1px solid #86efac",
              borderRadius: "var(--border-radius)",
              padding: "0.625rem 0.75rem",
              marginBottom: "1rem",
              fontSize: "0.875rem",
            }}
          >
            <span style={{ fontWeight: 600, color: "#15803d" }}>+ Adding:</span>{" "}
            {newCommitTitle}
          </div>
        )}

        {action === "REMOVE" && commit && (
          <div
            data-testid="scope-change-remove-preview"
            style={{
              background: "#fef2f2",
              border: "1px solid #fecaca",
              borderRadius: "var(--border-radius)",
              padding: "0.625rem 0.75rem",
              marginBottom: "1rem",
              fontSize: "0.875rem",
            }}
          >
            <span style={{ fontWeight: 600, color: "#991b1b" }}>
              − Removing:
            </span>{" "}
            {commit.title}
          </div>
        )}

        {action === "EDIT" && fieldChanges.length > 0 && (
          <div
            data-testid="scope-change-edit-preview"
            style={{
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              overflow: "hidden",
              marginBottom: "1rem",
            }}
          >
            <div
              style={{
                padding: "0.5rem 0.75rem",
                background: "#f8fafc",
                fontSize: "0.75rem",
                fontWeight: 600,
                color: "var(--color-text-muted)",
                textTransform: "uppercase",
                letterSpacing: "0.05em",
              }}
            >
              Changes to: {commit?.title}
            </div>
            <table
              style={{ width: "100%", borderCollapse: "collapse" }}
              aria-label="Commit field changes"
            >
              <thead>
                <tr>
                  <th
                    style={{
                      padding: "0.375rem 0.625rem",
                      textAlign: "left",
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      color: "var(--color-text-muted)",
                      borderBottom: "1px solid var(--color-border)",
                    }}
                  >
                    Field
                  </th>
                  <th
                    style={{
                      padding: "0.375rem 0.625rem",
                      textAlign: "left",
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      color: "#991b1b",
                      borderBottom: "1px solid var(--color-border)",
                    }}
                  >
                    Before
                  </th>
                  <th
                    style={{
                      padding: "0.375rem 0.625rem",
                      textAlign: "left",
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      color: "#15803d",
                      borderBottom: "1px solid var(--color-border)",
                    }}
                  >
                    After
                  </th>
                </tr>
              </thead>
              <tbody>
                {fieldChanges.map((change) => (
                  <tr key={change.field} data-testid={`scope-change-field-row`}>
                    <td
                      style={{
                        padding: "0.375rem 0.625rem",
                        fontSize: "0.8rem",
                        fontWeight: 600,
                        borderBottom: "1px solid var(--color-border)",
                      }}
                    >
                      {change.field}
                    </td>
                    <td
                      style={{
                        padding: "0.375rem 0.625rem",
                        fontSize: "0.8rem",
                        color: "#991b1b",
                        background: "#fef2f2",
                        borderBottom: "1px solid var(--color-border)",
                      }}
                    >
                      {change.before}
                    </td>
                    <td
                      style={{
                        padding: "0.375rem 0.625rem",
                        fontSize: "0.8rem",
                        color: "#15803d",
                        background: "#f0fdf4",
                        borderBottom: "1px solid var(--color-border)",
                      }}
                    >
                      {change.after}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Reason form */}
        <form onSubmit={handleSubmit} noValidate>
          <div style={{ marginBottom: "1rem" }}>
            <label
              htmlFor="scope-change-reason-select"
              style={{
                display: "block",
                marginBottom: "0.375rem",
                fontWeight: 600,
                fontSize: "0.875rem",
              }}
            >
              Reason for change{" "}
              <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>
                *
              </span>
            </label>
            <select
              id="scope-change-reason-select"
              value={selectedReason}
              onChange={(e) => {
                setSelectedReason(e.target.value);
                setReasonError(null);
              }}
              aria-required="true"
              aria-describedby={reasonError ? "scope-reason-error" : undefined}
              data-testid="scope-change-reason-select"
              style={{
                width: "100%",
                padding: "0.5rem",
                border: `1px solid ${reasonError ? "var(--color-danger)" : "var(--color-border)"}`,
                borderRadius: "var(--border-radius)",
                fontSize: "inherit",
                fontFamily: "inherit",
                background: "var(--color-surface)",
                color: "var(--color-text)",
              }}
            >
              <option value="">— Select a reason —</option>
              {REASON_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
            {reasonError && (
              <span
                id="scope-reason-error"
                role="alert"
                style={{
                  display: "block",
                  color: "var(--color-danger)",
                  fontSize: "0.75rem",
                  marginTop: "0.25rem",
                }}
              >
                {reasonError}
              </span>
            )}
          </div>

          {/* Free-text for "Other" */}
          {selectedReason === "Other" && (
            <div style={{ marginBottom: "1rem" }}>
              <label
                htmlFor="scope-change-reason-text"
                style={{
                  display: "block",
                  marginBottom: "0.375rem",
                  fontWeight: 600,
                  fontSize: "0.875rem",
                }}
              >
                Describe the reason{" "}
                <span
                  aria-hidden="true"
                  style={{ color: "var(--color-danger)" }}
                >
                  *
                </span>
              </label>
              <textarea
                id="scope-change-reason-text"
                value={reasonText}
                onChange={(e) => setReasonText(e.target.value)}
                rows={3}
                data-testid="scope-change-reason-text"
                style={{
                  width: "100%",
                  padding: "0.5rem",
                  border: "1px solid var(--color-border)",
                  borderRadius: "var(--border-radius)",
                  fontSize: "inherit",
                  fontFamily: "inherit",
                  boxSizing: "border-box",
                  resize: "vertical",
                }}
                placeholder="Please describe why this change is needed…"
              />
            </div>
          )}

          {/* Actions */}
          <div
            style={{
              display: "flex",
              gap: "0.5rem",
              justifyContent: "flex-end",
              paddingTop: "0.5rem",
              borderTop: "1px solid var(--color-border)",
            }}
          >
            <button
              type="button"
              onClick={onCancel}
              disabled={isSubmitting}
              data-testid="scope-change-cancel"
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
              type="submit"
              disabled={isSubmitting}
              data-testid="scope-change-confirm"
              style={{
                padding: "0.5rem 1.25rem",
                border: "none",
                borderRadius: "var(--border-radius)",
                background: actionColors[action],
                color: "#fff",
                cursor: isSubmitting ? "not-allowed" : "pointer",
                fontFamily: "inherit",
                fontSize: "0.875rem",
                fontWeight: 600,
              }}
            >
              {isSubmitting ? "Saving…" : "Confirm Change"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// Re-export EstimatePoints to avoid unused import warning
export type { EstimatePoints };
