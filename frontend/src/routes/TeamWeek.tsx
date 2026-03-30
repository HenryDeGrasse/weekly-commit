/**
 * Team Week dashboard — manager view of the team's weekly commitments.
 * Route: /weekly/team/:teamId?
 */
import { useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import { ChevronLeft, ChevronRight, Users } from "lucide-react";
import { useSelectedWeek } from "../lib/WeekContext.js";
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
import { CollapsibleSection } from "../components/shared/CollapsibleSection.js";
import { TeamWeekSkeleton } from "../components/shared/skeletons/TeamWeekSkeleton.js";
import { EmptyState } from "../components/shared/EmptyState.js";

function getWeekStartDate(offsetWeeks = 0): string {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const daysToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const monday = new Date(now);
  monday.setDate(now.getDate() + daysToMonday + offsetWeeks * 7);
  // Use local date parts — toISOString() converts to UTC and can shift the day.
  const yyyy = monday.getFullYear();
  const mm = String(monday.getMonth() + 1).padStart(2, '0');
  const dd = String(monday.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
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

  const { selectedWeek: weekStartDate, setSelectedWeek } = useSelectedWeek();

  const [activeTab, setActiveTab] = useState<TabId>("overview");
  const [insightsExpanded, setInsightsExpanded] = useState(false);
  const [managerSummaryPreview, setManagerSummaryPreview] = useState<string | null>(null);
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
        <Button variant="secondary" size="sm" onClick={() => { const d = new Date(weekStartDate + "T00:00:00"); d.setDate(d.getDate() - 7); setSelectedWeek(`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`); }} aria-label="Previous week" data-testid="team-prev-week-btn">
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span data-testid="team-week-label" className="font-semibold text-sm min-w-[14rem] text-center">{formatWeekLabel(weekStartDate)}</span>
        <Button variant="secondary" size="sm" onClick={() => { const d = new Date(weekStartDate + "T00:00:00"); d.setDate(d.getDate() + 7); setSelectedWeek(`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`); }} aria-label="Next week" data-testid="team-next-week-btn">
          <ChevronRight className="h-4 w-4" />
        </Button>
        {weekStartDate !== getWeekStartDate(0) && (
          <Button variant="secondary" size="sm" onClick={() => setSelectedWeek(getWeekStartDate(0))} data-testid="team-current-week-btn">Today</Button>
        )}
      </div>

      {!teamId && (
        <EmptyState
          data-testid="no-team-selected"
          icon={<Users className="h-10 w-10" />}
          title="No team selected"
          description="Please select a team from the navigation to view the weekly dashboard."
        />
      )}
      {teamId && (teamLoading || exceptionsLoading) && (
        <div data-testid="team-week-loading">
          <span role="status" aria-label="Loading team data" className="sr-only">Loading team data…</span>
          <TeamWeekSkeleton />
        </div>
      )}
      {(teamError ?? exceptionsError) && (
        <div role="alert" data-testid="team-week-error" className="rounded-default border border-border bg-foreground/5 px-3 py-2 text-sm text-foreground font-semibold">
          Failed to load team data: {(teamError ?? exceptionsError)?.message}
        </div>
      )}
      {actionError && (
        <div role="alert" data-testid="team-action-error" className="rounded-default border border-border bg-foreground/5 px-3 py-2 text-xs text-foreground font-semibold">{actionError}</div>
      )}

      {teamWeekData && (
        <>
          {/* Manager AI summary — collapsed by default; preview shown in badge */}
          {aiAssistanceEnabled && teamId && (
            <CollapsibleSection
              id="manager-ai-summary"
              title="Manager AI Summary"
              defaultExpanded={false}
              badge={
                managerSummaryPreview != null ? (
                  <span className="text-[0.65rem] text-info truncate max-w-[180px] block">
                    {managerSummaryPreview}…
                  </span>
                ) : undefined
              }
            >
              <ManagerAiSummaryCard
                teamId={teamId}
                weekStart={weekStartDate}
                onSummaryText={setManagerSummaryPreview}
              />
            </CollapsibleSection>
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
            {activeTab === "uncommitted" && <UncommittedWorkSection assignedTickets={teamWeekData.uncommittedAssignedTickets} unassignedTickets={teamWeekData.uncommittedUnassignedTickets} onQuickAssign={handleQuickAssign} memberNames={Object.fromEntries(teamWeekData.memberViews.map((m) => [m.userId, m.displayName]))} />}
            {activeTab === "exceptions" && <ExceptionQueueSection exceptions={exceptions ?? []} actorUserId={userId} onResolve={handleResolveException} onAddComment={handleAddComment} />}
            {activeTab === "history" && <TeamHistoryView entries={teamHistory?.entries ?? []} loading={historyLoading} />}
          </div>
        </>
      )}
    </div>
  );
}
