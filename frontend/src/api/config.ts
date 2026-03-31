/**
 * API base URL — reads from VITE_API_BASE_URL at build time.
 *
 * - Local dev (vite proxy):  "/api"
 * - Railway production:      "https://backend-xyz.up.railway.app/api"
 */
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL || "/api";
