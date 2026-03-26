/**
 * Re-parent dialog for moving an RCDO node to a different valid parent.
 */
import { useState } from "react";
import { Button } from "../ui/Button.js";
import { Select } from "../ui/Select.js";
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "../ui/Dialog.js";
import type { RcdoTreeNode, RcdoNodeType } from "../../api/rcdoTypes.js";

function flattenByType(nodes: RcdoTreeNode[], targetType: RcdoNodeType, excludeId?: string): RcdoTreeNode[] {
  const result: RcdoTreeNode[] = [];
  function collect(node: RcdoTreeNode) {
    if (node.nodeType === targetType && node.status !== "ARCHIVED" && node.id !== excludeId) result.push(node);
    node.children.forEach(collect);
  }
  nodes.forEach(collect);
  return result;
}

function requiredParentType(nodeType: RcdoNodeType): RcdoNodeType | null {
  switch (nodeType) {
    case "RALLY_CRY": return null;
    case "DEFINING_OBJECTIVE": return "RALLY_CRY";
    case "OUTCOME": return "DEFINING_OBJECTIVE";
  }
}

const NODE_TYPE_LABELS: Record<RcdoNodeType, string> = {
  RALLY_CRY: "Rally Cry",
  DEFINING_OBJECTIVE: "Defining Objective",
  OUTCOME: "Outcome",
};

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

export function MoveNodeDialog({ nodeName, nodeType, nodeId, currentParentId, tree, onConfirm, onCancel, submitting }: MoveNodeDialogProps) {
  const [selectedParentId, setSelectedParentId] = useState(currentParentId ?? "");
  const parentType = requiredParentType(nodeType);

  if (!parentType) {
    return (
      <Dialog open onClose={onCancel} aria-label="Cannot Move Rally Cry">
        <DialogHeader>
          <DialogTitle>Cannot Move Rally Cry</DialogTitle>
        </DialogHeader>
        <p className="mb-4 text-sm text-muted">
          Rally Cry nodes are at the top of the hierarchy and cannot be re-parented.
        </p>
        <DialogFooter>
          <Button variant="secondary" onClick={onCancel}>Close</Button>
        </DialogFooter>
      </Dialog>
    );
  }

  const validParents = flattenByType(tree, parentType, nodeId);

  return (
    <Dialog open onClose={onCancel} aria-label={`Move ${nodeName}`}>
      <DialogHeader>
        <DialogTitle>Move "{nodeName}"</DialogTitle>
        <DialogDescription>Select a new {NODE_TYPE_LABELS[parentType]} parent:</DialogDescription>
      </DialogHeader>

      <div className="mb-4">
        <Select
          value={selectedParentId}
          onChange={(e) => setSelectedParentId(e.target.value)}
          aria-label={`Select new ${NODE_TYPE_LABELS[parentType]} parent`}
        >
          <option value="">Choose a {NODE_TYPE_LABELS[parentType]}…</option>
          {validParents.map((parent) => <option key={parent.id} value={parent.id}>{parent.title}</option>)}
        </Select>

        {validParents.length === 0 && (
          <p className="mt-2 text-sm text-warning">
            No valid {NODE_TYPE_LABELS[parentType]} nodes available.
          </p>
        )}
      </div>

      <DialogFooter>
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>Cancel</Button>
        <Button
          variant="primary"
          onClick={() => { if (selectedParentId) onConfirm(selectedParentId); }}
          disabled={!selectedParentId || submitting}
          aria-disabled={!selectedParentId || submitting}
        >
          {submitting ? "Moving…" : "Move here"}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
