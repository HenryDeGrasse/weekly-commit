/**
 * Expandable tree view of the full RCDO hierarchy.
 * Supports expand/collapse, status filtering, and title search.
 */
import { useState, useCallback } from "react";
import type { RcdoTreeNode, RcdoNodeType, RcdoNodeStatus } from "../../api/rcdoTypes.js";

export type StatusFilter = "all" | "active-only" | "archived-only";

const NODE_TYPE_ICONS: Record<RcdoNodeType, string> = {
  RALLY_CRY: "🎯",
  DEFINING_OBJECTIVE: "📋",
  OUTCOME: "✅",
};

const NODE_TYPE_LABELS: Record<RcdoNodeType, string> = {
  RALLY_CRY: "Rally Cry",
  DEFINING_OBJECTIVE: "Defining Objective",
  OUTCOME: "Outcome",
};

const STATUS_BADGE_COLORS: Record<RcdoNodeStatus, { bg: string; fg: string }> =
  {
    DRAFT: { bg: "#fef3c7", fg: "#92400e" },
    ACTIVE: { bg: "#d1fae5", fg: "#065f46" },
    ARCHIVED: { bg: "#f3f4f6", fg: "#6b7280" },
  };

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Collect all node IDs in a tree. */
function getAllIds(nodes: RcdoTreeNode[]): Set<string> {
  const ids = new Set<string>();
  function collect(node: RcdoTreeNode) {
    ids.add(node.id);
    node.children.forEach(collect);
  }
  nodes.forEach(collect);
  return ids;
}

/**
 * Filter the tree by status and search query.
 * A parent is kept if any of its descendants passes the filter.
 */
function filterTree(
  nodes: RcdoTreeNode[],
  filter: StatusFilter,
  searchLower: string,
): RcdoTreeNode[] {
  function filterNode(node: RcdoTreeNode): RcdoTreeNode | null {
    const filteredChildren = node.children
      .map(filterNode)
      .filter((n): n is RcdoTreeNode => n !== null);

    const passesStatus =
      filter === "all" ||
      (filter === "active-only" && node.status !== "ARCHIVED") ||
      (filter === "archived-only" && node.status === "ARCHIVED");

    const passesSearch =
      !searchLower || node.title.toLowerCase().includes(searchLower);

    if ((passesStatus && passesSearch) || filteredChildren.length > 0) {
      return { ...node, children: filteredChildren };
    }
    return null;
  }
  return nodes.map(filterNode).filter((n): n is RcdoTreeNode => n !== null);
}

// ── StatusBadge ───────────────────────────────────────────────────────────────

function StatusBadge({ status }: { readonly status: RcdoNodeStatus }) {
  const label = status.charAt(0) + status.slice(1).toLowerCase();
  const { bg, fg } = STATUS_BADGE_COLORS[status];
  return (
    <span
      style={{
        display: "inline-block",
        padding: "1px 8px",
        borderRadius: "999px",
        fontSize: "0.7rem",
        fontWeight: 700,
        letterSpacing: "0.02em",
        background: bg,
        color: fg,
        flexShrink: 0,
      }}
      data-testid={`status-badge-${status.toLowerCase()}`}
    >
      {label}
    </span>
  );
}

// ── TreeNodeItem ──────────────────────────────────────────────────────────────

interface TreeNodeItemProps {
  readonly node: RcdoTreeNode;
  readonly depth: number;
  readonly selectedId: string | null;
  readonly expandedIds: Set<string>;
  readonly onSelect: (id: string) => void;
  readonly onToggleExpand: (id: string) => void;
}

function TreeNodeItem({
  node,
  depth,
  selectedId,
  expandedIds,
  onSelect,
  onToggleExpand,
}: TreeNodeItemProps) {
  const isExpanded = expandedIds.has(node.id);
  const isSelected = selectedId === node.id;
  const hasChildren = node.children.length > 0;

  return (
    <li
      role="treeitem"
      aria-expanded={hasChildren ? isExpanded : undefined}
      aria-selected={isSelected}
      style={{ listStyle: "none", margin: 0, padding: 0 }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "0.375rem",
          padding: "0.375rem 0.5rem",
          paddingLeft: `${depth * 1.25 + 0.5}rem`,
          cursor: "pointer",
          borderRadius: "var(--border-radius)",
          backgroundColor: isSelected
            ? "var(--color-primary)"
            : "transparent",
          color: isSelected ? "#fff" : "var(--color-text)",
          userSelect: "none",
        }}
        onClick={() => onSelect(node.id)}
        data-testid={`tree-node-${node.id}`}
      >
        {/* Expand/collapse toggle */}
        <button
          aria-label={
            hasChildren
              ? isExpanded
                ? `Collapse ${node.title}`
                : `Expand ${node.title}`
              : undefined
          }
          onClick={(e) => {
            e.stopPropagation();
            if (hasChildren) onToggleExpand(node.id);
          }}
          tabIndex={-1}
          style={{
            background: "none",
            border: "none",
            cursor: hasChildren ? "pointer" : "default",
            color: "inherit",
            padding: 0,
            width: "1rem",
            textAlign: "center",
            flexShrink: 0,
            opacity: hasChildren ? 1 : 0,
            fontSize: "0.75rem",
          }}
          aria-hidden={!hasChildren}
        >
          {isExpanded ? "▾" : "▸"}
        </button>

        {/* Node type icon */}
        <span
          aria-label={NODE_TYPE_LABELS[node.nodeType]}
          title={NODE_TYPE_LABELS[node.nodeType]}
          style={{ flexShrink: 0 }}
        >
          {NODE_TYPE_ICONS[node.nodeType]}
        </span>

        {/* Title */}
        <span
          style={{
            flex: 1,
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {node.title}
        </span>

        {/* Status badge */}
        <StatusBadge status={node.status} />
      </div>

      {/* Children */}
      {hasChildren && isExpanded && (
        <ul
          role="group"
          style={{ margin: 0, padding: 0, listStyle: "none" }}
        >
          {node.children.map((child) => (
            <TreeNodeItem
              key={child.id}
              node={child}
              depth={depth + 1}
              selectedId={selectedId}
              expandedIds={expandedIds}
              onSelect={onSelect}
              onToggleExpand={onToggleExpand}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

// ── RcdoTreeView ──────────────────────────────────────────────────────────────

export interface RcdoTreeViewProps {
  readonly nodes: RcdoTreeNode[];
  readonly selectedId: string | null;
  readonly onSelect: (id: string) => void;
  readonly statusFilter: StatusFilter;
  readonly searchQuery: string;
}

export function RcdoTreeView({
  nodes,
  selectedId,
  onSelect,
  statusFilter,
  searchQuery,
}: RcdoTreeViewProps) {
  // Start with top-level Rally Cry nodes expanded
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => {
    const initial = new Set<string>();
    nodes.forEach((n) => {
      if (n.nodeType === "RALLY_CRY") initial.add(n.id);
    });
    return initial;
  });

  const toggleExpand = useCallback((id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  const expandAll = useCallback(() => {
    setExpandedIds(getAllIds(nodes));
  }, [nodes]);

  const collapseAll = useCallback(() => {
    setExpandedIds(new Set());
  }, []);

  const searchLower = searchQuery.trim().toLowerCase();
  const filteredNodes = filterTree(nodes, statusFilter, searchLower);

  // When searching, auto-expand everything so results are visible
  const effectiveExpandedIds = searchLower
    ? getAllIds(nodes)
    : expandedIds;

  const btnStyle: React.CSSProperties = {
    background: "none",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    padding: "0.2rem 0.6rem",
    cursor: "pointer",
    fontSize: "0.75rem",
    color: "var(--color-text-muted)",
  };

  return (
    <div
      style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}
      data-testid="rcdo-tree-view"
    >
      {/* Expand / Collapse all controls */}
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <button onClick={expandAll} style={btnStyle} aria-label="Expand all nodes">
          Expand all
        </button>
        <button onClick={collapseAll} style={btnStyle} aria-label="Collapse all nodes">
          Collapse all
        </button>
      </div>

      {/* Tree */}
      <ul
        role="tree"
        aria-label="RCDO hierarchy"
        style={{ margin: 0, padding: 0, listStyle: "none" }}
      >
        {filteredNodes.length === 0 ? (
          <li
            style={{
              padding: "1rem",
              textAlign: "center",
              color: "var(--color-text-muted)",
              fontSize: "0.875rem",
            }}
          >
            No nodes match the current filter.
          </li>
        ) : (
          filteredNodes.map((node) => (
            <TreeNodeItem
              key={node.id}
              node={node}
              depth={0}
              selectedId={selectedId}
              expandedIds={effectiveExpandedIds}
              onSelect={onSelect}
              onToggleExpand={toggleExpand}
            />
          ))
        )}
      </ul>
    </div>
  );
}
