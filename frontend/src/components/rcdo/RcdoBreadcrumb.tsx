/**
 * Breadcrumb showing the full path from root to the selected RCDO node.
 * Renders: Rally Cry › Defining Objective › Outcome
 */
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";

interface RcdoBreadcrumbProps {
  /** Ordered path from root to (and including) the selected node. */
  readonly path: RcdoTreeNode[];
  /** Called when the user clicks an ancestor node in the breadcrumb. */
  readonly onNavigate?: (id: string) => void;
}

const breadcrumbStyle: React.CSSProperties = {
  display: "flex",
  alignItems: "center",
  flexWrap: "wrap",
  gap: "0.25rem",
  fontSize: "0.875rem",
  color: "var(--color-text-muted)",
  minHeight: "1.5rem",
};

const separatorStyle: React.CSSProperties = {
  margin: "0 0.125rem",
  userSelect: "none",
};

const ancestorButtonStyle: React.CSSProperties = {
  background: "none",
  border: "none",
  color: "var(--color-primary)",
  cursor: "pointer",
  padding: 0,
  fontSize: "inherit",
  textDecoration: "underline",
  textUnderlineOffset: "2px",
};

const currentNodeStyle: React.CSSProperties = {
  color: "var(--color-text)",
  fontWeight: 600,
};

export function RcdoBreadcrumb({ path, onNavigate }: RcdoBreadcrumbProps) {
  if (path.length === 0) {
    return (
      <nav aria-label="RCDO path" style={breadcrumbStyle}>
        <span>RCDO Hierarchy</span>
      </nav>
    );
  }

  return (
    <nav aria-label="RCDO path" style={breadcrumbStyle}>
      <ol
        style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", alignItems: "center", flexWrap: "wrap", gap: "0.25rem" }}
      >
        {path.map((node, index) => {
          const isLast = index === path.length - 1;
          return (
            <li key={node.id} style={{ display: "flex", alignItems: "center", gap: "0.25rem" }}>
              {index > 0 && (
                <span aria-hidden="true" style={separatorStyle}>
                  ›
                </span>
              )}
              {isLast ? (
                <span style={currentNodeStyle} aria-current="page">
                  {node.title}
                </span>
              ) : (
                <button
                  onClick={() => onNavigate?.(node.id)}
                  style={ancestorButtonStyle}
                  aria-label={`Navigate to ${node.title}`}
                >
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
