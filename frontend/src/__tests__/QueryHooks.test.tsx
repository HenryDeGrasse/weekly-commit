import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { useQuery } from "../api/hooks.js";

function DisabledQueryProbe({
  fetcher,
}: {
  readonly fetcher: () => Promise<string>;
}) {
  const state = useQuery("disabled-query", fetcher, { enabled: false });

  return (
    <div>
      <span data-testid="loading">{String(state.loading)}</span>
      <span data-testid="has-error">{String(state.error !== null)}</span>
      <span data-testid="has-data">{String(state.data !== undefined)}</span>
    </div>
  );
}

describe("useQuery", () => {
  it("stays idle when disabled", () => {
    const fetcher = vi.fn<() => Promise<string>>();

    render(<DisabledQueryProbe fetcher={fetcher} />);

    expect(fetcher).not.toHaveBeenCalled();
    expect(screen.getByTestId("loading")).toHaveTextContent("false");
    expect(screen.getByTestId("has-error")).toHaveTextContent("false");
    expect(screen.getByTestId("has-data")).toHaveTextContent("false");
  });
});
