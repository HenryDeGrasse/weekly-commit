import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmptyState } from "../components/shared/EmptyState.js";
import { Button } from "../components/ui/Button.js";

describe("EmptyState", () => {
  it("renders the title", () => {
    render(<EmptyState title="No items yet" />);
    expect(screen.getByText("No items yet")).toBeInTheDocument();
  });

  it("renders description when provided", () => {
    render(<EmptyState title="Empty" description="Add something to get started." />);
    expect(screen.getByText("Add something to get started.")).toBeInTheDocument();
  });

  it("does not render description element when omitted", () => {
    render(<EmptyState title="Empty" />);
    // Only the title paragraph should be present; no second <p>
    const paragraphs = document.querySelectorAll("p");
    expect(paragraphs).toHaveLength(1);
  });

  it("renders a CTA action when provided", () => {
    render(
      <EmptyState
        title="No commits"
        action={<Button data-testid="cta-btn">Add Commit</Button>}
      />,
    );
    expect(screen.getByTestId("cta-btn")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add Commit" })).toBeInTheDocument();
  });

  it("does not render action slot when action is omitted", () => {
    render(<EmptyState title="Empty" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("renders the icon slot when provided", () => {
    render(
      <EmptyState
        title="Empty"
        icon={<span data-testid="empty-icon">📭</span>}
      />,
    );
    expect(screen.getByTestId("empty-icon")).toBeInTheDocument();
  });

  it("uses the default data-testid when none is specified", () => {
    render(<EmptyState title="Default testid" />);
    expect(screen.getByTestId("empty-state")).toBeInTheDocument();
  });

  it("uses a custom data-testid when specified", () => {
    render(<EmptyState title="Custom" data-testid="my-empty" />);
    expect(screen.getByTestId("my-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("empty-state")).not.toBeInTheDocument();
  });

  it("applies custom className to the wrapper", () => {
    render(<EmptyState title="Styled" className="my-custom-class" />);
    expect(screen.getByTestId("empty-state").className).toContain("my-custom-class");
  });

  it("renders all elements together correctly", () => {
    render(
      <EmptyState
        title="No tickets"
        description="Create your first ticket to track work."
        icon={<span data-testid="icon">🎫</span>}
        action={<Button data-testid="create-btn">Create Ticket</Button>}
      />,
    );
    expect(screen.getByText("No tickets")).toBeInTheDocument();
    expect(screen.getByText("Create your first ticket to track work.")).toBeInTheDocument();
    expect(screen.getByTestId("icon")).toBeInTheDocument();
    expect(screen.getByTestId("create-btn")).toBeInTheDocument();
  });
});
