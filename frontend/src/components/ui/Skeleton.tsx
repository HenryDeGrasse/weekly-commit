import { type HTMLAttributes } from "react";
import { cn } from "../../lib/utils.js";

function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "animate-pulse rounded-default bg-muted/20",
        className,
      )}
      {...props}
    />
  );
}

export { Skeleton };
