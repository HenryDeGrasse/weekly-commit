/**
 * Plan data loading, RCDO tree, plan history, and carry-forward lineage.
 */
import { useState, useMemo } from "react";
import { useCurrentPlan, usePlanApi } from "../../api/planHooks.js";
import { useRcdoTree } from "../../api/rcdoHooks.js";
import { usePlanHistory, useCarryForwardLineage } from "../../api/ticketHooks.js";
import type { RcdoTreeNode } from "../../api/rcdoTypes.js";

function buildRcdoLabels(rcdoTree: RcdoTreeNode[] | null | undefined): Record<string, string> {
  const map: Record<string, string> = {};
  function traverse(nodes: RcdoTreeNode[] | null | undefined, path: string[] = []) {
    if (!nodes) return;
    for (const n of nodes) {
      const nextPath = [...path, n.title];
      map[n.id] = nextPath.join(" > ");
      traverse(n.children, nextPath);
    }
  }
  traverse(rcdoTree ?? []);
  return map;
}

export function useMyWeekPlan(weekStartDate: string, currentUserId: string) {
  const planApi = usePlanApi();
  const { data: planData, loading: planLoading, error: planError, refetch: refetchPlan } =
    useCurrentPlan(weekStartDate);
  const { data: rcdoTree } = useRcdoTree();

  const { data: planHistory, loading: historyLoading } = usePlanHistory(currentUserId);
  const [lineageCommitId, setLineageCommitId] = useState<string | null>(null);
  const { data: lineage, loading: lineageLoading } = useCarryForwardLineage(lineageCommitId);

  const plan = planData?.plan;
  const commits = useMemo(() => planData?.commits ?? [], [planData]);
  const rcdoLabels = useMemo(() => buildRcdoLabels(rcdoTree), [rcdoTree]);

  const isDraft = plan?.state === "DRAFT";
  const isLocked = plan?.state === "LOCKED";

  return {
    planApi,
    plan,
    commits,
    planLoading,
    planError,
    refetchPlan,
    rcdoTree: rcdoTree ?? [],
    rcdoLabels,
    isDraft,
    isLocked,
    planHistory: planHistory ?? [],
    historyLoading,
    lineageCommitId,
    setLineageCommitId,
    lineage,
    lineageLoading,
  };
}
