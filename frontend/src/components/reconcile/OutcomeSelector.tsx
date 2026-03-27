/**
 * OutcomeSelector — per-commit outcome radio control.
 * Uses monochrome design system with Lucide icons instead of emojis.
 */
import { Check, Circle, X, Ban } from "lucide-react";
import { cn } from "../../lib/utils.js";
import type { CommitOutcome } from "../../api/planTypes.js";
import type { LucideIcon } from "lucide-react";

const OUTCOME_CONFIG: Record<CommitOutcome, { label: string; icon: LucideIcon; selectedCls: string; selectedBorderCls: string }> = {
  ACHIEVED:           { label: "Achieved",           icon: Check,  selectedCls: "bg-foreground/10 text-foreground font-bold", selectedBorderCls: "border-foreground" },
  PARTIALLY_ACHIEVED: { label: "Partially Achieved", icon: Circle, selectedCls: "bg-foreground/5 text-muted",                selectedBorderCls: "border-foreground/40" },
  NOT_ACHIEVED:       { label: "Not Achieved",       icon: X,      selectedCls: "bg-foreground/8 text-foreground underline",  selectedBorderCls: "border-foreground/60" },
  CANCELED:           { label: "Canceled",           icon: Ban,    selectedCls: "bg-muted-bg text-muted",                     selectedBorderCls: "border-border" },
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
          const Icon = config.icon;
          return (
            <label
              key={outcome}
              data-testid={`outcome-option-${commitId}-${outcome.toLowerCase()}`}
              className={cn(
                "flex items-center gap-1.5 px-2.5 py-1 rounded-sm border-2 text-xs font-medium cursor-pointer transition-all select-none",
                isSelected ? `${config.selectedCls} ${config.selectedBorderCls} font-semibold` : "border-border text-foreground hover:bg-muted-bg",
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
              <Icon className="h-3.5 w-3.5" aria-hidden="true" />
              {config.label}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
