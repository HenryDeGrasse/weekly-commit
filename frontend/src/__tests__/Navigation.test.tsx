/**
 * Tests for the sidebar Navigation component.
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { Navigation } from "../components/Navigation.js";

function renderNav(props: { collapsed?: boolean } = {}) {
  return render(
    <MemoryRouter initialEntries={["/weekly/my-week"]}>
      <Navigation {...props} />
    </MemoryRouter>,
  );
}

describe("Navigation", () => {
  it("renders 5 navigation links", () => {
    renderNav();
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(5);
  });

  it("renders correct nav labels", () => {
    renderNav();
    expect(screen.getByText("My Week")).toBeInTheDocument();
    expect(screen.getByText("Reconcile")).toBeInTheDocument();
    expect(screen.getByText("Team Week")).toBeInTheDocument();
    expect(screen.getByText("Tickets")).toBeInTheDocument();
    expect(screen.getByText("RCDOs")).toBeInTheDocument();
  });

  it("links point to correct routes", () => {
    renderNav();
    const links = screen.getAllByRole("link");
    const hrefs = links.map((l) => l.getAttribute("href"));
    expect(hrefs).toContain("/weekly/my-week");
    expect(hrefs).toContain("/weekly/reconcile");
    expect(hrefs).toContain("/weekly/team");
    expect(hrefs).toContain("/weekly/tickets");
    expect(hrefs).toContain("/weekly/rcdos");
  });

  it("renders lucide icons (SVG elements)", () => {
    renderNav();
    // Each nav item should have an SVG icon
    const svgs = document.querySelectorAll("nav svg");
    expect(svgs.length).toBe(5);
  });

  it("hides nav label spans when collapsed", () => {
    renderNav({ collapsed: true });
    // In collapsed mode, labels are hidden but tooltip text still exists
    // Check that no <span class="truncate"> label is rendered inside links
    const links = screen.getAllByRole("link");
    for (const link of links) {
      expect(link.querySelector(".truncate")).toBeNull();
    }
  });

  it("shows tooltip wrapper when collapsed", () => {
    renderNav({ collapsed: true });
    // Tooltips wrap the links — check for tooltip role
    const tooltips = document.querySelectorAll("[role='tooltip']");
    expect(tooltips.length).toBe(5);
  });

  it("shows labels when expanded (default)", () => {
    renderNav({ collapsed: false });
    expect(screen.getByText("My Week")).toBeInTheDocument();
    expect(screen.getByText("Tickets")).toBeInTheDocument();
  });

  it("highlights the active link", () => {
    renderNav();
    const activeLink = screen.getByText("My Week").closest("a");
    expect(activeLink?.className).toContain("bg-primary");
  });

  it("has accessible navigation landmark", () => {
    renderNav();
    expect(screen.getByRole("navigation", { name: "Weekly Commit navigation" })).toBeInTheDocument();
  });
});
