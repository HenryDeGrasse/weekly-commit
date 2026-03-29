/**
 * CommitListSkeleton — shows 3 skeleton commit rows that mimic the
 * CommitList SortableItem layout (grip handle, rank badge, chess piece,
 * title bar, estimate badge, action buttons).
 */
import { Skeleton } from "../../ui/Skeleton.js";

function CommitRowSkeleton() {
  return (
    <li
      className="rounded-default border border-border bg-surface p-3"
      aria-hidden="true"
    >
      <div className="flex items-center gap-2">
        {/* Drag handle placeholder */}
        <Skeleton className="h-4 w-4 shrink-0" />
        {/* Rank badge */}
        <Skeleton className="h-6 w-6 rounded-full shrink-0" />
        {/* Chess piece */}
        <Skeleton className="h-5 w-5 shrink-0" />
        {/* Title */}
        <Skeleton className="h-4 flex-1" />
        {/* Estimate badge */}
        <Skeleton className="h-5 w-10 rounded-sm shrink-0" />
        {/* Edit / Delete buttons */}
        <Skeleton className="h-7 w-14 rounded-default shrink-0" />
        <Skeleton className="h-7 w-7 rounded-default shrink-0" />
      </div>
    </li>
  );
}

interface CommitListSkeletonProps {
  /** Number of skeleton rows to render (default: 3). */
  rows?: number;
}

export function CommitListSkeleton({ rows = 3 }: CommitListSkeletonProps) {
  return (
    <ol
      data-testid="commit-list-skeleton"
      aria-busy="true"
      aria-label="Loading commits"
      className="list-none m-0 p-0 flex flex-col gap-2"
    >
      {Array.from({ length: rows }, (_, i) => (
        <CommitRowSkeleton key={i} />
      ))}
    </ol>
  );
}
