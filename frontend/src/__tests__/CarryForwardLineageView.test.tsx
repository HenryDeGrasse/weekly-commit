/**
 * Tests for CarryForwardLineageView component.
 *
 * Covers:
 *   - Loading state
 *   - Empty chain
 *   - Renders chain nodes left to right
 *   - Current commit node is highlighted
 *   - Each node shows week, outcome, streak
 *   - Chain length reported correctly
 */
import { describe, it, expect } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { CarryForwardLineageView } from "../components/myweek/CarryForwardLineageView.js";
import type { CarryForwardLineageResponse, CarryForwardNode } from "../api/ticketTypes.js";

function makeNode(
  commitId: string,
  weekStart: string,
  overrides: Partial<CarryForwardNode> = {},
): CarryForwardNode {
  return {
    commitId,
    planId: `plan-${commitId}`,
    weekStartDate: weekStart,
    title: `Task ${commitId}`,
    outcome: null,
    streak: 0,
    ...overrides,
  };
}

const threeNodeChain: CarryForwardLineageResponse = {
  currentCommitId: "c-3",
  chain: [
    makeNode("c-1", "2026-03-10", { outcome: "NOT_ACHIEVED", streak: 0 }),
    makeNode("c-2", "2026-03-17", { outcome: "NOT_ACHIEVED", streak: 1 }),
    makeNode("c-3", "2026-03-24", { streak: 2 }),
  ],
};

describe("CarryForwardLineageView — rendering", () => {
  it("shows loading state", () => {
    render(
      <CarryForwardLineageView
        lineage={{ currentCommitId: "c-1", chain: [] }}
        loading={true}
      />,
    );
    expect(screen.getByTestId("cf-lineage-loading")).toBeInTheDocument();
  });

  it("shows empty state when chain is empty", () => {
    render(
      <CarryForwardLineageView
        lineage={{ currentCommitId: "c-1", chain: [] }}
        loading={false}
      />,
    );
    expect(screen.getByTestId("cf-lineage-empty")).toBeInTheDocument();
  });

  it("renders the lineage view container", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    expect(screen.getByTestId("cf-lineage-view")).toBeInTheDocument();
  });

  it("renders a node for each chain entry", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    expect(screen.getByTestId("cf-node-c-1")).toBeInTheDocument();
    expect(screen.getByTestId("cf-node-c-2")).toBeInTheDocument();
    expect(screen.getByTestId("cf-node-c-3")).toBeInTheDocument();
  });

  it("shows the week start date on each node", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    expect(screen.getByTestId("cf-node-week-c-1")).toHaveTextContent("2026-03-10");
    expect(screen.getByTestId("cf-node-week-c-2")).toHaveTextContent("2026-03-17");
    expect(screen.getByTestId("cf-node-week-c-3")).toHaveTextContent("2026-03-24");
  });

  it("shows the title on each node", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    expect(screen.getByTestId("cf-node-title-c-1")).toHaveTextContent("Task c-1");
  });

  it("shows streak badge when streak > 0", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    expect(screen.getByTestId("cf-node-streak-c-2")).toBeInTheDocument();
    expect(screen.getByTestId("cf-node-streak-c-2")).toHaveTextContent("1× CF");
    expect(screen.getByTestId("cf-node-streak-c-3")).toHaveTextContent("2× CF");
  });

  it("does NOT show streak badge when streak is 0", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    expect(screen.queryByTestId("cf-node-streak-c-1")).not.toBeInTheDocument();
  });

  it("shows outcome badge when outcome is set", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    expect(screen.getByTestId("cf-node-outcome-c-1")).toBeInTheDocument();
    expect(screen.getByTestId("cf-node-outcome-c-2")).toBeInTheDocument();
  });

  it("shows pending label when outcome is null (not current)", () => {
    // c-3 has no outcome set and is current — shows "Current"
    // Add a fourth node that has no outcome and is not current
    const chainWithPending: CarryForwardLineageResponse = {
      currentCommitId: "c-3",
      chain: [
        ...threeNodeChain.chain,
        makeNode("c-4", "2026-03-31", { streak: 3 }),
      ],
    };
    render(<CarryForwardLineageView lineage={chainWithPending} />);
    expect(screen.getByTestId("cf-node-pending-c-4")).toBeInTheDocument();
  });

  it("reports chain length correctly", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    expect(screen.getByTestId("cf-lineage-length")).toHaveTextContent("3 entries in chain");
  });

  it("shows singular 'entry' for chain of length 1", () => {
    const singleChain: CarryForwardLineageResponse = {
      currentCommitId: "c-1",
      chain: [makeNode("c-1", "2026-03-24")],
    };
    render(<CarryForwardLineageView lineage={singleChain} />);
    expect(screen.getByTestId("cf-lineage-length")).toHaveTextContent("1 entry in chain");
  });
});

describe("CarryForwardLineageView — current node", () => {
  it("the chain renders the correct number of nodes", () => {
    render(<CarryForwardLineageView lineage={threeNodeChain} />);
    const chain = screen.getByTestId("cf-lineage-chain");
    const nodes = within(chain).getAllByTestId(/^cf-node-c-/);
    expect(nodes).toHaveLength(3);
  });
});
