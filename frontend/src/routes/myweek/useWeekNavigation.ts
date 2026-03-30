/**
 * Week navigation state — offset, computed start date, and label.
 */
import { useState, useCallback } from "react";

export function getWeekStartDate(offsetWeeks = 0): string {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const daysToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const monday = new Date(now);
  monday.setDate(now.getDate() + daysToMonday + offsetWeeks * 7);
  // Use local date parts — toISOString() converts to UTC and can shift the day.
  const yyyy = monday.getFullYear();
  const mm = String(monday.getMonth() + 1).padStart(2, '0');
  const dd = String(monday.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export function formatWeekLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  return `Week of ${d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}`;
}

export function useWeekNavigation() {
  const [weekOffset, setWeekOffset] = useState(0);
  const weekStartDate = getWeekStartDate(weekOffset);

  const goToPreviousWeek = useCallback(() => setWeekOffset((o) => o - 1), []);
  const goToNextWeek = useCallback(() => setWeekOffset((o) => o + 1), []);
  const goToCurrentWeek = useCallback(() => setWeekOffset(0), []);

  return {
    weekOffset,
    weekStartDate,
    weekLabel: formatWeekLabel(weekStartDate),
    goToPreviousWeek,
    goToNextWeek,
    goToCurrentWeek,
  };
}
