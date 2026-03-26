import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { Input } from "../../components/ui/Input.js";

describe("Input", () => {
  it("renders a text input", () => {
    render(<Input placeholder="Enter text" />);
    expect(screen.getByPlaceholderText("Enter text")).toBeInTheDocument();
  });

  it("renders label when provided", () => {
    render(<Input label="Name" />);
    expect(screen.getByText("Name")).toBeInTheDocument();
  });

  it("associates label with input via htmlFor/id", () => {
    render(<Input label="Email" id="email-input" />);
    const input = screen.getByLabelText("Email");
    expect(input).toHaveAttribute("id", "email-input");
  });

  it("auto-generates id from label", () => {
    render(<Input label="First Name" />);
    const input = screen.getByLabelText("First Name");
    expect(input).toHaveAttribute("id", "input-first-name");
  });

  it("shows error message", () => {
    render(<Input label="Field" error="Required" />);
    expect(screen.getByRole("alert")).toHaveTextContent("Required");
  });

  it("sets aria-invalid when error exists", () => {
    render(<Input label="Field" error="Required" />);
    expect(screen.getByRole("textbox")).toHaveAttribute("aria-invalid", "true");
  });

  it("applies error border styling", () => {
    render(<Input error="Required" />);
    expect(screen.getByRole("textbox").className).toContain("border-danger");
  });

  it("does not show error when no error prop", () => {
    render(<Input label="Field" />);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("renders icon slot", () => {
    render(<Input icon={<span data-testid="icon">🔍</span>} />);
    expect(screen.getByTestId("icon")).toBeInTheDocument();
  });

  it("adds left padding when icon is present", () => {
    render(<Input icon={<span>🔍</span>} />);
    expect(screen.getByRole("textbox").className).toContain("pl-9");
  });

  it("handles change events", () => {
    const onChange = vi.fn();
    render(<Input onChange={onChange} />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "hello" } });
    expect(onChange).toHaveBeenCalledOnce();
  });

  it("renders with errorTestId on error element", () => {
    render(<Input error="Bad" errorTestId="my-error" />);
    expect(screen.getByTestId("my-error")).toHaveTextContent("Bad");
  });

  it("is disabled when disabled prop is set", () => {
    render(<Input disabled />);
    expect(screen.getByRole("textbox")).toBeDisabled();
  });

  it("merges custom className", () => {
    render(<Input className="w-full" />);
    expect(screen.getByRole("textbox").className).toContain("w-full");
  });

  it("forwards ref", () => {
    const ref = { current: null as HTMLInputElement | null };
    render(<Input ref={ref} />);
    expect(ref.current).toBeInstanceOf(HTMLInputElement);
  });
});
