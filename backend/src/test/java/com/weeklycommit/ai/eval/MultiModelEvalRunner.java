package com.weeklycommit.ai.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.provider.OpenRouterAiProvider;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * N-way model leaderboard for Weekly Commit AI features.
 *
 * <p>Runs eval datasets <em>in parallel</em> against a configurable model list,
 * with a per-call timeout so a slow model never blocks the others. Results are
 * ranked by a composite score (schema correctness + Opus judge quality + latency)
 * and written to {@code build/eval-results/model-compare-{ts}.json}.
 *
 * <pre>
 *   # Full run (all datasets, ~3-5 min)
 *   ./gradlew modelCompareTest
 *
 *   # Quick quality check — only judge-scored datasets, ~90 seconds
 *   QUICK=true ./gradlew modelCompareTest
 *
 *   # Override model list
 *   COMPARE_MODELS="openai/gpt-4.1,openai/gpt-4.1-mini" ./gradlew modelCompareTest
 * </pre>
 *
 * <p>Environment variables:
 * <ul>
 *   <li>{@code OPENROUTER_API_KEY} — required</li>
 *   <li>{@code COMPARE_MODELS}     — comma-separated model IDs (default: {@link #DEFAULT_MODELS})</li>
 *   <li>{@code QUICK}              — "true" runs only commit-draft + rag-query (~90 s)</li>
 *   <li>{@code CALL_TIMEOUT_SEC}   — per-model call timeout in seconds (default 20)</li>
 *   <li>{@code JUDGE_SAMPLE_SIZE}  — max cases per dataset sent to Opus judge (default 4)</li>
 * </ul>
 *
 * <p>Composite score:
 * <pre>
 *   composite = schemaPassRate   × 0.45
 *             + meanJudgeScore   × 0.35   (0.0 when no judge scores available)
 *             + (1 - normLatency)× 0.20
 * </pre>
 */
@Tag("model-compare")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiModelEvalRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiModelEvalRunner.class);

    // ── Default model roster ────────────────────────────────────────────────────

    static final List<String> DEFAULT_MODELS = List.of(
            // ── OpenAI fast model shootout ──────────────────────────────────────
            "openai/gpt-4o",            // quality anchor from prev run   ($2.50/M in)
            "openai/gpt-4.1",           // newer arch, same price tier    ($2.00/M in)
            "openai/gpt-4.1-mini",      // 5× cheaper than gpt-4o         ($0.40/M in)
            "openai/gpt-4.1-nano",      // ultra-cheap                    ($0.10/M in)
            "openai/gpt-5-mini",        // GPT-5 quality at low cost      ($0.25/M in)
            "openai/gpt-5-nano",        // cheapest model on the menu     ($0.05/M in)
            "openai/gpt-5.4-nano",      // fast 5.4 nano                  ($0.20/M in)
            // ── Reference anchor ───────────────────────────────────────────────
            "google/gemini-2.5-flash"   // speed+quality anchor from prev run
    );

    private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
    private static final int    MAX_TOKENS          = 1024;

    // ── Runtime config (set from env in setUp) ─────────────────────────────────

    private boolean         available;
    private List<String>    models;
    private Map<String, OpenRouterAiProvider> providers;
    private ExecutorService executor;
    private int             callTimeoutSec;   // per-model call hard timeout
    private int             judgeSampleSize;  // max cases sent to Opus judge per dataset
    private boolean         quickMode;        // only run judge-scored datasets

    /** modelId → list of per-case results */
    /** Thread-safe: datasets run in parallel, each appending to the same lists. */
    private final Map<String, List<ModelCaseResult>> resultsByModel = new ConcurrentHashMap<>();

    // ── Setup ──────────────────────────────────────────────────────────────────

    @BeforeAll
    void setUp() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENROUTER_API_KEY not set — model-compare tests will be skipped");
            available = false;
            return;
        }

        String modelsEnv = System.getenv("COMPARE_MODELS");
        models = (modelsEnv != null && !modelsEnv.isBlank())
                ? Arrays.stream(modelsEnv.split(",")).map(String::trim).toList()
                : DEFAULT_MODELS;

        callTimeoutSec = parseInt(System.getenv("CALL_TIMEOUT_SEC"), 20);
        judgeSampleSize = parseInt(System.getenv("JUDGE_SAMPLE_SIZE"), 4);
        quickMode = "true".equalsIgnoreCase(System.getenv("QUICK"));

        // Virtual-thread executor — one thread per in-flight call, no blocking
        executor = Executors.newVirtualThreadPerTaskExecutor();

        providers = new LinkedHashMap<>();
        for (String m : models) {
            providers.put(m, new OpenRouterAiProvider(apiKey, m, MAX_TOKENS, OPENROUTER_BASE_URL, objectMapper));
            resultsByModel.put(m, Collections.synchronizedList(new ArrayList<>()));
        }

        available = true;
        log.info("MultiModelEvalRunner — {} models, timeout={}s, judgeSample={}, quick={}",
                models.size(), callTimeoutSec, judgeSampleSize, quickMode);
        models.forEach(m -> log.info("  • {}", m));
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Datasets ───────────────────────────────────────────────────────────────

    /**
     * Single test method runs ALL datasets in parallel. This is the key fix —
     * previously each dataset was a separate @Test which JUnit ran sequentially.
     * Now they all fire at once and we collect results in one shot.
     */
    @Test
    void runAllDatasets() throws Exception {
        if (!available) { log.info("Skipping — provider unavailable"); return; }

        List<DatasetSpec> specs = quickMode ? quickSpecs() : allSpecs();
        log.info("Running {} datasets in parallel ({} mode)", specs.size(), quickMode ? "QUICK" : "FULL");

        // Launch all datasets concurrently
        List<Future<Void>> futures = specs.stream()
                .map(spec -> executor.submit((Callable<Void>) () -> { runDataset(spec); return null; }))
                .toList();

        // Wait for all — each dataset has its own internal concurrency
        for (Future<?> f : futures) {
            try {
                f.get(1800, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Dataset timed out — partial results included");
            } catch (Exception e) {
                log.warn("Dataset failed: {}", e.getMessage());
            }
        }
    }

    private List<DatasetSpec> allSpecs() {
        return List.of(
                spec("eval/commit-draft-assist/cases.json", AiContext.TYPE_COMMIT_DRAFT,
                        this::buildCommitDraftCtx,  this::validateCommitDraft,  this::judgeCommitDraft),
                spec("eval/commit-lint/cases.json",        AiContext.TYPE_COMMIT_LINT,
                        this::buildCommitLintCtx,   this::validateCommitLint,   null),
                spec("eval/rcdo-suggest/cases.json",       AiContext.TYPE_RCDO_SUGGEST,
                        this::buildRcdoSuggestCtx,  this::validateRcdoSuggest,  null),
                spec("eval/risk-signal/cases.json",        AiContext.TYPE_RISK_SIGNAL,
                        this::buildRiskSignalCtx,   this::validateRiskSignal,   null),
                spec("eval/reconcile-assist/cases.json",   AiContext.TYPE_RECONCILE_ASSIST,
                        this::buildReconcileCtx,    this::validateReconcileAssist, null),
                spec("eval/what-if/cases.json",            AiContext.TYPE_WHAT_IF,
                        this::buildWhatIfCtx,       this::validateWhatIf,       null),
                spec("eval/rag-query/cases.json",          AiContext.TYPE_RAG_QUERY,
                        this::buildRagQueryCtx,     this::validateRagQuery,     this::judgeRagFaithfulness)
        );
    }

    /** Quick mode: only the two judge-scored datasets — done in ~90 seconds */
    private List<DatasetSpec> quickSpecs() {
        return List.of(
                spec("eval/commit-draft-assist/cases.json", AiContext.TYPE_COMMIT_DRAFT,
                        this::buildCommitDraftCtx,  this::validateCommitDraft,  this::judgeCommitDraft),
                spec("eval/rag-query/cases.json",           AiContext.TYPE_RAG_QUERY,
                        this::buildRagQueryCtx,     this::validateRagQuery,     this::judgeRagFaithfulness)
        );
    }

    // ── Core runner ────────────────────────────────────────────────────────────

    private void runDataset(DatasetSpec spec) throws Exception {
        List<EvalCase> cases = loadCases(spec.resourcePath());
        if (cases.isEmpty()) { log.debug("No cases: {}", spec.resourcePath()); return; }

        boolean hasJudge = spec.judgeScorer() != null;
        log.info("[{}] {} cases × {} models (judge on first {} cases)",
                spec.suggestionType(), cases.size(), models.size(),
                hasJudge ? String.valueOf(Math.min(judgeSampleSize, cases.size())) : "0");

        int idx = 0;
        for (EvalCase c : cases) {
            idx++;
            AiContext ctx         = spec.contextBuilder().build(c);
            JudgeScorer judge     = (hasJudge && idx <= judgeSampleSize) ? spec.judgeScorer() : null;
            boolean willJudge     = judge != null;

            // All models for this case fire in parallel, each with a hard timeout
            List<Callable<ModelCaseResult>> tasks = models.stream()
                    .map(m -> (Callable<ModelCaseResult>) () ->
                            callModel(m, c, ctx, spec.schemaValidator(), judge, spec.suggestionType()))
                    .toList();

            List<Future<ModelCaseResult>> futs = executor.invokeAll(tasks);
            for (Future<ModelCaseResult> f : futs) {
                try {
                    ModelCaseResult r = f.get(callTimeoutSec + 5L, TimeUnit.SECONDS);
                    resultsByModel.get(r.modelId()).add(r);
                } catch (TimeoutException e) {
                    log.warn("  Result collection timeout for case {}", c.id());
                } catch (Exception e) {
                    log.debug("  Result collection error: {}", e.getMessage());
                }
            }

            if (willJudge) log.debug("  [{}] case {} judged", spec.suggestionType(), c.id());
        }
    }

    /**
     * Calls one model for one eval case, enforcing {@code callTimeoutSec}.
     * A timed-out call is recorded as unavailable rather than blocking the suite.
     */
    private ModelCaseResult callModel(
            String modelId, EvalCase evalCase, AiContext ctx,
            SchemaValidator sv, JudgeScorer judge, String type) {

        OpenRouterAiProvider provider = providers.get(modelId);
        long start = System.currentTimeMillis();

        // Hard timeout: submit the actual LLM call to a new virtual thread and wait
        AiSuggestionResult result;
        try {
            Future<AiSuggestionResult> callFuture = executor.submit(
                    () -> provider.generateSuggestion(ctx, modelId));
            result = callFuture.get(callTimeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("  TIMEOUT {}ms [{}] {}", callTimeoutSec * 1000, abbrev(modelId), evalCase.id());
            return new ModelCaseResult(modelId, evalCase.id(), type,
                    false, false, callTimeoutSec * 1000L, null, evalCase.critical());
        } catch (Exception e) {
            log.debug("  ERROR [{}] {}: {}", abbrev(modelId), evalCase.id(), e.getMessage());
            result = AiSuggestionResult.unavailable();
        }
        long latencyMs = System.currentTimeMillis() - start;

        boolean schemaValid = false;
        Double  judgeScore  = null;
        JsonNode output     = null;

        if (result.available() && result.payload() != null) {
            try {
                output     = objectMapper.readTree(result.payload());
                schemaValid = sv.isValid(output);
            } catch (Exception ignored) {}
        }

        if (schemaValid && judge != null && output != null) {
            try {
                judgeScore = judge.score(evalCase, output);
            } catch (Exception e) {
                log.debug("  Judge failed [{}] {}: {}", abbrev(modelId), evalCase.id(), e.getMessage());
            }
        }

        String judgeStr = judgeScore != null ? String.format("%.2f", judgeScore) : " n/a";
        log.info("  [{}] {} schema={} judge={} {}ms",
                abbrev(modelId), evalCase.id(), schemaValid ? "✓" : "✗", judgeStr, latencyMs);

        return new ModelCaseResult(modelId, evalCase.id(), type,
                result.available(), schemaValid, latencyMs, judgeScore, evalCase.critical());
    }

    // ── Report ─────────────────────────────────────────────────────────────────

    @AfterAll
    void writeReport() throws Exception {
        if (!available || resultsByModel.values().stream().allMatch(List::isEmpty)) {
            log.info("No results to report");
            return;
        }

        List<ModelSummary> leaderboard = buildLeaderboard();
        Map<String, Object> report     = buildReportMap(leaderboard);

        Path dir  = Path.of("build", "eval-results");
        Files.createDirectories(dir);
        String ts   = Instant.now().toString().replace(":", "-");
        File   file = dir.resolve("model-compare-" + ts + ".json").toFile();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, report);

        printLeaderboard(leaderboard);
        log.info("Report written → {}", file.getName());
    }

    // ── Leaderboard ────────────────────────────────────────────────────────────

    private List<ModelSummary> buildLeaderboard() {
        List<ModelSummary> summaries = models.stream()
                .map(m -> computeSummary(m, resultsByModel.get(m)))
                .collect(Collectors.toCollection(ArrayList::new));

        double maxMs = summaries.stream().mapToDouble(ModelSummary::meanLatencyMs).max().orElse(1);
        double minMs = summaries.stream().mapToDouble(ModelSummary::meanLatencyMs).min().orElse(0);
        double range = Math.max(maxMs - minMs, 1.0);

        for (ModelSummary s : summaries) {
            double normLatency   = (s.meanLatencyMs() - minMs) / range;
            double judgeWeight   = s.hasJudgeScores() ? 0.35 : 0.0;
            double schemaWeight  = s.hasJudgeScores() ? 0.45 : 0.80;
            double composite     = schemaWeight * s.schemaPassRate()
                                 + judgeWeight  * (s.meanJudgeScore() != null ? s.meanJudgeScore() : 0.0)
                                 + 0.20         * (1.0 - normLatency);
            s.setCompositeScore(composite);
        }

        summaries.sort(Comparator.comparingDouble(ModelSummary::compositeScore).reversed());
        for (int i = 0; i < summaries.size(); i++) summaries.get(i).setRank(i + 1);
        return summaries;
    }

    private ModelSummary computeSummary(String modelId, List<ModelCaseResult> results) {
        if (results.isEmpty()) return new ModelSummary(modelId, 0, 0, 0, 0, null, false, 0);
        long total         = results.size();
        long schemaOk      = results.stream().filter(ModelCaseResult::schemaValid).count();
        double meanMs      = results.stream().mapToLong(ModelCaseResult::latencyMs).average().orElse(0);
        long critTotal     = results.stream().filter(ModelCaseResult::critical).count();
        long critOk        = results.stream().filter(r -> r.critical() && r.schemaValid()).count();
        List<Double> scores = results.stream().map(ModelCaseResult::judgeScore)
                .filter(Objects::nonNull).toList();
        Double meanJudge   = scores.isEmpty() ? null
                : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new ModelSummary(modelId, total, schemaOk, meanMs, critOk, meanJudge,
                !scores.isEmpty(), critTotal);
    }

    // ── Console table ──────────────────────────────────────────────────────────

    private void printLeaderboard(List<ModelSummary> lb) {
        String sep = "─".repeat(108);
        log.info("\n{}", sep);
        log.info("  MODEL LEADERBOARD — Weekly Commit AI  (Opus 4.6 judge)");
        log.info(sep);
        log.info("  {:<4} {:<40} {:>9} {:>8} {:>9} {:>9}  {}", "RNK","MODEL","COMPOSITE","SCHEMA%","LATENCY","JUDGE","TAG");
        log.info(sep);
        for (ModelSummary s : lb) {
            String judge = s.meanJudgeScore() != null ? String.format("%.3f", s.meanJudgeScore()) : "  n/a ";
            String price = pricingHint(s.modelId());
            log.info("  {:<4} {:<40} {:>9.4f} {:>7.1f}% {:>7}ms {:>9}  {}",
                    "#" + s.rank(), abbrev(s.modelId()),
                    s.compositeScore(), s.schemaPassRate() * 100,
                    (long) s.meanLatencyMs(), judge, price);
        }
        log.info(sep);
        if (!lb.isEmpty()) {
            ModelSummary top = lb.getFirst();
            log.info("  ★  Recommended: {}  (composite {:.4f}, latency {}ms, judge {})",
                    abbrev(top.modelId()), top.compositeScore(), (long) top.meanLatencyMs(),
                    top.meanJudgeScore() != null ? String.format("%.3f", top.meanJudgeScore()) : "n/a");
        }
        log.info("{}\n", sep);
    }

    /** Rough pricing annotation from hardcoded OpenRouter rates ($/M input tokens). */
    private static String pricingHint(String id) {
        return switch (id) {
            case "openai/gpt-4o"           -> "$2.50/M";
            case "openai/gpt-4.1"          -> "$2.00/M";
            case "openai/gpt-4.1-mini"     -> "$0.40/M";
            case "openai/gpt-4.1-nano"     -> "$0.10/M";
            case "openai/gpt-5-mini"       -> "$0.25/M";
            case "openai/gpt-5-nano"       -> "$0.05/M";
            case "openai/gpt-5.4-nano"     -> "$0.20/M";
            case "openai/gpt-5.4-mini"     -> "$0.75/M";
            case "google/gemini-2.5-flash" -> "~$0.15/M";
            default -> "";
        };
    }

    // ── Report map ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildReportMap(List<ModelSummary> lb) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("timestamp", Instant.now().toString());
        r.put("mode", quickMode ? "QUICK" : "FULL");
        r.put("modelsEvaluated", models);
        r.put("judgeSampleSize", judgeSampleSize);
        r.put("callTimeoutSec", callTimeoutSec);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ModelSummary s : lb) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank",            s.rank());
            row.put("model",           s.modelId());
            row.put("compositeScore",  round2(s.compositeScore()));
            row.put("schemaPassRate",  round2(s.schemaPassRate()));
            row.put("criticalPassRate",round2(s.criticalPassRate()));
            row.put("meanLatencyMs",   (long) s.meanLatencyMs());
            row.put("totalCases",      s.totalCases());
            row.put("pricing",         pricingHint(s.modelId()));
            if (s.meanJudgeScore() != null) row.put("meanJudgeScore", round2(s.meanJudgeScore()));
            rows.add(row);
        }
        r.put("leaderboard", rows);

        // Per-case detail
        Map<String, List<Map<String, Object>>> perCase = new LinkedHashMap<>();
        resultsByModel.values().stream().flatMap(List::stream).forEach(res -> {
            perCase.computeIfAbsent(res.caseId(), k -> new ArrayList<>()).add(Map.of(
                    "model",      res.modelId(),
                    "schema",     res.schemaValid(),
                    "latencyMs",  res.latencyMs(),
                    "judgeScore", res.judgeScore() != null ? round2(res.judgeScore()) : "n/a"
            ));
        });
        r.put("perCase", perCase);

        if (!lb.isEmpty()) {
            r.put("recommendation", lb.getFirst().modelId());
            r.put("summary", String.format("#1 %s — composite=%.4f schema=%.0f%% latency=%dms judge=%s",
                    abbrev(lb.getFirst().modelId()), lb.getFirst().compositeScore(),
                    lb.getFirst().schemaPassRate() * 100, (long) lb.getFirst().meanLatencyMs(),
                    lb.getFirst().meanJudgeScore() != null
                            ? String.format("%.3f", lb.getFirst().meanJudgeScore()) : "n/a"));
        }
        return r;
    }

    // ── Context builders ───────────────────────────────────────────────────────

    private AiContext buildCommitDraftCtx(EvalCase c) {
        return new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null,
                objectMapper.convertValue(c.input(), new TypeReference<>() {}),
                Map.of(), List.of(), List.of(), Map.of());
    }
    private AiContext buildCommitLintCtx(EvalCase c) {
        List<Map<String, Object>> commits = objectMapper.convertValue(c.input().get("commits"), new TypeReference<>() {});
        Map<String, Object> plan = new LinkedHashMap<>();
        if (c.input().has("capacityBudgetPoints"))
            plan.put("capacityBudgetPoints", c.input().get("capacityBudgetPoints").asInt());
        return new AiContext(AiContext.TYPE_COMMIT_LINT, null, null, null,
                Map.of(), plan, commits, List.of(), Map.of());
    }
    private AiContext buildRcdoSuggestCtx(EvalCase c) {
        Map<String, Object> cd = objectMapper.convertValue(c.input().get("commitData"), new TypeReference<>() {});
        List<Map<String, Object>> tree = objectMapper.convertValue(c.input().get("rcdoTree"), new TypeReference<>() {});
        return new AiContext(AiContext.TYPE_RCDO_SUGGEST, null, null, null,
                cd, Map.of(), List.of(), tree, Map.of());
    }
    private AiContext buildRiskSignalCtx(EvalCase c) {
        List<Map<String, Object>> commits = objectMapper.convertValue(c.input().get("commits"), new TypeReference<>() {});
        Map<String, Object> plan = objectMapper.convertValue(c.input().get("planData"), new TypeReference<>() {});
        Map<String, Object> extra = new LinkedHashMap<>();
        if (c.input().has("scopeChanges"))
            extra.put("scopeChanges", objectMapper.convertValue(c.input().get("scopeChanges"), new TypeReference<List<Object>>() {}));
        return new AiContext(AiContext.TYPE_RISK_SIGNAL, null, null, null,
                Map.of(), plan, commits, List.of(), extra);
    }
    private AiContext buildReconcileCtx(EvalCase c) {
        List<Map<String, Object>> commits = objectMapper.convertValue(c.input().get("commits"), new TypeReference<>() {});
        Map<String, Object> plan = objectMapper.convertValue(c.input().get("planData"), new TypeReference<>() {});
        Map<String, Object> extra = new LinkedHashMap<>();
        if (c.input().has("scopeChanges"))
            extra.put("scopeChanges", objectMapper.convertValue(c.input().get("scopeChanges"), new TypeReference<List<Object>>() {}));
        return new AiContext(AiContext.TYPE_RECONCILE_ASSIST, null, null, null,
                Map.of(), plan, commits, List.of(), extra);
    }
    private AiContext buildWhatIfCtx(EvalCase c) {
        return new AiContext(AiContext.TYPE_WHAT_IF, null, null, null,
                Map.of(), Map.of(), List.of(), List.of(),
                objectMapper.convertValue(c.input(), new TypeReference<>() {}));
    }
    private AiContext buildRagQueryCtx(EvalCase c) {
        return new AiContext(AiContext.TYPE_RAG_QUERY, null, null, null,
                Map.of(), Map.of(), List.of(), List.of(),
                objectMapper.convertValue(c.input(), new TypeReference<>() {}));
    }

    // ── Schema validators ──────────────────────────────────────────────────────

    private boolean validateCommitDraft(JsonNode n)     { return n != null && n.has("suggestedTitle") && n.has("suggestedEstimatePoints"); }
    private boolean validateCommitLint(JsonNode n)      { return n != null && n.has("hardValidation") && n.has("softGuidance"); }
    private boolean validateRcdoSuggest(JsonNode n)     { return n != null && n.has("suggestedRcdoNodeId") && n.has("confidence"); }
    private boolean validateRiskSignal(JsonNode n)      { return n != null && n.has("signals") && n.get("signals").isArray(); }
    private boolean validateReconcileAssist(JsonNode n) { return n != null && n.has("likelyOutcomes") && n.has("draftSummary"); }
    private boolean validateWhatIf(JsonNode n)          { return n != null && n.has("narrative"); }
    private boolean validateRagQuery(JsonNode n)        { return n != null && n.has("answer") && n.has("confidence"); }

    // ── Opus judges ────────────────────────────────────────────────────────────

    private Double judgeCommitDraft(EvalCase c, JsonNode out) throws Exception {
        List<Double> scores = new ArrayList<>();
        JsonNode in = c.input();
        if (out.has("suggestedTitle") && !out.get("suggestedTitle").asText("").isBlank()) {
            String p = loadJudgePrompt("eval/judge-prompts/title-quality-judge.txt")
                    .replace("{originalTitle}", in.path("title").asText(""))
                    .replace("{suggestedTitle}", out.get("suggestedTitle").asText(""))
                    .replace("{chessPiece}", in.path("chessPiece").asText(""));
            extractNumericScores(callJudge(p)).forEach(scores::add);
        }
        if (out.has("suggestedSuccessCriteria") && !out.get("suggestedSuccessCriteria").asText("").isBlank()) {
            String p = loadJudgePrompt("eval/judge-prompts/criteria-quality-judge.txt")
                    .replace("{commitTitle}", in.path("title").asText(""))
                    .replace("{chessPiece}", in.path("chessPiece").asText(""))
                    .replace("{suggestedCriteria}", out.get("suggestedSuccessCriteria").asText(""));
            extractNumericScores(callJudge(p)).forEach(scores::add);
        }
        return scores.isEmpty() ? null
                : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private Double judgeRagFaithfulness(EvalCase c, JsonNode out) throws Exception {
        String answer = out.path("answer").asText("");
        if (answer.isBlank()) return null;
        String chunks = objectMapper.writeValueAsString(c.input().path("retrievedChunks"));
        String p = loadJudgePrompt("eval/judge-prompts/faithfulness-judge.txt")
                .replace("{answer}", answer.replace("\\", "\\\\").replace("\"", "\\\""))
                .replace("{retrievedChunks}", chunks);
        JsonNode result = callJudge(p);
        double score = result.path("faithfulnessScore").asDouble(-1.0);
        return score >= 0.0 ? score : null;
    }

    private JsonNode callJudge(String prompt) throws Exception {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "anthropic/claude-opus-4.6");
        body.put("max_tokens", 256);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        var client  = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)).build();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(OPENROUTER_BASE_URL + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://weeklycommit.dev")
                .header("X-Title", "Weekly Commit")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)))
                .build();

        var resp    = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(resp.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("").strip();
        if (content.startsWith("```")) {
            int nl = content.indexOf('\n'), end = content.lastIndexOf("```");
            if (nl != -1 && end > nl) content = content.substring(nl + 1, end).strip();
        }
        return objectMapper.readTree(content);
    }

    private List<Double> extractNumericScores(JsonNode n) {
        List<Double> out = new ArrayList<>();
        n.fields().forEachRemaining(e -> { if (e.getValue().isNumber()) out.add(e.getValue().asDouble()); });
        return out;
    }

    private String loadJudgePrompt(String path) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) throw new IllegalArgumentException("Judge prompt not found: " + path);
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private List<EvalCase> loadCases(String path) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) { log.warn("Eval cases not found: {}", path); return List.of(); }
        return objectMapper.readValue(is, new TypeReference<List<EvalCase>>() {});
    }

    private static String abbrev(String id) {
        int slash = id.indexOf('/');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }

    private static double round2(double v) { return Math.round(v * 10000.0) / 10000.0; }
    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private DatasetSpec spec(String path, String type, ContextBuilder cb, SchemaValidator sv, JudgeScorer js) {
        return new DatasetSpec(path, type, cb, sv, js);
    }

    // ── Records ────────────────────────────────────────────────────────────────

    record ModelCaseResult(String modelId, String caseId, String type,
                           boolean available, boolean schemaValid, long latencyMs,
                           Double judgeScore, boolean critical) {}

    record DatasetSpec(String resourcePath, String suggestionType,
                       ContextBuilder contextBuilder, SchemaValidator schemaValidator,
                       JudgeScorer judgeScorer) {}

    static class ModelSummary {
        private final String  modelId;
        private final long    totalCases, schemaOkCount, critOkCount, critTotal;
        private final double  meanLatencyMs;
        private final Double  meanJudgeScore;
        private final boolean hasJudgeScores;
        private double compositeScore;
        private int    rank;

        ModelSummary(String id, long total, long ok, double ms, long critOk,
                     Double judge, boolean hasJudge, long critTotal) {
            this.modelId = id; this.totalCases = total; this.schemaOkCount = ok;
            this.meanLatencyMs = ms; this.critOkCount = critOk;
            this.meanJudgeScore = judge; this.hasJudgeScores = hasJudge;
            this.critTotal = critTotal;
        }
        String  modelId()        { return modelId; }
        long    totalCases()     { return totalCases; }
        double  meanLatencyMs()  { return meanLatencyMs; }
        Double  meanJudgeScore() { return meanJudgeScore; }
        boolean hasJudgeScores() { return hasJudgeScores; }
        double  compositeScore() { return compositeScore; }
        int     rank()           { return rank; }
        double  schemaPassRate() { return totalCases > 0 ? (double) schemaOkCount / totalCases : 0; }
        double  criticalPassRate(){ return critTotal > 0 ? (double) critOkCount / critTotal : 1.0; }
        void setCompositeScore(double v) { compositeScore = v; }
        void setRank(int r) { rank = r; }
    }

    @FunctionalInterface interface ContextBuilder  { AiContext build(EvalCase c) throws Exception; }
    @FunctionalInterface interface SchemaValidator { boolean isValid(JsonNode out); }
    @FunctionalInterface interface JudgeScorer     { Double score(EvalCase c, JsonNode out) throws Exception; }
}
