/**
 * Standalone app entry for local development.
 *
 * Includes a dev-only user switcher so you can demo as Manager, IC, or Admin
 * without restarting. The switcher is a fixed banner at the top of the page.
 */
import { useState } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import WeeklyCommitRoutes from "./Routes.js";
import { mockHostBridge, mockHostContext, defaultDesignTokens } from "./host/MockHostProvider.js";
import type { HostBridge } from "./host/HostProvider.js";

// ── Dev user roster — matches V9 + V11 seed UUIDs ──────────────────────────

interface DevUser {
  id: string;
  email: string;
  displayName: string;
  role: "MANAGER" | "IC";
  label: string;
}

const DEV_USERS: DevUser[] = [
  { id: "00000000-0000-0000-0000-000000000001", email: "dev@example.com",     displayName: "Dev User",     role: "MANAGER", label: "Dev User (Manager)" },
  { id: "00000000-0000-0000-0000-000000000002", email: "manager@example.com", displayName: "Manager One",  role: "MANAGER", label: "Manager One (Manager)" },
  { id: "00000000-0000-0000-0000-000000000003", email: "alice@example.com",   displayName: "Alice Chen",   role: "IC",      label: "Alice Chen (IC)" },
  { id: "00000000-0000-0000-0000-000000000004", email: "bob@example.com",     displayName: "Bob Martinez", role: "IC",      label: "Bob Martinez (IC)" },
  { id: "00000000-0000-0000-0000-000000000005", email: "carol@example.com",   displayName: "Carol Nguyen", role: "IC",      label: "Carol Nguyen (IC)" },
  { id: "00000000-0000-0000-0000-000000000006", email: "dan@example.com",     displayName: "Dan Okafor",   role: "IC",      label: "Dan Okafor (IC)" },
];

function buildBridge(user: DevUser): HostBridge {
  const isManager = user.role === "MANAGER";
  return {
    ...mockHostBridge,
    context: {
      ...mockHostContext,
      authenticatedUser: { id: user.id, email: user.email, displayName: user.displayName },
      featureFlags: {
        ...mockHostContext.featureFlags,
        managerReviewEnabled: isManager,
        rcdoAdminEnabled: isManager,
      },
    },
    tokens: defaultDesignTokens,
  };
}

// ── Dev switcher banner ─────────────────────────────────────────────────────

function DevUserSwitcher({ current, onChange }: { current: DevUser; onChange: (u: DevUser) => void }) {
  return (
    <div
      style={{
        position: "fixed", top: 0, left: 0, right: 0, zIndex: 9999,
        background: "#1a1a1a", color: "#e5e5e5",
        display: "flex", alignItems: "center", gap: "10px",
        padding: "4px 12px", fontSize: "11px", fontFamily: "monospace",
        borderBottom: "1px solid #333",
      }}
    >
      <span style={{ color: "#737373", flexShrink: 0 }}>👤 Dev user:</span>
      <select
        value={current.id}
        onChange={(e) => {
          const u = DEV_USERS.find((u) => u.id === e.target.value) ?? DEV_USERS[0]!;
          onChange(u);
        }}
        style={{
          background: "#2a2a2a", color: "#e5e5e5", border: "1px solid #444",
          borderRadius: "3px", padding: "2px 6px", fontSize: "11px",
          fontFamily: "monospace", cursor: "pointer",
        }}
      >
        {DEV_USERS.map((u) => (
          <option key={u.id} value={u.id}>{u.label}</option>
        ))}
      </select>
      <span style={{ color: "#555", marginLeft: "auto" }}>dev mode</span>
    </div>
  );
}

// ── App ─────────────────────────────────────────────────────────────────────

export default function App() {
  // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
  const [activeUser, setActiveUser] = useState<DevUser>(DEV_USERS[0]!);
  const bridge = buildBridge(activeUser);

  return (
    <>
      <DevUserSwitcher current={activeUser} onChange={setActiveUser} />
      {/* Push page content below the fixed switcher banner */}
      <div style={{ paddingTop: "28px" }}>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Navigate to="/weekly" replace />} />
            <Route path="/weekly/*" element={<WeeklyCommitRoutes bridge={bridge} />} />
          </Routes>
        </BrowserRouter>
      </div>
    </>
  );
}
