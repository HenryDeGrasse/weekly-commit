/**
 * Tests for the route-aware Breadcrumb component.
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { Breadcrumb } from "../components/Breadcrumb.js";

function renderWithRoute(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Breadcrumb />
    </MemoryRouter>,
  );
}

describe("Breadcrumb", () => {
  it("renders nothing for a single-segment path", () => {
    renderWithRoute("/weekly");
    // Only one crumb ("Weekly Commit") — breadcrumb should return null
    expect(screen.queryByRole("navigation", { name: "Breadcrumb" })).not.toBeInTheDocument();
  });

  it("renders breadcrumb for /weekly/my-week", () => {
    renderWithRoute("/weekly/my-week");
    const nav = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(nav).toBeInTheDocument();
    expect(screen.getByText("Weekly Commit")).toBeInTheDocument();
    expect(screen.getByText("My Week")).toBeInTheDocument();
  });

  it("renders breadcrumb for /weekly/team", () => {
    renderWithRoute("/weekly/team");
    expect(screen.getByText("Weekly Commit")).toBeInTheDocument();
    expect(screen.getByText("Team Week")).toBeInTheDocument();
  });

  it("renders breadcrumb for /weekly/tickets", () => {
    renderWithRoute("/weekly/tickets");
    expect(screen.getByText("Tickets")).toBeInTheDocument();
  });

  it("renders breadcrumb for /weekly/rcdos", () => {
    renderWithRoute("/weekly/rcdos");
    expect(screen.getByText("RCDOs")).toBeInTheDocument();
  });

  it("marks the last crumb as current page", () => {
    renderWithRoute("/weekly/reconcile");
    const last = screen.getByText("Reconcile");
    // The last crumb renders as a span with font-medium
    expect(last.tagName).toBe("SPAN");
    expect(last.className).toContain("font-medium");
  });

  it("renders the first crumb as a link", () => {
    renderWithRoute("/weekly/my-week");
    const link = screen.getByText("Weekly Commit");
    // Should be a link (anchor), not a plain span
    expect(link.closest("a")).toBeInTheDocument();
  });

  it("skips dynamic UUID segments", () => {
    renderWithRoute("/weekly/reconcile/some-uuid-123");
    // Should show Weekly Commit > Reconcile, not the UUID
    expect(screen.getByText("Reconcile")).toBeInTheDocument();
    expect(screen.queryByText("some-uuid-123")).not.toBeInTheDocument();
  });
});
