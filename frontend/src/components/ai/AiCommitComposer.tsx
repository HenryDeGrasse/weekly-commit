/**
 * CommitComposer — freeform "describe → structure" commit creation modal.
 *
 * Phase 1: User describes their work in a textarea.
 * Phase 2: AI structures it — title, chess piece, RCDO link, estimate, success criteria.
 *
 * Per PRD §17: every suggestion shows rationale, can be accepted or dismissed,
 * is fully auditable, and never auto-submits. The user must explicitly accept.
 */
import { useState, useCallback } from "react";
import { Sparkles, Loader2, X, Check, ArrowRight } from "lucide-react";
import { Button } from "../ui/Button.js";
import { cn } from "../../lib/utils.js";
import { useAiApi, useAiStatus } from "../../api/aiHooks.js";
import { useHostBridge } from "../../host/HostProvider.js";
import { useRcdoTree } from "../../api/rcdoHooks.js";
import { RcdoTreeView } from "../rcdo/RcdoTreeView.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import { AiSuggestedBadge } from "./AiSuggestedBadge.js";
import type { RcdoSuggestResponse } from "../../api/aiApi.js";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";
import type {
  CreateCommitPayload,
  CommitResponse,
  ChessPiece,
  EstimatePoints,
} from "../../api/planTypes.js";

// ── Constants ─────────────────────────────────────────────────────────────────

const CHESS_PIECES: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔",
  QUEEN: "♕",
  ROOK: "♖",
  BISHOP: "♗",
  KNIGHT: "♘",
  PAWN: "♙",
};
const VALID_POINTS = [1, 2, 3, 5, 8] as const;
const MAX_KING_PER_WEEK = 1;
const MAX_QUEEN_PER_WEEK = 2;

// ── Helpers ───────────────────────────────────────────────────────────────────

function findNodeById(nodes: RcdoTreeNode[], id: string): RcdoTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findNodeById(node.children, id);
    if (found) return found;
  }
  return null;
}

// ── Internal draft state ──────────────────────────────────────────────────────

interface AiDraft {
  title: string;
  description: string;
  chessPiece: ChessPiece | "";
  /** Stored as string to match the estimate button toggle pattern. */
  estimatePoints: string;
  rcdoNodeId: string;
  rcdoTitle: string;
  rcdoRationale: string;
  rcdoConfidence?: number | undefined;
  successCriteria: string;
  /** Whether the RCDO was AI-suggested (vs manually picked). */
  rcdoAiSuggested: boolean;
  /** Suggestion id for draft-assist feedback. */
  draftSuggestionId?: string | undefined;
  /** Suggestion id for RCDO-suggest feedback. */
  rcdoSuggestionId?: string | undefined;
}

// ── Props ─────────────────────────────────────────────────────────────────────

export interface AiCommitComposerProps {
  planId: string;
  existingCommits: CommitResponse[];
  /**
   * Called when the user accepts the structured draft and wants to create
   * the commit.
   */
  onSubmit: (payload: CreateCommitPayload) => Promise<void>;
  /**
   * Called when the user wants to switch to the manual form.
   * Receives any AI-suggested values so the form can be pre-filled.
   */
  onSwitchToManual: (preFilled: Partial<CreateCommitPayload>) => void;
  onCancel: () => void;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function AiCommitComposer({
  planId,
  existingCommits,
  onSubmit,
  onSwitchToManual,
  onCancel,
}: AiCommitComposerProps) {
  const aiApi = useAiApi();
  const bridge = useHostBridge();
  const userId = bridge.context.authenticatedUser.id;
  const { data: aiStatus } = useAiStatus();
  const { data: rcdoTree } = useRcdoTree();

  const [freeformText, setFreeformText] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState<AiDraft | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showRcdoPicker, setShowRcdoPicker] = useState(false);
  const [rcdoSearchQuery, setRcdoSearchQuery] = useState("");

  const isAvailable = aiStatus?.available ?? false;
  const nodes = rcdoTree ?? [];

  const existingKings = existingCommits.filter((c) => c.chessPiece === "KING").length;
  const existingQueens = existingCommits.filter((c) => c.chessPiece === "QUEEN").length;

  // ── Generate handler ───────────────────────────────────────────────────────

  const handleGenerate = useCallback(async () => {
    const text = freeformText.trim();
    if (!text) return;
    setLoading(true);
    setError(null);
    setDraft(null);
    setShowRcdoPicker(false);

    try {
      // Call both APIs in parallel; RCDO suggest failure is non-fatal.
      const [draftResponse, rcdoResponse] = await Promise.all([
        aiApi.commitFromFreeform(planId, userId, text),
        aiApi
          .rcdoSuggest({ planId, userId, title: text })
          .catch((): RcdoSuggestResponse => ({
            aiAvailable: false,
            suggestionAvailable: false,
          })),
      ]);

      if (!draftResponse.aiAvailable) {
        setError(
          "AI is currently unavailable. Use the manual form to create your commit.",
        );
        return;
      }

      const rcdoSuggested =
        rcdoResponse.aiAvailable &&
        rcdoResponse.suggestionAvailable &&
        !!rcdoResponse.suggestedRcdoNodeId;

      const newDraft: AiDraft = {
        title: draftResponse.suggestedTitle ?? text,
        description: draftResponse.suggestedDescription ?? "",
        chessPiece:
          (draftResponse.suggestedChessPiece as ChessPiece | undefined | null) ?? "",
        estimatePoints:
          draftResponse.suggestedEstimatePoints != null
            ? String(draftResponse.suggestedEstimatePoints)
            : "",
        rcdoNodeId: rcdoSuggested ? (rcdoResponse.suggestedRcdoNodeId ?? "") : "",
        rcdoTitle: rcdoSuggested ? (rcdoResponse.rcdoTitle ?? "") : "",
        rcdoRationale: rcdoSuggested ? (rcdoResponse.rationale ?? "") : "",
        rcdoConfidence: rcdoSuggested ? rcdoResponse.confidence : undefined,
        rcdoAiSuggested: rcdoSuggested,
        successCriteria: draftResponse.suggestedSuccessCriteria ?? "",
        draftSuggestionId: draftResponse.suggestionId,
        rcdoSuggestionId:
          rcdoResponse.aiAvailable && rcdoResponse.suggestionId
            ? rcdoResponse.suggestionId
            : undefined,
      };
      setDraft(newDraft);
    } catch {
      setError(
        "Failed to generate commit. Please try again or switch to the manual form.",
      );
    } finally {
      setLoading(false);
    }
  }, [aiApi, userId, planId, freeformText]);

  // ── Build pre-filled payload for manual-form switch ────────────────────────

  const buildPreFilled = useCallback((): Partial<CreateCommitPayload> => {
    const title = draft?.title || freeformText.trim();
    return {
      ...(title ? { title } : {}),
      ...(draft?.description.trim() ? { description: draft.description.trim() } : {}),
      ...(draft?.chessPiece ? { chessPiece: draft.chessPiece as ChessPiece } : {}),
      ...(draft?.rcdoNodeId ? { rcdoNodeId: draft.rcdoNodeId } : {}),
      ...(draft?.estimatePoints
        ? { estimatePoints: Number(draft.estimatePoints) as EstimatePoints }
        : {}),
      ...(draft?.successCriteria.trim()
        ? { successCriteria: draft.successCriteria.trim() }
        : {}),
    };
  }, [draft, freeformText]);

  // ── Accept-all handler ─────────────────────────────────────────────────────

  const handleAcceptAll = useCallback(async () => {
    if (!draft) return;
    setSubmitting(true);
    setError(null);
    try {
      const payload: CreateCommitPayload = {
        title: draft.title || freeformText || "Untitled commit",
        chessPiece: (draft.chessPiece || "PAWN") as ChessPiece,
        ...(draft.description.trim() ? { description: draft.description.trim() } : {}),
        ...(draft.rcdoNodeId ? { rcdoNodeId: draft.rcdoNodeId } : {}),
        ...(draft.estimatePoints
          ? { estimatePoints: Number(draft.estimatePoints) as EstimatePoints }
          : {}),
        ...(draft.successCriteria.trim()
          ? { successCriteria: draft.successCriteria.trim() }
          : {}),
      };
      await onSubmit(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create commit");
    } finally {
      setSubmitting(false);
    }
  }, [draft, freeformText, onSubmit]);

  const handleSwitchToManual = useCallback(() => {
    onSwitchToManual(buildPreFilled());
  }, [buildPreFilled, onSwitchToManual]);

  // ── RCDO selection handlers ────────────────────────────────────────────────

  const handleSelectRcdo = useCallback((id: string) => {
    const node = findNodeById(nodes, id);
    setDraft((d) =>
      d ? { ...d, rcdoNodeId: id, rcdoTitle: node?.title ?? "", rcdoRationale: "", rcdoAiSuggested: false } : d,
    );
    setShowRcdoPicker(false);
  }, [nodes]);

  const handleClearRcdo = useCallback(() => {
    setDraft((d) =>
      d ? { ...d, rcdoNodeId: "", rcdoTitle: "", rcdoRationale: "", rcdoAiSuggested: false } : d,
    );
    setShowRcdoPicker(false);
  }, []);

  // ── Chess piece limit warnings ─────────────────────────────────────────────

  const chessPieceWarning =
    draft?.chessPiece === "KING" && existingKings >= MAX_KING_PER_WEEK
      ? `Max ${MAX_KING_PER_WEEK} King already used this week`
      : draft?.chessPiece === "QUEEN" && existingQueens >= MAX_QUEEN_PER_WEEK
        ? `Max ${MAX_QUEEN_PER_WEEK} Queens already used this week`
        : null;

  const canAcceptAll = !!(draft && !submitting && !chessPieceWarning);

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Commit Composer"
      data-testid="ai-commit-composer"
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 backdrop-blur-[2px]"
    >
      <div className="w-full max-w-[580px] rounded-lg border border-border bg-surface p-6 shadow-xl max-h-[90vh] overflow-y-auto">

        {/* Header */}
        <div className="flex items-center justify-between mb-5">
          <h3 className="m-0 text-lg font-semibold flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-primary" aria-hidden="true" />
            Commit Composer
          </h3>
          <Button
            variant="ghost"
            size="icon"
            type="button"
            onClick={onCancel}
            aria-label="Close"
            className="h-7 w-7"
            data-testid="ai-composer-close-btn"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* ── Phase 1: Freeform input ────────────────────────────────────── */}
        {!draft && (
          <>
            <p className="m-0 mb-3 text-sm text-muted">
              Describe what you&apos;re working on this week. AI will structure it into a
              commit with the right chess piece, RCDO link, estimate, and success criteria.
            </p>
            <div className="flex flex-col gap-3">
              <textarea
                data-testid="ai-composer-freeform-input"
                value={freeformText}
                onChange={(e) => setFreeformText(e.target.value)}
                rows={4}
                placeholder="e.g. Migrating the auth service to OAuth 2.0 tokens. Critical for our enterprise customers going live next week."
                className="w-full rounded-default border border-border bg-surface px-3 py-2.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
                disabled={loading}
                aria-label="Describe your work"
              />

              {error && (
                <p className="text-xs text-danger" data-testid="ai-composer-error">
                  {error}
                </p>
              )}

              <div className="flex items-center gap-3">
                <Button
                  type="button"
                  variant="primary"
                  onClick={() => void handleGenerate()}
                  disabled={loading || !freeformText.trim() || !isAvailable}
                  data-testid="ai-composer-generate-btn"
                  title={!isAvailable ? "AI is currently unavailable" : undefined}
                >
                  {loading ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" />
                      Generating…
                    </>
                  ) : (
                    <>
                      <Sparkles className="h-3.5 w-3.5 mr-1.5" />
                      Generate Commit
                    </>
                  )}
                </Button>
                <button
                  type="button"
                  onClick={handleSwitchToManual}
                  className="text-xs text-muted hover:text-foreground underline cursor-pointer bg-transparent border-none p-0"
                  data-testid="ai-composer-switch-manual-link"
                >
                  Switch to manual form
                </button>
              </div>
            </div>
          </>
        )}

        {/* ── Phase 2: AI-structured draft ──────────────────────────────── */}
        {draft && (
          <>
            <div
              className="rounded-default border border-primary/20 bg-primary/5 p-4 flex flex-col gap-4"
              data-testid="ai-composer-draft"
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-primary flex items-center gap-1.5">
                  <Sparkles className="h-3.5 w-3.5" />
                  AI-Structured Commit
                </span>
                {draft.draftSuggestionId && (
                  <AiFeedbackButtons suggestionId={draft.draftSuggestionId} />
                )}
              </div>

              {/* Title */}
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <label
                    htmlFor="ai-composer-title"
                    className="text-xs font-semibold text-muted uppercase tracking-wider"
                  >
                    Title
                  </label>
                  <AiSuggestedBadge />
                </div>
                <input
                  id="ai-composer-title"
                  type="text"
                  value={draft.title}
                  onChange={(e) =>
                    setDraft((d) => (d ? { ...d, title: e.target.value } : d))
                  }
                  className="rounded-default border border-border bg-surface px-3 py-1.5 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
                  data-testid="ai-composer-title-field"
                />
              </div>

              {/* Description */}
              {draft.description && (
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <label
                      htmlFor="ai-composer-description"
                      className="text-xs font-semibold text-muted uppercase tracking-wider"
                    >
                      Description
                    </label>
                    <AiSuggestedBadge />
                  </div>
                  <textarea
                    id="ai-composer-description"
                    value={draft.description}
                    onChange={(e) =>
                      setDraft((d) =>
                        d ? { ...d, description: e.target.value } : d,
                      )
                    }
                    rows={2}
                    className="w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
                    data-testid="ai-composer-description-field"
                  />
                </div>
              )}

              {/* Chess Piece */}
              {draft.chessPiece && (
                <div className="flex flex-col gap-1">
                  <label
                    htmlFor="ai-composer-chess-piece"
                    className="text-xs font-semibold text-muted uppercase tracking-wider"
                  >
                    Chess Piece
                  </label>
                  <div className="flex items-center gap-2 flex-wrap">
                    <select
                      id="ai-composer-chess-piece"
                      value={draft.chessPiece}
                      onChange={(e) =>
                        setDraft((d) =>
                          d ? { ...d, chessPiece: e.target.value as ChessPiece } : d,
                        )
                      }
                      className="rounded-default border border-border bg-surface px-3 py-1.5 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
                      data-testid="ai-composer-chess-piece-select"
                    >
                      {CHESS_PIECES.map((p) => (
                        <option key={p} value={p}>
                          {CHESS_PIECE_ICONS[p]} {p}
                        </option>
                      ))}
                    </select>
                    <AiSuggestedBadge />
                  </div>
                  {chessPieceWarning && (
                    <p
                      className="text-xs text-danger"
                      data-testid="ai-composer-chess-warning"
                    >
                      {chessPieceWarning}
                    </p>
                  )}
                </div>
              )}

              {/* Estimate Points */}
              {draft.estimatePoints && (
                <div className="flex flex-col gap-1">
                  <p className="text-xs font-semibold text-muted uppercase tracking-wider m-0">
                    Estimate
                  </p>
                  <div className="flex items-center gap-1.5 flex-wrap">
                    {VALID_POINTS.map((pts) => {
                      const isActive = draft.estimatePoints === String(pts);
                      return (
                        <button
                          key={pts}
                          type="button"
                          onClick={() =>
                            setDraft((d) =>
                              d
                                ? { ...d, estimatePoints: isActive ? "" : String(pts) }
                                : d,
                            )
                          }
                          data-testid={`ai-composer-estimate-${pts}`}
                          aria-pressed={isActive}
                          className={cn(
                            "h-9 w-9 rounded-default border-2 text-sm font-bold transition-colors",
                            isActive
                              ? "border-primary"
                              : "border-border bg-surface text-foreground hover:border-primary/60",
                          )}
                          style={
                            isActive
                              ? {
                                  background: "var(--color-primary)",
                                  color: "rgb(255, 255, 255)",
                                }
                              : undefined
                          }
                        >
                          {pts}
                        </button>
                      );
                    })}
                    <AiSuggestedBadge />
                  </div>
                </div>
              )}

              {/* RCDO Link — always shown; AI suggestion pre-fills it, tree picker as fallback */}
              <div className="flex flex-col gap-1">
                <p className="text-xs font-semibold text-muted uppercase tracking-wider m-0">
                  RCDO Link
                </p>

                {draft.rcdoNodeId ? (
                  /* Selected / AI-suggested state */
                  <div className="flex items-center gap-2 px-3 py-2 rounded-default border border-foreground/20 bg-foreground/5 text-sm flex-wrap">
                    <Check className="h-3.5 w-3.5 text-foreground shrink-0" aria-hidden="true" />
                    <span className="flex-1" data-testid="ai-composer-rcdo-title">
                      {draft.rcdoTitle || draft.rcdoNodeId.slice(0, 8) + "…"}
                    </span>
                    {draft.rcdoAiSuggested && (
                      <>
                        {draft.rcdoRationale && (
                          <span className="text-[0.65rem] text-muted italic">
                            {draft.rcdoRationale}
                          </span>
                        )}
                        {draft.rcdoConfidence != null && (
                          <span
                            className="text-[0.65rem] text-muted"
                            data-testid="ai-composer-rcdo-confidence"
                          >
                            {Math.round(draft.rcdoConfidence * 100)}% confidence
                          </span>
                        )}
                        <AiSuggestedBadge />
                        {draft.rcdoSuggestionId && (
                          <AiFeedbackButtons suggestionId={draft.rcdoSuggestionId} />
                        )}
                      </>
                    )}
                    <Button
                      variant="ghost"
                      size="icon"
                      type="button"
                      onClick={handleClearRcdo}
                      aria-label="Clear RCDO"
                      className="h-6 w-6 text-muted shrink-0"
                      data-testid="ai-composer-clear-rcdo"
                    >
                      <X className="h-3 w-3" />
                    </Button>
                  </div>
                ) : (
                  /* No selection yet — show picker toggle */
                  <Button
                    variant="secondary"
                    size="sm"
                    type="button"
                    onClick={() => setShowRcdoPicker((v) => !v)}
                    data-testid="ai-composer-rcdo-picker-toggle"
                    aria-expanded={showRcdoPicker}
                  >
                    {showRcdoPicker ? "Close RCDO picker" : "Browse RCDO nodes"}
                  </Button>
                )}

                {showRcdoPicker && nodes.length > 0 && (
                  <div
                    data-testid="ai-composer-rcdo-picker"
                    className="mt-1 rounded-default border border-border p-3 max-h-[260px] overflow-y-auto flex flex-col gap-2"
                  >
                    <input
                      type="search"
                      value={rcdoSearchQuery}
                      onChange={(e) => setRcdoSearchQuery(e.target.value)}
                      placeholder="Search nodes…"
                      data-testid="ai-composer-rcdo-search"
                      className="h-7 w-full rounded-default border border-border bg-surface px-2.5 text-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
                    />
                    <RcdoTreeView
                      nodes={nodes}
                      selectedId={draft.rcdoNodeId || null}
                      onSelect={handleSelectRcdo}
                      statusFilter="active-only"
                      searchQuery={rcdoSearchQuery}
                    />
                  </div>
                )}
              </div>

              {/* Success Criteria */}
              {draft.successCriteria && (
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <label
                      htmlFor="ai-composer-criteria"
                      className="text-xs font-semibold text-muted uppercase tracking-wider"
                    >
                      Success Criteria
                    </label>
                    <AiSuggestedBadge />
                  </div>
                  <textarea
                    id="ai-composer-criteria"
                    value={draft.successCriteria}
                    onChange={(e) =>
                      setDraft((d) =>
                        d ? { ...d, successCriteria: e.target.value } : d,
                      )
                    }
                    rows={2}
                    className="w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
                    data-testid="ai-composer-criteria-field"
                  />
                </div>
              )}
            </div>

            {error && (
              <p
                className="mt-3 text-xs text-danger"
                data-testid="ai-composer-submit-error"
              >
                {error}
              </p>
            )}

            {/* Action row */}
            <div className="flex items-center gap-3 mt-4 flex-wrap">
              <Button
                type="button"
                variant="primary"
                onClick={() => void handleAcceptAll()}
                disabled={!canAcceptAll}
                data-testid="ai-composer-accept-btn"
              >
                {submitting ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" />
                    Creating…
                  </>
                ) : (
                  <>
                    <Check className="h-3.5 w-3.5 mr-1.5" />
                    Accept &amp; Create
                  </>
                )}
              </Button>
              <button
                type="button"
                onClick={handleSwitchToManual}
                className="flex items-center gap-1 text-xs text-muted hover:text-foreground underline cursor-pointer bg-transparent border-none p-0"
                data-testid="ai-composer-switch-manual-btn"
              >
                <ArrowRight className="h-3 w-3" />
                Switch to manual form
              </button>
              <button
                type="button"
                onClick={() => { setDraft(null); setShowRcdoPicker(false); }}
                className="text-xs text-muted hover:text-foreground cursor-pointer bg-transparent border-none p-0 ml-auto"
                data-testid="ai-composer-regenerate-btn"
                aria-label="Regenerate"
              >
                ↺ Try again
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
