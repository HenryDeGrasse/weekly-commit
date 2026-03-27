/**
 * Route-level entry point exposed via Module Federation.
 * The PA host app imports this module to mount the weeklyCommit remote.
 *
 * Usage in host:
 *   const WeeklyCommitRoutes = React.lazy(() => import("weeklyCommit/Routes"));
 *   <WeeklyCommitRoutes bridge={hostBridge} />
 */
import { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { HostProvider, type HostBridge } from "./host/HostProvider.js";
import { AppShell } from "./components/AppShell.js";

const MyWeek = lazy(() => import("./routes/MyWeek.js"));
const Reconcile = lazy(() => import("./routes/Reconcile.js"));
const TeamWeek = lazy(() => import("./routes/TeamWeek.js"));
const Tickets = lazy(() => import("./routes/Tickets.js"));
const Rcdos = lazy(() => import("./routes/Rcdos.js"));
const Reports = lazy(() => import("./routes/Reports.js"));
const Admin = lazy(() => import("./routes/Admin.js"));

function RouteFallback() {
  return (
    <div
      role="status"
      aria-label="Loading page"
      className="py-8 text-center text-sm text-muted"
    >
      Loading…
    </div>
  );
}

interface WeeklyCommitRoutesProps {
  /** Host bridge injected by the PA host app. */
  readonly bridge: HostBridge;
}

/**
 * All weekly-commit routes wrapped in the HostProvider and AppShell.
 * The host must wrap this component with its own BrowserRouter (or
 * pass a MemoryRouter for integration tests).
 */
export default function WeeklyCommitRoutes({
  bridge,
}: WeeklyCommitRoutesProps) {
  return (
    <HostProvider bridge={bridge}>
      <AppShell>
        <Suspense fallback={<RouteFallback />}>
          <Routes>
            <Route index element={<Navigate to="my-week" replace />} />
            <Route path="my-week" element={<MyWeek />} />
            <Route path="reconcile" element={<Reconcile />} />
            <Route path="reconcile/:planId" element={<Reconcile />} />
            <Route path="team" element={<TeamWeek />} />
            <Route path="team/:teamId" element={<TeamWeek />} />
            <Route path="tickets" element={<Tickets />} />
            <Route path="rcdos" element={<Rcdos />} />
            <Route path="reports" element={<Reports />} />
            <Route path="admin" element={<Admin />} />
          </Routes>
        </Suspense>
      </AppShell>
    </HostProvider>
  );
}
