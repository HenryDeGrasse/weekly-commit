import { type HTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../../lib/utils.js";

/**
 * Badge variants — monochrome palette with subtle tonal shifts for
 * semantic meaning. Uses px-based rounding (not rounded-full) so badges
 * pick up the global radius token. Plan-state badges use gray-scale tints
 * to stay within the neutral design direction.
 */
const badgeVariants = cva(
  "inline-flex items-center rounded-sm border px-2.5 py-0.5 text-xs font-semibold tracking-wide transition-colors",
  {
    variants: {
      variant: {
        default: "border-border bg-muted-bg text-foreground",
        primary: "border-foreground/20 bg-foreground/5 text-foreground",
        success: "border-foreground/25 bg-foreground/5 text-foreground",
        warning: "border-foreground/15 bg-foreground/[0.03] text-muted",
        danger: "border-foreground/30 bg-foreground/8 text-foreground font-bold",
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
