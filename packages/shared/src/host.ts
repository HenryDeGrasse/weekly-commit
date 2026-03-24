/**
 * Host bridge interfaces for Module Federation integration.
 * Consumed by the weeklyCommit remote to receive context from the PA host app.
 */

/** Authenticated user information provided by the host. */
export interface AuthenticatedUser {
  readonly id: string;
  readonly email: string;
  readonly displayName: string;
  readonly avatarUrl?: string;
}

/** Team context provided by the host. */
export interface TeamContext {
  readonly id: string;
  readonly name: string;
  readonly parentTeamId?: string;
}

/** Environment identifier passed by the host. */
export type HostEnvironment = "development" | "staging" | "production";

/** Feature flags toggled by the host or release system. */
export interface FeatureFlags {
  readonly aiAssistanceEnabled: boolean;
  readonly managerReviewEnabled: boolean;
  readonly notificationsEnabled: boolean;
  [key: string]: boolean;
}

/**
 * Primary bridge contract between the PA host app and the weeklyCommit remote.
 * The host must supply this via the HostProvider.
 */
export interface HostContext {
  /** The currently signed-in user. */
  readonly authenticatedUser: AuthenticatedUser;
  /** The team currently in focus (may be null before selection). */
  readonly currentTeam: TeamContext | null;
  /** Ordered list of managers from direct manager up to org root. */
  readonly managerChain: readonly AuthenticatedUser[];
  /** Deployment environment. */
  readonly environment: HostEnvironment;
  /** Feature-flag map. */
  readonly featureFlags: FeatureFlags;
  /** Bearer token for API requests; refreshed by the host. */
  readonly authToken: string;
}

/**
 * CSS custom-property design tokens provided by the host.
 * The remote applies these as CSS variables on its root element.
 */
export interface DesignTokens {
  readonly colorPrimary: string;
  readonly colorSecondary: string;
  readonly colorBackground: string;
  readonly colorSurface: string;
  readonly colorText: string;
  readonly colorTextMuted: string;
  readonly colorBorder: string;
  readonly colorSuccess: string;
  readonly colorWarning: string;
  readonly colorDanger: string;
  readonly fontFamilyBase: string;
  readonly fontSizeBase: string;
  readonly borderRadius: string;
  readonly spacingUnit: string;
}

/** Callback for firing a cross-app notification (toast) via the host shell. */
export interface NotificationBridge {
  /** Show a success/info/warning/error toast in the host shell. */
  showToast(options: {
    type: "success" | "info" | "warning" | "error";
    message: string;
    durationMs?: number;
  }): void;
}

/** Telemetry callback for structured event tracking. */
export interface TelemetryCallback {
  trackEvent(eventName: string, properties?: Record<string, unknown>): void;
  trackError(error: Error, context?: Record<string, unknown>): void;
}

/** Navigation bridge for host-managed routing (cross-remote navigation). */
export interface NavigationBridge {
  /** Navigate to a path inside the host SPA (outside weeklyCommit). */
  navigateTo(path: string): void;
  /** Returns the current host-level base path prefix for the remote. */
  getBasePath(): string;
}
