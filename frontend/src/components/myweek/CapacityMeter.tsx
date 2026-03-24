/**
 * CapacityMeter — visual bar/gauge showing committed points vs weekly budget.
 *
 * Color coding:
 *   ≤ 70 % of budget → green
 *   71–100 % of budget → yellow
 *   > 100 % of budget → red
 *
 * Displays a breakdown by chess-piece type and surfaces a manager override badge
 * when the budget differs from the default (10 pts).
 */
import type { CommitResponse, ChessPiece } from "../../api/planTypes.js";

const DEFAULT_BUDGET = 10;

// Chess piece icons and labels used in the breakdown table.
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

// ── CapacityMeter ─────────────────────────────────────────────────────────────

export interface CapacityMeterProps {
  /** All commits in the plan (used to compute totals). */
  readonly commits: CommitResponse[];
  /** Weekly capacity budget in points (default: 10). */
  readonly budgetPoints: number;
  /** True when a manager has explicitly set a custom budget for this user/week. */
  readonly isManagerOverride?: boolean;
}

export function CapacityMeter({
  commits,
  budgetPoints,
  isManagerOverride = false,
}: CapacityMeterProps) {
  const totalPoints = commits.reduce(
    (sum, c) => sum + (c.estimatePoints ?? 0),
    0,
  );
  const fillPct = budgetPoints > 0
    ? Math.min((totalPoints / budgetPoints) * 100, 100)
    : 0;
  const isOver = totalPoints > budgetPoints;
  const isNear = !isOver && fillPct >= 70;

  const barColor = isOver
    ? "var(--color-danger)"
    : isNear
      ? "var(--color-warning)"
      : "var(--color-success)";

  // Compute per-piece breakdown (only pieces that have ≥ 1 pt).
  const pieceBreakdown = (
    Object.keys(CHESS_PIECE_ICONS) as ChessPiece[]
  )
    .map((piece) => ({
      piece,
      points: commits
        .filter((c) => c.chessPiece === piece)
        .reduce((sum, c) => sum + (c.estimatePoints ?? 0), 0),
    }))
    .filter((row) => row.points > 0);

  return (
    <div
      data-testid="capacity-meter"
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
        borderRadius: "var(--border-radius)",
        padding: "1rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
      }}
    >
      {/* Heading row */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: "0.5rem",
        }}
      >
        <h3 style={{ margin: 0, fontSize: "0.875rem", fontWeight: 700 }}>
          Capacity
        </h3>

        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
          {/* Override badge */}
          {(isManagerOverride || budgetPoints !== DEFAULT_BUDGET) && (
            <span
              data-testid="override-badge"
              style={{
                fontSize: "0.7rem",
                fontWeight: 700,
                padding: "1px 6px",
                borderRadius: "999px",
                background: "#ede9fe",
                color: "#5b21b6",
              }}
            >
              Manager override
            </span>
          )}

          {/* Point tally */}
          <span
            data-testid="capacity-tally"
            style={{
              fontSize: "0.875rem",
              fontWeight: 700,
              color: isOver ? "var(--color-danger)" : "var(--color-text)",
            }}
          >
            {totalPoints} / {budgetPoints} pts
          </span>
        </div>
      </div>

      {/* Progress bar */}
      <div
        role="progressbar"
        aria-label="Capacity usage"
        aria-valuenow={totalPoints}
        aria-valuemin={0}
        aria-valuemax={budgetPoints}
        style={{
          background: "var(--color-border)",
          borderRadius: "999px",
          height: "10px",
          overflow: "hidden",
        }}
      >
        <div
          data-testid="capacity-bar"
          style={{
            width: `${fillPct}%`,
            height: "100%",
            background: barColor,
            borderRadius: "999px",
            transition: "width 0.3s ease",
          }}
        />
      </div>

      {/* Over-budget alert */}
      {isOver && (
        <p
          role="alert"
          data-testid="over-budget-alert"
          style={{
            margin: 0,
            fontSize: "0.8rem",
            color: "var(--color-danger)",
            fontWeight: 600,
          }}
        >
          {totalPoints - budgetPoints} pt{totalPoints - budgetPoints !== 1 ? "s" : ""} over budget
        </p>
      )}

      {/* Per-piece breakdown */}
      {pieceBreakdown.length > 0 && (
        <ul
          aria-label="Points by chess piece"
          data-testid="capacity-breakdown"
          style={{
            margin: 0,
            padding: 0,
            listStyle: "none",
            display: "flex",
            flexWrap: "wrap",
            gap: "0.5rem",
          }}
        >
          {pieceBreakdown.map(({ piece, points }) => (
            <li
              key={piece}
              data-testid={`capacity-piece-${piece.toLowerCase()}`}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "0.25rem",
                fontSize: "0.75rem",
                background: "var(--color-background)",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                padding: "2px 8px",
              }}
            >
              <span aria-hidden="true">{CHESS_PIECE_ICONS[piece]}</span>
              <span>{CHESS_PIECE_LABELS[piece]}</span>
              <span style={{ fontWeight: 700 }}>{points}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
