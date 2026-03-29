/**
 * SemanticSearchInput — text input + submit that executes a RAG query
 * and renders the answer in a QueryAnswerCard below the input.
 */
import { useCallback, useRef, useState } from "react";
import { Bot, RotateCcw, Search, MessageCircle } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Input } from "../ui/Input.js";

import { cn } from "../../lib/utils.js";
import { useStreamingRagQuery } from "../../api/ragStreamHooks.js";
import { QueryAnswerCard } from "./QueryAnswerCard.js";

const SUGGESTED_QUESTIONS = [
  "What did the team commit to last week?",
  "Which RCDOs received the most effort this month?",
  "What are the recurring carry-forward patterns?",
  "Which team members are overcommitting?",
  "What KING/QUEEN work was achieved last week?",
] as const;

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
  const {
    answer,
    sources,
    confidence,
    isStreaming,
    error,
    startStream,
    cancel,
  } = useStreamingRagQuery(teamId ?? "", userId ?? "");

  // Treat "has received any answer content" as equivalent to batch data availability
  const hasAnswer = answer.length > 0;
  const loading = isStreaming && !hasAnswer;
  const inputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      const q = question.trim();
      if (!q) return;
      startStream(q);
    },
    [question, startStream],
  );

  const handleRetry = useCallback(() => {
    const q = question.trim();
    if (!q) return;
    cancel();
    startStream(q);
  }, [question, startStream, cancel]);

  return (
    <div className={cn("rounded-default border border-border bg-surface p-4 flex flex-col gap-3", className)}>
      {/* Header + form on one line */}
      <div className="flex items-center gap-2">
        <Bot className="h-4 w-4 text-primary shrink-0" aria-hidden="true" />
        <span className="text-sm font-semibold text-foreground whitespace-nowrap">
          Ask AI about your team&apos;s work
        </span>
      </div>

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

      {/* Suggested questions — shown when input is empty and no result */}
      {!question.trim() && !hasAnswer && !loading && !error && (
        <div className="flex flex-wrap gap-1.5" data-testid="suggested-questions">
          <span className="text-[0.65rem] text-muted flex items-center gap-1 mr-1">
            <MessageCircle className="h-3 w-3" /> Try asking:
          </span>
          {SUGGESTED_QUESTIONS.map((q) => (
            <button
              key={q}
              type="button"
              onClick={() => {
                setQuestion(q);
                startStream(q);
              }}
              className="inline-flex items-center rounded-full border border-primary/20 bg-primary/5 px-2.5 py-1 text-[0.65rem] text-primary hover:bg-primary/10 hover:border-primary/40 transition-colors cursor-pointer"
              data-testid={`suggested-q-${q.slice(0, 20).replace(/\s/g, "-").toLowerCase()}`}
            >
              {q}
            </button>
          ))}
        </div>
      )}

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

      {/* Answer card — streaming mode: shown as soon as any content arrives */}
      {(hasAnswer || isStreaming) && !error && (
        <QueryAnswerCard
          answer={answer}
          sources={sources}
          confidence={confidence}
          suggestionId=""
          streaming={true}
          isStreaming={isStreaming}
          data-testid="semantic-search-answer"
        />
      )}

      {/* AI unavailable — the streaming error callback covers this case via the
          error state rendered above. No additional block needed. */}
    </div>
  );
}
