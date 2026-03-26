import { forwardRef, type HTMLAttributes } from "react";
import { cn } from "../../lib/utils.js";

/* ── Card (container) ──────────────────────────────────────────────── */

const Card = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        "rounded-default border border-border bg-surface shadow-sm",
        className,
      )}
      {...props}
    />
  ),
);
Card.displayName = "Card";

/* ── CardHeader ────────────────────────────────────────────────────── */

const CardHeader = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        "flex items-center justify-between gap-3 px-4 py-3",
        className,
      )}
      {...props}
    />
  ),
);
CardHeader.displayName = "CardHeader";

/* ── CardTitle ─────────────────────────────────────────────────────── */

const CardTitle = forwardRef<
  HTMLHeadingElement,
  HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
  <h3
    ref={ref}
    className={cn("text-sm font-bold leading-none", className)}
    {...props}
  />
));
CardTitle.displayName = "CardTitle";

/* ── CardContent ───────────────────────────────────────────────────── */

const CardContent = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn("px-4 py-3 pt-0", className)} {...props} />
  ),
);
CardContent.displayName = "CardContent";

/* ── CardFooter ────────────────────────────────────────────────────── */

const CardFooter = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("flex items-center gap-2 px-4 py-3 pt-0", className)}
      {...props}
    />
  ),
);
CardFooter.displayName = "CardFooter";

export { Card, CardHeader, CardTitle, CardContent, CardFooter };
