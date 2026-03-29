/**
 * App shell — polished sidebar + header + content layout.
 * Applies design tokens as CSS custom properties on the root element.
 * Sidebar is collapsible with state persisted to localStorage.
 */
import { type ReactNode, useState, useEffect } from "react";
import type { DesignTokens } from "@weekly-commit/shared";
import { useHostBridge } from "../host/HostProvider.js";
import { Navigation } from "./Navigation.js";
import { Header, getWeekMonday } from "./Header.js";
import { cn } from "../lib/utils.js";
import { useTheme } from "../lib/useTheme.js";
import { useAiMode } from "../lib/useAiMode.js";

interface AppShellProps {
  readonly children: ReactNode;
}

const SIDEBAR_KEY = "wc-sidebar-collapsed";

/**
 * Converts DesignTokens into CSS custom properties.
 *
 * When dark mode is active, color tokens are omitted so the CSS
 * [data-theme="dark"] overrides in index.css take effect. Non-color
 * tokens (font, spacing, radius) always apply.
 */
function tokensToStyle(
  tokens: DesignTokens,
  isDark: boolean,
): React.CSSProperties {
  const base: Record<string, string> = {
    "--font-family-base": tokens.fontFamilyBase,
    "--font-size-base": tokens.fontSizeBase,
    "--border-radius": tokens.borderRadius,
    "--spacing-unit": tokens.spacingUnit,
  };

  if (!isDark) {
    // In light mode (or host-provided), apply color tokens as inline vars
    // so the host app's palette overrides our CSS defaults.
    Object.assign(base, {
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
    });
  }

  return base as React.CSSProperties;
}

/**
 * Sidebar footer toggle for the global AI suggestion mode.
 * 'full' = AI sections enabled and auto-run. 'on-demand' = all AI sections
 * collapsed by default; auto-run disabled.
 */
function AiModeToggle() {
  const { aiMode, setAiMode } = useAiMode();
  const isOnDemand = aiMode === "on-demand";
  return (
    <div className="flex items-center gap-2">
      <span className="text-[10px] text-muted">AI:</span>
      <button
        type="button"
        onClick={() => setAiMode(isOnDemand ? "full" : "on-demand")}
        className="text-[10px] text-muted hover:text-foreground transition-colors underline cursor-pointer bg-transparent border-none p-0"
        aria-label={isOnDemand ? "AI mode: on-demand — click to enable full AI" : "AI mode: full — click to switch to on-demand"}
        data-testid="ai-mode-toggle"
        title={isOnDemand ? "AI suggestions on-demand only" : "AI suggestions fully enabled"}
      >
        {isOnDemand ? "on-demand" : "full"}
      </button>
    </div>
  );
}

export function AppShell({ children }: AppShellProps) {
  const { tokens } = useHostBridge();
  const { resolved } = useTheme();
  const isDark = resolved === "dark";
  const [selectedWeek, setSelectedWeek] = useState(() =>
    getWeekMonday(new Date()),
  );
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(SIDEBAR_KEY) === "true";
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(SIDEBAR_KEY, String(collapsed));
    } catch {
      // ignore
    }
  }, [collapsed]);

  return (
    <div
      className="flex h-screen flex-col overflow-hidden bg-background text-foreground"
      style={{
        ...tokensToStyle(tokens, isDark),
        fontFamily: "var(--font-family-base)",
        fontSize: "var(--font-size-base)",
      }}
      data-testid="app-shell"
    >
      {/* Header */}
      <Header
        selectedWeek={selectedWeek}
        onWeekChange={setSelectedWeek}
        collapsed={collapsed}
        onToggleSidebar={() => setCollapsed((c) => !c)}
      />

      {/* Body: sidebar + main */}
      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar */}
        <aside
          className={cn(
            "flex shrink-0 flex-col border-r border-border bg-surface transition-sidebar overflow-y-auto overflow-x-hidden",
            collapsed ? "w-14 px-1.5 py-4" : "w-56 px-3 py-4",
          )}
          aria-label="Sidebar"
        >
          {/* Brand mark in sidebar — visible when expanded */}
          {!collapsed && (
            <div className="mb-6 px-3">
              <p className="text-[10px] font-semibold uppercase tracking-widest text-muted">
                Navigation
              </p>
            </div>
          )}

          <Navigation collapsed={collapsed} />

          {/* Spacer pushes bottom content down */}
          <div className="flex-1" />

          {/* AI mode toggle + version at bottom */}
          {!collapsed && (
            <div className="mt-4 px-3 pt-4 border-t border-border flex flex-col gap-2">
              <AiModeToggle />
              <p className="text-[10px] text-muted">v1.0.0</p>
            </div>
          )}
        </aside>

        {/* Main content */}
        <main
          className="flex-1 overflow-auto p-6"
          role="main"
        >
          {children}
        </main>
      </div>
    </div>
  );
}
