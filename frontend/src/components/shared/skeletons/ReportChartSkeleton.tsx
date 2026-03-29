/**
 * ReportChartSkeleton — generic chart card placeholder that mimics the
 * structure of a report chart (CardHeader with title + chart body area).
 */
import { Skeleton } from "../../ui/Skeleton.js";
import { Card, CardContent, CardHeader } from "../../ui/Card.js";

interface ReportChartSkeletonProps {
  /** Height of the chart body area in px (default: 180). */
  chartHeight?: number;
}

export function ReportChartSkeleton({ chartHeight = 180 }: ReportChartSkeletonProps) {
  return (
    <Card data-testid="report-chart-skeleton" aria-busy="true" aria-hidden="true">
      <CardHeader>
        {/* Chart title placeholder */}
        <div className="flex items-center gap-2">
          <Skeleton className="h-4 w-4 rounded-sm" />
          <Skeleton className="h-4 w-40" />
        </div>
        {/* Optional badge/stat placeholder */}
        <Skeleton className="h-5 w-16 rounded-sm" />
      </CardHeader>
      <CardContent>
        {/* Chart body area */}
        <Skeleton className="w-full rounded-default" style={{ height: chartHeight }} />
        {/* X-axis labels */}
        <div className="flex gap-1 mt-2">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-3 flex-1" />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
