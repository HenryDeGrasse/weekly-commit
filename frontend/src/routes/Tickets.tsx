/**
 * Tickets page — native ticket management.
 * Route: /weekly/tickets
 */
import { useState, useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { X } from "lucide-react";
import { Button } from "../components/ui/Button.js";
import { useHostBridge } from "../host/HostProvider.js";
import { useTicketList, useTicketApi, useTicket } from "../api/ticketHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import { TicketListView, type TicketSortColumn } from "../components/tickets/TicketListView.js";
import { TicketForm } from "../components/tickets/TicketForm.js";
import { TicketDetailView } from "../components/tickets/TicketDetailView.js";
import type { TicketListParams, TicketStatus, TicketPriority, CreateTicketPayload } from "../api/ticketTypes.js";
import { TICKET_STATUS_LABELS, TICKET_PRIORITY_LABELS } from "../api/ticketTypes.js";

const TICKET_STATUSES: TicketStatus[] = ["TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELED"];
const TICKET_PRIORITIES: TicketPriority[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
const PAGE_SIZE = 20;

function paramsToListParams(sp: URLSearchParams): TicketListParams & { sortBy: TicketSortColumn; sortDir: "asc" | "desc"; page: number } {
  const status = sp.get("status") as TicketStatus | null;
  const assignee = sp.get("assignee");
  const team = sp.get("team");
  const rcdo = sp.get("rcdo");
  const week = sp.get("week");
  const priority = sp.get("priority") as TicketPriority | null;
  return {
    ...(status ? { status } : {}), ...(assignee ? { assigneeUserId: assignee } : {}),
    ...(team ? { teamId: team } : {}), ...(rcdo ? { rcdoNodeId: rcdo } : {}),
    ...(week ? { targetWeek: week } : {}), ...(priority ? { priority } : {}),
    page: sp.get("page") ? Number(sp.get("page")) : 1, pageSize: PAGE_SIZE,
    sortBy: (sp.get("sortBy") as TicketSortColumn | null) ?? "updatedAt",
    sortDir: sp.get("sortDir") === "asc" ? "asc" : "desc",
  };
}

function buildRcdoLabels(nodes: ReturnType<typeof useRcdoTree>["data"]): Record<string, string> {
  const map: Record<string, string> = {};
  function traverse(ns: typeof nodes, path: string[] = []) {
    if (!ns) return;
    for (const n of ns) { const nextPath = [...path, n.title]; map[n.id] = nextPath.join(" > "); traverse(n.children, nextPath); }
  }
  traverse(nodes ?? []);
  return map;
}

const inputCls = "h-8 rounded-default border border-border bg-surface px-2.5 text-xs text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50";
const labelCls = "text-[0.65rem] font-bold uppercase tracking-wider text-muted mb-0.5 block";

function TicketFilters({ params, onParamsChange, onClearFilters }: {
  params: ReturnType<typeof paramsToListParams>;
  onParamsChange: (patch: Partial<ReturnType<typeof paramsToListParams>>) => void;
  onClearFilters: () => void;
}) {
  const hasFilter = params.status ?? params.assigneeUserId ?? params.teamId ?? params.rcdoNodeId ?? params.targetWeek ?? params.priority;
  return (
    <div data-testid="ticket-filters" className="rounded-default border border-border bg-surface px-4 py-3 flex gap-3 flex-wrap items-end">
      <div className="flex flex-col">
        <label htmlFor="tf-status-filter" className={labelCls}>Status</label>
        <select id="tf-status-filter" value={params.status ?? ""} onChange={(e) => { const v = e.target.value as TicketStatus | ""; onParamsChange({ ...(v ? { status: v } : {}), page: 1 }); }} data-testid="filter-status" className={inputCls}>
          <option value="">All statuses</option>
          {TICKET_STATUSES.map((s) => <option key={s} value={s}>{TICKET_STATUS_LABELS[s]}</option>)}
        </select>
      </div>
      <div className="flex flex-col">
        <label htmlFor="tf-priority-filter" className={labelCls}>Priority</label>
        <select id="tf-priority-filter" value={params.priority ?? ""} onChange={(e) => { const v = e.target.value as TicketPriority | ""; onParamsChange({ ...(v ? { priority: v } : {}), page: 1 }); }} data-testid="filter-priority" className={inputCls}>
          <option value="">All priorities</option>
          {TICKET_PRIORITIES.map((p) => <option key={p} value={p}>{TICKET_PRIORITY_LABELS[p]}</option>)}
        </select>
      </div>
      <div className="flex flex-col">
        <label htmlFor="tf-assignee-filter" className={labelCls}>Assignee</label>
        <input id="tf-assignee-filter" type="text" placeholder="User ID…" value={params.assigneeUserId ?? ""} onChange={(e) => { const v = e.target.value; onParamsChange({ ...(v ? { assigneeUserId: v } : {}), page: 1 }); }} data-testid="filter-assignee" className={`${inputCls} min-w-[110px]`} />
      </div>
      <div className="flex flex-col">
        <label htmlFor="tf-team-filter" className={labelCls}>Team</label>
        <input id="tf-team-filter" type="text" placeholder="Team ID…" value={params.teamId ?? ""} onChange={(e) => { const v = e.target.value; onParamsChange({ ...(v ? { teamId: v } : {}), page: 1 }); }} data-testid="filter-team" className={`${inputCls} min-w-[110px]`} />
      </div>
      <div className="flex flex-col">
        <label htmlFor="tf-rcdo-filter" className={labelCls}>RCDO</label>
        <input id="tf-rcdo-filter" type="text" placeholder="RCDO node ID…" value={params.rcdoNodeId ?? ""} onChange={(e) => { const v = e.target.value; onParamsChange({ ...(v ? { rcdoNodeId: v } : {}), page: 1 }); }} data-testid="filter-rcdo" className={`${inputCls} min-w-[110px]`} />
      </div>
      <div className="flex flex-col">
        <label htmlFor="tf-week-filter" className={labelCls}>Target Week</label>
        <input id="tf-week-filter" type="date" value={params.targetWeek ?? ""} onChange={(e) => { const v = e.target.value; onParamsChange({ ...(v ? { targetWeek: v } : {}), page: 1 }); }} data-testid="filter-week" className={inputCls} />
      </div>
      {hasFilter && (
        <Button variant="secondary" size="sm" onClick={onClearFilters} data-testid="filter-clear-all" className="self-end">
          <X className="h-3 w-3" />Clear all
        </Button>
      )}
    </div>
  );
}

export default function Tickets() {
  const bridge = useHostBridge();
  const currentUserId = bridge.context.authenticatedUser.id;
  const currentTeamId = bridge.context.currentTeam?.id;

  const [searchParams, setSearchParams] = useSearchParams();
  const listParams = useMemo(() => paramsToListParams(searchParams), [searchParams]);
  const { data: rcdoTreeData } = useRcdoTree();
  const rcdoLabels = useMemo(() => buildRcdoLabels(rcdoTreeData), [rcdoTreeData]);
  const { data: ticketPage, loading: ticketsLoading, refetch: refetchTickets } = useTicketList(listParams);
  const ticketApi = useTicketApi();

  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const { data: ticketDetail, loading: detailLoading, refetch: refetchDetail } = useTicket(selectedTicketId);
  type FormMode = "create" | "create-from-commit" | null;
  const [formMode, setFormMode] = useState<FormMode>(null);
  const [formInitialValues, setFormInitialValues] = useState<Partial<CreateTicketPayload>>({});

  const updateParams = useCallback((patch: Partial<ReturnType<typeof paramsToListParams>>) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      const merged = { ...paramsToListParams(prev), ...patch };
      const pairs: [string, string][] = [
        ...(merged.status ? [["status", merged.status] as [string, string]] : []),
        ...(merged.assigneeUserId ? [["assignee", merged.assigneeUserId] as [string, string]] : []),
        ...(merged.teamId ? [["team", merged.teamId] as [string, string]] : []),
        ...(merged.rcdoNodeId ? [["rcdo", merged.rcdoNodeId] as [string, string]] : []),
        ...(merged.targetWeek ? [["week", merged.targetWeek] as [string, string]] : []),
        ...(merged.priority ? [["priority", merged.priority] as [string, string]] : []),
        ...(merged.page && merged.page !== 1 ? [["page", String(merged.page)] as [string, string]] : []),
        ...(merged.sortBy && merged.sortBy !== "updatedAt" ? [["sortBy", merged.sortBy] as [string, string]] : []),
        ...(merged.sortDir && merged.sortDir !== "desc" ? [["sortDir", merged.sortDir] as [string, string]] : []),
      ];
      Array.from(next.keys()).forEach((k) => next.delete(k));
      pairs.forEach(([k, v]) => next.set(k, v));
      return next;
    });
  }, [setSearchParams]);

  const handleCreateTicket = useCallback(async (payload: CreateTicketPayload) => {
    await ticketApi.createTicket(payload);
    setFormMode(null); setFormInitialValues({});
    refetchTickets();
  }, [ticketApi, refetchTickets]);

  const handleStatusTransition = useCallback(async (ticketId: string, newStatus: TicketStatus) => {
    await ticketApi.transitionStatus(ticketId, newStatus, currentUserId);
    refetchDetail(); refetchTickets();
  }, [ticketApi, refetchDetail, refetchTickets, currentUserId]);

  const handleAssigneeChange = useCallback(async (ticketId: string, assigneeUserId: string) => {
    await ticketApi.updateTicket(ticketId, assigneeUserId ? { assigneeUserId } : {});
    refetchDetail(); refetchTickets();
  }, [ticketApi, refetchDetail, refetchTickets]);

  return (
    <div className="flex flex-col gap-4" data-testid="page-tickets">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="m-0 text-xl font-bold">Tickets</h2>
        <Button variant="primary" onClick={() => { setFormInitialValues({ reporterUserId: currentUserId, ...(currentTeamId ? { teamId: currentTeamId } : {}) }); setFormMode("create"); }} data-testid="create-ticket-btn">
          + Create Ticket
        </Button>
      </div>

      <TicketFilters params={listParams} onParamsChange={updateParams} onClearFilters={() => setSearchParams(new URLSearchParams())} />

      {ticketPage && (
        <div data-testid="ticket-count" className="text-sm text-muted">
          {ticketPage.total} ticket{ticketPage.total !== 1 ? "s" : ""}
        </div>
      )}

      <div className={`flex gap-4 items-start ${selectedTicketId ? "flex-nowrap" : "flex-wrap"}`}>
        <div style={{ flex: selectedTicketId ? "0 0 55%" : "1 1 100%", minWidth: 0 }}>
          <TicketListView
            tickets={ticketPage?.items ?? []}
            total={ticketPage?.total ?? 0}
            page={listParams.page ?? 1}
            pageSize={PAGE_SIZE}
            sortBy={listParams.sortBy}
            sortDir={listParams.sortDir}
            loading={ticketsLoading}
            onPageChange={(p) => updateParams({ page: p })}
            onSortChange={(col, dir) => updateParams({ sortBy: col, sortDir: dir })}
            onSelectTicket={(id) => setSelectedTicketId(id === selectedTicketId ? null : id)}
            rcdoLabels={rcdoLabels}
          />
        </div>

        {selectedTicketId && (
          <div data-testid="ticket-detail-panel" className="flex-1 min-w-[280px] sticky top-4">
            {detailLoading && <div data-testid="ticket-detail-loading" role="status" className="text-muted p-4">Loading…</div>}
            {!detailLoading && ticketDetail && (
              <TicketDetailView
                ticket={ticketDetail}
                onStatusTransition={handleStatusTransition}
                onAssigneeChange={handleAssigneeChange}
                {...(ticketDetail.rcdoNodeId && rcdoLabels[ticketDetail.rcdoNodeId] ? { rcdoPath: rcdoLabels[ticketDetail.rcdoNodeId] } : {})}
                onClose={() => setSelectedTicketId(null)}
                onEdit={() => {
                  setFormInitialValues({
                    title: ticketDetail.title,
                    ...(ticketDetail.description ? { description: ticketDetail.description } : {}),
                    status: ticketDetail.status, priority: ticketDetail.priority,
                    ...(ticketDetail.assigneeUserId ? { assigneeUserId: ticketDetail.assigneeUserId } : {}),
                    reporterUserId: ticketDetail.reporterUserId, teamId: ticketDetail.teamId,
                    ...(ticketDetail.rcdoNodeId ? { rcdoNodeId: ticketDetail.rcdoNodeId } : {}),
                    ...(ticketDetail.estimatePoints != null ? { estimatePoints: ticketDetail.estimatePoints } : {}),
                    ...(ticketDetail.targetWeekStartDate ? { targetWeekStartDate: ticketDetail.targetWeekStartDate } : {}),
                  });
                  setFormMode("create");
                }}
              />
            )}
          </div>
        )}
      </div>

      {formMode !== null && (
        <TicketForm
          mode="create"
          initialValues={formInitialValues}
          currentUserId={currentUserId}
          {...(currentTeamId ? { currentTeamId } : {})}
          onSubmit={handleCreateTicket}
          onCancel={() => { setFormMode(null); setFormInitialValues({}); }}
        />
      )}
    </div>
  );
}
