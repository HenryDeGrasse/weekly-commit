import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RcdoTreeView } from "../components/rcdo/RcdoTreeView.js";
import type { RcdoTreeNode } from "../api/rcdoTypes.js";

// ── Test fixture ──────────────────────────────────────────────────────────────

const mockTree: RcdoTreeNode[] = [
  {
    id: "rc-1",
    nodeType: "RALLY_CRY",
    status: "ACTIVE",
    title: "Grow Revenue",
    children: [
      {
        id: "do-1",
        nodeType: "DEFINING_OBJECTIVE",
        status: "ACTIVE",
        title: "Enterprise Sales",
        children: [
          {
            id: "out-1",
            nodeType: "OUTCOME",
            status: "ACTIVE",
            title: "Close 10 Enterprise Deals",
            children: [],
          },
          {
            id: "out-2",
            nodeType: "OUTCOME",
            status: "DRAFT",
            title: "Expand to APAC Market",
            children: [],
          },
        ],
      },
      {
        id: "do-2",
        nodeType: "DEFINING_OBJECTIVE",
        status: "ARCHIVED",
        title: "SMB Segment",
        children: [],
      },
    ],
  },
  {
    id: "rc-2",
    nodeType: "RALLY_CRY",
    status: "DRAFT",
    title: "Improve Product Quality",
    children: [],
  },
];

function renderTree(
  overrides: Partial<Parameters<typeof RcdoTreeView>[0]> = {},
) {
  const onSelect = vi.fn();
  render(
    <RcdoTreeView
      nodes={mockTree}
      selectedId={null}
      onSelect={onSelect}
      statusFilter="all"
      searchQuery=""
      {...overrides}
    />,
  );
  return { onSelect };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("RcdoTreeView", () => {
  it("renders the tree container with aria role=tree", () => {
    renderTree();
    expect(screen.getByRole("tree", { name: "RCDO hierarchy" })).toBeInTheDocument();
  });

  it("renders top-level Rally Cry nodes", () => {
    renderTree();
    expect(screen.getByText("Grow Revenue")).toBeInTheDocument();
    expect(screen.getByText("Improve Product Quality")).toBeInTheDocument();
  });

  it("renders the full hierarchy when rally cry is expanded by default", () => {
    renderTree();
    // Rally cries start expanded
    expect(screen.getByText("Enterprise Sales")).toBeInTheDocument();
  });

  it("does not show nested outcomes until DO is expanded", () => {
    renderTree();
    // "Enterprise Sales" is visible (RC expanded), but outcomes are under DO
    // DO is not expanded by default
    expect(screen.queryByText("Close 10 Enterprise Deals")).not.toBeInTheDocument();
  });

  it("shows children after clicking expand on a DO", () => {
    renderTree();
    // Click expand on "Enterprise Sales"
    const expandBtn = screen.getByRole("button", { name: "Expand Enterprise Sales" });
    fireEvent.click(expandBtn);
    expect(screen.getByText("Close 10 Enterprise Deals")).toBeInTheDocument();
    expect(screen.getByText("Expand to APAC Market")).toBeInTheDocument();
  });

  it("collapses children after toggling the expand button twice", () => {
    renderTree();
    const expandBtn = screen.getByRole("button", { name: "Expand Enterprise Sales" });
    fireEvent.click(expandBtn);
    expect(screen.getByText("Close 10 Enterprise Deals")).toBeInTheDocument();

    const collapseBtn = screen.getByRole("button", { name: "Collapse Enterprise Sales" });
    fireEvent.click(collapseBtn);
    expect(screen.queryByText("Close 10 Enterprise Deals")).not.toBeInTheDocument();
  });

  it("expands all nodes when Expand all is clicked", () => {
    renderTree();
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    expect(screen.getByText("Close 10 Enterprise Deals")).toBeInTheDocument();
    expect(screen.getByText("Expand to APAC Market")).toBeInTheDocument();
    expect(screen.getByText("SMB Segment")).toBeInTheDocument();
  });

  it("collapses all nodes when Collapse all is clicked", () => {
    renderTree();
    // First expand all
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    expect(screen.getByText("Enterprise Sales")).toBeInTheDocument();

    // Then collapse all
    fireEvent.click(screen.getByRole("button", { name: "Collapse all nodes" }));
    // Top-level nodes still visible
    expect(screen.getByText("Grow Revenue")).toBeInTheDocument();
    // Children should be hidden
    expect(screen.queryByText("Enterprise Sales")).not.toBeInTheDocument();
  });

  it("calls onSelect when a node is clicked", () => {
    const { onSelect } = renderTree();
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));
    expect(onSelect).toHaveBeenCalledWith("rc-1");
  });

  it("marks the selected node with aria-selected", () => {
    renderTree({ selectedId: "rc-1" });
    const item = screen.getByTestId("tree-node-rc-1").closest('[role="treeitem"]');
    expect(item).toHaveAttribute("aria-selected", "true");
  });

  it("renders status badges for visible nodes", () => {
    renderTree();
    const activeBadges = screen.getAllByTestId("status-badge-active");
    expect(activeBadges.length).toBeGreaterThan(0);
  });

  // ── Status filter ─────────────────────────────────────────────────────────

  it("hides ARCHIVED nodes when statusFilter=active-only", () => {
    renderTree({ statusFilter: "active-only" });
    // Expand to see children
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    expect(screen.queryByText("SMB Segment")).not.toBeInTheDocument();
  });

  it("shows only ARCHIVED nodes when statusFilter=archived-only", () => {
    renderTree({ statusFilter: "archived-only" });
    // Expand to see children
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    expect(screen.getByText("SMB Segment")).toBeInTheDocument();
    // Active OUTCOME nodes should be filtered out
    expect(screen.queryByText("Close 10 Enterprise Deals")).not.toBeInTheDocument();
  });

  it("keeps parent visible when archived-only filter and children match but parent does not", () => {
    // The parent RC-1 is ACTIVE, but DO-2 is ARCHIVED
    // RC-1 should still appear as a container
    renderTree({ statusFilter: "archived-only" });
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    expect(screen.getByText("Grow Revenue")).toBeInTheDocument();
    expect(screen.getByText("SMB Segment")).toBeInTheDocument();
  });

  // ── Search ────────────────────────────────────────────────────────────────

  it("filters tree nodes by search query", () => {
    renderTree({ searchQuery: "Enterprise" });
    // "Enterprise Sales" matches
    expect(screen.getByText("Enterprise Sales")).toBeInTheDocument();
    // "Improve Product Quality" does not match and has no matching children
    expect(screen.queryByText("Improve Product Quality")).not.toBeInTheDocument();
  });

  it("auto-expands tree when searching so results are visible", () => {
    renderTree({ searchQuery: "APAC" });
    // "Expand to APAC Market" is an Outcome deep in the tree
    // It should be visible without manually expanding
    expect(screen.getByText("Expand to APAC Market")).toBeInTheDocument();
  });

  it("shows 'No nodes match' message when search yields no results", () => {
    renderTree({ searchQuery: "xyzzy-no-match" });
    expect(screen.getByText("No nodes match the current filter.")).toBeInTheDocument();
  });

  it("search is case-insensitive", () => {
    renderTree({ searchQuery: "enterprise" });
    expect(screen.getByText("Enterprise Sales")).toBeInTheDocument();
  });

  it("keeps ancestor nodes visible for matching descendants", () => {
    renderTree({ searchQuery: "Close 10" });
    // "Grow Revenue" (RC) and "Enterprise Sales" (DO) must stay visible
    expect(screen.getByText("Grow Revenue")).toBeInTheDocument();
    expect(screen.getByText("Enterprise Sales")).toBeInTheDocument();
    expect(screen.getByText("Close 10 Enterprise Deals")).toBeInTheDocument();
  });

  it("renders empty state when nodes array is empty", () => {
    render(
      <RcdoTreeView
        nodes={[]}
        selectedId={null}
        onSelect={vi.fn()}
        statusFilter="all"
        searchQuery=""
      />,
    );
    expect(screen.getByText("No nodes match the current filter.")).toBeInTheDocument();
  });
});
