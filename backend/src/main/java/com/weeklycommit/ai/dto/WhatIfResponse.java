package com.weeklycommit.ai.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response for a what-if plan simulation.
 *
 * <p>
 * When {@code available} is {@code false}, all other fields are null/zero.
 */
public record WhatIfResponse(
		/** {@code false} when the simulation is unavailable. */
		boolean available,
		/** Current plan state snapshot, before mutations. */
		PlanSnapshot currentState,
		/** Projected plan state snapshot, after applying mutations. */
		PlanSnapshot projectedState,
		/** Change in total planned points: projected − current. */
		int capacityDelta,
		/** Per-RCDO-node point changes produced by the mutations. */
		List<RcdoCoverageChange> rcdoCoverageChanges,
		/** Risk signals added or resolved by the mutations. */
		RiskDelta riskDelta,
		/** LLM narrative (null in step 1 — pure computation only). */
		String narrative,
		/** LLM recommendation (null in step 1 — pure computation only). */
		String recommendation) {

	/**
	 * Snapshot of plan state at a point in time (current or projected).
	 */
	public record PlanSnapshot(
			/** Total planned story points. */
			int totalPoints,
			/** Capacity budget in story points. */
			int capacityBudget,
			/** Active risk signal types, e.g. {@code "OVERCOMMIT"}. */
			List<String> riskSignals,
			/** Map of RCDO node id → total planned points for that node. */
			Map<UUID, Integer> rcdoCoverage) {
	}

	/**
	 * Describes the change in story-point coverage for a single RCDO node.
	 */
	public record RcdoCoverageChange(
			/** RCDO node that changed. */
			UUID rcdoNodeId,
			/** RCDO node title (null if not resolved). */
			String rcdoTitle,
			/** Points before mutations. */
			int beforePoints,
			/** Points after mutations. */
			int afterPoints) {
	}

	/**
	 * Describes which risk signals were introduced or resolved by the mutations.
	 */
	public record RiskDelta(
			/** Risk signal types that appeared in the projected state. */
			List<String> newRisks,
			/** Risk signal types that disappeared in the projected state. */
			List<String> resolvedRisks) {
	}

	/** Convenience factory for the unavailable case. */
	public static WhatIfResponse unavailable() {
		return new WhatIfResponse(false, null, null, 0, null, null, null, null);
	}
}
