/**
 * OutcomeSelector — per-commit outcome radio control.
 *
 * Outcomes: ACHIEVED (green), PARTIALLY_ACHIEVED (yellow),
 *           NOT_ACHIEVED (red), CANCELED (gray).
 */
import type { CommitOutcome } from "../../api/planTypes.js";

const OUTCOME_CONFIG: Record<
  CommitOutcome,
  { label: string; emoji: string; bg: string; color: string }
> = {
  ACHIEVED: {
    label: "Achieved",
    emoji: "✅",
    bg: "#f0fdf4",
    color: "#15803d",
  },
  PARTIALLY_ACHIEVED: {
    label: "Partially Achieved",
    emoji: "🟡",
    bg: "#fffbeb",
    color: "#92400e",
  },
  NOT_ACHIEVED: {
    label: "Not Achieved",
    emoji: "❌",
    bg: "#fef2f2",
    color: "#991b1b",
  },
  CANCELED: {
    label: "Canceled",
    emoji: "🚫",
    bg: "#f3f4f6",
    color: "#374151",
  },
};

const ALL_OUTCOMES: CommitOutcome[] = [
  "ACHIEVED",
  "PARTIALLY_ACHIEVED",
  "NOT_ACHIEVED",
  "CANCELED",
];

export interface OutcomeSelectorProps {
  readonly commitId: string;
  readonly value: CommitOutcome | null;
  readonly onChange: (outcome: CommitOutcome) => void;
  readonly disabled?: boolean;
}

export function OutcomeSelector({
  commitId,
  value,
  onChange,
  disabled = false,
}: OutcomeSelectorProps) {
  return (
    <fieldset
      style={{ border: "none", padding: 0, margin: 0 }}
      data-testid={`outcome-selector-${commitId}`}
    >
      <legend
        style={{
          fontSize: "0.75rem",
          fontWeight: 600,
          color: "var(--color-text-muted)",
          textTransform: "uppercase",
          letterSpacing: "0.05em",
          marginBottom: "0.375rem",
        }}
      >
        Outcome
      </legend>
      <div
        style={{
          display: "flex",
          gap: "0.375rem",
          flexWrap: "wrap",
        }}
      >
        {ALL_OUTCOMES.map((outcome) => {
          const config = OUTCOME_CONFIG[outcome];
          const isSelected = value === outcome;
          return (
            <label
              key={outcome}
              data-testid={`outcome-option-${commitId}-${outcome.toLowerCase()}`}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "0.25rem",
                padding: "4px 10px",
                borderRadius: "999px",
                border: `2px solid ${isSelected ? config.color : "var(--color-border)"}`,
                background: isSelected ? config.bg : "transparent",
                cursor: disabled ? "not-allowed" : "pointer",
                opacity: disabled ? 0.6 : 1,
                fontSize: "0.8rem",
                fontWeight: isSelected ? 700 : 400,
                color: isSelected ? config.color : "var(--color-text)",
                transition: "all 0.1s",
              }}
            >
              <input
                type="radio"
                name={`outcome-${commitId}`}
                value={outcome}
                checked={isSelected}
                onChange={() => !disabled && onChange(outcome)}
                disabled={disabled}
                style={{ position: "absolute", opacity: 0, pointerEvents: "none" }}
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
