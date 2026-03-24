/**
 * App shell header with user info from host context, week selector,
 * and notification bell.
 */
import { useHostContext } from "../host/HostProvider.js";

/** Returns the ISO date string (yyyy-MM-dd) for the Monday of the given date. */
function getWeekMonday(date: Date): string {
  const d = new Date(date);
  const day = d.getDay();
  // 0 = Sunday, shift to Monday
  const diff = (day === 0 ? -6 : 1 - day);
  d.setDate(d.getDate() + diff);
  return d.toISOString().slice(0, 10);
}

interface HeaderProps {
  /** Currently selected week (Monday ISO date). Controlled by parent. */
  readonly selectedWeek: string;
  onWeekChange(week: string): void;
}

export function Header({ selectedWeek, onWeekChange }: HeaderProps) {
  const { authenticatedUser } = useHostContext();

  function handleWeekChange(e: React.ChangeEvent<HTMLInputElement>) {
    // The date input gives us the selected date; snap to Monday
    if (e.target.value) {
      onWeekChange(getWeekMonday(new Date(e.target.value)));
    }
  }

  return (
    <header className="app-header" role="banner">
      <div className="app-header__brand">
        <span className="app-header__logo" aria-hidden="true">📅</span>
        <span className="app-header__title">Weekly Commit</span>
      </div>

      <div className="app-header__controls">
        <label className="week-selector" htmlFor="week-selector-input">
          <span className="visually-hidden">Select week</span>
          <input
            id="week-selector-input"
            type="date"
            value={selectedWeek}
            onChange={handleWeekChange}
            aria-label="Select week"
          />
        </label>

        <button
          className="notification-bell"
          type="button"
          aria-label="Notifications"
        >
          <span aria-hidden="true">🔔</span>
        </button>
      </div>

      <div className="app-header__user" aria-label="Current user">
        {authenticatedUser.avatarUrl !== undefined && (
          <img
            src={authenticatedUser.avatarUrl}
            alt={authenticatedUser.displayName}
            className="user-avatar"
          />
        )}
        <span className="user-display-name">{authenticatedUser.displayName}</span>
      </div>
    </header>
  );
}

export { getWeekMonday };
