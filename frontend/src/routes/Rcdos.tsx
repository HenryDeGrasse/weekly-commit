/**
 * RCDO management page.
 * Route: /weekly/rcdos
 *
 * Layout: left panel (tree + filters) / right panel (node detail or form).
 * Permissions: Admins manage Rally Cries; Managers manage DOs and Outcomes;
 *              ICs see a read-only view.
 */
import { useState, useCallback } from "react";
import { useHostContext } from "../host/HostProvider.js";
import { useRcdoTree, useRcdoApi } from "../api/rcdoHooks.js";
import { RcdoTreeView, type StatusFilter } from "../components/rcdo/RcdoTreeView.js";
import { RcdoBreadcrumb } from "../components/rcdo/RcdoBreadcrumb.js";
import { RcdoNodeForm } from "../components/rcdo/RcdoNodeForm.js";
import { ArchiveConfirmDialog } from "../components/rcdo/ArchiveConfirmDialog.js";
import { MoveNodeDialog } from "../components/rcdo/MoveNodeDialog.js";
import type { RcdoTreeNode, RcdoNodeType } from "../api/rcdoTypes.js";

// ── Permission hook ───────────────────────────────────────────────────────────

interface RcdoPermissions {
  readonly canCreateRallyCry: boolean;
  readonly canEditRallyCry: boolean;
  readonly canCreateDO: boolean;
  readonly canEditDO: boolean;
  readonly canCreateOutcome: boolean;
  readonly canEditOutcome: boolean;
  readonly canArchive: boolean;
  readonly canMove: boolean;
  readonly isReadOnly: boolean;
}

function useRcdoPermissions(): RcdoPermissions {
  const { featureFlags } = useHostContext();
  // rcdoAdminEnabled grants full admin; managerReviewEnabled grants DO/Outcome access
  const isAdmin = Boolean(featureFlags.rcdoAdminEnabled);
  const isManager = featureFlags.managerReviewEnabled;
  return {
    canCreateRallyCry: isAdmin,
    canEditRallyCry: isAdmin,
    canCreateDO: isAdmin || isManager,
    canEditDO: isAdmin || isManager,
    canCreateOutcome: isAdmin || isManager,
    canEditOutcome: isAdmin || isManager,
    canArchive: isAdmin || isManager,
    canMove: isAdmin || isManager,
    isReadOnly: !isAdmin && !isManager,
  };
}

// ── Tree helpers ──────────────────────────────────────────────────────────────

/** Find the path from root to a target node (inclusive). Returns [] if not found. */
function findNodePath(
  nodes: RcdoTreeNode[],
  targetId: string,
  currentPath: RcdoTreeNode[] = [],
): RcdoTreeNode[] {
  for (const node of nodes) {
    const path = [...currentPath, node];
    if (node.id === targetId) return path;
    const found = findNodePath(node.children, targetId, path);
    if (found.length > 0) return found;
  }
  return [];
}

/** Find a node by id in the tree. */
function findNode(
  nodes: RcdoTreeNode[],
  targetId: string,
): RcdoTreeNode | null {
  for (const node of nodes) {
    if (node.id === targetId) return node;
    const found = findNode(node.children, targetId);
    if (found) return found;
  }
  return null;
}

/** Return true if any immediate or descendant child has status !== ARCHIVED. */
function hasActiveChildren(node: RcdoTreeNode): boolean {
  return node.children.some(
    (child) => child.status !== "ARCHIVED" || hasActiveChildren(child),
  );
}

// ── Panel modes ───────────────────────────────────────────────────────────────

type PanelMode = "empty" | "detail" | "create" | "edit";

// ── Rcdos Page ────────────────────────────────────────────────────────────────

export default function Rcdos() {
  const perms = useRcdoPermissions();
  const api = useRcdoApi();
  const { data: tree, loading, error, refetch } = useRcdoTree();

  // Selection & panel state
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [panelMode, setPanelMode] = useState<PanelMode>("empty");
  const [createNodeType, setCreateNodeType] = useState<RcdoNodeType | null>(null);
  const [createParentId, setCreateParentId] = useState<string | null>(null);

  // Dialogs
  const [showArchiveDialog, setShowArchiveDialog] = useState(false);
  const [showMoveDialog, setShowMoveDialog] = useState(false);
  const [dialogSubmitting, setDialogSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // Left-panel controls
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");

  const nodes = tree ?? [];

  // Derived state for selected node
  const selectedNode = selectedId ? findNode(nodes, selectedId) : null;
  const selectedPath = selectedId ? findNodePath(nodes, selectedId) : [];

  // ── Selection ────────────────────────────────────────────────────────────

  const handleSelect = useCallback((id: string) => {
    setSelectedId(id);
    setPanelMode("detail");
    setActionError(null);
  }, []);

  const handleBreadcrumbNavigate = useCallback((id: string) => {
    setSelectedId(id);
    setPanelMode("detail");
    setActionError(null);
  }, []);

  // ── Create ───────────────────────────────────────────────────────────────

  function openCreate(nodeType: RcdoNodeType, parentId?: string) {
    setCreateNodeType(nodeType);
    setCreateParentId(parentId ?? null);
    setPanelMode("create");
    setActionError(null);
  }

  async function handleCreate(
    payload: Parameters<ReturnType<typeof useRcdoApi>["createNode"]>[0],
  ) {
    const created = await api.createNode(payload);
    refetch();
    setSelectedId(created.id);
    setPanelMode("detail");
  }

  // ── Edit ─────────────────────────────────────────────────────────────────

  function openEdit() {
    if (!selectedNode) return;
    setPanelMode("edit");
    setActionError(null);
  }

  async function handleEdit(
    payload: Parameters<ReturnType<typeof useRcdoApi>["updateNode"]>[1],
  ) {
    if (!selectedId) return;
    await api.updateNode(selectedId, payload);
    refetch();
    setPanelMode("detail");
  }

  // ── Archive ──────────────────────────────────────────────────────────────

  async function handleArchiveConfirm() {
    if (!selectedId) return;
    setDialogSubmitting(true);
    try {
      await api.archiveNode(selectedId);
      refetch();
      setShowArchiveDialog(false);
      setPanelMode("empty");
      setSelectedId(null);
    } catch (err) {
      setActionError(
        err instanceof Error ? err.message : "Archive failed",
      );
      setShowArchiveDialog(false);
    } finally {
      setDialogSubmitting(false);
    }
  }

  // ── Move ─────────────────────────────────────────────────────────────────

  async function handleMoveConfirm(newParentId: string) {
    if (!selectedId) return;
    setDialogSubmitting(true);
    try {
      await api.moveNode(selectedId, newParentId);
      refetch();
      setShowMoveDialog(false);
      setPanelMode("detail");
    } catch (err) {
      setActionError(
        err instanceof Error ? err.message : "Move failed",
      );
      setShowMoveDialog(false);
    } finally {
      setDialogSubmitting(false);
    }
  }

  // ── Can edit/archive for the selected node type ──────────────────────────

  function canEditSelected(): boolean {
    if (!selectedNode) return false;
    switch (selectedNode.nodeType) {
      case "RALLY_CRY": return perms.canEditRallyCry;
      case "DEFINING_OBJECTIVE": return perms.canEditDO;
      case "OUTCOME": return perms.canEditOutcome;
    }
  }

  // ── Render ───────────────────────────────────────────────────────────────

  const sectionCardStyle: React.CSSProperties = {
    background: "var(--color-surface)",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    padding: "1rem",
  };

  const btnPrimary: React.CSSProperties = {
    padding: "0.375rem 0.75rem",
    border: "none",
    borderRadius: "var(--border-radius)",
    background: "var(--color-primary)",
    color: "#fff",
    cursor: "pointer",
    fontSize: "0.875rem",
    fontFamily: "inherit",
  };

  const btnSecondary: React.CSSProperties = {
    padding: "0.375rem 0.75rem",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    background: "var(--color-surface)",
    color: "var(--color-text)",
    cursor: "pointer",
    fontSize: "0.875rem",
    fontFamily: "inherit",
  };

  const btnDanger: React.CSSProperties = {
    ...btnSecondary,
    color: "var(--color-danger)",
    borderColor: "var(--color-danger)",
  };

  return (
    <div
      className="route-page"
      data-testid="page-rcdos"
      style={{ display: "flex", flexDirection: "column", gap: "1rem", height: "100%" }}
    >
      {/* Page heading */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "0.5rem" }}>
        <h2 style={{ margin: 0, fontSize: "1.25rem" }}>RCDO Hierarchy</h2>
        {/* Top-level create buttons */}
        {!perms.isReadOnly && (
          <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
            {perms.canCreateRallyCry && (
              <button
                style={btnPrimary}
                onClick={() => openCreate("RALLY_CRY")}
                data-testid="create-rally-cry-btn"
              >
                + Rally Cry
              </button>
            )}
            {perms.canCreateDO && (
              <button
                style={btnSecondary}
                onClick={() =>
                  openCreate(
                    "DEFINING_OBJECTIVE",
                    selectedNode?.nodeType === "RALLY_CRY"
                      ? selectedNode.id
                      : undefined,
                  )
                }
                data-testid="create-do-btn"
              >
                + Defining Objective
              </button>
            )}
            {perms.canCreateOutcome && (
              <button
                style={btnSecondary}
                onClick={() =>
                  openCreate(
                    "OUTCOME",
                    selectedNode?.nodeType === "DEFINING_OBJECTIVE"
                      ? selectedNode.id
                      : undefined,
                  )
                }
                data-testid="create-outcome-btn"
              >
                + Outcome
              </button>
            )}
          </div>
        )}
      </div>

      {/* Two-column layout */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "300px 1fr",
          gap: "1rem",
          flex: 1,
          minHeight: 0,
        }}
      >
        {/* ── Left panel: tree + filters ── */}
        <div
          style={{ ...sectionCardStyle, display: "flex", flexDirection: "column", gap: "0.75rem", overflow: "auto" }}
          aria-label="RCDO tree panel"
        >
          {/* Search */}
          <div>
            <label htmlFor="rcdo-search" style={{ display: "block", marginBottom: "0.25rem", fontSize: "0.875rem", fontWeight: 600 }}>
              Search
            </label>
            <input
              id="rcdo-search"
              type="search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Filter by title…"
              style={{
                width: "100%",
                padding: "0.375rem 0.5rem",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                fontSize: "0.875rem",
                fontFamily: "inherit",
                boxSizing: "border-box",
              }}
              aria-label="Search RCDO nodes"
            />
          </div>

          {/* Status filter */}
          <div>
            <fieldset style={{ border: "none", margin: 0, padding: 0 }}>
              <legend style={{ fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.375rem" }}>
                Show
              </legend>
              <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                {(
                  [
                    ["all", "All"],
                    ["active-only", "Active"],
                    ["archived-only", "Archived"],
                  ] as [StatusFilter, string][]
                ).map(([value, label]) => (
                  <label
                    key={value}
                    style={{ display: "flex", alignItems: "center", gap: "0.25rem", fontSize: "0.875rem", cursor: "pointer" }}
                  >
                    <input
                      type="radio"
                      name="rcdo-status-filter"
                      value={value}
                      checked={statusFilter === value}
                      onChange={() => setStatusFilter(value)}
                    />
                    {label}
                  </label>
                ))}
              </div>
            </fieldset>
          </div>

          {/* Tree */}
          {loading ? (
            <div
              role="status"
              aria-label="Loading RCDO hierarchy"
              style={{ color: "var(--color-text-muted)", fontSize: "0.875rem", padding: "0.5rem" }}
            >
              Loading…
            </div>
          ) : error ? (
            <div
              role="alert"
              style={{ color: "var(--color-danger)", fontSize: "0.875rem" }}
            >
              Failed to load: {error.message}
            </div>
          ) : (
            <RcdoTreeView
              nodes={nodes}
              selectedId={selectedId}
              onSelect={handleSelect}
              statusFilter={statusFilter}
              searchQuery={searchQuery}
            />
          )}
        </div>

        {/* ── Right panel: detail / form ── */}
        <div style={{ ...sectionCardStyle, overflow: "auto" }}>
          {panelMode === "empty" && (
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                height: "100%",
                minHeight: "200px",
                color: "var(--color-text-muted)",
                textAlign: "center",
                gap: "0.5rem",
              }}
            >
              <span style={{ fontSize: "2rem" }}>🎯</span>
              <p style={{ margin: 0, fontSize: "0.9rem" }}>
                Select a node from the tree to view details, or use the buttons
                above to create a new one.
              </p>
            </div>
          )}

          {panelMode === "detail" && selectedNode && (
            <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
              {/* Breadcrumb */}
              <RcdoBreadcrumb
                path={selectedPath}
                onNavigate={handleBreadcrumbNavigate}
              />

              {/* Node title & status */}
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexWrap: "wrap" }}>
                  <h3 style={{ margin: 0, fontSize: "1.125rem" }}>
                    {selectedNode.title}
                  </h3>
                  <span
                    style={{
                      padding: "2px 10px",
                      borderRadius: "999px",
                      fontSize: "0.75rem",
                      fontWeight: 700,
                      background:
                        selectedNode.status === "ACTIVE"
                          ? "#d1fae5"
                          : selectedNode.status === "DRAFT"
                            ? "#fef3c7"
                            : "#f3f4f6",
                      color:
                        selectedNode.status === "ACTIVE"
                          ? "#065f46"
                          : selectedNode.status === "DRAFT"
                            ? "#92400e"
                            : "#6b7280",
                    }}
                  >
                    {selectedNode.status}
                  </span>
                </div>
                <div style={{ fontSize: "0.8rem", color: "var(--color-text-muted)", marginTop: "0.25rem" }}>
                  {selectedNode.nodeType === "RALLY_CRY"
                    ? "Rally Cry"
                    : selectedNode.nodeType === "DEFINING_OBJECTIVE"
                      ? "Defining Objective"
                      : "Outcome"}
                </div>
              </div>

              {/* Action error */}
              {actionError && (
                <div
                  role="alert"
                  style={{
                    color: "var(--color-danger)",
                    fontSize: "0.875rem",
                    padding: "0.5rem",
                    background: "#fef2f2",
                    borderRadius: "var(--border-radius)",
                  }}
                >
                  {actionError}
                </div>
              )}

              {/* Action buttons */}
              {!perms.isReadOnly && (
                <div
                  style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}
                  data-testid="node-action-buttons"
                >
                  {canEditSelected() && (
                    <button
                      style={btnPrimary}
                      onClick={openEdit}
                      data-testid="edit-node-btn"
                    >
                      Edit
                    </button>
                  )}
                  {perms.canArchive && selectedNode.status !== "ARCHIVED" && (
                    <button
                      style={btnDanger}
                      onClick={() => setShowArchiveDialog(true)}
                      data-testid="archive-node-btn"
                    >
                      Archive
                    </button>
                  )}
                  {perms.canMove &&
                    selectedNode.nodeType !== "RALLY_CRY" && (
                      <button
                        style={btnSecondary}
                        onClick={() => setShowMoveDialog(true)}
                        data-testid="move-node-btn"
                      >
                        Move
                      </button>
                    )}
                </div>
              )}

              {/* Read-only label for ICs */}
              {perms.isReadOnly && (
                <p
                  style={{
                    fontSize: "0.875rem",
                    color: "var(--color-text-muted)",
                    fontStyle: "italic",
                  }}
                  data-testid="readonly-label"
                >
                  Read-only view
                </p>
              )}

              {/* Children count */}
              {selectedNode.children.length > 0 && (
                <div style={{ fontSize: "0.875rem", color: "var(--color-text-muted)" }}>
                  {selectedNode.children.length} child node
                  {selectedNode.children.length !== 1 ? "s" : ""}
                </div>
              )}
            </div>
          )}

          {panelMode === "create" && createNodeType && (
            <RcdoNodeForm
              mode="create"
              nodeType={createNodeType}
              {...(createParentId !== null
                ? { defaultParentId: createParentId }
                : {})}
              tree={nodes}
              onSubmit={handleCreate}
              onCancel={() => setPanelMode(selectedNode ? "detail" : "empty")}
            />
          )}

          {panelMode === "edit" && selectedNode && (
            <RcdoNodeForm
              mode="edit"
              nodeType={selectedNode.nodeType}
              status={selectedNode.status}
              initialValues={{
                title: selectedNode.title,
              }}
              tree={nodes}
              onSubmit={handleEdit}
              onCancel={() => setPanelMode("detail")}
            />
          )}
        </div>
      </div>

      {/* ── Dialogs ── */}
      {showArchiveDialog && selectedNode && (
        <ArchiveConfirmDialog
          nodeName={selectedNode.title}
          hasActiveChildren={hasActiveChildren(selectedNode)}
          onConfirm={handleArchiveConfirm}
          onCancel={() => setShowArchiveDialog(false)}
          submitting={dialogSubmitting}
        />
      )}

      {showMoveDialog && selectedNode && (
        <MoveNodeDialog
          nodeName={selectedNode.title}
          nodeType={selectedNode.nodeType}
          nodeId={selectedNode.id}
          tree={nodes}
          onConfirm={handleMoveConfirm}
          onCancel={() => setShowMoveDialog(false)}
          submitting={dialogSubmitting}
        />
      )}
    </div>
  );
}
