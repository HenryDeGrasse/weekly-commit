/**
 * LockConfirmDialog — confirmation dialog before locking a plan.
 */
import { Lock } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "../ui/Dialog.js";
import type { CommitResponse, ChessPiece } from "../../api/planTypes.js";

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙",
};

export interface LockConfirmDialogProps {
  readonly commits: CommitResponse[];
  readonly capacityBudgetPoints: number;
  readonly weekLabel: string;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
  readonly isLocking?: boolean;
}

export function LockConfirmDialog({ commits, capacityBudgetPoints, weekLabel, onConfirm, onCancel, isLocking = false }: LockConfirmDialogProps) {
  const totalPoints = commits.reduce((s, c) => s + (c.estimatePoints ?? 0), 0);
  const rcdoCoverage = Math.round((commits.filter((c) => c.rcdoNodeId).length / Math.max(commits.length, 1)) * 100);
  const chessPieceCounts = commits.reduce<Record<ChessPiece, number>>((acc, c) => {
    acc[c.chessPiece] = (acc[c.chessPiece] ?? 0) + 1;
    return acc;
  }, {} as Record<ChessPiece, number>);
  const pieceOrder: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];

  return (
    <Dialog open onClose={onCancel} aria-label="Lock Plan" data-testid="lock-confirm-dialog">
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2">
          <Lock className="h-4 w-4 text-primary" aria-hidden="true" />
          Lock Plan
        </DialogTitle>
        <DialogDescription>{weekLabel}</DialogDescription>
      </DialogHeader>

      {/* Summary stats */}
      <div data-testid="lock-confirm-summary" className="mb-5 grid grid-cols-3 gap-3 rounded-default border border-border bg-background p-3 text-center">
        <div>
          <div data-testid="lock-confirm-commit-count" className="text-2xl font-bold text-primary">{commits.length}</div>
          <div className="text-xs text-muted">commits</div>
        </div>
        <div>
          <div data-testid="lock-confirm-total-points" className="text-2xl font-bold text-primary">{totalPoints}/{capacityBudgetPoints}</div>
          <div className="text-xs text-muted">points</div>
        </div>
        <div>
          <div data-testid="lock-confirm-rcdo-coverage" className="text-2xl font-bold text-primary">{rcdoCoverage}%</div>
          <div className="text-xs text-muted">RCDO coverage</div>
        </div>
      </div>

      {/* Chess piece breakdown */}
      <div className="mb-5">
        <p className="m-0 mb-2 text-xs font-semibold uppercase tracking-wider text-muted">Commit breakdown</p>
        <div data-testid="lock-confirm-piece-breakdown" className="flex flex-wrap gap-1.5">
          {pieceOrder.filter((p) => chessPieceCounts[p] > 0).map((piece) => (
            <span key={piece} data-testid={`piece-count-${piece.toLowerCase()}`} className="rounded-full bg-indigo-100 px-2.5 py-0.5 text-xs font-semibold text-indigo-800">
              {CHESS_PIECE_ICONS[piece]} {chessPieceCounts[piece]}×{piece.charAt(0) + piece.slice(1).toLowerCase()}
            </span>
          ))}
        </div>
      </div>

      <p className="mb-5 text-sm text-muted">
        Once locked, edits require a scope-change reason. You can start reconciliation once the week ends.
      </p>

      <DialogFooter>
        <Button variant="secondary" onClick={onCancel} disabled={isLocking} data-testid="lock-confirm-cancel">
          Cancel
        </Button>
        <Button variant="primary" onClick={onConfirm} disabled={isLocking} data-testid="lock-confirm-btn">
          <Lock className="h-3.5 w-3.5" />
          {isLocking ? "Locking…" : "Confirm Lock"}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
