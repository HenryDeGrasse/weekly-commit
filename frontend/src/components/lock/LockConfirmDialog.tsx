/**
 * LockConfirmDialog — confirmation dialog before locking a plan.
 *
 * Shows a summary of commits being locked, total points, and RCDO coverage.
 * Confirm/Cancel buttons.
 */
import type { CommitResponse, ChessPiece } from "../../api/planTypes.js";

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔",
  QUEEN: "♕",
  ROOK: "♖",
  BISHOP: "♗",
  KNIGHT: "♘",
  PAWN: "♙",
};

export interface LockConfirmDialogProps {
  readonly commits: CommitResponse[];
  readonly capacityBudgetPoints: number;
  readonly weekLabel: string;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
  readonly isLocking?: boolean;
}

export function LockConfirmDialog({
  commits,
  capacityBudgetPoints,
  weekLabel,
  onConfirm,
  onCancel,
  isLocking = false,
}: LockConfirmDialogProps) {
  const totalPoints = commits.reduce(
    (s, c) => s + (c.estimatePoints ?? 0),
    0,
  );
  const rcdoCoverage = Math.round(
    (commits.filter((c) => c.rcdoNodeId).length / Math.max(commits.length, 1)) *
      100,
  );

  const chessPieceCounts = commits.reduce<Record<ChessPiece, number>>(
    (acc, c) => {
      acc[c.chessPiece] = (acc[c.chessPiece] ?? 0) + 1;
      return acc;
    },
    {} as Record<ChessPiece, number>,
  );

  const pieceOrder: ChessPiece[] = [
    "KING",
    "QUEEN",
    "ROOK",
    "BISHOP",
    "KNIGHT",
    "PAWN",
  ];

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="lock-confirm-title"
      data-testid="lock-confirm-dialog"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1200,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.4)",
      }}
    >
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "1.5rem",
          width: "min(480px, 94vw)",
          boxShadow: "0 8px 32px rgba(0,0,0,0.2)",
        }}
      >
        <h3
          id="lock-confirm-title"
          style={{ margin: "0 0 0.25rem", fontSize: "1.1rem" }}
        >
          🔒 Lock Plan
        </h3>
        <p
          style={{
            margin: "0 0 1.25rem",
            fontSize: "0.85rem",
            color: "var(--color-text-muted)",
          }}
        >
          {weekLabel}
        </p>

        {/* Summary stats */}
        <div
          data-testid="lock-confirm-summary"
          style={{
            background: "#f8fafc",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            padding: "0.875rem",
            marginBottom: "1.25rem",
            display: "grid",
            gridTemplateColumns: "1fr 1fr 1fr",
            gap: "0.75rem",
            textAlign: "center",
          }}
        >
          <div>
            <div
              data-testid="lock-confirm-commit-count"
              style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary)" }}
            >
              {commits.length}
            </div>
            <div style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>
              commits
            </div>
          </div>
          <div>
            <div
              data-testid="lock-confirm-total-points"
              style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary)" }}
            >
              {totalPoints}/{capacityBudgetPoints}
            </div>
            <div style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>
              points
            </div>
          </div>
          <div>
            <div
              data-testid="lock-confirm-rcdo-coverage"
              style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--color-primary)" }}
            >
              {rcdoCoverage}%
            </div>
            <div style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>
              RCDO coverage
            </div>
          </div>
        </div>

        {/* Chess piece breakdown */}
        <div style={{ marginBottom: "1.25rem" }}>
          <p
            style={{
              margin: "0 0 0.5rem",
              fontSize: "0.8rem",
              fontWeight: 600,
              color: "var(--color-text-muted)",
              textTransform: "uppercase",
              letterSpacing: "0.05em",
            }}
          >
            Commit breakdown
          </p>
          <div
            data-testid="lock-confirm-piece-breakdown"
            style={{
              display: "flex",
              flexWrap: "wrap",
              gap: "0.375rem",
            }}
          >
            {pieceOrder
              .filter((p) => chessPieceCounts[p] > 0)
              .map((piece) => (
                <span
                  key={piece}
                  data-testid={`piece-count-${piece.toLowerCase()}`}
                  style={{
                    padding: "2px 10px",
                    background: "#e0e7ff",
                    borderRadius: "999px",
                    fontSize: "0.8rem",
                    color: "#3730a3",
                    fontWeight: 600,
                  }}
                >
                  {CHESS_PIECE_ICONS[piece]} {chessPieceCounts[piece]}×{piece.charAt(0) + piece.slice(1).toLowerCase()}
                </span>
              ))}
          </div>
        </div>

        <p
          style={{
            margin: "0 0 1.25rem",
            fontSize: "0.85rem",
            color: "var(--color-text-muted)",
          }}
        >
          Once locked, edits require a scope-change reason. You can start
          reconciliation once the week ends.
        </p>

        <div
          style={{
            display: "flex",
            gap: "0.5rem",
            justifyContent: "flex-end",
          }}
        >
          <button
            type="button"
            onClick={onCancel}
            disabled={isLocking}
            data-testid="lock-confirm-cancel"
            style={{
              padding: "0.5rem 1rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              background: "var(--color-surface)",
              cursor: isLocking ? "not-allowed" : "pointer",
              fontFamily: "inherit",
              fontSize: "0.875rem",
            }}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isLocking}
            data-testid="lock-confirm-btn"
            style={{
              padding: "0.5rem 1.25rem",
              border: "none",
              borderRadius: "var(--border-radius)",
              background: "var(--color-primary)",
              color: "#fff",
              cursor: isLocking ? "not-allowed" : "pointer",
              fontFamily: "inherit",
              fontSize: "0.875rem",
              fontWeight: 600,
            }}
          >
            {isLocking ? "Locking…" : "🔒 Confirm Lock"}
          </button>
        </div>
      </div>
    </div>
  );
}
