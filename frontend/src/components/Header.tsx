/**
 * App shell header with brand, week selector, notification bell,
 * and user avatar / role indicator.
 */
import { useHostContext } from "../host/HostProvider.js";
import {
  Calendar,
  Bell,
  ChevronLeft,
  ChevronRight,
  PanelLeftClose,
  PanelLeft,
} from "lucide-react";
import { Button } from "./ui/Button.js";
import { Tooltip } from "./ui/Tooltip.js";
import { Breadcrumb } from "./Breadcrumb.js";

/** Returns the ISO date string (yyyy-MM-dd) for the Monday of the given date. */
function getWeekMonday(date: Date): string {
  const d = new Date(date);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  return d.toISOString().slice(0, 10);
}

/** Format a Monday ISO date as a short label. */
function formatWeekShort(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
}

interface HeaderProps {
  readonly selectedWeek: string;
  onWeekChange(week: string): void;
  collapsed?: boolean;
  onToggleSidebar?: () => void;
}

export function Header({
  selectedWeek,
  onWeekChange,
  collapsed,
  onToggleSidebar,
}: HeaderProps) {
  const { authenticatedUser } = useHostContext();

  function handleWeekChange(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.value) {
      onWeekChange(getWeekMonday(new Date(e.target.value)));
    }
  }

  function prevWeek() {
    const d = new Date(`${selectedWeek}T00:00:00`);
    d.setDate(d.getDate() - 7);
    onWeekChange(d.toISOString().slice(0, 10));
  }

  function nextWeek() {
    const d = new Date(`${selectedWeek}T00:00:00`);
    d.setDate(d.getDate() + 7);
    onWeekChange(d.toISOString().slice(0, 10));
  }

  // User initials fallback
  const initials = authenticatedUser.displayName
    .split(" ")
    .map((n) => n[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  return (
    <header
      className="flex h-14 items-center justify-between border-b border-border bg-surface px-4 gap-4"
      role="banner"
    >
      {/* Left: sidebar toggle + brand + breadcrumb */}
      <div className="flex items-center gap-3 min-w-0">
        {onToggleSidebar && (
          <Tooltip content={collapsed ? "Expand sidebar" : "Collapse sidebar"}>
            <Button
              variant="ghost"
              size="icon"
              onClick={onToggleSidebar}
              aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
              className="shrink-0"
            >
              {collapsed ? (
                <PanelLeft className="h-4 w-4" />
              ) : (
                <PanelLeftClose className="h-4 w-4" />
              )}
            </Button>
          </Tooltip>
        )}

        <div className="flex items-center gap-2 shrink-0">
          <Calendar className="h-5 w-5 text-primary" aria-hidden="true" />
          <span className="text-sm font-bold tracking-tight hidden sm:inline">
            Weekly Commit
          </span>
        </div>

        <div className="hidden md:block">
          <Breadcrumb />
        </div>
      </div>

      {/* Center: week selector */}
      <div className="flex items-center gap-1.5">
        <Button
          variant="ghost"
          size="icon"
          onClick={prevWeek}
          aria-label="Previous week"
          className="h-7 w-7"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>

        <label className="relative cursor-pointer" htmlFor="week-selector-input">
          <span className="visually-hidden">Select week</span>
          <span className="flex items-center gap-1.5 rounded-default border border-border bg-background px-2.5 py-1 text-xs font-semibold hover:border-primary/50 transition-colors">
            <Calendar className="h-3.5 w-3.5 text-muted" />
            {formatWeekShort(selectedWeek)}
          </span>
          <input
            id="week-selector-input"
            type="date"
            value={selectedWeek}
            onChange={handleWeekChange}
            aria-label="Select week"
            className="absolute inset-0 cursor-pointer opacity-0"
          />
        </label>

        <Button
          variant="ghost"
          size="icon"
          onClick={nextWeek}
          aria-label="Next week"
          className="h-7 w-7"
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>

      {/* Right: notifications + user */}
      <div className="flex items-center gap-2">
        <Tooltip content="Notifications">
          <Button
            variant="ghost"
            size="icon"
            aria-label="Notifications"
            className="relative"
          >
            <Bell className="h-4 w-4" />
            {/* Notification badge dot — placeholder for real count */}
            <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-danger" />
          </Button>
        </Tooltip>

        <div className="flex items-center gap-2 pl-2 border-l border-border" aria-label="Current user">
          {authenticatedUser.avatarUrl ? (
            <img
              src={authenticatedUser.avatarUrl}
              alt={authenticatedUser.displayName}
              className="h-7 w-7 rounded-full object-cover ring-2 ring-border"
            />
          ) : (
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary ring-2 ring-border">
              {initials}
            </div>
          )}
          <span className="hidden lg:inline text-sm font-medium truncate max-w-[120px]">
            {authenticatedUser.displayName}
          </span>
        </div>
      </div>
    </header>
  );
}

export { getWeekMonday };
