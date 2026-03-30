/**
 * Integration tests for the Rcdos page.
 * Tests: archive blocked with active children, permission-based action hiding,
 * and breadcrumb updates on selection.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { MockHostProvider } from "../host/MockHostProvider.js";
import type { HostBridge } from "../host/HostProvider.js";
import {
  mockHostBridge,
  mockHostContext,
} from "../host/MockHostProvider.js";
import type { RcdoTreeNode } from "../api/rcdoTypes.js";

// ── Mock rcdoHooks ────────────────────────────────────────────────────────────

const mockRefetch = vi.fn();
const mockCreateNode = vi.fn();
const mockUpdateNode = vi.fn();
const mockArchiveNode = vi.fn();
const mockMoveNode = vi.fn();
const mockActivateNode = vi.fn();

vi.mock("../api/rcdoHooks.js", () => ({
  useRcdoTree: vi.fn(),
  useRcdoNode: vi.fn(),
  useRcdoApi: vi.fn(),
}));

// Import after mock so we get the vi.fn() versions
import { useRcdoTree, useRcdoNode, useRcdoApi } from "../api/rcdoHooks.js";
import Rcdos from "../routes/Rcdos.js";

// ── Test fixture ──────────────────────────────────────────────────────────────

/** RC with active children → archive should be blocked */
const rcWithActiveChildren: RcdoTreeNode = {
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
      ],
    },
  ],
};

/** A leaf node with no children — archive should be allowed */
const leafOutcome: RcdoTreeNode = {
  id: "out-1",
  nodeType: "OUTCOME",
  status: "ACTIVE",
  title: "Close 10 Enterprise Deals",
  children: [],
};

const mockTree: RcdoTreeNode[] = [
  {
    ...rcWithActiveChildren,
    children: [
      {
        id: "do-1",
        nodeType: "DEFINING_OBJECTIVE",
        status: "ACTIVE",
        title: "Enterprise Sales",
        children: [leafOutcome],
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

const mockApi = {
  getTree: vi.fn(),
  getNode: vi.fn(),
  listNodes: vi.fn(),
  createNode: mockCreateNode,
  updateNode: mockUpdateNode,
  archiveNode: mockArchiveNode,
  moveNode: mockMoveNode,
  activateNode: mockActivateNode,
};

// ── Helpers ───────────────────────────────────────────────────────────────────

function renderPage(bridge: HostBridge = mockHostBridge) {
  return render(
    <MemoryRouter initialEntries={["/rcdos"]}>
      <MockHostProvider bridge={bridge}>
        <Rcdos />
      </MockHostProvider>
    </MemoryRouter>,
  );
}

/** Create a bridge with specific feature flags. */
function bridgeWithFlags(flags: Record<string, boolean>): HostBridge {
  return {
    ...mockHostBridge,
    context: {
      ...mockHostContext,
      featureFlags: {
        ...mockHostContext.featureFlags,
        ...flags,
      },
    },
  };
}

/** IC bridge — read-only (no admin, no manager) */
const icBridge = bridgeWithFlags({
  managerReviewEnabled: false,
  rcdoAdminEnabled: false,
});

/** Manager bridge — can manage DOs/Outcomes but not Rally Cries */
const managerBridge = bridgeWithFlags({
  managerReviewEnabled: true,
  rcdoAdminEnabled: false,
});

/** Admin bridge */
const adminBridge = bridgeWithFlags({
  rcdoAdminEnabled: true,
  managerReviewEnabled: true,
});

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.mocked(useRcdoTree).mockReturnValue({
    data: mockTree,
    loading: false,
    error: null,
    refetch: mockRefetch,
  });
  vi.mocked(useRcdoNode).mockReturnValue({
    data: null,
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  vi.mocked(useRcdoApi).mockReturnValue(mockApi as ReturnType<typeof useRcdoApi>);
  mockRefetch.mockReset();
  mockArchiveNode.mockReset();
  mockMoveNode.mockReset();
  mockCreateNode.mockReset();
  mockUpdateNode.mockReset();
});

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("Rcdos page — rendering", () => {
  it("renders the page with data-testid=page-rcdos", () => {
    renderPage();
    expect(screen.getByTestId("page-rcdos")).toBeInTheDocument();
  });

  it("renders the RCDO tree view", () => {
    renderPage();
    expect(screen.getByTestId("rcdo-tree-view")).toBeInTheDocument();
  });

  it("defaults to showing only active nodes in the tree", () => {
    renderPage();
    expect(screen.getByText("Grow Revenue")).toBeInTheDocument();
    expect(screen.queryByText("Improve Product Quality")).not.toBeInTheDocument();
  });

  it("shows loading state while tree data is loading", () => {
    vi.mocked(useRcdoTree).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByRole("status", { name: "Loading RCDO hierarchy" })).toBeInTheDocument();
  });

  it("shows error message when tree loading fails", () => {
    vi.mocked(useRcdoTree).mockReturnValue({
      data: undefined,
      loading: false,
      error: { name: "ApiRequestError", message: "Network error", status: 0 } as never,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByRole("alert")).toHaveTextContent("Network error");
  });
});

describe("Rcdos page — selection and breadcrumb", () => {
  it("shows empty state when no node is selected", () => {
    renderPage();
    // The empty-state guidance text should be visible
    expect(
      screen.getByText(/Select a node from the tree/),
    ).toBeInTheDocument();
  });

  it("shows node detail panel when a node is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));
    // The right panel should show action buttons — confirms detail panel is open
    expect(screen.getByTestId("node-action-buttons")).toBeInTheDocument();
    // Breadcrumb appears in right panel
    expect(screen.getByRole("navigation", { name: "RCDO path" })).toBeInTheDocument();
  });

  it("breadcrumb shows single node title for top-level selection", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));
    // Breadcrumb nav should be visible
    const breadcrumb = screen.getByRole("navigation", { name: "RCDO path" });
    expect(breadcrumb).toBeInTheDocument();
    expect(breadcrumb).toHaveTextContent("Grow Revenue");
  });

  it("breadcrumb shows full path when a nested node is selected", () => {
    renderPage();
    // Expand the RC first
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    // Click the DO
    fireEvent.click(screen.getByTestId("tree-node-do-1"));

    const breadcrumb = screen.getByRole("navigation", { name: "RCDO path" });
    // Should show RC > DO
    expect(breadcrumb).toHaveTextContent("Grow Revenue");
    expect(breadcrumb).toHaveTextContent("Enterprise Sales");
  });

  it("breadcrumb updates when a different node is selected", () => {
    renderPage();
    // Select first node
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));
    expect(
      screen.getByRole("navigation", { name: "RCDO path" }),
    ).toHaveTextContent("Grow Revenue");

    // Switch to all nodes so DRAFT nodes become visible, then select second node
    fireEvent.click(screen.getByDisplayValue("all"));
    fireEvent.click(screen.getByTestId("tree-node-rc-2"));
    expect(
      screen.getByRole("navigation", { name: "RCDO path" }),
    ).toHaveTextContent("Improve Product Quality");
  });
});

describe("Rcdos page — archive dialog", () => {
  it("opens archive dialog when Archive button is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));
    const archiveBtn = screen.getByTestId("archive-node-btn");
    fireEvent.click(archiveBtn);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("archive dialog shows blocked message when node has active children", () => {
    renderPage();
    // Select RC-1 which has an active DO child
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));
    fireEvent.click(screen.getByTestId("archive-node-btn"));

    expect(screen.getByTestId("archive-blocked-message")).toBeInTheDocument();
    expect(screen.getByTestId("archive-blocked-message")).toHaveTextContent(
      "Cannot archive",
    );
  });

  it("archive confirm button is disabled when node has active children", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));
    fireEvent.click(screen.getByTestId("archive-node-btn"));

    const confirmBtn = screen.getByTestId("archive-confirm-button");
    expect(confirmBtn).toBeDisabled();
  });

  it("archive confirm button is enabled when node has no active children", () => {
    renderPage();
    // Expand tree and select the leaf outcome
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    fireEvent.click(screen.getByTestId("tree-node-out-1"));
    fireEvent.click(screen.getByTestId("archive-node-btn"));

    const confirmBtn = screen.getByTestId("archive-confirm-button");
    expect(confirmBtn).not.toBeDisabled();
  });

  it("calls archiveNode and refetches when confirmed on leaf node", async () => {
    mockArchiveNode.mockResolvedValue({ id: "out-1", status: "ARCHIVED" });
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    fireEvent.click(screen.getByTestId("tree-node-out-1"));
    fireEvent.click(screen.getByTestId("archive-node-btn"));
    fireEvent.click(screen.getByTestId("archive-confirm-button"));

    await waitFor(() => {
      expect(mockArchiveNode).toHaveBeenCalledWith("out-1");
      expect(mockRefetch).toHaveBeenCalled();
    });
  });

  it("closes archive dialog when Cancel is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));
    fireEvent.click(screen.getByTestId("archive-node-btn"));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("Rcdos page — permission-based visibility", () => {
  it("shows create buttons for Manager (managerReviewEnabled=true)", () => {
    renderPage(managerBridge);
    expect(screen.getByTestId("create-do-btn")).toBeInTheDocument();
    expect(screen.getByTestId("create-outcome-btn")).toBeInTheDocument();
  });

  it("hides DO/Outcome create buttons for IC (managerReviewEnabled=false)", () => {
    renderPage(icBridge);
    expect(screen.queryByTestId("create-do-btn")).not.toBeInTheDocument();
    expect(screen.queryByTestId("create-outcome-btn")).not.toBeInTheDocument();
  });

  it("hides Rally Cry create button for Manager (rcdoAdminEnabled=false)", () => {
    renderPage(managerBridge);
    expect(screen.queryByTestId("create-rally-cry-btn")).not.toBeInTheDocument();
  });

  it("shows Rally Cry create button for Admin (rcdoAdminEnabled=true)", () => {
    renderPage(adminBridge);
    expect(screen.getByTestId("create-rally-cry-btn")).toBeInTheDocument();
  });

  it("hides action buttons for IC and shows read-only label", () => {
    renderPage(icBridge);
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));

    expect(screen.queryByTestId("node-action-buttons")).not.toBeInTheDocument();
    expect(screen.queryByTestId("edit-node-btn")).not.toBeInTheDocument();
    expect(screen.queryByTestId("archive-node-btn")).not.toBeInTheDocument();
    expect(screen.getByTestId("readonly-label")).toBeInTheDocument();
  });

  it("shows action buttons for Manager when node is selected", () => {
    renderPage(managerBridge);
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));

    // Manager can see archive and edit buttons (RC edit requires admin, so only archive visible)
    expect(screen.getByTestId("node-action-buttons")).toBeInTheDocument();
    expect(screen.getByTestId("archive-node-btn")).toBeInTheDocument();
  });

  it("Manager cannot see Edit button for Rally Cry", () => {
    // Edit Rally Cry requires admin
    renderPage(managerBridge);
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));

    expect(screen.queryByTestId("edit-node-btn")).not.toBeInTheDocument();
  });

  it("Admin sees Edit button for Rally Cry", () => {
    renderPage(adminBridge);
    fireEvent.click(screen.getByTestId("tree-node-rc-1"));

    expect(screen.getByTestId("edit-node-btn")).toBeInTheDocument();
  });

  it("Manager sees Edit button for Defining Objective", () => {
    renderPage(managerBridge);
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    fireEvent.click(screen.getByTestId("tree-node-do-1"));

    expect(screen.getByTestId("edit-node-btn")).toBeInTheDocument();
  });
});

describe("Rcdos page — create form", () => {
  it("opens create form when + Defining Objective is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("create-do-btn"));
    expect(screen.getByRole("form", { name: "Create Defining Objective" })).toBeInTheDocument();
  });

  it("opens create form for Outcome", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("create-outcome-btn"));
    expect(screen.getByRole("form", { name: "Create Outcome" })).toBeInTheDocument();
  });
});
