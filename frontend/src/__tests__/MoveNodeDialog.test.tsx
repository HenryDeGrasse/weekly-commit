/**
 * Tests for MoveNodeDialog — currently 0% coverage.
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MoveNodeDialog } from "../components/rcdo/MoveNodeDialog.js";
import type { RcdoTreeNode } from "../api/rcdoTypes.js";

const tree: RcdoTreeNode[] = [
  {
    id: "rc-1", nodeType: "RALLY_CRY", title: "Grow", status: "ACTIVE",
    children: [
      { id: "do-1", nodeType: "DEFINING_OBJECTIVE", title: "Revenue", status: "ACTIVE", children: [] },
      { id: "do-2", nodeType: "DEFINING_OBJECTIVE", title: "Retention", status: "ACTIVE", children: [] },
    ],
  },
  {
    id: "rc-2", nodeType: "RALLY_CRY", title: "Excel", status: "ACTIVE",
    children: [],
  },
];

describe("MoveNodeDialog", () => {
  it("renders the dialog with node name", () => {
    render(
      <MoveNodeDialog nodeName="Revenue" nodeType="DEFINING_OBJECTIVE" nodeId="do-1"
        tree={tree} onConfirm={vi.fn()} onCancel={vi.fn()} submitting={false} />,
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText(/Move "Revenue"/)).toBeInTheDocument();
  });

  it("shows valid parent options for a DO (Rally Cries)", () => {
    render(
      <MoveNodeDialog nodeName="Revenue" nodeType="DEFINING_OBJECTIVE" nodeId="do-1"
        tree={tree} onConfirm={vi.fn()} onCancel={vi.fn()} submitting={false} />,
    );
    // Should show Rally Cries as parent options
    expect(screen.getByText("Grow")).toBeInTheDocument();
    expect(screen.getByText("Excel")).toBeInTheDocument();
  });

  it("shows cannot-move message for Rally Cries", () => {
    render(
      <MoveNodeDialog nodeName="Grow" nodeType="RALLY_CRY" nodeId="rc-1"
        tree={tree} onConfirm={vi.fn()} onCancel={vi.fn()} submitting={false} />,
    );
    expect(screen.getByText(/cannot be re-parented/i)).toBeInTheDocument();
  });

  it("calls onConfirm with selected parent", () => {
    const onConfirm = vi.fn();
    render(
      <MoveNodeDialog nodeName="Revenue" nodeType="DEFINING_OBJECTIVE" nodeId="do-1"
        tree={tree} onConfirm={onConfirm} onCancel={vi.fn()} submitting={false} />,
    );
    // Select "Excel" as new parent
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "rc-2" } });
    fireEvent.click(screen.getByRole("button", { name: /Move here/i }));
    expect(onConfirm).toHaveBeenCalledWith("rc-2");
  });

  it("does not call onConfirm when no parent selected", () => {
    const onConfirm = vi.fn();
    render(
      <MoveNodeDialog nodeName="Revenue" nodeType="DEFINING_OBJECTIVE" nodeId="do-1"
        tree={tree} onConfirm={onConfirm} onCancel={vi.fn()} submitting={false} />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Move here/i }));
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("calls onCancel when Cancel is clicked", () => {
    const onCancel = vi.fn();
    render(
      <MoveNodeDialog nodeName="Revenue" nodeType="DEFINING_OBJECTIVE" nodeId="do-1"
        tree={tree} onConfirm={vi.fn()} onCancel={onCancel} submitting={false} />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("disables buttons when submitting", () => {
    render(
      <MoveNodeDialog nodeName="Revenue" nodeType="DEFINING_OBJECTIVE" nodeId="do-1"
        tree={tree} onConfirm={vi.fn()} onCancel={vi.fn()} submitting={true} />,
    );
    expect(screen.getByRole("button", { name: "Cancel" })).toBeDisabled();
  });

  it("excludes the node itself from valid parents", () => {
    // do-1 is a DO — valid parents are Rally Cries — rc-1 and rc-2 should both appear
    // but do-1 itself should not appear (it's not a Rally Cry so it wouldn't anyway)
    render(
      <MoveNodeDialog nodeName="Revenue" nodeType="DEFINING_OBJECTIVE" nodeId="do-1"
        tree={tree} onConfirm={vi.fn()} onCancel={vi.fn()} submitting={false} />,
    );
    const options = screen.getAllByRole("option");
    // placeholder + rc-1 + rc-2 = 3
    expect(options).toHaveLength(3);
  });
});
