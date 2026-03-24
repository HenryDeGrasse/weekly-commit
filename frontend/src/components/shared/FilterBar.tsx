/**
 * FilterBar — reusable cross-page filter bar.
 *
 * Filter dimensions:
 *   - week (date input, ISO Monday)
 *   - user (text search/select)
 *   - team (text select)
 *   - RCDO (hierarchical picker — text input for v1)
 *   - chess piece (multi-select)
 *   - plan state (multi-select)
 *   - risk flag (multi-select)
 *
 * Filters are applied via URL query params for shareability.
 * Clear all / save filter preset buttons.
 *
 * Usage: pass `params` (from useSearchParams()) and `onChange` callback.
 * The component does NOT manage its own URL state — the parent page does.
 */
import { useState, useCallback, type ChangeEvent } from "react";
import type { ChessPiece, PlanState } from "../../api/planTypes.js";
import type { FilterPreset } from "../../api/ticketTypes.js";

export type RiskFlag = "CARRY_FORWARD" | "AUTO_LOCKED" | "EXCEPTION" | "OVER_BUDGET";

export interface FilterValues {
  week?: string | undefined;
  userId?: string | undefined;
  teamId?: string | undefined;
  rcdoNodeId?: string | undefined;
  chessPieces?: ChessPiece[] | undefined;
  planStates?: PlanState[] | undefined;
  riskFlags?: RiskFlag[] | undefined;
}

export interface FilterBarProps {
  /** Current filter values. */
  readonly values: FilterValues;
  /** Called whenever a filter dimension changes. */
  readonly onChange: (next: FilterValues) => void;
  /** Optional list of teams to show in team select. */
  readonly teams?: Array<{ id: string; name: string }>;
  /** Optional list of saved presets. */
  readonly savedPresets?: FilterPreset[];
  /** Called when user saves a new preset. */
  readonly onSavePreset?: (name: string, values: FilterValues) => void;
  /** Called when user loads a saved preset. */
  readonly onLoadPreset?: (preset: FilterPreset) => void;
}

const CHESS_PIECES: ChessPiece[] = [
  "KING",
  "QUEEN",
  "ROOK",
  "BISHOP",
  "KNIGHT",
  "PAWN",
];
const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔",
  QUEEN: "♕",
  ROOK: "♖",
  BISHOP: "♗",
  KNIGHT: "♘",
  PAWN: "♙",
};

const PLAN_STATES: PlanState[] = ["DRAFT", "LOCKED", "RECONCILING", "RECONCILED"];

const RISK_FLAGS: RiskFlag[] = [
  "CARRY_FORWARD",
  "AUTO_LOCKED",
  "EXCEPTION",
  "OVER_BUDGET",
];
const RISK_FLAG_LABELS: Record<RiskFlag, string> = {
  CARRY_FORWARD: "Carry-forward",
  AUTO_LOCKED: "Auto-locked",
  EXCEPTION: "Exception",
  OVER_BUDGET: "Over budget",
};

function toggleArrayItem<T>(arr: T[] | undefined, item: T): T[] {
  const current = arr ?? [];
  return current.includes(item)
    ? current.filter((x) => x !== item)
    : [...current, item];
}

/** Small pill-style multi-select button. */
function TogglePill<T extends string>({
  value,
  label,
  active,
  onToggle,
}: {
  readonly value: T;
  readonly label: string;
  readonly active: boolean;
  readonly onToggle: (v: T) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onToggle(value)}
      data-testid={`filter-pill-${value.toLowerCase()}`}
      aria-pressed={active}
      style={{
        padding: "3px 10px",
        borderRadius: "999px",
        fontSize: "0.75rem",
        fontWeight: 600,
        border: active
          ? "1.5px solid var(--color-primary)"
          : "1px solid var(--color-border)",
        background: active ? "var(--color-primary)" : "var(--color-surface)",
        color: active ? "#fff" : "var(--color-text)",
        cursor: "pointer",
        fontFamily: "inherit",
        transition: "background 0.15s, color 0.15s",
      }}
    >
      {label}
    </button>
  );
}

const inputStyle: React.CSSProperties = {
  padding: "0.35rem 0.6rem",
  border: "1px solid var(--color-border)",
  borderRadius: "var(--border-radius)",
  background: "var(--color-surface)",
  color: "var(--color-text)",
  fontFamily: "inherit",
  fontSize: "0.8rem",
};

const labelStyle: React.CSSProperties = {
  fontSize: "0.7rem",
  fontWeight: 700,
  textTransform: "uppercase",
  letterSpacing: "0.04em",
  color: "var(--color-text-muted)",
  marginBottom: "0.25rem",
  display: "block",
};

export function FilterBar({
  values,
  onChange,
  teams = [],
  savedPresets = [],
  onSavePreset,
  onLoadPreset,
}: FilterBarProps) {
  const [savePresetName, setSavePresetName] = useState("");
  const [showSaveInput, setShowSaveInput] = useState(false);

  const handleWeekChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const v = e.target.value;
      const next: FilterValues = { ...values };
      next.week = v || undefined;
      onChange(next);
    },
    [values, onChange],
  );

  const handleUserChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const v = e.target.value;
      const next: FilterValues = { ...values };
      next.userId = v || undefined;
      onChange(next);
    },
    [values, onChange],
  );

  const handleTeamChange = useCallback(
    (e: ChangeEvent<HTMLSelectElement>) => {
      const v = e.target.value;
      const next: FilterValues = { ...values };
      next.teamId = v || undefined;
      onChange(next);
    },
    [values, onChange],
  );

  const handleRcdoChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const v = e.target.value;
      const next: FilterValues = { ...values };
      next.rcdoNodeId = v || undefined;
      onChange(next);
    },
    [values, onChange],
  );

  const handleChessToggle = useCallback(
    (piece: ChessPiece) => {
      onChange({
        ...values,
        chessPieces: toggleArrayItem(values.chessPieces, piece),
      });
    },
    [values, onChange],
  );

  const handlePlanStateToggle = useCallback(
    (state: PlanState) => {
      onChange({
        ...values,
        planStates: toggleArrayItem(values.planStates, state),
      });
    },
    [values, onChange],
  );

  const handleRiskFlagToggle = useCallback(
    (flag: RiskFlag) => {
      onChange({
        ...values,
        riskFlags: toggleArrayItem(values.riskFlags, flag),
      });
    },
    [values, onChange],
  );

  const handleClearAll = useCallback(() => {
    onChange({});
  }, [onChange]);

  const handleSavePreset = useCallback(() => {
    if (!savePresetName.trim()) return;
    onSavePreset?.(savePresetName.trim(), values);
    setSavePresetName("");
    setShowSaveInput(false);
  }, [savePresetName, values, onSavePreset]);

  const hasAnyFilter =
    values.week !== undefined ||
    values.userId !== undefined ||
    values.teamId !== undefined ||
    values.rcdoNodeId !== undefined ||
    (values.chessPieces?.length ?? 0) > 0 ||
    (values.planStates?.length ?? 0) > 0 ||
    (values.riskFlags?.length ?? 0) > 0;

  return (
    <div
      data-testid="filter-bar"
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
        borderRadius: "var(--border-radius)",
        padding: "0.875rem 1rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
      }}
    >
      {/* Row 1: date/text filters */}
      <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", alignItems: "flex-end" }}>
        {/* Week */}
        <div style={{ display: "flex", flexDirection: "column" }}>
          <label htmlFor="fb-week" style={labelStyle}>
            Week
          </label>
          <input
            id="fb-week"
            type="date"
            value={values.week ?? ""}
            onChange={handleWeekChange}
            data-testid="filter-week"
            style={inputStyle}
          />
        </div>

        {/* User */}
        <div style={{ display: "flex", flexDirection: "column" }}>
          <label htmlFor="fb-user" style={labelStyle}>
            User
          </label>
          <input
            id="fb-user"
            type="text"
            placeholder="User ID or name…"
            value={values.userId ?? ""}
            onChange={handleUserChange}
            data-testid="filter-user"
            style={{ ...inputStyle, minWidth: "140px" }}
          />
        </div>

        {/* Team */}
        <div style={{ display: "flex", flexDirection: "column" }}>
          <label htmlFor="fb-team" style={labelStyle}>
            Team
          </label>
          {teams.length > 0 ? (
            <select
              id="fb-team"
              value={values.teamId ?? ""}
              onChange={handleTeamChange}
              data-testid="filter-team"
              style={inputStyle}
            >
              <option value="">All teams</option>
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          ) : (
            <input
              id="fb-team"
              type="text"
              placeholder="Team ID…"
              value={values.teamId ?? ""}
              onChange={(e) => {
                const next: FilterValues = { ...values };
                next.teamId = e.target.value || undefined;
                onChange(next);
              }}
              data-testid="filter-team"
              style={{ ...inputStyle, minWidth: "120px" }}
            />
          )}
        </div>

        {/* RCDO */}
        <div style={{ display: "flex", flexDirection: "column" }}>
          <label htmlFor="fb-rcdo" style={labelStyle}>
            RCDO
          </label>
          <input
            id="fb-rcdo"
            type="text"
            placeholder="RCDO node ID…"
            value={values.rcdoNodeId ?? ""}
            onChange={handleRcdoChange}
            data-testid="filter-rcdo"
            style={{ ...inputStyle, minWidth: "140px" }}
          />
        </div>
      </div>

      {/* Row 2: chess piece multi-select */}
      <div>
        <span style={labelStyle}>Chess Piece</span>
        <div style={{ display: "flex", gap: "0.375rem", flexWrap: "wrap" }}>
          {CHESS_PIECES.map((piece) => (
            <TogglePill
              key={piece}
              value={piece}
              label={`${CHESS_PIECE_ICONS[piece]} ${piece}`}
              active={(values.chessPieces ?? []).includes(piece)}
              onToggle={handleChessToggle}
            />
          ))}
        </div>
      </div>

      {/* Row 3: plan state multi-select */}
      <div>
        <span style={labelStyle}>Plan State</span>
        <div style={{ display: "flex", gap: "0.375rem", flexWrap: "wrap" }}>
          {PLAN_STATES.map((state) => (
            <TogglePill
              key={state}
              value={state}
              label={state}
              active={(values.planStates ?? []).includes(state)}
              onToggle={handlePlanStateToggle}
            />
          ))}
        </div>
      </div>

      {/* Row 4: risk flags multi-select */}
      <div>
        <span style={labelStyle}>Risk Flag</span>
        <div style={{ display: "flex", gap: "0.375rem", flexWrap: "wrap" }}>
          {RISK_FLAGS.map((flag) => (
            <TogglePill
              key={flag}
              value={flag}
              label={RISK_FLAG_LABELS[flag]}
              active={(values.riskFlags ?? []).includes(flag)}
              onToggle={handleRiskFlagToggle}
            />
          ))}
        </div>
      </div>

      {/* Row 5: actions */}
      <div
        style={{
          display: "flex",
          gap: "0.5rem",
          alignItems: "center",
          flexWrap: "wrap",
          justifyContent: "space-between",
        }}
      >
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", flexWrap: "wrap" }}>
          {hasAnyFilter && (
            <button
              type="button"
              onClick={handleClearAll}
              data-testid="filter-clear-all"
              style={{
                padding: "0.3rem 0.75rem",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--border-radius)",
                background: "var(--color-surface)",
                color: "var(--color-text)",
                cursor: "pointer",
                fontFamily: "inherit",
                fontSize: "0.8rem",
              }}
            >
              ✕ Clear all
            </button>
          )}

          {/* Saved presets dropdown */}
          {savedPresets.length > 0 && (
            <select
              aria-label="Load saved filter preset"
              data-testid="filter-preset-select"
              onChange={(e) => {
                const preset = savedPresets.find((p) => p.id === e.target.value);
                if (preset) onLoadPreset?.(preset);
                e.target.value = "";
              }}
              defaultValue=""
              style={inputStyle}
            >
              <option value="" disabled>
                Load preset…
              </option>
              {savedPresets.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Save preset */}
        {onSavePreset && (
          <div style={{ display: "flex", gap: "0.375rem", alignItems: "center" }}>
            {showSaveInput ? (
              <>
                <input
                  type="text"
                  placeholder="Preset name…"
                  value={savePresetName}
                  onChange={(e) => setSavePresetName(e.target.value)}
                  data-testid="filter-preset-name-input"
                  style={{ ...inputStyle, minWidth: "120px" }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") handleSavePreset();
                    if (e.key === "Escape") setShowSaveInput(false);
                  }}
                  autoFocus
                />
                <button
                  type="button"
                  onClick={handleSavePreset}
                  disabled={!savePresetName.trim()}
                  data-testid="filter-preset-save-confirm"
                  style={{
                    padding: "0.3rem 0.75rem",
                    border: "none",
                    borderRadius: "var(--border-radius)",
                    background: "var(--color-primary)",
                    color: "#fff",
                    cursor: "pointer",
                    fontFamily: "inherit",
                    fontSize: "0.8rem",
                  }}
                >
                  Save
                </button>
                <button
                  type="button"
                  onClick={() => setShowSaveInput(false)}
                  style={{
                    padding: "0.3rem 0.75rem",
                    border: "1px solid var(--color-border)",
                    borderRadius: "var(--border-radius)",
                    background: "var(--color-surface)",
                    color: "var(--color-text)",
                    cursor: "pointer",
                    fontFamily: "inherit",
                    fontSize: "0.8rem",
                  }}
                >
                  Cancel
                </button>
              </>
            ) : (
              <button
                type="button"
                onClick={() => setShowSaveInput(true)}
                data-testid="filter-save-preset-btn"
                style={{
                  padding: "0.3rem 0.75rem",
                  border: "1px solid var(--color-border)",
                  borderRadius: "var(--border-radius)",
                  background: "var(--color-surface)",
                  color: "var(--color-text)",
                  cursor: "pointer",
                  fontFamily: "inherit",
                  fontSize: "0.8rem",
                }}
              >
                ☆ Save preset
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
