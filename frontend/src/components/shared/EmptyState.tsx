/**
 * EmptyState — consistent empty-state illustration used across all major
 * surfaces. Renders a centred block with optional icon, title, description
 * text, and a primary CTA action.
 *
 * Usage:
 *   <EmptyState
 *     icon={<CalendarX className="h-10 w-10" />}
 *     title="No commits yet"
 *     description="Add your first commitment to start planning your week."
 *     action={<Button onClick={…}>+ Add Commit</Button>}
 *   />
 */
import { type ReactNode } from "react";
import { cn } from "../../lib/utils.js";

export interface EmptyStateProps {
  /** Lucide icon element or emoji. Rendered at a larger size above the title. */
  readonly icon?: ReactNode;
  /** Short, direct headline — e.g. "No commits yet". */
  readonly title: string;
  /** One-sentence explanation of what to do next. */
  readonly description?: string;
  /** Primary CTA — typically a Button. */
  readonly action?: ReactNode;
  /** Optional wrapper class overrides. */
  readonly className?: string;
  readonly "data-testid"?: string;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
  "data-testid": testId,
}: EmptyStateProps) {
  return (
    <div
      data-testid={testId ?? "empty-state"}
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-default border border-border bg-surface px-6 py-12 text-center",
        className,
      )}
    >
      {icon != null && (
        <span className="text-muted opacity-40" aria-hidden="true">
          {icon}
        </span>
      )}

      <div className="flex flex-col items-center gap-1.5">
        <p className="text-sm font-semibold text-foreground">{title}</p>
        {description != null && (
          <p className="text-xs text-muted max-w-xs leading-relaxed">
            {description}
          </p>
        )}
      </div>

      {action != null && <div className="mt-1">{action}</div>}
    </div>
  );
}
