/**
 * Expandable tree view of the full RCDO hierarchy.
 */
import { useState, useCallback } from "react";
import { ChevronRight, ChevronDown } from "lucide-react";
import { Badge } from "../ui/Badge.js";
import { Button } from "../ui/Button.js";
import { cn } from "../../lib/utils.js";
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

const STATUS_VARIANT: Record<RcdoNodeStatus, "draft" | "success" | "default"> = {
  DRAFT: "draft",
  ACTIVE: "success",
  ARCHIVED: "default",
};

function getAllIds(nodes: RcdoTreeNode[]): Set<string> {
  const ids = new Set<string>();
  function collect(node: RcdoTreeNode) { ids.add(node.id); node.children.forEach(collect); }
  nodes.forEach(collect);
  return ids;
}

function filterTree(nodes: RcdoTreeNode[], filter: StatusFilter, searchLower: string): RcdoTreeNode[] {
  function filterNode(node: RcdoTreeNode): RcdoTreeNode | null {
    const filteredChildren = node.children.map(filterNode).filter((n): n is RcdoTreeNode => n !== null);
    const passesStatus = filter === "all" || (filter === "active-only" && node.status !== "ARCHIVED") || (filter === "archived-only" && node.status === "ARCHIVED");
    const passesSearch = !searchLower || node.title.toLowerCase().includes(searchLower);
    if ((passesStatus && passesSearch) || filteredChildren.length > 0) return { ...node, children: filteredChildren };
    return null;
  }
  return nodes.map(filterNode).filter((n): n is RcdoTreeNode => n !== null);
}

interface TreeNodeItemProps {
  readonly node: RcdoTreeNode;
  readonly depth: number;
  readonly selectedId: string | null;
  readonly expandedIds: Set<string>;
  readonly onSelect: (id: string) => void;
  readonly onToggleExpand: (id: string) => void;
}

function TreeNodeItem({ node, depth, selectedId, expandedIds, onSelect, onToggleExpand }: TreeNodeItemProps) {
  const isExpanded = expandedIds.has(node.id);
  const isSelected = selectedId === node.id;
  const hasChildren = node.children.length > 0;

  return (
    <li role="treeitem" aria-expanded={hasChildren ? isExpanded : undefined} aria-selected={isSelected} className="list-none m-0 p-0">
      <div
        className={cn(
          "flex items-center gap-1.5 px-2 py-1.5 rounded-default cursor-pointer select-none transition-colors",
          isSelected ? "bg-primary text-white" : "hover:bg-muted/10 text-foreground",
        )}
        style={{ paddingLeft: `${depth * 1.25 + 0.5}rem` }}
        onClick={() => onSelect(node.id)}
        data-testid={`tree-node-${node.id}`}
      >
        <button
          aria-label={hasChildren ? (isExpanded ? `Collapse ${node.title}` : `Expand ${node.title}`) : undefined}
          onClick={(e) => { e.stopPropagation(); if (hasChildren) onToggleExpand(node.id); }}
          tabIndex={-1}
          className={cn("bg-transparent border-none cursor-pointer p-0 text-inherit flex items-center shrink-0", !hasChildren && "invisible")}
          aria-hidden={!hasChildren}
        >
          {isExpanded ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
        </button>
        <span aria-label={NODE_TYPE_LABELS[node.nodeType]} title={NODE_TYPE_LABELS[node.nodeType]} className="shrink-0">
          {NODE_TYPE_ICONS[node.nodeType]}
        </span>
        <span className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap text-sm">{node.title}</span>
        <Badge
          variant={STATUS_VARIANT[node.status]}
          data-testid={`status-badge-${node.status.toLowerCase()}`}
          className={cn(isSelected && "bg-white/20 text-white")}
        >
          {node.status.charAt(0) + node.status.slice(1).toLowerCase()}
        </Badge>
      </div>
      {hasChildren && isExpanded && (
        <ul role="group" className="m-0 p-0 list-none">
          {node.children.map((child) => (
            <TreeNodeItem key={child.id} node={child} depth={depth + 1} selectedId={selectedId} expandedIds={expandedIds} onSelect={onSelect} onToggleExpand={onToggleExpand} />
          ))}
        </ul>
      )}
    </li>
  );
}

export interface RcdoTreeViewProps {
  readonly nodes: RcdoTreeNode[];
  readonly selectedId: string | null;
  readonly onSelect: (id: string) => void;
  readonly statusFilter: StatusFilter;
  readonly searchQuery: string;
}

export function RcdoTreeView({ nodes, selectedId, onSelect, statusFilter, searchQuery }: RcdoTreeViewProps) {
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => {
    const initial = new Set<string>();
    nodes.forEach((n) => { if (n.nodeType === "RALLY_CRY") initial.add(n.id); });
    return initial;
  });

  const toggleExpand = useCallback((id: string) => {
    setExpandedIds((prev) => { const next = new Set(prev); if (next.has(id)) { next.delete(id); } else { next.add(id); } return next; });
  }, []);

  const expandAll = useCallback(() => setExpandedIds(getAllIds(nodes)), [nodes]);
  const collapseAll = useCallback(() => setExpandedIds(new Set()), []);

  const searchLower = searchQuery.trim().toLowerCase();
  const filteredNodes = filterTree(nodes, statusFilter, searchLower);
  const effectiveExpandedIds = searchLower ? getAllIds(nodes) : expandedIds;

  return (
    <div className="flex flex-col gap-2" data-testid="rcdo-tree-view">
      <div className="flex gap-2">
        <Button variant="ghost" size="sm" onClick={expandAll} aria-label="Expand all nodes" className="h-7 px-2 text-xs border border-border">Expand all</Button>
        <Button variant="ghost" size="sm" onClick={collapseAll} aria-label="Collapse all nodes" className="h-7 px-2 text-xs border border-border">Collapse all</Button>
      </div>
      <ul role="tree" aria-label="RCDO hierarchy" className="m-0 p-0 list-none">
        {filteredNodes.length === 0 ? (
          <li className="py-4 text-center text-sm text-muted">No nodes match the current filter.</li>
        ) : (
          filteredNodes.map((node) => (
            <TreeNodeItem key={node.id} node={node} depth={0} selectedId={selectedId} expandedIds={effectiveExpandedIds} onSelect={onSelect} onToggleExpand={toggleExpand} />
          ))
        )}
      </ul>
    </div>
  );
}
