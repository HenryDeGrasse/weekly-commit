/**
 * ByRcdoSection — tree/grouped view of planned points by RCDO branch.
 *
 * Shows Rally Cry → Defining Objective → Outcome with:
 *   - Planned points and commit count per branch
 *   - Coverage gap highlighting for RCDO nodes with no commits
 *   - Planned vs achieved bar per branch (when outcome data is available)
 */
import type { RcdoRollupEntry, MemberWeekView } from "../../api/teamTypes.js";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";

export interface ByRcdoSectionProps {
  readonly rcdoRollup: RcdoRollupEntry[];
  readonly rcdoTree: RcdoTreeNode[];
  readonly memberViews: MemberWeekView[];
}

interface RollupMap {
  [rcdoNodeId: string]: RcdoRollupEntry;
}

interface PointsMap {
  [rcdoNodeId: string]: number;
}

interface RcdoRowProps {
  readonly node: RcdoTreeNode;
  readonly rollupMap: RollupMap;
  readonly achievedPointsMap: PointsMap;
  readonly depth: number;
}

function RcdoRow({ node, rollupMap, achievedPointsMap, depth }: RcdoRowProps) {
  const entry = rollupMap[node.id];
  const hasCoverage = entry !== undefined && entry.commitCount > 0;

  // Aggregate rollup across this subtree for totals at higher levels
  function subtreePoints(n: RcdoTreeNode): number {
    const own = rollupMap[n.id]?.totalPoints ?? 0;
    return own + n.children.reduce((sum, child) => sum + subtreePoints(child), 0);
  }

  function subtreeCommits(n: RcdoTreeNode): number {
    const own = rollupMap[n.id]?.commitCount ?? 0;
    return own + n.children.reduce((sum, child) => sum + subtreeCommits(child), 0);
  }

  function subtreeAchievedPoints(n: RcdoTreeNode): number {
    const own = achievedPointsMap[n.id] ?? 0;
    return own + n.children.reduce((sum, child) => sum + subtreeAchievedPoints(child), 0);
  }

  const totalPoints = subtreePoints(node);
  const totalCommits = subtreeCommits(node);
  const achievedPoints = subtreeAchievedPoints(node);
  const achievedPct = totalPoints > 0 ? Math.min((achievedPoints / totalPoints) * 100, 100) : 0;

  const TYPE_COLORS = {
    RALLY_CRY: { bg: "#ede9fe", color: "#5b21b6" },
    DEFINING_OBJECTIVE: { bg: "#dbeafe", color: "#1e40af" },
    OUTCOME: { bg: "#f0fdf4", color: "#166534" },
  };
  const typeStyle = TYPE_COLORS[node.nodeType] ?? { bg: "#f3f4f6", color: "#374151" };

  return (
    <>
      <tr
        data-testid={`rcdo-row-${node.id}`}
        style={{
          borderBottom: "1px solid var(--color-border)",
          opacity: node.status === "ARCHIVED" ? 0.5 : 1,
        }}
      >
        <td style={{ padding: "0.5rem 0.75rem", paddingLeft: `${0.75 + depth * 1.25}rem` }}>
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <span
              style={{
                fontSize: "0.65rem",
                fontWeight: 700,
                padding: "1px 6px",
                borderRadius: "999px",
                background: typeStyle.bg,
                color: typeStyle.color,
                textTransform: "uppercase",
                letterSpacing: "0.04em",
                flexShrink: 0,
              }}
            >
              {node.nodeType === "RALLY_CRY" ? "RC" : node.nodeType === "DEFINING_OBJECTIVE" ? "DO" : "OUT"}
            </span>
            <span
              style={{
                fontWeight: depth === 0 ? 700 : depth === 1 ? 600 : 400,
                fontSize: "0.875rem",
                color: !hasCoverage && totalCommits === 0 ? "var(--color-text-muted)" : "var(--color-text)",
              }}
            >
              {node.title}
            </span>
            {!hasCoverage && totalCommits === 0 && node.status === "ACTIVE" && (
              <span
                data-testid={`coverage-gap-${node.id}`}
                title="No commits targeting this RCDO"
                style={{
                  fontSize: "0.65rem",
                  padding: "1px 6px",
                  borderRadius: "999px",
                  background: "#fee2e2",
                  color: "#991b1b",
                  fontWeight: 600,
                }}
              >
                No coverage
              </span>
            )}
          </div>
        </td>
        <td style={{ padding: "0.5rem 0.75rem", textAlign: "right", fontSize: "0.8rem" }} data-testid={`rcdo-commits-${node.id}`}>
          {totalCommits > 0 ? totalCommits : <span style={{ color: "var(--color-text-muted)" }}>0</span>}
        </td>
        <td style={{ padding: "0.5rem 0.75rem", textAlign: "right" }}>
          <span data-testid={`rcdo-points-${node.id}`} style={{ fontWeight: 600, fontSize: "0.875rem" }}>
            {totalPoints > 0 ? totalPoints : <span style={{ color: "var(--color-text-muted)" }}>0</span>}
          </span>
        </td>
        <td style={{ padding: "0.5rem 0.75rem", textAlign: "right" }}>
          <span data-testid={`rcdo-achieved-${node.id}`} style={{ fontWeight: 600, fontSize: "0.875rem", color: achievedPoints > 0 ? "var(--color-success)" : "var(--color-text-muted)" }}>
            {achievedPoints > 0 ? achievedPoints : 0}
          </span>
        </td>
        <td style={{ padding: "0.5rem 0.75rem", minWidth: "140px" }}>
          <div
            data-testid={`rcdo-progress-${node.id}`}
            aria-label={`${node.title} planned versus achieved`}
            style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}
          >
            <div
              style={{
                height: "8px",
                borderRadius: "999px",
                background: "#e5e7eb",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${achievedPct}%`,
                  height: "100%",
                  background: achievedPoints > 0 ? "var(--color-success)" : "#cbd5e1",
                  transition: "width 0.2s ease",
                }}
              />
            </div>
            <span style={{ fontSize: "0.7rem", color: "var(--color-text-muted)", textAlign: "right" }}>
              {totalPoints > 0 ? `${Math.round(achievedPct)}%` : "—"}
            </span>
          </div>
        </td>
      </tr>
      {node.children.map((child) => (
        <RcdoRow
          key={child.id}
          node={child}
          rollupMap={rollupMap}
          achievedPointsMap={achievedPointsMap}
          depth={depth + 1}
        />
      ))}
    </>
  );
}

export function ByRcdoSection({
  rcdoRollup,
  rcdoTree,
  memberViews,
}: ByRcdoSectionProps) {
  const rollupMap: RollupMap = {};
  for (const entry of rcdoRollup) {
    rollupMap[entry.rcdoNodeId] = entry;
  }

  const achievedPointsMap: PointsMap = {};
  for (const member of memberViews) {
    for (const commit of member.commits) {
      if (
        commit.rcdoNodeId &&
        commit.outcome === "ACHIEVED" &&
        commit.estimatePoints != null
      ) {
        achievedPointsMap[commit.rcdoNodeId] =
          (achievedPointsMap[commit.rcdoNodeId] ?? 0) + commit.estimatePoints;
      }
    }
  }

  if (rcdoTree.length === 0) {
    return (
      <section aria-labelledby="by-rcdo-heading" data-testid="by-rcdo-section">
        <h3 id="by-rcdo-heading" style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}>
          By RCDO
        </h3>
        <p style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}>
          No RCDO hierarchy configured.
        </p>
      </section>
    );
  }

  return (
    <section aria-labelledby="by-rcdo-heading" data-testid="by-rcdo-section">
      <h3 id="by-rcdo-heading" style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}>
        By RCDO
      </h3>
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          overflow: "hidden",
        }}
      >
        <table
          data-testid="by-rcdo-table"
          style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}
          aria-label="RCDO rollup"
        >
          <thead>
            <tr style={{ background: "var(--color-background)", borderBottom: "1px solid var(--color-border)" }}>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                RCDO Node
              </th>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "right", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Commits
              </th>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "right", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Pts Planned
              </th>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "right", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Pts Achieved
              </th>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Planned vs Achieved
              </th>
            </tr>
          </thead>
          <tbody>
            {rcdoTree.map((root) => (
              <RcdoRow
                key={root.id}
                node={root}
                rollupMap={rollupMap}
                achievedPointsMap={achievedPointsMap}
                depth={0}
              />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
