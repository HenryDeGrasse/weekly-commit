/**
 * Tests for the sidebar Navigation component.
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { Navigation } from "../components/Navigation.js";
import { MockHostProvider, mockHostBridge } from "../host/MockHostProvider.js";
import type { HostBridge } from "../host/HostProvider.js";

/** Default mock bridge has managerReviewEnabled + rcdoAdminEnabled = true (manager). */
function renderNav(props: { collapsed?: boolean; bridge?: HostBridge } = {}) {
  const bridge = props.bridge ?? mockHostBridge;
  return render(
    <MemoryRouter initialEntries={["/weekly/my-week"]}>
      <MockHostProvider bridge={bridge}>
        {props.collapsed !== undefined ? <Navigation collapsed={props.collapsed} /> : <Navigation />}
      </MockHostProvider>
    </MemoryRouter>,
  );
}

/** Build a bridge with IC flags (managerReviewEnabled=false, rcdoAdminEnabled=false). */
function buildIcBridge(): HostBridge {
  return {
    ...mockHostBridge,
    context: {
      ...mockHostBridge.context,
      featureFlags: {
        ...mockHostBridge.context.featureFlags,
        managerReviewEnabled: false,
        rcdoAdminEnabled: false,
      },
    },
  };
}

describe("Navigation", () => {
  it("renders 7 navigation links for managers", () => {
    renderNav();
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(7);
  });

  it("renders 5 navigation links for ICs (hides Team Week and Admin)", () => {
    renderNav({ bridge: buildIcBridge() });
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(5);
    expect(screen.queryByText("Team Week")).not.toBeInTheDocument();
    expect(screen.queryByText("Admin")).not.toBeInTheDocument();
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
    expect(svgs.length).toBe(7);
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
    expect(tooltips.length).toBe(7);
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
