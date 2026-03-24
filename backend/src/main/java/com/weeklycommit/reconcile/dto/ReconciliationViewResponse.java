package com.weeklycommit.reconcile.dto;

import com.weeklycommit.plan.dto.PlanResponse;
import java.util.List;

/**
 * Full reconciliation dashboard for a plan in RECONCILING state.
 *
 * <p>
 * Displays each commit with its locked baseline alongside current live values
 * so the user can set outcomes accurately.
 */
public record ReconciliationViewResponse(PlanResponse plan, List<ReconcileCommitView> commits, int baselineTotalPoints,
		int currentTotalPoints, int commitCount, int outcomesSetCount) {
}
