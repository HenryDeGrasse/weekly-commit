import type { ReactNode } from "react";
import type {
  HostContext,
  DesignTokens,
  NotificationBridge,
  TelemetryCallback,
  NavigationBridge,
} from "@weekly-commit/shared";
import { HostProvider, type HostBridge } from "./HostProvider.js";

/**
 * Default design tokens for standalone development (maps to CSS custom props).
 *
 * Design direction: monochrome utilitarian — Geist Mono everywhere, zero
 * radius, neutral gray primary, minimal shadow. Inspired by shadcn/ui
 * "New York" neutral theme.
 */
export const defaultDesignTokens: DesignTokens = {
  colorPrimary: "#737373",
  colorSecondary: "#f5f5f5",
  colorBackground: "#ffffff",
  colorSurface: "#ffffff",
  colorText: "#0a0a0a",
  colorTextMuted: "#717171",
  colorBorder: "#e5e5e5",
  colorSuccess: "#3D7A4A",
  colorWarning: "#7A6520",
  colorDanger: "#9B3B3B",
  fontFamilyBase:
    "'Geist Mono', ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, 'Liberation Mono', monospace",
  fontSizeBase: "16px",
  borderRadius: "0px",
  spacingUnit: "8px",
};

/** Mock host context used during standalone dev and unit tests. */
export const mockHostContext: HostContext = {
  authenticatedUser: {
    id: "00000000-0000-0000-0000-000000000001",
    email: "dev@example.com",
    displayName: "Dev User",
  },
  currentTeam: {
    id: "00000000-0000-0000-0000-000000000010",
    name: "Engineering",
  },
  managerChain: [
    {
      id: "00000000-0000-0000-0000-000000000002",
      email: "manager@example.com",
      displayName: "Manager One",
    },
  ],
  environment: "development",
  featureFlags: {
    aiAssistanceEnabled: true,
    managerReviewEnabled: true,
    notificationsEnabled: true,
    rcdoAdminEnabled: true,
  },
  authToken: "mock-dev-token",
};

const mockNotifications: NotificationBridge = {
  showToast({ type, message }) {
    // In dev mode, log toasts to the console
    console.info(`[toast:${type}] ${message}`);
  },
};

const mockTelemetry: TelemetryCallback = {
  trackEvent(eventName, properties) {
    console.debug("[telemetry:event]", eventName, properties);
  },
  trackError(error, context) {
    console.error("[telemetry:error]", error, context);
  },
};

const mockNavigation: NavigationBridge = {
  navigateTo(path) {
    console.info("[navigation:navigateTo]", path);
  },
  getBasePath() {
    return "/weekly";
  },
};

export const mockHostBridge: HostBridge = {
  context: mockHostContext,
  tokens: defaultDesignTokens,
  notifications: mockNotifications,
  telemetry: mockTelemetry,
  navigation: mockNavigation,
};

interface MockHostProviderProps {
  readonly bridge?: HostBridge;
  readonly children: ReactNode;
}

/**
 * Drop-in replacement for HostProvider in standalone dev mode and unit tests.
 * Passes sensible mock data for all host bridge contracts.
 */
export function MockHostProvider({
  bridge = mockHostBridge,
  children,
}: MockHostProviderProps) {
  return <HostProvider bridge={bridge}>{children}</HostProvider>;
}
