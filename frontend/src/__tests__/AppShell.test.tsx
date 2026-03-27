import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AppShell } from "../components/AppShell.js";
import { MockHostProvider } from "../host/MockHostProvider.js";

function renderShell(children = <div data-testid="content">page content</div>) {
  return render(
    <MemoryRouter initialEntries={["/my-week"]}>
      <MockHostProvider>
        <AppShell>{children}</AppShell>
      </MockHostProvider>
    </MemoryRouter>,
  );
}

describe("AppShell", () => {
  it("renders the app shell container", () => {
    renderShell();
    expect(screen.getByTestId("app-shell")).toBeInTheDocument();
  });

  it("renders the navigation sidebar", () => {
    renderShell();
    expect(
      screen.getByRole("navigation", { name: "Weekly Commit navigation" }),
    ).toBeInTheDocument();
  });

  it("renders all 7 navigation links", () => {
    renderShell();
    const nav = screen.getByRole("navigation", {
      name: "Weekly Commit navigation",
    });
    const links = nav.querySelectorAll("a");
    expect(links).toHaveLength(7);
  });

  it("navigation links reference the expected paths", () => {
    renderShell();
    const nav = screen.getByRole("navigation", {
      name: "Weekly Commit navigation",
    });
    const hrefs = Array.from(nav.querySelectorAll("a")).map((a) =>
      a.getAttribute("href"),
    );
    expect(hrefs).toContain("/weekly/my-week");
    expect(hrefs).toContain("/weekly/reconcile");
    expect(hrefs).toContain("/weekly/team");
    expect(hrefs).toContain("/weekly/tickets");
    expect(hrefs).toContain("/weekly/rcdos");
  });

  it("renders the header banner", () => {
    renderShell();
    expect(screen.getByRole("banner")).toBeInTheDocument();
  });

  it("renders user display name in the header", () => {
    renderShell();
    expect(screen.getByText("Dev User")).toBeInTheDocument();
  });

  it("renders main content area", () => {
    renderShell();
    expect(screen.getByRole("main")).toBeInTheDocument();
    expect(screen.getByTestId("content")).toBeInTheDocument();
  });

  it("renders the notification bell button", () => {
    renderShell();
    expect(
      screen.getByRole("button", { name: "Notifications" }),
    ).toBeInTheDocument();
  });

  it("renders the week selector input", () => {
    renderShell();
    expect(screen.getByLabelText("Select week")).toBeInTheDocument();
  });
});
