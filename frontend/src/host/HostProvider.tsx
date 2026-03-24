import {
  createContext,
  useContext,
  type ReactNode,
} from "react";
import type {
  HostContext,
  DesignTokens,
  NotificationBridge,
  TelemetryCallback,
  NavigationBridge,
} from "@weekly-commit/shared";

export interface HostBridge {
  readonly context: HostContext;
  readonly tokens: DesignTokens;
  readonly notifications: NotificationBridge;
  readonly telemetry: TelemetryCallback;
  readonly navigation: NavigationBridge;
}

const HostBridgeContext = createContext<HostBridge | null>(null);

export function useHostBridge(): HostBridge {
  const ctx = useContext(HostBridgeContext);
  if (ctx === null) {
    throw new Error("useHostBridge must be used within a HostProvider");
  }
  return ctx;
}

export function useHostContext(): HostContext {
  return useHostBridge().context;
}

interface HostProviderProps {
  readonly bridge: HostBridge;
  readonly children: ReactNode;
}

/**
 * Provides the host bridge to the entire weeklyCommit remote tree.
 * In production the PA host mounts this with real bridge implementations.
 * In standalone dev/test use MockHostProvider instead.
 */
export function HostProvider({ bridge, children }: HostProviderProps) {
  return (
    <HostBridgeContext.Provider value={bridge}>
      {children}
    </HostBridgeContext.Provider>
  );
}
