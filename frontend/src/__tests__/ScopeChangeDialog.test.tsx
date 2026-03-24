/**
 * Tests for ScopeChangeDialog component.
 *
 * Covers:
 *   - Dialog renders with correct title for ADD, REMOVE, EDIT actions
 *   - Locked-plan notice displayed
 *   - ADD action shows new commit title
 *   - REMOVE action shows commit being removed
 *   - EDIT action shows before/after comparison table
 *   - Reason selection required (shows error if missing)
 *   - Free-text shown only when "Other" is selected
 *   - Calls onConfirm with selected reason
 *   - Cancel calls onCancel
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ScopeChangeDialog } from "../components/lock/ScopeChangeDialog.js";
import type { CommitResponse } from "../api/planTypes.js";

const mockCommit: CommitResponse = {
  id: "commit-1",
  planId: "plan-1",
  ownerUserId: "user-1",
  title: "Implement OAuth login",
  chessPiece: "KING",
  priorityOrder: 1,
  carryForwardStreak: 0,
  estimatePoints: 5,
  rcdoNodeId: "rcdo-1",
  createdAt: "2026-03-24T00:00:00Z",
  updatedAt: "2026-03-24T00:00:00Z",
};

describe("ScopeChangeDialog — ADD action", () => {
  it("renders ADD title", () => {
    render(
      <ScopeChangeDialog
        action="ADD"
        commit={null}
        newCommitTitle="New Feature X"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("scope-change-dialog")).toBeInTheDocument();
    expect(screen.getByText(/Add Commit/i)).toBeInTheDocument();
  });

  it("shows the new commit title in ADD preview", () => {
    render(
      <ScopeChangeDialog
        action="ADD"
        commit={null}
        newCommitTitle="New Feature X"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("scope-change-add-preview")).toHaveTextContent(
      "New Feature X",
    );
  });

  it("shows locked-plan notice", () => {
    render(
      <ScopeChangeDialog
        action="ADD"
        commit={null}
        newCommitTitle="Feature"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("scope-change-locked-notice")).toBeInTheDocument();
  });
});

describe("ScopeChangeDialog — REMOVE action", () => {
  it("renders REMOVE title", () => {
    render(
      <ScopeChangeDialog
        action="REMOVE"
        commit={mockCommit}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByText(/Remove Commit/i)).toBeInTheDocument();
  });

  it("shows commit being removed", () => {
    render(
      <ScopeChangeDialog
        action="REMOVE"
        commit={mockCommit}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("scope-change-remove-preview")).toHaveTextContent(
      "Implement OAuth login",
    );
  });
});

describe("ScopeChangeDialog — EDIT action", () => {
  it("renders EDIT title", () => {
    render(
      <ScopeChangeDialog
        action="EDIT"
        commit={mockCommit}
        proposedChanges={{ estimatePoints: 8 }}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByText(/Edit Commit/i)).toBeInTheDocument();
  });

  it("shows before/after table for changed fields", () => {
    render(
      <ScopeChangeDialog
        action="EDIT"
        commit={mockCommit}
        proposedChanges={{ estimatePoints: 8, title: "New Title" }}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("scope-change-edit-preview")).toBeInTheDocument();
    const rows = screen.getAllByTestId("scope-change-field-row");
    // Should show estimate change and title change
    expect(rows.length).toBeGreaterThanOrEqual(2);
  });
});

describe("ScopeChangeDialog — reason capture", () => {
  it("shows validation error if no reason selected on submit", () => {
    render(
      <ScopeChangeDialog
        action="REMOVE"
        commit={mockCommit}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByTestId("scope-change-confirm"));
    expect(screen.getByRole("alert")).toHaveTextContent("select");
  });

  it("calls onConfirm with selected reason", () => {
    const onConfirm = vi.fn();
    render(
      <ScopeChangeDialog
        action="REMOVE"
        commit={mockCommit}
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByTestId("scope-change-reason-select"), {
      target: { value: "Priority shift — more important work emerged" },
    });
    fireEvent.click(screen.getByTestId("scope-change-confirm"));
    expect(onConfirm).toHaveBeenCalledWith(
      "Priority shift — more important work emerged",
    );
  });

  it("shows free-text field when 'Other' is selected", () => {
    render(
      <ScopeChangeDialog
        action="REMOVE"
        commit={mockCommit}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByTestId("scope-change-reason-select"), {
      target: { value: "Other" },
    });
    expect(screen.getByTestId("scope-change-reason-text")).toBeInTheDocument();
  });

  it("calls onCancel when cancel is clicked", () => {
    const onCancel = vi.fn();
    render(
      <ScopeChangeDialog
        action="ADD"
        commit={null}
        newCommitTitle="Test"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    );
    fireEvent.click(screen.getByTestId("scope-change-cancel"));
    expect(onCancel).toHaveBeenCalled();
  });

  it("uses free-text when Other reason is entered", () => {
    const onConfirm = vi.fn();
    render(
      <ScopeChangeDialog
        action="ADD"
        commit={null}
        newCommitTitle="Feature"
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByTestId("scope-change-reason-select"), {
      target: { value: "Other" },
    });
    fireEvent.change(screen.getByTestId("scope-change-reason-text"), {
      target: { value: "Special custom reason" },
    });
    fireEvent.click(screen.getByTestId("scope-change-confirm"));
    expect(onConfirm).toHaveBeenCalledWith("Special custom reason");
  });
});
