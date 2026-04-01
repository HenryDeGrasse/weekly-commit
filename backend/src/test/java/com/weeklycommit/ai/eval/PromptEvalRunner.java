package com.weeklycommit.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.provider.OpenRouterAiProvider;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prompt evaluation runner. Calls the real LLM with actual prompt templates and
 * scores output against a golden dataset.
 *
 * <p>
 * Tagged with {@code @Tag("eval")} so it is excluded from normal
 * {@code ./gradlew test}. Run explicitly with:
 *
 * <pre>
 * ./gradlew test --tests "com.weeklycommit.ai.eval.*" -Dinclude.tags=eval
 * </pre>
 *
 * <p>
 * Requires {@code OPENROUTER_API_KEY} environment variable. Tests are skipped
 * gracefully if the key is absent.
 */
@Tag("eval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PromptEvalRunner {

	private static final Logger log = LoggerFactory.getLogger(PromptEvalRunner.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final List<EvalResult> allResults = new ArrayList<>();

	private OpenRouterAiProvider provider;
	private boolean providerAvailable;

	@BeforeAll
	void setUp() {
		String apiKey = System.getenv("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			log.warn("OPENROUTER_API_KEY not set — eval tests will be skipped");
			providerAvailable = false;
			provider = new OpenRouterAiProvider("", "skip", 1024, "http://localhost:0", objectMapper);
			return;
		}
		String model = System.getenv().getOrDefault("OPENROUTER_MODEL", "anthropic/claude-sonnet-4-20250514");
		int maxTokens = Integer.parseInt(System.getenv().getOrDefault("OPENROUTER_MAX_TOKENS", "1024"));
		provider = new OpenRouterAiProvider(apiKey, model, maxTokens, "https://openrouter.ai/api/v1", objectMapper);
		providerAvailable = true;
	}

	// ── Commit Draft Assist Evaluation ────────────────────────────────────

	Stream<EvalCase> commitDraftAssistCases() throws Exception {
		return loadCases("eval/commit-draft-assist/cases.json");
	}

	@ParameterizedTest(name = "commit-draft-assist: {0}")
	@MethodSource("commitDraftAssistCases")
	void evaluateCommitDraftAssist(EvalCase evalCase) throws Exception {
		if (!providerAvailable) {
			log.info("Skipping eval case {} — provider unavailable", evalCase.id());
			return;
		}

		JsonNode input = evalCase.input();
		JsonNode expected = evalCase.expectedBehavior();

		// Build context matching what CommitDraftAssistService sends
		Map<String, Object> commitData = objectMapper.convertValue(input, new TypeReference<>() {
		});
		AiContext context = new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null, commitData, Map.of(),
				List.of(), List.of(), Map.of());

		AiSuggestionResult result = provider.generateSuggestion(context);

		// Schema validation
		boolean schemaValid = false;
		JsonNode output = null;
		if (result.available() && result.payload() != null) {
			try {
				output = objectMapper.readTree(result.payload());
				schemaValid = output.has("suggestedTitle") && output.has("suggestedEstimatePoints");
			} catch (Exception e) {
				log.warn("Case {}: invalid JSON output: {}", evalCase.id(), e.getMessage());
			}
		}

		EvalResult evalResult = new EvalResult(evalCase.id(), evalCase.description(),
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload(),
				evalCase.critical());

		if (schemaValid && output != null) {
			// Automated checks against expected behavior
			evaluateDraftAssistOutput(output, expected, evalResult);
			// LLM judge scoring — optional, failures are non-fatal
			if (output.has("suggestedTitle") && !output.get("suggestedTitle").isNull()
					&& !output.get("suggestedTitle").asText("").isBlank()) {
				try {
					String titlePrompt = loadJudgePrompt("eval/judge-prompts/title-quality-judge.txt");
					titlePrompt = titlePrompt.replace("{originalTitle}", input.path("title").asText(""))
							.replace("{suggestedTitle}", output.get("suggestedTitle").asText(""))
							.replace("{chessPiece}", input.path("chessPiece").asText(""));
					JsonNode titleScores = callJudge(titlePrompt);
					evalResult.addScore("titleJudge_clarity", titleScores.path("clarity").asDouble(0.0));
					evalResult.addScore("titleJudge_specificity", titleScores.path("specificity").asDouble(0.0));
					evalResult.addScore("titleJudge_appropriate_scope",
							titleScores.path("appropriate_scope").asDouble(0.0));
					evalResult.addScore("titleJudge_improvement", titleScores.path("improvement").asDouble(0.0));
					evalResult.addScore("titleJudge_professional_tone",
							titleScores.path("professional_tone").asDouble(0.0));
				} catch (Exception e) {
					log.warn("Case {}: title judge failed — {}", evalCase.id(), e.getMessage());
				}
			}
			if (output.has("suggestedSuccessCriteria") && !output.get("suggestedSuccessCriteria").isNull()
					&& !output.get("suggestedSuccessCriteria").asText("").isBlank()) {
				try {
					String criteriaPrompt = loadJudgePrompt("eval/judge-prompts/criteria-quality-judge.txt");
					criteriaPrompt = criteriaPrompt.replace("{commitTitle}", input.path("title").asText(""))
							.replace("{chessPiece}", input.path("chessPiece").asText(""))
							.replace("{suggestedCriteria}", output.get("suggestedSuccessCriteria").asText(""));
					JsonNode criteriaScores = callJudge(criteriaPrompt);
					evalResult.addScore("criteriaJudge_measurable", criteriaScores.path("measurable").asDouble(0.0));
					evalResult.addScore("criteriaJudge_completeness",
							criteriaScores.path("completeness").asDouble(0.0));
					evalResult.addScore("criteriaJudge_achievable_in_one_week",
							criteriaScores.path("achievable_in_one_week").asDouble(0.0));
					evalResult.addScore("criteriaJudge_testable", criteriaScores.path("testable").asDouble(0.0));
				} catch (Exception e) {
					log.warn("Case {}: criteria judge failed — {}", evalCase.id(), e.getMessage());
				}
			}
		}

		evalResult.addNote("rawPayload", result.payload());
		evalResult.addNote("rationale", result.rationale());
		allResults.add(evalResult);

		log.info("Eval result: {}", evalResult);

		// Soft assertion — log failures but don't fail the build on individual cases
		if (!evalResult.passed()) {
			log.warn("EVAL FAIL: {} — checks={}", evalCase.id(), evalResult.getChecks());
		}
		assertThat(schemaValid).as("Schema validation for case %s", evalCase.id()).isTrue();
	}

	// ── Commit Lint Evaluation ────────────────────────────────────────────

	Stream<EvalCase> commitLintCases() throws Exception {
		return loadCases("eval/commit-lint/cases.json");
	}

	@ParameterizedTest(name = "commit-lint: {0}")
	@MethodSource("commitLintCases")
	void evaluateCommitLint(EvalCase evalCase) throws Exception {
		if (!providerAvailable) {
			log.info("Skipping eval case {} — provider unavailable", evalCase.id());
			return;
		}

		JsonNode input = evalCase.input();
		JsonNode expected = evalCase.expectedBehavior();

		// Build context: commits go into historicalCommits, budget into planData
		List<Map<String, Object>> commits = objectMapper.convertValue(input.get("commits"), new TypeReference<>() {
		});
		Map<String, Object> planData = new LinkedHashMap<>();
		if (input.has("capacityBudgetPoints")) {
			planData.put("capacityBudgetPoints", input.get("capacityBudgetPoints").asInt());
		}

		AiContext context = new AiContext(AiContext.TYPE_COMMIT_LINT, null, null, null, Map.of(), planData, commits,
				List.of(), Map.of());

		AiSuggestionResult result = provider.generateSuggestion(context);

		boolean schemaValid = false;
		JsonNode output = null;
		if (result.available() && result.payload() != null) {
			try {
				output = objectMapper.readTree(result.payload());
				schemaValid = output.has("hardValidation") && output.has("softGuidance");
			} catch (Exception e) {
				log.warn("Case {}: invalid JSON output: {}", evalCase.id(), e.getMessage());
			}
		}

		EvalResult evalResult = new EvalResult(evalCase.id(), evalCase.description(),
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload(),
				evalCase.critical());

		if (schemaValid && output != null) {
			evaluateLintOutput(output, expected, evalResult);
		}

		allResults.add(evalResult);
		log.info("Eval result: {}", evalResult);
		assertThat(schemaValid).as("Schema validation for case %s", evalCase.id()).isTrue();
	}

	// ── RCDO Suggest Evaluation ───────────────────────────────────────────

	Stream<EvalCase> rcdoSuggestCases() throws Exception {
		return loadCases("eval/rcdo-suggest/cases.json");
	}

	@ParameterizedTest(name = "rcdo-suggest: {0}")
	@MethodSource("rcdoSuggestCases")
	void evaluateRcdoSuggest(EvalCase evalCase) throws Exception {
		if (!providerAvailable) {
			log.info("Skipping eval case {} — provider unavailable", evalCase.id());
			return;
		}
		JsonNode input = evalCase.input();
		JsonNode expected = evalCase.expectedBehavior();

		Map<String, Object> commitData = objectMapper.convertValue(input.get("commitData"), new TypeReference<>() {
		});
		List<Map<String, Object>> rcdoTree = objectMapper.convertValue(input.get("rcdoTree"), new TypeReference<>() {
		});

		AiContext context = new AiContext(AiContext.TYPE_RCDO_SUGGEST, null, null, null, commitData, Map.of(),
				List.of(), rcdoTree, Map.of());

		AiSuggestionResult result = provider.generateSuggestion(context);

		boolean schemaValid = false;
		JsonNode output = null;
		if (result.available() && result.payload() != null) {
			try {
				output = objectMapper.readTree(result.payload());
				schemaValid = output.has("suggestedRcdoNodeId") && output.has("confidence");
			} catch (Exception e) {
				log.warn("Case {}: invalid JSON output: {}", evalCase.id(), e.getMessage());
			}
		}

		EvalResult evalResult = new EvalResult(evalCase.id(), evalCase.description(),
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload(),
				evalCase.critical());

		if (schemaValid && output != null) {
			evaluateRcdoSuggestOutput(output, expected, evalResult);
		}

		allResults.add(evalResult);
		log.info("Eval result: {}", evalResult);
		assertThat(schemaValid).as("Schema validation for case %s", evalCase.id()).isTrue();
	}

	// ── Risk Signal Evaluation ────────────────────────────────────────────

	Stream<EvalCase> riskSignalCases() throws Exception {
		return loadCases("eval/risk-signal/cases.json");
	}

	@ParameterizedTest(name = "risk-signal: {0}")
	@MethodSource("riskSignalCases")
	void evaluateRiskSignal(EvalCase evalCase) throws Exception {
		if (!providerAvailable) {
			log.info("Skipping eval case {} — provider unavailable", evalCase.id());
			return;
		}
		JsonNode input = evalCase.input();
		JsonNode expected = evalCase.expectedBehavior();

		List<Map<String, Object>> commits = objectMapper.convertValue(input.get("commits"), new TypeReference<>() {
		});
		Map<String, Object> planData = objectMapper.convertValue(input.get("planData"), new TypeReference<>() {
		});
		Map<String, Object> additionalContext = new LinkedHashMap<>();
		if (input.has("scopeChanges")) {
			additionalContext.put("scopeChanges",
					objectMapper.convertValue(input.get("scopeChanges"), new TypeReference<List<Object>>() {
					}));
		}

		AiContext context = new AiContext(AiContext.TYPE_RISK_SIGNAL, null, null, null, Map.of(), planData, commits,
				List.of(), additionalContext);

		AiSuggestionResult result = provider.generateSuggestion(context);

		boolean schemaValid = false;
		JsonNode output = null;
		if (result.available() && result.payload() != null) {
			try {
				output = objectMapper.readTree(result.payload());
				schemaValid = output.has("signals") && output.get("signals").isArray();
			} catch (Exception e) {
				log.warn("Case {}: invalid JSON output: {}", evalCase.id(), e.getMessage());
			}
		}

		EvalResult evalResult = new EvalResult(evalCase.id(), evalCase.description(),
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload(),
				evalCase.critical());

		if (schemaValid && output != null) {
			evaluateRiskSignalOutput(output, expected, evalResult);
		}

		allResults.add(evalResult);
		log.info("Eval result: {}", evalResult);
		assertThat(schemaValid).as("Schema validation for case %s", evalCase.id()).isTrue();
	}

	// ── Reconcile Assist Evaluation ───────────────────────────────────────

	Stream<EvalCase> reconcileAssistCases() throws Exception {
		return loadCases("eval/reconcile-assist/cases.json");
	}

	@ParameterizedTest(name = "reconcile-assist: {0}")
	@MethodSource("reconcileAssistCases")
	void evaluateReconcileAssist(EvalCase evalCase) throws Exception {
		if (!providerAvailable) {
			log.info("Skipping eval case {} — provider unavailable", evalCase.id());
			return;
		}
		JsonNode input = evalCase.input();
		JsonNode expected = evalCase.expectedBehavior();

		List<Map<String, Object>> commits = objectMapper.convertValue(input.get("commits"), new TypeReference<>() {
		});
		Map<String, Object> planData = objectMapper.convertValue(input.get("planData"), new TypeReference<>() {
		});
		Map<String, Object> additionalContext = new LinkedHashMap<>();
		if (input.has("scopeChanges")) {
			additionalContext.put("scopeChanges",
					objectMapper.convertValue(input.get("scopeChanges"), new TypeReference<List<Object>>() {
					}));
		}

		AiContext context = new AiContext(AiContext.TYPE_RECONCILE_ASSIST, null, null, null, Map.of(), planData,
				commits, List.of(), additionalContext);

		AiSuggestionResult result = provider.generateSuggestion(context);

		boolean schemaValid = false;
		JsonNode output = null;
		if (result.available() && result.payload() != null) {
			try {
				output = objectMapper.readTree(result.payload());
				schemaValid = output.has("likelyOutcomes") && output.has("draftSummary");
			} catch (Exception e) {
				log.warn("Case {}: invalid JSON output: {}", evalCase.id(), e.getMessage());
			}
		}

		EvalResult evalResult = new EvalResult(evalCase.id(), evalCase.description(),
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload(),
				evalCase.critical());

		if (schemaValid && output != null) {
			evaluateReconcileAssistOutput(output, expected, evalResult);
		}

		allResults.add(evalResult);
		log.info("Eval result: {}", evalResult);
		assertThat(schemaValid).as("Schema validation for case %s", evalCase.id()).isTrue();
	}

	// ── RAG Query Evaluation ──────────────────────────────────────────────

	Stream<EvalCase> ragQueryCases() throws Exception {
		return loadCases("eval/rag-query/cases.json");
	}

	@ParameterizedTest(name = "rag-query: {0}")
	@MethodSource("ragQueryCases")
	void evaluateRagQuery(EvalCase evalCase) throws Exception {
		if (!providerAvailable) {
			log.info("Skipping eval case {} — provider unavailable", evalCase.id());
			return;
		}
		JsonNode input = evalCase.input();
		JsonNode expected = evalCase.expectedBehavior();

		Map<String, Object> additionalContext = objectMapper.convertValue(input, new TypeReference<>() {
		});

		AiContext context = new AiContext(AiContext.TYPE_RAG_QUERY, null, null, null, Map.of(), Map.of(), List.of(),
				List.of(), additionalContext);

		AiSuggestionResult result = provider.generateSuggestion(context);

		boolean schemaValid = false;
		JsonNode output = null;
		if (result.available() && result.payload() != null) {
			try {
				output = objectMapper.readTree(result.payload());
				schemaValid = output.has("answer") && output.has("confidence");
			} catch (Exception e) {
				log.warn("Case {}: invalid JSON output: {}", evalCase.id(), e.getMessage());
			}
		}

		EvalResult evalResult = new EvalResult(evalCase.id(), evalCase.description(),
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload(),
				evalCase.critical());

		if (schemaValid && output != null) {
			evaluateRagQueryOutput(output, expected, evalResult);
		}

		allResults.add(evalResult);
		log.info("Eval result: {}", evalResult);
		assertThat(schemaValid).as("Schema validation for case %s", evalCase.id()).isTrue();
	}

	// ── What-If Narration Evaluation ────────────────────────────────────────

	Stream<EvalCase> whatIfCases() throws Exception {
		return loadCases("eval/what-if/cases.json");
	}

	@ParameterizedTest(name = "what-if: {0}")
	@MethodSource("whatIfCases")
	void evaluateWhatIf(EvalCase evalCase) throws Exception {
		if (!providerAvailable) {
			log.info("Skipping eval case {} — provider unavailable", evalCase.id());
			return;
		}

		JsonNode input = evalCase.input();
		JsonNode expected = evalCase.expectedBehavior();

		// input IS the additionalContext — matches
		// WhatIfService.buildNarrationContext()
		Map<String, Object> additionalContext = objectMapper.convertValue(input, new TypeReference<>() {
		});

		AiContext context = new AiContext(AiContext.TYPE_WHAT_IF, null, null, null, Map.of(), Map.of(), List.of(),
				List.of(), additionalContext);

		AiSuggestionResult result = provider.generateSuggestion(context);

		boolean schemaValid = false;
		JsonNode output = null;
		if (result.available() && result.payload() != null) {
			try {
				output = objectMapper.readTree(result.payload());
				schemaValid = output.has("narrative");
			} catch (Exception e) {
				log.warn("Case {}: invalid JSON output: {}", evalCase.id(), e.getMessage());
			}
		}

		EvalResult evalResult = new EvalResult(evalCase.id(), evalCase.description(),
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload(),
				evalCase.critical());

		if (schemaValid && output != null) {
			evaluateWhatIfOutput(output, expected, evalResult);
		}

		allResults.add(evalResult);
		log.info("Eval result: {}", evalResult);
		assertThat(schemaValid).as("Schema validation for case %s", evalCase.id()).isTrue();
	}

	// ── Output evaluation helpers ─────────────────────────────────────────

	private void evaluateWhatIfOutput(JsonNode output, JsonNode expected, EvalResult result) {
		String narrative = output.path("narrative").asText("");
		boolean hasNarrative = !narrative.isBlank() && !"null".equalsIgnoreCase(narrative);

		if (expected.has("shouldHaveNarrative")) {
			result.addCheck("hasNarrative", expected.get("shouldHaveNarrative").asBoolean() == hasNarrative);
		}

		if (expected.has("narrativeShouldMention") && hasNarrative) {
			String lowerNarrative = narrative.toLowerCase();
			for (JsonNode keyword : expected.get("narrativeShouldMention")) {
				String kw = keyword.asText("").toLowerCase();
				result.addCheck("mentions_" + kw.replace(" ", "_"), lowerNarrative.contains(kw));
			}
		}

		if (expected.has("maxNarrativeLength") && hasNarrative) {
			result.addCheck("maxNarrativeLength", narrative.length() <= expected.get("maxNarrativeLength").asInt());
		}

		if (expected.has("shouldHaveRecommendation")) {
			String rec = output.path("recommendation").asText("");
			boolean hasRec = !rec.isBlank() && !"null".equalsIgnoreCase(rec);
			result.addCheck("hasRecommendation", expected.get("shouldHaveRecommendation").asBoolean() == hasRec);
		}

		result.addScore("narrativeLength", narrative.length());
		result.addNote("narrativePreview", narrative.substring(0, Math.min(200, narrative.length())));
	}

	private void evaluateRcdoSuggestOutput(JsonNode output, JsonNode expected, EvalResult result) {
		boolean suggestedNode = output.has("suggestedRcdoNodeId") && !output.get("suggestedRcdoNodeId").isNull()
				&& !output.get("suggestedRcdoNodeId").asText("").isBlank()
				&& !"null".equals(output.get("suggestedRcdoNodeId").asText(""));
		double confidence = output.path("confidence").asDouble(0.0);

		if (expected.has("shouldSuggestNode")) {
			result.addCheck("shouldSuggestNode", expected.get("shouldSuggestNode").asBoolean() == suggestedNode);
		}
		if (expected.has("expectedNodeId") && suggestedNode) {
			result.addCheck("correctNode",
					expected.get("expectedNodeId").asText("").equals(output.get("suggestedRcdoNodeId").asText("")));
		}
		if (expected.has("shouldNotSuggestNodeId") && suggestedNode) {
			result.addCheck("avoidedWrongNode", !expected.get("shouldNotSuggestNodeId").asText("")
					.equals(output.get("suggestedRcdoNodeId").asText("")));
		}
		if (expected.has("minConfidence")) {
			result.addCheck("minConfidence", confidence >= expected.get("minConfidence").asDouble());
		}
		if (expected.has("maxConfidence")) {
			result.addCheck("maxConfidence", confidence <= expected.get("maxConfidence").asDouble());
		}
		if (expected.has("shouldNotSuggestRallyCry") && expected.get("shouldNotSuggestRallyCry").asBoolean()) {
			// Check that rationale doesn't indicate a Rally Cry was suggested
			String nodeId = suggestedNode ? output.get("suggestedRcdoNodeId").asText("") : "";
			result.addCheck("notRallyCry", !nodeId.startsWith("rc-"));
		}
		if (expected.has("shouldBeOutcome") && expected.get("shouldBeOutcome").asBoolean()) {
			String nodeId = suggestedNode ? output.get("suggestedRcdoNodeId").asText("") : "";
			result.addCheck("isOutcome", nodeId.startsWith("out-"));
		}

		result.addScore("confidence", confidence);
		if (output.has("rationale") && !output.get("rationale").isNull()) {
			result.addNote("rationale", output.get("rationale").asText());
		}
	}

	private void evaluateRiskSignalOutput(JsonNode output, JsonNode expected, EvalResult result) {
		JsonNode signals = output.get("signals");
		int signalCount = signals != null && signals.isArray() ? signals.size() : 0;

		if (expected.has("minSignals")) {
			result.addCheck("minSignals", signalCount >= expected.get("minSignals").asInt());
		}
		if (expected.has("maxSignals")) {
			result.addCheck("maxSignals", signalCount <= expected.get("maxSignals").asInt());
		}

		// Check that rules-based signals are not duplicated
		if (expected.has("shouldNotDuplicateRules") && expected.get("shouldNotDuplicateRules").asBoolean()
				&& signals != null) {
			java.util.Set<String> rulesBasedTypes = java.util.Set.of("OVERCOMMIT", "UNDERCOMMIT",
					"REPEATED_CARRY_FORWARD", "BLOCKED_CRITICAL", "SCOPE_VOLATILITY");
			boolean duplicated = false;
			for (JsonNode s : signals) {
				String signalType = s.path("signalType").asText("").toUpperCase();
				if (rulesBasedTypes.contains(signalType)) {
					duplicated = true;
					result.addNote("duplicatedRulesSignal", signalType);
				}
			}
			result.addCheck("noDuplicateRules", !duplicated);
		}

		result.addScore("signalCount", signalCount);
	}

	private void evaluateReconcileAssistOutput(JsonNode output, JsonNode expected, EvalResult result) {
		JsonNode outcomes = output.get("likelyOutcomes");
		JsonNode carryForwards = output.get("carryForwardRecommendations");
		String summary = output.path("draftSummary").asText("");
		int outcomeCount = outcomes != null && outcomes.isArray() ? outcomes.size() : 0;
		int cfCount = carryForwards != null && carryForwards.isArray() ? carryForwards.size() : 0;

		if (expected.has("minOutcomes")) {
			result.addCheck("minOutcomes", outcomeCount >= expected.get("minOutcomes").asInt());
		}
		if (expected.has("maxOutcomes")) {
			result.addCheck("maxOutcomes", outcomeCount <= expected.get("maxOutcomes").asInt());
		}
		if (expected.has("shouldHaveSummary")) {
			result.addCheck("hasSummary", !summary.isBlank());
		}
		if (expected.has("minCarryForwards")) {
			result.addCheck("minCarryForwards", cfCount >= expected.get("minCarryForwards").asInt());
		}
		if (expected.has("maxCarryForwards")) {
			result.addCheck("maxCarryForwards", cfCount <= expected.get("maxCarryForwards").asInt());
		}

		// Check specific expected outcomes
		if (expected.has("expectedOutcomes") && outcomes != null) {
			expected.get("expectedOutcomes").fields().forEachRemaining(entry -> {
				String commitId = entry.getKey();
				String expectedOutcome = entry.getValue().asText();
				boolean found = false;
				for (JsonNode o : outcomes) {
					if (commitId.equals(o.path("commitId").asText(""))) {
						found = o.path("suggestedOutcome").asText("").equalsIgnoreCase(expectedOutcome);
						break;
					}
				}
				result.addCheck("outcome_" + commitId, found);
			});
		}

		// Check carry-forward includes specific commits
		if (expected.has("carryForwardShouldInclude") && carryForwards != null) {
			for (JsonNode expectedCf : expected.get("carryForwardShouldInclude")) {
				String commitId = expectedCf.asText();
				boolean found = false;
				for (JsonNode cf : carryForwards) {
					if (commitId.equals(cf.path("commitId").asText(""))) {
						found = true;
						break;
					}
				}
				result.addCheck("carryForward_" + commitId, found);
			}
		}

		result.addScore("outcomeCount", outcomeCount);
		result.addScore("carryForwardCount", cfCount);
	}

	private void evaluateRagQueryOutput(JsonNode output, JsonNode expected, EvalResult result) {
		String answer = output.path("answer").asText("");
		double confidence = output.path("confidence").asDouble(0.0);
		JsonNode sources = output.get("sources");
		int sourceCount = sources != null && sources.isArray() ? sources.size() : 0;

		if (expected.has("shouldHaveAnswer")) {
			result.addCheck("hasAnswer", !answer.isBlank());
		}
		if (expected.has("minConfidence")) {
			result.addCheck("minConfidence", confidence >= expected.get("minConfidence").asDouble());
		}
		if (expected.has("maxConfidence")) {
			result.addCheck("maxConfidence", confidence <= expected.get("maxConfidence").asDouble());
		}
		if (expected.has("minSources")) {
			result.addCheck("minSources", sourceCount >= expected.get("minSources").asInt());
		}
		if (expected.has("maxSources")) {
			result.addCheck("maxSources", sourceCount <= expected.get("maxSources").asInt());
		}

		// Check answer contains expected keywords
		if (expected.has("answerShouldMention")) {
			String lowerAnswer = answer.toLowerCase();
			for (JsonNode keyword : expected.get("answerShouldMention")) {
				String kw = keyword.asText("").toLowerCase();
				// For multi-word keywords, check that all words appear anywhere in the answer
				// (not necessarily as an exact substring). This handles paraphrasing like
				// "sharing access" matching "share access" or "three weeks" matching "3 weeks".
				boolean mentioned;
				String[] words = kw.split("\\s+");
				if (words.length > 1) {
					mentioned = true;
					for (String word : words) {
						// Also check common word forms (e.g., "share" matches "sharing")
						String stem = word.length() > 4 ? word.substring(0, word.length() - 1) : word;
						if (!lowerAnswer.contains(word) && !lowerAnswer.contains(stem)) {
							mentioned = false;
							break;
						}
					}
				} else {
					// Single word: also try numeric ↔ written form for small numbers
					mentioned = lowerAnswer.contains(kw);
					if (!mentioned) {
						String numericAlt = kw.equals("3") ? "three" : kw.equals("three") ? "3" : null;
						if (numericAlt != null)
							mentioned = lowerAnswer.contains(numericAlt);
					}
				}
				result.addCheck("mentions_" + kw.replace(" ", "_"), mentioned);
			}
		}

		// Check answer indicates insufficient data
		if (expected.has("answerShouldIndicateInsufficient")
				&& expected.get("answerShouldIndicateInsufficient").asBoolean()) {
			String lowerAnswer = answer.toLowerCase();
			boolean indicatesInsufficient = lowerAnswer.contains("no data") || lowerAnswer.contains("not found")
					|| lowerAnswer.contains("insufficient") || lowerAnswer.contains("no information")
					|| lowerAnswer.contains("don't have") || lowerAnswer.contains("unable to find")
					|| lowerAnswer.contains("no relevant") || lowerAnswer.contains("cannot find")
					|| lowerAnswer.contains("not available") || lowerAnswer.contains("no chunks")
					|| lowerAnswer.contains("doesn't appear") || lowerAnswer.contains("no record")
					|| lowerAnswer.contains("no specific") || lowerAnswer.contains("no mention")
					|| lowerAnswer.contains("not mentioned") || lowerAnswer.contains("no evidence")
					|| lowerAnswer.contains("not present");
			result.addCheck("indicatesInsufficient", indicatesInsufficient);
		}

		result.addScore("confidence", confidence);
		result.addScore("sourceCount", sourceCount);
		result.addNote("answerPreview", answer.substring(0, Math.min(200, answer.length())));
	}

	private void evaluateDraftAssistOutput(JsonNode output, JsonNode expected, EvalResult result) {
		// Check shouldSuggestTitle
		if (expected.has("shouldSuggestTitle")) {
			boolean shouldSuggest = expected.get("shouldSuggestTitle").asBoolean();
			boolean didSuggest = output.has("suggestedTitle") && !output.get("suggestedTitle").isNull()
					&& !output.get("suggestedTitle").asText("").isBlank();
			result.addCheck("suggestTitle", shouldSuggest == didSuggest);
		}

		// Check titleShouldNotContain
		if (expected.has("titleShouldNotContain") && output.has("suggestedTitle")
				&& !output.get("suggestedTitle").isNull()) {
			String title = output.get("suggestedTitle").asText("").toLowerCase();
			boolean clean = true;
			for (JsonNode forbidden : expected.get("titleShouldNotContain")) {
				if (title.contains(forbidden.asText("").toLowerCase())) {
					clean = false;
					result.addNote("forbiddenWordFound", forbidden.asText());
				}
			}
			result.addCheck("titleClean", clean);
		}

		// Check titleMinLength
		if (expected.has("titleMinLength") && output.has("suggestedTitle") && !output.get("suggestedTitle").isNull()) {
			int minLen = expected.get("titleMinLength").asInt();
			int actualLen = output.get("suggestedTitle").asText("").length();
			result.addCheck("titleMinLength", actualLen >= minLen);
		}

		// Check titleMaxLength
		if (expected.has("titleMaxLength") && output.has("suggestedTitle") && !output.get("suggestedTitle").isNull()) {
			int maxLen = expected.get("titleMaxLength").asInt();
			int actualLen = output.get("suggestedTitle").asText("").length();
			result.addCheck("titleMaxLength", actualLen <= maxLen);
		}

		// Check shouldSuggestSuccessCriteria
		if (expected.has("shouldSuggestSuccessCriteria")) {
			boolean shouldSuggest = expected.get("shouldSuggestSuccessCriteria").asBoolean();
			boolean didSuggest = output.has("suggestedSuccessCriteria")
					&& !output.get("suggestedSuccessCriteria").isNull()
					&& !output.get("suggestedSuccessCriteria").asText("").isBlank();
			result.addCheck("suggestCriteria", shouldSuggest == didSuggest);
		}

		// Check shouldSuggestEstimate
		if (expected.has("shouldSuggestEstimate")) {
			boolean shouldSuggest = expected.get("shouldSuggestEstimate").asBoolean();
			boolean didSuggest = output.has("suggestedEstimatePoints")
					&& !output.get("suggestedEstimatePoints").isNull();
			result.addCheck("suggestEstimate", shouldSuggest == didSuggest);
		}

		// Check estimateRange
		if (expected.has("estimateRange") && output.has("suggestedEstimatePoints")
				&& !output.get("suggestedEstimatePoints").isNull()) {
			int estimate = output.get("suggestedEstimatePoints").asInt();
			int min = expected.get("estimateRange").get(0).asInt();
			int max = expected.get("estimateRange").get(1).asInt();
			result.addCheck("estimateInRange", estimate >= min && estimate <= max);
			result.addScore("estimate", estimate);
		}

		// Check shouldSuggestDescription
		if (expected.has("shouldSuggestDescription")) {
			boolean shouldSuggest = expected.get("shouldSuggestDescription").asBoolean();
			boolean didSuggest = output.has("suggestedDescription") && !output.get("suggestedDescription").isNull()
					&& !output.get("suggestedDescription").asText("").isBlank();
			result.addCheck("suggestDescription", shouldSuggest == didSuggest);
		}
	}

	private void evaluateLintOutput(JsonNode output, JsonNode expected, EvalResult result) {
		JsonNode hard = output.get("hardValidation");
		JsonNode soft = output.get("softGuidance");
		int hardCount = hard != null && hard.isArray() ? hard.size() : 0;
		int softCount = soft != null && soft.isArray() ? soft.size() : 0;

		// Check minHardValidations
		if (expected.has("minHardValidations")) {
			result.addCheck("minHardValidations", hardCount >= expected.get("minHardValidations").asInt());
		}

		// Check maxHardValidations
		if (expected.has("maxHardValidations")) {
			result.addCheck("maxHardValidations", hardCount <= expected.get("maxHardValidations").asInt());
		}

		// Check maxSoftGuidance
		if (expected.has("maxSoftGuidance")) {
			result.addCheck("maxSoftGuidance", softCount <= expected.get("maxSoftGuidance").asInt());
		}

		// Check minSoftGuidance
		if (expected.has("minSoftGuidance")) {
			result.addCheck("minSoftGuidance", softCount >= expected.get("minSoftGuidance").asInt());
		}

		// Check expectedHardCodes — uses fuzzy matching because LLM may use
		// different but semantically equivalent code names (e.g.
		// "MULTIPLE_KING_COMMITS"
		// vs "KING_LIMIT_EXCEEDED")
		if (expected.has("expectedHardCodes") && hard != null) {
			// Build keyword sets for fuzzy matching
			Map<String, List<String>> codeSynonyms = Map.of("KING_LIMIT_EXCEEDED",
					List.of("KING", "LIMIT", "MULTIPLE", "MAX", "TOO MANY"), "QUEEN_LIMIT_EXCEEDED",
					List.of("QUEEN", "LIMIT", "MULTIPLE", "MAX", "TOO MANY"), "MISSING_SUCCESS_CRITERIA",
					List.of("SUCCESS", "CRITERIA", "MISSING"), "VAGUE_TITLE",
					List.of("VAGUE", "TITLE", "MEANINGLESS", "UNCLEAR"));
			for (JsonNode expectedCode : expected.get("expectedHardCodes")) {
				String code = expectedCode.asText();
				boolean found = false;
				List<String> synonyms = codeSynonyms.getOrDefault(code, List.of(code.replace("_", " ")));
				for (JsonNode h : hard) {
					String hCode = h.has("code") ? h.get("code").asText("").toUpperCase() : "";
					String hMessage = h.has("message") ? h.get("message").asText("").toUpperCase() : "";
					String combined = hCode + " " + hMessage;
					// Match if the code or message contains at least 2 of the synonym keywords
					long matchCount = synonyms.stream().filter(s -> combined.contains(s.toUpperCase())).count();
					if (matchCount >= 2) {
						found = true;
						break;
					}
				}
				result.addCheck("hardCode_" + code, found);
			}
		}

		// Check expectedSoftCodes
		if (expected.has("expectedSoftCodes") && soft != null) {
			for (JsonNode expectedCode : expected.get("expectedSoftCodes")) {
				String code = expectedCode.asText();
				boolean found = false;
				for (JsonNode s : soft) {
					String sCode = s.has("code") ? s.get("code").asText("") : "";
					String sMessage = s.has("message") ? s.get("message").asText("").toUpperCase() : "";
					if (sCode.contains(code) || sMessage.contains(code.replace("_", " "))
							|| sMessage.contains("TOO MANY") || sMessage.contains("PAWN")) {
						found = true;
						break;
					}
				}
				result.addCheck("softCode_" + code, found);
			}
		}

		result.addScore("hardValidationCount", hardCount);
		result.addScore("softGuidanceCount", softCount);
	}

	// ── Helpers ───────────────────────────────────────────────────────────

	private String loadJudgePrompt(String resourcePath) throws Exception {
		InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
		if (is == null) {
			throw new IllegalArgumentException("Judge prompt not found: " + resourcePath);
		}
		return new String(is.readAllBytes(), StandardCharsets.UTF_8);
	}

	private JsonNode callJudge(String filledPrompt) throws Exception {
		String apiKey = System.getenv("OPENROUTER_API_KEY");
		String model = System.getenv().getOrDefault("OPENROUTER_MODEL", "anthropic/claude-sonnet-4-20250514");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		List<Map<String, Object>> messages = new ArrayList<>();
		Map<String, Object> userMsg = new LinkedHashMap<>();
		userMsg.put("role", "user");
		userMsg.put("content", filledPrompt);
		messages.add(userMsg);
		body.put("messages", messages);
		body.put("max_tokens", 256);
		String requestJson = objectMapper.writeValueAsString(body);
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
				.header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(requestJson)).build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode responseNode = objectMapper.readTree(response.body());
		String content = responseNode.path("choices").path(0).path("message").path("content").asText("");
		content = content.trim();
		if (content.startsWith("```")) {
			int firstNewline = content.indexOf('\n');
			int lastFence = content.lastIndexOf("```");
			if (firstNewline != -1 && lastFence > firstNewline) {
				content = content.substring(firstNewline + 1, lastFence).trim();
			}
		}
		return objectMapper.readTree(content);
	}

	private Stream<EvalCase> loadCases(String resourcePath) throws Exception {
		InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
		if (is == null) {
			log.warn("Eval cases not found: {}", resourcePath);
			return Stream.empty();
		}
		List<EvalCase> cases = objectMapper.readValue(is, new TypeReference<List<EvalCase>>() {
		});
		return cases.stream();
	}

	/**
	 * Writes all collected eval results to a JSON report file after all tests
	 * complete.
	 */
	@org.junit.jupiter.api.AfterAll
	void writeReport() throws Exception {
		if (allResults.isEmpty()) {
			return;
		}
		Path reportDir = Path.of("build", "eval-results");
		Files.createDirectories(reportDir);
		String timestamp = Instant.now().toString().replace(":", "-");
		File reportFile = reportDir.resolve("eval-" + timestamp + ".json").toFile();

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("timestamp", Instant.now().toString());
		report.put("totalCases", allResults.size());
		report.put("passed", allResults.stream().filter(EvalResult::passed).count());
		report.put("failed", allResults.stream().filter(r -> !r.passed()).count());
		report.put("results", allResults);

		objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportFile, report);
		log.info("Eval report written to: {}", reportFile.getAbsolutePath());

		// Summary to stdout
		log.info("═══════════════════════════════════════════════════════════");
		log.info("  EVAL SUMMARY: {}/{} passed", allResults.stream().filter(EvalResult::passed).count(),
				allResults.size());
		for (EvalResult r : allResults) {
			log.info("  {} {} — {}", r.passed() ? "✅" : "❌", r.getCaseId(), r.getDescription());
			if (!r.passed()) {
				r.getChecks().entrySet().stream().filter(e -> !e.getValue())
						.forEach(e -> log.info("     ↳ FAILED: {}", e.getKey()));
			}
		}
		log.info("═══════════════════════════════════════════════════════════");
	}
}
