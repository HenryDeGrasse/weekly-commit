/**
 * ConfidenceBadge — renders a calibration/evidence confidence tier
 * as a colored badge with an accessible CSS tooltip.
 *
 * Tier → color mapping:
 *  HIGH        → green (success variant)
 *  MEDIUM      → yellow (warning variant)
 *  LOW         → orange/red (danger variant)
 *  INSUFFICIENT → gray (draft variant)
 */
import { Badge } from "../ui/Badge.js";
import { Tooltip } from "../ui/Tooltip.js";
import type { BadgeProps } from "../ui/Badge.js";

// ── Tier mappings ─────────────────────────────────────────────────────────────

type ConfidenceTier = "HIGH" | "MEDIUM" | "LOW" | "INSUFFICIENT";

const TIER_VARIANT: Record<ConfidenceTier, BadgeProps["variant"]> = {
  HIGH: "success",
  MEDIUM: "warning",
  LOW: "danger",
  INSUFFICIENT: "draft",
};

const TIER_TOOLTIP: Record<ConfidenceTier, string> = {
  HIGH: "High confidence — 12+ weeks of data",
  MEDIUM: "Medium confidence — 8–11 weeks of data",
  LOW: "Low confidence — fewer than 8 weeks of data",
  INSUFFICIENT: "Insufficient data — needs 8+ weeks to calibrate",
};

const TIER_LABEL: Record<ConfidenceTier, string> = {
  HIGH: "High",
  MEDIUM: "Medium",
  LOW: "Low",
  INSUFFICIENT: "Insufficient data",
};

// ── Component ─────────────────────────────────────────────────────────────────

interface ConfidenceBadgeProps {
  /** Confidence tier string from the backend: HIGH | MEDIUM | LOW | INSUFFICIENT. */
  tier: string;
  className?: string;
}

export function ConfidenceBadge({ tier, className }: ConfidenceBadgeProps) {
  const normTier = (tier?.toUpperCase() as ConfidenceTier) ?? "INSUFFICIENT";
  const variant = TIER_VARIANT[normTier] ?? "draft";
  const tooltip = TIER_TOOLTIP[normTier] ?? tier;
  const label = TIER_LABEL[normTier] ?? tier;

  return (
    <Tooltip content={tooltip} side="top">
      <Badge
        variant={variant}
        className={className}
        data-testid="confidence-badge"
        data-tier={normTier}
      >
        {label}
      </Badge>
    </Tooltip>
  );
}
