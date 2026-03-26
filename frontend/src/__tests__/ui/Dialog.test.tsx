import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import {
  Dialog,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "../../components/ui/Dialog.js";

function renderDialog(props: { open?: boolean; onClose?: () => void } = {}) {
  const onClose = props.onClose ?? vi.fn();
  return render(
    <Dialog open={props.open ?? true} onClose={onClose} aria-label="Test dialog">
      <DialogHeader>
        <DialogTitle>Title</DialogTitle>
        <DialogDescription>Description text</DialogDescription>
      </DialogHeader>
      <div data-testid="dialog-body">Body content</div>
      <DialogFooter>
        <button onClick={onClose}>Close</button>
      </DialogFooter>
    </Dialog>,
  );
}

describe("Dialog", () => {
  it("renders when open=true", () => {
    renderDialog();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Title")).toBeInTheDocument();
    expect(screen.getByText("Description text")).toBeInTheDocument();
    expect(screen.getByTestId("dialog-body")).toBeInTheDocument();
  });

  it("does not render when open=false", () => {
    renderDialog({ open: false });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("renders dialog with aria-modal=true", () => {
    renderDialog();
    expect(screen.getByRole("dialog")).toHaveAttribute("aria-modal", "true");
  });

  it("calls onClose when Escape is pressed", () => {
    const onClose = vi.fn();
    renderDialog({ onClose });
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("calls onClose when backdrop is clicked", () => {
    const onClose = vi.fn();
    renderDialog({ onClose });
    // The overlay is the parent of the dialog panel
    const overlay = screen.getByRole("dialog").parentElement!;
    fireEvent.click(overlay);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("does not close when clicking inside the dialog panel", () => {
    const onClose = vi.fn();
    renderDialog({ onClose });
    fireEvent.click(screen.getByRole("dialog"));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("renders footer with action buttons", () => {
    renderDialog();
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
  });

  it("prevents body scroll when open", () => {
    renderDialog();
    expect(document.body.style.overflow).toBe("hidden");
  });

  it("restores body scroll on unmount", () => {
    const { unmount } = renderDialog();
    unmount();
    expect(document.body.style.overflow).not.toBe("hidden");
  });
});
