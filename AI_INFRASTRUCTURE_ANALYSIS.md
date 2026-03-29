# AI Infrastructure Deep Dive: Every Line Analyzed

## Executive Summary

This is a **graceful-degradation AI platform** for Weekly Commit. The architecture enforces:
- **No-throw contracts** on core interfaces (failures return unavailable results)
- **Pluggable providers** (stub/OpenRouter/future)
- **4-pillar evidence gathering** (SQL facts, carry-forward lineage, semantic vectors, risk features)
- **LLM-as-judge evaluation** for faithfulness scoring
- **11 distinct suggestion types** routed through the same pipeline

**Robustness**: Good contract design, error handling, async evaluation
**Simplicity Issues**: String manipulation for JSON parsing, no formal schema validation, ad-hoc routing

---

# 1. AiProvider.java — The Core Strategy Interface

**File**: `/backend/src/main/java/com/weeklycommit/ai/provider/AiProvider.java`

## Exact Contract

```java
public interface AiProvider {
    String getName();                          // e.g., "stub", "openrouter"
    String getVersion();                       // Model ID for audit
    boolean isAvailable();                     // Health check (should be cached)
    AiSuggestionResult generateSuggestion(AiContext context);  // Never throws
}
```

## Key Constraints

- **Idempotent**: Same input → same output (no side effects beyond token counting)
- **No-throw**: Must catch ALL exceptions internally and return `AiSuggestionResult.unavailable()`
- **Fast health check**: `isAvailable()` should use caching (60s TTL minimum)
- **Thread-safe**: Multiple threads may call simultaneously

## Data Flow

```
Controller → AiProviderRegistry.generateSuggestion(AiContext)
  ↓
Registry queries all providers for isAvailable()
  ↓
First available provider.generateSuggestion(AiContext) 
  ↓
AiSuggestionResult (available=bool, payload, rationale, confidence, modelVersion, promptVersion)
  ↓
Controller response (aiAvailable field + suggestion data)
```

## Robustness

✅ **Excellent**: 
- Clear exception handling strategy
- Caching guidance documented
- Result type guarantees non-null return

❌ **Simplistic**:
- No rate limit contract (each impl handles separately)
- No timeout guarantee documented
- Version string is free-form (could be ambiguous)

---

# 2. AiContext.java — Input DTO with Suggestion Types

**File**: `/backend/src/main/java/com/weeklycommit/ai/provider/AiContext.java`

## Exact Contract (Record)

```java
public record AiContext(
    String suggestionType,           // Discriminator (required)
    UUID userId,                     // Optional: requesting user
    UUID planId,                     // Optional: plan context
    UUID commitId,                   // Optional: commit context
    Map<String, Object> commitData,  // Optional: serialized commit fields
    Map<String, Object> planData,    // Optional: serialized plan summary
    List<Map<String, Object>> historicalCommits,  // Optional: pattern data
    List<Map<String, Object>> rcdoTree,           // Optional: RCDO nodes
    Map<String, Object> additionalContext         // Optional: scope/risk inputs
)
```

## Suggestion Type Constants (11 Total)

```
TYPE_COMMIT_DRAFT       → Draft improvement suggestions (title, description, estimate)
TYPE_COMMIT_LINT        → Quality validation (hard blocks + soft guidance)
TYPE_RCDO_SUGGEST       → Link suggestion (confidence ≥ 0.7 to surface)
TYPE_RISK_SIGNAL        → Risk detection for plan owner/manager
TYPE_RECONCILE_ASSIST   → End-of-week reconciliation help
TYPE_TEAM_SUMMARY       → Manager aggregate view
TYPE_RAG_INTENT         → Semantic search intent classification
TYPE_RAG_QUERY          → RAG answer generation
TYPE_TEAM_INSIGHT       → Proactive team pattern alerts
TYPE_PERSONAL_INSIGHT   → Proactive personal/plan alerts
TYPE_WHAT_IF            → Plan simulation narration
```

## Data Flow

All contexts converge to the same pipeline:

```
Service Layer (CommitDraftAssistService, RcdoSuggestService, etc.)
  ↓ (builds AiContext from domain entities)
AiProviderRegistry.generateSuggestion(context)
  ↓
Provider.generateSuggestion(context)
  ↓
Provider formats context as JSON user message
  ↓
LLM receives system prompt (per type) + context JSON
```

## Robustness

✅ **Good**:
- Record immutability
- Type discriminator is required
- All fields are untyped Maps (flexible for schema evolution)

❌ **Fragile**:
- **No validation** of context shape per type (e.g., RCDO_SUGGEST doesn't enforce `rcdoTree` non-null)
- **Map keys are strings** — typos silently drop context
- **No documentation** of which fields are required for each type
- **No schema registration** — LLM sees arbitrary JSON

---

# 3. AiSuggestionResult.java — Output DTO

**File**: `/backend/src/main/java/com/weeklycommit/ai/provider/AiSuggestionResult.java`

## Exact Contract (Record)

```java
public record AiSuggestionResult(
    boolean available,                // false = provider down or AI disabled
    String payload,                   // JSON-serialized suggestion (structure varies by type)
    String rationale,                 // Human-readable explanation
    double confidence,                // [0.0, 1.0] — used for filtering (RCDO: ≥0.7)
    String modelVersion,              // Model ID for audit (e.g., "claude-sonnet-4")
    String promptVersion              // Prompt template version for A/B testing (nullable)
)
```

## Backwards Compatibility

```java
// Old callers that don't pass promptVersion — constructor fills null
public AiSuggestionResult(boolean available, String payload, String rationale, 
    double confidence, String modelVersion) {
    this(available, payload, rationale, confidence, modelVersion, null);
}

// Convenience factory
public static AiSuggestionResult unavailable() {
    return new AiSuggestionResult(false, "{}", "AI provider unavailable", 0.0, "none", null);
}
```

## Data Flow

When controller receives `AiSuggestionResult`:
- If `available=false`: Include `"aiAvailable": false` in response, don't include suggestion data
- If `available=true` AND confidence < threshold (e.g., 0.7 for RCDO): Filter from UI
- Payload is **trusted directly** — no secondary validation
- `rationale` is truncated to 500 chars in OpenRouter impl

## Robustness

✅ **Good**:
- Immutable record
- Backwards-compatible constructor
- Clear unavailable() factory

❌ **Fragile**:
- **No schema validation** of payload against expected structure
- **No type checking** — payload could be `{}` and caller wouldn't know
- **Confidence range [0.0, 1.0]** — no validation (could be NaN or infinity)
- **Rationale truncation** is lossy (500 chars)

---

# 4. OpenRouterAiProvider.java — Production Provider

**File**: `/backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java`

**Size**: ~600 lines. Most complex implementation.

## Exact Contract

Implements `AiProvider` with:
```java
getName()              → "openrouter"
getVersion()           → model (e.g., "anthropic/claude-sonnet-4-20250514")
isAvailable()          → Health check with 60s TTL caching
generateSuggestion()   → HTTP POST to OpenRouter, parse JSON response, never throw
```

## Configuration (Spring @Value Injected)

```properties
ai.openrouter.api-key          # Required (null check: isAvailable() returns false)
ai.openrouter.model            # Default: "anthropic/claude-sonnet-4-20250514"
ai.openrouter.max-tokens       # Default: 1024
ai.openrouter.base-url         # Default: "https://openrouter.ai/api/v1"
```

## Key Subsystems

### 1. Health Check (`isAvailable()`)

```
TTL-cached check (60s):
  1. If no API key: return false (hard-coded)
  2. If cache valid: return cached result
  3. Otherwise: GET /models with Bearer token
  4. Return: statusCode == 200
  5. On exception: log debug, return false
```

**Robustness**: ⭐⭐⭐ Solid
- Caching prevents hammering
- Timeout: 5s
- Silent failure (returns false, doesn't crash)

### 2. Rate Limiting (`tryAcquireRate()`)

```
Token bucket, 30 req/min:
  - Per-request counter in 60s window
  - If window expired: reset count to 0
  - Increment and check ≤ 30
  - Returns bool (true = allowed, false = limited)
```

**Robustness**: ⭐⭐ Simplistic
- ❌ No distributed rate limiting (single JVM only)
- ❌ Window boundary race conditions (multiple threads could exceed limit at edge)
- ❌ No exponential backoff, just rejects
- ⭐ Good enough for development, inadequate for production multi-instance

### 3. Prompt Loading (`loadPromptTemplate()`)

```
Switch on suggestionType → filename (e.g., "commit-draft-assist.txt")
Try:
  ClassPathResource("prompts/" + filename).readAllBytes()
Catch:
  Return hardcoded generic prompt
```

**Robustness**: ⭐⭐
- ✅ Graceful fallback to generic prompt
- ❌ Silent file not found (dev may not notice typo)
- ❌ No versioning in filenames (can't safely update prompts)

### 4. HTTP Call (`callOpenRouter()`)

```
Build JSON body:
  {
    "model": "anthropic/claude-sonnet-...",
    "max_tokens": 1024,
    "messages": [
      {"role": "system", "content": systemPrompt},
      {"role": "user", "content": userMessage}
    ]
  }

POST /chat/completions with:
  Authorization: Bearer {apiKey}
  Content-Type: application/json
  HTTP-Referer: https://weeklycommit.dev
  X-Title: Weekly Commit
  Timeout: 30s

Parse response:
  - Check status 200, throw if not
  - Extract usage.total_tokens → increment totalTokensUsed
  - Return response.body (raw JSON string)
```

**Robustness**: ⭐⭐⭐
- ✅ Standard OpenRouter headers
- ✅ 30s timeout (reasonable for LLM)
- ✅ Token tracking for billing
- ⭐ Token tracking silently fails on parse error

### 5. JSON Response Parsing (`parseResponse()`)

```
Input: OpenRouter response JSON
  {
    "choices": [
      {"message": {"content": "...}}
    ]
  }

Process:
  1. Extract choices[0].message.content
  2. Pass to extractJson() to handle markdown fences
  3. Pass to extractRationale() for explanation text
  4. Validate payload is valid JSON (throws if not)
  5. Return AiSuggestionResult(true, jsonPayload, rationale, 0.85, model, promptVersion)

On exception: return unavailable()
```

**Robustness**: ⭐⭐
- ✅ JSON validation before returning
- ✅ Exception handling
- ❌ **Hard-coded 0.85 confidence** (all successful responses same confidence)
- ❌ `rationale` is lossy truncation to 500 chars

### 6. JSON Extraction (`extractJson()`)

```
Try in order:
  1. Find ```json ... ``` fence
  2. Find ``` ... ``` fence (with optional language)
  3. Find {…} substring (raw JSON object)
  4. Return content found
```

**Robustness**: ⭐⭐⭐
- ✅ Handles three common formats
- ✅ Defensive substring logic
- ⭐ No support for JSON arrays (only objects)

### 7. Prompt Version Resolution (`resolvePromptVersion()`)

```
Switch on suggestionType → base name (e.g., "commit-draft-assist")
Special case: "commit-draft-assist" → append "-v2" (added title length rule + example)
Others: append "-v1"
Return string for audit
```

**Robustness**: ⭐⭐⭐ Good for A/B testing
- ✅ Explicit version tracking
- ✅ Can be extended to feature flags
- ⭐ Only `commit-draft-assist` gets special case (manual, not scalable)

## Data Flow (Complete Request-Response)

```
AiProviderRegistry.generateSuggestion(AiContext)
  ↓
OpenRouterAiProvider.generateSuggestion()
  ├─ Check: apiKey blank? → unavailable()
  ├─ Check: rate limit? → unavailable() if exceeded
  ├─ Load: systemPrompt ← loadPromptTemplate(suggestionType)
  ├─ Build: userMessage ← buildUserMessage(AiContext)
  │         // Serializes context to JSON with all fields
  ├─ HTTP POST: callOpenRouter(systemPrompt, userMessage)
  │   ├─ POST /chat/completions
  │   ├─ Extract usage.total_tokens
  │   ├─ Return response body
  ├─ Parse: parseResponse(responseBody, suggestionType, promptVersion)
  │   ├─ Extract JSON from content
  │   ├─ Validate is parseable JSON
  │   ├─ Truncate rationale to 500 chars
  │   ├─ Return AiSuggestionResult(true, payload, rationale, 0.85, model, promptVersion)
  └─ Exception? → unavailable()

AiSuggestionResult → Service → Controller → HTTP response
```

## Observability

```java
getTotalTokensUsed()    // Atomic long for Prometheus gauge
getTotalRequests()      // Atomic long for Prometheus gauge
```

Exposed to `AiQualityMetrics` for monitoring.

---

# 5. StubAiProvider.java — Test/Dev Provider

**File**: `/backend/src/main/java/com/weeklycommit/ai/provider/StubAiProvider.java`

**Size**: ~230 lines

## Exact Contract

Implements `AiProvider`:
```java
getName()              → "stub"
getVersion()           → "stub-v1"
isAvailable()          → Always true (no dependencies)
generateSuggestion()   → Return canned response per suggestionType
```

## Activation

```
@ConditionalOnProperty(name = "ai.provider", havingValue = "stub")
```

Enabled when `application.properties` has `ai.provider=stub`.

## Response Generation

Giant switch statement with **11 hardcoded responses**:

```java
switch (context.suggestionType()) {
    case TYPE_COMMIT_DRAFT -> {
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
    // ... 10 more cases
}
return new AiSuggestionResult(true, payload.trim(), rationale, confidence, STUB_MODEL_VERSION);
```

## Robustness

✅ **Strengths**:
- No external dependencies
- Deterministic (useful for testing)
- Covers all 11 types
- Confidence values vary per type (0.85–0.95)

❌ **Weaknesses**:
- Canned responses aren't realistic (always have `id` on RCDO, but may not match request context)
- `TYPE_RCDO_SUGGEST` tries to use `context.rcdoTree()` but might be empty
- No variation based on input (same response every time)
- Payload is hardcoded formatted JSON (brittle if schema changes)

---

# 6. AiProviderRegistry.java — Provider Router

**File**: `/backend/src/main/java/com/weeklycommit/ai/provider/AiProviderRegistry.java`

**Size**: ~100 lines

## Exact Contract

```java
public boolean isAiEnabled()                    // AI enabled flag (default true)
public Optional<AiProvider> getActiveProvider() // First available provider
public AiSuggestionResult generateSuggestion(AiContext context)  // Route to first available
```

## Constructor

```java
public AiProviderRegistry(List<AiProvider> providers) {
    this.providers = providers != null ? providers : List.of();
}
```

Spring injects all `@Component` implementations of `AiProvider` (OpenRouter, Stub, etc.).

## Feature Flag

```
@Value("${ai.enabled:true}")
private boolean aiEnabled;  // Can disable all AI without removing beans
```

## Logic

### `isAiEnabled()`
```
return aiEnabled && !providers.isEmpty();
```
Returns false if:
- Flag is `ai.enabled=false`, OR
- No providers registered

### `getActiveProvider()`
```
if (!aiEnabled) {
    return Optional.empty();
}
return providers.stream()
    .filter(AiProvider::isAvailable)
    .findFirst();  // First provider where isAvailable() returns true
```

**Robustness**: ⭐⭐
- ✅ Graceful when no providers
- ❌ No fallback ordering (just takes first available)
- ❌ No provider priorities (if OpenRouter is flaky, can't prefer Stub)

### `generateSuggestion(AiContext)`
```
Optional<AiProvider> provider = getActiveProvider();
if (provider.isEmpty()) {
    return AiSuggestionResult.unavailable();
}
try {
    return provider.get().generateSuggestion(context);
} catch (Exception ex) {
    // Defensive: provider may violate no-throw contract
    return AiSuggestionResult.unavailable();
}
```

**Robustness**: ⭐⭐⭐
- ✅ Double-checks provider contract (expects no throw, but catches anyway)
- ✅ Graceful degradation
- ⭐ Logs the exception (useful for debugging)

## Data Flow

```
Service → Registry.generateSuggestion(AiContext)
  ↓
getActiveProvider()
  ↓
First provider where isAvailable() == true
  ↓
provider.generateSuggestion(context)
  ↓
AiSuggestionResult
```

---

# 7. AiController.java — REST Endpoints

**File**: `/backend/src/main/java/com/weeklycommit/ai/controller/AiController.java`

**Size**: ~480 lines, ~13 endpoints

## Exact Contract

Dependency injection (constructor):
```java
public AiController(
    CommitDraftAssistService draftAssistService,
    CommitLintService lintService,
    RcdoSuggestService rcdoSuggestService,
    RiskDetectionService riskDetectionService,
    ReconcileAssistService reconcileAssistService,
    ManagerAiSummaryService managerSummaryService,
    AiSuggestionService suggestionService,
    AiProviderRegistry providerRegistry,
    SemanticIndexService semanticIndexService,
    SemanticQueryService semanticQueryService,
    AiSuggestionRepository suggestionRepo,
    StructuredEvidenceService evidenceService,
    WhatIfService whatIfService,
    ObjectMapper objectMapper
)
```

13 dependencies. **Each endpoint delegates to a service.**

## Endpoints (All POST/GET, All Return ResponseEntity, All Degrade Gracefully)

| Endpoint | Method | Input | Output | Service |
|----------|--------|-------|--------|---------|
| `/api/ai/commit-draft-assist` | POST | CommitDraftAssistRequest | CommitDraftAssistResponse | CommitDraftAssistService |
| `/api/ai/commit-lint` | POST | CommitLintRequest | CommitLintResponse | CommitLintService |
| `/api/ai/rcdo-suggest` | POST | RcdoSuggestRequest | RcdoSuggestResponse | RcdoSuggestService |
| `/api/plans/{id}/risk-signals` | GET | UUID planId, X-Actor-User-Id header | PlanRiskSignals | RiskDetectionService |
| `/api/ai/reconcile-assist` | POST | ReconcileAssistRequest | ReconcileAssistResponse | ReconcileAssistService |
| `/api/ai/what-if` | POST | WhatIfRequest | WhatIfResponse | WhatIfService |
| `/api/teams/{id}/week/{weekStart}/ai-summary` | GET | UUID teamId, LocalDate weekStart | ManagerAiSummaryResponse | ManagerAiSummaryService |
| `/api/ai/query` | POST | RagQueryRequest | RagQueryResponse | SemanticQueryService |
| `/api/teams/{id}/week/{weekStart}/ai-insights` | GET | UUID teamId, LocalDate weekStart | InsightListResponse | AiSuggestionRepository |
| `/api/plans/{id}/ai-insights` | GET | UUID planId | InsightListResponse | AiSuggestionRepository |
| `/api/plans/{planId}/evidence` | GET | UUID planId, ?question | StructuredEvidenceResponse | StructuredEvidenceService |
| `/api/commits/{commitId}/evidence` | GET | UUID commitId, ?question | StructuredEvidenceResponse | StructuredEvidenceService |
| `/api/ai/status` | GET | (none) | {aiEnabled, providerName, providerVersion, available} | AiProviderRegistry |
| `/api/ai/feedback` | POST | AiFeedbackRequest | 201 Created | AiSuggestionService |
| `/api/admin/ai/reindex` | POST | (none) | {plansQueued, status, message} | SemanticIndexService |

## Key Implementation Details

### Graceful Degradation Pattern

```java
@PostMapping("/api/ai/commit-draft-assist")
public ResponseEntity<CommitDraftAssistResponse> commitDraftAssist(
        @Valid @RequestBody CommitDraftAssistRequest request) {
    return ResponseEntity.ok(draftAssistService.assist(request));
}
```

**Every service must internally**:
1. Call `aiProviderRegistry.generateSuggestion(context)`
2. If `result.available() == false`: Return response with `aiAvailable: false` + default data
3. Never throw exception to controller

### Risk Signals (Authorization)

```java
@GetMapping("/api/plans/{id}/risk-signals")
public ResponseEntity<PlanRiskSignals> getRiskSignals(@PathVariable UUID id,
        @RequestHeader(value = "X-Actor-User-Id", required = false) UUID callerId) {
    if (callerId == null) {
        return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(riskDetectionService.getRiskSignals(id, callerId));
}
```

⚠️ **Simple header-based auth** (not Spring Security). Caller ID is **not validated**. ❌ Simplistic.

### Insight Parsing

```java
private List<InsightCardDto> parseInsightCards(List<AiSuggestion> rows) {
    List<InsightCardDto> result = new ArrayList<>(rows.size());
    for (AiSuggestion row : rows) {
        try {
            JsonNode root = objectMapper.readTree(row.getSuggestionPayload());
            String insightText = root.path("insightText").asText(row.getRationale());
            // ... extract 4 more fields
            result.add(new InsightCardDto(...));
        } catch (Exception ex) {
            log.debug("Skipping unparseable insight payload...");
        }
    }
    return result;
}
```

**Robustness**: ⭐⭐⭐
- ✅ Silent skip on parse error (doesn't break UI)
- ✅ Fallback to rationale if insightText missing
- ⭐ No schema validation (assumes payload has required structure)

### Evidence Endpoints

```java
@GetMapping("/api/plans/{planId}/evidence")
public ResponseEntity<StructuredEvidenceResponse> getEvidence(@PathVariable UUID planId,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String question) {
    try {
        StructuredEvidence evidence = evidenceService.gatherForPlan(planId, question);
        return ResponseEntity.ok(StructuredEvidenceResponse.of(evidence));
    } catch (Exception e) {
        log.warn("Evidence gathering failed for plan {}: {}", planId, e.getMessage());
        return ResponseEntity.ok(StructuredEvidenceResponse.unavailable());
    }
}
```

**Robustness**: ⭐⭐⭐
- ✅ Graceful degradation
- ✅ Question param optional (allows RAG + non-RAG use)
- ✅ Exception caught and logged

## Data Flow (Request → Response)

```
POST /api/ai/commit-draft-assist
  ↓ @Valid validates CommitDraftAssistRequest
  ↓
CommitDraftAssistService.assist(request)
  ├─ Build AiContext from request
  ├─ Call AiProviderRegistry.generateSuggestion(context)
  ├─ If unavailable: return response with aiAvailable=false
  ├─ If available: parse payload, return response with data
  └─ On exception: return response with aiAvailable=false
  ↓
CommitDraftAssistResponse (serialized to JSON)
  ↓
HTTP 200 OK with JSON body
```

---

# 8. FaithfulnessEvaluator.java — LLM-as-Judge

**File**: `/backend/src/main/java/com/weeklycommit/ai/eval/FaithfulnessEvaluator.java`

**Size**: ~170 lines

## Purpose

Scores the **faithfulness** of AI outputs using recursive LLM evaluation. Per Manus RAG evaluation report (§2.1), faithfulness is the #1 metric for enterprise risk signals.

## Exact Contract

```java
@Async
public void maybeScopeAsync(UUID suggestionId)
    // Enqueue evaluation if suggestion should be scored

@Transactional
public void scoreFaithfulness(AiSuggestion suggestion)
    // Run judge, write score to DB
```

## Evaluation Strategy

### Decision Logic (`maybeScopeAsync`)

```java
1. Load AiSuggestion by ID
2. If already scored (evalFaithfulnessScore != null): skip
3. If suggestionType in HIGH_STAKES_TYPES: evaluate
   - HIGH_STAKES: RISK_SIGNAL, TEAM_INSIGHT, PERSONAL_INSIGHT, RAG_QUERY
4. If NOT high-stakes:
   - Roll random [0, 1)
   - If > 0.10: skip (90% sampling rate → 10% evaluated)
5. Call scoreFaithfulness()
```

**Robustness**: ⭐⭐⭐
- ✅ Idempotent (checks already-scored)
- ✅ Async (doesn't block request)
- ✅ Sampling for low-stakes (saves tokens)
- ⚠️ Random sampling is biased by order (first suggestion may be more likely evaluated)

### Scoring Flow (`scoreFaithfulness`)

```java
1. Extract from AiSuggestion:
   - answer = suggestionPayload (JSON string)
   - context = prompt field (retrieved context)
   - suggestionType
2. Build judgeInput: Map<String, Object> with answer, retrievedContext, suggestionType
3. Create AiContext for "FAITHFULNESS_EVAL" type
   - additionalContext = judgeInput
4. Call AiProviderRegistry.generateSuggestion(judgeContext)
5. If judge unavailable: log debug, return (don't fail)
6. Parse judge response: extract faithfulnessScore, relevancyScore
7. Write to DB: evalFaithfulnessScore, evalRelevancyScore, evalScoredAt
8. If faithfulness < 0.85: log warning
```

**Robustness**: ⭐⭐
- ✅ Judge invokes same pipeline (no hidden dependency)
- ✅ Graceful degradation if judge unavailable
- ⭐ Hardcoded threshold 0.85 for alerts
- ❌ No retry on judge failure
- ❌ No timeout documented

### Score Parsing (`parseScore`)

```java
private float parseScore(String payload, String fieldName) {
    try {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode scoreNode = root.path(fieldName);
        if (scoreNode.isMissingNode() || scoreNode.isNull()) {
            return -1.0f;
        }
        return (float) scoreNode.asDouble(-1.0);
    } catch (Exception e) {
        return -1.0f;
    }
}
```

**Robustness**: ⭐⭐⭐
- ✅ Handles missing/null fields (-1.0 sentinel)
- ✅ Safe type conversion
- ✅ Silent exception (logs debug)

## Data Flow

```
Service persists AiSuggestion (suggestionPayload, prompt fields)
  ↓
Controller returns to client
  ↓
@Async FaithfulnessEvaluator.maybeScopeAsync() triggered by listener
  ↓
Load AiSuggestion
  ↓
Check: already scored? high-stakes type? sample?
  ↓ If yes, call scoreFaithfulness()
  ↓
Build FAITHFULNESS_EVAL AiContext
  ↓
AiProviderRegistry.generateSuggestion()
  ↓
Judge response: {"faithfulnessScore": 0.92, "relevancyScore": 0.88, ...}
  ↓
Parse scores, write back to AiSuggestion
  ├─ evalFaithfulnessScore = 0.92
  ├─ evalRelevancyScore = 0.88
  └─ evalScoredAt = now()
```

---

# 9. AiQualityMetrics.java — Prometheus Gauges

**File**: `/backend/src/main/java/com/weeklycommit/ai/metrics/AiQualityMetrics.java`

**Size**: ~180 lines

## Purpose

Exposes Prometheus gauges for Grafana dashboards, refreshed every 5 minutes.

## Exact Contract

```java
public class AiQualityMetrics {
    @Scheduled(fixedRate = 300_000, initialDelay = 30_000)
    public void refreshMetrics()  // Runs every 5 min (300s)
}
```

## Metrics Exposed

| Gauge | Labels | Source | Calculation |
|-------|--------|--------|-------------|
| `weekly_commit_ai_provider_available` | (none) | OpenRouterAiProvider.isAvailable() | 1.0 if up, 0.0 if down |
| `weekly_commit_ai_tokens_total` | (none) | OpenRouterAiProvider.getTotalTokensUsed() | Sum of usage.total_tokens |
| `weekly_commit_ai_requests_total` | (none) | OpenRouterAiProvider.getTotalRequests() | Increment per request |
| `weekly_commit_ai_faithfulness_score` | {suggestion_type} | AiSuggestionRepository.avgFaithfulnessByType(7d) | 7-day avg |
| `weekly_commit_ai_acceptance_rate` | {suggestion_type} | AiSuggestionRepository.acceptanceRateByType(7d) | accepted / (accepted + dismissed) |

## Implementation

### Constructor

```java
Gauge.builder("weekly_commit_ai_provider_available", providerAvailable, AtomicReference::get)
    .register(registry);

if (openRouterProvider != null) {
    Gauge.builder("weekly_commit_ai_tokens_total", openRouterProvider, 
        OpenRouterAiProvider::getTotalTokensUsed).register(registry);
    // ...
}
```

**Robustness**: ⭐⭐
- ✅ Conditional registration of provider metrics
- ⚠️ OpenRouter may be null, metrics silently skip

### Refresh Job (`refreshMetrics()`)

```java
@Scheduled(fixedRate = 300_000, initialDelay = 30_000)
public void refreshMetrics() {
    try {
        refreshProviderAvailability();
        refreshFaithfulnessScores();
        refreshAcceptanceRates();
    } catch (Exception e) {
        log.warn("AiQualityMetrics: refresh failed — {}", e.getMessage());
    }
}
```

Called every 300 seconds with 30s initial delay.

### Faithfulness Refresh

```java
private void refreshFaithfulnessScores() {
    Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
    
    List<Object[]> rows = suggestionRepo.avgFaithfulnessByType(sevenDaysAgo);
    for (Object[] row : rows) {
        String type = (String) row[0];
        Double avgScore = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
        
        String key = "faithfulness_" + type;
        if (!faithfulnessCache.containsKey(key)) {
            // Register gauge on first encounter
            Gauge.builder("weekly_commit_ai_faithfulness_score",
                () -> faithfulnessCache.getOrDefault("faithfulness_" + typeLabel, 0.0))
                .tags(Tags.of("suggestion_type", typeLabel))
                .register(registry);
        }
        faithfulnessCache.put(key, avgScore);
    }
}
```

**Robustness**: ⭐⭐⭐
- ✅ Dynamic gauge registration per type
- ✅ Cache prevents repeated DB queries
- ⭐ Assumes repository has `avgFaithfulnessByType()` method (not shown)
- ⭐ No handling of new types appearing mid-lifetime (once registered, stays)

## Data Flow

```
Scheduler: every 300s
  ├─ Query: avg(evalFaithfulnessScore) where createdAt > 7d ago, grouped by suggestionType
  ├─ For each type:
  │   ├─ Register gauge if first time
  │   └─ Cache value
  ├─ Query: acceptance_rate by type (7d)
  │   └─ Cache value
  └─ Query: provider.isAvailable()
      └─ Cache 1.0 or 0.0

Prometheus scrape:
  ├─ Reads gauges from cache
  ├─ Tags with suggestion_type label
  └─ Sends to time-series database
```

---

# 10. StructuredEvidenceService.java — Multi-Source Context Assembly

**File**: `/backend/src/main/java/com/weeklycommit/ai/evidence/StructuredEvidenceService.java`

**Size**: ~340 lines. **Most intricate data gathering logic.**

## Purpose

Assembles **4-pillar evidence bundles** from transactional + semantic + derived data. Every piece passed to the LLM is explicitly sourced, never invented by the model.

## Exact Contract

```java
public StructuredEvidence gatherForPlan(UUID planId, String question)
public StructuredEvidence gatherForCommit(UUID commitId, String question)
```

Both return `StructuredEvidence` with 4 components (may have nulls).

## Four Evidence Pillars

### 1. SQL Facts (`buildSqlFacts`)

```
Extract from database + join multiple tables:

SqlFacts {
    userId, userDisplayName,
    teamId, teamName,
    planId, weekStartDate, planState,
    capacityBudgetPoints,
    totalPlannedPoints (sum of all commit estimates),
    totalAchievedPoints (sum of achieved commit estimates),
    commitCount,
    carryForwardCount (count where streak > 0),
    scopeChangeCount (from UserWeekFact),
    lockCompliance, reconcileCompliance (from UserWeekFact),
    chessDistribution: Map<String, Integer>  // e.g., {"KING": 2, "QUEEN": 1}
}
```

**Query Pattern**:
```sql
SELECT u.displayName, t.name FROM WeeklyPlan p
  LEFT JOIN User u ON p.ownerUserId = u.id
  LEFT JOIN Team t ON p.teamId = t.id
SELECT * FROM WeeklyCommit WHERE planId = ?
SELECT * FROM UserWeekFact WHERE userId = ? AND weekStart = ?
```

**Robustness**: ⭐⭐
- ✅ Left joins (handles missing user/team)
- ✅ Aggregations (sum, count, group by)
- ❌ No null-safety on chessDistribution (could be empty)
- ❌ Outcome check hardcoded to "ACHIEVED".equals() (enum name, fragile)

### 2. Lineage (`buildLineage`)

```
For each commit in the plan with carryForwardStreak > 0:
  ├─ Call LineageQueryService.traceLineage(commitId)
  └─ Return first non-null LineageChain found

If no carries: return null
```

**Robustness**: ⭐⭐
- ✅ Finds first carry-forward commit
- ❌ Stops at first result (may miss carries on other commits)

### 3. Semantic Evidence (`retrieveSemanticEvidence`)

```
Prerequisites:
  - pineconeClient != null && pineconeClient.isAvailable()
  - embeddingService != null && embeddingService.isAvailable()

If question is null/blank:
  question = "weekly plan commits and outcomes for week " + weekStart

Embed question:
  vector = embeddingService.embed(question)
  If vector.length == 0: return empty

Query Pinecone:
  namespace = plan.getTeamId() (or "" if null)
  filter = {teamId: teamId}
  matches = pineconeClient.query(namespace, vector, TOP_K=10, filter)

Transform matches:
  List<SemanticMatch>(entityType, entityId, score, weekStartDate, text)
```

**Robustness**: ⭐⭐⭐
- ✅ Handles unavailable Pinecone/embeddings gracefully
- ✅ Fallback question generation
- ⭐ TOP_K hardcoded to 10 (no tuning)
- ❌ Filter is `{teamId}` — what if plan has no teamId? (namespace = "")

### 4. Risk Features (`buildRiskFeatures`)

```
Extract from derived tables:

RiskFeatures {
    completionRatio: (achievedPoints / plannedPoints) for current week,
    avgCompletion4w: average of previous 4 weeks,
    carryForwardStreakMax: max streak in current plan,
    scopeChangeCount: from UserWeekFact,
    kingCount, queenCount: chess piece distribution,
    activeRiskSignalTypes: List of signal types from AiSuggestion table
}
```

**Query Pattern**:
```sql
SELECT * FROM UserWeekFact WHERE userId = ? AND weekStart BETWEEN 4w_ago AND now
SELECT * FROM WeeklyCommit WHERE planId = ? ORDER BY priority
SELECT * FROM AiSuggestion WHERE planId = ? AND suggestionType = 'RISK_SIGNAL'
```

**Robustness**: ⭐⭐
- ✅ Uses 4-week historical average
- ❌ No null-safety on current UserWeekFact (could be missing)
- ❌ `extractSignalType()` uses string parsing instead of JSON deserialization

## Complete Data Flow

```
Request: GET /api/plans/{planId}/evidence?question=...
  ↓
Controller.getEvidence(planId, question)
  ↓
StructuredEvidenceService.gatherForPlan(planId, question)
  ├─ BuildSqlFacts:
  │   ├─ Load WeeklyPlan
  │   ├─ Join User, Team
  │   ├─ Load all WeeklyCommit rows
  │   ├─ Aggregate: planned points, achieved points, chess distribution
  │   ├─ Load UserWeekFact (may be null)
  │   └─ Return SqlFacts
  ├─ BuildLineage:
  │   ├─ Iterate commits for carry-forward streak > 0
  │   ├─ Call LineageQueryService.traceLineage(commitId)
  │   └─ Return first non-null chain
  ├─ RetrieveSemanticEvidence:
  │   ├─ Check: Pinecone + Embeddings available?
  │   ├─ Embed question (or default)
  │   ├─ Query Pinecone with namespace, vector, filter
  │   ├─ Transform matches to SemanticMatch records
  │   └─ Return List<SemanticMatch>
  ├─ BuildRiskFeatures:
  │   ├─ Load current UserWeekFact
  │   ├─ Load 4-week history
  │   ├─ Calculate completion ratio, averages
  │   ├─ Count kings/queens
  │   ├─ Load active risk signals
  │   └─ Return RiskFeatures
  └─ Combine into StructuredEvidence
      ↓
Controller.StructuredEvidenceResponse.of(evidence)
  ↓
HTTP 200 + JSON with 4 sections (sqlFacts, lineage, semanticMatches, riskFeatures)
```

---

# 11. StructuredEvidence.java — Evidence Record Types

**File**: `/backend/src/main/java/com/weeklycommit/ai/evidence/StructuredEvidence.java`

**Size**: ~75 lines

## Exact Contract (Record Hierarchy)

```java
public record StructuredEvidence(
    SqlFacts sqlFacts,
    LineageChain lineage,
    List<SemanticMatch> semanticMatches,
    RiskFeatures riskFeatures
)

public record SqlFacts(
    UUID userId, String userDisplayName,
    UUID teamId, String teamName,
    UUID planId, LocalDate weekStart, String planState,
    int capacityBudget, int totalPlannedPoints, int totalAchievedPoints,
    int commitCount, int carryForwardCount, int scopeChangeCount,
    boolean lockCompliance, boolean reconcileCompliance,
    Map<String, Integer> chessDistribution
)

public record LineageChain(
    UUID currentCommitId, String currentTitle, int streakLength,
    List<LineageNode> nodes
)

public record LineageNode(
    UUID commitId, String title, LocalDate weekStart,
    String outcome, String chessPiece, Integer estimatePoints,
    String carryForwardReason
)

public record SemanticMatch(
    String entityType, String entityId, double score,
    String weekStartDate, String text
)

public record RiskFeatures(
    double completionRatio, double avgCompletionRatio4w,
    int carryForwardStreakMax, int scopeChangeCount,
    int kingCount, int queenCount, List<String> activeRiskSignalTypes
)
```

## Robustness

✅ **Good**:
- Immutable records
- Clear semantics per field
- `empty()` factory for degradation

❌ **Simplistic**:
- No schema versioning
- `String outcome` should be enum
- `String chessPiece` should be enum
- `String planState` should be enum
- No validation on ranges (e.g., completion ratio should be [0.0, 1.0])

---

# 12. LineageQueryService.java — Recursive CTE Queries

**File**: `/backend/src/main/java/com/weeklycommit/ai/evidence/LineageQueryService.java`

**Size**: ~280 lines

## Purpose

Executes native SQL recursive CTEs to traverse carry-forward chains, RCDO ancestry, and scope change timelines.

## Exact Contract

```java
public LineageChain traceLineage(UUID commitId)        // Carry-forward chain
public List<String> rcdoAncestryPath(UUID rcdoNodeId)  // RCDO path
public List<ScopeChangeEntry> scopeChangeTimeline(UUID planId)  // Timeline
```

### 1. `traceLineage(UUID commitId)`

**Query**: Recursive CTE on `carry_forward_link` table

```sql
WITH RECURSIVE lineage_back AS (
    -- Anchor: find links to this commit
    SELECT cfl.source_commit_id, cfl.target_commit_id, cfl.reason, 1 AS depth
    FROM carry_forward_link cfl
    WHERE cfl.target_commit_id = :commitId
    
    -- Recursive: walk back to the origin
    UNION ALL
    SELECT cfl.source_commit_id, cfl.target_commit_id, cfl.reason, lb.depth + 1
    FROM carry_forward_link cfl
    JOIN lineage_back lb ON lb.source_commit_id = cfl.target_commit_id
    WHERE lb.depth < 20  -- Protect against cycles
)
SELECT wc.id, wc.title, wp.week_start_date, wc.outcome, wc.chess_piece,
       wc.estimate_points, lb.reason, lb.depth
FROM lineage_back lb
JOIN weekly_commit wc ON wc.id = lb.source_commit_id
JOIN weekly_plan wp ON wp.id = wc.plan_id
ORDER BY lb.depth DESC
```

**Flow**:
1. Find all links WHERE target = this commit (backward walk)
2. Recursively follow source → target chains up to 20 levels
3. Join with WeeklyCommit and WeeklyPlan for context
4. Order oldest to newest (depth DESC = oldest first)
5. Add current commit as final node
6. Return LineageChain(currentId, currentTitle, streakLength = nodeCount - 1, nodes)

**Robustness**: ⭐⭐⭐
- ✅ Cycle protection (depth < 20)
- ✅ Proper recursive CTE syntax
- ⚠️ Depth 20 is arbitrary (could be configurable)
- ⭐ UUID conversion from Object (type inference fragile)

### 2. `rcdoAncestryPath(UUID rcdoNodeId)`

```sql
WITH RECURSIVE ancestry AS (
    -- Anchor: start at the given node
    SELECT id, title, node_type, parent_id, 0 AS depth
    FROM rcdo_node WHERE id = :nodeId
    
    -- Recursive: walk up the tree to the root
    UNION ALL
    SELECT rn.id, rn.title, rn.node_type, rn.parent_id, a.depth + 1
    FROM rcdo_node rn
    JOIN ancestry a ON a.parent_id = rn.id
    WHERE a.depth < 10
)
SELECT node_type, title FROM ancestry ORDER BY depth DESC
```

**Example output**:
```
["RALLY CRY: Modernize infrastructure", "DELIVERY OUTCOME: Kubernetes migration", "OUTCOME: Reduce latency"]
```

**Robustness**: ⭐⭐⭐
- ✅ Walks UP the tree (parent_id)
- ✅ Depth 10 limit
- ✅ Formats as "NODE_TYPE: Title"

### 3. `scopeChangeTimeline(UUID planId)`

```sql
SELECT sce.category, sce.reason, sce.created_at,
       wc.title AS commit_title, ua.display_name AS changed_by
FROM scope_change_event sce
LEFT JOIN weekly_commit wc ON wc.id = sce.commit_id
LEFT JOIN user_account ua ON ua.id = sce.changed_by_user_id
WHERE sce.plan_id = :planId
ORDER BY sce.created_at ASC
```

**Returns**: List<ScopeChangeEntry>
```java
public record ScopeChangeEntry(
    String category, String reason, String timestamp,
    String commitTitle, String changedBy
)
```

**Robustness**: ⭐⭐⭐
- ✅ Chronological order
- ✅ Left joins (handle missing entities)
- ⭐ timestamp is String (not LocalDateTime)

## Data Flow

```
StructuredEvidenceService.buildLineage()
  ↓
LineageQueryService.traceLineage(commitId)
  ├─ Execute backward-walking CTE
  ├─ Parse Object[] rows to LineageNode records
  ├─ Add current commit as final node
  └─ Return LineageChain(commitId, title, nodes.size()-1, nodes)
      
Controller renders in evidence drawer:
  "Carry-forward chain: [origin] → [week2] → [week3] → [current]"
```

---

# Summary Table: Robustness vs Simplicity

| Component | Robustness ⭐ | Simplicity Issue |
|-----------|:---:|---|
| **AiProvider** | ⭐⭐⭐ | Clear contract, no rate limit spec |
| **AiContext** | ⭐⭐ | No validation per type, Map keys fragile |
| **AiSuggestionResult** | ⭐⭐ | No schema validation, hard-coded confidence |
| **OpenRouterAiProvider** | ⭐⭐⭐ | Rate limiter non-distributed, JSON parsing regex-ish |
| **StubAiProvider** | ⭐⭐ | Canned responses, no variation |
| **AiProviderRegistry** | ⭐⭐ | No provider priorities, simple linear search |
| **AiController** | ⭐⭐⭐ | 13 injected dependencies, header-based auth not Spring Security |
| **FaithfulnessEvaluator** | ⭐⭐⭐ | Good sampling + async, but random bias |
| **AiQualityMetrics** | ⭐⭐⭐ | Solid Prometheus integration, assumes repo methods exist |
| **StructuredEvidenceService** | ⭐⭐ | 4 pillars working, but null handling fragile, string parsing |
| **StructuredEvidence** | ⭐⭐ | No enum usage, no validation ranges |
| **LineageQueryService** | ⭐⭐⭐ | Solid CTEs, UUID casting fragile |

---

# Critical Integration Points

## Request → Response (Complete Flow Example)

```
POST /api/ai/commit-draft-assist
  Headers: X-Actor-User-Id, Content-Type: application/json
  Body: {
    "planId": "uuid",
    "commitTitle": "...",
    "commitDescription": "...",
    ...
  }

  ↓
AiController.commitDraftAssist(request)
  ├─ CommitDraftAssistService.assist(request)
  │   ├─ Load Plan, Commits from DB
  │   ├─ Load historical commits (pattern data)
  │   ├─ Build AiContext(
  │   │     suggestionType: TYPE_COMMIT_DRAFT,
  │   │     planId, commitData, planData, historicalCommits,
  │   │     ...)
  │   ├─ AiProviderRegistry.generateSuggestion(context)
  │   │   ├─ getActiveProvider()  ← OpenRouter or Stub
  │   │   ├─ provider.generateSuggestion(context)
  │   │   │   [OpenRouter]:
  │   │   │     ├─ checkRate()
  │   │   │     ├─ loadPrompt(TYPE_COMMIT_DRAFT) → "commit-draft-assist-v2.txt"
  │   │   │     ├─ buildUserMessage() → serialize context to JSON
  │   │   │     ├─ POST /chat/completions → LLM
  │   │   │     ├─ parse JSON response
  │   │   │     └─ return AiSuggestionResult(true, payload, rationale, 0.85, model, "commit-draft-assist-v2")
  │   │   └─ Return result or unavailable()
  │   ├─ Parse result.payload (JSON string)
  │   ├─ Extract suggestions: title, description, estimate, criteria
  │   ├─ Build CommitDraftAssistResponse(aiAvailable, suggestions)
  │   └─ Return response
  │
  └─ ResponseEntity.ok(response)

HTTP 200 OK
  {
    "aiAvailable": true,
    "suggestedTitle": "...",
    "suggestedDescription": "...",
    "rationale": "..."
  }
```

## Evaluation Loop (Async)

```
Service persists AiSuggestion → suggestionRepo.save(suggestion)
  ↓ Event listener triggers
  ↓
FaithfulnessEvaluator.maybeScopeAsync(suggestionId)
  ├─ Load suggestion
  ├─ Check: already scored? high-stakes? sample?
  ├─ Build FAITHFULNESS_EVAL AiContext
  ├─ AiProviderRegistry.generateSuggestion(judgeContext)
  │   └─ LLM judges: is every fact in the answer supported by context?
  ├─ Parse judge response: faithfulnessScore, relevancyScore
  ├─ Update suggestion.evalFaithfulnessScore, evalRelevancyScore
  └─ suggestionRepo.save(suggestion)
      ↓ (now has scores for Prometheus)
```

## Evidence Drawer (Inspection)

```
GET /api/plans/{planId}/evidence?question="What did we achieve?"
  ↓
StructuredEvidenceService.gatherForPlan(planId, "What did we achieve?")
  ├─ BuildSqlFacts:
  │   └─ Load: plan, user, team, commits, userWeekFact → SqlFacts
  ├─ BuildLineage:
  │   └─ LineageQueryService.traceLineage(commitId) → LineageChain
  ├─ RetrieveSemanticEvidence:
  │   ├─ EmbeddingService.embed("What did we achieve?")
  │   ├─ PineconeClient.query(namespace, vector, TOP_K=10)
  │   └─ List<SemanticMatch>
  ├─ BuildRiskFeatures:
  │   └─ Load: completion ratio, 4-week history, kings/queens, risk signals
  └─ Return StructuredEvidence(sqlFacts, lineage, semanticMatches, riskFeatures)

HTTP 200
  {
    "sqlFacts": {
      "userId": "...", "userDisplayName": "...", "commitCount": 5,
      "totalPlannedPoints": 13, "totalAchievedPoints": 10,
      "chessDistribution": {"KING": 2, "QUEEN": 1, "PAWN": 2}
    },
    "lineage": {
      "currentCommitId": "...", "streakLength": 3,
      "nodes": [
        {"commitId": "...", "title": "Add logging", "weekStart": "2024-01-01", ...},
        {"commitId": "...", "title": "Add logging", "weekStart": "2024-01-08", ...},
        {"commitId": "...", "title": "Add logging", "weekStart": "2024-01-15", ...}
      ]
    },
    "semanticMatches": [
      {"entityType": "WeeklyCommit", "entityId": "...", "score": 0.92, "text": "..."},
      ...
    ],
    "riskFeatures": {
      "completionRatio": 0.77, "carryForwardStreakMax": 3,
      "kingCount": 2, "queenCount": 1, "activeRiskSignalTypes": ["LOW_CAPACITY"]
    }
  }
```

---

# Known Simplifications / Technical Debt

1. **String-based JSON Parsing** (OpenRouterAiProvider)
   - Uses regex + substring logic to extract JSON from markdown fences
   - Should use proper JSON schema validator + parser

2. **Hard-coded Confidence** (OpenRouterAiProvider)
   - All successful responses get `0.85` confidence
   - Should extract from model metadata or use judge to re-score

3. **No Provider Prioritization** (AiProviderRegistry)
   - Linear search for first available
   - Should allow fallback chains (OpenRouter → Stub on failure)

4. **Missing Type Validation** (AiContext, CommitDraftAssistService, etc.)
   - `Map<String, Object>` fields are untyped
   - No schema enforcement
   - LLM receives arbitrary JSON structure

5. **Single-JVM Rate Limiting** (OpenRouterAiProvider)
   - 30 req/min per instance, not globally
   - Multi-instance deployments can exceed OpenRouter quota

6. **Random Sampling Bias** (FaithfulnessEvaluator)
   - `Math.random() > 0.10` is not uniform across all suggestions
   - Should use deterministic hash or load-balancing queue

7. **Header-based Authorization** (AiController)
   - `X-Actor-User-Id` header not validated
   - Should use Spring Security or OAuth2

8. **No Timeout Guarantees** (FaithfulnessEvaluator, SemanticQueryService)
   - Judge may hang indefinitely
   - LLM calls should have enforced timeouts

9. **Fragile UUID Type Conversion** (LineageQueryService)
   - `UUID toUUID(Object obj)` uses string parsing
   - Should use native JDBC type converters

10. **Hard-coded Thresholds**
    - RCDO confidence: 0.7
    - Faithfulness alert: 0.85
    - TOP_K semantic matches: 10
    - Depth limits: 20, 10
    - Should all be configurable

---

# Recommendations for Robustness

## High Priority

1. **Implement JSON Schema Validation**
   - Define schema for each suggestion type's payload
   - Validate before storing in DB
   - Return 400 Bad Request if payload violates schema

2. **Distributed Rate Limiting**
   - Move from per-instance counter to Redis-backed rate limiter
   - Ensures global quota enforcement across all app instances

3. **Explicit Timeout Enforcement**
   - Wrap all LLM calls in `Resilience4j` timeout/circuit breaker
   - Default: 30s for standard requests, 60s for judges

4. **Spring Security Integration**
   - Replace header-based auth with proper Spring Security
   - Integrate with IAM for user validation

5. **Enum Usage in Records**
   - Convert `String outcome`, `String chessPiece`, `String planState` to enums
   - Type safety + IDE support

## Medium Priority

6. **Config-driven Thresholds**
   - Move all hard-coded values to `application.properties`
   - Allow per-deployment tuning

7. **Provider Fallback Chain**
   - Allow configuration: `ai.providers.order=openrouter,stub,disabled`
   - Supports graceful degradation

8. **Schema Registry**
   - Define JSON schemas for each suggestion type
   - Version them (v1, v2, v3)
   - Validate incoming/outgoing payloads

9. **Semantic Evidence Deduplication**
   - Pinecone may return duplicate entity IDs
   - Filter duplicates before returning to UI

10. **Lineage Query Optimization**
    - Cache carry-forward chains (7-day TTL)
    - Avoid repeated CTE execution for same commit

---

# Data Model Assumptions

The code assumes these tables exist (not shown):

```sql
-- Core
weekly_plan(id, owner_user_id, team_id, week_start_date, state, capacity_budget_points)
weekly_commit(id, plan_id, title, estimate_points, chess_piece, outcome, carry_forward_streak)
user_account(id, display_name)
team(id, name, organization_id)

-- AI suggestions
ai_suggestion(
    id, user_id, plan_id, commit_id, team_id,
    suggestion_type, suggestion_payload, prompt,
    rationale, confidence, model_version, prompt_version,
    eval_faithfulness_score, eval_relevancy_score, eval_scored_at,
    feedback_status (ACCEPTED | DISMISSED | EDITED),
    created_at, updated_at
)
ai_feedback(id, suggestion_id, feedback_status, created_at)

-- Lineage
carry_forward_link(source_commit_id, target_commit_id, reason)

-- Derived
user_week_fact(
    user_id, week_start, total_planned_points, total_achieved_points,
    scope_change_count, lock_compliance, reconcile_compliance
)
scope_change_event(plan_id, category, reason, commit_id, changed_by_user_id, created_at)

-- RCDO
rcdo_node(id, title, node_type, parent_id)

-- Vector search
pinecone(namespace, vectors, metadata) [external service]
```

---

# Conclusion

This is a **well-structured graceful-degradation AI platform** with excellent separation of concerns:

- **Provider abstraction** (AiProvider interface) enables swappable implementations
- **No-throw contracts** prevent AI unavailability from crashing the app
- **Multi-pillar evidence gathering** ensures LLM faithfulness + auditability
- **Async evaluation** (FaithfulnessEvaluator) prevents request blocking
- **Comprehensive metrics** (Prometheus gauges) enable ops visibility

**However**, production deployment should address:
- JSON schema validation (prevent malformed suggestions)
- Distributed rate limiting (prevent quota overrun)
- Explicit timeout enforcement (prevent hangs)
- Proper authorization (Spring Security, not headers)
- Type safety (enums, not strings for domain values)

The architecture is **flexible and pragmatic** — suitable for rapid iteration on AI features, with clear hooks for hardening as the system matures.
