/**
 * Admin — org cadence config, team overrides, and capacity defaults.
 * Route: /weekly/admin
 */
import { useState, useCallback, useEffect, useMemo } from "react";
import {
  Settings,
  Clock,
  Users,
  Save,
  CheckCircle,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import { Button } from "../components/ui/Button.js";
import { Input } from "../components/ui/Input.js";
import { Select } from "../components/ui/Select.js";
import { Card, CardHeader, CardTitle, CardContent } from "../components/ui/Card.js";
import { Badge } from "../components/ui/Badge.js";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "../api/client.js";
import {
  createConfigApi,
  type OrgConfigResponse,
  type OrgConfigRequest,
  type EffectiveConfigResponse,
  type TeamConfigOverrideRequest,
} from "../api/configApi.js";


// ── Helpers ─────────────────────────────────────────────────────────────────

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;
const TIMEZONES = [
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Los_Angeles",
  "America/Phoenix",
  "UTC",
  "Europe/London",
  "Europe/Berlin",
  "Asia/Tokyo",
  "Asia/Kolkata",
  "Australia/Sydney",
] as const;

function displayDay(day: string): string {
  return day.charAt(0) + day.slice(1).toLowerCase();
}

// ── Offset ↔ Day+Time helpers ───────────────────────────────────────────────

const DAY_INDEX: Record<string, number> = {
  MONDAY: 0, TUESDAY: 1, WEDNESDAY: 2, THURSDAY: 3,
  FRIDAY: 4, SATURDAY: 5, SUNDAY: 6,
};
const DAY_FROM_INDEX = DAYS; // DAYS is already in 0-6 order

/** Convert offset-hours-from-week-start → { dayOfWeek, time "HH:MM" }. */
function offsetToDayTime(offsetHours: number, weekStartDay: string): { day: string; time: string } {
  const startIdx = DAY_INDEX[weekStartDay] ?? 0;
  // offsetHours can be negative (e.g. draft opens before week start)
  let totalHours = offsetHours;
  // normalise into 0..167 range (one week)
  totalHours = ((totalHours % 168) + 168) % 168;
  const dayOffset = Math.floor(totalHours / 24);
  const hour = Math.floor(totalHours % 24);
  const minute = Math.round((totalHours % 1) * 60);
  const dayIdx = (startIdx + dayOffset) % 7;
  return {
    day: DAY_FROM_INDEX[dayIdx] ?? "MONDAY",
    time: `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`,
  };
}

/** Convert { dayOfWeek, time "HH:MM" } → offset-hours-from-week-start. */
function dayTimeToOffset(day: string, time: string, weekStartDay: string): number {
  const startIdx = DAY_INDEX[weekStartDay] ?? 0;
  const targetIdx = DAY_INDEX[day] ?? 0;
  let dayOffset = targetIdx - startIdx;
  if (dayOffset < 0) dayOffset += 7;
  const [h, m] = time.split(":").map(Number);
  return dayOffset * 24 + (h || 0) + (m || 0) / 60;
}

// ── Main Component ──────────────────────────────────────────────────────────

export default function Admin() {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;
  const teamId = bridge.context.currentTeam?.id ?? "";
  const api = useMemo(() => {
    const client = createApiClient({
      baseUrl: API_BASE_URL,
      getAuthToken: () => bridge.context.authToken,
    });
    return createConfigApi(client, authenticatedUser.id);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken, authenticatedUser.id]);

  return (
    <div className="space-y-6" data-testid="admin-page">
      <div className="flex items-center gap-3">
        <Settings className="h-6 w-6 text-primary" />
        <h1 className="text-xl font-bold tracking-tight">Administration</h1>
      </div>

      <OrgConfigSection api={api} />
      <TeamConfigSection api={api} teamId={teamId} />
    </div>
  );
}

// ── Org Config Section ──────────────────────────────────────────────────────

function OrgConfigSection({ api }: { readonly api: ReturnType<typeof createConfigApi> }) {
  const [config, setConfig] = useState<OrgConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Editable form state
  const [form, setForm] = useState<OrgConfigRequest>({
    weekStartDay: "MONDAY",
    draftOpenOffsetHours: -60,
    lockDueOffsetHours: 12,
    reconcileOpenOffsetHours: 89,
    reconcileDueOffsetHours: 106,
    defaultWeeklyBudget: 10,
    timezone: "America/New_York",
  });

  useEffect(() => {
    let cancelled = false;
    api.getOrgConfig().then((data) => {
      if (cancelled) return;
      setConfig(data);
      setForm({
        weekStartDay: data.weekStartDay,
        draftOpenOffsetHours: data.draftOpenOffsetHours,
        lockDueOffsetHours: data.lockDueOffsetHours,
        reconcileOpenOffsetHours: data.reconcileOpenOffsetHours,
        reconcileDueOffsetHours: data.reconcileDueOffsetHours,
        defaultWeeklyBudget: data.defaultWeeklyBudget,
        timezone: data.timezone,
      });
      setLoading(false);
    }).catch((err: unknown) => {
      if (cancelled) return;
      setError(err instanceof Error ? err.message : "Failed to load config");
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [api]);

  const handleSave = useCallback(async () => {
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      const updated = await api.updateOrgConfig(form);
      setConfig(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  }, [api, form]);

  const updateField = useCallback(<K extends keyof OrgConfigRequest>(key: K, value: OrgConfigRequest[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setSaved(false);
  }, []);

  return (
    <Card data-testid="org-config-section">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Clock className="h-4 w-4" />
          Organization Cadence Configuration
        </CardTitle>
      </CardHeader>
      <CardContent>
        {loading && <p className="text-sm text-muted animate-pulse">Loading configuration…</p>}
        {error && (
          <div className="flex items-center gap-2 text-sm text-danger mb-4" role="alert">
            <AlertTriangle className="h-4 w-4" /> {error}
          </div>
        )}
        {!loading && (
          <div className="space-y-5">
            {/* Row 1: Week start + timezone */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-muted mb-1">Week Start Day</label>
                <Select
                  value={form.weekStartDay}
                  onChange={(e) => updateField("weekStartDay", e.target.value)}
                  data-testid="org-week-start-day"
                >
                  {DAYS.map((d) => <option key={d} value={d}>{displayDay(d)}</option>)}
                </Select>
              </div>
              <div>
                <label className="block text-xs font-medium text-muted mb-1">Timezone</label>
                <Select
                  value={form.timezone}
                  onChange={(e) => updateField("timezone", e.target.value)}
                  data-testid="org-timezone"
                >
                  {TIMEZONES.map((tz) => <option key={tz} value={tz}>{tz}</option>)}
                </Select>
              </div>
            </div>

            {/* Row 2: Cadence deadlines as Day + Time */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <DayTimeField
                label="Draft opens"
                offsetHours={form.draftOpenOffsetHours}
                weekStartDay={form.weekStartDay}
                onChange={(v) => updateField("draftOpenOffsetHours", v)}
                testId="org-draft-offset"
              />
              <DayTimeField
                label="Lock due"
                offsetHours={form.lockDueOffsetHours}
                weekStartDay={form.weekStartDay}
                onChange={(v) => updateField("lockDueOffsetHours", v)}
                testId="org-lock-offset"
              />
              <DayTimeField
                label="Reconcile opens"
                offsetHours={form.reconcileOpenOffsetHours}
                weekStartDay={form.weekStartDay}
                onChange={(v) => updateField("reconcileOpenOffsetHours", v)}
                testId="org-reconcile-open-offset"
              />
              <DayTimeField
                label="Reconcile due"
                offsetHours={form.reconcileDueOffsetHours}
                weekStartDay={form.weekStartDay}
                onChange={(v) => updateField("reconcileDueOffsetHours", v)}
                testId="org-reconcile-due-offset"
              />
            </div>

            {/* Row 3: Budget */}
            <div className="max-w-xs">
              <label className="block text-xs font-medium text-muted mb-1">Default Weekly Budget (points)</label>
              <Input
                type="number"
                min={1}
                max={50}
                value={form.defaultWeeklyBudget}
                onChange={(e) => updateField("defaultWeeklyBudget", Number(e.target.value) || 10)}
                data-testid="org-budget"
              />
            </div>

            {/* Actions */}
            <div className="flex items-center gap-3 pt-2 border-t border-border">
              <Button onClick={() => void handleSave()} disabled={saving} data-testid="org-save-btn">
                {saving ? "Saving…" : <><Save className="h-3.5 w-3.5 mr-1.5" /> Save Configuration</>}
              </Button>
              {saved && (
                <span className="flex items-center gap-1 text-sm text-success" data-testid="org-saved-indicator">
                  <CheckCircle className="h-4 w-4" /> Saved
                </span>
              )}
              {config?.updatedAt && (
                <span className="text-xs text-muted ml-auto">
                  Last updated: {new Date(config.updatedAt).toLocaleDateString()}
                </span>
              )}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ── Team Config Section ─────────────────────────────────────────────────────

function TeamConfigSection({
  api,
  teamId,
}: {
  readonly api: ReturnType<typeof createConfigApi>;
  readonly teamId: string;
}) {
  const [teamConfig, setTeamConfig] = useState<EffectiveConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Override form — null means "use org default"
  const [overrides, setOverrides] = useState<TeamConfigOverrideRequest>({});

  useEffect(() => {
    if (!teamId) return;
    let cancelled = false;
    api.getTeamConfig(teamId).then((data) => {
      if (cancelled) return;
      setTeamConfig(data);
      setLoading(false);
    }).catch((err: unknown) => {
      if (cancelled) return;
      setError(err instanceof Error ? err.message : "Failed to load team config");
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [api, teamId]);

  const handleSaveOverride = useCallback(async () => {
    if (!teamId) return;
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      await api.updateTeamConfig(teamId, overrides);
      const refreshed = await api.getTeamConfig(teamId);
      setTeamConfig(refreshed);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  }, [api, teamId, overrides]);

  return (
    <Card data-testid="team-config-section">
      <CardHeader>
        <button
          type="button"
          className="flex w-full items-center justify-between"
          onClick={() => setExpanded(!expanded)}
        >
          <CardTitle className="flex items-center gap-2">
            <Users className="h-4 w-4" />
            Team Configuration Overrides
          </CardTitle>
          {expanded ? <ChevronUp className="h-4 w-4 text-muted" /> : <ChevronDown className="h-4 w-4 text-muted" />}
        </button>
      </CardHeader>
      {expanded && (
        <CardContent>
          {loading && <p className="text-sm text-muted animate-pulse">Loading team config…</p>}
          {error && (
            <div className="flex items-center gap-2 text-sm text-danger mb-4" role="alert">
              <AlertTriangle className="h-4 w-4" /> {error}
            </div>
          )}
          {!loading && teamConfig && (
            <div className="space-y-4">
              <div className="flex items-center gap-2 mb-3">
                <Badge variant={teamConfig.hasTeamOverride ? "locked" : "draft"}>
                  {teamConfig.hasTeamOverride ? "Has team overrides" : "Using org defaults"}
                </Badge>
              </div>

              {/* Show current effective values */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <ConfigValueCard label="Week start" value={displayDay(teamConfig.weekStartDay)} />
                <ConfigValueCard label="Lock due" value={`${teamConfig.lockDueOffsetHours}h`} />
                <ConfigValueCard label="Budget" value={`${teamConfig.defaultWeeklyBudget} pts`} />
                <ConfigValueCard label="Timezone" value={teamConfig.timezone} />
              </div>

              {/* Override form */}
              <div className="pt-3 border-t border-border space-y-3">
                <p className="text-xs text-muted">
                  Set values below to override org defaults for this team. Leave blank to inherit.
                </p>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-muted mb-1">Budget override</label>
                    <Input
                      type="number"
                      min={1}
                      max={50}
                      placeholder={String(teamConfig.defaultWeeklyBudget)}
                      value={overrides.defaultWeeklyBudget ?? ""}
                      onChange={(e) =>
                        setOverrides((prev) => ({
                          ...prev,
                          defaultWeeklyBudget: e.target.value ? Number(e.target.value) : null,
                        }))
                      }
                      data-testid="team-budget-override"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted mb-1">Lock due override (hours)</label>
                    <Input
                      type="number"
                      placeholder={String(teamConfig.lockDueOffsetHours)}
                      value={overrides.lockDueOffsetHours ?? ""}
                      onChange={(e) =>
                        setOverrides((prev) => ({
                          ...prev,
                          lockDueOffsetHours: e.target.value ? Number(e.target.value) : null,
                        }))
                      }
                      data-testid="team-lock-override"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted mb-1">Timezone override</label>
                    <Select
                      value={overrides.timezone ?? ""}
                      onChange={(e) =>
                        setOverrides((prev) => ({
                          ...prev,
                          timezone: e.target.value || null,
                        }))
                      }
                      data-testid="team-timezone-override"
                    >
                      <option value="">Inherit from org</option>
                      {TIMEZONES.map((tz) => <option key={tz} value={tz}>{tz}</option>)}
                    </Select>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <Button
                    onClick={() => void handleSaveOverride()}
                    disabled={saving}
                    size="sm"
                    data-testid="team-save-btn"
                  >
                    {saving ? "Saving…" : <><Save className="h-3.5 w-3.5 mr-1.5" /> Save Overrides</>}
                  </Button>
                  {saved && (
                    <span className="flex items-center gap-1 text-sm text-success">
                      <CheckCircle className="h-4 w-4" /> Saved
                    </span>
                  )}
                </div>
              </div>
            </div>
          )}
        </CardContent>
      )}
    </Card>
  );
}

// ── Small reusable components ───────────────────────────────────────────────

function DayTimeField({
  label,
  offsetHours,
  weekStartDay,
  onChange,
  testId,
}: {
  readonly label: string;
  readonly offsetHours: number;
  readonly weekStartDay: string;
  readonly onChange: (v: number) => void;
  readonly testId: string;
}) {
  const { day, time } = offsetToDayTime(offsetHours, weekStartDay);
  return (
    <div>
      <label className="block text-xs font-medium text-muted mb-1">{label}</label>
      <div className="flex gap-2">
        <Select
          value={day}
          onChange={(e) => onChange(dayTimeToOffset(e.target.value, time, weekStartDay))}
          data-testid={`${testId}-day`}
          className="flex-1"
        >
          {DAYS.map((d) => <option key={d} value={d}>{displayDay(d)}</option>)}
        </Select>
        <Input
          type="time"
          value={time}
          onChange={(e) => onChange(dayTimeToOffset(day, e.target.value || "00:00", weekStartDay))}
          data-testid={`${testId}-time`}
          className="w-[130px]"
        />
      </div>
    </div>
  );
}

function ConfigValueCard({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div className="rounded-default border border-border bg-surface-raised p-3">
      <p className="text-[10px] font-medium text-muted uppercase tracking-wide">{label}</p>
      <p className="text-sm font-semibold mt-0.5">{value}</p>
    </div>
  );
}
