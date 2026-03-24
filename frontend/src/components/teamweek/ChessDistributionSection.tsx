/**
 * ChessDistributionSection — visual distribution of commits and points
 * across chess pieces for the team week.
 *
 * Shows a horizontal stacked bar and per-piece breakdown table.
 * King/Queen (critical work) rows are highlighted.
 */
import type { ChessDistributionEntry, ChessPiece } from "../../api/teamTypes.js";

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

/** Color palette for the stacked bar — one hue per piece. */
const PIECE_COLORS: Record<ChessPiece, string> = {
  KING: "#7c3aed",
  QUEEN: "#db2777",
  ROOK: "#2563eb",
  BISHOP: "#0891b2",
  KNIGHT: "#059669",
  PAWN: "#6b7280",
};

/** King and Queen are considered "critical" — highlighted specially. */
const CRITICAL_PIECES: ReadonlySet<ChessPiece> = new Set<ChessPiece>(["KING", "QUEEN"]);

export interface ChessDistributionSectionProps {
  readonly chessDistribution: ChessDistributionEntry[];
}

export function ChessDistributionSection({
  chessDistribution,
}: ChessDistributionSectionProps) {
  const totalPoints = chessDistribution.reduce((sum, e) => sum + e.totalPoints, 0);
  const totalCommits = chessDistribution.reduce((sum, e) => sum + e.commitCount, 0);

  // Order matches chess importance descending for display
  const ORDER: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
  const sorted = ORDER.map((piece) => {
    const entry = chessDistribution.find((e) => e.chessPiece === piece);
    return entry ?? { chessPiece: piece, commitCount: 0, totalPoints: 0 };
  });

  const criticalPoints = sorted
    .filter((e) => CRITICAL_PIECES.has(e.chessPiece))
    .reduce((sum, e) => sum + e.totalPoints, 0);
  const criticalPct = totalPoints > 0 ? Math.round((criticalPoints / totalPoints) * 100) : 0;

  return (
    <section aria-labelledby="chess-dist-heading" data-testid="chess-distribution-section">
      <h3
        id="chess-dist-heading"
        style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}
      >
        Chess Distribution
      </h3>
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "1rem",
          display: "flex",
          flexDirection: "column",
          gap: "0.875rem",
        }}
      >
        {/* Critical work highlight */}
        {criticalPct > 0 && (
          <div
            data-testid="critical-work-summary"
            style={{
              display: "flex",
              gap: "0.5rem",
              alignItems: "center",
              padding: "0.5rem 0.75rem",
              background: criticalPct >= 50 ? "#fef3c7" : "#f0fdf4",
              borderRadius: "var(--border-radius)",
              fontSize: "0.8rem",
              fontWeight: 600,
              color: criticalPct >= 50 ? "#92400e" : "#166534",
            }}
          >
            <span>♔♕ Critical work (King + Queen):</span>
            <span data-testid="critical-work-pct">{criticalPct}% of planned points</span>
          </div>
        )}

        {/* Stacked bar */}
        {totalPoints > 0 && (
          <div
            role="img"
            aria-label="Chess piece point distribution bar"
            data-testid="chess-stacked-bar"
            style={{
              display: "flex",
              height: "20px",
              borderRadius: "999px",
              overflow: "hidden",
              gap: "1px",
            }}
          >
            {sorted
              .filter((e) => e.totalPoints > 0)
              .map((e) => (
                <div
                  key={e.chessPiece}
                  data-testid={`bar-segment-${e.chessPiece.toLowerCase()}`}
                  title={`${CHESS_PIECE_LABELS[e.chessPiece]}: ${e.totalPoints} pts (${Math.round((e.totalPoints / totalPoints) * 100)}%)`}
                  style={{
                    width: `${(e.totalPoints / totalPoints) * 100}%`,
                    background: PIECE_COLORS[e.chessPiece],
                    transition: "width 0.3s ease",
                    minWidth: "2px",
                  }}
                />
              ))}
          </div>
        )}

        {/* Legend / breakdown table */}
        <table
          data-testid="chess-distribution-table"
          style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.8rem" }}
          aria-label="Chess piece breakdown"
        >
          <thead>
            <tr style={{ borderBottom: "1px solid var(--color-border)" }}>
              <th style={{ padding: "0.25rem 0.5rem", textAlign: "left", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", color: "var(--color-text-muted)" }}>
                Piece
              </th>
              <th style={{ padding: "0.25rem 0.5rem", textAlign: "right", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", color: "var(--color-text-muted)" }}>
                Commits
              </th>
              <th style={{ padding: "0.25rem 0.5rem", textAlign: "right", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", color: "var(--color-text-muted)" }}>
                Points
              </th>
              <th style={{ padding: "0.25rem 0.5rem", textAlign: "right", fontWeight: 600, fontSize: "0.7rem", textTransform: "uppercase", color: "var(--color-text-muted)" }}>
                %
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((e) => {
              const ptPct = totalPoints > 0 ? Math.round((e.totalPoints / totalPoints) * 100) : 0;
              const isCritical = CRITICAL_PIECES.has(e.chessPiece);
              return (
                <tr
                  key={e.chessPiece}
                  data-testid={`chess-row-${e.chessPiece.toLowerCase()}`}
                  style={{
                    borderBottom: "1px solid var(--color-border)",
                    background: isCritical && e.totalPoints > 0 ? "#faf5ff" : "transparent",
                    opacity: e.commitCount === 0 ? 0.4 : 1,
                  }}
                >
                  <td style={{ padding: "0.375rem 0.5rem" }}>
                    <span style={{ display: "flex", alignItems: "center", gap: "0.375rem" }}>
                      <span
                        style={{
                          display: "inline-block",
                          width: "10px",
                          height: "10px",
                          borderRadius: "2px",
                          background: PIECE_COLORS[e.chessPiece],
                          flexShrink: 0,
                        }}
                      />
                      <span style={{ fontSize: "0.9rem" }}>{CHESS_PIECE_ICONS[e.chessPiece]}</span>
                      <span style={{ fontWeight: isCritical ? 700 : 400 }}>{CHESS_PIECE_LABELS[e.chessPiece]}</span>
                      {isCritical && e.totalPoints > 0 && (
                        <span
                          style={{
                            fontSize: "0.6rem",
                            padding: "1px 5px",
                            borderRadius: "999px",
                            background: "#ede9fe",
                            color: "#5b21b6",
                            fontWeight: 700,
                          }}
                        >
                          CRITICAL
                        </span>
                      )}
                    </span>
                  </td>
                  <td style={{ padding: "0.375rem 0.5rem", textAlign: "right" }} data-testid={`chess-commits-${e.chessPiece.toLowerCase()}`}>
                    {e.commitCount > 0 ? e.commitCount : <span style={{ color: "var(--color-text-muted)" }}>0</span>}
                  </td>
                  <td style={{ padding: "0.375rem 0.5rem", textAlign: "right", fontWeight: 600 }} data-testid={`chess-points-${e.chessPiece.toLowerCase()}`}>
                    {e.totalPoints > 0 ? e.totalPoints : <span style={{ color: "var(--color-text-muted)" }}>0</span>}
                  </td>
                  <td style={{ padding: "0.375rem 0.5rem", textAlign: "right", color: "var(--color-text-muted)" }} data-testid={`chess-pct-${e.chessPiece.toLowerCase()}`}>
                    {ptPct > 0 ? `${ptPct}%` : "—"}
                  </td>
                </tr>
              );
            })}
          </tbody>
          {totalCommits > 0 && (
            <tfoot>
              <tr style={{ borderTop: "2px solid var(--color-border)", fontWeight: 700 }}>
                <td style={{ padding: "0.375rem 0.5rem" }}>Total</td>
                <td style={{ padding: "0.375rem 0.5rem", textAlign: "right" }} data-testid="chess-total-commits">{totalCommits}</td>
                <td style={{ padding: "0.375rem 0.5rem", textAlign: "right" }} data-testid="chess-total-points">{totalPoints}</td>
                <td style={{ padding: "0.375rem 0.5rem", textAlign: "right" }}>100%</td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </section>
  );
}
