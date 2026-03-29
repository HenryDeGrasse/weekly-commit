/**
 * PlanHeaderSkeleton — mimics the plan header Card layout shown on My Week
 * while the plan data is loading.
 */
import { Skeleton } from "../../ui/Skeleton.js";
import { Card, CardHeader } from "../../ui/Card.js";

export function PlanHeaderSkeleton() {
  return (
    <Card data-testid="plan-header-skeleton" aria-busy="true" aria-label="Loading plan">
      <CardHeader>
        {/* Badge + compliance badge placeholders */}
        <div className="flex items-center gap-2.5">
          <Skeleton className="h-5 w-16 rounded-sm" />
          <Skeleton className="h-5 w-20 rounded-sm" />
        </div>
        {/* Action button placeholders */}
        <div className="flex items-center gap-2">
          <Skeleton className="h-8 w-24 rounded-default" />
        </div>
      </CardHeader>
    </Card>
  );
}
