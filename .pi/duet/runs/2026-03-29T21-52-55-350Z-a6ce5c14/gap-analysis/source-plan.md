# AI Pipeline Optimization: Prompt Caching, A/B Testing, HyDE, SQL Routing, Chunk Analysis

## Goal

Add six optimization capabilities to the AI pipeline:

1. **Prompt Caching** — Enable Anthropic prompt caching via OpenRouter for all 12 prompt templates (targeting 50–90% cost reduction on repeated system prompts)
2. **A/B Testing Infrastructure** — Create a lightweight experiment framework that can toggle embedding models, LLM models, and HyDE on/off, recording variants in audit records
3. **Embedding Model A/B** — Support switching between `openai/text-embedding-3-small` (current) and `openai/text-embedding-3-large` via experiment config
4. **LLM Model A/B** — Support per-suggestion-type model override (current `anthropic/claude-sonnet-4` vs `google/gemini-2.5-flash-preview` via OpenRouter) 
5. **HyDE (Hypothetical Document Embeddings)** — Generate a hypothetical answer before embedding, as an A/B-testable feature in the RAG pipeline
6. **SQL Query Routing** — Detect analytical/aggregate queries in intent classification and route them to read models (UserWeekFact, TeamWeekRollup, RcdoWeekRollup, CarryForwardFact, ComplianceFact) instead of vector search
7. **Chunk Size Analysis** — Add monitoring/metrics for chunk token counts with configurable max size

All changes MUST maintain backward compatibility — the system must work identically with all experiments disabled (the default).

## Constraints

- Java 21, Spring Boot 3.4, no new framework dependencies
- All A/B test features default to OFF (current behavior preserved)
- OpenRouter is the sole LLM gateway — no direct Anthropic/OpenAI/Google API calls
- Existing eval test suite (`PromptEvalRunner`) must still pass
- No frontend changes in this plan
- No database schema changes — experiment tracking uses existing `AiSuggestion` fields (`prompt_version` and `model_version`)
- Do NOT modify any files in `frontend/`, `packages/`, or `e2e/`

## Steps

### Step 1: Prompt Caching in OpenRouterAiProvider

Add Anthropic prompt caching support to the OpenRouter provider. When the model string starts with `anthropic/`, add `cache_control: {"type": "ephemeral"}` to the system message in the chat completions request. This tells Anthropic to cache the system prompt prefix, reducing cost by ~90% and latency by ~85% for repeated prompts.

**Scope:** Only modify `OpenRouterAiProvider.java`. Do NOT touch any other AI service files.

**Files to modify:**
- `backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java`
  - In `callOpenRouter()`, when building the messages array, if `model.startsWith("anthropic/")`, add a `cache_control` object `{"type": "ephemeral"}` to the system message object
  - The system message should be: `{"role": "system", "content": "...", "cache_control": {"type": "ephemeral"}}`
  - Add a log line at INFO level on first startup: `"Prompt caching enabled for Anthropic models"`
  - Track cache hit metrics: parse the response `usage` object for `cache_creation_input_tokens` and `cache_read_input_tokens` fields, log them, and expose via atomic counters (like existing `totalTokensUsed`)

**Files to modify for test:**
- `backend/src/test/java/com/weeklycommit/ai/provider/OpenRouterAiProviderTest.java` — if this file exists, add a test verifying cache_control is added for anthropic models and NOT added for non-anthropic models

**Checks:** static, unit

### Step 2: A/B Testing Infrastructure

Create a lightweight experiment service that assigns variants based on configuration. No new dependencies — just config-driven variant selection recorded in audit trails.

**Scope:** Create 2 new files. Do NOT modify existing service files yet — that happens in later steps.

**Files to create:**
- `backend/src/main/java/com/weeklycommit/ai/experiment/ExperimentConfig.java`
  - A `@ConfigurationProperties(prefix = "ai.experiments")` class
  - Contains a `Map<String, ExperimentDefinition> experiments` (keyed by experiment name)
  - `ExperimentDefinition` has: `boolean enabled`, `double controlWeight` (0.0–1.0, default 1.0 = all traffic goes to control), `String controlValue`, `String treatmentValue`, `List<String> appliesTo` (list of suggestion types this experiment affects, empty = all)
  - Add `@EnableConfigurationProperties(ExperimentConfig.class)` annotation

- `backend/src/main/java/com/weeklycommit/ai/experiment/ExperimentService.java`
  - `@Service` class
  - `public ExperimentAssignment assign(String experimentName, UUID userId)` — returns which variant to use based on:
    1. Environment variable override: `AB_FORCE_{EXPERIMENT_NAME}=control|treatment` (for eval runs)
    2. Config weight: `Math.random() < controlWeight ? CONTROL : TREATMENT`
  - `ExperimentAssignment` record: `String experimentName, String variant, String value`
  - `public boolean isEnabled(String experimentName)` — checks if experiment exists and is enabled
  - `public String resolveValue(String experimentName, UUID userId)` — convenience: returns the value (controlValue or treatmentValue) for the assigned variant
  - `public Map<String, String> getActiveAssignments(UUID userId)` — returns all active experiment assignments for recording in audit

**Files to modify:**
- `backend/src/main/resources/application.yml` — add experiment config block:
  ```yaml
  ai:
    experiments:
      experiments:
        embedding-model:
          enabled: false
          control-weight: 0.5
          control-value: "openai/text-embedding-3-small"
          treatment-value: "openai/text-embedding-3-large"
        llm-model:
          enabled: false
          control-weight: 0.5
          control-value: "anthropic/claude-sonnet-4"
          treatment-value: "google/gemini-2.5-flash-preview"
          applies-to:
            - COMMIT_LINT
            - RAG_INTENT
            - RAG_QUERY
        hyde:
          enabled: false
          control-weight: 0.5
          control-value: "disabled"
          treatment-value: "enabled"
          applies-to:
            - RAG_QUERY
  ```

**Files to create for test:**
- `backend/src/test/java/com/weeklycommit/ai/experiment/ExperimentServiceTest.java`
  - Test default behavior (disabled experiments return control)
  - Test env var override
  - Test weight-based assignment
  - Test `getActiveAssignments`

**Checks:** static, unit

### Step 3: Embedding Model A/B + Chunk Size Analysis

Modify `EmbeddingService` to support configurable model selection via the experiment framework. Add chunk size monitoring.

**Scope:** Modify `EmbeddingService.java`, create `ChunkSizeAnalyzer.java`. Do NOT modify `SemanticQueryService` yet.

**Files to modify:**
- `backend/src/main/java/com/weeklycommit/ai/rag/EmbeddingService.java`
  - Add a new method: `public float[] embed(String text, String modelOverride)` that uses the specified model instead of the default
  - The original `embed(String text)` delegates to the new method with `this.model` as the default
  - Add an `int getDimensions(String model)` method that returns 1536 for text-embedding-3-small, 3072 for text-embedding-3-large (used by callers that need to know vector size)
  - Track per-model call counts via atomic counters for observability

**Files to create:**
- `backend/src/main/java/com/weeklycommit/ai/rag/ChunkSizeAnalyzer.java`
  - `@Component` class
  - `public ChunkStats analyze(ChunkBuilder.ChunkData chunk)` — estimates token count (~4 chars per token), text length in chars, metadata key count
  - `ChunkStats` record: `int estimatedTokens, int charLength, int metadataKeyCount, boolean exceedsRecommendedSize`
  - `exceedsRecommendedSize` is true when estimatedTokens > 625 (= 2500 chars / 4, the "context cliff" threshold from research)
  - Integrate with Micrometer: register a `DistributionSummary` named `ai.chunk.tokens` that records the token count of each analyzed chunk
  - `public void logWarningIfOversized(ChunkBuilder.ChunkData chunk)` — logs WARN if chunk exceeds threshold

**Files to modify:**
- `backend/src/main/java/com/weeklycommit/ai/rag/SemanticIndexService.java`
  - In `upsertChunk()`, after building the chunk, call `chunkSizeAnalyzer.analyze(chunk)` and `chunkSizeAnalyzer.logWarningIfOversized(chunk)` to track chunk sizes
  - Inject `ChunkSizeAnalyzer` via constructor

**Files to create for test:**
- `backend/src/test/java/com/weeklycommit/ai/rag/ChunkSizeAnalyzerTest.java`
  - Test token estimation
  - Test warning threshold
  - Test with real ChunkBuilder output (build a commit chunk with full enrichment and verify size)

**Checks:** static, unit

### Step 4: LLM Model A/B + Experiment-Aware Provider

Modify the AI provider to support per-request model override driven by experiments. Wire experiment assignments into audit records.

**Scope:** Modify `OpenRouterAiProvider.java` and `AiProviderRegistry.java`. Do NOT modify `SemanticQueryService` yet.

**Files to modify:**
- `backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java`
  - Add a new method: `public AiSuggestionResult generateSuggestion(AiContext context, String modelOverride)`
  - The original `generateSuggestion(AiContext context)` calls the new method with `null` (use default)
  - In `callOpenRouter()`, accept a `modelOverride` parameter. When non-null, use it instead of `this.model`
  - **Important:** When `modelOverride` is set and does NOT start with `anthropic/`, do NOT add `cache_control` to the system message (cache_control is Anthropic-specific)
  - Also: when using non-Anthropic models, do NOT set `response_format: json_object` unless the model supports it. For `google/gemini-*` models, this is supported. Log the actual model used.

- `backend/src/main/java/com/weeklycommit/ai/provider/AiProviderRegistry.java`
  - Inject `ExperimentService`
  - Add a new method: `public AiSuggestionResult generateSuggestion(AiContext context, UUID userId)`
  - This method:
    1. Checks if the `llm-model` experiment is enabled and applies to this suggestion type
    2. If yes, resolves the variant and calls `provider.generateSuggestion(context, modelOverride)`
    3. If no, calls the normal `provider.generateSuggestion(context)`
    4. Records experiment assignments in the result's metadata
  - The original `generateSuggestion(AiContext)` is unchanged (backward compatible)

- `backend/src/main/java/com/weeklycommit/ai/provider/AiProvider.java`
  - Add a default method: `default AiSuggestionResult generateSuggestion(AiContext context, String modelOverride) { return generateSuggestion(context); }`

- `backend/src/main/java/com/weeklycommit/ai/provider/AiSuggestionResult.java`
  - Add an optional `Map<String, String> experimentAssignments` field (6th parameter)
  - Add a backwards-compatible constructor that defaults to `Map.of()`
  - Update `unavailable()` factory

**Checks:** static, unit

### Step 5: HyDE Service + SQL Query Router

Create two new pipeline components: HyDE for improved embedding, and SQL query routing for analytical questions.

**Scope:** Create 3 new files + 1 new prompt template. Do NOT modify `SemanticQueryService` yet — that's Step 6.

**Files to create:**
- `backend/src/main/resources/prompts/hyde.txt`
  ```
  You are a planning analyst for Weekly Commit, a weekly work commitment tool.

  Given a question about team planning data, write a SHORT hypothetical answer (2-3 sentences) as if you had the data. Include specific but plausible details: team member names, commit titles, chess pieces (KING/QUEEN/ROOK/BISHOP/KNIGHT/PAWN), point values, RCDO references, and week dates.

  This hypothetical answer will be used for semantic search — its purpose is to be embedded and matched against real data chunks. It does NOT need to be factually correct.

  Respond with ONLY valid JSON (no markdown fences):
  {"hypotheticalAnswer": "A 2-3 sentence answer with plausible planning details"}
  ```

- `backend/src/main/java/com/weeklycommit/ai/rag/HydeService.java`
  - `@Service` class
  - `public HydeResult generateHypothetical(String question, UUID userId)` — calls the LLM with the hyde prompt template to generate a hypothetical answer
  - `HydeResult` record: `boolean available, String hypotheticalAnswer`
  - Uses `AiProviderRegistry.generateSuggestion()` with type `"HYDE"`
  - Graceful degradation: if LLM call fails, returns `HydeResult(false, null)`
  - Add `TYPE_HYDE = "HYDE"` constant to `AiContext`

- `backend/src/main/resources/prompts/sql-synthesis.txt`
  ```
  You are an AI assistant for Weekly Commit. Answer the user's analytical question using ONLY the structured data provided below.

  The data comes from pre-computed read models (aggregate fact tables). Each result set is labeled with its source table.

  ## Input:
  - question: the user's question
  - sqlResults: structured data from read models

  ## Rules:
  1. Use ONLY the provided data — do not invent numbers or trends
  2. Be specific: cite exact values, dates, team names, and percentages
  3. For trend questions, compare across weeks explicitly
  4. Keep the answer under 200 words
  5. If the data doesn't fully answer the question, say what's missing

  Respond with ONLY valid JSON:
  {
    "answer": "Direct analytical answer with specific numbers",
    "dataSource": "Which read model(s) were used",
    "confidence": 0.85
  }
  ```

- `backend/src/main/java/com/weeklycommit/ai/rag/SqlQueryRouter.java`
  - `@Service` class  
  - `public boolean canHandle(String intent, List<String> keywords)` — returns true for intent types that map to analytical queries:
    - `"analytical"` intent (new)
    - `"status_query"` with aggregation keywords: "total", "average", "most", "least", "how many", "percentage", "rate", "trend", "compare", "ranking"
    - `"compliance_query"` (new)
  - `public SqlQueryResult query(String question, String intent, List<String> keywords, UUID teamId, String timeRangeFrom, String timeRangeTo, UUID userId)` — executes the appropriate read model queries and synthesizes an answer via LLM
  - Internally:
    1. Maps intent+keywords to which read model(s) to query
    2. Executes JPA queries against: `UserWeekFactRepository`, `TeamWeekRollupRepository`, `RcdoWeekRollupRepository`, `CarryForwardFactRepository`, `ComplianceFactRepository`
    3. Formats query results as JSON context
    4. Calls LLM with `sql-synthesis.txt` prompt template for natural language answer
  - `SqlQueryResult` record: `boolean available, String answer, String dataSource, double confidence, UUID suggestionId`
  - Intent → read model mapping:
    - Keywords contain "carry forward" / "CF" → `CarryForwardFactRepository`
    - Keywords contain "compliance" / "lock" / "reconcile" → `ComplianceFactRepository`
    - Keywords contain "RCDO" / "investment" / "coverage" → `RcdoWeekRollupRepository`
    - Keywords contain "team" / "points" / "achieved" → `TeamWeekRollupRepository`
    - Default for user-specific: `UserWeekFactRepository`

**Files to modify:**
- `backend/src/main/java/com/weeklycommit/ai/provider/AiContext.java` — add `TYPE_HYDE = "HYDE"` and `TYPE_SQL_SYNTHESIS = "SQL_SYNTHESIS"` constants
- `backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java` — add `case AiContext.TYPE_HYDE -> "hyde";` and `case AiContext.TYPE_SQL_SYNTHESIS -> "sql-synthesis";` to `loadPromptTemplate()` and `resolvePromptVersion()`
- `backend/src/main/resources/prompts/rag-intent.txt` — add two new intent types `"analytical"` (for aggregate/comparison questions) and `"compliance_query"` to the classification rules and examples:
  - Add this example: `Question: "Who carried forward the most last quarter?" → {"intent":"analytical","userFilter":"team","entityTypes":["carry_forward"],...,"keywords":["carried forward","most","quarter"]}`
  - Add this example: `Question: "What's our team's lock compliance rate?" → {"intent":"compliance_query","userFilter":"team","entityTypes":[],...,"keywords":["lock","compliance","rate"]}`
  - Add this to the `intent` enum in the response spec: `"analytical|compliance_query"`

**Files to create for test:**
- `backend/src/test/java/com/weeklycommit/ai/rag/HydeServiceTest.java` — test with stub provider
- `backend/src/test/java/com/weeklycommit/ai/rag/SqlQueryRouterTest.java` — test intent detection, keyword matching

**Checks:** static, unit

### Step 6: Wire Everything into SemanticQueryService

Integrate all new components into the RAG query pipeline with experiment-aware routing.

**Scope:** Modify `SemanticQueryService.java` only. This is the integration step.

**Files to modify:**
- `backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java`
  - Add constructor dependencies: `ExperimentService`, `HydeService`, `SqlQueryRouter`, `ChunkSizeAnalyzer`
  - Modify the `query()` method pipeline to add these steps between intent classification and Pinecone query:

  **Step 2.5 — SQL routing check:**
  ```java
  // After intent classification, before embedding
  if (sqlQueryRouter.canHandle(intent.intent(), intent.keywords())) {
      SqlQueryResult sqlResult = sqlQueryRouter.query(question, intent.intent(), 
          intent.keywords(), teamId, intent.timeRangeFrom(), intent.timeRangeTo(), userId);
      if (sqlResult.available()) {
          return new RagQueryResult(true, sqlResult.answer(), List.of(), 
              sqlResult.confidence(), sqlResult.suggestionId());
      }
      // Fall through to vector search if SQL routing fails
  }
  ```

  **Step 3 — HyDE experiment check:**
  ```java
  // Before embedding, check HyDE experiment
  String textToEmbed = question;
  if (experimentService.isEnabled("hyde")) {
      String hydeVariant = experimentService.resolveValue("hyde", userId);
      if ("enabled".equals(hydeVariant)) {
          HydeResult hydeResult = hydeService.generateHypothetical(question, userId);
          if (hydeResult.available()) {
              textToEmbed = hydeResult.hypotheticalAnswer();
              log.debug("SemanticQueryService: using HyDE — embedding hypothetical answer");
          }
      }
  }
  ```

  **Step 3 — Embedding model experiment check:**
  ```java
  // Before embedding, check embedding model experiment
  String embeddingModel = null;
  if (experimentService.isEnabled("embedding-model")) {
      embeddingModel = experimentService.resolveValue("embedding-model", userId);
  }
  float[] vector = embeddingModel != null 
      ? embeddingService.embed(textToEmbed, embeddingModel)
      : embeddingService.embed(textToEmbed);
  ```

  **Step 6 — LLM model experiment for answer generation:**
  ```java
  // Use experiment-aware provider for RAG answer generation
  AiSuggestionResult llmResult = aiProviderRegistry.generateSuggestion(ragContext, userId);
  ```

  - Record experiment assignments in the audit context string

**Files to create for test:**
- `backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceIntegrationTest.java`
  - Test SQL routing bypass (analytical query goes to SQL router, not Pinecone)
  - Test HyDE path (when experiment enabled, hypothetical answer is generated)
  - Test default path unchanged when experiments disabled

**Checks:** static, unit

### Step 7: Extended Eval Runner for A/B Comparison

Add an A/B comparison mode to the eval runner that runs each test case against two model variants and compares scores.

**Scope:** Create 1 new test file. Do NOT modify `PromptEvalRunner.java` — create a separate runner.

**Files to create:**
- `backend/src/test/java/com/weeklycommit/ai/eval/AbComparisonRunner.java`
  - `@Tag("ab-eval")` — separate from normal eval runs
  - Reads the same golden datasets as `PromptEvalRunner`
  - For each case, runs against TWO models: the control and treatment (configured via env vars `AB_CONTROL_MODEL` and `AB_TREATMENT_MODEL`)
  - Creates two `OpenRouterAiProvider` instances, one per model
  - Compares: schema validity, judge scores, latency, token usage
  - Writes a comparison report to `build/eval-results/ab-comparison-{timestamp}.json` with:
    - Per-case: control score, treatment score, delta, winner
    - Aggregate: mean score per model, win rate, statistical summary
  - Similarly supports `AB_CONTROL_EMBEDDING` and `AB_TREATMENT_EMBEDDING` for embedding model comparison

- Add to `backend/build.gradle.kts`:
  - A new task `abEvalTest` that includes the `ab-eval` tag:
    ```kotlin
    tasks.register<Test>("abEvalTest") {
        description = "Run A/B comparison eval tests (requires OPENROUTER_API_KEY + AB_* env vars)"
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform { includeTags("ab-eval") }
        systemProperty("junit.jupiter.execution.timeout.default", "300s")
        environment("OPENROUTER_API_KEY", System.getenv("OPENROUTER_API_KEY") ?: "")
        environment("AB_CONTROL_MODEL", System.getenv("AB_CONTROL_MODEL") ?: "anthropic/claude-sonnet-4")
        environment("AB_TREATMENT_MODEL", System.getenv("AB_TREATMENT_MODEL") ?: "google/gemini-2.5-flash-preview")
    }
    ```

**Checks:** static
