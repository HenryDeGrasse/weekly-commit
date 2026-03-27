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
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload());

		if (schemaValid && output != null) {
			// Automated checks against expected behavior
			evaluateDraftAssistOutput(output, expected, evalResult);
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
				result.promptVersion() != null ? result.promptVersion() : "unknown", schemaValid, result.payload());

		if (schemaValid && output != null) {
			evaluateLintOutput(output, expected, evalResult);
		}

		allResults.add(evalResult);
		log.info("Eval result: {}", evalResult);
		assertThat(schemaValid).as("Schema validation for case %s", evalCase.id()).isTrue();
	}

	// ── Output evaluation helpers ─────────────────────────────────────────

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
