/**
 * Thumbs up / thumbs down feedback buttons for AI suggestions.
 * Records feedback via POST /api/ai/feedback.
 */
import { useState, useCallback } from "react";
import { ThumbsUp, ThumbsDown } from "lucide-react";
import { Button } from "../ui/Button.js";
import { cn } from "../../lib/utils.js";
import { useAiApi } from "../../api/aiHooks.js";
import { useHostBridge } from "../../host/HostProvider.js";
import type { FeedbackAction } from "../../api/aiApi.js";

interface AiFeedbackButtonsProps {
  suggestionId: string;
  className?: string;
}

export function AiFeedbackButtons({ suggestionId, className }: AiFeedbackButtonsProps) {
  const aiApi = useAiApi();
  const bridge = useHostBridge();
  const userId = bridge.context.authenticatedUser.id;
  const [submitted, setSubmitted] = useState<FeedbackAction | null>(null);

  const handleFeedback = useCallback(
    async (action: FeedbackAction) => {
      try {
        await aiApi.recordFeedback({ suggestionId, userId, action });
        setSubmitted(action);
      } catch {
        // Non-critical — don't block the UI
      }
    },
    [aiApi, suggestionId, userId],
  );

  if (submitted) {
    return (
      <span className={cn("text-xs text-muted italic", className)}>
        {submitted === "ACCEPTED" ? <><ThumbsUp className="h-3 w-3 inline-block" aria-hidden="true" /> Thanks!</> : <><ThumbsDown className="h-3 w-3 inline-block" aria-hidden="true" /> Noted</>}
      </span>
    );
  }

  return (
    <span className={cn("inline-flex gap-1", className)}>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => void handleFeedback("ACCEPTED")}
        aria-label="Helpful suggestion"
        className="h-6 w-6 text-muted hover:text-foreground"
        data-testid="ai-feedback-accept"
      >
        <ThumbsUp className="h-3.5 w-3.5" />
      </Button>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => void handleFeedback("DISMISSED")}
        aria-label="Not helpful"
        className="h-6 w-6 text-muted hover:text-foreground"
        data-testid="ai-feedback-dismiss"
      >
        <ThumbsDown className="h-3.5 w-3.5" />
      </Button>
    </span>
  );
}
