/**
 * TeamWeekSkeleton — overview card grid skeleton that mimics the
 * OverviewSection stat boxes shown on Team Week while data is loading.
 */
import { Skeleton } from "../../ui/Skeleton.js";
import { Card, CardContent, CardHeader } from "../../ui/Card.js";

function StatBoxSkeleton() {
  return (
    <Card aria-hidden="true">
      <CardContent className="py-4 flex flex-col items-center gap-2">
        <Skeleton className="h-8 w-12" />
        <Skeleton className="h-3.5 w-20" />
      </CardContent>
    </Card>
  );
}

export function TeamWeekSkeleton() {
  return (
    <div
      data-testid="team-week-skeleton"
      aria-busy="true"
      aria-label="Loading team data"
      className="flex flex-col gap-4"
    >
      {/* Stat cards row */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
        {Array.from({ length: 4 }, (_, i) => (
          <StatBoxSkeleton key={i} />
        ))}
      </div>

      {/* Tab navigation placeholder */}
      <div className="flex gap-1 border-b border-border pb-1">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} className="h-8 w-20 rounded-sm" />
        ))}
      </div>

      {/* Table body placeholder */}
      <Card>
        <CardHeader>
          <Skeleton className="h-4 w-32" />
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="flex items-center gap-3">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-4 flex-1" />
              <Skeleton className="h-5 w-16 rounded-sm" />
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
