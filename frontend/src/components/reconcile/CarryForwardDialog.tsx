/**
 * CarryForwardDialog — captures carry-forward reason and target week.
 */
import { useState, type FormEvent } from "react";
import { RotateCcw, X } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Select } from "../ui/Select.js";
import { cn } from "../../lib/utils.js";
import type { CommitResponse, CarryForwardReason } from "../../api/planTypes.js";

const CARRY_FORWARD_REASONS: { value: CarryForwardReason; label: string }[] = [
  { value: "STILL_IN_PROGRESS",     label: "Still in progress — work continues" },
  { value: "BLOCKED_BY_DEPENDENCY", label: "Blocked by a dependency" },
  { value: "SCOPE_EXPANDED",        label: "Scope expanded beyond estimate" },
  { value: "REPRIORITIZED",         label: "Reprioritized — will complete next week" },
  { value: "RESOURCE_UNAVAILABLE",  label: "Resource or person unavailable" },
  { value: "TECHNICAL_OBSTACLE",    label: "Technical obstacle encountered" },
  { value: "EXTERNAL_DELAY",        label: "External delay (vendor, partner, etc.)" },
  { value: "UNDERESTIMATED",        label: "Work was underestimated" },
];

function getWeekStart(offsetWeeks = 0): string {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const daysToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const monday = new Date(now);
  monday.setDate(now.getDate() + daysToMonday + offsetWeeks * 7);
  return monday.toISOString().slice(0, 10);
}

function formatWeekLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  return `Week of ${d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}`;
}

export interface CarryForwardDialogProps {
  readonly commit: CommitResponse;
  readonly onConfirm: (targetWeekStart: string, reason: CarryForwardReason, reasonText?: string) => void;
  readonly onCancel: () => void;
  readonly isSubmitting?: boolean;
}

export function CarryForwardDialog({ commit, onConfirm, onCancel, isSubmitting = false }: CarryForwardDialogProps) {
  const [targetWeekStart, setTargetWeekStart] = useState(getWeekStart(1));
  const [reason, setReason] = useState<CarryForwardReason | "">("");
  const [reasonText, setReasonText] = useState("");
  const [errors, setErrors] = useState<{ reason?: string; target?: string }>({});

  const nextWeekOption = getWeekStart(1);
  const twoWeeksOption = getWeekStart(2);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const next: typeof errors = {};
    if (!reason) next.reason = "Please select a reason";
    if (!targetWeekStart) next.target = "Target week is required";
    if (Object.keys(next).length > 0) { setErrors(next); return; }
    setErrors({});
    const trimmedText = reasonText.trim();
    onConfirm(targetWeekStart, reason as CarryForwardReason, trimmedText !== "" ? trimmedText : undefined);
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="carry-forward-dialog-title"
      data-testid="carry-forward-dialog"
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/45 backdrop-blur-[2px]"
    >
      <div className="w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-xl max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <h3 id="carry-forward-dialog-title" className="m-0 flex items-center gap-2 text-base font-semibold text-primary">
            <RotateCcw className="h-4 w-4" aria-hidden="true" />
            Carry Forward
          </h3>
          <Button variant="ghost" size="icon" onClick={onCancel} aria-label="Close" className="h-7 w-7">
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* Commit preview */}
        <div data-testid="carry-forward-commit-preview" className="mb-4 rounded-default border border-neutral-200 bg-neutral-50 px-3 py-2.5 text-sm text-foreground">
          <strong>"{commit.title}"</strong> will be copied into the target week as a new commit with carry-forward provenance.
          {commit.carryForwardStreak > 0 && (
            <span className="block mt-1 text-xs text-muted">
              Already carried forward {commit.carryForwardStreak} time{commit.carryForwardStreak !== 1 ? "s" : ""}.
            </span>
          )}
        </div>

        <form onSubmit={handleSubmit} noValidate>
          {/* Target week */}
          <div className="mb-4">
            <p className="m-0 mb-1.5 text-sm font-medium">
              Target week <span aria-hidden="true" className="text-danger">*</span>
            </p>
            <div role="group" aria-label="Target week options" className="flex flex-col gap-1.5" data-testid="carry-forward-week-options">
              {[nextWeekOption, twoWeeksOption].map((weekStart) => (
                <label
                  key={weekStart}
                  className={cn(
                    "flex items-center gap-2 px-3 py-2 rounded-default border text-sm cursor-pointer",
                    targetWeekStart === weekStart ? "border-foreground bg-foreground/5" : "border-border",
                  )}
                >
                  <input type="radio" name="targetWeek" value={weekStart} checked={targetWeekStart === weekStart} onChange={() => setTargetWeekStart(weekStart)} />
                  {formatWeekLabel(weekStart)}
                </label>
              ))}
            </div>
            {errors.target && <p role="alert" className="mt-1 text-xs text-danger">{errors.target}</p>}
          </div>

          {/* Reason */}
          <div className="mb-4">
            <Select
              label="Reason *"
              id="carry-forward-reason"
              value={reason}
              onChange={(e) => { setReason(e.target.value as CarryForwardReason | ""); setErrors((prev) => { const { reason: _r, ...rest } = prev; return rest; }); }}
              aria-required="true"
              data-testid="carry-forward-reason-select"
              error={errors.reason}
            >
              <option value="">— Select a reason —</option>
              {CARRY_FORWARD_REASONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
            </Select>
          </div>

          {/* Optional notes */}
          <div className="mb-5 flex flex-col gap-1.5">
            <label htmlFor="carry-forward-notes" className="text-sm font-medium">Additional notes (optional)</label>
            <textarea
              id="carry-forward-notes"
              value={reasonText}
              onChange={(e) => setReasonText(e.target.value)}
              rows={2}
              data-testid="carry-forward-notes"
              className="w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:border-primary"
              placeholder="Any additional context…"
            />
          </div>

          <div className="flex items-center justify-end gap-2 pt-4 border-t border-border">
            <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting} data-testid="carry-forward-cancel">
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={isSubmitting} data-testid="carry-forward-confirm">
              <RotateCcw className="h-3.5 w-3.5" />
              {isSubmitting ? "Saving…" : "Carry Forward"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
