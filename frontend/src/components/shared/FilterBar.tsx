/**
 * FilterBar — reusable cross-page filter bar.
 */
import { useState, useCallback, type ChangeEvent } from "react";
import { X, Bookmark } from "lucide-react";
import { Button } from "../ui/Button.js";
import { cn } from "../../lib/utils.js";
import type { ChessPiece, PlanState } from "../../api/planTypes.js";
import type { FilterPreset } from "../../api/ticketTypes.js";

export type RiskFlag = "CARRY_FORWARD" | "AUTO_LOCKED" | "EXCEPTION" | "OVER_BUDGET";

export interface FilterValues {
  week?: string;
  userId?: string;
  teamId?: string;
  rcdoNodeId?: string;
  chessPieces?: ChessPiece[];
  planStates?: PlanState[];
  riskFlags?: RiskFlag[];
}

export interface FilterBarProps {
  readonly values: FilterValues;
  readonly onChange: (next: FilterValues) => void;
  readonly teams?: Array<{ id: string; name: string }>;
  readonly savedPresets?: FilterPreset[];
  readonly onSavePreset?: (name: string, values: FilterValues) => void;
  readonly onLoadPreset?: (preset: FilterPreset) => void;
}

const CHESS_PIECES: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
const CHESS_PIECE_ICONS: Record<ChessPiece, string> = { KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙" };
const PLAN_STATES: PlanState[] = ["DRAFT", "LOCKED", "RECONCILING", "RECONCILED"];
const RISK_FLAGS: RiskFlag[] = ["CARRY_FORWARD", "AUTO_LOCKED", "EXCEPTION", "OVER_BUDGET"];
const RISK_FLAG_LABELS: Record<RiskFlag, string> = { CARRY_FORWARD: "Carry-forward", AUTO_LOCKED: "Auto-locked", EXCEPTION: "Exception", OVER_BUDGET: "Over budget" };

function toggleArrayItem<T>(arr: T[] | undefined, item: T): T[] {
  const current = arr ?? [];
  return current.includes(item) ? current.filter((x) => x !== item) : [...current, item];
}

function TogglePill<T extends string>({ value, label, active, onToggle }: { value: T; label: string; active: boolean; onToggle: (v: T) => void }) {
  return (
    <button
      type="button"
      onClick={() => onToggle(value)}
      data-testid={`filter-pill-${value.toLowerCase()}`}
      aria-pressed={active}
      className={cn(
        "px-2.5 py-0.5 rounded-full text-xs font-semibold border transition-colors",
        active ? "border-primary bg-primary text-primary-foreground" : "border-border bg-surface text-foreground hover:bg-muted/10",
      )}
    >
      {label}
    </button>
  );
}

const inputCls = "h-8 rounded-default border border-border bg-surface px-2.5 text-xs text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:border-primary";
const labelCls = "text-[0.65rem] font-bold uppercase tracking-wider text-muted mb-1 block";

export function FilterBar({ values, onChange, teams = [], savedPresets = [], onSavePreset, onLoadPreset }: FilterBarProps) {
  const [savePresetName, setSavePresetName] = useState("");
  const [showSaveInput, setShowSaveInput] = useState(false);

  const handleWeekChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value;
    onChange({ ...values, ...(v ? { week: v } : {}) });
  }, [values, onChange]);

  const handleClearAll = useCallback(() => onChange({}), [onChange]);

  const hasAnyFilter = values.week !== undefined || values.userId !== undefined || values.teamId !== undefined || values.rcdoNodeId !== undefined
    || (values.chessPieces?.length ?? 0) > 0 || (values.planStates?.length ?? 0) > 0 || (values.riskFlags?.length ?? 0) > 0;

  const handleSavePreset = useCallback(() => {
    if (!savePresetName.trim()) return;
    onSavePreset?.(savePresetName.trim(), values);
    setSavePresetName("");
    setShowSaveInput(false);
  }, [savePresetName, values, onSavePreset]);

  return (
    <div data-testid="filter-bar" className="rounded-default border border-border bg-surface p-4 flex flex-col gap-3">
      {/* Row 1: text/date inputs */}
      <div className="flex gap-3 flex-wrap items-end">
        <div className="flex flex-col">
          <label htmlFor="fb-week" className={labelCls}>Week</label>
          <input id="fb-week" type="date" value={values.week ?? ""} onChange={handleWeekChange} data-testid="filter-week" className={inputCls} />
        </div>
        <div className="flex flex-col">
          <label htmlFor="fb-user" className={labelCls}>User</label>
          <input id="fb-user" type="text" placeholder="User ID or name…" value={values.userId ?? ""} onChange={(e) => { const v = e.target.value; onChange({ ...values, ...(v ? { userId: v } : {}) }); }} data-testid="filter-user" className={cn(inputCls, "min-w-[140px]")} />
        </div>
        <div className="flex flex-col">
          <label htmlFor="fb-team" className={labelCls}>Team</label>
          {teams.length > 0 ? (
            <select id="fb-team" value={values.teamId ?? ""} onChange={(e) => { const v = e.target.value; onChange({ ...values, ...(v ? { teamId: v } : {}) }); }} data-testid="filter-team" className={inputCls}>
              <option value="">All teams</option>
              {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          ) : (
            <input id="fb-team" type="text" placeholder="Team ID…" value={values.teamId ?? ""} onChange={(e) => { const v = e.target.value; onChange({ ...values, ...(v ? { teamId: v } : {}) }); }} data-testid="filter-team" className={cn(inputCls, "min-w-[120px]")} />
          )}
        </div>
        <div className="flex flex-col">
          <label htmlFor="fb-rcdo" className={labelCls}>RCDO</label>
          <input id="fb-rcdo" type="text" placeholder="RCDO node ID…" value={values.rcdoNodeId ?? ""} onChange={(e) => { const v = e.target.value; onChange({ ...values, ...(v ? { rcdoNodeId: v } : {}) }); }} data-testid="filter-rcdo" className={cn(inputCls, "min-w-[140px]")} />
        </div>
      </div>

      {/* Row 2: chess piece */}
      <div>
        <span className={labelCls}>Chess Piece</span>
        <div className="flex gap-1.5 flex-wrap">
          {CHESS_PIECES.map((piece) => (
            <TogglePill key={piece} value={piece} label={`${CHESS_PIECE_ICONS[piece]} ${piece}`} active={(values.chessPieces ?? []).includes(piece)} onToggle={(p) => onChange({ ...values, chessPieces: toggleArrayItem(values.chessPieces, p) })} />
          ))}
        </div>
      </div>

      {/* Row 3: plan state */}
      <div>
        <span className={labelCls}>Plan State</span>
        <div className="flex gap-1.5 flex-wrap">
          {PLAN_STATES.map((state) => (
            <TogglePill key={state} value={state} label={state} active={(values.planStates ?? []).includes(state)} onToggle={(s) => onChange({ ...values, planStates: toggleArrayItem(values.planStates, s) })} />
          ))}
        </div>
      </div>

      {/* Row 4: risk flags */}
      <div>
        <span className={labelCls}>Risk Flag</span>
        <div className="flex gap-1.5 flex-wrap">
          {RISK_FLAGS.map((flag) => (
            <TogglePill key={flag} value={flag} label={RISK_FLAG_LABELS[flag]} active={(values.riskFlags ?? []).includes(flag)} onToggle={(f) => onChange({ ...values, riskFlags: toggleArrayItem(values.riskFlags, f) })} />
          ))}
        </div>
      </div>

      {/* Row 5: actions */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex gap-2 items-center flex-wrap">
          {hasAnyFilter && (
            <Button variant="secondary" size="sm" onClick={handleClearAll} data-testid="filter-clear-all">
              <X className="h-3 w-3" />Clear all
            </Button>
          )}
          {savedPresets.length > 0 && (
            <select
              aria-label="Load saved filter preset"
              data-testid="filter-preset-select"
              onChange={(e) => { const preset = savedPresets.find((p) => p.id === e.target.value); if (preset) onLoadPreset?.(preset); e.target.value = ""; }}
              defaultValue=""
              className={inputCls}
            >
              <option value="" disabled>Load preset…</option>
              {savedPresets.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          )}
        </div>

        {onSavePreset && (
          <div className="flex gap-1.5 items-center">
            {showSaveInput ? (
              <>
                <input
                  type="text"
                  placeholder="Preset name…"
                  value={savePresetName}
                  onChange={(e) => setSavePresetName(e.target.value)}
                  data-testid="filter-preset-name-input"
                  className={cn(inputCls, "min-w-[120px]")}
                  onKeyDown={(e) => { if (e.key === "Enter") handleSavePreset(); if (e.key === "Escape") setShowSaveInput(false); }}
                  autoFocus
                />
                <Button variant="primary" size="sm" onClick={handleSavePreset} disabled={!savePresetName.trim()} data-testid="filter-preset-save-confirm">Save</Button>
                <Button variant="secondary" size="sm" onClick={() => setShowSaveInput(false)}>Cancel</Button>
              </>
            ) : (
              <Button variant="secondary" size="sm" onClick={() => setShowSaveInput(true)} data-testid="filter-save-preset-btn">
                <Bookmark className="h-3 w-3" />Save preset
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
