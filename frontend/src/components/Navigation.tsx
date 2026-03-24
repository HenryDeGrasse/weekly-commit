/**
 * Sidebar navigation listing all 5 weekly-commit routes.
 */
import { NavLink } from "react-router-dom";

interface NavItem {
  readonly to: string;
  readonly label: string;
  readonly icon: string;
}

const NAV_ITEMS: readonly NavItem[] = [
  { to: "/weekly/my-week", label: "My Week", icon: "📋" },
  { to: "/weekly/reconcile", label: "Reconcile", icon: "🔄" },
  { to: "/weekly/team", label: "Team Week", icon: "👥" },
  { to: "/weekly/tickets", label: "Tickets", icon: "🎫" },
  { to: "/weekly/rcdos", label: "RCDOs", icon: "🎯" },
];

export function Navigation() {
  return (
    <nav className="sidebar-nav" aria-label="Weekly Commit navigation">
      <ul role="list" style={{ listStyle: "none", padding: 0, margin: 0 }}>
        {NAV_ITEMS.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              className={({ isActive }) =>
                isActive ? "nav-link nav-link--active" : "nav-link"
              }
              aria-current={undefined}
            >
              <span className="nav-link__icon" aria-hidden="true">
                {item.icon}
              </span>
              <span className="nav-link__label">{item.label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
