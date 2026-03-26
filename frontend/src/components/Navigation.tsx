/**
 * Sidebar navigation listing all 5 weekly-commit routes.
 * Supports collapsed (icon-only) mode via prop.
 */
import { NavLink } from "react-router-dom";
import {
  ClipboardList,
  RefreshCw,
  Users,
  Ticket,
  Target,
  type LucideIcon,
} from "lucide-react";
import { cn } from "../lib/utils.js";
import { Tooltip } from "./ui/Tooltip.js";

interface NavItem {
  readonly to: string;
  readonly label: string;
  readonly icon: LucideIcon;
}

const NAV_ITEMS: readonly NavItem[] = [
  { to: "/weekly/my-week", label: "My Week", icon: ClipboardList },
  { to: "/weekly/reconcile", label: "Reconcile", icon: RefreshCw },
  { to: "/weekly/team", label: "Team Week", icon: Users },
  { to: "/weekly/tickets", label: "Tickets", icon: Ticket },
  { to: "/weekly/rcdos", label: "RCDOs", icon: Target },
];

interface NavigationProps {
  collapsed?: boolean;
}

export function Navigation({ collapsed = false }: NavigationProps) {
  return (
    <nav className="flex flex-col gap-1" aria-label="Weekly Commit navigation">
      <ul role="list" className="flex flex-col gap-0.5">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const link = (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  "group flex items-center gap-3 rounded-default px-3 py-2 text-sm font-medium transition-colors",
                  "hover:bg-primary/8 hover:text-primary",
                  isActive
                    ? "bg-primary/10 text-primary font-semibold"
                    : "text-muted",
                  collapsed && "justify-center px-2",
                )
              }
              aria-current={undefined}
            >
              <Icon
                className="h-[18px] w-[18px] shrink-0"
                aria-hidden="true"
              />
              {!collapsed && (
                <span className="truncate">{item.label}</span>
              )}
            </NavLink>
          );

          return (
            <li key={item.to}>
              {collapsed ? (
                <Tooltip content={item.label} side="bottom">
                  {link}
                </Tooltip>
              ) : (
                link
              )}
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
