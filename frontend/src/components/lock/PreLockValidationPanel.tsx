/**
 * PreLockValidationPanel — shows hard errors and soft warnings before locking.
 */
import { CheckCircle2, XCircle, AlertTriangle } from "lucide-react";
import type { LockValidationError, CommitResponse } from "../../api/planTypes.js";
import { derivePreLockSoftWarnings, getEffectivePreLockErrors } from "./lockValidation.js";

export interface PreLockValidationPanelProps {
  readonly errors: LockValidationError[];
  readonly commits: CommitResponse[];
  readonly isLoading?: boolean;
}

export function PreLockValidationPanel({ errors, commits, isLoading = false }: PreLockValidationPanelProps) {
  const effectiveErrors = getEffectivePreLockErrors(commits, errors);
  const softWarnings = derivePreLockSoftWarnings(commits);

  if (isLoading) {
    return (
      <div data-testid="pre-lock-validation-loading" className="rounded-default border border-border bg-background p-3 text-sm text-muted">
        Validating…
      </div>
    );
  }

  const hasErrors = effectiveErrors.length > 0;
  const hasWarnings = softWarnings.length > 0;

  return (
    <div data-testid="pre-lock-validation-panel" className="flex flex-col gap-3">
      {/* All-clear */}
      {!hasErrors && !hasWarnings && (
        <div data-testid="pre-lock-validation-ok" className="flex items-center gap-2 rounded-default border border-success/30 bg-success/5 px-3 py-2.5 text-sm text-foreground">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-success" aria-hidden="true" />
          All validation checks passed. Your plan is ready to lock.
        </div>
      )}

      {/* Hard errors */}
      {hasErrors && (
        <div data-testid="pre-lock-hard-errors" className="rounded-default border border-danger/30 bg-danger/5 p-3">
          <p className="m-0 mb-2 flex items-center gap-1.5 text-sm font-bold text-danger">
            <XCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
            Hard Errors — must be resolved before locking
          </p>
          <ul className="m-0 pl-5 flex flex-col gap-1">
            {effectiveErrors.map((err, i) => (
              <li key={`${err.field}-${i}`} data-testid="hard-error-item" className="text-xs text-foreground">
                <strong>{err.field}:</strong> {err.message}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Soft warnings */}
      {hasWarnings && (
        <div data-testid="pre-lock-soft-warnings" className="rounded-default border border-warning/30 bg-warning/5 p-3">
          <p className="m-0 mb-2 flex items-center gap-1.5 text-sm font-bold text-warning">
            <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
            Soft Warnings — informational, will not block lock
          </p>
          <ul className="m-0 pl-5 flex flex-col gap-1">
            {softWarnings.map((warn, i) => (
              <li key={i} data-testid="soft-warning-item" className="text-xs text-muted">{warn}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
