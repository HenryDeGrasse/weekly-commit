import { type HTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../../lib/utils.js";

/**
 * Badge variants — semantic color tokens for status, monochrome for plan state.
 * Status variants (success/warning/danger/info) use WCAG AA-validated muted
 * palette from the design token system. Plan-state badges remain monochrome
 * to keep chrome neutral. Uses px-based rounding so badges pick up the global
 * radius token.
 */
const badgeVariants = cva(
  "inline-flex items-center rounded-sm border px-2.5 py-0.5 text-xs font-semibold tracking-wide transition-colors",
  {
    variants: {
      variant: {
        default: "border-border bg-muted-bg text-foreground",
        primary: "border-foreground/20 bg-foreground/5 text-foreground",
        /* Semantic status variants — use design-token bg/border/text */
        success: "border-success-border bg-success-bg text-success",
        warning: "border-warning-border bg-warning-bg text-warning",
        danger: "border-danger-border bg-danger-bg text-danger font-bold",
        info: "border-info-border bg-info-bg text-info",
        /* Plan-state badges: monochrome tonal variations */
        draft: "border-border bg-muted-bg text-muted",
        locked: "border-foreground/20 bg-foreground/5 text-foreground",
        reconciling: "border-foreground/15 bg-foreground/[0.03] text-muted",
        reconciled: "border-foreground/25 bg-foreground/5 text-foreground",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

export interface BadgeProps
  extends HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ variant, className }))} {...props} />
  );
}

export { Badge, badgeVariants };
