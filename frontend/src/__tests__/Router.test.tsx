import { describe, it, expect } from "vitest";
import { render, screen, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import WeeklyCommitRoutes from "../Routes.js";
import { mockHostBridge } from "../host/MockHostProvider.js";

function renderAtPath(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <WeeklyCommitRoutes bridge={mockHostBridge} />
    </MemoryRouter>,
  );
}

describe("Router", () => {
  it("renders My Week page at /my-week", async () => {
    renderAtPath("/my-week");
    expect(await screen.findByTestId("page-my-week")).toBeInTheDocument();
  });

  it("renders Reconcile page at /reconcile", async () => {
    renderAtPath("/reconcile");
    expect(await screen.findByTestId("page-reconcile")).toBeInTheDocument();
  });

  it("renders Reconcile page with planId at /reconcile/:planId", async () => {
    renderAtPath("/reconcile/plan-abc-123");
    expect(await screen.findByTestId("page-reconcile")).toBeInTheDocument();
    // Page shows loading or error state while fetching reconciliation data
    // (stub API call fails in test env, but the page container renders)
  });

  it("renders Team Week page at /team", async () => {
    renderAtPath("/team");
    expect(await screen.findByTestId("page-team-week")).toBeInTheDocument();
  });

  it("renders Team Week page with teamId at /team/:teamId", async () => {
    renderAtPath("/team/team-xyz");
    expect(await screen.findByTestId("page-team-week")).toBeInTheDocument();
    // Page shows week selector with navigation controls
    expect(await screen.findByTestId("team-week-selector")).toBeInTheDocument();
  });

  it("renders Tickets page at /tickets", async () => {
    renderAtPath("/tickets");
    expect(await screen.findByTestId("page-tickets")).toBeInTheDocument();
  });

  it("renders RCDOs page at /rcdos", async () => {
    renderAtPath("/rcdos");
    expect(await screen.findByTestId("page-rcdos")).toBeInTheDocument();
  });

  it("renders the navigation sidebar with all 7 links", async () => {
    renderAtPath("/my-week");
    const nav = await screen.findByRole("navigation", {
      name: "Weekly Commit navigation",
    });
    expect(nav).toBeInTheDocument();
    const links = nav.querySelectorAll("a");
    expect(links).toHaveLength(7);
  });

  it("redirects index to /my-week", async () => {
    renderAtPath("/");
    await act(async () => {
      // allow React to flush redirects
      await Promise.resolve();
    });
    expect(await screen.findByTestId("page-my-week")).toBeInTheDocument();
  });
});
