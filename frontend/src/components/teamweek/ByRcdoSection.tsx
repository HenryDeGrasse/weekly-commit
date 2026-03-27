/**
 * ByRcdoSection — tree/grouped view of planned points by RCDO branch.
 */
import { cn } from "../../lib/utils.js";
import type { RcdoRollupEntry, MemberWeekView } from "../../api/teamTypes.js";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";

export interface ByRcdoSectionProps {
  readonly rcdoRollup: RcdoRollupEntry[];
  readonly rcdoTree: RcdoTreeNode[];
  readonly memberViews: MemberWeekView[];
}

interface RollupMap { [rcdoNodeId: string]: RcdoRollupEntry; }
interface PointsMap { [rcdoNodeId: string]: number; }

const TYPE_BADGE: Record<string, string> = {
  RALLY_CRY: "bg-foreground text-background",
  DEFINING_OBJECTIVE: "bg-neutral-400 text-background",
  OUTCOME: "bg-neutral-200 text-neutral-700",
};
const TYPE_ABBR: Record<string, string> = { RALLY_CRY: "RC", DEFINING_OBJECTIVE: "DO", OUTCOME: "OUT" };

const thCls = "px-3 py-2 text-[0.7rem] font-semibold uppercase tracking-wider border-b border-border";

function RcdoRow({ node, rollupMap, achievedPointsMap, depth }: { node: RcdoTreeNode; rollupMap: RollupMap; achievedPointsMap: PointsMap; depth: number }) {
  function subtreePoints(n: RcdoTreeNode): number { return (rollupMap[n.id]?.totalPoints ?? 0) + n.children.reduce((s, c) => s + subtreePoints(c), 0); }
  function subtreeCommits(n: RcdoTreeNode): number { return (rollupMap[n.id]?.commitCount ?? 0) + n.children.reduce((s, c) => s + subtreeCommits(c), 0); }
  function subtreeAchieved(n: RcdoTreeNode): number { return (achievedPointsMap[n.id] ?? 0) + n.children.reduce((s, c) => s + subtreeAchieved(c), 0); }

  const totalPoints = subtreePoints(node);
  const totalCommits = subtreeCommits(node);
  const achievedPoints = subtreeAchieved(node);
  const achievedPct = totalPoints > 0 ? Math.min((achievedPoints / totalPoints) * 100, 100) : 0;
  const hasCoverage = (rollupMap[node.id]?.commitCount ?? 0) > 0;

  return (
    <>
      <tr data-testid={`rcdo-row-${node.id}`} className={cn("border-b border-border", node.status === "ARCHIVED" && "opacity-50")}>
        <td className="px-3 py-2" style={{ paddingLeft: `${0.75 + depth * 1.25}rem` }}>
          <div className="flex items-center gap-2">
            <span className={cn("text-[0.6rem] font-bold px-1.5 py-px rounded-full uppercase tracking-wider shrink-0", TYPE_BADGE[node.nodeType] ?? "bg-neutral-100 text-neutral-600")}>
              {TYPE_ABBR[node.nodeType] ?? node.nodeType}
            </span>
            <span className={cn("text-sm", depth === 0 ? "font-bold" : depth === 1 ? "font-semibold" : "font-normal", !hasCoverage && totalCommits === 0 && "text-muted")}>
              {node.title}
            </span>
            {!hasCoverage && totalCommits === 0 && node.status === "ACTIVE" && (
              <span data-testid={`coverage-gap-${node.id}`} className="text-[0.6rem] px-1.5 py-px rounded-full bg-foreground/10 text-foreground font-bold underline">No coverage</span>
            )}
          </div>
        </td>
        <td className="px-3 py-2 text-right text-sm" data-testid={`rcdo-commits-${node.id}`}>
          {totalCommits > 0 ? totalCommits : <span className="text-muted">0</span>}
        </td>
        <td className="px-3 py-2 text-right font-semibold text-sm" data-testid={`rcdo-points-${node.id}`}>
          {totalPoints > 0 ? totalPoints : <span className="text-muted font-normal">0</span>}
        </td>
        <td className="px-3 py-2 text-right" data-testid={`rcdo-achieved-${node.id}`}>
          <span className={cn("font-semibold text-sm", achievedPoints > 0 ? "text-foreground" : "text-muted")}>
            {achievedPoints > 0 ? achievedPoints : 0}
          </span>
        </td>
        <td className="px-3 py-2 min-w-[140px]">
          <div data-testid={`rcdo-progress-${node.id}`} aria-label={`${node.title} planned versus achieved`} className="flex flex-col gap-1">
            <div className="h-2 rounded-full bg-neutral-200 overflow-hidden">
              <div className={cn("h-full transition-[width] duration-200", achievedPoints > 0 ? "bg-foreground" : "bg-neutral-300")} style={{ width: `${achievedPct}%` }} />
            </div>
            <span className="text-[0.65rem] text-muted text-right">{totalPoints > 0 ? `${Math.round(achievedPct)}%` : "—"}</span>
          </div>
        </td>
      </tr>
      {node.children.map((child) => <RcdoRow key={child.id} node={child} rollupMap={rollupMap} achievedPointsMap={achievedPointsMap} depth={depth + 1} />)}
    </>
  );
}

export function ByRcdoSection({ rcdoRollup, rcdoTree, memberViews }: ByRcdoSectionProps) {
  const rollupMap: RollupMap = {};
  for (const entry of rcdoRollup) rollupMap[entry.rcdoNodeId] = entry;

  const achievedPointsMap: PointsMap = {};
  for (const member of memberViews) {
    for (const commit of member.commits) {
      if (commit.rcdoNodeId && commit.outcome === "ACHIEVED" && commit.estimatePoints != null) {
        achievedPointsMap[commit.rcdoNodeId] = (achievedPointsMap[commit.rcdoNodeId] ?? 0) + commit.estimatePoints;
      }
    }
  }

  if (rcdoTree.length === 0) {
    return (
      <section aria-labelledby="by-rcdo-heading" data-testid="by-rcdo-section">
        <h3 id="by-rcdo-heading" className="m-0 mb-3 text-sm font-bold">By RCDO</h3>
        <p className="text-sm text-muted">No RCDO hierarchy configured.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby="by-rcdo-heading" data-testid="by-rcdo-section">
      <h3 id="by-rcdo-heading" className="m-0 mb-3 text-sm font-bold">By RCDO</h3>
      <div className="rounded-default border border-border bg-surface overflow-hidden">
        <table data-testid="by-rcdo-table" className="w-full border-collapse text-sm" aria-label="RCDO rollup">
          <thead>
            <tr className="bg-background border-b border-border">
              <th className={cn(thCls, "text-left")}>RCDO Node</th>
              <th className={cn(thCls, "text-right")}>Commits</th>
              <th className={cn(thCls, "text-right")}>Pts Planned</th>
              <th className={cn(thCls, "text-right")}>Pts Achieved</th>
              <th className={cn(thCls, "text-left")}>Planned vs Achieved</th>
            </tr>
          </thead>
          <tbody>
            {rcdoTree.map((root) => <RcdoRow key={root.id} node={root} rollupMap={rollupMap} achievedPointsMap={achievedPointsMap} depth={0} />)}
          </tbody>
        </table>
      </div>
    </section>
  );
}
