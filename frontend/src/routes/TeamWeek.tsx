/**
 * Team Week dashboard — manager view of the team's weekly commitments.
 * Route: /weekly/team/:teamId?
 *
 * Features:
 *   - Team selector (for managers with multiple teams) and week selector.
 *   - Tabbed sections: Overview, By Person, By RCDO, Chess Distribution,
 *     Uncommitted Work, Exception Queue.
 *   - All sections receive data from GET /api/teams/{id}/week/{weekStart}
 *     and GET /api/teams/{id}/week/{weekStart}/exceptions.
 */
import { useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import { useHostBridge } from "../host/HostProvider.js";
import { useTeamApi, useTeamWeekView, useExceptionQueue } from "../api/teamHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import { OverviewSection } from "../components/teamweek/OverviewSection.js";
import { ByPersonSection } from "../components/teamweek/ByPersonSection.js";
import { ByRcdoSection } from "../components/teamweek/ByRcdoSection.js";
import { ChessDistributionSection } from "../components/teamweek/ChessDistributionSection.js";
import { UncommittedWorkSection } from "../components/teamweek/UncommittedWorkSection.js";
import { ExceptionQueueSection } from "../components/teamweek/ExceptionQueueSection.js";
import type { ResolveExceptionPayload, AddCommentPayload } from "../api/teamTypes.js";

// ── Week date helpers ─────────────────────────────────────────────────────────

function getWeekStartDate(offsetWeeks = 0): string {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const daysToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const monday = new Date(now);
  monday.setDate(now.getDate() + daysToMonday + offsetWeeks * 7);
  return monday.toISOString().slice(0, 10);
}

function formatWeekLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  return `Week of ${d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  })}`;
}

// ── Section tab type ──────────────────────────────────────────────────────────

type TabId =
  | "overview"
  | "by-person"
  | "by-rcdo"
  | "chess"
  | "uncommitted"
  | "exceptions";

const TABS: Array<{ id: TabId; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "by-person", label: "By Person" },
  { id: "by-rcdo", label: "By RCDO" },
  { id: "chess", label: "Chess" },
  { id: "uncommitted", label: "Uncommitted" },
  { id: "exceptions", label: "Exceptions" },
];

// ── TeamWeek Page ─────────────────────────────────────────────────────────────

export default function TeamWeek() {
  const { teamId: routeTeamId } = useParams<{ teamId?: string }>();
  const bridge = useHostBridge();
  const teamApi = useTeamApi();

  // Prefer the route teamId, fallback to host context team
  const teamId = routeTeamId ?? bridge.context.currentTeam?.id ?? null;
  const userId = bridge.context.authenticatedUser.id;

  const [weekOffset, setWeekOffset] = useState(0);
  const weekStartDate = getWeekStartDate(weekOffset);

  const [activeTab, setActiveTab] = useState<TabId>("overview");
  const [actionError, setActionError] = useState<string | null>(null);

  const {
    data: teamWeekData,
    loading: teamLoading,
    error: teamError,
    refetch: refetchTeamWeek,
  } = useTeamWeekView(teamId, weekStartDate);

  const {
    data: exceptions,
    loading: exceptionsLoading,
    error: exceptionsError,
    refetch: refetchExceptions,
  } = useExceptionQueue(teamId, weekStartDate);

  const { data: rcdoTree } = useRcdoTree();

  // ── Action handlers ──────────────────────────────────────────────────

  const handleResolveException = useCallback(
    async (exceptionId: string, payload: ResolveExceptionPayload) => {
      setActionError(null);
      try {
        await teamApi.resolveException(exceptionId, payload);
        refetchExceptions();
      } catch (err) {
        setActionError(err instanceof Error ? err.message : "Resolve failed");
        throw err;
      }
    },
    [teamApi, refetchExceptions],
  );

  const handleAddComment = useCallback(
    async (payload: AddCommentPayload) => {
      setActionError(null);
      try {
        await teamApi.addComment(payload);
      } catch (err) {
        setActionError(err instanceof Error ? err.message : "Comment failed");
        throw err;
      }
    },
    [teamApi],
  );

  const handleQuickAssign = useCallback(
    async (ticketId: string, assigneeUserId: string) => {
      setActionError(null);
      try {
        await teamApi.quickAssignTicket(ticketId, { assigneeUserId });
        refetchTeamWeek();
      } catch (err) {
        setActionError(err instanceof Error ? err.message : "Assign failed");
        throw err;
      }
    },
    [teamApi, refetchTeamWeek],
  );

  // ── Shared styles ────────────────────────────────────────────────────

  const btnNav: React.CSSProperties = {
    padding: "0.375rem 0.625rem",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    background: "var(--color-surface)",
    cursor: "pointer",
    fontFamily: "inherit",
    fontSize: "0.875rem",
    color: "var(--color-text)",
  };

  const btnSecondary: React.CSSProperties = {
    padding: "0.5rem 1rem",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    background: "var(--color-surface)",
    color: "var(--color-text)",
    cursor: "pointer",
    fontFamily: "inherit",
    fontSize: "0.875rem",
  };

  // ── Render ───────────────────────────────────────────────────────────

  return (
    <div
      className="route-page"
      data-testid="page-team-week"
      style={{ display: "flex", flexDirection: "column", gap: "1rem" }}
    >
      {/* Page header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: "0.5rem",
        }}
      >
        <h2 style={{ margin: 0, fontSize: "1.25rem" }}>Team Week</h2>
        {teamWeekData && (
          <span style={{ fontSize: "0.875rem", color: "var(--color-text-muted)", fontWeight: 600 }}>
            {teamWeekData.teamName}
          </span>
        )}
      </div>

      {/* Week selector */}
      <div
        data-testid="team-week-selector"
        style={{
          display: "flex",
          alignItems: "center",
          gap: "0.5rem",
          flexWrap: "wrap",
        }}
      >
        <button
          style={btnNav}
          onClick={() => setWeekOffset((o) => o - 1)}
          aria-label="Previous week"
          data-testid="team-prev-week-btn"
        >
          ◀
        </button>
        <span
          data-testid="team-week-label"
          style={{ fontWeight: 600, fontSize: "0.9rem", minWidth: "14rem", textAlign: "center" }}
        >
          {formatWeekLabel(weekStartDate)}
        </span>
        <button
          style={btnNav}
          onClick={() => setWeekOffset((o) => o + 1)}
          aria-label="Next week"
          data-testid="team-next-week-btn"
        >
          ▶
        </button>
        {weekOffset !== 0 && (
          <button
            style={btnSecondary}
            onClick={() => setWeekOffset(0)}
            data-testid="team-current-week-btn"
          >
            Today
          </button>
        )}
      </div>

      {/* No team selected */}
      {!teamId && (
        <div
          data-testid="no-team-selected"
          style={{
            padding: "2rem",
            textAlign: "center",
            color: "var(--color-text-muted)",
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
          }}
        >
          No team selected. Please select a team to view the dashboard.
        </div>
      )}

      {/* Loading */}
      {teamId && (teamLoading || exceptionsLoading) && (
        <div
          role="status"
          aria-label="Loading team data"
          data-testid="team-week-loading"
          style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}
        >
          Loading team data…
        </div>
      )}

      {/* Error */}
      {(teamError ?? exceptionsError) && (
        <div
          role="alert"
          data-testid="team-week-error"
          style={{
            color: "var(--color-danger)",
            background: "#fef2f2",
            borderRadius: "var(--border-radius)",
            padding: "0.75rem",
            fontSize: "0.875rem",
          }}
        >
          Failed to load team data: {(teamError ?? exceptionsError)?.message}
        </div>
      )}

      {/* Action error */}
      {actionError && (
        <div
          role="alert"
          data-testid="team-action-error"
          style={{
            color: "var(--color-danger)",
            background: "#fef2f2",
            borderRadius: "var(--border-radius)",
            padding: "0.5rem 0.75rem",
            fontSize: "0.8rem",
          }}
        >
          {actionError}
        </div>
      )}

      {/* Dashboard content */}
      {teamWeekData && (
        <>
          {/* Tab navigation */}
          <nav
            aria-label="Team week sections"
            style={{ display: "flex", gap: "0.125rem", borderBottom: "2px solid var(--color-border)", flexWrap: "wrap" }}
          >
            {TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={activeTab === tab.id}
                onClick={() => setActiveTab(tab.id)}
                data-testid={`tab-${tab.id}`}
                style={{
                  padding: "0.5rem 1rem",
                  border: "none",
                  borderBottom: activeTab === tab.id ? "2px solid var(--color-primary)" : "2px solid transparent",
                  marginBottom: "-2px",
                  background: "none",
                  cursor: "pointer",
                  fontFamily: "inherit",
                  fontSize: "0.875rem",
                  fontWeight: activeTab === tab.id ? 700 : 400,
                  color: activeTab === tab.id ? "var(--color-primary)" : "var(--color-text)",
                }}
              >
                {tab.label}
                {tab.id === "exceptions" && (exceptions?.filter((e) => !e.resolved).length ?? 0) > 0 && (
                  <span
                    style={{
                      marginLeft: "0.375rem",
                      fontSize: "0.65rem",
                      fontWeight: 700,
                      padding: "1px 5px",
                      borderRadius: "999px",
                      background: "#fee2e2",
                      color: "#991b1b",
                    }}
                  >
                    {exceptions?.filter((e) => !e.resolved).length}
                  </span>
                )}
              </button>
            ))}
          </nav>

          {/* Tab panels */}
          <div role="tabpanel" aria-label={activeTab} data-testid={`panel-${activeTab}`}>
            {activeTab === "overview" && (
              <OverviewSection
                complianceSummary={teamWeekData.complianceSummary}
                memberViews={teamWeekData.memberViews}
                exceptions={exceptions ?? []}
              />
            )}
            {activeTab === "by-person" && (
              <ByPersonSection
                memberViews={teamWeekData.memberViews}
                complianceSummary={teamWeekData.complianceSummary}
              />
            )}
            {activeTab === "by-rcdo" && (
              <ByRcdoSection
                rcdoRollup={teamWeekData.rcdoRollup}
                rcdoTree={rcdoTree ?? []}
                memberViews={teamWeekData.memberViews}
              />
            )}
            {activeTab === "chess" && (
              <ChessDistributionSection
                chessDistribution={teamWeekData.chessDistribution}
              />
            )}
            {activeTab === "uncommitted" && (
              <UncommittedWorkSection
                assignedTickets={teamWeekData.uncommittedAssignedTickets}
                unassignedTickets={teamWeekData.uncommittedUnassignedTickets}
                onQuickAssign={handleQuickAssign}
              />
            )}
            {activeTab === "exceptions" && (
              <ExceptionQueueSection
                exceptions={exceptions ?? []}
                actorUserId={userId}
                onResolve={handleResolveException}
                onAddComment={handleAddComment}
              />
            )}
          </div>
        </>
      )}
    </div>
  );
}
