/**
 * API base URL — inlined at build time via Vite `define`.
 *
 * __WC_API_BASE_URL__ is replaced by Vite with a string literal at every usage site,
 * avoiding Module Federation chunk boundary issues.
 *
 * Set VITE_API_BASE_URL env var at build time to override:
 *   - Local dev:   not set → defaults to "/api" (Vite proxy handles it)
 *   - Production:  "https://backend-xyz.up.railway.app/api"
 */
declare global {
  const __WC_API_BASE_URL__: string;
}

export {};
