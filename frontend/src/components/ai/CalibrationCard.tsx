/**
 * CalibrationCard — displays the user's rolling calibration profile.
 *
 * Shows:
 * - Overall achievement rate as a progress bar
 * - Per-chess-piece achievement rates as progress bars
 * - Carry-forward probability
 * - Confidence tier badge (via ConfidenceBadge)
 * - "Based on N weeks of data" messaging
 * - "Not enough data yet — needs 8+ weeks" for INSUFFICIENT tier
 */
import { TrendingUp } from "lucide-react";
import { Card, CardHeader, CardTitle, CardContent } from "../ui/Card.js";
import { Badge } from "../ui/Badge.js";
import { Skeleton } from "../ui/Skeleton.js";
import { ConfidenceBadge } from "./ConfidenceBadge.js";
import type { CalibrationProfile } from "../../api/calibrationApi.js";
import { cn } from "../../lib/utils.js";

// ── Chess-piece display helpers ───────────────────────────────────────────────

const PIECE_GLYPHS: Record<string, string> = {
  KING: "♔",
  QUEEN: "♕",
  ROOK: "♜",
  BISHOP: "♗",
  KNIGHT: "♞",
  PAWN: "♙",
};

function pieceLabel(piece: string): string {
  const glyph = PIECE_GLYPHS[piece.toUpperCase()] ?? "";
  return glyph ? `${glyph} ${piece}` : piece;
}

// ── Progress bar ──────────────────────────────────────────────────────────────

function RateBar({
  label,
  rate,
  testId,
}: {
  label: string;
  rate: number;
  testId?: string;
}) {
  const pct = Math.round(Math.max(0, Math.min(1, rate)) * 100);
  return (
    <div className="flex flex-col gap-0.5" data-testid={testId}>
      <div className="flex items-center justify-between text-xs">
        <span className="text-muted">{label}</span>
        <span className="tabular-nums font-semibold">{pct}%</span>
      </div>
      <div className="h-1.5 w-full bg-border overflow-hidden rounded-sm">
        <div
          className="h-full bg-foreground/60 transition-all"
          style={{ width: `${pct}%` }}
          data-testid={testId ? `${testId}-bar` : undefined}
        />
      </div>
    </div>
  );
}

// ── Skeleton ──────────────────────────────────────────────────────────────────

function CalibrationCardSkeleton() {
  return (
    <Card data-testid="calibration-card-skeleton">
      <CardHeader>
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-5 w-16" />
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-3 w-40" />
      </CardContent>
    </Card>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

interface CalibrationCardProps {
  profile: CalibrationProfile;
  /** When {@code true}, renders a skeleton placeholder instead of data. */
  loading?: boolean;
  className?: string;
}

export function CalibrationCard({
  profile,
  loading = false,
  className,
}: CalibrationCardProps) {
  if (loading) return <CalibrationCardSkeleton />;

  const isInsufficient =
    !profile.available || profile.confidenceTier === "INSUFFICIENT";

  return (
    <Card className={cn("w-full", className)} data-testid="calibration-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <TrendingUp className="h-4 w-4 text-primary" aria-hidden="true" />
          Your Calibration Profile
        </CardTitle>
        <ConfidenceBadge tier={profile.confidenceTier} />
      </CardHeader>

      <CardContent className="flex flex-col gap-3">
        {isInsufficient ? (
          <p
            className="text-sm text-muted"
            data-testid="calibration-insufficient-msg"
          >
            Not enough data yet — needs 8+ weeks to calibrate.
          </p>
        ) : (
          <>
            {/* Overall achievement rate */}
            <RateBar
              label="Overall achievement rate"
              rate={profile.overallAchievementRate}
              testId="calibration-overall-rate"
            />

            {/* Per-chess-piece rates */}
            {Object.entries(profile.chessPieceAchievementRates).map(
              ([piece, rate]) => (
                <RateBar
                  key={piece}
                  label={pieceLabel(piece)}
                  rate={rate}
                  testId={`calibration-piece-rate-${piece.toLowerCase()}`}
                />
              ),
            )}

            {/* Carry-forward probability */}
            <div
              className="flex items-center justify-between text-xs"
              data-testid="calibration-carry-forward"
            >
              <span className="text-muted">Carry-forward probability</span>
              <Badge variant="default" className="text-[0.6rem]">
                {Math.round(profile.carryForwardProbability * 100)}%
              </Badge>
            </div>
          </>
        )}

        {/* Data basis */}
        <p
          className="text-[0.65rem] text-muted mt-1"
          data-testid="calibration-weeks-label"
        >
          Based on {profile.weeksOfData}{" "}
          {profile.weeksOfData === 1 ? "week" : "weeks"} of data
        </p>
      </CardContent>
    </Card>
  );
}
