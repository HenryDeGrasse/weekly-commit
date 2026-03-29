package com.weeklycommit.ai.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.provider.OpenRouterAiProvider;
import com.weeklycommit.ai.rag.EmbeddingService;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offline A/B comparison runner for evaluating LLM model and embedding model
 * variants side-by-side.
 *
 * <p>
 * Tagged with {@code @Tag("ab-eval")} so it is excluded from both normal
 * {@code ./gradlew test} and the existing {@code evalTest} task. Run explicitly
 * with:
 *
 * <pre>
 * ./gradlew abEvalTest
 * </pre>
 *
 * <p>
 * Environment variables:
 * <ul>
 * <li>{@code OPENROUTER_API_KEY} — required; tests skip gracefully if
 * absent.</li>
 * <li>{@code AB_CONTROL_MODEL} — LLM control arm (default:
 * {@code anthropic/claude-sonnet-4}).</li>
 * <li>{@code AB_TREATMENT_MODEL} — LLM treatment arm (default:
 * {@code google/gemini-2.5-flash-preview}).</li>
 * <li>{@code AB_CONTROL_EMBEDDING} — embedding control model (default:
 * {@code openai/text-embedding-3-small}).</li>
 * <li>{@code AB_TREATMENT_EMBEDDING} — embedding treatment model (default:
 * {@code openai/text-embedding-3-large}). NOTE: this is for offline eval only —
 * the live Pinecone index is fixed at 1536-d and cannot accept 3072-d vectors
 * from text-embedding-3-large.</li>
 * </ul>
 *
 * <p>
 * Output: {@code build/eval-results/ab-comparison-{timestamp}.json} with
 * per-case scores and aggregate summary (mean score, win rate, latency, token
 * usage, and retrieval-quality proxies for embedding comparisons).
 */
@Tag("ab-eval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AbComparisonRunner {

	private static final Logger log = LoggerFactory.getLogger(AbComparisonRunner.class);

	private static final String DEFAULT_CONTROL_MODEL = "anthropic/claude-sonnet-4";
	private static final String DEFAULT_TREATMENT_MODEL = "google/gemini-2.5-flash-preview";
	private static final String DEFAULT_CONTROL_EMBEDDING = "openai/text-embedding-3-small";
	private static final String DEFAULT_TREATMENT_EMBEDDING = "openai/text-embedding-3-large";
	private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
	private static final int MAX_TOKENS = 1024;
	private static final int EMBEDDING_RECALL_K = 3;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient judgeHttpClient = HttpClient.newHttpClient();

	private boolean providerAvailable;
	private String controlModel;
	private String treatmentModel;
	private String controlEmbeddingModel;
	private String treatmentEmbeddingModel;

	private OpenRouterAiProvider controlProvider;
	private OpenRouterAiProvider treatmentProvider;
	private EmbeddingService controlEmbeddingService;
	private EmbeddingService treatmentEmbeddingService;

	private final List<Map<String, Object>> llmCaseResults = new ArrayList<>();
	private final List<Map<String, Object>> embeddingCaseResults = new ArrayList<>();

	@BeforeAll
	void setUp() {
		String apiKey = System.getenv("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			log.warn("OPENROUTER_API_KEY not set — A/B eval tests will be skipped");
			providerAvailable = false;
			return;
		}

		controlModel = System.getenv().getOrDefault("AB_CONTROL_MODEL", DEFAULT_CONTROL_MODEL);
		treatmentModel = System.getenv().getOrDefault("AB_TREATMENT_MODEL", DEFAULT_TREATMENT_MODEL);
		controlEmbeddingModel = System.getenv().getOrDefault("AB_CONTROL_EMBEDDING", DEFAULT_CONTROL_EMBEDDING);
		treatmentEmbeddingModel = System.getenv().getOrDefault("AB_TREATMENT_EMBEDDING", DEFAULT_TREATMENT_EMBEDDING);

		controlProvider = new OpenRouterAiProvider(apiKey, controlModel, MAX_TOKENS, OPENROUTER_BASE_URL, objectMapper);
		treatmentProvider = new OpenRouterAiProvider(apiKey, treatmentModel, MAX_TOKENS, OPENROUTER_BASE_URL,
				objectMapper);
		controlEmbeddingService = new EmbeddingService(apiKey, OPENROUTER_BASE_URL, controlEmbeddingModel,
				objectMapper);
		treatmentEmbeddingService = new EmbeddingService(apiKey, OPENROUTER_BASE_URL, treatmentEmbeddingModel,
				objectMapper);

		providerAvailable = true;
		log.info("A/B Comparison Runner initialised:");
		log.info("  LLM  control:    {}", controlModel);
		log.info("  LLM  treatment:  {}", treatmentModel);
		log.info("  Emb  control:    {}", controlEmbeddingModel);
		log.info("  Emb  treatment:  {}", treatmentEmbeddingModel);
	}

	@Test
	void runLlmAbComparison() throws Exception {
		if (!providerAvailable) {
			log.info("Skipping LLM A/B comparison — provider unavailable");
			return;
		}

		for (DatasetSpec spec : datasetSpecs()) {
			runLlmCasesForDataset(spec);
		}
	}

	@Test
	void runEmbeddingAbComparison() throws Exception {
		if (!providerAvailable) {
			log.info("Skipping embedding A/B comparison — provider unavailable");
			return;
		}

		List<EvalCase> ragCases = loadCases("eval/rag-query/cases.json").toList();
		if (ragCases.isEmpty()) {
			log.info("Skipping embedding A/B comparison — no rag-query cases available");
			return;
		}

		for (EvalCase evalCase : ragCases) {
			EmbeddingQuality controlQuality = scoreEmbeddingRetrieval(evalCase, controlEmbeddingService,
					controlEmbeddingModel);
			EmbeddingQuality treatmentQuality = scoreEmbeddingRetrieval(evalCase, treatmentEmbeddingService,
					treatmentEmbeddingModel);

			Map<String, Object> caseResult = new LinkedHashMap<>();
			caseResult.put("caseId", evalCase.id());
			caseResult.put("description", evalCase.description());
			caseResult.put("query", evalCase.input().path("question").asText(""));
			caseResult.put("controlModel", controlEmbeddingModel);
			caseResult.put("treatmentModel", treatmentEmbeddingModel);
			caseResult.put("controlDimensions", controlQuality.dimensions());
			caseResult.put("treatmentDimensions", treatmentQuality.dimensions());
			caseResult.put("controlSuccess", controlQuality.success());
			caseResult.put("treatmentSuccess", treatmentQuality.success());
			caseResult.put("controlLatencyMs", controlQuality.latencyMs());
			caseResult.put("treatmentLatencyMs", treatmentQuality.latencyMs());
			caseResult.put("controlTopChunkId", controlQuality.topChunkId());
			caseResult.put("treatmentTopChunkId", treatmentQuality.topChunkId());
			caseResult.put("controlRelevantChunkCount", controlQuality.relevantChunkCount());
			caseResult.put("treatmentRelevantChunkCount", treatmentQuality.relevantChunkCount());
			putNullable(caseResult, "controlMrr", controlQuality.mrr());
			putNullable(caseResult, "treatmentMrr", treatmentQuality.mrr());
			putNullable(caseResult, "controlRecallAt3", controlQuality.recallAtK());
			putNullable(caseResult, "treatmentRecallAt3", treatmentQuality.recallAtK());
			putNullable(caseResult, "controlVectorMagnitude", controlQuality.queryMagnitude());
			putNullable(caseResult, "treatmentVectorMagnitude", treatmentQuality.queryMagnitude());
			caseResult.put("winner", determineEmbeddingWinner(controlQuality, treatmentQuality));
			caseResult.put("dimensionMismatch", controlQuality.dimensions() != treatmentQuality.dimensions());

			embeddingCaseResults.add(caseResult);
			log.info(
					"Embedding A/B [{}] — control: {}d mrr={} recall@{}={} {}ms | treatment: {}d mrr={} recall@{}={} {}ms",
					evalCase.id(), controlQuality.dimensions(), controlQuality.mrr(), EMBEDDING_RECALL_K,
					controlQuality.recallAtK(), controlQuality.latencyMs(), treatmentQuality.dimensions(),
					treatmentQuality.mrr(), EMBEDDING_RECALL_K, treatmentQuality.recallAtK(),
					treatmentQuality.latencyMs());
		}
	}

	@AfterAll
	void writeReport() throws Exception {
		if (llmCaseResults.isEmpty() && embeddingCaseResults.isEmpty()) {
			log.info("A/B comparison: no results to report (provider unavailable or no cases loaded)");
			return;
		}

		Path reportDir = Path.of("build", "eval-results");
		Files.createDirectories(reportDir);
		String timestamp = Instant.now().toString().replace(":", "-");
		File reportFile = reportDir.resolve("ab-comparison-" + timestamp + ".json").toFile();

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("timestamp", Instant.now().toString());
		report.put("controlModel", controlModel);
		report.put("treatmentModel", treatmentModel);
		report.put("controlEmbeddingModel", controlEmbeddingModel);
		report.put("treatmentEmbeddingModel", treatmentEmbeddingModel);

		if (!llmCaseResults.isEmpty()) {
			report.put("llmComparison", buildLlmSummary());
		}
		if (!embeddingCaseResults.isEmpty()) {
			report.put("embeddingComparison", buildEmbeddingSummary());
		}

		objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportFile, report);
		log.info("A/B comparison report written to: {}", reportFile.getAbsolutePath());
		logSummary(report);
	}

	private List<DatasetSpec> datasetSpecs() {
		return List.of(
				new DatasetSpec("eval/commit-draft-assist/cases.json", AiContext.TYPE_COMMIT_DRAFT,
						this::buildCommitDraftContext,
						output -> output != null && output.has("suggestedTitle")
								&& output.has("suggestedEstimatePoints"),
						this::scoreCommitDraftJudges),
				new DatasetSpec("eval/commit-lint/cases.json", AiContext.TYPE_COMMIT_LINT, this::buildCommitLintContext,
						output -> output != null && output.has("hardValidation") && output.has("softGuidance"),
						(evalCase, output) -> JudgeOutcome.none()),
				new DatasetSpec("eval/rcdo-suggest/cases.json", AiContext.TYPE_RCDO_SUGGEST,
						this::buildRcdoSuggestContext,
						output -> output != null && output.has("suggestedRcdoNodeId") && output.has("confidence"),
						(evalCase, output) -> JudgeOutcome.none()),
				new DatasetSpec("eval/risk-signal/cases.json", AiContext.TYPE_RISK_SIGNAL, this::buildRiskSignalContext,
						output -> output != null && output.has("signals") && output.get("signals").isArray(),
						(evalCase, output) -> JudgeOutcome.none()),
				new DatasetSpec("eval/reconcile-assist/cases.json", AiContext.TYPE_RECONCILE_ASSIST,
						this::buildReconcileAssistContext,
						output -> output != null && output.has("likelyOutcomes") && output.has("draftSummary"),
						(evalCase, output) -> JudgeOutcome.none()),
				new DatasetSpec("eval/rag-query/cases.json", AiContext.TYPE_RAG_QUERY, this::buildRagQueryContext,
						output -> output != null && output.has("answer") && output.has("confidence"),
						this::scoreRagFaithfulnessJudge),
				new DatasetSpec("eval/what-if/cases.json", AiContext.TYPE_WHAT_IF, this::buildWhatIfContext,
						output -> output != null && output.has("narrative"),
						(evalCase, output) -> JudgeOutcome.none()));
	}

	private void runLlmCasesForDataset(DatasetSpec spec) throws Exception {
		loadCases(spec.resourcePath()).forEach(evalCase -> {
			try {
				runSingleLlmCase(evalCase, spec);
			} catch (Exception e) {
				log.warn("LLM A/B case '{}' failed: {}", evalCase.id(), e.getMessage());
			}
		});
	}

	private void runSingleLlmCase(EvalCase evalCase, DatasetSpec spec) throws Exception {
		AiContext context = spec.contextBuilder().build(evalCase);

		long controlTokensBefore = controlProvider.getTotalTokensUsed();
		long controlStart = System.currentTimeMillis();
		AiSuggestionResult controlResult = controlProvider.generateSuggestion(context);
		long controlLatencyMs = System.currentTimeMillis() - controlStart;
		long controlTokensUsed = controlProvider.getTotalTokensUsed() - controlTokensBefore;
		CaseEvaluation controlEval = evaluateLlmResult(evalCase, controlResult, spec, controlLatencyMs,
				controlTokensUsed, controlModel);

		long treatmentTokensBefore = treatmentProvider.getTotalTokensUsed();
		long treatmentStart = System.currentTimeMillis();
		AiSuggestionResult treatmentResult = treatmentProvider.generateSuggestion(context);
		long treatmentLatencyMs = System.currentTimeMillis() - treatmentStart;
		long treatmentTokensUsed = treatmentProvider.getTotalTokensUsed() - treatmentTokensBefore;
		CaseEvaluation treatmentEval = evaluateLlmResult(evalCase, treatmentResult, spec, treatmentLatencyMs,
				treatmentTokensUsed, treatmentModel);

		String winner = determineLlmWinner(controlEval, treatmentEval);

		Map<String, Object> caseResult = new LinkedHashMap<>();
		caseResult.put("caseId", evalCase.id());
		caseResult.put("description", evalCase.description());
		caseResult.put("resourcePath", spec.resourcePath());
		caseResult.put("suggestionType", spec.suggestionType());
		caseResult.put("critical", evalCase.critical());
		caseResult.put("controlModel", controlModel);
		caseResult.put("treatmentModel", treatmentModel);
		caseResult.put("controlSchemaValid", controlEval.schemaValid());
		caseResult.put("treatmentSchemaValid", treatmentEval.schemaValid());
		caseResult.put("controlLatencyMs", controlEval.latencyMs());
		caseResult.put("treatmentLatencyMs", treatmentEval.latencyMs());
		caseResult.put("controlTokensUsed", controlEval.tokensUsed());
		caseResult.put("treatmentTokensUsed", treatmentEval.tokensUsed());
		caseResult.put("controlPromptVersion", controlEval.promptVersion());
		caseResult.put("treatmentPromptVersion", treatmentEval.promptVersion());
		putNullable(caseResult, "controlJudgeScore", controlEval.judgeScore());
		putNullable(caseResult, "treatmentJudgeScore", treatmentEval.judgeScore());
		if (!controlEval.judgeDimensions().isEmpty()) {
			caseResult.put("controlJudgeDimensions", controlEval.judgeDimensions());
		}
		if (!treatmentEval.judgeDimensions().isEmpty()) {
			caseResult.put("treatmentJudgeDimensions", treatmentEval.judgeDimensions());
		}
		caseResult.put("winner", winner);

		llmCaseResults.add(caseResult);
		log.info(
				"LLM A/B [{}] — control: schema={} judge={} {}ms {}tok | treatment: schema={} judge={} {}ms {}tok | winner: {}",
				evalCase.id(), controlEval.schemaValid(), controlEval.judgeScore(), controlEval.latencyMs(),
				controlEval.tokensUsed(), treatmentEval.schemaValid(), treatmentEval.judgeScore(),
				treatmentEval.latencyMs(), treatmentEval.tokensUsed(), winner);
	}

	private CaseEvaluation evaluateLlmResult(EvalCase evalCase, AiSuggestionResult result, DatasetSpec spec,
			long latencyMs, long tokensUsed, String modelName) {
		boolean schemaValid = false;
		JsonNode output = null;
		JudgeOutcome judgeOutcome = JudgeOutcome.none();
		if (result.available() && result.payload() != null) {
			try {
				output = objectMapper.readTree(result.payload());
				schemaValid = spec.schemaValidator().isValid(output);
				if (schemaValid) {
					judgeOutcome = spec.judgeScorer().score(evalCase, output);
				}
			} catch (Exception e) {
				log.warn("A/B eval parse failed for case {} model {}: {}", evalCase.id(), modelName, e.getMessage());
			}
		}
		return new CaseEvaluation(schemaValid, latencyMs, tokensUsed, result.promptVersion(), judgeOutcome.meanScore(),
				judgeOutcome.dimensions());
	}

	private Map<String, Object> buildLlmSummary() {
		int total = llmCaseResults.size();
		long controlSchemaValidCount = llmCaseResults.stream()
				.filter(r -> Boolean.TRUE.equals(r.get("controlSchemaValid"))).count();
		long treatmentSchemaValidCount = llmCaseResults.stream()
				.filter(r -> Boolean.TRUE.equals(r.get("treatmentSchemaValid"))).count();
		long controlWins = llmCaseResults.stream().filter(r -> "control".equals(r.get("winner"))).count();
		long treatmentWins = llmCaseResults.stream().filter(r -> "treatment".equals(r.get("winner"))).count();
		long ties = llmCaseResults.stream().filter(r -> "tie".equals(r.get("winner"))).count();

		double meanControlLatency = llmCaseResults.stream()
				.mapToLong(r -> ((Number) r.get("controlLatencyMs")).longValue()).average().orElse(0);
		double meanTreatmentLatency = llmCaseResults.stream()
				.mapToLong(r -> ((Number) r.get("treatmentLatencyMs")).longValue()).average().orElse(0);
		long totalControlTokens = llmCaseResults.stream()
				.mapToLong(r -> ((Number) r.get("controlTokensUsed")).longValue()).sum();
		long totalTreatmentTokens = llmCaseResults.stream()
				.mapToLong(r -> ((Number) r.get("treatmentTokensUsed")).longValue()).sum();

		List<Double> controlJudgeScores = extractNullableNumbers(llmCaseResults, "controlJudgeScore");
		List<Double> treatmentJudgeScores = extractNullableNumbers(llmCaseResults, "treatmentJudgeScore");
		List<Double> judgeScoreDeltas = pairedNullableDeltas(llmCaseResults, "controlJudgeScore",
				"treatmentJudgeScore");
		List<Double> latencyDeltas = pairedNumericDeltas(llmCaseResults, "controlLatencyMs", "treatmentLatencyMs");
		List<Double> tokenDeltas = pairedNumericDeltas(llmCaseResults, "controlTokensUsed", "treatmentTokensUsed");

		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("totalCases", total);
		summary.put("controlSchemaValidCount", controlSchemaValidCount);
		summary.put("treatmentSchemaValidCount", treatmentSchemaValidCount);
		summary.put("controlSchemaPassRate", total > 0 ? (double) controlSchemaValidCount / total : 0.0);
		summary.put("treatmentSchemaPassRate", total > 0 ? (double) treatmentSchemaValidCount / total : 0.0);
		summary.put("controlWins", controlWins);
		summary.put("treatmentWins", treatmentWins);
		summary.put("ties", ties);
		summary.put("controlWinRate", total > 0 ? (double) controlWins / total : 0.0);
		summary.put("treatmentWinRate", total > 0 ? (double) treatmentWins / total : 0.0);
		summary.put("meanControlLatencyMs", meanControlLatency);
		summary.put("meanTreatmentLatencyMs", meanTreatmentLatency);
		summary.put("totalControlTokensUsed", totalControlTokens);
		summary.put("totalTreatmentTokensUsed", totalTreatmentTokens);
		summary.put("casesWithJudgeScores", judgeScoreDeltas.size());
		putNullable(summary, "meanControlJudgeScore", mean(controlJudgeScores));
		putNullable(summary, "meanTreatmentJudgeScore", mean(treatmentJudgeScores));
		summary.put("statisticalSummary", buildStatisticalSummary(latencyDeltas, tokenDeltas, judgeScoreDeltas, null));
		summary.put("perCase", llmCaseResults);
		return summary;
	}

	private Map<String, Object> buildEmbeddingSummary() {
		int total = embeddingCaseResults.size();
		long controlSuccessCount = embeddingCaseResults.stream()
				.filter(r -> Boolean.TRUE.equals(r.get("controlSuccess"))).count();
		long treatmentSuccessCount = embeddingCaseResults.stream()
				.filter(r -> Boolean.TRUE.equals(r.get("treatmentSuccess"))).count();
		long controlWins = embeddingCaseResults.stream().filter(r -> "control".equals(r.get("winner"))).count();
		long treatmentWins = embeddingCaseResults.stream().filter(r -> "treatment".equals(r.get("winner"))).count();
		long ties = embeddingCaseResults.stream().filter(r -> "tie".equals(r.get("winner"))).count();

		double meanControlLatency = embeddingCaseResults.stream()
				.mapToLong(r -> ((Number) r.get("controlLatencyMs")).longValue()).average().orElse(0);
		double meanTreatmentLatency = embeddingCaseResults.stream()
				.mapToLong(r -> ((Number) r.get("treatmentLatencyMs")).longValue()).average().orElse(0);

		List<Double> controlMrrs = extractNullableNumbers(embeddingCaseResults, "controlMrr");
		List<Double> treatmentMrrs = extractNullableNumbers(embeddingCaseResults, "treatmentMrr");
		List<Double> controlRecalls = extractNullableNumbers(embeddingCaseResults, "controlRecallAt3");
		List<Double> treatmentRecalls = extractNullableNumbers(embeddingCaseResults, "treatmentRecallAt3");
		List<Double> mrrDeltas = pairedNullableDeltas(embeddingCaseResults, "controlMrr", "treatmentMrr");
		List<Double> recallDeltas = pairedNullableDeltas(embeddingCaseResults, "controlRecallAt3",
				"treatmentRecallAt3");
		List<Double> latencyDeltas = pairedNumericDeltas(embeddingCaseResults, "controlLatencyMs",
				"treatmentLatencyMs");

		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("totalCases", total);
		summary.put("controlModel", controlEmbeddingModel);
		summary.put("treatmentModel", treatmentEmbeddingModel);
		summary.put("controlSuccessCount", controlSuccessCount);
		summary.put("treatmentSuccessCount", treatmentSuccessCount);
		summary.put("controlSuccessRate", total > 0 ? (double) controlSuccessCount / total : 0.0);
		summary.put("treatmentSuccessRate", total > 0 ? (double) treatmentSuccessCount / total : 0.0);
		summary.put("controlWins", controlWins);
		summary.put("treatmentWins", treatmentWins);
		summary.put("ties", ties);
		summary.put("controlWinRate", total > 0 ? (double) controlWins / total : 0.0);
		summary.put("treatmentWinRate", total > 0 ? (double) treatmentWins / total : 0.0);
		summary.put("meanControlLatencyMs", meanControlLatency);
		summary.put("meanTreatmentLatencyMs", meanTreatmentLatency);
		putNullable(summary, "meanControlMrr", mean(controlMrrs));
		putNullable(summary, "meanTreatmentMrr", mean(treatmentMrrs));
		putNullable(summary, "meanControlRecallAt3", mean(controlRecalls));
		putNullable(summary, "meanTreatmentRecallAt3", mean(treatmentRecalls));
		summary.put("statisticalSummary", buildStatisticalSummary(latencyDeltas, null, mrrDeltas, recallDeltas));
		summary.put("note",
				"Retrieval quality is evaluated offline with rag-query fixtures using cosine-similarity ranking, MRR, and recall@3. Treatment embeddings are never sent to the live Pinecone index.");
		summary.put("perCase", embeddingCaseResults);
		return summary;
	}

	private AiContext buildCommitDraftContext(EvalCase evalCase) {
		Map<String, Object> commitData = objectMapper.convertValue(evalCase.input(), new TypeReference<>() {
		});
		return new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null, commitData, Map.of(), List.of(), List.of(),
				Map.of());
	}

	private AiContext buildCommitLintContext(EvalCase evalCase) {
		JsonNode input = evalCase.input();
		List<Map<String, Object>> commits = objectMapper.convertValue(input.get("commits"), new TypeReference<>() {
		});
		Map<String, Object> planData = new LinkedHashMap<>();
		if (input.has("capacityBudgetPoints")) {
			planData.put("capacityBudgetPoints", input.get("capacityBudgetPoints").asInt());
		}
		return new AiContext(AiContext.TYPE_COMMIT_LINT, null, null, null, Map.of(), planData, commits, List.of(),
				Map.of());
	}

	private AiContext buildRcdoSuggestContext(EvalCase evalCase) {
		JsonNode input = evalCase.input();
		Map<String, Object> commitData = objectMapper.convertValue(input.get("commitData"), new TypeReference<>() {
		});
		List<Map<String, Object>> rcdoTree = objectMapper.convertValue(input.get("rcdoTree"), new TypeReference<>() {
		});
		return new AiContext(AiContext.TYPE_RCDO_SUGGEST, null, null, null, commitData, Map.of(), List.of(), rcdoTree,
				Map.of());
	}

	private AiContext buildRiskSignalContext(EvalCase evalCase) {
		JsonNode input = evalCase.input();
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
		return new AiContext(AiContext.TYPE_RISK_SIGNAL, null, null, null, Map.of(), planData, commits, List.of(),
				additionalContext);
	}

	private AiContext buildReconcileAssistContext(EvalCase evalCase) {
		JsonNode input = evalCase.input();
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
		return new AiContext(AiContext.TYPE_RECONCILE_ASSIST, null, null, null, Map.of(), planData, commits, List.of(),
				additionalContext);
	}

	private AiContext buildRagQueryContext(EvalCase evalCase) {
		Map<String, Object> additionalContext = objectMapper.convertValue(evalCase.input(), new TypeReference<>() {
		});
		return new AiContext(AiContext.TYPE_RAG_QUERY, null, null, null, Map.of(), Map.of(), List.of(), List.of(),
				additionalContext);
	}

	private AiContext buildWhatIfContext(EvalCase evalCase) {
		Map<String, Object> additionalContext = objectMapper.convertValue(evalCase.input(), new TypeReference<>() {
		});
		return new AiContext(AiContext.TYPE_WHAT_IF, null, null, null, Map.of(), Map.of(), List.of(), List.of(),
				additionalContext);
	}

	private JudgeOutcome scoreCommitDraftJudges(EvalCase evalCase, JsonNode output) throws Exception {
		List<Double> scores = new ArrayList<>();
		Map<String, Double> dimensions = new LinkedHashMap<>();
		JsonNode input = evalCase.input();

		if (output.has("suggestedTitle") && !output.get("suggestedTitle").isNull()
				&& !output.get("suggestedTitle").asText("").isBlank()) {
			String titlePrompt = loadJudgePrompt("eval/judge-prompts/title-quality-judge.txt");
			titlePrompt = titlePrompt.replace("{originalTitle}", input.path("title").asText(""))
					.replace("{suggestedTitle}", output.get("suggestedTitle").asText(""))
					.replace("{chessPiece}", input.path("chessPiece").asText(""));
			collectJudgeDimensions("titleJudge_", callJudge(titlePrompt), dimensions, scores);
		}

		if (output.has("suggestedSuccessCriteria") && !output.get("suggestedSuccessCriteria").isNull()
				&& !output.get("suggestedSuccessCriteria").asText("").isBlank()) {
			String criteriaPrompt = loadJudgePrompt("eval/judge-prompts/criteria-quality-judge.txt");
			criteriaPrompt = criteriaPrompt.replace("{commitTitle}", input.path("title").asText(""))
					.replace("{chessPiece}", input.path("chessPiece").asText(""))
					.replace("{suggestedCriteria}", output.get("suggestedSuccessCriteria").asText(""));
			collectJudgeDimensions("criteriaJudge_", callJudge(criteriaPrompt), dimensions, scores);
		}

		return scores.isEmpty() ? JudgeOutcome.none() : new JudgeOutcome(mean(scores), dimensions);
	}

	private JudgeOutcome scoreRagFaithfulnessJudge(EvalCase evalCase, JsonNode output) throws Exception {
		String answer = output.path("answer").asText("");
		if (answer.isBlank()) {
			return JudgeOutcome.none();
		}
		String retrievedChunks = objectMapper.writeValueAsString(evalCase.input().path("retrievedChunks"));
		String prompt = loadJudgePrompt("eval/judge-prompts/faithfulness-judge.txt")
				.replace("{answer}", escapeForPrompt(answer)).replace("{retrievedChunks}", retrievedChunks);
		JsonNode judgeResult = callJudge(prompt);
		double faithfulness = judgeResult.path("faithfulnessScore").asDouble(-1.0);
		if (faithfulness < 0.0) {
			return JudgeOutcome.none();
		}
		Map<String, Double> dimensions = Map.of("faithfulnessScore", faithfulness);
		return new JudgeOutcome(faithfulness, dimensions);
	}

	private void collectJudgeDimensions(String prefix, JsonNode judgeNode, Map<String, Double> dimensions,
			List<Double> scores) {
		judgeNode.fields().forEachRemaining(entry -> {
			if (entry.getValue().isNumber()) {
				double value = entry.getValue().asDouble();
				dimensions.put(prefix + entry.getKey(), value);
				scores.add(value);
			}
		});
	}

	private EmbeddingQuality scoreEmbeddingRetrieval(EvalCase evalCase, EmbeddingService embeddingService,
			String model) {
		JsonNode input = evalCase.input();
		String query = input.path("question").asText("");
		if (query.isBlank()) {
			return new EmbeddingQuality(false, 0L, 0, null, null, null, null, 0);
		}

		long start = System.currentTimeMillis();
		float[] queryVector = embeddingService.embed(query, model);
		if (queryVector.length == 0) {
			long latencyMs = System.currentTimeMillis() - start;
			return new EmbeddingQuality(false, latencyMs, 0, null, null, null, null, 0);
		}

		List<RankedChunk> rankedChunks = new ArrayList<>();
		for (JsonNode chunkNode : input.path("retrievedChunks")) {
			String chunkId = chunkNode.path("id").asText("");
			String chunkText = chunkNode.path("metadata").path("text").asText("");
			if (chunkText.isBlank()) {
				continue;
			}
			float[] chunkVector = embeddingService.embed(chunkText, model);
			if (chunkVector.length == 0 || chunkVector.length != queryVector.length) {
				continue;
			}
			rankedChunks.add(new RankedChunk(chunkId, cosineSimilarity(queryVector, chunkVector)));
		}
		long latencyMs = System.currentTimeMillis() - start;
		rankedChunks.sort(Comparator.comparingDouble(RankedChunk::score).reversed());

		Set<String> relevantChunkIds = deriveRelevantChunkIds(evalCase);
		Double mrr = relevantChunkIds.isEmpty() ? null : computeMrr(rankedChunks, relevantChunkIds);
		Double recallAtK = relevantChunkIds.isEmpty()
				? null
				: computeRecallAtK(rankedChunks, relevantChunkIds, EMBEDDING_RECALL_K);
		String topChunkId = rankedChunks.isEmpty() ? null : rankedChunks.getFirst().chunkId();

		return new EmbeddingQuality(true, latencyMs, queryVector.length, mrr, recallAtK, topChunkId,
				computeL2Magnitude(queryVector), relevantChunkIds.size());
	}

	private Set<String> deriveRelevantChunkIds(EvalCase evalCase) {
		Set<String> relevantChunkIds = new LinkedHashSet<>();
		JsonNode expected = evalCase.expectedBehavior();
		List<String> keywords = new ArrayList<>();
		if (expected.has("answerShouldMention") && expected.get("answerShouldMention").isArray()) {
			for (JsonNode keyword : expected.get("answerShouldMention")) {
				String value = keyword.asText("").trim().toLowerCase();
				if (!value.isBlank()) {
					keywords.add(value);
				}
			}
		}

		for (JsonNode chunkNode : evalCase.input().path("retrievedChunks")) {
			String chunkId = chunkNode.path("id").asText("");
			String chunkText = chunkNode.path("metadata").path("text").asText("").toLowerCase();
			if (chunkId.isBlank()) {
				continue;
			}
			if (!keywords.isEmpty() && keywords.stream().anyMatch(chunkText::contains)) {
				relevantChunkIds.add(chunkId);
			}
		}

		if (relevantChunkIds.isEmpty() && !expected.path("answerShouldIndicateInsufficient").asBoolean(false)) {
			String fallbackTopChunkId = evalCase.input().path("retrievedChunks").path(0).path("id").asText("");
			if (!fallbackTopChunkId.isBlank()) {
				relevantChunkIds.add(fallbackTopChunkId);
			}
		}
		return relevantChunkIds;
	}

	private String determineLlmWinner(CaseEvaluation controlEval, CaseEvaluation treatmentEval) {
		if (controlEval.schemaValid() && !treatmentEval.schemaValid()) {
			return "control";
		}
		if (!controlEval.schemaValid() && treatmentEval.schemaValid()) {
			return "treatment";
		}
		if (controlEval.judgeScore() != null && treatmentEval.judgeScore() != null) {
			double delta = treatmentEval.judgeScore() - controlEval.judgeScore();
			if (Math.abs(delta) > 1.0e-9) {
				return delta > 0 ? "treatment" : "control";
			}
		}
		if (treatmentEval.latencyMs() < controlEval.latencyMs()) {
			return "treatment";
		}
		if (controlEval.latencyMs() < treatmentEval.latencyMs()) {
			return "control";
		}
		return "tie";
	}

	private String determineEmbeddingWinner(EmbeddingQuality controlQuality, EmbeddingQuality treatmentQuality) {
		if (controlQuality.success() && !treatmentQuality.success()) {
			return "control";
		}
		if (!controlQuality.success() && treatmentQuality.success()) {
			return "treatment";
		}
		if (controlQuality.mrr() != null && treatmentQuality.mrr() != null) {
			double delta = treatmentQuality.mrr() - controlQuality.mrr();
			if (Math.abs(delta) > 1.0e-9) {
				return delta > 0 ? "treatment" : "control";
			}
		}
		if (controlQuality.recallAtK() != null && treatmentQuality.recallAtK() != null) {
			double delta = treatmentQuality.recallAtK() - controlQuality.recallAtK();
			if (Math.abs(delta) > 1.0e-9) {
				return delta > 0 ? "treatment" : "control";
			}
		}
		if (treatmentQuality.latencyMs() < controlQuality.latencyMs()) {
			return "treatment";
		}
		if (controlQuality.latencyMs() < treatmentQuality.latencyMs()) {
			return "control";
		}
		return "tie";
	}

	private double computeMrr(List<RankedChunk> rankedChunks, Set<String> relevantChunkIds) {
		for (int i = 0; i < rankedChunks.size(); i++) {
			if (relevantChunkIds.contains(rankedChunks.get(i).chunkId())) {
				return 1.0 / (i + 1);
			}
		}
		return 0.0;
	}

	private double computeRecallAtK(List<RankedChunk> rankedChunks, Set<String> relevantChunkIds, int k) {
		if (relevantChunkIds.isEmpty()) {
			return 0.0;
		}
		long hits = rankedChunks.stream().limit(k).map(RankedChunk::chunkId).filter(relevantChunkIds::contains).count();
		return (double) hits / relevantChunkIds.size();
	}

	private double cosineSimilarity(float[] left, float[] right) {
		double dot = 0.0;
		double leftNorm = 0.0;
		double rightNorm = 0.0;
		for (int i = 0; i < left.length; i++) {
			dot += (double) left[i] * right[i];
			leftNorm += (double) left[i] * left[i];
			rightNorm += (double) right[i] * right[i];
		}
		if (leftNorm == 0.0 || rightNorm == 0.0) {
			return 0.0;
		}
		return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
	}

	private double computeL2Magnitude(float[] vector) {
		double sumSq = 0.0;
		for (float value : vector) {
			sumSq += (double) value * value;
		}
		return Math.sqrt(sumSq);
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
		body.put("max_tokens", 256);
		body.put("response_format", Map.of("type", "json_object"));
		body.put("messages", List.of(Map.of("role", "user", "content", filledPrompt)));

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(OPENROUTER_BASE_URL + "/chat/completions"))
				.header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey)
				.header("HTTP-Referer", "https://weeklycommit.dev").header("X-Title", "Weekly Commit")
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();

		HttpResponse<String> response = judgeHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode responseNode = objectMapper.readTree(response.body());
		String content = responseNode.path("choices").path(0).path("message").path("content").asText("").trim();
		if (content.startsWith("```")) {
			int firstNewline = content.indexOf('\n');
			int lastFence = content.lastIndexOf("```");
			if (firstNewline != -1 && lastFence > firstNewline) {
				content = content.substring(firstNewline + 1, lastFence).trim();
			}
		}
		return objectMapper.readTree(content);
	}

	private String escapeForPrompt(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void putNullable(Map<String, Object> target, String key, Object value) {
		if (value != null) {
			target.put(key, value);
		}
	}

	private List<Double> extractNullableNumbers(List<Map<String, Object>> rows, String key) {
		return rows.stream().map(row -> row.get(key)).filter(Number.class::isInstance).map(Number.class::cast)
				.map(Number::doubleValue).toList();
	}

	private List<Double> pairedNullableDeltas(List<Map<String, Object>> rows, String controlKey, String treatmentKey) {
		List<Double> deltas = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			Object control = row.get(controlKey);
			Object treatment = row.get(treatmentKey);
			if (control instanceof Number controlNumber && treatment instanceof Number treatmentNumber) {
				deltas.add(treatmentNumber.doubleValue() - controlNumber.doubleValue());
			}
		}
		return deltas;
	}

	private List<Double> pairedNumericDeltas(List<Map<String, Object>> rows, String controlKey, String treatmentKey) {
		return rows.stream().map(
				row -> ((Number) row.get(treatmentKey)).doubleValue() - ((Number) row.get(controlKey)).doubleValue())
				.toList();
	}

	private Map<String, Object> buildStatisticalSummary(List<Double> latencyDeltas, List<Double> tokenDeltas,
			List<Double> primaryScoreDeltas, List<Double> secondaryScoreDeltas) {
		Map<String, Object> statisticalSummary = new LinkedHashMap<>();
		putNullable(statisticalSummary, "meanLatencyDeltaMs", mean(latencyDeltas));
		putNullable(statisticalSummary, "medianLatencyDeltaMs", median(latencyDeltas));
		if (tokenDeltas != null) {
			putNullable(statisticalSummary, "meanTokenDelta", mean(tokenDeltas));
			putNullable(statisticalSummary, "medianTokenDelta", median(tokenDeltas));
		}
		if (primaryScoreDeltas != null) {
			putNullable(statisticalSummary, "meanPrimaryScoreDelta", mean(primaryScoreDeltas));
			putNullable(statisticalSummary, "medianPrimaryScoreDelta", median(primaryScoreDeltas));
		}
		if (secondaryScoreDeltas != null) {
			putNullable(statisticalSummary, "meanSecondaryScoreDelta", mean(secondaryScoreDeltas));
			putNullable(statisticalSummary, "medianSecondaryScoreDelta", median(secondaryScoreDeltas));
		}
		return statisticalSummary;
	}

	private Double mean(List<Double> values) {
		return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
	}

	private Double median(List<Double> values) {
		if (values.isEmpty()) {
			return null;
		}
		List<Double> sorted = values.stream().sorted().toList();
		int middle = sorted.size() / 2;
		if (sorted.size() % 2 == 0) {
			return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
		}
		return sorted.get(middle);
	}

	private void logSummary(Map<String, Object> report) {
		log.info("═══════════════════════════════════════════════════════════");
		log.info("  A/B COMPARISON SUMMARY");
		@SuppressWarnings("unchecked")
		Map<String, Object> llm = (Map<String, Object>) report.get("llmComparison");
		if (llm != null) {
			log.info("  LLM  control='{}' treatment='{}'", controlModel, treatmentModel);
			log.info("  Cases: {} | Control wins: {} | Treatment wins: {} | Ties: {}", llm.get("totalCases"),
					llm.get("controlWins"), llm.get("treatmentWins"), llm.get("ties"));
			log.info("  Schema pass rate — control: {} | treatment: {}", llm.get("controlSchemaPassRate"),
					llm.get("treatmentSchemaPassRate"));
			log.info("  Mean judge score — control: {} | treatment: {}", llm.get("meanControlJudgeScore"),
					llm.get("meanTreatmentJudgeScore"));
			log.info("  Mean latency — control: {}ms | treatment: {}ms", llm.get("meanControlLatencyMs"),
					llm.get("meanTreatmentLatencyMs"));
			log.info("  Total tokens — control: {} | treatment: {}", llm.get("totalControlTokensUsed"),
					llm.get("totalTreatmentTokensUsed"));
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> emb = (Map<String, Object>) report.get("embeddingComparison");
		if (emb != null) {
			log.info("  Embedding  control='{}' treatment='{}'", controlEmbeddingModel, treatmentEmbeddingModel);
			log.info("  Cases: {} | Control wins: {} | Treatment wins: {} | Ties: {}", emb.get("totalCases"),
					emb.get("controlWins"), emb.get("treatmentWins"), emb.get("ties"));
			log.info("  Mean MRR — control: {} | treatment: {}", emb.get("meanControlMrr"),
					emb.get("meanTreatmentMrr"));
			log.info("  Mean recall@3 — control: {} | treatment: {}", emb.get("meanControlRecallAt3"),
					emb.get("meanTreatmentRecallAt3"));
			log.info("  Mean latency — control: {}ms | treatment: {}ms", emb.get("meanControlLatencyMs"),
					emb.get("meanTreatmentLatencyMs"));
		}
		log.info("═══════════════════════════════════════════════════════════");
	}

	private record DatasetSpec(String resourcePath, String suggestionType, ContextBuilder contextBuilder,
			SchemaValidator schemaValidator, JudgeScorer judgeScorer) {
	}

	private record CaseEvaluation(boolean schemaValid, long latencyMs, long tokensUsed, String promptVersion,
			Double judgeScore, Map<String, Double> judgeDimensions) {
	}

	private record JudgeOutcome(Double meanScore, Map<String, Double> dimensions) {

		static JudgeOutcome none() {
			return new JudgeOutcome(null, Map.of());
		}
	}

	private record RankedChunk(String chunkId, double score) {
	}

	private record EmbeddingQuality(boolean success, long latencyMs, int dimensions, Double mrr, Double recallAtK,
			String topChunkId, Double queryMagnitude, int relevantChunkCount) {
	}

	@FunctionalInterface
	private interface ContextBuilder {

		AiContext build(EvalCase evalCase) throws Exception;
	}

	@FunctionalInterface
	private interface SchemaValidator {

		boolean isValid(JsonNode output);
	}

	@FunctionalInterface
	private interface JudgeScorer {

		JudgeOutcome score(EvalCase evalCase, JsonNode output) throws Exception;
	}
}
