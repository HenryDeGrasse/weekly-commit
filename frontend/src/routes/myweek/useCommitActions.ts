/**
 * Commit CRUD handlers, form mode state, scope change state, and ticket form state.
 */
import { useState, useCallback } from "react";
import { useTicketApi } from "../../api/ticketHooks.js";
import { useHostBridge } from "../../host/HostProvider.js";
import type { CreateTicketPayload } from "../../api/ticketTypes.js";
import type {
  CommitResponse,
  ScopeChangeEventResponse,
  UpdateCommitPayload,
  CreateCommitPayload,
} from "../../api/planTypes.js";
import type { PlanApi } from "../../api/planApi.js";

export type FormMode = "create" | "edit" | null;
export type ScopeChangeMode = "add" | "edit" | "remove" | null;

export function useCommitActions(
  plan: { id: string; state: string } | undefined,
  commits: CommitResponse[],
  isLocked: boolean,
  planApi: PlanApi,
  refetchPlan: () => void,
  setLintRefreshKey: React.Dispatch<React.SetStateAction<number>>,
) {
  const bridge = useHostBridge();
  const currentUserId = bridge.context.authenticatedUser.id;
  const ticketApi = useTicketApi();

  // Form state
  const [formMode, setFormMode] = useState<FormMode>(null);
  const [editingCommit, setEditingCommit] = useState<CommitResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CommitResponse | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [commitFormInitialValues, setCommitFormInitialValues] = useState<Partial<CreateCommitPayload>>({});

  // Scope change state
  const [scopeChangeMode, setScopeChangeMode] = useState<ScopeChangeMode>(null);
  const [scopeChangeTargetCommit, setScopeChangeTargetCommit] = useState<CommitResponse | null>(null);
  const [pendingEditPayload, setPendingEditPayload] = useState<UpdateCommitPayload | null>(null);
  const [pendingCreatePayload, setPendingCreatePayload] = useState<CreateCommitPayload | null>(null);
  const [scopeChangeSaving, setScopeChangeSaving] = useState(false);
  const [scopeChangeEvents, setScopeChangeEvents] = useState<ScopeChangeEventResponse[]>([]);
  const [showTimeline, setShowTimeline] = useState(false);

  // Ticket form state
  const [ticketFormVisible, setTicketFormVisible] = useState(false);
  const [ticketFormInitialValues, setTicketFormInitialValues] = useState<Partial<CreateTicketPayload>>({});

  // ── Handlers ──────────────────────────────────────────────────────────────

  const handleOpenManualCreate = useCallback(() => {
    setCommitFormInitialValues({});
    setFormMode("create");
    setEditingCommit(null);
    setActionError(null);
  }, []);

  const handleOpenEdit = useCallback((commit: CommitResponse) => {
    setFormMode("edit");
    setEditingCommit(commit);
    setActionError(null);
  }, []);

  const handleFormCancel = useCallback(() => {
    setFormMode(null);
    setEditingCommit(null);
    setCommitFormInitialValues({});
  }, []);

  const handleCreateCommit = useCallback(
    async (payload: CreateCommitPayload) => {
      if (!plan) return;
      if (isLocked) {
        setPendingCreatePayload(payload);
        setScopeChangeMode("add");
        setScopeChangeTargetCommit(null);
        setFormMode(null);
        return;
      }
      await planApi.createCommit(plan.id, payload);
      refetchPlan();
      setFormMode(null);
      setLintRefreshKey((k) => k + 1);
    },
    [plan, isLocked, planApi, refetchPlan, setLintRefreshKey],
  );

  const handleUpdateCommit = useCallback(
    async (payload: UpdateCommitPayload) => {
      if (!plan || !editingCommit) return;
      if (isLocked) {
        setPendingEditPayload(payload);
        setScopeChangeMode("edit");
        setScopeChangeTargetCommit(editingCommit);
        setFormMode(null);
        setEditingCommit(null);
        return;
      }
      await planApi.updateCommit(plan.id, editingCommit.id, payload);
      refetchPlan();
      setFormMode(null);
      setEditingCommit(null);
      setLintRefreshKey((k) => k + 1);
    },
    [plan, editingCommit, isLocked, planApi, refetchPlan, setLintRefreshKey],
  );

  const handleDeleteRequest = useCallback(
    (commitId: string) => {
      const commit = commits.find((c) => c.id === commitId);
      if (!commit) return;
      if (isLocked) {
        setScopeChangeMode("remove");
        setScopeChangeTargetCommit(commit);
      } else {
        setDeleteTarget(commit);
      }
    },
    [commits, isLocked],
  );

  const handleDeleteConfirm = useCallback(async () => {
    if (!plan || !deleteTarget) return;
    try {
      await planApi.deleteCommit(plan.id, deleteTarget.id);
      refetchPlan();
      setLintRefreshKey((k) => k + 1);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Delete failed");
    } finally {
      setDeleteTarget(null);
    }
  }, [plan, deleteTarget, planApi, refetchPlan, setLintRefreshKey]);

  const handleReorder = useCallback(
    async (orderedIds: string[]) => {
      if (!plan) return;
      try {
        await planApi.reorderCommits(plan.id, orderedIds);
        refetchPlan();
        setLintRefreshKey((k) => k + 1);
      } catch (err) {
        setActionError(err instanceof Error ? err.message : "Reorder failed");
      }
    },
    [plan, planApi, refetchPlan, setLintRefreshKey],
  );

  const handleCreateTicketFromCommit = useCallback(
    (commit: CommitResponse) => {
      const teamId = bridge.context.currentTeam?.id;
      setTicketFormInitialValues({
        title: commit.title,
        ...(commit.description ? { description: commit.description } : {}),
        ...(commit.rcdoNodeId ? { rcdoNodeId: commit.rcdoNodeId } : {}),
        ...(commit.estimatePoints != null ? { estimatePoints: commit.estimatePoints } : {}),
        reporterUserId: currentUserId,
        ...(teamId ? { teamId } : {}),
      });
      setTicketFormVisible(true);
    },
    [currentUserId, bridge],
  );

  const handleTicketFormSubmit = useCallback(
    async (payload: CreateTicketPayload) => {
      await ticketApi.createTicket(payload);
      setTicketFormVisible(false);
      setTicketFormInitialValues({});
    },
    [ticketApi],
  );

  const handleScopeChangeConfirm = useCallback(
    async (reason: string) => {
      if (!plan) return;
      setScopeChangeSaving(true);
      setActionError(null);
      try {
        if (scopeChangeMode === "add" && pendingCreatePayload) {
          const result = await planApi.applyScopeChange(plan.id, {
            action: "ADD",
            reason,
            commitData: pendingCreatePayload,
          });
          setScopeChangeEvents(result.events);
        } else if (scopeChangeMode === "remove" && scopeChangeTargetCommit) {
          const result = await planApi.applyScopeChange(plan.id, {
            action: "REMOVE",
            reason,
            commitId: scopeChangeTargetCommit.id,
          });
          setScopeChangeEvents(result.events);
        } else if (scopeChangeMode === "edit" && scopeChangeTargetCommit && pendingEditPayload) {
          const result = await planApi.applyScopeChange(plan.id, {
            action: "EDIT",
            reason,
            commitId: scopeChangeTargetCommit.id,
            changes: pendingEditPayload,
          });
          setScopeChangeEvents(result.events);
        }
        refetchPlan();
      } catch (err) {
        setActionError(err instanceof Error ? err.message : "Scope change failed");
      } finally {
        setScopeChangeSaving(false);
        setScopeChangeMode(null);
        setScopeChangeTargetCommit(null);
        setPendingEditPayload(null);
        setPendingCreatePayload(null);
      }
    },
    [plan, scopeChangeMode, pendingCreatePayload, pendingEditPayload, scopeChangeTargetCommit, planApi, refetchPlan],
  );

  const handleScopeChangeCancel = useCallback(() => {
    setScopeChangeMode(null);
    setScopeChangeTargetCommit(null);
    setPendingEditPayload(null);
    setPendingCreatePayload(null);
  }, []);

  const handleLoadTimeline = useCallback(async () => {
    if (!plan) return;
    try {
      const result = await planApi.getScopeChangeTimeline(plan.id);
      setScopeChangeEvents(result.events);
      setShowTimeline(true);
    } catch {
      /* non-critical */
    }
  }, [plan, planApi]);

  return {
    // Form state
    formMode,
    setFormMode,
    editingCommit,
    setEditingCommit,
    deleteTarget,
    setDeleteTarget,
    actionError,
    setActionError,
    commitFormInitialValues,
    setCommitFormInitialValues,

    // Scope change state
    scopeChangeMode,
    setScopeChangeMode,
    scopeChangeTargetCommit,
    setScopeChangeTargetCommit,
    pendingEditPayload,
    pendingCreatePayload,
    setPendingCreatePayload,
    scopeChangeSaving,
    scopeChangeEvents,
    showTimeline,

    // Ticket form state
    ticketFormVisible,
    setTicketFormVisible,
    ticketFormInitialValues,
    setTicketFormInitialValues,

    // Handlers
    handleOpenManualCreate,
    handleOpenEdit,
    handleFormCancel,
    handleCreateCommit,
    handleUpdateCommit,
    handleDeleteRequest,
    handleDeleteConfirm,
    handleReorder,
    handleCreateTicketFromCommit,
    handleTicketFormSubmit,
    handleScopeChangeConfirm,
    handleScopeChangeCancel,
    handleLoadTimeline,
  };
}
