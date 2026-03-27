/**
 * AnswerRenderer — renders LLM answer text with lightweight markdown support.
 *
 * Handles:
 *   - **bold** → <strong>
 *   - \n\n  → paragraph breaks
 *   - \n    → line breaks within a paragraph
 *   - "quoted commit titles" → monospace highlighted spans
 *   - Chess piece names (KING/QUEEN/ROOK/BISHOP/KNIGHT/PAWN) → styled badges
 *   - Bullet lists (lines starting with - or •)
 *   - Percentages and point values → highlighted
 *
 * No external dependencies. Pure string → JSX transformation.
 */
import { type ReactNode } from "react";
import { cn } from "../../lib/utils.js";

// ── Chess piece rendering ─────────────────────────────────────────────────

const CHESS_GLYPHS: Record<string, string> = {
  KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙",
};

const CHESS_SHADES: Record<string, string> = {
  KING: "var(--chess-king)", QUEEN: "var(--chess-queen)", ROOK: "var(--chess-rook)",
  BISHOP: "var(--chess-bishop)", KNIGHT: "var(--chess-knight)", PAWN: "var(--chess-pawn)",
};

// ── Inline token parser ──────────────────────────────────────────────────

type InlineToken =
  | { type: "text"; value: string }
  | { type: "bold"; value: string }
  | { type: "chess"; piece: string }
  | { type: "commit-title"; value: string }
  | { type: "metric"; value: string; unit: string };

function tokenizeInline(text: string): InlineToken[] {
  const tokens: InlineToken[] = [];
  // Combined regex for all inline patterns
  // Order matters: bold first, then commit titles, then chess pieces, then metrics
  const pattern =
    /(\*\*(.+?)\*\*)|("([^"]{3,60})")|(\b(KING|QUEEN|ROOK|BISHOP|KNIGHT|PAWN)\b)|(\b(\d+(?:\.\d+)?)(pts?|%)\b)/g;

  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) !== null) {
    // Push preceding plain text
    if (match.index > lastIndex) {
      tokens.push({ type: "text", value: text.slice(lastIndex, match.index) });
    }

    if (match[1]) {
      // **bold**
      tokens.push({ type: "bold", value: match[2]! });
    } else if (match[3]) {
      // "commit title"
      tokens.push({ type: "commit-title", value: match[4]! });
    } else if (match[5]) {
      // CHESS PIECE
      tokens.push({ type: "chess", piece: match[6]! });
    } else if (match[7]) {
      // 5pts, 85%
      tokens.push({ type: "metric", value: match[8]!, unit: match[9]! });
    }

    lastIndex = match.index + match[0].length;
  }

  // Trailing text
  if (lastIndex < text.length) {
    tokens.push({ type: "text", value: text.slice(lastIndex) });
  }

  return tokens;
}

function renderInlineTokens(tokens: InlineToken[]): ReactNode[] {
  return tokens.map((token, i) => {
    switch (token.type) {
      case "text":
        return <span key={i}>{token.value}</span>;
      case "bold":
        return (
          <strong key={i} className="font-bold text-foreground">
            {token.value}
          </strong>
        );
      case "chess":
        return (
          <span
            key={i}
            className="inline-flex items-center gap-0.5 px-1 py-px text-[0.65rem] font-bold tracking-wider"
            style={{ color: CHESS_SHADES[token.piece] }}
          >
            <span className="text-xs">{CHESS_GLYPHS[token.piece]}</span>
            {token.piece}
          </span>
        );
      case "commit-title":
        return (
          <span
            key={i}
            className="font-medium text-foreground bg-muted-bg px-1 py-px border border-border text-[0.8em]"
          >
            {token.value}
          </span>
        );
      case "metric":
        return (
          <span key={i} className="font-bold text-foreground tabular-nums">
            {token.value}
            <span className="text-muted text-[0.85em]">{token.unit}</span>
          </span>
        );
      default:
        return null;
    }
  });
}

// ── Block-level parser ───────────────────────────────────────────────────

type Block =
  | { type: "paragraph"; content: string }
  | { type: "list"; items: string[] }
  | { type: "heading"; content: string };

function parseBlocks(text: string): Block[] {
  const blocks: Block[] = [];
  const paragraphs = text.split(/\n\n+/);

  for (const para of paragraphs) {
    const trimmed = para.trim();
    if (!trimmed) continue;

    const lines = trimmed.split("\n");

    // Check if this is a list (all lines start with - or • or *)
    const isListBlock = lines.length > 1 && lines.every(
      (l) => /^\s*[-•*]\s/.test(l) || l.trim() === "",
    );

    if (isListBlock) {
      blocks.push({
        type: "list",
        items: lines
          .map((l) => l.replace(/^\s*[-•*]\s*/, "").trim())
          .filter(Boolean),
      });
    } else {
      // Join lines within a paragraph (single \n → space or linebreak)
      blocks.push({ type: "paragraph", content: trimmed });
    }
  }

  return blocks;
}

// ── Main component ───────────────────────────────────────────────────────

interface AnswerRendererProps {
  text: string;
  className?: string;
}

export function AnswerRenderer({ text, className }: AnswerRendererProps) {
  const blocks = parseBlocks(text);

  return (
    <div
      className={cn("flex flex-col gap-2 text-sm leading-relaxed", className)}
      data-testid="rag-answer-text"
    >
      {blocks.map((block, i) => {
        switch (block.type) {
          case "paragraph": {
            // Handle inline \n within a paragraph
            const lines = block.content.split("\n");
            return (
              <p key={i} className="m-0">
                {lines.map((line, j) => (
                  <span key={j}>
                    {j > 0 && <br />}
                    {renderInlineTokens(tokenizeInline(line))}
                  </span>
                ))}
              </p>
            );
          }
          case "list":
            return (
              <ul key={i} className="m-0 pl-4 flex flex-col gap-1">
                {block.items.map((item, j) => (
                  <li key={j} className="text-sm list-disc list-outside">
                    {renderInlineTokens(tokenizeInline(item))}
                  </li>
                ))}
              </ul>
            );
          case "heading":
            return (
              <h4 key={i} className="m-0 text-sm font-bold">
                {renderInlineTokens(tokenizeInline(block.content))}
              </h4>
            );
          default:
            return null;
        }
      })}
    </div>
  );
}
