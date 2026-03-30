/**
 * RCDO management page.
 * Route: /weekly/rcdos
 */
import { useState, useCallback } from "react";
import { Skeleton } from "../components/ui/Skeleton.js";
import { Target, Network } from "lucide-react";
import { Button } from "../components/ui/Button.js";
import { Input } from "../components/ui/Input.js";
import { Badge } from "../components/ui/Badge.js";
import { useHostContext } from "../host/HostProvider.js";
import { EmptyState } from "../components/shared/EmptyState.js";
import { useRcdoTree, useRcdoApi } from "../api/rcdoHooks.js";
import { RcdoTreeView, type StatusFilter } from "../components/rcdo/RcdoTreeView.js";
import { RcdoBreadcrumb } from "../components/rcdo/RcdoBreadcrumb.js";
import { RcdoNodeForm } from "../components/rcdo/RcdoNodeForm.js";
import { ArchiveConfirmDialog } from "../components/rcdo/ArchiveConfirmDialog.js";
import { MoveNodeDialog } from "../components/rcdo/MoveNodeDialog.js";
import type { RcdoTreeNode, RcdoNodeType } from "../api/rcdoTypes.js";

interface RcdoPermissions {
  readonly canCreateRallyCry: boolean; readonly canEditRallyCry: boolean;
  readonly canCreateDO: boolean; readonly canEditDO: boolean;
  readonly canCreateOutcome: boolean; readonly canEditOutcome: boolean;
  readonly canArchive: boolean; readonly canMove: boolean; readonly isReadOnly: boolean;
}

function useRcdoPermissions(): RcdoPermissions {
  const { featureFlags } = useHostContext();
  const isAdmin = Boolean(featureFlags.rcdoAdminEnabled);
  const isManager = featureFlags.managerReviewEnabled;
  return {
    canCreateRallyCry: isAdmin, canEditRallyCry: isAdmin,
    canCreateDO: isAdmin || isManager, canEditDO: isAdmin || isManager,
    canCreateOutcome: isAdmin || isManager, canEditOutcome: isAdmin || isManager,
    canArchive: isAdmin || isManager, canMove: isAdmin || isManager,
    isReadOnly: !isAdmin && !isManager,
  };
}

function findNodePath(nodes: RcdoTreeNode[], targetId: string, currentPath: RcdoTreeNode[] = []): RcdoTreeNode[] {
  for (const node of nodes) {
    const path = [...currentPath, node];
    if (node.id === targetId) return path;
    const found = findNodePath(node.children, targetId, path);
    if (found.length > 0) return found;
  }
  return [];
}

function findNode(nodes: RcdoTreeNode[], targetId: string): RcdoTreeNode | null {
  for (const node of nodes) {
    if (node.id === targetId) return node;
    const found = findNode(node.children, targetId);
    if (found) return found;
  }
  return null;
}

function hasActiveChildren(node: RcdoTreeNode): boolean {
  return node.children.some((child) => child.status !== "ARCHIVED" || hasActiveChildren(child));
}

type PanelMode = "empty" | "detail" | "create" | "edit";

const STATUS_BADGE_VARIANT: Record<string, "success" | "draft" | "default"> = {
  ACTIVE: "success", DRAFT: "draft", ARCHIVED: "default",
};

export default function Rcdos() {
  const perms = useRcdoPermissions();
  const api = useRcdoApi();
  const { data: tree, loading, error, refetch } = useRcdoTree();

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [panelMode, setPanelMode] = useState<PanelMode>("empty");
  const [createNodeType, setCreateNodeType] = useState<RcdoNodeType | null>(null);
  const [createParentId, setCreateParentId] = useState<string | null>(null);
  const [showArchiveDialog, setShowArchiveDialog] = useState(false);
  const [showMoveDialog, setShowMoveDialog] = useState(false);
  const [dialogSubmitting, setDialogSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("active-only");

  const nodes = tree ?? [];
  const selectedNode = selectedId ? findNode(nodes, selectedId) : null;
  const selectedPath = selectedId ? findNodePath(nodes, selectedId) : [];

  const handleSelect = useCallback((id: string) => { setSelectedId(id); setPanelMode("detail"); setActionError(null); }, []);
  const handleBreadcrumbNavigate = useCallback((id: string) => { setSelectedId(id); setPanelMode("detail"); setActionError(null); }, []);

  function openCreate(nodeType: RcdoNodeType, parentId?: string) {
    setCreateNodeType(nodeType); setCreateParentId(parentId ?? null);
    setPanelMode("create"); setActionError(null);
  }

  async function handleCreate(payload: Parameters<ReturnType<typeof useRcdoApi>["createNode"]>[0]) {
    const created = await api.createNode(payload);
    if (created.status === "DRAFT") {
      await api.activateNode(created.id);
    }
    refetch(); setSelectedId(created.id); setPanelMode("detail");
  }

  function openEdit() { if (!selectedNode) return; setPanelMode("edit"); setActionError(null); }

  async function handleEdit(payload: Parameters<ReturnType<typeof useRcdoApi>["updateNode"]>[1]) {
    if (!selectedId) return;
    await api.updateNode(selectedId, payload); refetch(); setPanelMode("detail");
  }

  async function handleArchiveConfirm() {
    if (!selectedId) return;
    setDialogSubmitting(true);
    try { await api.archiveNode(selectedId); refetch(); setShowArchiveDialog(false); setPanelMode("empty"); setSelectedId(null); }
    catch (err) { setActionError(err instanceof Error ? err.message : "Archive failed"); setShowArchiveDialog(false); }
    finally { setDialogSubmitting(false); }
  }

  async function handleMoveConfirm(newParentId: string) {
    if (!selectedId) return;
    setDialogSubmitting(true);
    try { await api.moveNode(selectedId, newParentId); refetch(); setShowMoveDialog(false); setPanelMode("detail"); }
    catch (err) { setActionError(err instanceof Error ? err.message : "Move failed"); setShowMoveDialog(false); }
    finally { setDialogSubmitting(false); }
  }

  async function handleActivate() {
    if (!selectedId) return;
    setActionError(null);
    try { await api.activateNode(selectedId); refetch(); }
    catch (err) { setActionError(err instanceof Error ? err.message : "Activate failed"); }
  }

  function canEditSelected(): boolean {
    if (!selectedNode) return false;
    switch (selectedNode.nodeType) {
      case "RALLY_CRY": return perms.canEditRallyCry;
      case "DEFINING_OBJECTIVE": return perms.canEditDO;
      case "OUTCOME": return perms.canEditOutcome;
    }
  }

  return (
    <div className="flex flex-col gap-4 h-full" data-testid="page-rcdos">
      {/* Page heading */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="m-0 text-xl font-bold">RCDO Hierarchy</h2>
        {!perms.isReadOnly && (
          <div className="flex gap-2 flex-wrap">
            {perms.canCreateRallyCry && (
              <Button variant="primary" size="sm" onClick={() => openCreate("RALLY_CRY")} data-testid="create-rally-cry-btn">+ Rally Cry</Button>
            )}
            {perms.canCreateDO && (
              <Button variant="secondary" size="sm" onClick={() => openCreate("DEFINING_OBJECTIVE", selectedNode?.nodeType === "RALLY_CRY" ? selectedNode.id : undefined)} data-testid="create-do-btn">+ Defining Objective</Button>
            )}
            {perms.canCreateOutcome && (
              <Button variant="secondary" size="sm" onClick={() => openCreate("OUTCOME", selectedNode?.nodeType === "DEFINING_OBJECTIVE" ? selectedNode.id : undefined)} data-testid="create-outcome-btn">+ Outcome</Button>
            )}
          </div>
        )}
      </div>

      {/* Two-column layout */}
      <div className="grid gap-4 flex-1 min-h-0" style={{ gridTemplateColumns: "300px 1fr" }}>
        {/* Left panel */}
        <div className="rounded-default border border-border bg-surface p-4 flex flex-col gap-3 overflow-auto" aria-label="RCDO tree panel">
          <Input id="rcdo-search" label="Search" type="search" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="Filter by title…" aria-label="Search RCDO nodes" />

          <fieldset className="border-0 m-0 p-0">
            <legend className="text-sm font-medium mb-1.5">Show</legend>
            <div className="flex gap-3 flex-wrap">
              {(["all", "active-only", "archived-only"] as StatusFilter[]).map((value, i) => (
                <label key={value} className="flex items-center gap-1 text-sm cursor-pointer">
                  <input type="radio" name="rcdo-status-filter" value={value} checked={statusFilter === value} onChange={() => setStatusFilter(value)} />
                  {["All (incl. archived)", "Active only", "Archived"][i]}
                </label>
              ))}
            </div>
          </fieldset>

          {loading ? (
            <div role="status" aria-label="Loading RCDO hierarchy" className="flex flex-col gap-2 px-2">
              <span className="sr-only">Loading RCDO hierarchy…</span>
              {/* Tree skeleton — indent levels mimic RCDO depth */}
              <Skeleton className="h-7 w-full" />
              <Skeleton className="h-7 w-11/12 ml-5" />
              <Skeleton className="h-7 w-10/12 ml-10" />
              <Skeleton className="h-7 w-10/12 ml-10" />
              <Skeleton className="h-7 w-11/12 ml-5" />
              <Skeleton className="h-7 w-10/12 ml-10" />
            </div>
          ) : error ? (
            <div role="alert" className="text-sm text-foreground font-semibold">Failed to load: {error.message}</div>
          ) : nodes.length === 0 ? (
            <EmptyState
              data-testid="rcdo-tree-empty"
              icon={<Network className="h-9 w-9" />}
              title="No strategy nodes"
              description="Create your first Rally Cry to get started building your strategic hierarchy."
              action={
                perms.canCreateRallyCry ? (
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => { setCreateNodeType("RALLY_CRY"); setCreateParentId(null); setPanelMode("create"); }}
                    data-testid="empty-create-rally-cry-btn"
                  >
                    + Create Rally Cry
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <RcdoTreeView nodes={nodes} selectedId={selectedId} onSelect={handleSelect} statusFilter={statusFilter} searchQuery={searchQuery} />
          )}
        </div>

        {/* Right panel */}
        <div className="rounded-default border border-border bg-surface p-4 overflow-auto">
          {panelMode === "empty" && (
            <div className="flex flex-col items-center justify-center h-full min-h-[200px] text-muted text-center gap-2">
              <Target className="h-10 w-10 opacity-30" aria-hidden="true" />
              <p className="m-0 text-sm">Select a node from the tree to view details, or use the buttons above to create a new one.</p>
            </div>
          )}

          {panelMode === "detail" && selectedNode && (
            <div className="flex flex-col gap-4">
              <RcdoBreadcrumb path={selectedPath} onNavigate={handleBreadcrumbNavigate} />

              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <h3 className="m-0 text-lg font-semibold">{selectedNode.title}</h3>
                  <Badge variant={STATUS_BADGE_VARIANT[selectedNode.status] ?? "default"}>{selectedNode.status}</Badge>
                </div>
                <div className="text-xs text-muted mt-1">
                  {selectedNode.nodeType === "RALLY_CRY" ? "Rally Cry" : selectedNode.nodeType === "DEFINING_OBJECTIVE" ? "Defining Objective" : "Outcome"}
                </div>
              </div>

              {actionError && (
                <div role="alert" className="rounded-default border border-danger/30 bg-danger/5 px-3 py-2 text-sm text-foreground font-semibold">{actionError}</div>
              )}

              {!perms.isReadOnly && (
                <div className="flex gap-2 flex-wrap" data-testid="node-action-buttons">
                  {canEditSelected() && <Button variant="primary" size="sm" onClick={openEdit} data-testid="edit-node-btn">Edit</Button>}
                  {selectedNode.status === "DRAFT" && (
                    <Button variant="success" size="sm" onClick={() => void handleActivate()} data-testid="activate-node-btn">Activate</Button>
                  )}
                  {perms.canArchive && selectedNode.status !== "ARCHIVED" && (
                    <Button variant="danger" size="sm" onClick={() => setShowArchiveDialog(true)} data-testid="archive-node-btn">Archive</Button>
                  )}
                  {perms.canMove && selectedNode.nodeType !== "RALLY_CRY" && (
                    <Button variant="secondary" size="sm" onClick={() => setShowMoveDialog(true)} data-testid="move-node-btn">Move</Button>
                  )}
                </div>
              )}
              {perms.isReadOnly && <p className="text-sm text-muted italic" data-testid="readonly-label">Read-only view</p>}
              {selectedNode.children.length > 0 && (
                <div className="text-sm text-muted">{selectedNode.children.length} child node{selectedNode.children.length !== 1 ? "s" : ""}</div>
              )}
            </div>
          )}

          {panelMode === "create" && createNodeType && (
            <RcdoNodeForm mode="create" nodeType={createNodeType} {...(createParentId !== null ? { defaultParentId: createParentId } : {})} tree={nodes} onSubmit={handleCreate} onCancel={() => setPanelMode(selectedNode ? "detail" : "empty")} />
          )}

          {panelMode === "edit" && selectedNode && (
            <RcdoNodeForm mode="edit" nodeType={selectedNode.nodeType} status={selectedNode.status} initialValues={{ title: selectedNode.title }} tree={nodes} onSubmit={handleEdit} onCancel={() => setPanelMode("detail")} />
          )}
        </div>
      </div>

      {showArchiveDialog && selectedNode && (
        <ArchiveConfirmDialog nodeName={selectedNode.title} hasActiveChildren={hasActiveChildren(selectedNode)} onConfirm={handleArchiveConfirm} onCancel={() => setShowArchiveDialog(false)} submitting={dialogSubmitting} />
      )}
      {showMoveDialog && selectedNode && (
        <MoveNodeDialog nodeName={selectedNode.title} nodeType={selectedNode.nodeType} nodeId={selectedNode.id} tree={nodes} onConfirm={handleMoveConfirm} onCancel={() => setShowMoveDialog(false)} submitting={dialogSubmitting} />
      )}
    </div>
  );
}
