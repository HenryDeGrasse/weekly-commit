/**
 * QueryAnswerCard — displays a RAG-generated answer with source citations,
 * a confidence indicator, and AI feedback buttons.
 */
import { ExternalLink, FileText, Ticket } from "lucide-react";
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
import type { RagSource } from "../../api/ragApi.js";

// ── Confidence indicator ──────────────────────────────────────────────────────

function confidenceLabel(confidence: number): string {
  if (confidence >= 0.8) return "High";
  if (confidence >= 0.5) return "Medium";
  return "Low";
}

function confidenceCls(confidence: number): string {
  if (confidence >= 0.8) return "text-success";
  if (confidence >= 0.5) return "text-warning";
  return "text-danger";
}

function confidenceBarCls(confidence: number): string {
  if (confidence >= 0.8) return "bg-success";
  if (confidence >= 0.5) return "bg-warning";
  return "bg-danger";
}

// ── Source citation ───────────────────────────────────────────────────────────

function SourceCitation({ source }: { source: RagSource }) {
  const { entityType, entityId, weekStartDate, snippet } = source;

  let link: string | null = null;
  let Icon = FileText;

  if (entityType === "commit" || entityType === "plan_summary") {
    // No reliable deep-link without planId; show entity info only
    link = null;
  } else if (entityType === "ticket") {
    link = "/tickets";
    Icon = Ticket;
  }

  const label =
    entityType === "carry_forward"
      ? "carry-forward"
      : entityType.replace(/_/g, " ");

  return (
    <li
      className="flex items-start gap-2 text-xs text-muted"
      data-testid={`rag-source-${entityId}`}
    >
      <Icon className="h-3.5 w-3.5 mt-0.5 shrink-0" aria-hidden="true" />
      <span className="flex-1 min-w-0">
        <Badge variant="default" className="text-[0.6rem] mr-1 capitalize">
          {label}
        </Badge>
        {weekStartDate && (
          <span className="text-[0.65rem]">w/{weekStartDate}</span>
        )}
        {snippet && (
          <span className="block text-[0.65rem] italic truncate mt-0.5">
            &ldquo;{snippet}&rdquo;
          </span>
        )}
        {link && (
          <a
            href={link}
            className="ml-1 inline-flex items-center gap-0.5 text-primary hover:underline text-[0.65rem]"
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
}

export function QueryAnswerCard({
  answer,
  sources,
  confidence,
  suggestionId,
  className,
}: QueryAnswerCardProps) {
  const pct = Math.round(Math.max(0, Math.min(1, confidence)) * 100);

  return (
    <Card className={cn("w-full", className)} data-testid="query-answer-card">
      <CardHeader>
        <CardTitle>AI Answer</CardTitle>
        {/* Confidence indicator */}
        <div
          className="flex items-center gap-1.5"
          data-testid="confidence-indicator"
        >
          <span
            className={cn(
              "text-xs font-semibold",
              confidenceCls(confidence),
            )}
          >
            {confidenceLabel(confidence)}
          </span>
          <div
            className="h-1.5 w-16 rounded-full bg-border overflow-hidden"
            aria-label={`Confidence: ${pct}%`}
            title={`Confidence: ${pct}%`}
          >
            <div
              className={cn("h-full rounded-full", confidenceBarCls(confidence))}
              style={{ width: `${pct}%` }}
              data-testid="confidence-bar"
            />
          </div>
          <span className="text-[0.65rem] text-muted">{pct}%</span>
        </div>
      </CardHeader>

      <CardContent className="flex flex-col gap-3">
        {/* Answer text */}
        <p className="text-sm leading-relaxed" data-testid="rag-answer-text">
          {answer}
        </p>

        {/* Source citations */}
        {sources.length > 0 && (
          <div data-testid="rag-sources-section">
            <h4 className="text-[0.65rem] font-semibold text-muted uppercase tracking-wider mb-1.5">
              Sources
            </h4>
            <ul className="flex flex-col gap-1.5 list-none m-0 p-0">
              {sources.map((s) => (
                <SourceCitation key={`${s.entityType}-${s.entityId}`} source={s} />
              ))}
            </ul>
          </div>
        )}
      </CardContent>

      <CardFooter className="justify-between">
        <span className="text-[0.65rem] text-muted">
          AI-generated answer — verify before acting
        </span>
        <AiFeedbackButtons suggestionId={suggestionId} />
      </CardFooter>
    </Card>
  );
}
