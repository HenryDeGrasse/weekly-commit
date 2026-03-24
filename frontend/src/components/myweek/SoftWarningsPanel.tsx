/**
 * SoftWarningsPanel — UI-only soft warnings, never lock blockers.
 *
 * Warns when:
 *   1. More than 8 active commits are in the plan.
 *   2. Pawn-piece commits represent > 40 % of total committed points.
 */
import type { CommitResponse } from "../../api/planTypes.js";

const SOFT_MAX_COMMITS = 8;
const PAWN_POINT_WARNING_THRESHOLD = 0.4;

// ── SoftWarningsPanel ─────────────────────────────────────────────────────────

export interface SoftWarningsPanelProps {
  readonly commits: CommitResponse[];
}

export function SoftWarningsPanel({ commits }: SoftWarningsPanelProps) {
  const activeCommits = commits.filter((c) => c.outcome == null);
  const totalPoints = commits.reduce(
    (sum, c) => sum + (c.estimatePoints ?? 0),
    0,
  );
  const pawnPoints = commits
    .filter((c) => c.chessPiece === "PAWN")
    .reduce((sum, c) => sum + (c.estimatePoints ?? 0), 0);

  const tooManyCommits = activeCommits.length > SOFT_MAX_COMMITS;
  const tooManyPawns =
    totalPoints > 0 && pawnPoints / totalPoints > PAWN_POINT_WARNING_THRESHOLD;

  if (!tooManyCommits && !tooManyPawns) return null;

  return (
    <div
      data-testid="soft-warnings-panel"
      aria-live="polite"
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
      }}
    >
      {tooManyCommits && (
        <div
          role="status"
          data-testid="warning-too-many-commits"
          style={{
            display: "flex",
            alignItems: "flex-start",
            gap: "0.5rem",
            padding: "0.625rem 0.75rem",
            background: "#fffbeb",
            border: "1px solid #fcd34d",
            borderRadius: "var(--border-radius)",
            fontSize: "0.875rem",
            color: "#92400e",
          }}
        >
          <span aria-hidden="true" style={{ fontSize: "1rem", lineHeight: 1 }}>
            ⚠️
          </span>
          <span>
            <strong>Too many commits:</strong> you have{" "}
            {activeCommits.length} active commits. Consider focusing on fewer
            items for better weekly throughput (recommended: ≤{SOFT_MAX_COMMITS}
            ).
          </span>
        </div>
      )}

      {tooManyPawns && (
        <div
          role="status"
          data-testid="warning-pawn-heavy"
          style={{
            display: "flex",
            alignItems: "flex-start",
            gap: "0.5rem",
            padding: "0.625rem 0.75rem",
            background: "#fffbeb",
            border: "1px solid #fcd34d",
            borderRadius: "var(--border-radius)",
            fontSize: "0.875rem",
            color: "#92400e",
          }}
        >
          <span aria-hidden="true" style={{ fontSize: "1rem", lineHeight: 1 }}>
            ⚠️
          </span>
          <span>
            <strong>Pawn-heavy plan:</strong>{" "}
            {Math.round((pawnPoints / totalPoints) * 100)}% of your points are
            Pawn commits. Consider elevating strategic work.
          </span>
        </div>
      )}
    </div>
  );
}
