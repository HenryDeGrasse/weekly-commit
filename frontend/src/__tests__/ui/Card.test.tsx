import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "../../components/ui/Card.js";

describe("Card", () => {
  it("renders children", () => {
    render(<Card data-testid="card">Content</Card>);
    expect(screen.getByTestId("card")).toHaveTextContent("Content");
  });

  it("has surface background and border", () => {
    render(<Card data-testid="card">C</Card>);
    const cls = screen.getByTestId("card").className;
    expect(cls).toContain("bg-surface");
    expect(cls).toContain("border");
  });

  it("merges custom className", () => {
    render(<Card className="max-w-lg">C</Card>);
    expect(screen.getByText("C").className).toContain("max-w-lg");
  });

  it("forwards ref", () => {
    const ref = { current: null as HTMLDivElement | null };
    render(<Card ref={ref}>C</Card>);
    expect(ref.current).toBeInstanceOf(HTMLDivElement);
  });
});

describe("CardHeader", () => {
  it("renders with flex layout", () => {
    render(<CardHeader data-testid="header">Header</CardHeader>);
    expect(screen.getByTestId("header").className).toContain("flex");
  });
});

describe("CardTitle", () => {
  it("renders as h3", () => {
    render(<CardTitle>My Title</CardTitle>);
    expect(screen.getByRole("heading", { level: 3 })).toHaveTextContent("My Title");
  });

  it("applies font-bold", () => {
    render(<CardTitle>T</CardTitle>);
    expect(screen.getByRole("heading").className).toContain("font-bold");
  });
});

describe("CardContent", () => {
  it("renders children with padding", () => {
    render(<CardContent data-testid="content">Body</CardContent>);
    const el = screen.getByTestId("content");
    expect(el).toHaveTextContent("Body");
    expect(el.className).toContain("px-4");
  });
});

describe("CardFooter", () => {
  it("renders with flex and gap", () => {
    render(<CardFooter data-testid="footer">Footer</CardFooter>);
    const el = screen.getByTestId("footer");
    expect(el.className).toContain("flex");
    expect(el.className).toContain("gap-2");
  });
});

describe("Card compound", () => {
  it("renders full card structure", () => {
    render(
      <Card data-testid="full-card">
        <CardHeader>
          <CardTitle>Title</CardTitle>
        </CardHeader>
        <CardContent>Body</CardContent>
        <CardFooter>
          <button>Action</button>
        </CardFooter>
      </Card>,
    );
    expect(screen.getByTestId("full-card")).toBeInTheDocument();
    expect(screen.getByRole("heading")).toHaveTextContent("Title");
    expect(screen.getByText("Body")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Action" })).toBeInTheDocument();
  });
});
