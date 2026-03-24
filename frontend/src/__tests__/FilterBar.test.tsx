/**
 * Tests for the FilterBar reusable component.
 *
 * Covers:
 *   - Renders all filter dimensions
 *   - onChange called with correct values
 *   - Clear-all button appears when a filter is active and clears filters
 *   - Multi-select toggle pills for chess piece, plan state, risk flags
 *   - Save preset flow
 *   - Load preset from dropdown
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { FilterBar, type FilterValues } from "../components/shared/FilterBar.js";

function renderBar(
  values: FilterValues = {},
  onChange = vi.fn(),
  extras: Partial<React.ComponentProps<typeof FilterBar>> = {},
) {
  return render(
    <FilterBar values={values} onChange={onChange} {...extras} />,
  );
}

describe("FilterBar — rendering", () => {
  it("renders the filter bar container", () => {
    renderBar();
    expect(screen.getByTestId("filter-bar")).toBeInTheDocument();
  });

  it("renders week, user, team, and RCDO inputs", () => {
    renderBar();
    expect(screen.getByTestId("filter-week")).toBeInTheDocument();
    expect(screen.getByTestId("filter-user")).toBeInTheDocument();
    expect(screen.getByTestId("filter-team")).toBeInTheDocument();
    expect(screen.getByTestId("filter-rcdo")).toBeInTheDocument();
  });

  it("renders chess piece toggle pills", () => {
    renderBar();
    expect(screen.getByTestId("filter-pill-king")).toBeInTheDocument();
    expect(screen.getByTestId("filter-pill-queen")).toBeInTheDocument();
    expect(screen.getByTestId("filter-pill-pawn")).toBeInTheDocument();
  });

  it("renders plan state toggle pills", () => {
    renderBar();
    expect(screen.getByTestId("filter-pill-draft")).toBeInTheDocument();
    expect(screen.getByTestId("filter-pill-locked")).toBeInTheDocument();
    expect(screen.getByTestId("filter-pill-reconciled")).toBeInTheDocument();
  });

  it("renders risk flag toggle pills", () => {
    renderBar();
    expect(screen.getByTestId("filter-pill-carry_forward")).toBeInTheDocument();
    expect(screen.getByTestId("filter-pill-auto_locked")).toBeInTheDocument();
  });
});

describe("FilterBar — filter changes", () => {
  it("calls onChange when week input changes", () => {
    const onChange = vi.fn();
    renderBar({}, onChange);
    fireEvent.change(screen.getByTestId("filter-week"), {
      target: { value: "2026-03-24" },
    });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ week: "2026-03-24" }));
  });

  it("calls onChange when user input changes", () => {
    const onChange = vi.fn();
    renderBar({}, onChange);
    fireEvent.change(screen.getByTestId("filter-user"), {
      target: { value: "user-1" },
    });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ userId: "user-1" }));
  });

  it("calls onChange when RCDO input changes", () => {
    const onChange = vi.fn();
    renderBar({}, onChange);
    fireEvent.change(screen.getByTestId("filter-rcdo"), {
      target: { value: "rcdo-1" },
    });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ rcdoNodeId: "rcdo-1" }));
  });

  it("toggles chess piece pill on click", () => {
    const onChange = vi.fn();
    renderBar({}, onChange);
    fireEvent.click(screen.getByTestId("filter-pill-king"));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ chessPieces: ["KING"] }),
    );
  });

  it("untoggle chess piece removes it from array", () => {
    const onChange = vi.fn();
    renderBar({ chessPieces: ["KING"] }, onChange);
    fireEvent.click(screen.getByTestId("filter-pill-king"));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ chessPieces: [] }),
    );
  });

  it("plan state pill is marked active when included in values", () => {
    renderBar({ planStates: ["LOCKED"] });
    const pill = screen.getByTestId("filter-pill-locked");
    expect(pill).toHaveAttribute("aria-pressed", "true");
  });

  it("toggles plan state pill on click", () => {
    const onChange = vi.fn();
    renderBar({}, onChange);
    fireEvent.click(screen.getByTestId("filter-pill-locked"));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ planStates: ["LOCKED"] }),
    );
  });

  it("risk flag pill toggles on click", () => {
    const onChange = vi.fn();
    renderBar({}, onChange);
    fireEvent.click(screen.getByTestId("filter-pill-carry_forward"));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ riskFlags: ["CARRY_FORWARD"] }),
    );
  });
});

describe("FilterBar — clear all", () => {
  it("clear-all button does NOT appear when no filter is active", () => {
    renderBar({});
    expect(screen.queryByTestId("filter-clear-all")).not.toBeInTheDocument();
  });

  it("clear-all button appears when a filter is active", () => {
    renderBar({ week: "2026-03-24" });
    expect(screen.getByTestId("filter-clear-all")).toBeInTheDocument();
  });

  it("clicking clear-all calls onChange with empty object", () => {
    const onChange = vi.fn();
    renderBar({ week: "2026-03-24", userId: "user-1" }, onChange);
    fireEvent.click(screen.getByTestId("filter-clear-all"));
    expect(onChange).toHaveBeenCalledWith({});
  });
});

describe("FilterBar — save preset", () => {
  it("does NOT show save-preset button if onSavePreset is not provided", () => {
    renderBar({});
    expect(screen.queryByTestId("filter-save-preset-btn")).not.toBeInTheDocument();
  });

  it("shows save-preset button when onSavePreset is provided", () => {
    const onSave = vi.fn();
    renderBar({}, vi.fn(), { onSavePreset: onSave });
    expect(screen.getByTestId("filter-save-preset-btn")).toBeInTheDocument();
  });

  it("clicking save-preset reveals name input", () => {
    const onSave = vi.fn();
    renderBar({}, vi.fn(), { onSavePreset: onSave });
    fireEvent.click(screen.getByTestId("filter-save-preset-btn"));
    expect(screen.getByTestId("filter-preset-name-input")).toBeInTheDocument();
  });

  it("save confirm button disabled when name is empty", () => {
    const onSave = vi.fn();
    renderBar({}, vi.fn(), { onSavePreset: onSave });
    fireEvent.click(screen.getByTestId("filter-save-preset-btn"));
    expect(screen.getByTestId("filter-preset-save-confirm")).toBeDisabled();
  });

  it("save confirm calls onSavePreset with name and values", () => {
    const onSave = vi.fn();
    const values: FilterValues = { week: "2026-03-24" };
    renderBar(values, vi.fn(), { onSavePreset: onSave });
    fireEvent.click(screen.getByTestId("filter-save-preset-btn"));
    fireEvent.change(screen.getByTestId("filter-preset-name-input"), {
      target: { value: "My filter" },
    });
    fireEvent.click(screen.getByTestId("filter-preset-save-confirm"));
    expect(onSave).toHaveBeenCalledWith("My filter", values);
  });

  it("shows preset dropdown when savedPresets are provided", () => {
    const presets = [{ id: "p-1", name: "My Preset", params: {} }];
    renderBar({}, vi.fn(), {
      savedPresets: presets,
      onLoadPreset: vi.fn(),
    });
    expect(screen.getByTestId("filter-preset-select")).toBeInTheDocument();
  });
});
