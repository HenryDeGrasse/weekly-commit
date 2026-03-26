/**
 * PreLockValidationPanel — shows hard errors and soft warnings before locking.
 */
import { CheckCircle2, XCircle, AlertTriangle } from "lucide-react";
import { AiLintPanel } from "../ai/AiLintPanel.js";
import { useHostBridge } from "../../host/HostProvider.js";
import type { LockValidationError, CommitResponse } from "../../api/planTypes.js";
import { derivePreLockSoftWarnings, getEffectivePreLockErrors } from "./lockValidation.js";

export interface PreLockValidationPanelProps {
  readonly errors: LockValidationError[];
  readonly commits: CommitResponse[];
  readonly isLoading?: boolean;
}

export function PreLockValidationPanel({ errors, commits, isLoading = false }: PreLockValidationPanelProps) {
  const bridge = useHostBridge();
  const userId = bridge.context.authenticatedUser.id;
  const effectiveErrors = getEffectivePreLockErrors(commits, errors);
  const softWarnings = derivePreLockSoftWarnings(commits);
  // Get planId from first commit (all commits share the same planId)
  const planId = commits.length > 0 && commits[0] ? commits[0].planId : null;

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
        <div data-testid="pre-lock-validation-ok" className="flex items-center gap-2 rounded-default border border-emerald-300 bg-emerald-50 px-3 py-2.5 text-sm text-emerald-700">
          <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" />
          All validation checks passed. Your plan is ready to lock.
        </div>
      )}

      {/* Hard errors */}
      {hasErrors && (
        <div data-testid="pre-lock-hard-errors" className="rounded-default border border-red-200 bg-red-50 p-3">
          <p className="m-0 mb-2 flex items-center gap-1.5 text-sm font-bold text-red-800">
            <XCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
            Hard Errors — must be resolved before locking
          </p>
          <ul className="m-0 pl-5 flex flex-col gap-1">
            {effectiveErrors.map((err, i) => (
              <li key={`${err.field}-${i}`} data-testid="hard-error-item" className="text-xs text-red-900">
                <strong>{err.field}:</strong> {err.message}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Soft warnings */}
      {hasWarnings && (
        <div data-testid="pre-lock-soft-warnings" className="rounded-default border border-amber-200 bg-amber-50 p-3">
          <p className="m-0 mb-2 flex items-center gap-1.5 text-sm font-bold text-amber-800">
            <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
            Soft Warnings — informational, will not block lock
          </p>
          <ul className="m-0 pl-5 flex flex-col gap-1">
            {softWarnings.map((warn, i) => (
              <li key={i} data-testid="soft-warning-item" className="text-xs text-amber-900">{warn}</li>
            ))}
          </ul>
        </div>
      )}

      {/* AI lint — real integration replacing the placeholder */}
      {planId && (
        <AiLintPanel planId={planId} userId={userId} />
      )}
      {!planId && (
        <div data-testid="pre-lock-ai-lint-placeholder" className="flex items-center gap-2 rounded-default border border-dashed border-border bg-background/60 px-3 py-2.5 text-xs text-muted italic">
          AI commit quality hints will appear once commits are added
        </div>
      )}
    </div>
  );
}
