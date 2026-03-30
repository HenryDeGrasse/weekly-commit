/**
 * SoftWarningsPanel — UI-only soft warnings, never lock blockers.
 */
import { AlertTriangle } from "lucide-react";
import type { CommitResponse } from "../../api/planTypes.js";

const SOFT_MAX_COMMITS = 8;
const PAWN_POINT_WARNING_THRESHOLD = 0.4;

export interface SoftWarningsPanelProps {
  readonly commits: CommitResponse[];
}

export function SoftWarningsPanel({ commits }: SoftWarningsPanelProps) {
  const activeCommits = commits.filter((c) => c.outcome == null);
  const totalPoints = commits.reduce((sum, c) => sum + (c.estimatePoints ?? 0), 0);
  const pawnPoints = commits.filter((c) => c.chessPiece === "PAWN").reduce((sum, c) => sum + (c.estimatePoints ?? 0), 0);

  const tooManyCommits = activeCommits.length > SOFT_MAX_COMMITS;
  const tooManyPawns = totalPoints > 0 && pawnPoints / totalPoints > PAWN_POINT_WARNING_THRESHOLD;

  if (!tooManyCommits && !tooManyPawns) return null;

  return (
    <div data-testid="soft-warnings-panel" aria-live="polite" className="flex flex-col gap-2">
      {tooManyCommits && (
        <div
          role="status"
          data-testid="warning-too-many-commits"
          className="flex items-start gap-2 px-3 py-2.5 rounded-default border border-warning/30 bg-warning/5 text-sm text-foreground"
        >
          <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-neutral-500" aria-hidden="true" />
          <span>
            <strong>Too many commits:</strong> you have {activeCommits.length} active commits. Consider focusing on
            fewer items for better weekly throughput (recommended: ≤{SOFT_MAX_COMMITS}).
          </span>
        </div>
      )}

      {tooManyPawns && (
        <div
          role="status"
          data-testid="warning-pawn-heavy"
          className="flex items-start gap-2 px-3 py-2.5 rounded-default border border-neutral-300 bg-neutral-100 text-sm text-foreground"
        >
          <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-neutral-500" aria-hidden="true" />
          <span>
            <strong>Pawn-heavy plan:</strong> {Math.round((pawnPoints / totalPoints) * 100)}% of your points are Pawn
            commits. Consider elevating strategic work.
          </span>
        </div>
      )}
    </div>
  );
}
