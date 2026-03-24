/**
 * Re-parent dialog for moving an RCDO node to a different valid parent.
 * Shows all non-archived parents of the required type.
 */
import { useState } from "react";
import type { RcdoTreeNode, RcdoNodeType } from "../../api/rcdoTypes.js";

// ── Helpers ──────────────────────────────────────────────────────────────────

function flattenByType(
  nodes: RcdoTreeNode[],
  targetType: RcdoNodeType,
  excludeId?: string,
): RcdoTreeNode[] {
  const result: RcdoTreeNode[] = [];
  function collect(node: RcdoTreeNode) {
    if (
      node.nodeType === targetType &&
      node.status !== "ARCHIVED" &&
      node.id !== excludeId
    ) {
      result.push(node);
    }
    node.children.forEach(collect);
  }
  nodes.forEach(collect);
  return result;
}

function requiredParentType(nodeType: RcdoNodeType): RcdoNodeType | null {
  switch (nodeType) {
    case "RALLY_CRY":
      return null;
    case "DEFINING_OBJECTIVE":
      return "RALLY_CRY";
    case "OUTCOME":
      return "DEFINING_OBJECTIVE";
  }
}

const NODE_TYPE_LABELS: Record<RcdoNodeType, string> = {
  RALLY_CRY: "Rally Cry",
  DEFINING_OBJECTIVE: "Defining Objective",
  OUTCOME: "Outcome",
};

// ── MoveNodeDialog ────────────────────────────────────────────────────────────

interface MoveNodeDialogProps {
  readonly nodeName: string;
  readonly nodeType: RcdoNodeType;
  readonly nodeId: string;
  readonly currentParentId?: string;
  readonly tree: RcdoTreeNode[];
  readonly onConfirm: (newParentId: string) => void;
  readonly onCancel: () => void;
  readonly submitting: boolean;
}

const overlayStyle: React.CSSProperties = {
  position: "fixed",
  inset: 0,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  backgroundColor: "rgba(0, 0, 0, 0.5)",
  zIndex: 1000,
};

const panelStyle: React.CSSProperties = {
  background: "var(--color-surface)",
  borderRadius: "var(--border-radius)",
  padding: "1.5rem",
  maxWidth: "480px",
  width: "90vw",
  boxShadow: "0 20px 60px rgba(0,0,0,0.3)",
};

export function MoveNodeDialog({
  nodeName,
  nodeType,
  nodeId,
  currentParentId,
  tree,
  onConfirm,
  onCancel,
  submitting,
}: MoveNodeDialogProps) {
  const [selectedParentId, setSelectedParentId] = useState(
    currentParentId ?? "",
  );

  const parentType = requiredParentType(nodeType);

  // Rally Cries cannot be moved (they are top-level)
  if (!parentType) {
    return (
      <div role="dialog" aria-modal="true" aria-labelledby="move-dialog-title" style={overlayStyle}>
        <div style={panelStyle}>
          <h3 id="move-dialog-title" style={{ margin: "0 0 1rem" }}>
            Cannot Move Rally Cry
          </h3>
          <p style={{ color: "var(--color-text-muted)", marginBottom: "1rem" }}>
            Rally Cry nodes are at the top of the hierarchy and cannot be
            re-parented.
          </p>
          <div style={{ display: "flex", justifyContent: "flex-end" }}>
            <button
              onClick={onCancel}
              style={{
                padding: "0.5rem 1rem",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                background: "var(--color-surface)",
                cursor: "pointer",
                fontSize: "inherit",
                fontFamily: "inherit",
              }}
            >
              Close
            </button>
          </div>
        </div>
      </div>
    );
  }

  const validParents = flattenByType(tree, parentType, nodeId);

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="move-dialog-title" style={overlayStyle}>
      <div style={panelStyle}>
        <h3 id="move-dialog-title" style={{ margin: "0 0 1rem", fontSize: "1.125rem" }}>
          Move "{nodeName}"
        </h3>
        <p
          style={{
            margin: "0 0 1rem",
            color: "var(--color-text-muted)",
            fontSize: "0.875rem",
          }}
        >
          Select a new {NODE_TYPE_LABELS[parentType]} parent:
        </p>

        <select
          value={selectedParentId}
          onChange={(e) => setSelectedParentId(e.target.value)}
          aria-label={`Select new ${NODE_TYPE_LABELS[parentType]} parent`}
          style={{
            width: "100%",
            padding: "0.5rem",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            fontSize: "inherit",
            fontFamily: "inherit",
            marginBottom: "1rem",
          }}
        >
          <option value="">Choose a {NODE_TYPE_LABELS[parentType]}…</option>
          {validParents.map((parent) => (
            <option key={parent.id} value={parent.id}>
              {parent.title}
            </option>
          ))}
        </select>

        {validParents.length === 0 && (
          <p
            style={{
              fontSize: "0.875rem",
              color: "var(--color-warning)",
              marginBottom: "0.5rem",
            }}
          >
            No valid {NODE_TYPE_LABELS[parentType]} nodes available.
          </p>
        )}

        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}>
          <button
            onClick={onCancel}
            disabled={submitting}
            style={{
              padding: "0.5rem 1rem",
              border: "1px solid var(--color-border)",
              borderRadius: "var(--border-radius)",
              background: "var(--color-surface)",
              cursor: "pointer",
              fontSize: "inherit",
              fontFamily: "inherit",
            }}
          >
            Cancel
          </button>
          <button
            onClick={() => {
              if (selectedParentId) onConfirm(selectedParentId);
            }}
            disabled={!selectedParentId || submitting}
            aria-disabled={!selectedParentId || submitting}
            style={{
              padding: "0.5rem 1rem",
              border: "none",
              borderRadius: "var(--border-radius)",
              background:
                selectedParentId ? "var(--color-primary)" : "var(--color-border)",
              color: selectedParentId ? "#fff" : "var(--color-text-muted)",
              cursor:
                !selectedParentId || submitting ? "not-allowed" : "pointer",
              fontSize: "inherit",
              fontFamily: "inherit",
            }}
          >
            {submitting ? "Moving…" : "Move here"}
          </button>
        </div>
      </div>
    </div>
  );
}
