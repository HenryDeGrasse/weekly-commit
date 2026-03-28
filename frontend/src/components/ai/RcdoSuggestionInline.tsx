/**
 * RcdoSuggestionInline — shows an AI-suggested RCDO link below the RCDO picker
 * in CommitForm. Fires the existing `/api/ai/rcdo-suggest` endpoint when the
 * user has typed enough title text, and presents the suggestion with rationale
 * + confidence + accept/dismiss controls.
 *
 * PRD §17: assistive, visible rationale, auditable, never auto-links.
 */
import { useState, useEffect, useRef, useCallback } from "react";
import { Check, X, Loader2 } from "lucide-react";
import { Button } from "../ui/Button.js";
import { cn } from "../../lib/utils.js";
import { useAiApi, useAiStatus } from "../../api/aiHooks.js";
import { useHostBridge } from "../../host/HostProvider.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import { AiSuggestedBadge } from "./AiSuggestedBadge.js";
import type { RcdoSuggestResponse } from "../../api/aiApi.js";

interface RcdoSuggestionInlineProps {
  planId: string;
  /** Current commit title — used for semantic matching. */
  currentTitle: string;
  /** Current commit description (optional extra context). */
  currentDescription?: string | undefined;
  /** Current chess piece (optional extra context). */
  chessPiece?: string | undefined;
  /** Currently selected RCDO node id (skip suggestion if already set). */
  currentRcdoNodeId: string;
  /** Called when user accepts the suggestion. */
  onAccept: (rcdoNodeId: string) => void;
  className?: string | undefined;
}

const MIN_TITLE_LENGTH = 8;
const DEBOUNCE_MS = 1200;

export function RcdoSuggestionInline({
  planId,
  currentTitle,
  currentDescription,
  chessPiece,
  currentRcdoNodeId,
  onAccept,
  className,
}: RcdoSuggestionInlineProps) {
  const aiApi = useAiApi();
  const bridge = useHostBridge();
  const userId = bridge.context.authenticatedUser.id;
  const { data: aiStatus } = useAiStatus();

  const [result, setResult] = useState<RcdoSuggestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [accepted, setAccepted] = useState(false);

  const isAvailable = aiStatus?.available ?? false;

  // Debounce timer ref
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Track what we last fetched for to avoid duplicate requests
  const lastFetchedRef = useRef<string>("");

  // Reset dismissed/accepted state when the title meaningfully changes
  const titleKeyRef = useRef(currentTitle);
  useEffect(() => {
    if (currentTitle !== titleKeyRef.current) {
      titleKeyRef.current = currentTitle;
      setDismissed(false);
      setAccepted(false);
    }
  }, [currentTitle]);

  // Debounced fetch
  useEffect(() => {
    // Don't suggest if AI unavailable, title too short, user already picked an RCDO,
    // or we've been dismissed
    if (
      !isAvailable ||
      currentTitle.trim().length < MIN_TITLE_LENGTH ||
      currentRcdoNodeId ||
      dismissed
    ) {
      return;
    }

    const key = `${currentTitle.trim()}|${chessPiece ?? ""}`;
    if (key === lastFetchedRef.current) return;

    if (timerRef.current) clearTimeout(timerRef.current);

    timerRef.current = setTimeout(() => {
      lastFetchedRef.current = key;
      setLoading(true);
      aiApi
        .rcdoSuggest({
          planId,
          userId,
          title: currentTitle.trim(),
          description: currentDescription,
          chessPiece,
        })
        .then((response) => {
          setResult(response);
          setLoading(false);
        })
        .catch(() => {
          setResult(null);
          setLoading(false);
        });
    }, DEBOUNCE_MS);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTitle, currentRcdoNodeId, dismissed, isAvailable, chessPiece]);

  const handleAccept = useCallback(() => {
    if (!result?.suggestedRcdoNodeId) return;
    onAccept(result.suggestedRcdoNodeId);
    setAccepted(true);
  }, [result, onAccept]);

  const handleDismiss = useCallback(() => {
    setDismissed(true);
    setResult(null);
  }, []);

  // Don't render anything if AI is unavailable, nothing to show, or user already
  // has an RCDO selected
  if (!isAvailable || currentRcdoNodeId || dismissed || accepted) return null;

  if (loading) {
    return (
      <div
        className={cn("flex items-center gap-2 text-xs text-muted animate-pulse mt-1.5", className)}
        data-testid="rcdo-suggestion-loading"
      >
        <Loader2 className="h-3 w-3 animate-spin" />
        Finding best RCDO match…
      </div>
    );
  }

  if (!result?.aiAvailable || !result?.suggestionAvailable || !result?.suggestedRcdoNodeId) {
    return null;
  }

  return (
    <div
      className={cn(
        "flex items-start gap-2 rounded-default border border-primary/20 bg-primary/5 px-3 py-2 mt-1.5",
        className,
      )}
      data-testid="rcdo-suggestion-inline"
    >
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5 flex-wrap">
          <AiSuggestedBadge />
          <span className="text-sm font-semibold text-foreground" data-testid="rcdo-suggestion-title">
            {result.rcdoTitle ?? result.suggestedRcdoNodeId.slice(0, 8) + "…"}
          </span>
          {result.confidence != null && (
            <span className="text-[0.65rem] text-muted" data-testid="rcdo-suggestion-confidence">
              {Math.round(result.confidence * 100)}%
            </span>
          )}
        </div>
        {result.rationale && (
          <p className="m-0 mt-0.5 text-xs text-muted" data-testid="rcdo-suggestion-rationale">
            {result.rationale}
          </p>
        )}
      </div>
      <div className="flex items-center gap-1 shrink-0">
        {result.suggestionId && <AiFeedbackButtons suggestionId={result.suggestionId} />}
        <Button
          variant="ghost"
          size="icon"
          type="button"
          onClick={handleAccept}
          className="h-6 w-6 text-primary hover:bg-primary/10"
          aria-label="Accept RCDO suggestion"
          data-testid="rcdo-suggestion-accept"
        >
          <Check className="h-3.5 w-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          type="button"
          onClick={handleDismiss}
          className="h-6 w-6 text-muted hover:bg-neutral-100"
          aria-label="Dismiss RCDO suggestion"
          data-testid="rcdo-suggestion-dismiss"
        >
          <X className="h-3 w-3" />
        </Button>
      </div>
    </div>
  );
}
