/**
 * Breadcrumb showing the full path from root to the selected RCDO node.
 */
import { ChevronRight } from "lucide-react";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";

interface RcdoBreadcrumbProps {
  readonly path: RcdoTreeNode[];
  readonly onNavigate?: (id: string) => void;
}

export function RcdoBreadcrumb({ path, onNavigate }: RcdoBreadcrumbProps) {
  if (path.length === 0) {
    return (
      <nav aria-label="RCDO path" className="flex items-center flex-wrap gap-1 text-sm text-muted min-h-6">
        <span>RCDO Hierarchy</span>
      </nav>
    );
  }

  return (
    <nav aria-label="RCDO path" className="flex items-center flex-wrap gap-1 text-sm text-muted min-h-6">
      <ol className="list-none m-0 p-0 flex items-center flex-wrap gap-1">
        {path.map((node, index) => {
          const isLast = index === path.length - 1;
          return (
            <li key={node.id} className="flex items-center gap-1">
              {index > 0 && <ChevronRight className="h-3.5 w-3.5 text-border" aria-hidden="true" />}
              {isLast ? (
                <span className="font-semibold text-foreground" aria-current="page">{node.title}</span>
              ) : (
                <button onClick={() => onNavigate?.(node.id)} aria-label={`Navigate to ${node.title}`} className="bg-transparent border-none p-0 text-primary cursor-pointer text-sm underline underline-offset-2 hover:text-primary/80">
                  {node.title}
                </button>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
