/**
 * LoadingWithTimeout — three-tier loading strategy.
 *
 * Tier 1 (0 – timeoutMs): renders `skeleton` children.
 * Tier 3 (> timeoutMs): renders a "Still loading… [Retry]" banner
 *   with elapsed time indicator and an optional retry action.
 *
 * Usage:
 *   <LoadingWithTimeout skeleton={<PlanHeaderSkeleton />} onRetry={refetch}>
 *     {content}
 *   </LoadingWithTimeout>
 */
import { useState, useEffect, type ReactNode } from "react";
import { RefreshCw } from "lucide-react";
import { Button } from "../ui/Button.js";

interface LoadingWithTimeoutProps {
  /** Whether data is still being loaded. */
  isLoading: boolean;
  /** Skeleton element(s) shown during Tier 1 loading. */
  skeleton: ReactNode;
  /** Seconds before switching from skeleton to "Still loading…" state (default: 5). */
  timeoutSeconds?: number;
  /** Called when the user clicks "Retry". */
  onRetry?: () => void;
  /** Content rendered when `isLoading` is false. */
  children: ReactNode;
}

export function LoadingWithTimeout({
  isLoading,
  skeleton,
  timeoutSeconds = 5,
  onRetry,
  children,
}: LoadingWithTimeoutProps) {
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!isLoading) {
      setElapsed(0);
      return;
    }

    const interval = setInterval(() => {
      setElapsed((s) => s + 1);
    }, 1000);

    return () => clearInterval(interval);
  }, [isLoading]);

  if (!isLoading) return <>{children}</>;

  // Tier 1 — skeleton
  if (elapsed < timeoutSeconds) return <>{skeleton}</>;

  // Tier 3 — explicit timeout state with retry
  return (
    <div
      role="status"
      data-testid="loading-timeout-banner"
      className="flex items-center gap-3 rounded-default border border-border bg-surface px-4 py-3 text-sm text-muted"
    >
      <RefreshCw className="h-4 w-4 shrink-0 animate-spin text-muted" aria-hidden="true" />
      <span className="flex-1">
        Still loading… ({elapsed}s elapsed)
      </span>
      {onRetry && (
        <Button
          variant="secondary"
          size="sm"
          onClick={() => {
            setElapsed(0);
            onRetry();
          }}
          data-testid="loading-timeout-retry"
        >
          Retry
        </Button>
      )}
    </div>
  );
}
