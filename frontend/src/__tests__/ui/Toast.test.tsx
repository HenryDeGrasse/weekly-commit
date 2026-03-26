import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { ToastProvider, useToast } from "../../components/ui/Toast.js";

function ToastTrigger({ type = "info" as "success" | "info" | "warning" | "error", message = "Test toast" }) {
  const { showToast } = useToast();
  return (
    <button
      onClick={() => showToast({ type, message, durationMs: 500 })}
      data-testid="trigger"
    >
      Show
    </button>
  );
}

function renderWithProvider(ui: React.ReactElement = <ToastTrigger />) {
  return render(<ToastProvider>{ui}</ToastProvider>);
}

describe("Toast", () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it("shows toast when triggered", () => {
    renderWithProvider();
    fireEvent.click(screen.getByTestId("trigger"));
    expect(screen.getByText("Test toast")).toBeInTheDocument();
  });

  it("shows success toast variant", () => {
    renderWithProvider(<ToastTrigger type="success" message="Done!" />);
    fireEvent.click(screen.getByTestId("trigger"));
    expect(screen.getByText("Done!")).toBeInTheDocument();
    const el = screen.getByText("Done!").closest("div");
    expect(el).not.toBeNull();
    expect(el!.className).toContain("text-success");
  });

  it("shows error toast variant", () => {
    renderWithProvider(<ToastTrigger type="error" message="Failed" />);
    fireEvent.click(screen.getByTestId("trigger"));
    const el = screen.getByText("Failed").closest("div");
    expect(el).not.toBeNull();
    expect(el!.className).toContain("text-danger");
  });

  it("shows warning toast variant", () => {
    renderWithProvider(<ToastTrigger type="warning" message="Warn" />);
    fireEvent.click(screen.getByTestId("trigger"));
    const el = screen.getByText("Warn").closest("div");
    expect(el).not.toBeNull();
    expect(el!.className).toContain("text-warning");
  });

  it("auto-dismisses after durationMs", () => {
    renderWithProvider();
    fireEvent.click(screen.getByTestId("trigger"));
    expect(screen.getByText("Test toast")).toBeInTheDocument();
    act(() => { vi.advanceTimersByTime(600); });
    expect(screen.queryByText("Test toast")).not.toBeInTheDocument();
  });

  it("dismisses when X button is clicked", () => {
    renderWithProvider();
    fireEvent.click(screen.getByTestId("trigger"));
    expect(screen.getByText("Test toast")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Dismiss"));
    expect(screen.queryByText("Test toast")).not.toBeInTheDocument();
  });

  it("supports multiple toasts simultaneously", () => {
    renderWithProvider(
      <>
        <ToastTrigger type="info" message="First" />
        <ToastTrigger type="error" message="Second" />
      </>,
    );
    // Both use same trigger testid — click both
    const triggers = screen.getAllByTestId("trigger");
    fireEvent.click(triggers[0]!);
    fireEvent.click(triggers[1]!);
    expect(screen.getByText("First")).toBeInTheDocument();
    expect(screen.getByText("Second")).toBeInTheDocument();
  });

  it("renders in an aria-live region", () => {
    renderWithProvider();
    fireEvent.click(screen.getByTestId("trigger"));
    const liveRegion = screen.getByText("Test toast").closest('[aria-live]');
    expect(liveRegion).toHaveAttribute("aria-live", "polite");
  });

  it("throws if useToast is used outside provider", () => {
    expect(() => {
      render(<ToastTrigger />);
    }).toThrow("useToast must be used within <ToastProvider>");
  });
});
