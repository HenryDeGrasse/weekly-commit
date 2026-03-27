import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Badge } from "../../components/ui/Badge.js";

describe("Badge", () => {
  it("renders children text", () => {
    render(<Badge>DRAFT</Badge>);
    expect(screen.getByText("DRAFT")).toBeInTheDocument();
  });

  it("applies default variant", () => {
    render(<Badge>Default</Badge>);
    expect(screen.getByText("Default").className).toContain("bg-muted");
  });

  it("applies primary variant", () => {
    render(<Badge variant="primary">Primary</Badge>);
    expect(screen.getByText("Primary").className).toContain("text-foreground");
  });

  it("applies success variant", () => {
    render(<Badge variant="success">OK</Badge>);
    expect(screen.getByText("OK").className).toContain("text-foreground");
  });

  it("applies warning variant", () => {
    render(<Badge variant="warning">Warn</Badge>);
    expect(screen.getByText("Warn").className).toContain("text-muted");
  });

  it("applies danger variant", () => {
    render(<Badge variant="danger">Error</Badge>);
    expect(screen.getByText("Error").className).toContain("text-foreground");
  });

  it("applies draft plan state variant", () => {
    render(<Badge variant="draft">DRAFT</Badge>);
    expect(screen.getByText("DRAFT").className).toContain("text-muted");
  });

  it("applies locked plan state variant", () => {
    render(<Badge variant="locked">LOCKED</Badge>);
    expect(screen.getByText("LOCKED").className).toContain("text-foreground");
  });

  it("applies reconciling plan state variant", () => {
    render(<Badge variant="reconciling">RECONCILING</Badge>);
    expect(screen.getByText("RECONCILING").className).toContain("text-muted");
  });

  it("applies reconciled plan state variant", () => {
    render(<Badge variant="reconciled">RECONCILED</Badge>);
    expect(screen.getByText("RECONCILED").className).toContain("text-foreground");
  });

  it("has consistent shape", () => {
    render(<Badge>Pill</Badge>);
    expect(screen.getByText("Pill").className).toContain("rounded-sm");
  });

  it("merges custom className", () => {
    render(<Badge className="ml-2">Extra</Badge>);
    expect(screen.getByText("Extra").className).toContain("ml-2");
  });

  it("passes through data-testid", () => {
    render(<Badge data-testid="my-badge">Badge</Badge>);
    expect(screen.getByTestId("my-badge")).toBeInTheDocument();
  });
});
