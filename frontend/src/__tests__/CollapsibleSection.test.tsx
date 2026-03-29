import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CollapsibleSection } from "../components/shared/CollapsibleSection.js";

describe("CollapsibleSection", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  // ── Rendering ────────────────────────────────────────────────────────────

  it("renders the title", () => {
    render(
      <CollapsibleSection id="test-render" title="My Section">
        <p>Content here</p>
      </CollapsibleSection>,
    );
    expect(screen.getByText("My Section")).toBeInTheDocument();
  });

  it("renders children when expanded", () => {
    render(
      <CollapsibleSection id="test-children" title="Section" defaultExpanded={true}>
        <p>Child content</p>
      </CollapsibleSection>,
    );
    expect(screen.getByText("Child content")).toBeInTheDocument();
  });

  it("renders badge when provided", () => {
    render(
      <CollapsibleSection
        id="test-badge"
        title="With Badge"
        badge={<span data-testid="count-badge">3 hints</span>}
      >
        Content
      </CollapsibleSection>,
    );
    expect(screen.getByTestId("count-badge")).toBeInTheDocument();
    expect(screen.getByText("3 hints")).toBeInTheDocument();
  });

  it("does not render badge slot when badge is not provided", () => {
    render(
      <CollapsibleSection id="test-no-badge" title="No Badge">
        Content
      </CollapsibleSection>,
    );
    // No badge element — just title and toggle
    expect(screen.getByText("No Badge")).toBeInTheDocument();
    expect(screen.queryByTestId("count-badge")).not.toBeInTheDocument();
  });

  // ── defaultExpanded ──────────────────────────────────────────────────────

  it("is expanded by default when defaultExpanded=true", () => {
    render(
      <CollapsibleSection id="test-expanded" title="Section" defaultExpanded={true}>
        Content
      </CollapsibleSection>,
    );
    expect(
      screen.getByTestId("collapsible-header-test-expanded"),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("is collapsed by default when defaultExpanded=false", () => {
    render(
      <CollapsibleSection id="test-collapsed" title="Section" defaultExpanded={false}>
        Content
      </CollapsibleSection>,
    );
    expect(
      screen.getByTestId("collapsible-header-test-collapsed"),
    ).toHaveAttribute("aria-expanded", "false");
  });

  it("defaults to expanded when defaultExpanded is not specified", () => {
    render(
      <CollapsibleSection id="test-default" title="Section">
        Content
      </CollapsibleSection>,
    );
    expect(
      screen.getByTestId("collapsible-header-test-default"),
    ).toHaveAttribute("aria-expanded", "true");
  });

  // ── Toggle behavior ──────────────────────────────────────────────────────

  it("toggles from expanded to collapsed on header click", () => {
    render(
      <CollapsibleSection id="test-toggle" title="Toggle" defaultExpanded={true}>
        Content
      </CollapsibleSection>,
    );
    const header = screen.getByTestId("collapsible-header-test-toggle");
    expect(header).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(header);
    expect(header).toHaveAttribute("aria-expanded", "false");
  });

  it("toggles from collapsed to expanded on header click", () => {
    render(
      <CollapsibleSection id="test-toggle2" title="Toggle2" defaultExpanded={false}>
        Content
      </CollapsibleSection>,
    );
    const header = screen.getByTestId("collapsible-header-test-toggle2");
    expect(header).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(header);
    expect(header).toHaveAttribute("aria-expanded", "true");
  });

  it("toggles back and forth correctly", () => {
    render(
      <CollapsibleSection id="test-roundtrip" title="Roundtrip" defaultExpanded={true}>
        Content
      </CollapsibleSection>,
    );
    const header = screen.getByTestId("collapsible-header-test-roundtrip");
    fireEvent.click(header); // collapse
    expect(header).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(header); // expand
    expect(header).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(header); // collapse again
    expect(header).toHaveAttribute("aria-expanded", "false");
  });

  // ── localStorage persistence ─────────────────────────────────────────────

  it("persists collapsed state to localStorage", () => {
    render(
      <CollapsibleSection id="persist-test" title="Persistent" defaultExpanded={true}>
        Content
      </CollapsibleSection>,
    );
    const header = screen.getByTestId("collapsible-header-persist-test");
    fireEvent.click(header); // collapse → should write "false"
    expect(localStorage.getItem("wc-section-persist-test")).toBe("false");
  });

  it("persists expanded state to localStorage", () => {
    render(
      <CollapsibleSection id="persist-test2" title="Persistent2" defaultExpanded={false}>
        Content
      </CollapsibleSection>,
    );
    const header = screen.getByTestId("collapsible-header-persist-test2");
    fireEvent.click(header); // expand → should write "true"
    expect(localStorage.getItem("wc-section-persist-test2")).toBe("true");
  });

  it("reads initial state from localStorage instead of defaultExpanded", () => {
    // Pre-seed localStorage with collapsed=false even though defaultExpanded=true
    localStorage.setItem("wc-section-stored-test", "false");
    render(
      <CollapsibleSection id="stored-test" title="Stored" defaultExpanded={true}>
        Content
      </CollapsibleSection>,
    );
    // localStorage wins over defaultExpanded
    expect(
      screen.getByTestId("collapsible-header-stored-test"),
    ).toHaveAttribute("aria-expanded", "false");
  });

  it("uses defaultExpanded when localStorage has no value for this key", () => {
    // No localStorage entry for this id
    render(
      <CollapsibleSection id="fresh-test" title="Fresh" defaultExpanded={false}>
        Content
      </CollapsibleSection>,
    );
    expect(
      screen.getByTestId("collapsible-header-fresh-test"),
    ).toHaveAttribute("aria-expanded", "false");
  });

  // ── Accessibility ────────────────────────────────────────────────────────

  it("header button carries aria-controls pointing to content region", () => {
    render(
      <CollapsibleSection id="a11y-test" title="Accessible">
        Content
      </CollapsibleSection>,
    );
    const header = screen.getByTestId("collapsible-header-a11y-test");
    const contentId = header.getAttribute("aria-controls");
    expect(contentId).toBeTruthy();
    const content = document.getElementById(contentId!);
    expect(content).not.toBeNull();
    expect(content).toHaveAttribute("role", "region");
  });

  it("content region carries aria-labelledby pointing to the header", () => {
    render(
      <CollapsibleSection id="label-test" title="Labelled">
        Content
      </CollapsibleSection>,
    );
    const content = screen.getByTestId("collapsible-content-label-test");
    const labelId = content.getAttribute("aria-labelledby");
    expect(labelId).toBeTruthy();
    const header = document.getElementById(labelId!);
    expect(header).not.toBeNull();
  });

  it("header is a button element", () => {
    render(
      <CollapsibleSection id="button-test" title="Button Test">
        Content
      </CollapsibleSection>,
    );
    const header = screen.getByTestId("collapsible-header-button-test");
    expect(header.tagName.toLowerCase()).toBe("button");
    expect(header).toHaveAttribute("type", "button");
  });

  // ── Outer wrapper ────────────────────────────────────────────────────────

  it("renders outer wrapper with expected testid", () => {
    render(
      <CollapsibleSection id="wrapper-test" title="Wrapper">
        Content
      </CollapsibleSection>,
    );
    expect(
      screen.getByTestId("collapsible-section-wrapper-test"),
    ).toBeInTheDocument();
  });

  it("applies custom className to outer wrapper", () => {
    render(
      <CollapsibleSection id="cls-test" title="Custom class" className="my-4">
        Content
      </CollapsibleSection>,
    );
    expect(
      screen.getByTestId("collapsible-section-cls-test").className,
    ).toContain("my-4");
  });
});
