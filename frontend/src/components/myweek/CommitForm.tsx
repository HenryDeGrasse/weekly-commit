/**
 * CommitForm — modal/panel for creating and editing weekly commits.
 *
 * Fields:
 *   - title (required)
 *   - description (textarea)
 *   - chess piece (radio with icons, descriptions, and limit enforcement)
 *   - estimate points (segmented control: 1 / 2 / 3 / 5 / 8)
 *   - primary RCDO link (hierarchical picker via RcdoTreeView)
 *   - success criteria (required for KING / QUEEN)
 *   - linked ticket (search/select text field)
 *
 * Carry-forward: shows a provenance banner when the commit has a streak > 0.
 */
import { useState, type FormEvent } from "react";
import { RcdoTreeView } from "../rcdo/RcdoTreeView.js";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";
import type {
  ChessPiece,
  EstimatePoints,
  CommitResponse,
  CreateCommitPayload,
  UpdateCommitPayload,
} from "../../api/planTypes.js";

// ── Chess piece metadata ──────────────────────────────────────────────────────

const CHESS_PIECES: ChessPiece[] = [
  "KING",
  "QUEEN",
  "ROOK",
  "BISHOP",
  "KNIGHT",
  "PAWN",
];

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔",
  QUEEN: "♕",
  ROOK: "♖",
  BISHOP: "♗",
  KNIGHT: "♘",
  PAWN: "♙",
};

const CHESS_PIECE_LABELS: Record<ChessPiece, string> = {
  KING: "King",
  QUEEN: "Queen",
  ROOK: "Rook",
  BISHOP: "Bishop",
  KNIGHT: "Knight",
  PAWN: "Pawn",
};

const CHESS_PIECE_DESCRIPTIONS: Record<ChessPiece, string> = {
  KING: "Mission-critical — must be done this week",
  QUEEN: "High priority — strong commitment",
  ROOK: "Important — significant contribution",
  BISHOP: "Valuable — worth commitment",
  KNIGHT: "Tactical — opportunistic work",
  PAWN: "Nice-to-have — best-effort",
};

const MAX_KING_PER_WEEK = 1;
const MAX_QUEEN_PER_WEEK = 2;
const VALID_POINTS: EstimatePoints[] = [1, 2, 3, 5, 8];

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Find a node in the tree by id, returns null if not found. */
function findNodeById(
  nodes: RcdoTreeNode[],
  id: string,
): RcdoTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findNodeById(node.children, id);
    if (found) return found;
  }
  return null;
}

function getRcdoSelectionError(node: RcdoTreeNode): string | null {
  if (node.nodeType === "RALLY_CRY") {
    return "Select an Outcome, or a Defining Objective with no active Outcomes";
  }
  if (
    node.nodeType === "DEFINING_OBJECTIVE" &&
    node.children.some(
      (child) => child.nodeType === "OUTCOME" && child.status === "ACTIVE",
    )
  ) {
    return "Select an Outcome, or a Defining Objective with no active Outcomes";
  }
  return null;
}

// ── Form field types ──────────────────────────────────────────────────────────

interface FormValues {
  title: string;
  description: string;
  chessPiece: ChessPiece | "";
  estimatePoints: EstimatePoints | "";
  rcdoNodeId: string;
  successCriteria: string;
  workItemId: string;
}

type FormErrors = Partial<Record<keyof FormValues, string>>;

// ── Props ─────────────────────────────────────────────────────────────────────

interface CreateModeProps {
  readonly mode: "create";
  /** RCDO hierarchy for the picker. */
  readonly rcdoTree: RcdoTreeNode[];
  /** All existing commits in the plan — used for chess-piece limit checks. */
  readonly existingCommits: CommitResponse[];
  readonly onSubmit: (payload: CreateCommitPayload) => Promise<void>;
  readonly onCancel: () => void;
}

interface EditModeProps {
  readonly mode: "edit";
  readonly commit: CommitResponse;
  /** RCDO hierarchy for the picker. */
  readonly rcdoTree: RcdoTreeNode[];
  /** All existing commits in the plan (excluding the one being edited). */
  readonly existingCommits: CommitResponse[];
  readonly onSubmit: (payload: UpdateCommitPayload) => Promise<void>;
  readonly onCancel: () => void;
}

export type CommitFormProps = CreateModeProps | EditModeProps;

// ── CommitForm ────────────────────────────────────────────────────────────────

export function CommitForm(props: CommitFormProps) {
  const { rcdoTree, existingCommits, onCancel } = props;
  const isCreate = props.mode === "create";
  const editCommit = props.mode === "edit" ? props.commit : null;

  // ── State ──────────────────────────────────────────────────────────────

  const [values, setValues] = useState<FormValues>({
    title: editCommit?.title ?? "",
    description: editCommit?.description ?? "",
    chessPiece: (editCommit?.chessPiece ?? "") as ChessPiece | "",
    estimatePoints: (editCommit?.estimatePoints ?? "") as EstimatePoints | "",
    rcdoNodeId: editCommit?.rcdoNodeId ?? "",
    successCriteria: editCommit?.successCriteria ?? "",
    workItemId: editCommit?.workItemId ?? "",
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [showRcdoPicker, setShowRcdoPicker] = useState(false);

  // ── Chess piece limit helpers ──────────────────────────────────────────

  const otherCommits = editCommit
    ? existingCommits.filter((c) => c.id !== editCommit.id)
    : existingCommits;

  const existingKings = otherCommits.filter(
    (c) => c.chessPiece === "KING",
  ).length;
  const existingQueens = otherCommits.filter(
    (c) => c.chessPiece === "QUEEN",
  ).length;

  function isChessPieceDisabled(piece: ChessPiece): boolean {
    if (piece === "KING" && existingKings >= MAX_KING_PER_WEEK) return true;
    if (piece === "QUEEN" && existingQueens >= MAX_QUEEN_PER_WEEK) return true;
    return false;
  }

  function chessPieceLimitMessage(piece: ChessPiece): string | null {
    if (piece === "KING" && existingKings >= MAX_KING_PER_WEEK) {
      return `Max ${MAX_KING_PER_WEEK} King already used`;
    }
    if (piece === "QUEEN" && existingQueens >= MAX_QUEEN_PER_WEEK) {
      return `Max ${MAX_QUEEN_PER_WEEK} Queens already used`;
    }
    return null;
  }

  // ── Field change handlers ──────────────────────────────────────────────

  function handleChange(field: keyof FormValues, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  }

  // ── Validation ─────────────────────────────────────────────────────────

  function validate(): boolean {
    const next: FormErrors = {};

    if (!values.title.trim()) {
      next.title = "Title is required";
    }
    if (!values.chessPiece) {
      next.chessPiece = "Chess piece is required";
    } else if (isChessPieceDisabled(values.chessPiece as ChessPiece)) {
      next.chessPiece = chessPieceLimitMessage(values.chessPiece as ChessPiece) ?? "Piece limit reached";
    }
    if (
      (values.chessPiece === "KING" || values.chessPiece === "QUEEN") &&
      !values.successCriteria.trim()
    ) {
      next.successCriteria = "Success criteria is required for King / Queen";
    }

    if (values.rcdoNodeId) {
      const selectedNode = findNodeById(rcdoTree, values.rcdoNodeId);
      if (selectedNode) {
        const rcdoError = getRcdoSelectionError(selectedNode);
        if (rcdoError) {
          next.rcdoNodeId = rcdoError;
        }
      }
    }

    setErrors(next);
    return Object.keys(next).length === 0;
  }

  // ── Submit ─────────────────────────────────────────────────────────────

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;

    setSubmitting(true);
    setSubmitError(null);

    try {
      if (isCreate) {
        const payload: CreateCommitPayload = {
          title: values.title.trim(),
          chessPiece: values.chessPiece as ChessPiece,
          ...(values.description.trim()
            ? { description: values.description.trim() }
            : {}),
          ...(values.rcdoNodeId ? { rcdoNodeId: values.rcdoNodeId } : {}),
          ...(values.workItemId ? { workItemId: values.workItemId } : {}),
          ...(values.estimatePoints !== ""
            ? {
                estimatePoints: Number(
                  values.estimatePoints,
                ) as EstimatePoints,
              }
            : {}),
          ...(values.successCriteria.trim()
            ? { successCriteria: values.successCriteria.trim() }
            : {}),
        };
        await (props as CreateModeProps).onSubmit(payload);
      } else {
        const payload: UpdateCommitPayload = {
          title: values.title.trim(),
          chessPiece: values.chessPiece as ChessPiece,
          ...(values.description.trim()
            ? { description: values.description.trim() }
            : {}),
          ...(values.rcdoNodeId ? { rcdoNodeId: values.rcdoNodeId } : {}),
          ...(values.workItemId ? { workItemId: values.workItemId } : {}),
          ...(values.estimatePoints !== ""
            ? {
                estimatePoints: Number(
                  values.estimatePoints,
                ) as EstimatePoints,
              }
            : {}),
          ...(values.successCriteria.trim()
            ? { successCriteria: values.successCriteria.trim() }
            : {}),
        };
        await (props as EditModeProps).onSubmit(payload);
      }
    } catch (err) {
      setSubmitError(
        err instanceof Error ? err.message : "An unexpected error occurred",
      );
    } finally {
      setSubmitting(false);
    }
  }

  // ── RCDO picker ────────────────────────────────────────────────────────

  const selectedRcdoNode = values.rcdoNodeId
    ? findNodeById(rcdoTree, values.rcdoNodeId)
    : null;

  // ── Styles ─────────────────────────────────────────────────────────────

  const inputStyle: React.CSSProperties = {
    width: "100%",
    padding: "0.5rem",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    fontSize: "inherit",
    boxSizing: "border-box",
    fontFamily: "inherit",
  };

  const errorInputStyle: React.CSSProperties = {
    ...inputStyle,
    borderColor: "var(--color-danger)",
  };

  const fieldStyle: React.CSSProperties = { marginBottom: "1rem" };

  const labelStyle: React.CSSProperties = {
    display: "block",
    marginBottom: "0.25rem",
    fontWeight: 600,
    fontSize: "0.875rem",
  };

  const errorTextStyle: React.CSSProperties = {
    color: "var(--color-danger)",
    fontSize: "0.75rem",
    marginTop: "0.25rem",
    display: "block",
  };

  const requiredMark = (
    <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>
      {" *"}
    </span>
  );

  // ── Render ─────────────────────────────────────────────────────────────

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={isCreate ? "New commit" : "Edit commit"}
      data-testid="commit-form-modal"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1000,
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
          width: "min(600px, 96vw)",
          maxHeight: "90vh",
          overflowY: "auto",
          boxShadow: "0 8px 32px rgba(0,0,0,0.18)",
        }}
      >
        {/* Carry-forward provenance banner */}
        {editCommit && editCommit.carryForwardStreak > 0 && (
          <div
            data-testid="carry-forward-banner"
            style={{
              background: "#eff6ff",
              border: "1px solid #bfdbfe",
              borderRadius: "var(--border-radius)",
              padding: "0.625rem 0.75rem",
              marginBottom: "1rem",
              fontSize: "0.85rem",
              color: "#1e40af",
            }}
          >
            🔁{" "}
            <strong>Carried forward</strong> — this commit has been carried
            forward {editCommit.carryForwardStreak} time
            {editCommit.carryForwardStreak !== 1 ? "s" : ""}.
          </div>
        )}

        <form
          onSubmit={handleSubmit}
          noValidate
          aria-label={isCreate ? "New commit" : "Edit commit"}
          data-testid="commit-form"
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: "1.25rem",
            }}
          >
            <h3 style={{ margin: 0, fontSize: "1.125rem" }}>
              {isCreate ? "New Commit" : "Edit Commit"}
            </h3>
            <button
              type="button"
              onClick={onCancel}
              aria-label="Close"
              style={{
                background: "none",
                border: "none",
                cursor: "pointer",
                fontSize: "1.25rem",
                color: "var(--color-text-muted)",
                lineHeight: 1,
                padding: "0.25rem",
              }}
            >
              ✕
            </button>
          </div>

          {/* Title */}
          <div style={fieldStyle}>
            <label htmlFor="commit-form-title" style={labelStyle}>
              Title{requiredMark}
            </label>
            <input
              id="commit-form-title"
              type="text"
              value={values.title}
              onChange={(e) => handleChange("title", e.target.value)}
              aria-required="true"
              aria-describedby={
                errors.title ? "commit-form-title-error" : undefined
              }
              style={errors.title ? errorInputStyle : inputStyle}
              placeholder="What will you commit to this week?"
              data-testid="commit-form-title"
            />
            {errors.title && (
              <span
                id="commit-form-title-error"
                role="alert"
                style={errorTextStyle}
              >
                {errors.title}
              </span>
            )}
          </div>

          {/* Description */}
          <div style={fieldStyle}>
            <label htmlFor="commit-form-description" style={labelStyle}>
              Description
            </label>
            <textarea
              id="commit-form-description"
              value={values.description}
              onChange={(e) => handleChange("description", e.target.value)}
              rows={3}
              style={{ ...inputStyle, resize: "vertical" }}
              placeholder="Optional: provide additional context"
              data-testid="commit-form-description"
            />
          </div>

          {/* Chess piece */}
          <div style={fieldStyle}>
            <fieldset
              style={{ border: "none", padding: 0, margin: 0 }}
              aria-describedby={
                errors.chessPiece ? "commit-form-piece-error" : undefined
              }
            >
              <legend style={{ ...labelStyle, display: "flex", gap: "0.25rem" }}>
                Chess Piece{requiredMark}
              </legend>
              <div
                style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}
                data-testid="commit-form-chess-piece-group"
              >
                {CHESS_PIECES.map((piece) => {
                  const disabled = isChessPieceDisabled(piece);
                  const limitMsg = chessPieceLimitMessage(piece);
                  return (
                    <label
                      key={piece}
                      data-testid={`chess-piece-option-${piece.toLowerCase()}`}
                      style={{
                        display: "flex",
                        alignItems: "flex-start",
                        gap: "0.625rem",
                        padding: "0.5rem",
                        border: `1px solid ${
                          values.chessPiece === piece
                            ? "var(--color-primary)"
                            : "var(--color-border)"
                        }`,
                        borderRadius: "var(--border-radius)",
                        cursor: disabled ? "not-allowed" : "pointer",
                        background:
                          values.chessPiece === piece
                            ? "#eff6ff"
                            : "transparent",
                        opacity: disabled ? 0.55 : 1,
                      }}
                    >
                      <input
                        type="radio"
                        name="chessPiece"
                        value={piece}
                        checked={values.chessPiece === piece}
                        onChange={() => {
                          if (!disabled) handleChange("chessPiece", piece);
                        }}
                        disabled={disabled}
                        style={{ marginTop: "2px", flexShrink: 0 }}
                      />
                      <span
                        aria-hidden="true"
                        style={{ fontSize: "1.25rem", lineHeight: 1, flexShrink: 0 }}
                      >
                        {CHESS_PIECE_ICONS[piece]}
                      </span>
                      <span>
                        <span style={{ fontWeight: 600 }}>
                          {CHESS_PIECE_LABELS[piece]}
                        </span>
                        <span
                          style={{
                            display: "block",
                            fontSize: "0.8rem",
                            color: "var(--color-text-muted)",
                          }}
                        >
                          {CHESS_PIECE_DESCRIPTIONS[piece]}
                        </span>
                        {limitMsg && (
                          <span
                            data-testid={`chess-piece-limit-${piece.toLowerCase()}`}
                            style={{
                              display: "block",
                              fontSize: "0.75rem",
                              color: "var(--color-danger)",
                              fontWeight: 600,
                              marginTop: "2px",
                            }}
                          >
                            {limitMsg}
                          </span>
                        )}
                      </span>
                    </label>
                  );
                })}
              </div>
              {errors.chessPiece && (
                <span
                  id="commit-form-piece-error"
                  role="alert"
                  style={errorTextStyle}
                >
                  {errors.chessPiece}
                </span>
              )}
            </fieldset>
          </div>

          {/* Estimate points */}
          <div style={fieldStyle}>
            <p style={{ ...labelStyle, margin: "0 0 0.375rem" }}>
              Estimate Points
            </p>
            <div
              role="group"
              aria-label="Estimate points"
              style={{ display: "flex", gap: "0.375rem", flexWrap: "wrap" }}
              data-testid="commit-form-estimate-group"
            >
              {VALID_POINTS.map((pts) => (
                <button
                  key={pts}
                  type="button"
                  onClick={() =>
                    handleChange(
                      "estimatePoints",
                      values.estimatePoints === String(pts) ? "" : String(pts),
                    )
                  }
                  data-testid={`estimate-btn-${pts}`}
                  aria-pressed={values.estimatePoints === String(pts)}
                  style={{
                    width: "2.5rem",
                    height: "2.5rem",
                    border: `2px solid ${
                      values.estimatePoints === pts
                        ? "var(--color-primary)"
                        : "var(--color-border)"
                    }`,
                    borderRadius: "var(--border-radius)",
                    background:
                      values.estimatePoints === pts
                        ? "var(--color-primary)"
                        : "var(--color-surface)",
                    color:
                      values.estimatePoints === pts
                        ? "#fff"
                        : "var(--color-text)",
                    cursor: "pointer",
                    fontWeight: 700,
                    fontSize: "0.875rem",
                    fontFamily: "inherit",
                  }}
                >
                  {pts}
                </button>
              ))}
            </div>
          </div>

          {/* Success criteria — required for King / Queen */}
          <div style={fieldStyle}>
            <label htmlFor="commit-form-success-criteria" style={labelStyle}>
              Success Criteria
              {(values.chessPiece === "KING" ||
                values.chessPiece === "QUEEN") && requiredMark}
            </label>
            <textarea
              id="commit-form-success-criteria"
              value={values.successCriteria}
              onChange={(e) => handleChange("successCriteria", e.target.value)}
              rows={2}
              aria-required={
                values.chessPiece === "KING" || values.chessPiece === "QUEEN"
              }
              aria-describedby={
                errors.successCriteria
                  ? "commit-form-sc-error"
                  : undefined
              }
              style={{
                ...(errors.successCriteria ? errorInputStyle : inputStyle),
                resize: "vertical",
              }}
              placeholder="How will you know you succeeded?"
              data-testid="commit-form-success-criteria"
            />
            {errors.successCriteria && (
              <span
                id="commit-form-sc-error"
                role="alert"
                style={errorTextStyle}
              >
                {errors.successCriteria}
              </span>
            )}
          </div>

          {/* RCDO picker */}
          <div style={fieldStyle}>
            <p style={{ ...labelStyle, margin: "0 0 0.375rem" }}>
              RCDO Link
            </p>
            {selectedRcdoNode && (
              <div
                data-testid="rcdo-selected-node"
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "0.5rem",
                  padding: "0.375rem 0.625rem",
                  background: "#f0fdf4",
                  border: "1px solid #86efac",
                  borderRadius: "var(--border-radius)",
                  marginBottom: "0.5rem",
                  fontSize: "0.875rem",
                }}
              >
                <span style={{ flex: 1 }}>✅ {selectedRcdoNode.title}</span>
                <button
                  type="button"
                  onClick={() => handleChange("rcdoNodeId", "")}
                  aria-label="Clear RCDO link"
                  style={{
                    background: "none",
                    border: "none",
                    cursor: "pointer",
                    color: "var(--color-text-muted)",
                    fontSize: "0.875rem",
                    padding: 0,
                  }}
                >
                  ✕
                </button>
              </div>
            )}
            <button
              type="button"
              onClick={() => setShowRcdoPicker((v) => !v)}
              data-testid="rcdo-picker-toggle"
              aria-expanded={showRcdoPicker}
              style={{
                padding: "0.375rem 0.75rem",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                background: "var(--color-surface)",
                cursor: "pointer",
                fontSize: "0.875rem",
                fontFamily: "inherit",
                color: "var(--color-text)",
              }}
            >
              {showRcdoPicker ? "▲ Close RCDO picker" : "▼ Browse RCDO nodes"}
            </button>
            {showRcdoPicker && (
              <div
                data-testid="rcdo-picker-panel"
                style={{
                  marginTop: "0.5rem",
                  border: "1px solid var(--color-border)",
                  borderRadius: "var(--border-radius)",
                  padding: "0.75rem",
                  maxHeight: "250px",
                  overflowY: "auto",
                }}
              >
                <RcdoTreeView
                  nodes={rcdoTree}
                  selectedId={values.rcdoNodeId || null}
                  onSelect={(id) => {
                    const selectedNode = findNodeById(rcdoTree, id);
                    if (!selectedNode) return;

                    const rcdoError = getRcdoSelectionError(selectedNode);
                    if (rcdoError) {
                      setErrors((prev) => ({ ...prev, rcdoNodeId: rcdoError }));
                      return;
                    }

                    handleChange("rcdoNodeId", id);
                    setShowRcdoPicker(false);
                  }}
                  statusFilter="active-only"
                  searchQuery=""
                />
              </div>
            )}
            {errors.rcdoNodeId && (
              <span role="alert" style={errorTextStyle}>
                {errors.rcdoNodeId}
              </span>
            )}
          </div>

          {/* Linked ticket */}
          <div style={fieldStyle}>
            <label htmlFor="commit-form-ticket" style={labelStyle}>
              Linked Ticket
            </label>
            <input
              id="commit-form-ticket"
              type="text"
              value={values.workItemId}
              onChange={(e) => handleChange("workItemId", e.target.value)}
              style={inputStyle}
              placeholder="Ticket ID (optional)"
              data-testid="commit-form-ticket"
            />
          </div>

          {/* API error */}
          {submitError && (
            <div
              role="alert"
              style={{
                color: "var(--color-danger)",
                marginBottom: "1rem",
                fontSize: "0.875rem",
                padding: "0.5rem",
                background: "#fef2f2",
                borderRadius: "var(--border-radius)",
              }}
            >
              {submitError}
            </div>
          )}

          {/* Action buttons */}
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
              disabled={submitting}
              style={{
                padding: "0.5rem 1rem",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                background: "var(--color-surface)",
                cursor: "pointer",
                fontSize: "inherit",
                fontFamily: "inherit",
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              data-testid="commit-form-submit"
              style={{
                padding: "0.5rem 1rem",
                border: "none",
                borderRadius: "var(--border-radius)",
                background: "var(--color-primary)",
                color: "#fff",
                cursor: submitting ? "not-allowed" : "pointer",
                fontSize: "inherit",
                fontFamily: "inherit",
              }}
            >
              {submitting ? "Saving…" : isCreate ? "Add Commit" : "Save Changes"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
