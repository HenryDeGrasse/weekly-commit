/**
 * CommitForm — modal/panel for creating and editing weekly commits.
 */
import { useState, type FormEvent, Component, type ErrorInfo, type ReactNode } from "react";
import { X, ChevronDown, ChevronUp, Check } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Input } from "../ui/Input.js";
import { cn } from "../../lib/utils.js";
import { RcdoTreeView } from "../rcdo/RcdoTreeView.js";
import { CommitDraftAssistButton } from "../ai/CommitDraftAssistButton.js";
import { RcdoSuggestionInline } from "../ai/RcdoSuggestionInline.js";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";
import type { ChessPiece, EstimatePoints, CommitResponse, CreateCommitPayload, UpdateCommitPayload } from "../../api/planTypes.js";

/**
 * Error boundary that silently hides AI components when HostProvider is absent
 * (e.g., in unit tests). Prevents AI features from crashing the form.
 */
class AiErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean }> {
  state = { hasError: false };
  static getDerivedStateFromError() { return { hasError: true }; }
  componentDidCatch(_: Error, __: ErrorInfo) { /* silently suppress */ }
  render() { return this.state.hasError ? null : this.props.children; }
}

const CHESS_PIECES: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
const CHESS_PIECE_ICONS: Record<ChessPiece, string> = { KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙" };
const CHESS_PIECE_LABELS: Record<ChessPiece, string> = { KING: "King", QUEEN: "Queen", ROOK: "Rook", BISHOP: "Bishop", KNIGHT: "Knight", PAWN: "Pawn" };
const CHESS_PIECE_DESCRIPTIONS: Record<ChessPiece, string> = {
  KING: "Mission-critical — must be done this week",
  QUEEN: "High priority — strong commitment",
  ROOK: "Important — significant contribution",
  BISHOP: "Valuable — worth commitment",
  KNIGHT: "Tactical — opportunistic work",
  PAWN: "Nice-to-have — best-effort",
};
const MAX_KING_PER_WEEK = 1;
const MAX_QUEEN_PER_WEEK = 2;
const VALID_POINTS: EstimatePoints[] = [1, 2, 3, 5, 8];

function findNodeById(nodes: RcdoTreeNode[], id: string): RcdoTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findNodeById(node.children, id);
    if (found) return found;
  }
  return null;
}

function getRcdoSelectionError(node: RcdoTreeNode): string | null {
  if (node.nodeType === "RALLY_CRY") return "Select an Outcome, or a Defining Objective with no active Outcomes";
  if (node.nodeType === "DEFINING_OBJECTIVE" && node.children.some((child) => child.nodeType === "OUTCOME" && child.status === "ACTIVE")) {
    return "Select an Outcome, or a Defining Objective with no active Outcomes";
  }
  return null;
}

interface FormValues {
  title: string; description: string; chessPiece: ChessPiece | ""; estimatePoints: EstimatePoints | "";
  rcdoNodeId: string; successCriteria: string; workItemId: string;
}
type FormErrors = Partial<Record<keyof FormValues, string>>;

interface CreateModeProps {
  readonly mode: "create";
  readonly planId: string;
  readonly rcdoTree: RcdoTreeNode[];
  readonly existingCommits: CommitResponse[];
  readonly onSubmit: (payload: CreateCommitPayload) => Promise<void>;
  readonly onCancel: () => void;
  /** Optional initial values pre-filled from AI suggestions or other sources. */
  readonly initialValues?: Partial<CreateCommitPayload> | undefined;
}
interface EditModeProps {
  readonly mode: "edit";
  readonly commit: CommitResponse;
  readonly rcdoTree: RcdoTreeNode[];
  readonly existingCommits: CommitResponse[];
  readonly onSubmit: (payload: UpdateCommitPayload) => Promise<void>;
  readonly onCancel: () => void;
}
export type CommitFormProps = CreateModeProps | EditModeProps;

export function CommitForm(props: CommitFormProps) {
  const { rcdoTree, existingCommits, onCancel } = props;
  const isCreate = props.mode === "create";
  const editCommit = props.mode === "edit" ? props.commit : null;
  const resolvedPlanId = props.mode === "edit" ? props.commit.planId : props.planId;

  // For create mode, seed from optional pre-filled values (e.g. from AI composer).
  const iv = props.mode === "create" ? (props.initialValues ?? {}) : {} as Partial<CreateCommitPayload>;

  const [values, setValues] = useState<FormValues>({
    title: editCommit?.title ?? iv.title ?? "",
    description: editCommit?.description ?? iv.description ?? "",
    chessPiece: (editCommit?.chessPiece ?? iv.chessPiece ?? "") as ChessPiece | "",
    estimatePoints: (editCommit?.estimatePoints ?? iv.estimatePoints ?? "") as EstimatePoints | "",
    rcdoNodeId: editCommit?.rcdoNodeId ?? iv.rcdoNodeId ?? "",
    successCriteria: editCommit?.successCriteria ?? iv.successCriteria ?? "",
    workItemId: editCommit?.workItemId ?? "",
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [showRcdoPicker, setShowRcdoPicker] = useState(false);

  const otherCommits = editCommit ? existingCommits.filter((c) => c.id !== editCommit.id) : existingCommits;
  const existingKings = otherCommits.filter((c) => c.chessPiece === "KING").length;
  const existingQueens = otherCommits.filter((c) => c.chessPiece === "QUEEN").length;

  function isChessPieceDisabled(piece: ChessPiece): boolean {
    if (piece === "KING" && existingKings >= MAX_KING_PER_WEEK) return true;
    if (piece === "QUEEN" && existingQueens >= MAX_QUEEN_PER_WEEK) return true;
    return false;
  }
  function chessPieceLimitMessage(piece: ChessPiece): string | null {
    if (piece === "KING" && existingKings >= MAX_KING_PER_WEEK) return `Max ${MAX_KING_PER_WEEK} King already used`;
    if (piece === "QUEEN" && existingQueens >= MAX_QUEEN_PER_WEEK) return `Max ${MAX_QUEEN_PER_WEEK} Queens already used`;
    return null;
  }

  function handleChange(field: keyof FormValues, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: undefined }));
  }

  function validate(): boolean {
    const next: FormErrors = {};
    if (!values.title.trim()) next.title = "Title is required";
    if (!values.chessPiece) next.chessPiece = "Chess piece is required";
    else if (isChessPieceDisabled(values.chessPiece as ChessPiece)) next.chessPiece = chessPieceLimitMessage(values.chessPiece as ChessPiece) ?? "Piece limit reached";
    if ((values.chessPiece === "KING" || values.chessPiece === "QUEEN") && !values.successCriteria.trim()) next.successCriteria = "Success criteria is required for King / Queen";
    if (values.rcdoNodeId) {
      const selectedNode = findNodeById(rcdoTree, values.rcdoNodeId);
      if (selectedNode) { const rcdoError = getRcdoSelectionError(selectedNode); if (rcdoError) next.rcdoNodeId = rcdoError; }
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    setSubmitting(true); setSubmitError(null);
    try {
      const base = {
        title: values.title.trim(),
        chessPiece: values.chessPiece as ChessPiece,
        ...(values.description.trim() ? { description: values.description.trim() } : {}),
        ...(values.rcdoNodeId ? { rcdoNodeId: values.rcdoNodeId } : {}),
        ...(values.workItemId ? { workItemId: values.workItemId } : {}),
        ...(values.estimatePoints !== "" ? { estimatePoints: Number(values.estimatePoints) as EstimatePoints } : {}),
        ...(values.successCriteria.trim() ? { successCriteria: values.successCriteria.trim() } : {}),
      };
      if (isCreate) { await (props as CreateModeProps).onSubmit(base as CreateCommitPayload); }
      else { await (props as EditModeProps).onSubmit(base as UpdateCommitPayload); }
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "An unexpected error occurred");
    } finally {
      setSubmitting(false);
    }
  }

  const selectedRcdoNode = values.rcdoNodeId ? findNodeById(rcdoTree, values.rcdoNodeId) : null;

  const textareaCls = "w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:border-primary";

  return (
    <div role="dialog" aria-modal="true" aria-label={isCreate ? "New commit" : "Edit commit"} data-testid="commit-form-modal"
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 backdrop-blur-[2px]">
      <div className="w-full max-w-[600px] rounded-lg border border-border bg-surface p-6 shadow-xl max-h-[90vh] overflow-y-auto">

        {/* Carry-forward banner */}
        {editCommit && editCommit.carryForwardStreak > 0 && (
          <div data-testid="carry-forward-banner" className="mb-4 rounded-default border border-neutral-200 bg-neutral-50 px-3 py-2.5 text-sm text-foreground">
            🔁 <strong>Carried forward</strong> — this commit has been carried forward {editCommit.carryForwardStreak} time{editCommit.carryForwardStreak !== 1 ? "s" : ""}.
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate aria-label={isCreate ? "New commit" : "Edit commit"} data-testid="commit-form">
          <div className="flex items-center justify-between mb-5">
            <h3 className="m-0 text-lg font-semibold">{isCreate ? "New Commit" : "Edit Commit"}</h3>
            <Button variant="ghost" size="icon" type="button" onClick={onCancel} aria-label="Close" className="h-7 w-7">
              <X className="h-4 w-4" />
            </Button>
          </div>

          {/* Title */}
          <div className="mb-4">
            <Input
              id="commit-form-title"
              label="Title *"
              type="text"
              value={values.title}
              onChange={(e) => handleChange("title", e.target.value)}
              aria-required="true"
              error={errors.title}
              placeholder="What will you commit to this week?"
              data-testid="commit-form-title"
            />
          </div>

          {/* AI Draft Assist — error boundary silently hides when HostProvider absent */}
          {values.title.trim().length > 0 && (
            <AiErrorBoundary>
              <CommitDraftAssistButton
                planId={resolvedPlanId}
                {...(editCommit?.id ? { commitId: editCommit.id } : {})}
                currentTitle={values.title}
                currentDescription={values.description || undefined}
                currentSuccessCriteria={values.successCriteria || undefined}
                currentEstimatePoints={values.estimatePoints !== "" ? Number(values.estimatePoints) : undefined}
                chessPiece={values.chessPiece || undefined}
                onAcceptTitle={(t) => handleChange("title", t)}
                onAcceptDescription={(d) => handleChange("description", d)}
                onAcceptSuccessCriteria={(c) => handleChange("successCriteria", c)}
                onAcceptEstimatePoints={(p) => handleChange("estimatePoints", String(p))}
                onAcceptChessPiece={(p) => handleChange("chessPiece", p)}
                className="mb-4"
              />
            </AiErrorBoundary>
          )}

          {/* Description */}
          <div className="mb-4 flex flex-col gap-1.5">
            <label htmlFor="commit-form-description" className="text-sm font-medium">Description</label>
            <textarea id="commit-form-description" value={values.description} onChange={(e) => handleChange("description", e.target.value)} rows={3} className={textareaCls} placeholder="Optional: provide additional context" data-testid="commit-form-description" />
          </div>

          {/* Chess piece */}
          <div className="mb-4">
            <fieldset className="border-0 p-0 m-0" aria-describedby={errors.chessPiece ? "commit-form-piece-error" : undefined}>
              <legend className="text-sm font-medium mb-2">Chess Piece *</legend>
              <div className="flex flex-col gap-2" data-testid="commit-form-chess-piece-group">
                {CHESS_PIECES.map((piece) => {
                  const disabled = isChessPieceDisabled(piece);
                  const limitMsg = chessPieceLimitMessage(piece);
                  const isSelected = values.chessPiece === piece;
                  return (
                    <label key={piece} data-testid={`chess-piece-option-${piece.toLowerCase()}`}
                      className={cn(
                        "flex items-start gap-2.5 p-2 rounded-default border cursor-pointer transition-colors",
                        isSelected ? "border-foreground bg-foreground/5" : "border-border hover:border-foreground/30",
                        disabled && "opacity-55 cursor-not-allowed",
                      )}>
                      <input type="radio" name="chessPiece" value={piece} checked={isSelected} onChange={() => { if (!disabled) handleChange("chessPiece", piece); }} disabled={disabled} className="mt-0.5 shrink-0" />
                      <span aria-hidden="true" className="text-xl leading-none shrink-0">{CHESS_PIECE_ICONS[piece]}</span>
                      <span>
                        <span className="font-semibold text-sm">{CHESS_PIECE_LABELS[piece]}</span>
                        <span className="block text-xs text-muted mt-0.5">{CHESS_PIECE_DESCRIPTIONS[piece]}</span>
                        {limitMsg && <span data-testid={`chess-piece-limit-${piece.toLowerCase()}`} className="block text-xs text-danger font-semibold mt-0.5">{limitMsg}</span>}
                      </span>
                    </label>
                  );
                })}
              </div>
              {errors.chessPiece && <span id="commit-form-piece-error" role="alert" className="block mt-1 text-xs text-danger">{errors.chessPiece}</span>}
            </fieldset>
          </div>

          {/* Estimate points */}
          <div className="mb-4">
            <p className="text-sm font-medium mb-2">Estimate Points</p>
            <div role="group" aria-label="Estimate points" className="flex gap-1.5 flex-wrap" data-testid="commit-form-estimate-group">
              {VALID_POINTS.map((pts) => {
                const isActive = String(values.estimatePoints) === String(pts);
                return (
                  <button key={pts} type="button" onClick={() => handleChange("estimatePoints", values.estimatePoints === String(pts) ? "" : String(pts))} data-testid={`estimate-btn-${pts}`} aria-pressed={isActive}
                    className={cn("h-10 w-10 rounded-default border-2 text-sm font-bold transition-colors", isActive ? "border-primary text-primary-foreground" : "border-border bg-surface text-foreground hover:border-primary/60")}
                    style={isActive ? { background: "var(--color-primary)", color: "rgb(255, 255, 255)" } : undefined}>
                    {pts}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Success criteria */}
          <div className="mb-4 flex flex-col gap-1.5">
            <label htmlFor="commit-form-success-criteria" className="text-sm font-medium">
              Success Criteria{(values.chessPiece === "KING" || values.chessPiece === "QUEEN") && <span className="text-danger"> *</span>}
            </label>
            <textarea id="commit-form-success-criteria" value={values.successCriteria} onChange={(e) => handleChange("successCriteria", e.target.value)} rows={2}
              aria-required={values.chessPiece === "KING" || values.chessPiece === "QUEEN"}
              className={cn(textareaCls, errors.successCriteria && "border-danger focus-visible:ring-danger/50")}
              placeholder="How will you know you succeeded?" data-testid="commit-form-success-criteria" />
            {errors.successCriteria && <span id="commit-form-sc-error" role="alert" className="text-xs text-danger">{errors.successCriteria}</span>}
          </div>

          {/* RCDO picker */}
          <div className="mb-4">
            <p className="text-sm font-medium mb-2">RCDO Link</p>
            {selectedRcdoNode && (
              <div data-testid="rcdo-selected-node" className="flex items-center gap-2 px-3 py-2 rounded-default border border-foreground/20 bg-foreground/5 mb-2 text-sm">
                <Check className="h-3.5 w-3.5 text-foreground shrink-0" aria-hidden="true" />
                <span className="flex-1 text-foreground">{selectedRcdoNode.title}</span>
                <Button variant="ghost" size="icon" type="button" onClick={() => handleChange("rcdoNodeId", "")} aria-label="Clear RCDO link" className="h-6 w-6 text-muted">
                  <X className="h-3 w-3" />
                </Button>
              </div>
            )}
            <Button variant="secondary" size="sm" type="button" onClick={() => setShowRcdoPicker((v) => !v)} data-testid="rcdo-picker-toggle" aria-expanded={showRcdoPicker}>
              {showRcdoPicker ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
              {showRcdoPicker ? "Close RCDO picker" : "Browse RCDO nodes"}
            </Button>
            {showRcdoPicker && (
              <div data-testid="rcdo-picker-panel" className="mt-2 rounded-default border border-border p-3 max-h-[250px] overflow-y-auto">
                <RcdoTreeView
                  nodes={rcdoTree}
                  selectedId={values.rcdoNodeId || null}
                  onSelect={(id) => {
                    const selectedNode = findNodeById(rcdoTree, id);
                    if (!selectedNode) return;
                    const rcdoError = getRcdoSelectionError(selectedNode);
                    if (rcdoError) { setErrors((prev) => ({ ...prev, rcdoNodeId: rcdoError })); return; }
                    handleChange("rcdoNodeId", id);
                    setShowRcdoPicker(false);
                  }}
                  statusFilter="active-only"
                  searchQuery=""
                />
              </div>
            )}
            {errors.rcdoNodeId && <span role="alert" className="block mt-1 text-xs text-danger">{errors.rcdoNodeId}</span>}

            {/* AI RCDO suggestion — appears when no RCDO is selected and title has enough text */}
            <AiErrorBoundary>
              <RcdoSuggestionInline
                planId={resolvedPlanId}
                currentTitle={values.title}
                currentDescription={values.description || undefined}
                chessPiece={values.chessPiece || undefined}
                currentRcdoNodeId={values.rcdoNodeId}
                onAccept={(rcdoNodeId) => handleChange("rcdoNodeId", rcdoNodeId)}
              />
            </AiErrorBoundary>
          </div>

          {/* Linked ticket */}
          <div className="mb-4">
            <Input id="commit-form-ticket" label="Linked Ticket" type="text" value={values.workItemId} onChange={(e) => handleChange("workItemId", e.target.value)} placeholder="Ticket ID (optional)" data-testid="commit-form-ticket" />
          </div>

          {/* API error */}
          {submitError && (
            <div role="alert" className="mb-4 rounded-default border border-neutral-300 bg-neutral-100 px-3 py-2 text-sm text-foreground font-semibold">{submitError}</div>
          )}

          <div className="flex gap-2 justify-end pt-4 border-t border-border">
            <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>Cancel</Button>
            <Button type="submit" variant="primary" disabled={submitting} data-testid="commit-form-submit">
              {submitting ? "Saving…" : isCreate ? "Add Commit" : "Save Changes"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
