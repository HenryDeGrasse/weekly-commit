/**
 * CarryForwardDialog — captures carry-forward reason and target week.
 *
 * Shown for NOT_ACHIEVED and PARTIALLY_ACHIEVED commits when the user
 * opts to carry the commit into the next week.
 */
import { useState, type FormEvent } from "react";
import type { CommitResponse, CarryForwardReason } from "../../api/planTypes.js";

const CARRY_FORWARD_REASONS: { value: CarryForwardReason; label: string }[] = [
  { value: "STILL_IN_PROGRESS", label: "Still in progress — work continues" },
  { value: "BLOCKED_BY_DEPENDENCY", label: "Blocked by a dependency" },
  { value: "SCOPE_EXPANDED", label: "Scope expanded beyond estimate" },
  { value: "REPRIORITIZED", label: "Reprioritized — will complete next week" },
  { value: "RESOURCE_UNAVAILABLE", label: "Resource or person unavailable" },
  { value: "TECHNICAL_OBSTACLE", label: "Technical obstacle encountered" },
  { value: "EXTERNAL_DELAY", label: "External delay (vendor, partner, etc.)" },
  { value: "UNDERESTIMATED", label: "Work was underestimated" },
];

/** Returns the ISO date (yyyy-MM-dd) for the Monday of a given week offset from today. */
function getWeekStart(offsetWeeks = 0): string {
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

export interface CarryForwardDialogProps {
  readonly commit: CommitResponse;
  readonly onConfirm: (
    targetWeekStart: string,
    reason: CarryForwardReason,
    reasonText?: string,
  ) => void;
  readonly onCancel: () => void;
  readonly isSubmitting?: boolean;
}

export function CarryForwardDialog({
  commit,
  onConfirm,
  onCancel,
  isSubmitting = false,
}: CarryForwardDialogProps) {
  // Default target to next week
  const [targetWeekStart, setTargetWeekStart] = useState(getWeekStart(1));
  const [reason, setReason] = useState<CarryForwardReason | "">("");
  const [reasonText, setReasonText] = useState("");
  const [errors, setErrors] = useState<{ reason?: string | undefined; target?: string | undefined }>({});

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const next: typeof errors = {};
    if (!reason) next.reason = "Please select a reason";
    if (!targetWeekStart) next.target = "Target week is required";
    if (Object.keys(next).length > 0) {
      setErrors(next);
      return;
    }
    setErrors({});
    const trimmedText = reasonText.trim();
    onConfirm(
      targetWeekStart,
      reason as CarryForwardReason,
      trimmedText !== "" ? trimmedText : undefined,
    );
  }

  const nextWeekOption = getWeekStart(1);
  const twoWeeksOption = getWeekStart(2);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="carry-forward-dialog-title"
      data-testid="carry-forward-dialog"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1300,
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
          width: "min(480px, 96vw)",
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
            id="carry-forward-dialog-title"
            style={{ margin: 0, fontSize: "1rem", color: "#1e40af" }}
          >
            🔁 Carry Forward
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

        {/* Commit preview */}
        <div
          data-testid="carry-forward-commit-preview"
          style={{
            background: "#eff6ff",
            border: "1px solid #bfdbfe",
            borderRadius: "var(--border-radius)",
            padding: "0.625rem 0.75rem",
            marginBottom: "1rem",
            fontSize: "0.875rem",
            color: "#1e40af",
          }}
        >
          <strong>"{commit.title}"</strong> will be copied into the target week
          as a new commit with carry-forward provenance.
          {commit.carryForwardStreak > 0 && (
            <span
              style={{
                display: "block",
                fontSize: "0.75rem",
                marginTop: "0.25rem",
                color: "#6d28d9",
              }}
            >
              Already carried forward {commit.carryForwardStreak} time
              {commit.carryForwardStreak !== 1 ? "s" : ""}.
            </span>
          )}
        </div>

        <form onSubmit={handleSubmit} noValidate>
          {/* Target week */}
          <div style={{ marginBottom: "1rem" }}>
            <p
              style={{
                margin: "0 0 0.375rem",
                fontWeight: 600,
                fontSize: "0.875rem",
              }}
            >
              Target week{" "}
              <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>
                *
              </span>
            </p>
            <div
              role="group"
              aria-label="Target week options"
              style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}
              data-testid="carry-forward-week-options"
            >
              {[nextWeekOption, twoWeeksOption].map((weekStart) => (
                <label
                  key={weekStart}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "0.5rem",
                    padding: "0.5rem 0.625rem",
                    border: `1px solid ${
                      targetWeekStart === weekStart
                        ? "var(--color-primary)"
                        : "var(--color-border)"
                    }`,
                    borderRadius: "var(--border-radius)",
                    background:
                      targetWeekStart === weekStart ? "#eff6ff" : "transparent",
                    cursor: "pointer",
                    fontSize: "0.875rem",
                  }}
                >
                  <input
                    type="radio"
                    name="targetWeek"
                    value={weekStart}
                    checked={targetWeekStart === weekStart}
                    onChange={() => setTargetWeekStart(weekStart)}
                  />
                  {formatWeekLabel(weekStart)}
                </label>
              ))}
            </div>
            {errors.target && (
              <span
                role="alert"
                style={{
                  display: "block",
                  color: "var(--color-danger)",
                  fontSize: "0.75rem",
                  marginTop: "0.25rem",
                }}
              >
                {errors.target}
              </span>
            )}
          </div>

          {/* Reason select */}
          <div style={{ marginBottom: "1rem" }}>
            <label
              htmlFor="carry-forward-reason"
              style={{
                display: "block",
                marginBottom: "0.375rem",
                fontWeight: 600,
                fontSize: "0.875rem",
              }}
            >
              Reason{" "}
              <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>
                *
              </span>
            </label>
            <select
              id="carry-forward-reason"
              value={reason}
              onChange={(e) => {
                setReason(e.target.value as CarryForwardReason | "");
                setErrors((prev) => ({ ...prev, reason: undefined }));
              }}
              aria-required="true"
              aria-describedby={errors.reason ? "cf-reason-error" : undefined}
              data-testid="carry-forward-reason-select"
              style={{
                width: "100%",
                padding: "0.5rem",
                border: `1px solid ${errors.reason ? "var(--color-danger)" : "var(--color-border)"}`,
                borderRadius: "var(--border-radius)",
                fontSize: "inherit",
                fontFamily: "inherit",
                background: "var(--color-surface)",
                color: reason ? "var(--color-text)" : "var(--color-text-muted)",
              }}
            >
              <option value="">— Select a reason —</option>
              {CARRY_FORWARD_REASONS.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </select>
            {errors.reason && (
              <span
                id="cf-reason-error"
                role="alert"
                style={{
                  display: "block",
                  color: "var(--color-danger)",
                  fontSize: "0.75rem",
                  marginTop: "0.25rem",
                }}
              >
                {errors.reason}
              </span>
            )}
          </div>

          {/* Optional notes */}
          <div style={{ marginBottom: "1.25rem" }}>
            <label
              htmlFor="carry-forward-notes"
              style={{
                display: "block",
                marginBottom: "0.375rem",
                fontWeight: 600,
                fontSize: "0.875rem",
              }}
            >
              Additional notes (optional)
            </label>
            <textarea
              id="carry-forward-notes"
              value={reasonText}
              onChange={(e) => setReasonText(e.target.value)}
              rows={2}
              data-testid="carry-forward-notes"
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
              placeholder="Any additional context…"
            />
          </div>

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
              data-testid="carry-forward-cancel"
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
              data-testid="carry-forward-confirm"
              style={{
                padding: "0.5rem 1.25rem",
                border: "none",
                borderRadius: "var(--border-radius)",
                background: "#1e40af",
                color: "#fff",
                cursor: isSubmitting ? "not-allowed" : "pointer",
                fontFamily: "inherit",
                fontSize: "0.875rem",
                fontWeight: 600,
              }}
            >
              {isSubmitting ? "Saving…" : "🔁 Carry Forward"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
