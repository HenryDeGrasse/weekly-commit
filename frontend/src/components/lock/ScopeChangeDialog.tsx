/**
 * ScopeChangeDialog — captures reason for a post-lock scope change.
 */
import { useState, type FormEvent } from "react";
import { Lock, X } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Select } from "../ui/Select.js";
import { cn } from "../../lib/utils.js";
import type { CommitResponse, ScopeChangeAction, ChessPiece, EstimatePoints } from "../../api/planTypes.js";

const REASON_OPTIONS: { value: string; label: string }[] = [
  { value: "Priority shift — more important work emerged", label: "Priority shift — more important work emerged" },
  { value: "Scope expanded — original estimate was too small", label: "Scope expanded — original estimate was too small" },
  { value: "Blocked — external dependency", label: "Blocked — external dependency" },
  { value: "Resource unavailable", label: "Resource unavailable" },
  { value: "Technical obstacle discovered", label: "Technical obstacle discovered" },
  { value: "Business context changed", label: "Business context changed" },
  { value: "Error correction — wrong commit included at lock", label: "Error correction — wrong commit included at lock" },
  { value: "Other", label: "Other" },
];

const CHESS_PIECE_LABELS: Record<ChessPiece, string> = {
  KING: "♔ King", QUEEN: "♕ Queen", ROOK: "♖ Rook", BISHOP: "♗ Bishop", KNIGHT: "♘ Knight", PAWN: "♙ Pawn",
};

interface FieldChange { readonly field: string; readonly before: string; readonly after: string; }

function computeChanges(before: CommitResponse, after: Partial<CommitResponse>): FieldChange[] {
  const changes: FieldChange[] = [];
  if (after.title !== undefined && after.title !== before.title) changes.push({ field: "Title", before: before.title, after: after.title });
  if (after.chessPiece !== undefined && after.chessPiece !== before.chessPiece) changes.push({ field: "Chess Piece", before: CHESS_PIECE_LABELS[before.chessPiece], after: CHESS_PIECE_LABELS[after.chessPiece as ChessPiece] });
  if (after.estimatePoints !== undefined && after.estimatePoints !== before.estimatePoints) changes.push({ field: "Estimate", before: before.estimatePoints != null ? `${before.estimatePoints} pts` : "—", after: `${after.estimatePoints} pts` });
  if (after.rcdoNodeId !== undefined && after.rcdoNodeId !== before.rcdoNodeId) changes.push({ field: "RCDO", before: before.rcdoNodeId ?? "—", after: (after.rcdoNodeId as string) ?? "—" });
  if (after.description !== undefined && after.description !== before.description) changes.push({ field: "Description", before: before.description ?? "—", after: (after.description as string) ?? "—" });
  return changes;
}

export interface ScopeChangeDialogProps {
  readonly action: ScopeChangeAction;
  readonly commit: CommitResponse | null;
  readonly proposedChanges?: Partial<CommitResponse>;
  readonly newCommitTitle?: string;
  readonly onConfirm: (reason: string) => void;
  readonly onCancel: () => void;
  readonly isSubmitting?: boolean;
}

const ACTION_LABELS: Record<ScopeChangeAction, string> = {
  ADD: "Add Commit (Post-lock)", REMOVE: "Remove Commit (Post-lock)", EDIT: "Edit Commit (Post-lock)",
};
const ACTION_CLS: Record<ScopeChangeAction, string> = {
  ADD: "text-success", REMOVE: "text-danger", EDIT: "text-primary",
};
const ACTION_BTN_VARIANT: Record<ScopeChangeAction, "success" | "danger" | "primary"> = {
  ADD: "success", REMOVE: "danger", EDIT: "primary",
};

const thCls = "px-2.5 py-1.5 text-left text-xs font-semibold border-b border-border";

export function ScopeChangeDialog({ action, commit, proposedChanges, newCommitTitle, onConfirm, onCancel, isSubmitting = false }: ScopeChangeDialogProps) {
  const [selectedReason, setSelectedReason] = useState("");
  const [reasonText, setReasonText] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);

  const fieldChanges = action === "EDIT" && commit && proposedChanges ? computeChanges(commit, proposedChanges) : [];

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const reason = selectedReason === "Other" ? reasonText.trim() : selectedReason;
    if (!reason) { setReasonError("Please select or enter a reason"); return; }
    setReasonError(null);
    onConfirm(reason);
  }

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="scope-change-dialog-title" data-testid="scope-change-dialog"
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/40 backdrop-blur-[2px]">
      <div className="w-full max-w-lg rounded-lg border border-border bg-surface p-6 shadow-xl max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <h3 id="scope-change-dialog-title" className={cn("m-0 text-base font-semibold", ACTION_CLS[action])}>
            {ACTION_LABELS[action]}
          </h3>
          <Button variant="ghost" size="icon" onClick={onCancel} aria-label="Close" className="h-7 w-7">
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* Locked notice */}
        <div data-testid="scope-change-locked-notice" className="mb-4 flex items-center gap-2 rounded-default border border-neutral-200 bg-neutral-50 px-3 py-2.5 text-xs text-foreground">
          <Lock className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          This plan is <strong>locked</strong>. This change will be recorded in the scope-change timeline with your reason.
        </div>

        {/* What's changing */}
        {action === "ADD" && newCommitTitle && (
          <div data-testid="scope-change-add-preview" className="mb-4 rounded-default border border-foreground/20 bg-foreground/5 px-3 py-2.5 text-sm">
            <span className="font-semibold text-foreground">+ Adding:</span> {newCommitTitle}
          </div>
        )}
        {action === "REMOVE" && commit && (
          <div data-testid="scope-change-remove-preview" className="mb-4 rounded-default border border-neutral-300 bg-neutral-100 px-3 py-2.5 text-sm">
            <span className="font-semibold text-foreground">− Removing:</span> {commit.title}
          </div>
        )}
        {action === "EDIT" && fieldChanges.length > 0 && (
          <div data-testid="scope-change-edit-preview" className="mb-4 overflow-hidden rounded-default border border-border">
            <div className="bg-background px-3 py-2 text-xs font-semibold uppercase tracking-wider text-muted">
              Changes to: {commit?.title}
            </div>
            <table className="w-full border-collapse" aria-label="Commit field changes">
              <thead>
                <tr>
                  <th className={thCls}>Field</th>
                  <th className={cn(thCls, "text-foreground")}>Before</th>
                  <th className={cn(thCls, "text-foreground")}>After</th>
                </tr>
              </thead>
              <tbody>
                {fieldChanges.map((change) => (
                  <tr key={change.field} data-testid="scope-change-field-row">
                    <td className="px-2.5 py-1.5 text-xs font-semibold border-b border-border">{change.field}</td>
                    <td className="px-2.5 py-1.5 text-xs text-foreground bg-neutral-100 border-b border-border line-through">{change.before}</td>
                    <td className="px-2.5 py-1.5 text-xs text-foreground bg-foreground/5 border-b border-border font-semibold">{change.after}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Reason form */}
        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-4">
            <Select
              label="Reason for change *"
              id="scope-change-reason-select"
              value={selectedReason}
              onChange={(e) => { setSelectedReason(e.target.value); setReasonError(null); }}
              aria-required="true"
              aria-describedby={reasonError ? "scope-reason-error" : undefined}
              data-testid="scope-change-reason-select"
              error={reasonError ?? undefined}
            >
              <option value="">— Select a reason —</option>
              {REASON_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </Select>
          </div>

          {selectedReason === "Other" && (
            <div className="mb-4 flex flex-col gap-1.5">
              <label htmlFor="scope-change-reason-text" className="text-sm font-medium">
                Describe the reason <span aria-hidden="true" className="text-danger">*</span>
              </label>
              <textarea
                id="scope-change-reason-text"
                value={reasonText}
                onChange={(e) => setReasonText(e.target.value)}
                rows={3}
                data-testid="scope-change-reason-text"
                className="w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:border-primary"
                placeholder="Please describe why this change is needed…"
              />
            </div>
          )}

          <div className="mt-5 flex items-center justify-end gap-2 pt-4 border-t border-border">
            <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting} data-testid="scope-change-cancel">
              Cancel
            </Button>
            <Button type="submit" variant={ACTION_BTN_VARIANT[action]} disabled={isSubmitting} data-testid="scope-change-confirm">
              {isSubmitting ? "Saving…" : "Confirm Change"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export type { EstimatePoints };
