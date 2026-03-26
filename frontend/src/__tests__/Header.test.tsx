/**
 * Tests for the redesigned Header component.
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { MockHostProvider } from "../host/MockHostProvider.js";
import { Header } from "../components/Header.js";

function renderHeader(props: {
  selectedWeek?: string;
  onWeekChange?: (w: string) => void;
  collapsed?: boolean;
  onToggleSidebar?: () => void;
} = {}) {
  const headerProps: {
    selectedWeek: string;
    onWeekChange: (w: string) => void;
    collapsed?: boolean;
    onToggleSidebar?: () => void;
  } = {
    selectedWeek: props.selectedWeek ?? "2026-03-23",
    onWeekChange: props.onWeekChange ?? vi.fn(),
  };
  if (props.collapsed !== undefined) headerProps.collapsed = props.collapsed;
  if (props.onToggleSidebar !== undefined) headerProps.onToggleSidebar = props.onToggleSidebar;

  return render(
    <MemoryRouter initialEntries={["/weekly/my-week"]}>
      <MockHostProvider>
        <Header {...headerProps} />
      </MockHostProvider>
    </MemoryRouter>,
  );
}

describe("Header", () => {
  it("renders the header banner", () => {
    renderHeader();
    expect(screen.getByRole("banner")).toBeInTheDocument();
  });

  it("renders the brand name", () => {
    renderHeader();
    expect(screen.getAllByText("Weekly Commit").length).toBeGreaterThanOrEqual(1);
  });

  it("renders the notification bell", () => {
    renderHeader();
    expect(screen.getByRole("button", { name: "Notifications" })).toBeInTheDocument();
  });

  it("renders the week selector input", () => {
    renderHeader();
    expect(screen.getByLabelText("Select week")).toBeInTheDocument();
  });

  it("renders user display name", () => {
    renderHeader();
    expect(screen.getByText("Dev User")).toBeInTheDocument();
  });

  it("renders user initials when no avatar", () => {
    renderHeader();
    // Dev User → DU
    expect(screen.getByText("DU")).toBeInTheDocument();
  });

  it("renders previous/next week buttons", () => {
    renderHeader();
    expect(screen.getByLabelText("Previous week")).toBeInTheDocument();
    expect(screen.getByLabelText("Next week")).toBeInTheDocument();
  });

  it("calls onWeekChange when prev week is clicked", () => {
    const onWeekChange = vi.fn();
    renderHeader({ onWeekChange });
    fireEvent.click(screen.getByLabelText("Previous week"));
    expect(onWeekChange).toHaveBeenCalledWith("2026-03-16");
  });

  it("calls onWeekChange when next week is clicked", () => {
    const onWeekChange = vi.fn();
    renderHeader({ onWeekChange });
    fireEvent.click(screen.getByLabelText("Next week"));
    expect(onWeekChange).toHaveBeenCalledWith("2026-03-30");
  });

  it("renders sidebar toggle button when onToggleSidebar provided", () => {
    const toggle = vi.fn();
    renderHeader({ onToggleSidebar: toggle });
    const btn = screen.getByLabelText("Collapse sidebar");
    expect(btn).toBeInTheDocument();
    fireEvent.click(btn);
    expect(toggle).toHaveBeenCalledOnce();
  });

  it("shows expand label when collapsed", () => {
    renderHeader({ collapsed: true, onToggleSidebar: vi.fn() });
    expect(screen.getByLabelText("Expand sidebar")).toBeInTheDocument();
  });

  it("does not render sidebar toggle when no callback", () => {
    renderHeader();
    expect(screen.queryByLabelText("Collapse sidebar")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Expand sidebar")).not.toBeInTheDocument();
  });

  it("renders lucide Calendar icon (SVG)", () => {
    renderHeader();
    // Brand icon should be an SVG
    const svgs = screen.getByRole("banner").querySelectorAll("svg");
    expect(svgs.length).toBeGreaterThanOrEqual(1);
  });
});
