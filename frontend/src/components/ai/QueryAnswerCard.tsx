/**
 * QueryAnswerCard — displays a RAG-generated answer with rich formatting,
 * source citations, a confidence indicator, and AI feedback buttons.
 */
import { useState } from "react";
import {
  ChevronDown,
  ChevronRight,
  ExternalLink,
  FileText,
  Ticket,
  ArrowRightLeft,
  MessageSquare,
  Bot,
} from "lucide-react";
import { Badge } from "../ui/Badge.js";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "../ui/Card.js";
import { cn } from "../../lib/utils.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import { AnswerRenderer } from "./AnswerRenderer.js";
import { ConfidenceBadge } from "./ConfidenceBadge.js";
import type { RagSource } from "../../api/ragApi.js";

// ── Confidence indicator ──────────────────────────────────────────────────────

function confidenceLabel(confidence: number): string {
  if (confidence >= 0.8) return "High";
  if (confidence >= 0.5) return "Medium";
  return "Low";
}

function confidenceCls(confidence: number): string {
  if (confidence >= 0.8) return "text-foreground font-bold";
  if (confidence >= 0.5) return "text-muted";
  return "text-foreground underline";
}

function confidenceBarCls(confidence: number): string {
  if (confidence >= 0.8) return "bg-foreground";
  if (confidence >= 0.5) return "bg-foreground/40";
  return "bg-foreground/20";
}

// ── Source icon ───────────────────────────────────────────────────────────────

function sourceIcon(entityType: string) {
  switch (entityType) {
    case "commit":
    case "plan_summary":
      return FileText;
    case "ticket":
      return Ticket;
    case "scope_change":
      return ArrowRightLeft;
    case "carry_forward":
      return ArrowRightLeft;
    case "manager_comment":
      return MessageSquare;
    default:
      return FileText;
  }
}

// ── Source citation ───────────────────────────────────────────────────────────

function SourceCitation({ source }: { source: RagSource }) {
  const { entityType, entityId, weekStartDate, snippet } = source;
  const Icon = sourceIcon(entityType);

  let link: string | null = null;
  if (entityType === "ticket") link = "/tickets";

  const label =
    entityType === "carry_forward"
      ? "carry-forward"
      : entityType.replace(/_/g, " ");

  return (
    <li
      className="flex items-start gap-2 text-xs rounded-default border border-border/50 bg-surface-raised px-2.5 py-2"
      data-testid={`rag-source-${entityId}`}
    >
      <Icon className="h-3.5 w-3.5 mt-0.5 shrink-0 text-muted" aria-hidden="true" />
      <span className="flex-1 min-w-0">
        <span className="flex items-center gap-1.5 flex-wrap">
          <Badge variant="default" className="text-[0.6rem] capitalize">
            {label}
          </Badge>
          {weekStartDate && (
            <span className="text-[0.65rem] text-muted tabular-nums">w/{weekStartDate}</span>
          )}
        </span>
        {snippet && (
          <span className="block text-[0.65rem] text-muted mt-1 leading-relaxed">
            &ldquo;{snippet}&rdquo;
          </span>
        )}
        {link && (
          <a
            href={link}
            className="inline-flex items-center gap-0.5 text-primary hover:underline text-[0.65rem] mt-0.5"
            target="_blank"
            rel="noreferrer"
            data-testid={`rag-source-link-${entityId}`}
          >
            view <ExternalLink className="h-2.5 w-2.5" />
          </a>
        )}
      </span>
    </li>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

interface QueryAnswerCardProps {
  answer: string;
  sources: RagSource[];
  confidence: number;
  suggestionId: string;
  className?: string;
  /**
   * When true the card renders in streaming mode: answer text is shown as it
   * arrives (incremental), a typing indicator is shown while streaming, and
   * sources / confidence are hidden until the stream completes.
   * Defaults to false (batch mode) for backward compatibility.
   */
  streaming?: boolean;
  /** True while a stream is still open (only meaningful when streaming=true). */
  isStreaming?: boolean;
  /**
   * Optional evidence-quality tier from the backend (HIGH | MEDIUM | LOW | INSUFFICIENT).
   * When present, renders a {@link ConfidenceBadge} in place of the numeric confidence
   * indicator. When absent, the existing numeric display is used unchanged.
   */
  confidenceTier?: string;
}

export function QueryAnswerCard({
  answer,
  sources,
  confidence,
  suggestionId,
  className,
  streaming = false,
  isStreaming = false,
  confidenceTier,
}: QueryAnswerCardProps) {
  const pct = Math.round(Math.max(0, Math.min(1, confidence)) * 100);
  const [sourcesExpanded, setSourcesExpanded] = useState(false);

  return (
    <Card className={cn("w-full", className)} data-testid="query-answer-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Bot className="h-4 w-4 text-primary" />
          AI Answer
        </CardTitle>
        {/* Confidence indicator */}
        <div
          className="flex items-center gap-1.5"
          data-testid="confidence-indicator"
        >
          {confidenceTier ? (
            <ConfidenceBadge tier={confidenceTier} />
          ) : (
            <>
              <span
                className={cn(
                  "text-xs font-semibold",
                  confidenceCls(confidence),
                )}
              >
                {confidenceLabel(confidence)}
              </span>
              <div
                className="h-1.5 w-16 bg-border overflow-hidden"
                aria-label={`Confidence: ${pct}%`}
                title={`Confidence: ${pct}%`}
              >
                <div
                  className={cn("h-full", confidenceBarCls(confidence))}
                  style={{ width: `${pct}%` }}
                  data-testid="confidence-bar"
                />
              </div>
              <span className="text-[0.65rem] text-muted tabular-nums">{pct}%</span>
            </>
          )}
        </div>
      </CardHeader>

      <CardContent className="flex flex-col gap-3">
        {/* Rich answer text */}
        <AnswerRenderer text={answer} />

        {/* Typing indicator — shown only in streaming mode while still receiving tokens */}
        {streaming && isStreaming && (
          <span
            className="inline-flex items-center gap-1 text-xs text-muted"
            data-testid="streaming-typing-indicator"
            aria-label="Streaming…"
          >
            <span className="h-1.5 w-1.5 rounded-full bg-primary animate-bounce [animation-delay:0ms]" />
            <span className="h-1.5 w-1.5 rounded-full bg-primary animate-bounce [animation-delay:150ms]" />
            <span className="h-1.5 w-1.5 rounded-full bg-primary animate-bounce [animation-delay:300ms]" />
          </span>
        )}

        {/* Source citations — collapsible */}
        {sources.length > 0 && (
          <div data-testid="rag-sources-section">
            <button
              type="button"
              onClick={() => setSourcesExpanded((v) => !v)}
              className="flex items-center gap-1 text-[0.65rem] font-semibold text-muted uppercase tracking-wider hover:text-foreground transition-colors cursor-pointer bg-transparent border-none p-0"
              aria-expanded={sourcesExpanded}
              data-testid="rag-sources-toggle"
            >
              {sourcesExpanded ? (
                <ChevronDown className="h-3 w-3" />
              ) : (
                <ChevronRight className="h-3 w-3" />
              )}
              Sources ({sources.length})
            </button>
            {sourcesExpanded && (
              <ul className="flex flex-col gap-1.5 list-none m-0 p-0 mt-2">
                {sources.map((s) => (
                  <SourceCitation key={`${s.entityType}-${s.entityId}`} source={s} />
                ))}
              </ul>
            )}
          </div>
        )}
      </CardContent>

      <CardFooter className="justify-between">
        <span className="text-[0.65rem] text-muted">
          AI-generated answer — verify before acting
        </span>
        {!streaming && suggestionId ? (
          <AiFeedbackButtons suggestionId={suggestionId} />
        ) : null}
      </CardFooter>
    </Card>
  );
}
