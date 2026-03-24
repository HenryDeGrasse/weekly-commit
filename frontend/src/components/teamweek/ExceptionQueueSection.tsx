/**
 * ExceptionQueueSection — filterable list of manager-review exceptions.
 *
 * Features:
 *   - Sorted by severity (HIGH first, LOW last).
 *   - Filter by: all / unresolved / resolved.
 *   - Each exception shows: user, type, detail, created date, resolution status.
 *   - Resolve action: mark as reviewed with a required note.
 *   - Comment action: add a plan-level comment.
 */
import { useState } from "react";
import type {
  ExceptionResponse,
  ExceptionSeverity,
  ExceptionType,
  AddCommentPayload,
  ResolveExceptionPayload,
} from "../../api/teamTypes.js";

const SEVERITY_STYLES: Record<
  ExceptionSeverity,
  { bg: string; color: string; label: string }
> = {
  HIGH: { bg: "#fee2e2", color: "#991b1b", label: "HIGH" },
  MEDIUM: { bg: "#fef3c7", color: "#92400e", label: "MED" },
  LOW: { bg: "#eff6ff", color: "#1e40af", label: "LOW" },
};

const SEVERITY_ORDER: Record<ExceptionSeverity, number> = {
  HIGH: 0,
  MEDIUM: 1,
  LOW: 2,
};

const EXCEPTION_TYPE_LABELS: Record<ExceptionType, string> = {
  MISSED_LOCK: "Missed Lock",
  AUTO_LOCKED: "Auto-Locked",
  MISSED_RECONCILE: "Missed Reconcile",
  OVER_BUDGET: "Over Budget",
  REPEATED_CARRY_FORWARD: "Repeated Carry-Forward",
  POST_LOCK_SCOPE_INCREASE: "Post-Lock Scope Increase",
  KING_CHANGED_POST_LOCK: "King Changed Post-Lock",
  HIGH_SCOPE_VOLATILITY: "High Scope Volatility",
};

type FilterMode = "all" | "unresolved" | "resolved";

export interface ExceptionQueueSectionProps {
  readonly exceptions: ExceptionResponse[];
  /** Current user ID — used as the resolverId when resolving exceptions. */
  readonly actorUserId: string;
  /**
   * Called when the manager resolves an exception.
   * The parent component calls the API and refreshes the exception list.
   */
  readonly onResolve?: (
    exceptionId: string,
    payload: ResolveExceptionPayload,
  ) => Promise<void>;
  /**
   * Called when the manager adds a comment on a plan.
   */
  readonly onAddComment?: (payload: AddCommentPayload) => Promise<void>;
}

function ResolveDialog({
  exception,
  onConfirm,
  onCancel,
}: {
  readonly exception: ExceptionResponse;
  readonly onConfirm: (resolution: string) => Promise<void>;
  readonly onCancel: () => void;
}) {
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!note.trim()) return;
    setSaving(true);
    setError(null);
    try {
      await onConfirm(note.trim());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to resolve");
      setSaving(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Resolve exception"
      data-testid="resolve-exception-dialog"
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
          width: "min(420px, 92vw)",
          boxShadow: "0 8px 32px rgba(0,0,0,0.18)",
          display: "flex",
          flexDirection: "column",
          gap: "1rem",
        }}
      >
        <h3 style={{ margin: 0, fontSize: "1rem" }}>Resolve Exception</h3>
        <p style={{ margin: 0, fontSize: "0.875rem", color: "var(--color-text-muted)" }}>
          {EXCEPTION_TYPE_LABELS[exception.exceptionType]}: {exception.description}
        </p>
        <div>
          <label htmlFor="resolution-note" style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, marginBottom: "0.375rem" }}>
            Resolution note <span style={{ color: "var(--color-danger)" }}>*</span>
          </label>
          <textarea
            id="resolution-note"
            data-testid="resolution-note-input"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={3}
            placeholder="Describe how this exception was resolved…"
            style={{
              width: "100%",
              padding: "0.5rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              fontFamily: "inherit",
              fontSize: "0.875rem",
              resize: "vertical",
              boxSizing: "border-box",
            }}
          />
        </div>
        {error && (
          <p role="alert" style={{ margin: 0, fontSize: "0.8rem", color: "var(--color-danger)" }}>
            {error}
          </p>
        )}
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
              fontSize: "0.875rem",
            }}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void handleSubmit()}
            disabled={!note.trim() || saving}
            data-testid="resolve-exception-confirm"
            style={{
              padding: "0.5rem 1rem",
              border: "none",
              borderRadius: "var(--border-radius)",
              background: "var(--color-success)",
              color: "#fff",
              cursor: !note.trim() || saving ? "not-allowed" : "pointer",
              opacity: !note.trim() || saving ? 0.5 : 1,
              fontFamily: "inherit",
              fontSize: "0.875rem",
              fontWeight: 600,
            }}
          >
            {saving ? "Resolving…" : "Mark Resolved"}
          </button>
        </div>
      </div>
    </div>
  );
}

function CommentDialog({
  exception,
  onConfirm,
  onCancel,
}: {
  readonly exception: ExceptionResponse;
  readonly onConfirm: (text: string) => Promise<void>;
  readonly onCancel: () => void;
}) {
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!text.trim()) return;
    setSaving(true);
    setError(null);
    try {
      await onConfirm(text.trim());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add comment");
      setSaving(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Add comment"
      data-testid="add-comment-dialog"
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
          width: "min(420px, 92vw)",
          boxShadow: "0 8px 32px rgba(0,0,0,0.18)",
          display: "flex",
          flexDirection: "column",
          gap: "1rem",
        }}
      >
        <h3 style={{ margin: 0, fontSize: "1rem" }}>Add Comment</h3>
        {exception.planId && (
          <p style={{ margin: 0, fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
            Comment on plan: <code style={{ fontSize: "0.75rem" }}>{exception.planId}</code>
          </p>
        )}
        <div>
          <label htmlFor="comment-text" style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, marginBottom: "0.375rem" }}>
            Comment <span style={{ color: "var(--color-danger)" }}>*</span>
          </label>
          <textarea
            id="comment-text"
            data-testid="comment-text-input"
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={3}
            placeholder="Add your manager comment…"
            style={{
              width: "100%",
              padding: "0.5rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              fontFamily: "inherit",
              fontSize: "0.875rem",
              resize: "vertical",
              boxSizing: "border-box",
            }}
          />
        </div>
        {error && (
          <p role="alert" style={{ margin: 0, fontSize: "0.8rem", color: "var(--color-danger)" }}>
            {error}
          </p>
        )}
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
              fontSize: "0.875rem",
            }}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void handleSubmit()}
            disabled={!text.trim() || saving}
            data-testid="add-comment-confirm"
            style={{
              padding: "0.5rem 1rem",
              border: "none",
              borderRadius: "var(--border-radius)",
              background: "var(--color-primary)",
              color: "#fff",
              cursor: !text.trim() || saving ? "not-allowed" : "pointer",
              opacity: !text.trim() || saving ? 0.5 : 1,
              fontFamily: "inherit",
              fontSize: "0.875rem",
              fontWeight: 600,
            }}
          >
            {saving ? "Posting…" : "Post Comment"}
          </button>
        </div>
      </div>
    </div>
  );
}

export function ExceptionQueueSection({
  exceptions,
  actorUserId,
  onResolve,
  onAddComment,
}: ExceptionQueueSectionProps) {
  const [filter, setFilter] = useState<FilterMode>("unresolved");
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [commentingId, setCommentingId] = useState<string | null>(null);

  const filtered = exceptions
    .filter((e) => {
      if (filter === "unresolved") return !e.resolved;
      if (filter === "resolved") return e.resolved;
      return true;
    })
    .slice()
    .sort(
      (a, b) =>
        SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity],
    );

  const resolvingException = resolvingId ? exceptions.find((e) => e.id === resolvingId) : null;
  const commentingException = commentingId ? exceptions.find((e) => e.id === commentingId) : null;

  const handleResolve = async (exceptionId: string, resolution: string) => {
    if (!onResolve) return;
    await onResolve(exceptionId, { resolverId: actorUserId, resolution });
    setResolvingId(null);
  };

  const handleComment = async (exception: ExceptionResponse, text: string) => {
    if (!onAddComment) return;
    await onAddComment({
      managerId: actorUserId,
      ...(exception.planId ? { planId: exception.planId } : {}),
      text,
    });
    setCommentingId(null);
  };

  const unresolvedCount = exceptions.filter((e) => !e.resolved).length;

  return (
    <section aria-labelledby="exception-queue-heading" data-testid="exception-queue-section">
      <h3 id="exception-queue-heading" style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}>
        Exception Queue
        {unresolvedCount > 0 && (
          <span
            data-testid="exception-queue-badge"
            style={{
              marginLeft: "0.5rem",
              fontSize: "0.7rem",
              fontWeight: 700,
              padding: "1px 7px",
              borderRadius: "999px",
              background: "#fee2e2",
              color: "#991b1b",
            }}
          >
            {unresolvedCount}
          </span>
        )}
      </h3>

      {/* Filter tabs */}
      <div
        style={{ display: "flex", gap: "0.25rem", marginBottom: "0.75rem" }}
        role="tablist"
        aria-label="Exception filter"
      >
        {(["all", "unresolved", "resolved"] as FilterMode[]).map((mode) => (
          <button
            key={mode}
            type="button"
            role="tab"
            aria-selected={filter === mode}
            onClick={() => setFilter(mode)}
            data-testid={`exception-filter-${mode}`}
            style={{
              padding: "0.375rem 0.875rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              background: filter === mode ? "var(--color-primary)" : "var(--color-surface)",
              color: filter === mode ? "#fff" : "var(--color-text)",
              cursor: "pointer",
              fontFamily: "inherit",
              fontSize: "0.8rem",
              fontWeight: filter === mode ? 600 : 400,
            }}
          >
            {mode.charAt(0).toUpperCase() + mode.slice(1)}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <p
          data-testid="no-exceptions-message"
          style={{
            color: filter === "unresolved" ? "var(--color-success)" : "var(--color-text-muted)",
            fontSize: "0.875rem",
            fontWeight: filter === "unresolved" ? 600 : 400,
          }}
        >
          {filter === "unresolved" ? "✓ No open exceptions." : "No exceptions to show."}
        </p>
      ) : (
        <div
          data-testid="exception-list"
          style={{
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            overflow: "hidden",
          }}
        >
          {filtered.map((exception) => {
            const severityStyle = SEVERITY_STYLES[exception.severity];
            return (
              <div
                key={exception.id}
                data-testid={`exception-item-${exception.id}`}
                style={{
                  borderBottom: "1px solid var(--color-border)",
                  padding: "0.75rem",
                  opacity: exception.resolved ? 0.7 : 1,
                }}
              >
                <div
                  style={{
                    display: "flex",
                    alignItems: "flex-start",
                    justifyContent: "space-between",
                    gap: "0.75rem",
                    flexWrap: "wrap",
                  }}
                >
                  {/* Left: severity + type + description */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexWrap: "wrap", marginBottom: "0.375rem" }}>
                      <span
                        data-testid={`exception-severity-${exception.id}`}
                        style={{
                          fontSize: "0.65rem",
                          fontWeight: 700,
                          padding: "1px 7px",
                          borderRadius: "999px",
                          background: severityStyle.bg,
                          color: severityStyle.color,
                        }}
                      >
                        {severityStyle.label}
                      </span>
                      <span
                        data-testid={`exception-type-${exception.id}`}
                        style={{ fontSize: "0.8rem", fontWeight: 600 }}
                      >
                        {EXCEPTION_TYPE_LABELS[exception.exceptionType]}
                      </span>
                      {exception.resolved && (
                        <span
                          data-testid={`exception-resolved-badge-${exception.id}`}
                          style={{
                            fontSize: "0.65rem",
                            padding: "1px 7px",
                            borderRadius: "999px",
                            background: "#d1fae5",
                            color: "#065f46",
                            fontWeight: 600,
                          }}
                        >
                          Resolved
                        </span>
                      )}
                    </div>
                    <p style={{ margin: "0 0 0.25rem", fontSize: "0.8rem" }}>
                      {exception.description}
                    </p>
                    <div style={{ fontSize: "0.7rem", color: "var(--color-text-muted)", display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
                      <span data-testid={`exception-user-${exception.id}`}>User: {exception.userId}</span>
                      <span data-testid={`exception-date-${exception.id}`}>{new Date(exception.createdAt).toLocaleDateString()}</span>
                    </div>
                    {exception.resolved && exception.resolution && (
                      <div
                        data-testid={`exception-resolution-${exception.id}`}
                        style={{
                          marginTop: "0.375rem",
                          padding: "0.375rem 0.625rem",
                          background: "#f0fdf4",
                          borderRadius: "var(--border-radius)",
                          fontSize: "0.75rem",
                          color: "#166534",
                        }}
                      >
                        ✓ {exception.resolution}
                      </div>
                    )}
                  </div>

                  {/* Right: actions */}
                  {!exception.resolved && (
                    <div style={{ display: "flex", gap: "0.375rem", flexShrink: 0 }}>
                      {onResolve && (
                        <button
                          type="button"
                          onClick={() => setResolvingId(exception.id)}
                          data-testid={`resolve-btn-${exception.id}`}
                          style={{
                            padding: "0.375rem 0.75rem",
                            border: "1px solid var(--color-success)",
                            borderRadius: "var(--border-radius)",
                            background: "var(--color-surface)",
                            color: "var(--color-success)",
                            cursor: "pointer",
                            fontFamily: "inherit",
                            fontSize: "0.75rem",
                            fontWeight: 600,
                          }}
                        >
                          ✓ Resolve
                        </button>
                      )}
                      {onAddComment && exception.planId && (
                        <button
                          type="button"
                          onClick={() => setCommentingId(exception.id)}
                          data-testid={`comment-btn-${exception.id}`}
                          style={{
                            padding: "0.375rem 0.75rem",
                            border: "1px solid var(--color-border)",
                            borderRadius: "var(--border-radius)",
                            background: "var(--color-surface)",
                            color: "var(--color-text)",
                            cursor: "pointer",
                            fontFamily: "inherit",
                            fontSize: "0.75rem",
                          }}
                        >
                          💬 Comment
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Resolve dialog */}
      {resolvingException && (
        <ResolveDialog
          exception={resolvingException}
          onConfirm={(resolution) => handleResolve(resolvingException.id, resolution)}
          onCancel={() => setResolvingId(null)}
        />
      )}

      {/* Comment dialog */}
      {commentingException && (
        <CommentDialog
          exception={commentingException}
          onConfirm={(text) => handleComment(commentingException, text)}
          onCancel={() => setCommentingId(null)}
        />
      )}
    </section>
  );
}
