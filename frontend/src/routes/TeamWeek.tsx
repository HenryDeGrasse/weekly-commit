/**
 * Team Week dashboard — manager view of the team's weekly commitments.
 * Route: /weekly/team/:teamId?
 */
import { useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "../components/ui/Button.js";
import { cn } from "../lib/utils.js";
import { useHostBridge } from "../host/HostProvider.js";
import { useTeamApi, useTeamWeekView, useExceptionQueue } from "../api/teamHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import { OverviewSection } from "../components/teamweek/OverviewSection.js";
import { ByPersonSection } from "../components/teamweek/ByPersonSection.js";
import { ByRcdoSection } from "../components/teamweek/ByRcdoSection.js";
import { ChessDistributionSection } from "../components/teamweek/ChessDistributionSection.js";
import { UncommittedWorkSection } from "../components/teamweek/UncommittedWorkSection.js";
import { ExceptionQueueSection } from "../components/teamweek/ExceptionQueueSection.js";
import { TeamHistoryView } from "../components/teamweek/TeamHistoryView.js";
import { useTeamHistory } from "../api/ticketHooks.js";
import type { ResolveExceptionPayload, AddCommentPayload } from "../api/teamTypes.js";
import { InsightPanel } from "../components/ai/InsightPanel.js";
import { SemanticSearchInput } from "../components/ai/SemanticSearchInput.js";
import { ManagerAiSummaryCard } from "../components/ai/ManagerAiSummaryCard.js";
import { TeamRiskSummaryBanner } from "../components/ai/TeamRiskSummaryBanner.js";

function getWeekStartDate(offsetWeeks = 0): string {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const daysToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const monday = new Date(now);
  monday.setDate(now.getDate() + daysToMonday + offsetWeeks * 7);
  return monday.toISOString().slice(0, 10);
}

function formatWeekLabel(isoDate: string): string {
  return `Week of ${new Date(`${isoDate}T00:00:00`).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}`;
}

type TabId = "overview" | "by-person" | "by-rcdo" | "chess" | "uncommitted" | "exceptions" | "history";
const TABS: Array<{ id: TabId; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "by-person", label: "By Person" },
  { id: "by-rcdo", label: "By RCDO" },
  { id: "chess", label: "Chess" },
  { id: "uncommitted", label: "Uncommitted" },
  { id: "exceptions", label: "Exceptions" },
  { id: "history", label: "History" },
];

export default function TeamWeek() {
  const { teamId: routeTeamId } = useParams<{ teamId?: string }>();
  const bridge = useHostBridge();
  const teamApi = useTeamApi();
  const teamId = routeTeamId ?? bridge.context.currentTeam?.id ?? null;
  const userId = bridge.context.authenticatedUser.id;

  const aiAssistanceEnabled = bridge.context.featureFlags.aiAssistanceEnabled;

  const [weekOffset, setWeekOffset] = useState(0);
  const weekStartDate = getWeekStartDate(weekOffset);
  const [activeTab, setActiveTab] = useState<TabId>("overview");
  const [insightsExpanded, setInsightsExpanded] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);

  const { data: teamWeekData, loading: teamLoading, error: teamError, refetch: refetchTeamWeek } = useTeamWeekView(teamId, weekStartDate);
  const { data: exceptions, loading: exceptionsLoading, error: exceptionsError, refetch: refetchExceptions } = useExceptionQueue(teamId, weekStartDate);
  const { data: rcdoTree } = useRcdoTree();
  const { data: teamHistory, loading: historyLoading } = useTeamHistory(teamId);

  const handleResolveException = useCallback(async (exceptionId: string, payload: ResolveExceptionPayload) => {
    setActionError(null);
    try { await teamApi.resolveException(exceptionId, payload); refetchExceptions(); }
    catch (err) { setActionError(err instanceof Error ? err.message : "Resolve failed"); throw err; }
  }, [teamApi, refetchExceptions]);

  const handleAddComment = useCallback(async (payload: AddCommentPayload) => {
    setActionError(null);
    try { await teamApi.addComment(payload); }
    catch (err) { setActionError(err instanceof Error ? err.message : "Comment failed"); throw err; }
  }, [teamApi]);

  const handleQuickAssign = useCallback(async (ticketId: string, assigneeUserId: string) => {
    setActionError(null);
    try { await teamApi.quickAssignTicket(ticketId, { assigneeUserId }); refetchTeamWeek(); }
    catch (err) { setActionError(err instanceof Error ? err.message : "Assign failed"); throw err; }
  }, [teamApi, refetchTeamWeek]);

  const unresolvedExceptions = exceptions?.filter((e) => !e.resolved).length ?? 0;

  return (
    <div className="flex flex-col gap-4" data-testid="page-team-week">
      {/* Page header */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="m-0 text-xl font-bold">Team Week</h2>
        {teamWeekData && <span className="text-sm text-muted font-semibold">{teamWeekData.teamName}</span>}
      </div>

      {/* AI semantic search — full-width row so the answer card doesn't shift the header */}
      {aiAssistanceEnabled && teamId && (
        <SemanticSearchInput teamId={teamId} userId={userId} />
      )}

      {/* Week selector */}
      <div data-testid="team-week-selector" className="flex items-center gap-2 flex-wrap">
        <Button variant="secondary" size="sm" onClick={() => setWeekOffset((o) => o - 1)} aria-label="Previous week" data-testid="team-prev-week-btn">
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span data-testid="team-week-label" className="font-semibold text-sm min-w-[14rem] text-center">{formatWeekLabel(weekStartDate)}</span>
        <Button variant="secondary" size="sm" onClick={() => setWeekOffset((o) => o + 1)} aria-label="Next week" data-testid="team-next-week-btn">
          <ChevronRight className="h-4 w-4" />
        </Button>
        {weekOffset !== 0 && (
          <Button variant="secondary" size="sm" onClick={() => setWeekOffset(0)} data-testid="team-current-week-btn">Today</Button>
        )}
      </div>

      {!teamId && (
        <div data-testid="no-team-selected" className="py-8 text-center text-sm text-muted rounded-default border border-border bg-surface">
          No team selected. Please select a team to view the dashboard.
        </div>
      )}
      {teamId && (teamLoading || exceptionsLoading) && (
        <div role="status" aria-label="Loading team data" data-testid="team-week-loading" className="text-sm text-muted">Loading team data…</div>
      )}
      {(teamError ?? exceptionsError) && (
        <div role="alert" data-testid="team-week-error" className="rounded-default border border-neutral-300 bg-neutral-100 px-3 py-2 text-sm text-foreground font-semibold">
          Failed to load team data: {(teamError ?? exceptionsError)?.message}
        </div>
      )}
      {actionError && (
        <div role="alert" data-testid="team-action-error" className="rounded-default border border-neutral-300 bg-neutral-100 px-3 py-2 text-xs text-foreground font-semibold">{actionError}</div>
      )}

      {teamWeekData && (
        <>
          {/* Manager AI summary — expanded by default; the first thing managers see */}
          {aiAssistanceEnabled && teamId && (
            <ManagerAiSummaryCard teamId={teamId} weekStart={weekStartDate} />
          )}

          {/* Team-level risk signals — aggregated across all locked member plans */}
          {aiAssistanceEnabled && teamWeekData.memberViews.some((m) => m.planId && m.planState === "LOCKED") && (
            <TeamRiskSummaryBanner
              memberPlans={teamWeekData.memberViews
                .filter((m): m is typeof m & { planId: string } => m.planId != null && m.planState === "LOCKED")
                .map((m) => ({ planId: m.planId, displayName: m.displayName }))}
            />
          )}

          {/* AI insights (collapsible, open by default) */}
          {aiAssistanceEnabled && teamId && (
            <div data-testid="team-insights-section">
              <button
                type="button"
                onClick={() => setInsightsExpanded((e) => !e)}
                className="flex items-center gap-1.5 text-xs font-semibold text-muted hover:text-foreground transition-colors mb-2"
                data-testid="team-insights-toggle"
                aria-expanded={insightsExpanded}
              >
                <span>{insightsExpanded ? "▾" : "▸"}</span>
                AI Insights
              </button>
              {insightsExpanded && (
                <InsightPanel mode="team" teamId={teamId} weekStart={weekStartDate} />
              )}
            </div>
          )}

          {/* Tab navigation */}
          <nav aria-label="Team week sections" className="flex gap-0 border-b-2 border-border flex-wrap">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={activeTab === tab.id}
                onClick={() => setActiveTab(tab.id)}
                data-testid={`tab-${tab.id}`}
                className={cn(
                  "px-4 py-2 border-none border-b-2 -mb-0.5 bg-transparent cursor-pointer text-sm font-medium transition-colors",
                  activeTab === tab.id ? "border-b-primary font-bold text-primary" : "border-b-transparent text-muted hover:text-foreground",
                )}
                style={{ borderBottom: activeTab === tab.id ? "2px solid var(--color-primary)" : "2px solid transparent" }}
              >
                {tab.label}
                {tab.id === "exceptions" && unresolvedExceptions > 0 && (
                  <span className="ml-1.5 text-[0.6rem] font-bold px-1 py-px rounded-full bg-foreground text-background">{unresolvedExceptions}</span>
                )}
              </button>
            ))}
          </nav>

          {/* Tab panels */}
          <div role="tabpanel" aria-label={activeTab} data-testid={`panel-${activeTab}`}>
            {activeTab === "overview" && <OverviewSection complianceSummary={teamWeekData.complianceSummary} memberViews={teamWeekData.memberViews} exceptions={exceptions ?? []} />}
            {activeTab === "by-person" && <ByPersonSection memberViews={teamWeekData.memberViews} complianceSummary={teamWeekData.complianceSummary} />}
            {activeTab === "by-rcdo" && <ByRcdoSection rcdoRollup={teamWeekData.rcdoRollup} rcdoTree={rcdoTree ?? []} memberViews={teamWeekData.memberViews} />}
            {activeTab === "chess" && <ChessDistributionSection chessDistribution={teamWeekData.chessDistribution} />}
            {activeTab === "uncommitted" && <UncommittedWorkSection assignedTickets={teamWeekData.uncommittedAssignedTickets} unassignedTickets={teamWeekData.uncommittedUnassignedTickets} onQuickAssign={handleQuickAssign} />}
            {activeTab === "exceptions" && <ExceptionQueueSection exceptions={exceptions ?? []} actorUserId={userId} onResolve={handleResolveException} onAddComment={handleAddComment} />}
            {activeTab === "history" && <TeamHistoryView entries={teamHistory?.entries ?? []} loading={historyLoading} />}
          </div>
        </>
      )}
    </div>
  );
}
