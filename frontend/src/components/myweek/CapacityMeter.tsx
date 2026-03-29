/**
 * CapacityMeter — visual bar/gauge showing committed points vs weekly budget.
 */
import type { CommitResponse, ChessPiece } from "../../api/planTypes.js";
import { cn } from "../../lib/utils.js";

const DEFAULT_BUDGET = 10;

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙",
};
const CHESS_PIECE_LABELS: Record<ChessPiece, string> = {
  KING: "King", QUEEN: "Queen", ROOK: "Rook", BISHOP: "Bishop", KNIGHT: "Knight", PAWN: "Pawn",
};

export interface CapacityMeterProps {
  readonly commits: CommitResponse[];
  readonly budgetPoints: number;
  readonly isManagerOverride?: boolean;
}

export function CapacityMeter({ commits, budgetPoints, isManagerOverride = false }: CapacityMeterProps) {
  const totalPoints = commits.reduce((sum, c) => sum + (c.estimatePoints ?? 0), 0);
  const fillPct = budgetPoints > 0 ? Math.min((totalPoints / budgetPoints) * 100, 100) : 0;
  const isOver = totalPoints > budgetPoints;
  const isNear = !isOver && fillPct >= 70;

  const pieceBreakdown = (Object.keys(CHESS_PIECE_ICONS) as ChessPiece[])
    .map((piece) => ({
      piece,
      points: commits.filter((c) => c.chessPiece === piece).reduce((sum, c) => sum + (c.estimatePoints ?? 0), 0),
    }))
    .filter((row) => row.points > 0);

  return (
    <div data-testid="capacity-meter" className="rounded-default border border-border bg-surface p-4 flex flex-col gap-3">
      {/* Heading row */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h3 className="m-0 text-sm font-bold">Capacity</h3>
        <div className="flex items-center gap-2">
          {(isManagerOverride || budgetPoints !== DEFAULT_BUDGET) && (
            <span data-testid="override-badge" className="text-[0.7rem] font-bold px-1.5 py-0.5 rounded-full bg-neutral-200 text-neutral-700">
              Manager override
            </span>
          )}
          <span data-testid="capacity-tally" className={cn("text-sm font-bold font-mono", isOver ? "text-foreground underline" : "text-foreground")}>
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
        className="h-2.5 rounded-full bg-border overflow-hidden"
      >
        <div
          data-testid="capacity-bar"
          className={cn(
            "h-full rounded-full transition-[width] duration-300",
            isOver ? "bg-foreground" : isNear ? "bg-neutral-400" : "bg-neutral-600",
          )}
          style={{ width: `${fillPct}%` }}
        />
      </div>

      {/* Over-budget alert */}
      {isOver && (
        <p role="alert" data-testid="over-budget-alert" className="m-0 text-xs text-foreground font-bold underline">
          {totalPoints - budgetPoints} pt{totalPoints - budgetPoints !== 1 ? "s" : ""} over budget
        </p>
      )}

      {/* Per-piece breakdown */}
      {pieceBreakdown.length > 0 && (
        <ul aria-label="Points by chess piece" data-testid="capacity-breakdown" className="m-0 p-0 list-none flex flex-wrap gap-2">
          {pieceBreakdown.map(({ piece, points }) => (
            <li
              key={piece}
              data-testid={`capacity-piece-${piece.toLowerCase()}`}
              className="flex items-center gap-1 text-xs rounded-default border border-border bg-background px-2 py-0.5"
            >
              <span aria-hidden="true">{CHESS_PIECE_ICONS[piece]}</span>
              <span>{CHESS_PIECE_LABELS[piece]}</span>
              <span className="font-bold font-mono">{points}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
