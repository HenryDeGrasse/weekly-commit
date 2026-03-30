/**
 * ChessDistributionSection — visual distribution of commits and points
 * across chess pieces for the team week.
 */
import { cn } from "../../lib/utils.js";
import type { ChessDistributionEntry, ChessPiece } from "../../api/teamTypes.js";

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = { KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙" };
const CHESS_PIECE_LABELS: Record<ChessPiece, string> = { KING: "King", QUEEN: "Queen", ROOK: "Rook", BISHOP: "Bishop", KNIGHT: "Knight", PAWN: "Pawn" };
const PIECE_COLORS: Record<ChessPiece, string> = { KING: "var(--chess-king)", QUEEN: "var(--chess-queen)", ROOK: "var(--chess-rook)", BISHOP: "var(--chess-bishop)", KNIGHT: "var(--chess-knight)", PAWN: "var(--chess-pawn)" };
const CRITICAL_PIECES: ReadonlySet<ChessPiece> = new Set<ChessPiece>(["KING", "QUEEN"]);

const thCls = "px-2 py-1.5 text-[0.65rem] font-semibold uppercase text-muted";

export interface ChessDistributionSectionProps {
  readonly chessDistribution: ChessDistributionEntry[];
}

export function ChessDistributionSection({ chessDistribution }: ChessDistributionSectionProps) {
  const totalPoints = chessDistribution.reduce((sum, e) => sum + e.totalPoints, 0);
  const totalCommits = chessDistribution.reduce((sum, e) => sum + e.commitCount, 0);
  const ORDER: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
  const sorted = ORDER.map((piece) => chessDistribution.find((e) => e.chessPiece === piece) ?? { chessPiece: piece, commitCount: 0, totalPoints: 0 });
  const criticalPoints = sorted.filter((e) => CRITICAL_PIECES.has(e.chessPiece)).reduce((sum, e) => sum + e.totalPoints, 0);
  const criticalPct = totalPoints > 0 ? Math.round((criticalPoints / totalPoints) * 100) : 0;

  return (
    <section aria-labelledby="chess-dist-heading" data-testid="chess-distribution-section">
      <h3 id="chess-dist-heading" className="m-0 mb-3 text-sm font-bold">Chess Distribution</h3>
      <div className="rounded-default border border-border bg-surface p-4 flex flex-col gap-3.5">
        {criticalPct > 0 && (
          <div data-testid="critical-work-summary" className={cn("flex gap-2 items-center px-3 py-2 rounded-default text-xs font-semibold", criticalPct >= 50 ? "bg-foreground/8 text-foreground" : "bg-foreground/5 text-muted")}>
            <span>♔♕ Critical work (King + Queen):</span>
            <span data-testid="critical-work-pct">{criticalPct}% of planned points</span>
          </div>
        )}

        {totalPoints > 0 && (
          <div role="img" aria-label="Chess piece point distribution bar" data-testid="chess-stacked-bar" className="flex h-5 rounded-full overflow-hidden gap-px">
            {sorted.filter((e) => e.totalPoints > 0).map((e) => (
              <div
                key={e.chessPiece}
                data-testid={`bar-segment-${e.chessPiece.toLowerCase()}`}
                title={`${CHESS_PIECE_LABELS[e.chessPiece]}: ${e.totalPoints} pts (${Math.round((e.totalPoints / totalPoints) * 100)}%)`}
                style={{ width: `${(e.totalPoints / totalPoints) * 100}%`, background: PIECE_COLORS[e.chessPiece], transition: "width 0.3s ease", minWidth: "2px" }}
              />
            ))}
          </div>
        )}

        <table data-testid="chess-distribution-table" className="w-full border-collapse text-xs" aria-label="Chess piece breakdown">
          <thead>
            <tr className="border-b border-border">
              <th className={cn(thCls, "text-left")}>Piece</th>
              <th className={cn(thCls, "text-right")}>Commits</th>
              <th className={cn(thCls, "text-right")}>Points</th>
              <th className={cn(thCls, "text-right")}>%</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((e) => {
              const ptPct = totalPoints > 0 ? Math.round((e.totalPoints / totalPoints) * 100) : 0;
              const isCritical = CRITICAL_PIECES.has(e.chessPiece);
              return (
                <tr key={e.chessPiece} data-testid={`chess-row-${e.chessPiece.toLowerCase()}`}
                  className={cn("border-b border-border", isCritical && e.totalPoints > 0 && "bg-foreground/5", e.commitCount === 0 && "opacity-40")}>
                  <td className="px-2 py-1.5">
                    <span className="flex items-center gap-1.5">
                      <span className="inline-block h-2.5 w-2.5 rounded-[2px] shrink-0" style={{ background: PIECE_COLORS[e.chessPiece] }} />
                      <span className="text-sm">{CHESS_PIECE_ICONS[e.chessPiece]}</span>
                      <span className={cn(isCritical ? "font-bold" : "font-normal")}>{CHESS_PIECE_LABELS[e.chessPiece]}</span>
                      {isCritical && e.totalPoints > 0 && <span className="text-[0.55rem] px-1 py-px rounded-full bg-foreground text-background font-bold">CRITICAL</span>}
                    </span>
                  </td>
                  <td className="px-2 py-1.5 text-right" data-testid={`chess-commits-${e.chessPiece.toLowerCase()}`}>
                    {e.commitCount > 0 ? e.commitCount : <span className="text-muted">0</span>}
                  </td>
                  <td className="px-2 py-1.5 text-right font-semibold" data-testid={`chess-points-${e.chessPiece.toLowerCase()}`}>
                    {e.totalPoints > 0 ? e.totalPoints : <span className="text-muted font-normal">0</span>}
                  </td>
                  <td className="px-2 py-1.5 text-right text-muted" data-testid={`chess-pct-${e.chessPiece.toLowerCase()}`}>
                    {ptPct > 0 ? `${ptPct}%` : "—"}
                  </td>
                </tr>
              );
            })}
          </tbody>
          {totalCommits > 0 && (
            <tfoot>
              <tr className="border-t-2 border-border font-bold">
                <td className="px-2 py-1.5">Total</td>
                <td className="px-2 py-1.5 text-right" data-testid="chess-total-commits">{totalCommits}</td>
                <td className="px-2 py-1.5 text-right" data-testid="chess-total-points">{totalPoints}</td>
                <td className="px-2 py-1.5 text-right">100%</td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </section>
  );
}
