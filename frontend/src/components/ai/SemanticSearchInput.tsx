/**
 * SemanticSearchInput — text input + submit that executes a RAG query
 * and renders the answer in a QueryAnswerCard below the input.
 */
import { useCallback, useRef, useState } from "react";
import { Bot, RotateCcw, Search } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Input } from "../ui/Input.js";
import { cn } from "../../lib/utils.js";
import { useSemanticQuery } from "../../api/ragHooks.js";
import { QueryAnswerCard } from "./QueryAnswerCard.js";

interface SemanticSearchInputProps {
  teamId?: string;
  userId?: string;
  className?: string;
}

export function SemanticSearchInput({
  teamId,
  userId,
  className,
}: SemanticSearchInputProps) {
  const [question, setQuestion] = useState("");
  const { mutate, data, loading, error } = useSemanticQuery();
  const inputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const q = question.trim();
      if (!q) return;
      await mutate(q, teamId, userId);
    },
    [question, mutate, teamId, userId],
  );

  const handleRetry = useCallback(() => {
    const q = question.trim();
    if (!q) return;
    void mutate(q, teamId, userId);
  }, [question, mutate, teamId, userId]);

  return (
    <div className={cn("flex flex-col gap-4", className)}>
      {/* Header */}
      <div className="flex items-center gap-2">
        <Bot className="h-4 w-4 text-primary shrink-0" aria-hidden="true" />
        <span className="text-sm font-semibold text-foreground">
          Ask AI about your team&apos;s work
        </span>
      </div>

      {/* Search form */}
      <form onSubmit={(e) => void handleSubmit(e)} className="flex gap-2">
        <Input
          ref={inputRef}
          data-testid="semantic-search-input"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="e.g. What did the team commit to last week?"
          icon={<Search className="h-3.5 w-3.5" />}
          disabled={loading}
          className="flex-1"
          aria-label="Ask a question about your team's work history"
        />
        <Button
          type="submit"
          variant="primary"
          size="md"
          disabled={loading || question.trim().length === 0}
          data-testid="semantic-search-submit"
          aria-label="Submit question"
        >
          {loading ? (
            <span className="inline-flex items-center gap-1.5">
              <span
                className="h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin"
                aria-hidden="true"
              />
              Searching…
            </span>
          ) : (
            <>
              <Search className="h-3.5 w-3.5" />
              Search
            </>
          )}
        </Button>
      </form>

      {/* Loading state */}
      {loading && (
        <div
          className="flex items-center gap-2 text-sm text-muted animate-pulse"
          data-testid="semantic-search-loading"
          role="status"
          aria-live="polite"
        >
          <Bot className="h-4 w-4 shrink-0" aria-hidden="true" />
          Searching your team&apos;s planning history…
        </div>
      )}

      {/* Error state */}
      {!loading && error && (
        <div
          className="flex items-center justify-between gap-2 rounded-default border border-danger/30 bg-danger/5 px-3 py-2.5"
          data-testid="semantic-search-error"
          role="alert"
        >
          <span className="text-sm text-danger">{error}</span>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRetry}
            data-testid="semantic-search-retry"
            aria-label="Retry search"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            Retry
          </Button>
        </div>
      )}

      {/* Answer card */}
      {!loading && !error && data?.aiAvailable && data.answer && (
        <QueryAnswerCard
          answer={data.answer}
          sources={data.sources ?? []}
          confidence={data.confidence ?? 0}
          suggestionId={data.suggestionId ?? ""}
          data-testid="semantic-search-answer"
        />
      )}

      {/* AI unavailable */}
      {!loading && !error && data && !data.aiAvailable && (
        <p
          className="text-sm text-muted italic"
          data-testid="semantic-search-unavailable"
        >
          AI search is currently unavailable. Try again later.
        </p>
      )}
    </div>
  );
}
