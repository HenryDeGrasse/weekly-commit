/**
 * Simple CSS-only tooltip using group hover.
 * For richer behavior, swap for @floating-ui later.
 */
import { type ReactNode } from "react";
import { cn } from "../../lib/utils.js";

interface TooltipProps {
  content: string;
  children: ReactNode;
  side?: "top" | "bottom";
  className?: string;
}

function Tooltip({ content, children, side = "top", className }: TooltipProps) {
  return (
    <span className={cn("group relative inline-flex", className)}>
      {children}
      <span
        role="tooltip"
        className={cn(
          "pointer-events-none absolute left-1/2 z-50 -translate-x-1/2 whitespace-nowrap rounded-sm bg-foreground px-2 py-1 text-xs text-surface opacity-0 shadow-md transition-opacity group-hover:opacity-100",
          side === "top" && "bottom-full mb-1.5",
          side === "bottom" && "top-full mt-1.5",
        )}
      >
        {content}
      </span>
    </span>
  );
}

export { Tooltip };
