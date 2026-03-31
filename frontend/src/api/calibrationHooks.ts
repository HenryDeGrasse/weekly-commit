/**
 * React hooks for calibration API endpoints.
 */
import { useMemo } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import { createCalibrationApi, type CalibrationProfile } from "./calibrationApi.js";

// ── Base hook ─────────────────────────────────────────────────────────────────

/** Returns a stable calibration API instance bound to the current auth token. */
function useCalibrationApi() {
  const bridge = useHostBridge();
  const { authToken } = bridge.context;
  return useMemo(() => {
    const client = createApiClient({
      baseUrl: __WC_API_BASE_URL__,
      getAuthToken: () => bridge.context.authToken,
    });
    return createCalibrationApi(client);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);
}

// ── Query hook ────────────────────────────────────────────────────────────────

/**
 * Fetches the rolling calibration profile for the given user.
 *
 * @param userId user UUID string, or {@code null} to skip fetching
 */
export function useCalibration(userId: string | null): QueryState<CalibrationProfile> {
  const api = useCalibrationApi();
  return useQuery<CalibrationProfile>(
    `calibration-${userId ?? "none"}`,
    () => {
      if (!userId) return Promise.reject(new Error("No user ID"));
      return api.fetchCalibration(userId);
    },
    { enabled: userId != null },
  );
}
