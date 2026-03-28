package com.weeklycommit.ai.evidence;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A structured evidence bundle assembled from multiple retrieval strategies.
 * Passed to the LLM as grounded context — the model synthesises only from this
 * bundle, never from its own knowledge.
 *
 * <p>
 * Also returned to the frontend so the UI can render an evidence drawer showing
 * exactly what the AI used to produce its answer.
 */
public record StructuredEvidence(SqlFacts sqlFacts, LineageChain lineage, List<SemanticMatch> semanticMatches,
		RiskFeatures riskFeatures) {

	/** Empty evidence bundle — used when no context could be assembled. */
	public static StructuredEvidence empty() {
		return new StructuredEvidence(null, null, List.of(), null);
	}

	/**
	 * Exact facts from SQL: point-in-time correct, never invented by the LLM.
	 */
	public record SqlFacts(UUID userId, String userDisplayName, UUID teamId, String teamName, UUID planId,
			LocalDate weekStart, String planState, int capacityBudget, int totalPlannedPoints, int totalAchievedPoints,
			int commitCount, int carryForwardCount, int scopeChangeCount, boolean lockCompliance,
			boolean reconcileCompliance, Map<String, Integer> chessDistribution) {
	}

	/**
	 * A carry-forward lineage chain: ordered list of nodes from origin to current.
	 */
	public record LineageChain(UUID currentCommitId, String currentTitle, int streakLength, List<LineageNode> nodes) {
	}

	/**
	 * A single node in a carry-forward lineage chain.
	 */
	public record LineageNode(UUID commitId, String title, LocalDate weekStart, String outcome, String chessPiece,
			Integer estimatePoints, String carryForwardReason) {
	}

	/**
	 * A semantic match from Pinecone vector retrieval.
	 */
	public record SemanticMatch(String entityType, String entityId, double score, String weekStartDate, String text) {
	}

	/**
	 * Pre-computed risk features from derived read-model tables.
	 */
	public record RiskFeatures(double completionRatio, double avgCompletionRatio4w, int carryForwardStreakMax,
			int scopeChangeCount, int kingCount, int queenCount, List<String> activeRiskSignalTypes) {
	}
}
