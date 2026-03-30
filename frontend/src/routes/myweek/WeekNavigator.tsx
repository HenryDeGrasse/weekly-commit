/**
 * Week selector with prev/next/today buttons and week label.
 */
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "../../components/ui/Button.js";

interface WeekNavigatorProps {
  readonly weekLabel: string;
  readonly weekOffset: number;
  readonly onPrevWeek: () => void;
  readonly onNextWeek: () => void;
  readonly onCurrentWeek: () => void;
}

export function WeekNavigator({ weekLabel, weekOffset, onPrevWeek, onNextWeek, onCurrentWeek }: WeekNavigatorProps) {
  return (
    <div data-testid="week-selector" className="flex items-center gap-2 flex-wrap">
      <Button variant="secondary" size="sm" onClick={onPrevWeek} aria-label="Previous week" data-testid="prev-week-btn">
        <ChevronLeft className="h-4 w-4" />
      </Button>
      <span data-testid="week-label" className="font-semibold text-sm min-w-[14rem] text-center">
        {weekLabel}
      </span>
      <Button variant="secondary" size="sm" onClick={onNextWeek} aria-label="Next week" data-testid="next-week-btn">
        <ChevronRight className="h-4 w-4" />
      </Button>
      {weekOffset !== 0 && (
        <Button variant="secondary" size="sm" onClick={onCurrentWeek} data-testid="current-week-btn">
          Today
        </Button>
      )}
    </div>
  );
}
