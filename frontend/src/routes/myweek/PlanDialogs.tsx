/**
 * All dialog/modal components for MyWeek: delete confirm, lock confirm,
 * scope change, commit form, ticket form, and AI composer.
 */
import { Button } from "../../components/ui/Button.js";
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "../../components/ui/Dialog.js";
import { CommitForm } from "../../components/myweek/CommitForm.js";
import { LockConfirmDialog } from "../../components/lock/LockConfirmDialog.js";
import { ScopeChangeDialog } from "../../components/lock/ScopeChangeDialog.js";
import { TicketForm } from "../../components/tickets/TicketForm.js";
import { AiCommitComposer } from "../../components/ai/AiCommitComposer.js";
import { AiErrorBoundary } from "./AiSections.js";
import type {
  CommitResponse,
  CreateCommitPayload,
  UpdateCommitPayload,
} from "../../api/planTypes.js";
import type { CreateTicketPayload } from "../../api/ticketTypes.js";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";
import type { FormMode, ScopeChangeMode } from "./useCommitActions.js";

// ── DeleteConfirmDialog ─────────────────────────────────────────────────────

export function DeleteConfirmDialog({
  title,
  onConfirm,
  onCancel,
}: {
  readonly title: string;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
}) {
  return (
    <Dialog open onClose={onCancel} aria-label="Delete commit" data-testid="delete-confirm-dialog">
      <DialogHeader>
        <DialogTitle>Delete commit?</DialogTitle>
        <DialogDescription>
          Are you sure you want to delete <strong>"{title}"</strong>? This cannot be undone.
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button variant="danger" onClick={onConfirm} data-testid="delete-confirm-btn">
          Delete
        </Button>
      </DialogFooter>
    </Dialog>
  );
}

// ── PlanDialogs ─────────────────────────────────────────────────────────────

interface PlanDialogsProps {
  readonly plan: { id: string; state: string; capacityBudgetPoints: number } | undefined;
  readonly commits: CommitResponse[];
  readonly isDraft: boolean;
  readonly isLocked: boolean;
  readonly rcdoTree: RcdoTreeNode[];
  readonly currentUserId: string;
  readonly weekLabel: string;

  // Form state
  readonly formMode: FormMode;
  readonly editingCommit: CommitResponse | null;
  readonly deleteTarget: CommitResponse | null;
  readonly commitFormInitialValues: Partial<CreateCommitPayload>;

  // Scope change state
  readonly scopeChangeMode: ScopeChangeMode;
  readonly scopeChangeTargetCommit: CommitResponse | null;
  readonly pendingEditPayload: UpdateCommitPayload | null;
  readonly pendingCreatePayload: CreateCommitPayload | null;
  readonly scopeChangeSaving: boolean;

  // Lock state
  readonly showLockConfirm: boolean;
  readonly lockLoading: boolean;

  // Ticket form state
  readonly ticketFormVisible: boolean;
  readonly ticketFormInitialValues: Partial<CreateTicketPayload>;

  // AI composer state
  readonly showAiComposer: boolean;
  readonly currentTeamId?: string | undefined;

  // Handlers
  readonly onCreateCommit: (payload: CreateCommitPayload) => Promise<void>;
  readonly onUpdateCommit: (payload: UpdateCommitPayload) => Promise<void>;
  readonly onFormCancel: () => void;
  readonly onDeleteConfirm: () => void;
  readonly onDeleteCancel: () => void;
  readonly onLockConfirm: () => void;
  readonly onLockCancel: () => void;
  readonly onScopeChangeConfirm: (reason: string) => void;
  readonly onScopeChangeCancel: () => void;
  readonly onTicketFormSubmit: (payload: CreateTicketPayload) => Promise<void>;
  readonly onTicketFormCancel: () => void;
  readonly onAiComposerSubmit: (payload: CreateCommitPayload) => Promise<void>;
  readonly onAiComposerSwitchToManual: (preFilled: Partial<CreateCommitPayload>) => void;
  readonly onAiComposerCancel: () => void;
  readonly onScopeChangeCreateSubmit: (payload: CreateCommitPayload) => Promise<void>;
}

export function PlanDialogs({
  plan,
  commits,
  isDraft,
  isLocked,
  rcdoTree,
  currentUserId,
  weekLabel,
  formMode,
  editingCommit,
  deleteTarget,
  commitFormInitialValues,
  scopeChangeMode,
  scopeChangeTargetCommit,
  pendingEditPayload,
  pendingCreatePayload,
  scopeChangeSaving,
  showLockConfirm,
  lockLoading,
  ticketFormVisible,
  ticketFormInitialValues,
  showAiComposer,
  currentTeamId,
  onCreateCommit,
  onUpdateCommit,
  onFormCancel,
  onDeleteConfirm,
  onDeleteCancel,
  onLockConfirm,
  onLockCancel,
  onScopeChangeConfirm,
  onScopeChangeCancel,
  onTicketFormSubmit,
  onTicketFormCancel,
  onAiComposerSubmit,
  onAiComposerSwitchToManual,
  onAiComposerCancel,
  onScopeChangeCreateSubmit,
}: PlanDialogsProps) {
  return (
    <>
      {/* AI Commit Composer */}
      {showAiComposer && plan && (isDraft || isLocked) && (
        <AiErrorBoundary>
          <AiCommitComposer
            planId={plan.id}
            existingCommits={commits}
            onSubmit={onAiComposerSubmit}
            onSwitchToManual={onAiComposerSwitchToManual}
            onCancel={onAiComposerCancel}
          />
        </AiErrorBoundary>
      )}

      {/* CommitForm modals */}
      {formMode === "create" && plan && !isLocked && (
        <CommitForm
          mode="create"
          planId={plan.id}
          rcdoTree={rcdoTree}
          existingCommits={commits}
          onSubmit={onCreateCommit}
          onCancel={onFormCancel}
          initialValues={commitFormInitialValues}
        />
      )}
      {formMode === "edit" && plan && editingCommit && (
        <CommitForm
          mode="edit"
          commit={editingCommit}
          rcdoTree={rcdoTree}
          existingCommits={commits}
          onSubmit={onUpdateCommit}
          onCancel={onFormCancel}
        />
      )}
      {scopeChangeMode === "add" && !pendingCreatePayload && plan && (
        <CommitForm
          mode="create"
          planId={plan.id}
          rcdoTree={rcdoTree}
          existingCommits={commits}
          onSubmit={onScopeChangeCreateSubmit}
          onCancel={onScopeChangeCancel}
        />
      )}

      {/* Delete confirm */}
      {deleteTarget && !isLocked && (
        <DeleteConfirmDialog title={deleteTarget.title} onConfirm={onDeleteConfirm} onCancel={onDeleteCancel} />
      )}

      {/* Ticket form */}
      {ticketFormVisible && (
        <TicketForm
          mode="create"
          initialValues={ticketFormInitialValues}
          currentUserId={currentUserId}
          {...(currentTeamId ? { currentTeamId } : {})}
          rcdoTree={rcdoTree}
          onSubmit={onTicketFormSubmit}
          onCancel={onTicketFormCancel}
        />
      )}

      {/* Lock confirm */}
      {showLockConfirm && plan && (
        <LockConfirmDialog
          commits={commits}
          capacityBudgetPoints={plan.capacityBudgetPoints}
          weekLabel={weekLabel}
          onConfirm={onLockConfirm}
          onCancel={onLockCancel}
          isLocking={lockLoading}
        />
      )}

      {/* Scope change dialogs */}
      {scopeChangeMode === "add" && pendingCreatePayload && (
        <ScopeChangeDialog
          action="ADD"
          commit={null}
          newCommitTitle={pendingCreatePayload.title}
          onConfirm={(reason) => void onScopeChangeConfirm(reason)}
          onCancel={onScopeChangeCancel}
          isSubmitting={scopeChangeSaving}
        />
      )}
      {scopeChangeMode === "edit" && scopeChangeTargetCommit && pendingEditPayload && (
        <ScopeChangeDialog
          action="EDIT"
          commit={scopeChangeTargetCommit}
          proposedChanges={pendingEditPayload as Partial<CommitResponse>}
          onConfirm={(reason) => void onScopeChangeConfirm(reason)}
          onCancel={onScopeChangeCancel}
          isSubmitting={scopeChangeSaving}
        />
      )}
      {scopeChangeMode === "remove" && scopeChangeTargetCommit && (
        <ScopeChangeDialog
          action="REMOVE"
          commit={scopeChangeTargetCommit}
          onConfirm={(reason) => void onScopeChangeConfirm(reason)}
          onCancel={onScopeChangeCancel}
          isSubmitting={scopeChangeSaving}
        />
      )}
    </>
  );
}
