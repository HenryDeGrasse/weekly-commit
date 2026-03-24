import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import App from "./App.js";

describe("App", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/weekly/my-week");
  });

  it("renders standalone app under the /weekly base path", async () => {
    render(<App />);
    expect(await screen.findByTestId("page-my-week")).toBeInTheDocument();
  });
});
