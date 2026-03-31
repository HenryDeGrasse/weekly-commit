/**
 * WhatIfPanel — interactive what-if planner for hypothetical commit mutations.
 *
 * Lets users queue ADD / REMOVE / MODIFY mutations, simulate their impact
 * against the plan (always deterministic) and optionally view an AI narrative
 * (shown only when narrative is non-null — degrades gracefully when AI is down).
 *
 * Important: this panel does NOT gate on AI availability — the simulation is
 * backend rule-based logic that works without a live LLM.
 */
import { useState, useCallback } from "react";
import { Sparkles, Loader2, ChevronUp, Plus, X, Lightbulb } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { Card, CardContent, CardHeader, CardTitle } from "../ui/Card.js";
import { cn } from "../../lib/utils.js";
import { useWhatIfApi } from "../../api/aiHooks.js";
import { useHostBridge } from "../../host/HostProvider.js";
import type { CommitResponse } from "../../api/planTypes.js";
import type {
  WhatIfMutation,
  WhatIfMutationAction,
  WhatIfResponse,
} from "../../api/whatIfApi.js";

// ── Constants ─────────────────────────────────────────────────────────────────

const CHESS_PIECES = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"] as const;
const ESTIMATE_POINTS = [1, 2, 3, 5, 8] as const;

// ── Types ─────────────────────────────────────────────────────────────────────

type ModifyField = "estimatePoints" | "chessPiece" | "rcdoNodeId";

export interface WhatIfPanelProps {
  planId: string;
  currentCommits: ReadonlyArray<CommitResponse>;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function WhatIfPanel({ planId, currentCommits }: WhatIfPanelProps) {
  const api = useWhatIfApi();
  const bridge = useHostBridge();
  const userId = bridge.context.authenticatedUser.id;

  const [isExpanded, setIsExpanded] = useState(false);
  const [mutations, setMutations] = useState<WhatIfMutation[]>([]);
  const [result, setResult] = useState<WhatIfResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ── Mutation form state ───────────────────────────────────────────────────
  const [action, setAction] = useState<WhatIfMutationAction>("ADD_COMMIT");

  // ADD_COMMIT fields
  const [addTitle, setAddTitle] = useState("");
  const [addChessPiece, setAddChessPiece] = useState<string>("PAWN");
  const [addPoints, setAddPoints] = useState<string>("");

  // REMOVE_COMMIT fields
  const [removeCommitId, setRemoveCommitId] = useState<string>("");

  // MODIFY_COMMIT fields
  const [modifyCommitId, setModifyCommitId] = useState<string>("");
  const [modifyField, setModifyField] = useState<ModifyField>("estimatePoints");
  const [modifyValue, setModifyValue] = useState<string>("");

  // ── Handlers ─────────────────────────────────────────────────────────────

  const handleQueueMutation = useCallback(() => {
    if (action === "ADD_COMMIT") {
      if (!addTitle.trim()) return;
      const parsedPoints = addPoints
        ? parseInt(addPoints, 10)
        : undefined;
      const mutation: WhatIfMutation = {
        action: "ADD_COMMIT",
        title: addTitle.trim(),
        chessPiece: addChessPiece,
        ...(parsedPoints !== undefined && !isNaN(parsedPoints)
          ? { estimatePoints: parsedPoints }
          : {}),
      };
      setMutations((prev) => [...prev, mutation]);
      setAddTitle("");
      setAddPoints("");
    } else if (action === "REMOVE_COMMIT") {
      if (!removeCommitId) return;
      setMutations((prev) => [
        ...prev,
        { action: "REMOVE_COMMIT", commitId: removeCommitId },
      ]);
      setRemoveCommitId("");
    } else if (action === "MODIFY_COMMIT") {
      if (!modifyCommitId || !modifyValue.trim()) return;
      const mutation: WhatIfMutation = {
        action: "MODIFY_COMMIT",
        commitId: modifyCommitId,
        ...(modifyField === "estimatePoints"
          ? { estimatePoints: parseInt(modifyValue, 10) }
          : {}),
        ...(modifyField === "chessPiece" ? { chessPiece: modifyValue } : {}),
        ...(modifyField === "rcdoNodeId" ? { rcdoNodeId: modifyValue } : {}),
      };
      setMutations((prev) => [...prev, mutation]);
      setModifyValue("");
    }
  }, [
    action,
    addTitle,
    addChessPiece,
    addPoints,
    removeCommitId,
    modifyCommitId,
    modifyField,
    modifyValue,
  ]);

  const handleSimulate = useCallback(async () => {
    if (mutations.length === 0) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const response = await api.simulate({
        planId,
        userId,
        hypotheticalChanges: mutations,
      });
      setResult(response);
    } catch {
      setError("Simulation failed. Please try again.");
    } finally {
      setLoading(false);
    }
  }, [api, planId, userId, mutations]);

  // ── Collapsed state ───────────────────────────────────────────────────────

  if (!isExpanded) {
    return (
      <Button
        variant="ghost"
        size="sm"
        onClick={() => setIsExpanded(true)}
        className="self-start text-primary hover:text-primary/80 border border-dashed border-primary/30"
        data-testid="whatif-expand-btn"
      >
        <Sparkles className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
        What-If Planner
      </Button>
    );
  }

  // ── Expanded state ────────────────────────────────────────────────────────

  return (
    <Card data-testid="whatif-panel">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Sparkles className="h-4 w-4 text-primary" aria-hidden="true" />
          What-If Planner
        </CardTitle>
        <Button
          variant="ghost"
          size="icon"
          onClick={() => setIsExpanded(false)}
          className="h-7 w-7"
          aria-label="Collapse what-if planner"
          data-testid="whatif-collapse-btn"
        >
          <ChevronUp className="h-4 w-4" />
        </Button>
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        {/* ── Mutation form ─────────────────────────────────────────────── */}
        <div className="flex flex-col gap-3">
          {/* Action tabs */}
          <div className="flex gap-1.5" role="group" aria-label="Mutation type">
            {(
              [
                "ADD_COMMIT",
                "REMOVE_COMMIT",
                "MODIFY_COMMIT",
              ] as WhatIfMutationAction[]
            ).map((a) => (
              <Button
                key={a}
                variant={action === a ? "primary" : "secondary"}
                size="sm"
                onClick={() => setAction(a)}
                data-testid={`whatif-action-${a}`}
              >
                {a === "ADD_COMMIT"
                  ? "+ Add"
                  : a === "REMOVE_COMMIT"
                    ? "− Remove"
                    : "✎ Modify"}
              </Button>
            ))}
          </div>

          {/* ADD_COMMIT inputs */}
          {action === "ADD_COMMIT" && (
            <div className="flex flex-col gap-2">
              <input
                type="text"
                placeholder="Commit title (required)"
                value={addTitle}
                onChange={(e) => setAddTitle(e.target.value)}
                className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground w-full"
                data-testid="whatif-add-title"
                aria-label="Hypothetical commit title"
              />
              <div className="flex gap-2">
                <select
                  value={addChessPiece}
                  onChange={(e) => setAddChessPiece(e.target.value)}
                  className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground"
                  data-testid="whatif-add-chess"
                  aria-label="Chess piece"
                >
                  {CHESS_PIECES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
                <select
                  value={addPoints}
                  onChange={(e) => setAddPoints(e.target.value)}
                  className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground"
                  data-testid="whatif-add-points"
                  aria-label="Estimate points"
                >
                  <option value="">Points…</option>
                  {ESTIMATE_POINTS.map((p) => (
                    <option key={p} value={String(p)}>
                      {p} pts
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {/* REMOVE_COMMIT input */}
          {action === "REMOVE_COMMIT" && (
            <select
              value={removeCommitId}
              onChange={(e) => setRemoveCommitId(e.target.value)}
              className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground"
              data-testid="whatif-remove-select"
              aria-label="Select commit to remove"
            >
              <option value="">Select commit to remove…</option>
              {currentCommits.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.title}
                </option>
              ))}
            </select>
          )}

          {/* MODIFY_COMMIT inputs */}
          {action === "MODIFY_COMMIT" && (
            <div className="flex flex-col gap-2">
              <select
                value={modifyCommitId}
                onChange={(e) => setModifyCommitId(e.target.value)}
                className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground"
                data-testid="whatif-modify-commit-select"
                aria-label="Select commit to modify"
              >
                <option value="">Select commit to modify…</option>
                {currentCommits.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.title}
                  </option>
                ))}
              </select>
              <div className="flex gap-2">
                <select
                  value={modifyField}
                  onChange={(e) =>
                    setModifyField(e.target.value as ModifyField)
                  }
                  className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground"
                  data-testid="whatif-modify-field-select"
                  aria-label="Field to modify"
                >
                  <option value="estimatePoints">Estimate Points</option>
                  <option value="chessPiece">Chess Piece</option>
                  <option value="rcdoNodeId">RCDO Node ID</option>
                </select>
                {modifyField === "estimatePoints" ? (
                  <select
                    value={modifyValue}
                    onChange={(e) => setModifyValue(e.target.value)}
                    className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground"
                    data-testid="whatif-modify-value-points"
                    aria-label="New estimate points"
                  >
                    <option value="">Points…</option>
                    {ESTIMATE_POINTS.map((p) => (
                      <option key={p} value={String(p)}>
                        {p} pts
                      </option>
                    ))}
                  </select>
                ) : modifyField === "chessPiece" ? (
                  <select
                    value={modifyValue}
                    onChange={(e) => setModifyValue(e.target.value)}
                    className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground"
                    data-testid="whatif-modify-value-chess"
                    aria-label="New chess piece"
                  >
                    <option value="">Piece…</option>
                    {CHESS_PIECES.map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    type="text"
                    placeholder="RCDO Node ID (UUID)"
                    value={modifyValue}
                    onChange={(e) => setModifyValue(e.target.value)}
                    className="rounded-default border border-border px-2 py-1.5 text-sm bg-background text-foreground flex-1"
                    data-testid="whatif-modify-value-input"
                    aria-label="New RCDO node ID"
                  />
                )}
              </div>
            </div>
          )}

          <Button
            variant="secondary"
            size="sm"
            onClick={handleQueueMutation}
            className="self-start"
            data-testid="whatif-queue-btn"
          >
            <Plus className="h-3.5 w-3.5 mr-1" aria-hidden="true" />
            Queue Mutation
          </Button>
        </div>

        {/* ── Queued mutations list ──────────────────────────────────────── */}
        {mutations.length > 0 && (
          <div className="flex flex-col gap-1.5" data-testid="whatif-mutation-list">
            <p className="m-0 text-xs font-semibold text-muted uppercase tracking-wider">
              Queued Mutations ({mutations.length})
            </p>
            {mutations.map((m, i) => {
              const commitLabel =
                currentCommits.find((c) => c.id === m.commitId)?.title ??
                m.commitId ??
                "";
              return (
                <div
                  key={i}
                  className="flex items-center justify-between gap-2 rounded-sm border border-border px-2.5 py-1.5 text-xs"
                  data-testid={`whatif-mutation-${i}`}
                >
                  <span className="flex items-center gap-1.5 flex-1 min-w-0 truncate">
                    <Badge
                      variant="default"
                      className="text-[0.6rem] shrink-0"
                    >
                      {m.action}
                    </Badge>
                    <span className="truncate">
                      {m.action === "ADD_COMMIT" ? m.title : commitLabel}
                    </span>
                  </span>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() =>
                      setMutations((prev) => prev.filter((_, j) => j !== i))
                    }
                    className="h-5 w-5 shrink-0"
                    aria-label={`Remove mutation ${i + 1}`}
                    data-testid={`whatif-remove-mutation-${i}`}
                  >
                    <X className="h-3 w-3" />
                  </Button>
                </div>
              );
            })}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setMutations([])}
              className="self-start text-xs text-muted"
              data-testid="whatif-clear-mutations-btn"
            >
              Clear all
            </Button>
          </div>
        )}

        {/* ── Simulate button ────────────────────────────────────────────── */}
        <Button
          variant="primary"
          size="sm"
          onClick={() => void handleSimulate()}
          disabled={loading || mutations.length === 0}
          className="self-start"
          data-testid="whatif-simulate-btn"
        >
          {loading ? (
            <>
              <Loader2
                className="h-3.5 w-3.5 mr-1.5 animate-spin"
                aria-hidden="true"
              />
              Simulating…
            </>
          ) : (
            <>
              <Sparkles className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
              Simulate
            </>
          )}
        </Button>

        {/* ── Error ─────────────────────────────────────────────────────── */}
        {error && (
          <div
            className="rounded-default border border-danger/30 bg-danger/5 px-3 py-2 text-xs text-danger"
            data-testid="whatif-error"
            role="alert"
          >
            {error}
          </div>
        )}

        {/* ── Results ───────────────────────────────────────────────────── */}
        {result && (
          <div className="flex flex-col gap-3 border-t border-border pt-3" data-testid="whatif-results">
            {/* Simulation unavailable */}
            {!result.available && (
              <div
                className="rounded-default border border-border bg-foreground/5 px-3 py-2 text-xs text-muted"
                data-testid="whatif-unavailable"
              >
                Simulation is currently unavailable.
              </div>
            )}

            {result.available &&
              result.currentState &&
              result.projectedState && (
                <>
                  {/* Capacity impact */}
                  <div data-testid="whatif-capacity">
                    <p className="m-0 mb-1 text-xs font-semibold text-muted uppercase tracking-wider">
                      Capacity Impact
                    </p>
                    <div className="flex items-center gap-2 text-sm flex-wrap">
                      <span>{result.currentState.totalPoints} pts</span>
                      <span className="text-muted" aria-hidden="true">
                        →
                      </span>
                      <span
                        className={cn(
                          "font-semibold",
                          result.projectedState.totalPoints >
                            result.currentState.capacityBudget
                            ? "text-danger"
                            : "text-success",
                        )}
                      >
                        {result.projectedState.totalPoints} pts
                      </span>
                      <span className="text-xs text-muted">
                        (budget: {result.currentState.capacityBudget} pts,{" "}
                        <span
                          className={cn(
                            result.capacityDelta > 0
                              ? "text-warning"
                              : result.capacityDelta < 0
                                ? "text-success"
                                : "text-muted",
                          )}
                        >
                          {result.capacityDelta > 0 ? "+" : ""}
                          {result.capacityDelta}
                        </span>
                        )
                      </span>
                    </div>
                  </div>

                  {/* Risk delta */}
                  {result.riskDelta &&
                    (result.riskDelta.newRisks.length > 0 ||
                      result.riskDelta.resolvedRisks.length > 0) && (
                      <div data-testid="whatif-risk-delta">
                        <p className="m-0 mb-1 text-xs font-semibold text-muted uppercase tracking-wider">
                          Risk Changes
                        </p>
                        <div className="flex flex-col gap-1">
                          {result.riskDelta.newRisks.map((r) => (
                            <div
                              key={r}
                              className="flex items-center gap-1.5 text-xs text-danger"
                            >
                              <span aria-hidden="true">⚠</span>
                              <span>New:</span>
                              <Badge
                                variant="default"
                                className="text-[0.6rem] bg-danger/10 text-danger border-danger/20"
                              >
                                {r}
                              </Badge>
                            </div>
                          ))}
                          {result.riskDelta.resolvedRisks.map((r) => (
                            <div
                              key={r}
                              className="flex items-center gap-1.5 text-xs text-success"
                            >
                              <span aria-hidden="true">✓</span>
                              <span>Resolved:</span>
                              <Badge
                                variant="default"
                                className="text-[0.6rem] bg-success/10 text-success border-success/20"
                              >
                                {r}
                              </Badge>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                  {/* RCDO coverage changes */}
                  {result.rcdoCoverageChanges &&
                    result.rcdoCoverageChanges.length > 0 && (
                      <div data-testid="whatif-coverage-changes">
                        <p className="m-0 mb-1 text-xs font-semibold text-muted uppercase tracking-wider">
                          RCDO Coverage Changes
                        </p>
                        <div className="flex flex-col gap-1">
                          {result.rcdoCoverageChanges.map((c) => (
                            <div
                              key={c.rcdoNodeId}
                              className="flex items-center gap-2 text-xs"
                            >
                              <span className="font-mono text-muted text-[0.65rem] truncate max-w-[8rem]">
                                {c.rcdoTitle ??
                                  c.rcdoNodeId.slice(0, 8) + "…"}
                              </span>
                              <span>{c.beforePoints} pts</span>
                              <span className="text-muted" aria-hidden="true">
                                →
                              </span>
                              <span
                                className={cn(
                                  "font-semibold",
                                  c.afterPoints > c.beforePoints
                                    ? "text-success"
                                    : "text-danger",
                                )}
                              >
                                {c.afterPoints} pts
                              </span>
                              <span
                                className={cn(
                                  "text-[0.65rem]",
                                  c.afterPoints > c.beforePoints
                                    ? "text-success"
                                    : "text-danger",
                                )}
                              >
                                (
                                {c.afterPoints - c.beforePoints > 0 ? "+" : ""}
                                {c.afterPoints - c.beforePoints})
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                  {/* AI narrative — shown only when narrative is non-null */}
                  {result.narrative && (
                    <div
                      className="rounded-default border border-primary/20 bg-primary/5 px-3 py-2.5"
                      data-testid="whatif-narrative"
                    >
                      <p className="m-0 mb-1.5 text-xs font-semibold text-primary uppercase tracking-wider flex items-center gap-1">
                        <Sparkles className="h-3 w-3" aria-hidden="true" />
                        AI Insight
                      </p>
                      <p className="m-0 text-sm leading-relaxed">
                        {result.narrative}
                      </p>
                      {result.recommendation && (
                        <p className="m-0 mt-2 text-xs text-muted italic">
                          <Lightbulb className="h-3 w-3 inline-block shrink-0" aria-hidden="true" /> {result.recommendation}
                        </p>
                      )}
                    </div>
                  )}
                </>
              )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
