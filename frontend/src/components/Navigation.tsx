/**
 * Sidebar navigation listing weekly-commit routes.
 * Filters entries based on the caller's role via feature flags:
 *   - "Team Week" requires managerReviewEnabled (manager/admin only).
 *   - "Admin" requires rcdoAdminEnabled (manager/admin only).
 * Supports collapsed (icon-only) mode via prop.
 */
import { useMemo } from "react";
import { NavLink } from "react-router-dom";
import {
  ClipboardList,
  RefreshCw,
  Users,
  Ticket,
  Target,
  BarChart3,
  Settings,
  type LucideIcon,
} from "lucide-react";
import { cn } from "../lib/utils.js";
import { Tooltip } from "./ui/Tooltip.js";
import { useHostContext } from "../host/HostProvider.js";

interface NavItem {
  readonly to: string;
  readonly label: string;
  readonly icon: LucideIcon;
  /** When set, the item is only shown if this feature flag is truthy. */
  readonly requiredFlag?: string;
}

const NAV_ITEMS: readonly NavItem[] = [
  { to: "/weekly/my-week", label: "My Week", icon: ClipboardList },
  { to: "/weekly/reconcile", label: "Reconcile", icon: RefreshCw },
  { to: "/weekly/team", label: "Team Week", icon: Users, requiredFlag: "managerReviewEnabled" },
  { to: "/weekly/tickets", label: "Tickets", icon: Ticket },
  { to: "/weekly/rcdos", label: "RCDOs", icon: Target },
  { to: "/weekly/reports", label: "Reports", icon: BarChart3 },
  { to: "/weekly/admin", label: "Admin", icon: Settings, requiredFlag: "rcdoAdminEnabled" },
];

interface NavigationProps {
  collapsed?: boolean;
}

export function Navigation({ collapsed = false }: NavigationProps) {
  const { featureFlags } = useHostContext();

  const visibleItems = useMemo(
    () =>
      NAV_ITEMS.filter(
        (item) =>
          !item.requiredFlag ||
          Boolean((featureFlags as Record<string, unknown>)[item.requiredFlag]),
      ),
    [featureFlags],
  );

  return (
    <nav className="flex flex-col gap-1" aria-label="Weekly Commit navigation">
      <ul role="list" className="flex flex-col gap-0.5">
        {visibleItems.map((item) => {
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
