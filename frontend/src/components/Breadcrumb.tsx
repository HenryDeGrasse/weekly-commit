/**
 * Route-aware breadcrumb derived from the current URL path.
 */
import { Link, useLocation } from "react-router-dom";
import { ChevronRight } from "lucide-react";

/** Map path segments to human-readable labels. */
const SEGMENT_LABELS: Record<string, string> = {
  weekly: "Weekly Commit",
  "my-week": "My Week",
  reconcile: "Reconcile",
  team: "Team Week",
  tickets: "Tickets",
  rcdos: "RCDOs",
};

interface Crumb {
  label: string;
  to: string;
}

export function Breadcrumb() {
  const { pathname } = useLocation();
  const segments = pathname.split("/").filter(Boolean);

  const crumbs: Crumb[] = [];
  let path = "";
  for (const seg of segments) {
    path += `/${seg}`;
    const label = SEGMENT_LABELS[seg];
    if (label) {
      crumbs.push({ label, to: path });
    }
    // Skip dynamic segments (UUIDs, plan IDs, etc.) — they don't get crumbs
  }

  if (crumbs.length <= 1) return null;

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-sm text-muted">
      {crumbs.map((crumb, i) => {
        const isLast = i === crumbs.length - 1;
        return (
          <span key={crumb.to} className="flex items-center gap-1">
            {i > 0 && <ChevronRight className="h-3.5 w-3.5 text-border" />}
            {isLast ? (
              <span className="font-medium text-foreground">{crumb.label}</span>
            ) : (
              <Link
                to={crumb.to}
                className="hover:text-primary transition-colors"
              >
                {crumb.label}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
