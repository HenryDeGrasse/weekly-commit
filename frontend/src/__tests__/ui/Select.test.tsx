import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { Select } from "../../components/ui/Select.js";

describe("Select", () => {
  it("renders a select element", () => {
    render(
      <Select>
        <option value="a">A</option>
        <option value="b">B</option>
      </Select>,
    );
    expect(screen.getByRole("combobox")).toBeInTheDocument();
  });

  it("renders label when provided", () => {
    render(<Select label="Status"><option>OK</option></Select>);
    expect(screen.getByText("Status")).toBeInTheDocument();
    expect(screen.getByLabelText("Status")).toBeInTheDocument();
  });

  it("shows error message", () => {
    render(<Select label="Field" error="Required"><option>A</option></Select>);
    expect(screen.getByRole("alert")).toHaveTextContent("Required");
  });

  it("sets aria-invalid when error exists", () => {
    render(<Select error="Required"><option>A</option></Select>);
    expect(screen.getByRole("combobox")).toHaveAttribute("aria-invalid", "true");
  });

  it("handles change events", () => {
    const onChange = vi.fn();
    render(
      <Select onChange={onChange}>
        <option value="a">A</option>
        <option value="b">B</option>
      </Select>,
    );
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "b" } });
    expect(onChange).toHaveBeenCalledOnce();
  });

  it("is disabled when disabled prop is set", () => {
    render(<Select disabled><option>A</option></Select>);
    expect(screen.getByRole("combobox")).toBeDisabled();
  });

  it("forwards ref", () => {
    const ref = { current: null as HTMLSelectElement | null };
    render(<Select ref={ref}><option>A</option></Select>);
    expect(ref.current).toBeInstanceOf(HTMLSelectElement);
  });
});
