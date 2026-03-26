/**
 * Modal confirmation dialog for archiving an RCDO node.
 */
import { AlertTriangle } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Dialog, DialogHeader, DialogTitle, DialogFooter } from "../ui/Dialog.js";

interface ArchiveConfirmDialogProps {
  readonly nodeName: string;
  readonly hasActiveChildren: boolean;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
  readonly submitting: boolean;
}

export function ArchiveConfirmDialog({ nodeName, hasActiveChildren, onConfirm, onCancel, submitting }: ArchiveConfirmDialogProps) {
  return (
    <Dialog open onClose={onCancel} aria-label={`Archive ${nodeName}`}>
      <DialogHeader>
        <DialogTitle>Archive "{nodeName}"?</DialogTitle>
      </DialogHeader>

      {hasActiveChildren ? (
        <div data-testid="archive-blocked-message" className="mb-4 flex items-start gap-2 rounded-default border border-red-200 bg-red-50 p-3 text-sm text-danger">
          <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" aria-hidden="true" />
          <span>
            <strong>Cannot archive.</strong> This node has active child nodes.
            Archive all children first before archiving this node.
          </span>
        </div>
      ) : (
        <p className="mb-4 text-sm text-muted">
          Archiving will hide this node from active planning views. Commits already linked to this node will retain
          their linkage, but the node will no longer be selectable for new commits.
        </p>
      )}

      <DialogFooter>
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>Cancel</Button>
        <Button
          variant="danger"
          onClick={onConfirm}
          disabled={hasActiveChildren || submitting}
          aria-disabled={hasActiveChildren || submitting}
          data-testid="archive-confirm-button"
        >
          {submitting ? "Archiving…" : "Archive"}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
