import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Skeleton } from "../../components/ui/Skeleton.js";

describe("Skeleton", () => {
  it("renders a div", () => {
    render(<Skeleton data-testid="skel" />);
    expect(screen.getByTestId("skel")).toBeInTheDocument();
  });

  it("has animate-pulse class", () => {
    render(<Skeleton data-testid="skel" />);
    expect(screen.getByTestId("skel").className).toContain("animate-pulse");
  });

  it("has muted background", () => {
    render(<Skeleton data-testid="skel" />);
    expect(screen.getByTestId("skel").className).toContain("bg-muted");
  });

  it("merges custom className (for sizing)", () => {
    render(<Skeleton data-testid="skel" className="h-8 w-full" />);
    const cls = screen.getByTestId("skel").className;
    expect(cls).toContain("h-8");
    expect(cls).toContain("w-full");
  });
});
