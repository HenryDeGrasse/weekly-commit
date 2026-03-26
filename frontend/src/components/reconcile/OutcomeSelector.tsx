/**
 * OutcomeSelector — per-commit outcome radio control.
 */
import { cn } from "../../lib/utils.js";
import type { CommitOutcome } from "../../api/planTypes.js";

const OUTCOME_CONFIG: Record<CommitOutcome, { label: string; emoji: string; selectedCls: string; selectedBorderCls: string }> = {
  ACHIEVED:           { label: "Achieved",           emoji: "✅", selectedCls: "bg-emerald-50 text-emerald-800", selectedBorderCls: "border-emerald-600" },
  PARTIALLY_ACHIEVED: { label: "Partially Achieved", emoji: "🟡", selectedCls: "bg-amber-50 text-amber-800",   selectedBorderCls: "border-amber-500" },
  NOT_ACHIEVED:       { label: "Not Achieved",       emoji: "❌", selectedCls: "bg-red-50 text-red-800",       selectedBorderCls: "border-red-600" },
  CANCELED:           { label: "Canceled",           emoji: "🚫", selectedCls: "bg-slate-100 text-slate-700",  selectedBorderCls: "border-slate-400" },
};

const ALL_OUTCOMES: CommitOutcome[] = ["ACHIEVED", "PARTIALLY_ACHIEVED", "NOT_ACHIEVED", "CANCELED"];

export interface OutcomeSelectorProps {
  readonly commitId: string;
  readonly value: CommitOutcome | null;
  readonly onChange: (outcome: CommitOutcome) => void;
  readonly disabled?: boolean;
}

export function OutcomeSelector({ commitId, value, onChange, disabled = false }: OutcomeSelectorProps) {
  return (
    <fieldset className="border-0 p-0 m-0" data-testid={`outcome-selector-${commitId}`}>
      <legend className="text-xs font-semibold uppercase tracking-wider text-muted mb-1.5">Outcome</legend>
      <div className="flex gap-1.5 flex-wrap">
        {ALL_OUTCOMES.map((outcome) => {
          const config = OUTCOME_CONFIG[outcome];
          const isSelected = value === outcome;
          return (
            <label
              key={outcome}
              data-testid={`outcome-option-${commitId}-${outcome.toLowerCase()}`}
              className={cn(
                "flex items-center gap-1.5 px-2.5 py-1 rounded-full border-2 text-xs font-medium cursor-pointer transition-all select-none",
                isSelected ? `${config.selectedCls} ${config.selectedBorderCls} font-semibold` : "border-border text-foreground hover:bg-muted/10",
                disabled && "cursor-not-allowed opacity-60",
              )}
            >
              <input
                type="radio"
                name={`outcome-${commitId}`}
                value={outcome}
                checked={isSelected}
                onChange={() => !disabled && onChange(outcome)}
                disabled={disabled}
                className="absolute opacity-0 pointer-events-none"
              />
              <span aria-hidden="true">{config.emoji}</span>
              {config.label}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
