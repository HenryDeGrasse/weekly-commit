/**
 * Standalone app entry for local development.
 *
 * Mirrors how the PA host app mounts WeeklyCommitRoutes:
 *   - No basename on the BrowserRouter (host doesn't use one either).
 *   - WeeklyCommitRoutes is nested under /weekly/* so the absolute NavLink
 *     paths (e.g. "/weekly/my-week") resolve correctly without doubling.
 */
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import WeeklyCommitRoutes from "./Routes.js";
import { mockHostBridge } from "./host/MockHostProvider.js";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Redirect bare root to the app entry point */}
        <Route path="/" element={<Navigate to="/weekly" replace />} />
        {/* Mount the module at /weekly/* — same shape as the host app */}
        <Route path="/weekly/*" element={<WeeklyCommitRoutes bridge={mockHostBridge} />} />
      </Routes>
    </BrowserRouter>
  );
}
