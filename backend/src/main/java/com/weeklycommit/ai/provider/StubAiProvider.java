package com.weeklycommit.ai.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development/test stub implementation of {@link AiProvider}.
 *
 * <p>
 * Returns deterministic canned responses for every suggestion type so the rest
 * of the AI pipeline can be tested end-to-end without a real model. Always
 * reports itself as available.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "ai.provider", havingValue = "stub")
public class StubAiProvider implements AiProvider {

	private static final Logger log = LoggerFactory.getLogger(StubAiProvider.class);

	static final String STUB_MODEL_VERSION = "stub-v1";

	@Override
	public String getName() {
		return "stub";
	}

	@Override
	public String getVersion() {
		return STUB_MODEL_VERSION;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public AiSuggestionResult generateSuggestion(AiContext context) {
		log.debug("StubAiProvider.generateSuggestion: type={}", context.suggestionType());
		String payload;
		String rationale;
		double confidence = 0.85;

		switch (context.suggestionType()) {
			case AiContext.TYPE_COMMIT_DRAFT -> {
				payload = """
						{
						  "suggestedTitle": "Implement feature with clear acceptance criteria",
						  "suggestedDescription": "Break this commit into smaller, measurable deliverables.",
						  "suggestedSuccessCriteria": "Feature is deployed and passing all tests.",
						  "suggestedEstimatePoints": 3
						}
						""";
				rationale = "Commit title appears vague; similar past commits used more specific language.";
			}
			case AiContext.TYPE_COMMIT_LINT -> {
				payload = """
						{
						  "hardValidation": [],
						  "softGuidance": [
						    {
						      "code": "VAGUE_TITLE",
						      "message": "Consider making the title more specific and outcome-focused."
						    }
						  ]
						}
						""";
				rationale = "Commit title could be more specific. No hard violations detected.";
				confidence = 0.9;
			}
			case AiContext.TYPE_RCDO_SUGGEST -> {
				Object rcdoList = context.rcdoTree() != null && !context.rcdoTree().isEmpty()
						? context.rcdoTree().get(0).get("id")
						: null;
				payload = String.format("""
						{
						  "suggestedRcdoNodeId": "%s",
						  "confidence": 0.85
						}
						""", rcdoList != null ? rcdoList : "");
				rationale = "Commit title and description closely match this Outcome's scope.";
				confidence = 0.85;
			}
			case AiContext.TYPE_RISK_SIGNAL -> {
				payload = """
						{
						  "signals": []
						}
						""";
				rationale = "No risk signals detected for this plan.";
				confidence = 0.95;
			}
			case AiContext.TYPE_RECONCILE_ASSIST -> {
				payload = """
						{
						  "likelyOutcomes": [],
						  "draftSummary": "Plan reconciliation appears straightforward with no major deviations.",
						  "carryForwardRecommendations": []
						}
						""";
				rationale = "No unresolved commits detected that require carry-forward.";
			}
			case AiContext.TYPE_TEAM_SUMMARY -> {
				payload = """
						{
						  "summaryText": "Team plan looks healthy this week.",
						  "topRcdoBranches": [],
						  "carryForwardPatterns": [],
						  "criticalBlockedItemIds": []
						}
						""";
				rationale = "Generated from team plan data for the requested week.";
			}
			case AiContext.TYPE_RAG_INTENT -> {
				payload = """
						{
						  "intent": "status_query",
						  "userFilter": "team",
						  "entityTypes": ["commit", "plan_summary"],
						  "timeRange": null,
						  "keywords": ["weekly", "commit", "plan"]
						}
						""";
				rationale = "Intent classified as a general status query over commits and plans.";
			}
			case AiContext.TYPE_RAG_QUERY -> {
				payload = """
						{
						  "answer": "Based on the retrieved planning data, the team is making steady progress.",
						  "sources": [],
						  "confidence": 0.85
						}
						""";
				rationale = "Answer generated from retrieved context chunks.";
			}
			case AiContext.TYPE_TEAM_INSIGHT -> {
				payload = """
						{
						  "insights": [
						    {
						      "insightText": "The team shows consistent carry-forward patterns suggesting recurring capacity pressure.",
						      "severity": "MEDIUM",
						      "sourceEntityIds": [],
						      "actionSuggestion": "Review team capacity and consider reducing weekly commitment scope."
						    }
						  ]
						}
						""";
				rationale = "Team insight generated from historical planning patterns.";
			}
			case AiContext.TYPE_PERSONAL_INSIGHT -> {
				payload = """
						{
						  "insights": [
						    {
						      "insightText": "You consistently achieve your high-priority commits. Consider raising the bar on estimates.",
						      "severity": "LOW",
						      "sourceEntityIds": [],
						      "actionSuggestion": "Incrementally increase estimate points on KING/QUEEN commits."
						    }
						  ]
						}
						""";
				rationale = "Personal insight generated from recent plan history.";
			}
			case AiContext.TYPE_WHAT_IF -> {
				payload = """
						{
						  "narrative": "This change would shift the plan's total points and affect RCDO coverage as described.",
						  "recommendation": "Consider whether the capacity change aligns with your weekly goals."
						}
						""";
				rationale = "Stub what-if narration generated for simulation result.";
			}
			default -> {
				payload = "{}";
				rationale = "Unknown suggestion type.";
				confidence = 0.0;
			}
		}

		return new AiSuggestionResult(true, payload.trim(), rationale, confidence, STUB_MODEL_VERSION);
	}
}
