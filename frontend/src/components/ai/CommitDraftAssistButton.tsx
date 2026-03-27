/**
 * CommitDraftAssistButton — "✨ AI Suggest" button that calls the existing
 * CommitDraftAssistService backend and renders inline suggestions.
 *
 * SOTA note: Completes the wiring of an existing backend capability to the
 * frontend (ai-eval-roadmap Phase 3a). Every suggestion shows rationale and
 * is accept/dismiss-able per PRD §17 ("assistive, not authoritarian").
 */
import { useState, useCallback } from "react";
import { Sparkles, Check, Loader2 } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { cn } from "../../lib/utils.js";
import { useAiApi, useAiStatus } from "../../api/aiHooks.js";
import { useHostBridge } from "../../host/HostProvider.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import type { CommitDraftAssistResponse } from "../../api/aiApi.js";

interface CommitDraftAssistButtonProps {
  planId: string;
  commitId?: string | undefined;
  currentTitle: string;
  currentDescription?: string | undefined;
  currentSuccessCriteria?: string | undefined;
  currentEstimatePoints?: number | undefined;
  chessPiece?: string | undefined;
  onAcceptTitle?: ((title: string) => void) | undefined;
  onAcceptDescription?: ((description: string) => void) | undefined;
  onAcceptSuccessCriteria?: ((criteria: string) => void) | undefined;
  onAcceptEstimatePoints?: ((points: number) => void) | undefined;
  className?: string | undefined;
}

interface SuggestionField {
  label: string;
  current: string;
  suggested: string;
  onAccept: () => void;
}

export function CommitDraftAssistButton({
  planId,
  commitId,
  currentTitle,
  currentDescription,
  currentSuccessCriteria,
  currentEstimatePoints,
  chessPiece,
  onAcceptTitle,
  onAcceptDescription,
  onAcceptSuccessCriteria,
  onAcceptEstimatePoints,
  className,
}: CommitDraftAssistButtonProps) {
  const aiApi = useAiApi();
  const bridge = useHostBridge();
  const userId = bridge.context.authenticatedUser.id;
  const { data: aiStatus } = useAiStatus();
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<CommitDraftAssistResponse | null>(null);
  const [accepted, setAccepted] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  const isAvailable = aiStatus?.available ?? false;

  const handleRequest = useCallback(async () => {
    setLoading(true);
    setError(null);
    setResult(null);
    setAccepted(new Set());
    try {
      const response = await aiApi.commitDraftAssist({
        userId,
        planId,
        commitId,
        currentTitle,
        currentDescription,
        currentSuccessCriteria,
        currentEstimatePoints,
        chessPiece,
      });
      if (!response.aiAvailable) {
        setError("AI is currently unavailable. Try again later.");
        return;
      }
      setResult(response);
    } catch {
      setError("Failed to get AI suggestions.");
    } finally {
      setLoading(false);
    }
  }, [
    aiApi, userId, planId, commitId, currentTitle,
    currentDescription, currentSuccessCriteria, currentEstimatePoints, chessPiece,
  ]);

  const handleAccept = useCallback((field: string, acceptFn: () => void) => {
    acceptFn();
    setAccepted((prev) => new Set(prev).add(field));
  }, []);

  // Build suggestion fields from the response
  const suggestions: SuggestionField[] = [];
  if (result) {
    if (result.suggestedTitle && onAcceptTitle) {
      suggestions.push({
        label: "Title",
        current: currentTitle,
        suggested: result.suggestedTitle,
        onAccept: () => onAcceptTitle(result.suggestedTitle!),
      });
    }
    if (result.suggestedDescription && onAcceptDescription) {
      suggestions.push({
        label: "Description",
        current: currentDescription ?? "",
        suggested: result.suggestedDescription,
        onAccept: () => onAcceptDescription(result.suggestedDescription!),
      });
    }
    if (result.suggestedSuccessCriteria && onAcceptSuccessCriteria) {
      suggestions.push({
        label: "Success Criteria",
        current: currentSuccessCriteria ?? "",
        suggested: result.suggestedSuccessCriteria,
        onAccept: () => onAcceptSuccessCriteria(result.suggestedSuccessCriteria!),
      });
    }
    if (result.suggestedEstimatePoints != null && onAcceptEstimatePoints) {
      suggestions.push({
        label: "Estimate",
        current: currentEstimatePoints != null ? `${currentEstimatePoints} pts` : "not set",
        suggested: `${result.suggestedEstimatePoints} pts`,
        onAccept: () => onAcceptEstimatePoints(result.suggestedEstimatePoints!),
      });
    }
  }

  const allGood = result && suggestions.length === 0;

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      {/* Trigger button */}
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => void handleRequest()}
        disabled={loading || !isAvailable || !currentTitle.trim()}
        data-testid="ai-draft-assist-btn"
        className="self-start text-primary hover:text-primary/80"
        title={!isAvailable ? "AI unavailable" : "Get AI suggestions for this commit"}
      >
        {loading ? (
          <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
        ) : (
          <Sparkles className="h-3.5 w-3.5 mr-1" />
        )}
        {loading ? "Thinking…" : "AI Suggest"}
      </Button>

      {/* Error */}
      {error && (
        <p className="text-xs text-danger" data-testid="ai-draft-assist-error">{error}</p>
      )}

      {/* All good */}
      {allGood && (
        <div
          className="flex items-center gap-1.5 text-xs text-foreground bg-foreground/5 rounded-default px-2.5 py-1.5"
          data-testid="ai-draft-assist-good"
        >
          <Check className="h-3.5 w-3.5" />
          Looks good! No suggestions.
          {result.suggestionId && (
            <AiFeedbackButtons suggestionId={result.suggestionId} className="ml-auto" />
          )}
        </div>
      )}

      {/* Suggestions */}
      {suggestions.length > 0 && (
        <div
          className="rounded-default border border-primary/20 bg-primary/5 p-3 flex flex-col gap-2.5"
          data-testid="ai-draft-assist-suggestions"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-primary flex items-center gap-1">
              <Sparkles className="h-3 w-3" /> AI Suggestions
            </span>
            {result?.suggestionId && (
              <AiFeedbackButtons suggestionId={result.suggestionId} />
            )}
          </div>

          {suggestions.map((s) => {
            const isAccepted = accepted.has(s.label);
            return (
              <div
                key={s.label}
                className={cn(
                  "rounded-sm border px-2.5 py-2 text-xs",
                  isAccepted
                    ? "border-foreground/20 bg-foreground/5"
                    : "border-neutral-200 bg-white",
                )}
                data-testid={`ai-suggestion-${s.label.toLowerCase().replace(/\s/g, "-")}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1">
                    <Badge variant="default" className="mb-1 text-[0.6rem]">
                      {s.label}
                    </Badge>
                    {s.current && (
                      <p className="m-0 text-muted line-through">{s.current}</p>
                    )}
                    <p className="m-0 font-medium text-foreground">{s.suggested}</p>
                  </div>
                  {!isAccepted && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => handleAccept(s.label, s.onAccept)}
                      className="h-6 w-6 text-foreground hover:bg-neutral-100 shrink-0"
                      aria-label={`Accept ${s.label} suggestion`}
                      data-testid={`ai-accept-${s.label.toLowerCase().replace(/\s/g, "-")}`}
                    >
                      <Check className="h-3.5 w-3.5" />
                    </Button>
                  )}
                  {isAccepted && (
                    <span className="text-[0.6rem] text-foreground font-semibold">✓ Applied</span>
                  )}
                </div>
              </div>
            );
          })}

          {result?.rationale && (
            <p className="m-0 text-[0.65rem] text-muted italic" data-testid="ai-draft-assist-rationale">
              {result.rationale}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
