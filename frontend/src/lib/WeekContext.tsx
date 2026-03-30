/**
 * Shared week-selection context.
 *
 * AppShell provides the current selectedWeek ISO date (Monday of the chosen
 * week) and a setter. Any route (MyWeek, TeamWeek, etc.) can call
 * useSelectedWeek() to read or update the header calendar without prop-drilling.
 */
import { createContext, useContext, useState } from "react";

/** Returns the local date string (yyyy-MM-dd) for the Monday of the given date. */
function getWeekMondayLocal(date: Date): string {
  const d = new Date(date);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export interface WeekContextValue {
  /** ISO date string (yyyy-MM-dd) for the Monday of the selected week. */
  selectedWeek: string;
  setSelectedWeek: (week: string) => void;
}

export const WeekContext = createContext<WeekContextValue | null>(null);

/**
 * Returns the shared week selection.
 * When used outside AppShell (e.g. in tests), falls back to local state.
 */
export function useSelectedWeek(): WeekContextValue {
  const ctx = useContext(WeekContext);
  const [fallback, setFallback] = useState(() => getWeekMondayLocal(new Date()));
  if (ctx) return ctx;
  return { selectedWeek: fallback, setSelectedWeek: setFallback };
}
