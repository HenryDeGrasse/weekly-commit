/**
 * App shell — desktop-first layout with sidebar navigation and content area.
 * Applies design tokens as CSS custom properties on the root element.
 */
import { type ReactNode, useState } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { Navigation } from "./Navigation.js";
import { Header, getWeekMonday } from "./Header.js";

interface AppShellProps {
  readonly children: ReactNode;
}

/** Converts a DesignTokens object into CSS custom properties. */
function tokensToStyle(
  tokens: import("@weekly-commit/shared").DesignTokens,
): React.CSSProperties {
  return {
    "--color-primary": tokens.colorPrimary,
    "--color-secondary": tokens.colorSecondary,
    "--color-background": tokens.colorBackground,
    "--color-surface": tokens.colorSurface,
    "--color-text": tokens.colorText,
    "--color-text-muted": tokens.colorTextMuted,
    "--color-border": tokens.colorBorder,
    "--color-success": tokens.colorSuccess,
    "--color-warning": tokens.colorWarning,
    "--color-danger": tokens.colorDanger,
    "--font-family-base": tokens.fontFamilyBase,
    "--font-size-base": tokens.fontSizeBase,
    "--border-radius": tokens.borderRadius,
    "--spacing-unit": tokens.spacingUnit,
  } as React.CSSProperties;
}

export function AppShell({ children }: AppShellProps) {
  const { tokens } = useHostBridge();
  const [selectedWeek, setSelectedWeek] = useState(() =>
    getWeekMonday(new Date()),
  );

  return (
    <div
      className="app-shell"
      style={{
        ...tokensToStyle(tokens),
        display: "grid",
        gridTemplateRows: "auto 1fr",
        gridTemplateColumns: "220px 1fr",
        minHeight: "100vh",
        background: "var(--color-background)",
        color: "var(--color-text)",
        fontFamily: "var(--font-family-base)",
        fontSize: "var(--font-size-base)",
      }}
      data-testid="app-shell"
    >
      <div
        style={{ gridRow: "1", gridColumn: "1 / -1" }}
      >
        <Header
          selectedWeek={selectedWeek}
          onWeekChange={setSelectedWeek}
        />
      </div>

      <aside
        className="app-sidebar"
        style={{
          gridRow: "2",
          gridColumn: "1",
          background: "var(--color-surface)",
          borderRight: "1px solid var(--color-border)",
          padding: "var(--spacing-unit)",
        }}
        aria-label="Sidebar"
      >
        <Navigation />
      </aside>

      <main
        className="app-content"
        style={{
          gridRow: "2",
          gridColumn: "2",
          padding: "calc(var(--spacing-unit) * 2)",
          overflow: "auto",
        }}
        role="main"
      >
        {children}
      </main>
    </div>
  );
}
