import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { useHostContext } from "../host/HostProvider.js";
import { MockHostProvider, mockHostContext } from "../host/MockHostProvider.js";

function UserDisplay() {
  const { authenticatedUser } = useHostContext();
  return <span data-testid="user-name">{authenticatedUser.displayName}</span>;
}

describe("HostProvider", () => {
  it("renders children and provides host context", () => {
    render(
      <MockHostProvider>
        <UserDisplay />
      </MockHostProvider>,
    );
    expect(screen.getByTestId("user-name")).toHaveTextContent(
      mockHostContext.authenticatedUser.displayName,
    );
  });

  it("provides currentTeam from context", () => {
    function TeamDisplay() {
      const { currentTeam } = useHostContext();
      return <span data-testid="team-name">{currentTeam?.name ?? "none"}</span>;
    }

    render(
      <MockHostProvider>
        <TeamDisplay />
      </MockHostProvider>,
    );
    expect(screen.getByTestId("team-name")).toHaveTextContent(
      mockHostContext.currentTeam?.name ?? "none",
    );
  });

  it("throws when useHostBridge is used outside HostProvider", () => {
    // Silence the React error boundary output in the test
    const consoleSpy = vi
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    try {
      expect(() => {
        render(<UserDisplay />);
      }).toThrow("useHostBridge must be used within a HostProvider");
    } finally {
      consoleSpy.mockRestore();
    }
  });

  it("provides feature flags from host context", () => {
    function FlagsDisplay() {
      const { featureFlags } = useHostContext();
      return (
        <span data-testid="ai-flag">
          {featureFlags.aiAssistanceEnabled ? "on" : "off"}
        </span>
      );
    }

    render(
      <MockHostProvider>
        <FlagsDisplay />
      </MockHostProvider>,
    );
    expect(screen.getByTestId("ai-flag")).toHaveTextContent("on");
  });
});
