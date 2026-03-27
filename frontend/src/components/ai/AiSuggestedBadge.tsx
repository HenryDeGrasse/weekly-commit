/**
 * AiSuggestedBadge — inline badge indicating an AI-suggested value.
 * Rendered next to outcome selectors (and future fields) that were
 * pre-filled by an AI suggestion, not yet explicitly confirmed by the user.
 */
import { Sparkles } from "lucide-react";
import { cn } from "../../lib/utils.js";

interface AiSuggestedBadgeProps {
  className?: string;
}

export function AiSuggestedBadge({ className }: AiSuggestedBadgeProps) {
  return (
    <span
      data-testid="ai-suggested-badge"
      className={cn(
        "inline-flex items-center gap-1 rounded-sm border border-primary/20 bg-primary/5 px-1.5 py-px text-[0.6rem] font-medium text-primary",
        className,
      )}
      aria-label="AI suggested"
    >
      <Sparkles className="h-2.5 w-2.5" aria-hidden="true" />
      AI suggested
    </span>
  );
}
