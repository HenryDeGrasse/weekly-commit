/**
 * PreLockValidationPanel — shows hard errors and soft warnings before locking.
 *
 * Hard errors block the lock (e.g. missing required fields, piece limits).
 * Soft warnings are informational (AI lint placeholder, capacity).
 */
import type { LockValidationError, CommitResponse } from "../../api/planTypes.js";
import {
  derivePreLockSoftWarnings,
  getEffectivePreLockErrors,
} from "./lockValidation.js";

export interface PreLockValidationPanelProps {
  readonly errors: LockValidationError[];
  readonly commits: CommitResponse[];
  readonly isLoading?: boolean;
}

export function PreLockValidationPanel({
  errors,
  commits,
  isLoading = false,
}: PreLockValidationPanelProps) {
  const effectiveErrors = getEffectivePreLockErrors(commits, errors);
  const softWarnings = derivePreLockSoftWarnings(commits);

  if (isLoading) {
    return (
      <div
        data-testid="pre-lock-validation-loading"
        style={{
          padding: "0.75rem",
          background: "#f9fafb",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          fontSize: "0.875rem",
          color: "var(--color-text-muted)",
        }}
      >
        Validating…
      </div>
    );
  }

  const hasErrors = effectiveErrors.length > 0;
  const hasWarnings = softWarnings.length > 0;

  return (
    <div
      data-testid="pre-lock-validation-panel"
      style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}
    >
      {/* All-clear banner (shown only when no errors and no warnings) */}
      {!hasErrors && !hasWarnings && (
        <div
          data-testid="pre-lock-validation-ok"
          style={{
            padding: "0.75rem",
            background: "#f0fdf4",
            border: "1px solid #86efac",
            borderRadius: "var(--border-radius)",
            fontSize: "0.875rem",
            color: "#15803d",
            display: "flex",
            alignItems: "center",
            gap: "0.5rem",
          }}
        >
          ✅ All validation checks passed. Your plan is ready to lock.
        </div>
      )}

      {/* Hard errors section */}
      {hasErrors && (
        <div
          data-testid="pre-lock-hard-errors"
          style={{
            background: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem",
          }}
        >
          <p
            style={{
              margin: "0 0 0.5rem",
              fontWeight: 700,
              fontSize: "0.875rem",
              color: "#991b1b",
              display: "flex",
              alignItems: "center",
              gap: "0.375rem",
            }}
          >
            🚫 Hard Errors — must be resolved before locking
          </p>
          <ul
            style={{
              margin: 0,
              padding: "0 0 0 1.25rem",
              display: "flex",
              flexDirection: "column",
              gap: "0.25rem",
            }}
          >
            {effectiveErrors.map((err, i) => (
              <li
                key={`${err.field}-${i}`}
                data-testid={`hard-error-item`}
                style={{ fontSize: "0.8rem", color: "#7f1d1d" }}
              >
                <strong>{err.field}:</strong> {err.message}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Soft warnings section */}
      {hasWarnings && (
        <div
          data-testid="pre-lock-soft-warnings"
          style={{
            background: "#fffbeb",
            border: "1px solid #fde68a",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem",
          }}
        >
          <p
            style={{
              margin: "0 0 0.5rem",
              fontWeight: 700,
              fontSize: "0.875rem",
              color: "#92400e",
              display: "flex",
              alignItems: "center",
              gap: "0.375rem",
            }}
          >
            ⚠ Soft Warnings — informational, will not block lock
          </p>
          <ul
            style={{
              margin: 0,
              padding: "0 0 0 1.25rem",
              display: "flex",
              flexDirection: "column",
              gap: "0.25rem",
            }}
          >
            {softWarnings.map((warn, i) => (
              <li
                key={i}
                data-testid={`soft-warning-item`}
                style={{ fontSize: "0.8rem", color: "#78350f" }}
              >
                {warn}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* AI lint placeholder — always shown */}
      <div
        data-testid="pre-lock-ai-lint-placeholder"
        style={{
          background: "#f8fafc",
          border: "1px dashed var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "0.625rem 0.75rem",
          fontSize: "0.8rem",
          color: "var(--color-text-muted)",
          fontStyle: "italic",
        }}
      >
        🤖 AI commit quality hints will appear here (coming soon)
      </div>
    </div>
  );
}
