/**
 * Reports — operational analytics from derived read-model tables.
 * Route: /weekly/reports
 *
 * Charts are rendered as pure CSS bar/line charts (no charting library dependency).
 * Data comes from 8 pre-computed report endpoints. Several derived metrics are
 * calculated client-side from existing data (velocity, achievement rate, etc.).
 */
import { useState, useEffect, useMemo, useCallback } from "react";
import {
  ComposedChart,
  BarChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
  Cell,
} from "recharts";
import { ReportChartSkeleton } from "../components/shared/skeletons/ReportChartSkeleton.js";
import { EmptyState } from "../components/shared/EmptyState.js";
import {
  BarChart3,
  BarChart2,
  TrendingUp,
  TrendingDown,
  RotateCcw,
  Shield,
  Brain,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Activity,
  Target,
  Zap,
  Clock,
  ShieldAlert,
} from "lucide-react";
import { Card, CardHeader, CardTitle, CardContent } from "../components/ui/Card.js";
import { Badge } from "../components/ui/Badge.js";
import { Button } from "../components/ui/Button.js";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "../api/client.js";
import {
  createReportApi,
  type PlannedVsAchievedEntry,
  type CarryForwardReportEntry,
  type ComplianceReportEntry,
  type AiAcceptanceReportEntry,
  type ChessDistributionReportEntry,
  type ScopeChangeReportEntry,
  type ExceptionAgingEntry,
} from "../api/reportApi.js";

// ── Constants ───────────────────────────────────────────────────────────────

const CHESS_ICONS: Record<string, string> = {
  KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙",
};

const CHESS_ORDER = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"] as const;

/** Monochrome shades for chess pieces — resolved from CSS vars for dark mode */
const CHESS_SHADES: Record<string, string> = {
  KING: "var(--chess-king)", QUEEN: "var(--chess-queen)", ROOK: "var(--chess-rook)",
  BISHOP: "var(--chess-bishop)", KNIGHT: "var(--chess-knight)", PAWN: "var(--chess-pawn)",
};

// ── Helpers ─────────────────────────────────────────────────────────────────

function weeksAgo(n: number): string {
  const d = new Date();
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff - n * 7);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

function shortWeek(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function pct(n: number, d: number): number {
  return d === 0 ? 0 : Math.round((n / d) * 100);
}

function formatHours(h: number): string {
  if (h < 24) return `${h}h`;
  const days = Math.floor(h / 24);
  const rem = h % 24;
  return rem > 0 ? `${days}d ${rem}h` : `${days}d`;
}

/** Generate list of week-start ISO dates for the range */
function weeksList(rangeWeeks: number): string[] {
  const out: string[] = [];
  for (let i = rangeWeeks - 1; i >= 0; i--) {
    out.push(weeksAgo(i));
  }
  return out;
}

// ── Main Component ──────────────────────────────────────────────────────────

export default function Reports() {
  const bridge = useHostBridge();
  const { authToken } = bridge.context;
  const teamId = bridge.context.currentTeam?.id ?? "";

  const api = useMemo(() => {
    const client = createApiClient({
      baseUrl: API_BASE_URL,
      getAuthToken: () => bridge.context.authToken,
    });
    return createReportApi(client);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);

  const [rangeWeeks, setRangeWeeks] = useState(8);
  const weekStart = useMemo(() => weeksAgo(rangeWeeks - 1), [rangeWeeks]);
  const weekEnd = useMemo(() => weeksAgo(0), []);
  const weeks = useMemo(() => weeksList(rangeWeeks), [rangeWeeks]);

  // ── Data state ──────────────────────────────────────────────────────────
  const [pva, setPva] = useState<PlannedVsAchievedEntry[]>([]);
  const [cf, setCf] = useState<CarryForwardReportEntry[]>([]);
  const [compliance, setCompliance] = useState<ComplianceReportEntry[]>([]);
  const [aiStats, setAiStats] = useState<AiAcceptanceReportEntry | null>(null);
  const [scopeChanges, setScopeChanges] = useState<ScopeChangeReportEntry[]>([]);
  const [chessDist, setChessDist] = useState<ChessDistributionReportEntry[]>([]);
  const [exceptions, setExceptions] = useState<ExceptionAgingEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Fetch chess distribution for every week in range (API takes single weekStart)
  const fetchAllChess = useCallback(
    async (wks: string[]): Promise<ChessDistributionReportEntry[]> => {
      const results = await Promise.all(
        wks.map((w) =>
          api.getChessDistribution(teamId, w).catch(() => null),
        ),
      );
      return results.filter((r): r is ChessDistributionReportEntry => r !== null);
    },
    [api, teamId],
  );

  useEffect(() => {
    if (!teamId) return;
    setLoading(true);
    setError(null);

    Promise.all([
      api.getPlannedVsAchieved(teamId, weekStart, weekEnd),
      api.getCarryForward(teamId, weekStart, weekEnd),
      api.getCompliance(teamId, weekStart, weekEnd),
      api.getAiAcceptance(),
      api.getScopeChanges(teamId, weekStart, weekEnd),
      fetchAllChess(weeks),
      api.getExceptionAging(teamId),
    ])
      .then(([pvaData, cfData, compData, aiData, scData, chessData, excData]) => {
        setPva(pvaData);
        setCf(cfData);
        setCompliance(compData);
        setAiStats(aiData);
        setScopeChanges(scData);
        setChessDist(chessData);
        setExceptions(excData);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Failed to load reports");
        setLoading(false);
      });
  }, [api, teamId, weekStart, weekEnd, weeks, fetchAllChess]);

  return (
    <div className="space-y-6" data-testid="reports-page">
      {/* ── Header ──────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <BarChart3 className="h-6 w-6 text-primary" />
          <h1 className="text-xl font-bold tracking-tight">Reports & Analytics</h1>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setRangeWeeks((w) => Math.min(52, w + 4))}
          >
            <ChevronLeft className="h-3 w-3 mr-1" /> More weeks
          </Button>
          <Badge variant="draft">{rangeWeeks} weeks</Badge>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setRangeWeeks((w) => Math.max(4, w - 4))}
          >
            Fewer <ChevronRight className="h-3 w-3 ml-1" />
          </Button>
        </div>
      </div>

      {loading && (
        <>
          <span role="status" aria-label="Loading report data" className="sr-only">Loading report data…</span>
          <ReportChartSkeleton chartHeight={200} />
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <ReportChartSkeleton chartHeight={160} />
            <ReportChartSkeleton chartHeight={160} />
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <ReportChartSkeleton chartHeight={140} />
            <ReportChartSkeleton chartHeight={140} />
          </div>
        </>
      )}
      {error && (
        <div className="flex items-center gap-2 text-sm text-danger" role="alert">
          <AlertTriangle className="h-4 w-4" /> {error}
        </div>
      )}

      {!loading && !error && pva.length === 0 && compliance.length === 0 && cf.length === 0 && (
        <EmptyState
          data-testid="reports-empty"
          icon={<BarChart2 className="h-10 w-10" />}
          title="Not enough data yet"
          description="Reports will populate after your first reconciled week. Lock and reconcile your weekly plan to start tracking progress."
        />
      )}

      {!loading && !error && (pva.length > 0 || compliance.length > 0 || cf.length > 0) && (
        <>
          {/* ── Row 1: Hero velocity ──────────────────────────────── */}
          <VelocityTrendChart data={pva} />

          {/* ── Row 2: Core planning metrics ──────────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <PlannedVsAchievedChart data={pva} />
            <AchievementRateChart data={pva} />
          </div>

          {/* ── Row 3: Work composition ───────────────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <ChessDistributionChart data={chessDist} />
            <ScopeChangeChart data={scopeChanges} />
          </div>

          {/* ── Row 4: Carry-forward & compliance ─────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <CarryForwardChart data={cf} />
            <ComplianceChart data={compliance} />
          </div>

          {/* ── Row 5: Exceptions ─────────────────────────────────── */}
          <ExceptionAgingTable data={exceptions} />

          {/* ── Row 6: AI ─────────────────────────────────────────── */}
          <AiAcceptanceCard data={aiStats} />
        </>
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Velocity Trend (hero chart — full width) ────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function VelocityTrendChart({ data }: { readonly data: PlannedVsAchievedEntry[] }) {
  const sorted = useMemo(
    () => [...data].sort((a, b) => a.weekStart.localeCompare(b.weekStart)),
    [data],
  );

  // Build Recharts-friendly data rows (add rolling average per row)
  const chartData = useMemo(() => {
    return sorted.map((row, i) => {
      const window = sorted.slice(Math.max(0, i - 3), i + 1);
      const avg = Math.round(
        window.reduce((s, r) => s + r.totalAchievedPoints, 0) / window.length,
      );
      return {
        week: shortWeek(row.weekStart),
        Achieved: row.totalAchievedPoints,
        "4-wk avg": avg,
      };
    });
  }, [sorted]);

  // Trend direction (last week vs prior)
  const trend = useMemo(() => {
    if (sorted.length < 2) return 0;
    const last = sorted[sorted.length - 1]!.totalAchievedPoints;
    const prev = sorted[sorted.length - 2]!.totalAchievedPoints;
    return last - prev;
  }, [sorted]);

  return (
    <Card data-testid="velocity-chart">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Activity className="h-4 w-4 text-foreground" />
          Velocity — Achieved Points per Week
        </CardTitle>
        {trend !== 0 && (
          <div className="flex items-center gap-1 text-xs">
            {trend > 0 ? (
              <TrendingUp className="h-3.5 w-3.5 text-success" />
            ) : (
              <TrendingDown className="h-3.5 w-3.5 text-danger" />
            )}
            <span className={`font-mono ${trend > 0 ? "text-success" : "text-danger"}`}>
              {trend > 0 ? "+" : ""}{trend} pts
            </span>
          </div>
        )}
      </CardHeader>
      <CardContent>
        {chartData.length === 0 ? (
          <p className="text-sm text-muted">No data for this period.</p>
        ) : (
          <div role="img" aria-label="Weekly achieved points with 4-week rolling average trend">
            <ResponsiveContainer width="100%" height={200}>
              <ComposedChart
                data={chartData}
                margin={{ top: 8, right: 8, left: -16, bottom: 0 }}
              >
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke="var(--color-border)"
                  vertical={false}
                />
                <XAxis
                  dataKey="week"
                  tick={{ fontSize: 9, fontFamily: "var(--font-family-mono)", fill: "var(--color-muted)" }}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  tick={{ fontSize: 9, fontFamily: "var(--font-family-mono)", fill: "var(--color-muted)" }}
                  tickLine={false}
                  axisLine={false}
                  width={28}
                />
                <Tooltip
                  formatter={(value) => [`${value} pts`]}
                  contentStyle={{
                    fontSize: 11,
                    fontFamily: "var(--font-family-mono)",
                    background: "var(--color-surface)",
                    border: "1px solid var(--color-border)",
                    borderRadius: 2,
                    padding: "6px 10px",
                  }}
                  labelStyle={{ fontWeight: 600, marginBottom: 4 }}
                  itemStyle={{ color: "var(--color-foreground)" }}
                />
                <Legend
                  iconSize={8}
                  wrapperStyle={{ fontSize: 10, color: "var(--color-muted)" }}
                />
                {/* Achieved points bars */}
                <Bar
                  dataKey="Achieved"
                  fill="var(--color-foreground)"
                  fillOpacity={0.2}
                  radius={[2, 2, 0, 0]}
                  maxBarSize={40}
                />
                {/* Rolling 4-week average line */}
                <Line
                  dataKey="4-wk avg"
                  type="monotone"
                  stroke="var(--color-foreground)"
                  strokeWidth={1.5}
                  dot={{ r: 3, fill: "var(--color-foreground)", stroke: "var(--color-surface)", strokeWidth: 1 }}
                  activeDot={{ r: 4 }}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ─── Shared tooltip style (reused by all migrated charts) ────────────────────
const TOOLTIP_STYLE = {
  contentStyle: {
    fontSize: 11,
    fontFamily: "var(--font-family-mono)",
    background: "var(--color-surface)",
    border: "1px solid var(--color-border)",
    borderRadius: 2,
    padding: "6px 10px",
  },
  labelStyle: { fontWeight: 600, marginBottom: 2 },
  itemStyle: { color: "var(--color-foreground)" },
  formatter: (v: unknown) => [`${v} pts`],
} as const;

const AXIS_TICK = {
  fontSize: 9,
  fontFamily: "var(--font-family-mono)",
  fill: "var(--color-muted)",
} as const;

// ═══════════════════════════════════════════════════════════════════════════
// ── Planned vs Achieved ─────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function PlannedVsAchievedChart({ data }: { readonly data: PlannedVsAchievedEntry[] }) {
  const chartData = useMemo(
    () =>
      [...data]
        .sort((a, b) => a.weekStart.localeCompare(b.weekStart))
        .map((row) => ({
          week: shortWeek(row.weekStart),
          Planned: row.totalPlannedPoints,
          Achieved: row.totalAchievedPoints,
        })),
    [data],
  );

  return (
    <Card data-testid="pva-chart">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <TrendingUp className="h-4 w-4 text-primary" />
          Planned vs. Achieved Points
        </CardTitle>
      </CardHeader>
      <CardContent>
        {chartData.length === 0 ? (
          <p className="text-sm text-muted">No data for this period.</p>
        ) : (
          <div role="img" aria-label="Grouped bar chart comparing planned and achieved points by week">
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={chartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="week" tick={AXIS_TICK} tickLine={false} axisLine={false} />
                <YAxis tick={AXIS_TICK} tickLine={false} axisLine={false} width={28} />
                <Tooltip {...TOOLTIP_STYLE} />
                <Legend iconSize={8} wrapperStyle={{ fontSize: 10, color: "var(--color-muted)" }} />
                <Bar dataKey="Planned" fill="var(--color-primary)" fillOpacity={0.3} radius={[2, 2, 0, 0]} maxBarSize={20} />
                <Bar dataKey="Achieved" fill="var(--color-primary)" radius={[2, 2, 0, 0]} maxBarSize={20} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Achievement Rate % ──────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function AchievementRateChart({ data }: { readonly data: PlannedVsAchievedEntry[] }) {
  const TARGET = 80;

  const { chartData, avgRate } = useMemo(() => {
    const sorted = [...data].sort((a, b) => a.weekStart.localeCompare(b.weekStart));
    const rows = sorted.map((r) => ({
      week: shortWeek(r.weekStart),
      Rate: pct(r.totalAchievedPoints, r.totalPlannedPoints),
    }));
    const avg = rows.length > 0 ? Math.round(rows.reduce((s, r) => s + r.Rate, 0) / rows.length) : 0;
    return { chartData: rows, avgRate: avg };
  }, [data]);

  return (
    <Card data-testid="achievement-rate-chart">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Target className="h-4 w-4 text-foreground" />
          Achievement Rate
        </CardTitle>
        <Badge variant={avgRate >= TARGET ? "reconciled" : "locked"}>
          avg {avgRate}%
        </Badge>
      </CardHeader>
      <CardContent>
        {chartData.length === 0 ? (
          <p className="text-sm text-muted">No data for this period.</p>
        ) : (
          <div role="img" aria-label="Weekly achievement rate bar chart with an 80 percent target line">
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={chartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="week" tick={AXIS_TICK} tickLine={false} axisLine={false} />
                <YAxis domain={[0, 100]} tick={AXIS_TICK} tickLine={false} axisLine={false} width={28} tickFormatter={(v) => `${v}%`} />
                <Tooltip
                  formatter={(v) => [`${v}%`]}
                  contentStyle={TOOLTIP_STYLE.contentStyle}
                  labelStyle={TOOLTIP_STYLE.labelStyle}
                  itemStyle={TOOLTIP_STYLE.itemStyle}
                />
                <ReferenceLine
                  y={TARGET}
                  stroke="var(--color-muted)"
                  strokeDasharray="4 4"
                  label={{ value: `${TARGET}% target`, position: "insideTopRight", fontSize: 9, fontFamily: "var(--font-family-mono)", fill: "var(--color-muted)" }}
                />
                <Bar dataKey="Rate" radius={[2, 2, 0, 0]} maxBarSize={28}>
                  {chartData.map((entry, i) => (
                    <Cell
                      key={i}
                      fill="var(--color-foreground)"
                      fillOpacity={entry.Rate >= TARGET ? 0.2 : 0.1}
                    />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Chess Distribution ──────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function ChessDistributionChart({
  data,
}: {
  readonly data: ChessDistributionReportEntry[];
}) {
  // Aggregate all weeks into one distribution
  const aggregate = useMemo(() => {
    const acc: Record<string, number> = {};
    for (const entry of data) {
      for (const [piece, count] of Object.entries(entry.distribution)) {
        acc[piece] = (acc[piece] ?? 0) + count;
      }
    }
    return acc;
  }, [data]);

  const total = Object.values(aggregate).reduce((s, v) => s + v, 0);

  return (
    <Card data-testid="chess-dist-chart">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <span className="text-base leading-none">♔</span>
          Chess Piece Distribution
        </CardTitle>
        {total > 0 && (
          <span className="text-[10px] text-muted">{total} commits</span>
        )}
      </CardHeader>
      <CardContent>
        {total === 0 ? (
          <p className="text-sm text-muted">No data for this period.</p>
        ) : (
          <div className="space-y-3">
            {/* Stacked bar */}
            <div className="flex h-6 overflow-hidden gap-px">
              {CHESS_ORDER.map((piece) => {
                const count = aggregate[piece] ?? 0;
                if (count === 0) return null;
                const w = (count / total) * 100;
                return (
                  <div
                    key={piece}
                    className="h-full transition-all relative group"
                    style={{
                      width: `${w}%`,
                      backgroundColor: CHESS_SHADES[piece],
                      minWidth: 3,
                    }}
                    title={`${piece}: ${count} (${Math.round(w)}%)`}
                  />
                );
              })}
            </div>

            {/* Breakdown rows */}
            <div className="space-y-1">
              {CHESS_ORDER.map((piece) => {
                const count = aggregate[piece] ?? 0;
                const p = pct(count, total);
                return (
                  <div key={piece} className="flex items-center gap-2 text-xs">
                    <span
                      className="inline-block h-2.5 w-2.5 shrink-0"
                      style={{ backgroundColor: CHESS_SHADES[piece] }}
                    />
                    <span className="w-5 text-center text-sm leading-none">{CHESS_ICONS[piece]}</span>
                    <span className="w-14 font-medium">{piece}</span>
                    <div className="flex-1 bg-surface-raised h-2.5 overflow-hidden">
                      <div
                        className="h-full transition-all"
                        style={{
                          width: `${p}%`,
                          backgroundColor: CHESS_SHADES[piece],
                        }}
                      />
                    </div>
                    <span className="w-8 text-right text-muted">{count}</span>
                    <span className="w-10 text-right font-bold">{p}%</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Scope Change Volume ─────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function ScopeChangeChart({ data }: { readonly data: ScopeChangeReportEntry[] }) {
  const { chartData, avgChanges } = useMemo(() => {
    const map = new Map<string, number>();
    for (const row of data) {
      map.set(row.weekStart, (map.get(row.weekStart) ?? 0) + row.scopeChangeCount);
    }
    const rows = [...map.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([week, count]) => ({ week: shortWeek(week), Changes: count }));
    const avg = rows.length > 0 ? rows.reduce((s, r) => s + r.Changes, 0) / rows.length : 0;
    return { chartData: rows, avgChanges: Math.round(avg) };
  }, [data]);

  return (
    <Card data-testid="scope-change-chart">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Zap className="h-4 w-4 text-foreground" />
          Scope Changes
        </CardTitle>
        {chartData.length > 0 && (
          <span className="text-[10px] font-mono text-muted">avg {avgChanges}/wk</span>
        )}
      </CardHeader>
      <CardContent>
        {chartData.length === 0 ? (
          <p className="text-sm text-muted">No data for this period.</p>
        ) : (
          <div role="img" aria-label="Weekly scope change counts with a high threshold reference line">
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={chartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="week" tick={AXIS_TICK} tickLine={false} axisLine={false} />
                <YAxis tick={AXIS_TICK} tickLine={false} axisLine={false} width={28} allowDecimals={false} />
                <Tooltip
                  formatter={(v) => [`${v} changes`]}
                  contentStyle={TOOLTIP_STYLE.contentStyle}
                  labelStyle={TOOLTIP_STYLE.labelStyle}
                  itemStyle={TOOLTIP_STYLE.itemStyle}
                />
                {avgChanges > 0 && (
                  <ReferenceLine
                    y={avgChanges * 1.5}
                    stroke="var(--color-warning)"
                    strokeDasharray="4 4"
                    label={{ value: "HIGH threshold", position: "insideTopRight", fontSize: 8, fontFamily: "var(--font-family-mono)", fill: "var(--color-warning)" }}
                  />
                )}
                <Bar dataKey="Changes" radius={[2, 2, 0, 0]} maxBarSize={32}>
                  {chartData.map((entry, i) => (
                    <Cell
                      key={i}
                      fill="var(--color-foreground)"
                      fillOpacity={entry.Changes > avgChanges * 1.5 ? 0.3 : 0.15}
                    />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Carry-Forward Trends ────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function CarryForwardChart({ data }: { readonly data: CarryForwardReportEntry[] }) {
  const chartData = useMemo(() => {
    const map = new Map<string, { commits: number; carried: number }>();
    for (const row of data) {
      const existing = map.get(row.weekStart) ?? { commits: 0, carried: 0 };
      existing.commits += row.commitCount;
      existing.carried += row.carryForwardCount;
      map.set(row.weekStart, existing);
    }
    return [...map.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([week, vals]) => ({
        week: shortWeek(week),
        "CF Rate %": pct(vals.carried, vals.commits),
      }));
  }, [data]);

  return (
    <Card data-testid="cf-chart">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <RotateCcw className="h-4 w-4 text-foreground" />
          Carry-Forward Trends
        </CardTitle>
      </CardHeader>
      <CardContent>
        {chartData.length === 0 ? (
          <p className="text-sm text-muted">No data for this period.</p>
        ) : (
          <div role="img" aria-label="Weekly carry-forward rate percentages by week">
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={chartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="week" tick={AXIS_TICK} tickLine={false} axisLine={false} />
                <YAxis domain={[0, 100]} tick={AXIS_TICK} tickLine={false} axisLine={false} width={28} tickFormatter={(v) => `${v}%`} />
                <Tooltip
                  formatter={(v) => [`${v}%`]}
                  contentStyle={TOOLTIP_STYLE.contentStyle}
                  labelStyle={TOOLTIP_STYLE.labelStyle}
                  itemStyle={TOOLTIP_STYLE.itemStyle}
                />
                <Bar dataKey="CF Rate %" fill="var(--color-foreground)" fillOpacity={0.25} radius={[2, 2, 0, 0]} maxBarSize={32} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Compliance Chart ────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function ComplianceChart({ data }: { readonly data: ComplianceReportEntry[] }) {
  const chartData = useMemo(() => {
    const map = new Map<string, { total: number; lockedOnTime: number; reconciledOnTime: number; autoLocked: number }>();
    for (const row of data) {
      const existing = map.get(row.weekStart) ?? { total: 0, lockedOnTime: 0, reconciledOnTime: 0, autoLocked: 0 };
      existing.total++;
      if (row.lockOnTime) existing.lockedOnTime++;
      if (row.reconcileOnTime) existing.reconciledOnTime++;
      if (row.autoLocked) existing.autoLocked++;
      map.set(row.weekStart, existing);
    }
    return [...map.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([week, v]) => ({
        week: shortWeek(week),
        "Lock on time": pct(v.lockedOnTime, v.total),
        "Reconcile on time": pct(v.reconciledOnTime, v.total),
        "Auto-locked": pct(v.autoLocked, v.total),
      }));
  }, [data]);

  return (
    <Card data-testid="compliance-chart">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Shield className="h-4 w-4 text-success" />
          Lock &amp; Reconcile Compliance
        </CardTitle>
      </CardHeader>
      <CardContent>
        {chartData.length === 0 ? (
          <p className="text-sm text-muted">No data for this period.</p>
        ) : (
          <div role="img" aria-label="Weekly lock and reconcile compliance percentages including auto-locked plans">
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={chartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis dataKey="week" tick={AXIS_TICK} tickLine={false} axisLine={false} />
                <YAxis domain={[0, 100]} tick={AXIS_TICK} tickLine={false} axisLine={false} width={28} tickFormatter={(v) => `${v}%`} />
                <Tooltip
                  formatter={(v) => [`${v}%`]}
                  contentStyle={TOOLTIP_STYLE.contentStyle}
                  labelStyle={TOOLTIP_STYLE.labelStyle}
                  itemStyle={TOOLTIP_STYLE.itemStyle}
                />
                <Legend iconSize={8} wrapperStyle={{ fontSize: 10, color: "var(--color-muted)" }} />
                <Bar dataKey="Lock on time" fill="var(--color-foreground)" fillOpacity={0.7} radius={[2, 2, 0, 0]} maxBarSize={18} />
                <Bar dataKey="Reconcile on time" fill="var(--color-foreground)" fillOpacity={0.3} radius={[2, 2, 0, 0]} maxBarSize={18} />
                <Bar dataKey="Auto-locked" fill="var(--color-warning)" fillOpacity={0.4} radius={[2, 2, 0, 0]} maxBarSize={18} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Exception Aging Table ───────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

const SEVERITY_STYLES: Record<string, string> = {
  CRITICAL: "border-danger-border bg-danger-bg text-danger",
  HIGH: "border-warning-border bg-warning-bg text-warning",
  MEDIUM: "border-border bg-muted-bg text-muted",
  LOW: "border-border bg-surface text-muted",
};

const EXCEPTION_LABELS: Record<string, string> = {
  MISSED_LOCK: "Missed lock",
  AUTO_LOCKED: "Auto-locked",
  MISSED_RECONCILE: "Missed reconcile",
  OVER_BUDGET: "Over budget",
  REPEATED_CARRY_FORWARD: "Repeat carry-fwd",
  POST_LOCK_SCOPE_INCREASE: "Scope increase",
  KING_CHANGED_POST_LOCK: "King changed",
  HIGH_SCOPE_VOLATILITY: "High volatility",
};

function ExceptionAgingTable({ data }: { readonly data: ExceptionAgingEntry[] }) {
  const sorted = useMemo(
    () => [...data].sort((a, b) => b.ageInHours - a.ageInHours),
    [data],
  );

  const criticalCount = sorted.filter((e) => e.severity === "CRITICAL" || e.severity === "HIGH").length;

  return (
    <Card data-testid="exception-aging-table">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <ShieldAlert className="h-4 w-4 text-foreground" />
          Exception Aging — Unresolved
        </CardTitle>
        <div className="flex items-center gap-2">
          {criticalCount > 0 && (
            <Badge variant="locked">{criticalCount} urgent</Badge>
          )}
          <Badge variant="draft">{sorted.length} open</Badge>
        </div>
      </CardHeader>
      <CardContent>
        {sorted.length === 0 ? (
          <div className="flex items-center gap-2 py-4 text-sm text-muted">
            <Shield className="h-4 w-4" />
            No unresolved exceptions. All clear.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-xs" aria-label="Unresolved exceptions">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-2 py-1.5 text-left text-[10px] font-semibold uppercase text-muted">Severity</th>
                  <th className="px-2 py-1.5 text-left text-[10px] font-semibold uppercase text-muted">Type</th>
                  <th className="px-2 py-1.5 text-left text-[10px] font-semibold uppercase text-muted">Week</th>
                  <th className="px-2 py-1.5 text-right text-[10px] font-semibold uppercase text-muted">Age</th>
                  <th className="px-2 py-1.5 text-right text-[10px] font-semibold uppercase text-muted">Status</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((exc) => (
                  <tr key={exc.exceptionId} className="border-b border-border/50 hover:bg-muted-bg/50">
                    <td className="px-2 py-1.5">
                      <span
                        className={`inline-flex items-center px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider border ${
                          SEVERITY_STYLES[exc.severity] ?? SEVERITY_STYLES.LOW
                        }`}
                      >
                        {exc.severity}
                      </span>
                    </td>
                    <td className="px-2 py-1.5 font-medium">
                      {EXCEPTION_LABELS[exc.exceptionType] ?? exc.exceptionType}
                    </td>
                    <td className="px-2 py-1.5 font-mono text-muted">{shortWeek(exc.weekStartDate)}</td>
                    <td className="px-2 py-1.5 text-right font-bold">
                      <span className="flex items-center justify-end gap-1 font-mono">
                        <Clock className="h-3 w-3 text-muted" />
                        {formatHours(exc.ageInHours)}
                      </span>
                    </td>
                    <td className="px-2 py-1.5 text-right">
                      {exc.ageInHours >= 72 ? (
                        <span className="text-[9px] font-bold uppercase tracking-wider text-danger">STALE</span>
                      ) : exc.ageInHours >= 24 ? (
                        <span className="text-[9px] font-bold uppercase tracking-wider text-warning">AGING</span>
                      ) : (
                        <span className="text-[9px] font-bold uppercase tracking-wider text-muted">NEW</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── AI Acceptance ───────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function AiAcceptanceCard({ data }: { readonly data: AiAcceptanceReportEntry | null }) {
  if (!data) return null;
  const rate = Math.round(data.acceptanceRate * 100);
  const feedbackRate = data.totalSuggestions > 0
    ? Math.round((data.totalFeedbackGiven / data.totalSuggestions) * 100)
    : 0;
  const meetsTarget = rate >= 25;

  return (
    <Card data-testid="ai-acceptance-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Brain className="h-4 w-4 text-primary" />
          AI Suggestion Acceptance
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
          <StatBox label="Total Suggestions" value={data.totalSuggestions.toLocaleString()} />
          <StatBox label="Feedback Given" value={data.totalFeedbackGiven.toLocaleString()} />
          <StatBox label="Accepted" value={data.acceptedCount.toLocaleString()} accent="text-success" />
          <StatBox label="Dismissed" value={data.dismissedCount.toLocaleString()} />
          <StatBox label="Feedback Rate" value={`${feedbackRate}%`} accent={feedbackRate >= 50 ? "text-success" : "text-muted"} />
        </div>
        <div className="mt-4 space-y-2">
          {/* Acceptance rate */}
          <div className="flex items-center gap-3">
            <span className="text-[10px] text-muted w-20 shrink-0 uppercase tracking-wide">Acceptance</span>
            <div className="flex-1 bg-surface-raised h-4 overflow-hidden">
              <div
                className="h-full bg-foreground/20 transition-all"
                style={{ width: `${rate}%` }}
              />
            </div>
            <span className="text-sm font-bold w-10 text-right">{rate}%</span>
            <Badge variant={meetsTarget ? "reconciled" : "locked"}>
              {meetsTarget ? "Above 25%" : "Below 25%"}
            </Badge>
          </div>
          {/* Feedback engagement rate */}
          <div className="flex items-center gap-3">
            <span className="text-[10px] text-muted w-20 shrink-0 uppercase tracking-wide">Engagement</span>
            <div className="flex-1 bg-surface-raised h-4 overflow-hidden">
              <div
                className="h-full bg-foreground/10 transition-all"
                style={{ width: `${feedbackRate}%` }}
              />
            </div>
            <span className="text-sm font-bold w-10 text-right">{feedbackRate}%</span>
            <span className="text-[10px] text-muted">of suggestions reviewed</span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Shared components ───────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

function StatBox({
  label,
  value,
  accent,
}: {
  readonly label: string;
  readonly value: string;
  readonly accent?: string;
}) {
  return (
    <div className="rounded-default border border-border bg-surface-raised p-3 text-center">
      <p className="text-[10px] font-medium text-muted uppercase tracking-wide">{label}</p>
      <p className={`text-lg font-mono font-bold mt-0.5 ${accent ?? ""}`}>{value}</p>
    </div>
  );
}
