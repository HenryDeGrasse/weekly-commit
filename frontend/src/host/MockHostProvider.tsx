import type { ReactNode } from "react";
import type {
  HostContext,
  DesignTokens,
  NotificationBridge,
  TelemetryCallback,
  NavigationBridge,
} from "@weekly-commit/shared";
import { HostProvider, type HostBridge } from "./HostProvider.js";

/** Default design tokens for standalone development (maps to CSS custom props). */
export const defaultDesignTokens: DesignTokens = {
  colorPrimary: "#2563eb",
  colorSecondary: "#7c3aed",
  colorBackground: "#f9fafb",
  colorSurface: "#ffffff",
  colorText: "#111827",
  colorTextMuted: "#6b7280",
  colorBorder: "#e5e7eb",
  colorSuccess: "#059669",
  colorWarning: "#d97706",
  colorDanger: "#dc2626",
  fontFamilyBase:
    "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
  fontSizeBase: "16px",
  borderRadius: "6px",
  spacingUnit: "8px",
};

/** Mock host context used during standalone dev and unit tests. */
export const mockHostContext: HostContext = {
  authenticatedUser: {
    id: "user-dev-1",
    email: "dev@example.com",
    displayName: "Dev User",
  },
  currentTeam: {
    id: "team-dev-1",
    name: "Engineering",
  },
  managerChain: [
    {
      id: "user-manager-1",
      email: "manager@example.com",
      displayName: "Manager One",
    },
  ],
  environment: "development",
  featureFlags: {
    aiAssistanceEnabled: true,
    managerReviewEnabled: true,
    notificationsEnabled: true,
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
