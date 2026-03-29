# Weekly Commit AI Integration Reference

**Complete Documentation of AI Pipeline Architecture, Data Flows, Prompt Templates, and Evaluation**

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [AI Provider Layer](#ai-provider-layer)
3. [Core AI Services (Capabilities 1-7)](#core-ai-services)
4. [RAG System](#rag-system)
5. [Evaluation & Metrics](#evaluation--metrics)
6. [Structured Evidence](#structured-evidence)
7. [Prompt Templates](#prompt-templates)
8. [Integration Examples](#integration-examples)

---

## Architecture Overview

### High-Level Flow

```
HTTP Request
    ↓
AiController (REST endpoint)
    ↓
Domain-specific Service (CommitDraftAssistService, etc.)
    ↓
[Data collection] → AiContext (input bundle)
    ↓
AiProviderRegistry → OpenRouterAiProvider
    ↓
[Load prompt template] + [Build user message] → HTTP/OpenRouter API
    ↓
[Parse response JSON] → AiSuggestionResult
    ↓
Service parses domain-specific DTO
    ↓
[Optional async] → FaithfulnessEvaluator (eval scoring)
    ↓
[Optional async] → SemanticIndexService (Pinecone vectorization)
    ↓
HTTP Response (with aiAvailable flag)
```

### Core Principles

- **Degradation**: All AI failures are silent. Services return `aiAvailable: false` without throwing exceptions.
- **Idempotency**: AI provider must never throw unchecked exceptions.
- **Rate Limiting**: 30 requests/min per OpenRouter endpoint (configurable).
- **Versioning**: Prompt templates are versioned for A/B testing; model version + prompt version tracked in each suggestion record.
- **Audit**: Every suggestion is persisted to `ai_suggestion` table with context hash, payload, rationale, confidence, and model version.

---

## AI Provider Layer

### Interface: `AiProvider`

```java
public interface AiProvider {
    String getName();                           // e.g., "openrouter"
    String getVersion();                        // e.g., "anthropic/claude-sonnet-4-20250514"
    boolean isAvailable();                      // Fast health check; cached 60s
    AiSuggestionResult generateSuggestion(AiContext context);
}
```

### Implementation: `OpenRouterAiProvider`

**Configuration (via `application.properties`)**
```properties
ai.provider=openrouter                          # default
ai.openrouter.api-key=${OPENROUTER_API_KEY}
ai.openrouter.model=anthropic/claude-sonnet-4-20250514
ai.openrouter.max-tokens=1024
ai.openrouter.base-url=https://openrouter.ai/api/v1
```

**Request Flow**

1. **Resolve Prompt Template**
   - File pattern: `prompts/{suggestion-type}.txt` (from classpath)
   - Fallback: Generic system prompt if file not found
   - Version tracking: Prompt filename used as version identifier (e.g., `commit-draft-assist-v2`)

2. **Build User Message**
   - Serializes entire `AiContext` to JSON
   - Includes: `committData`, `planData`, `historicalCommits`, `rcdoTree`, `additionalContext`
   - All nulls replaced with empty collections/maps for stability

3. **HTTP Call to OpenRouter**
   ```
   POST {baseUrl}/chat/completions
   Headers:
     - Authorization: Bearer {apiKey}
     - Content-Type: application/json
     - HTTP-Referer: https://weeklycommit.dev
     - X-Title: Weekly Commit
   Body:
     {
       "model": "anthropic/claude-sonnet-4-20250514",
       "max_tokens": 1024,
       "messages": [
         {"role": "system", "content": "{systemPrompt}"},
         {"role": "user", "content": "{userMessage}"}
       ]
     }
   ```

4. **Response Parsing**
   - Extract JSON from markdown code fences or raw braces
   - Validate JSON parseability
   - Extract usage tokens for metrics tracking
   - Return `AiSuggestionResult(available, jsonPayload, rationale, confidence, modelVersion, promptVersion)`

**Rate Limiting**
- Token bucket: 30 requests/minute window
- Checked before every request; returns `unavailable()` if exceeded
- Window resets per minute boundary

**Health Check**
- Cached 60 seconds
- Calls `{baseUrl}/models` to verify API key validity
- Fallback: report unavailable if API key is blank

---

## Core AI Services

### AiContext Input Schema

All services populate `AiContext` with the same discriminator and field structure:

```java
record AiContext(
    String suggestionType,                      // Discriminator constant
    UUID userId,                                // Requesting user
    UUID planId,                                // Associated plan (may be null)
    UUID commitId,                              // Associated commit (may be null)
    Map<String, Object> commitData,             // Commit fields
    Map<String, Object> planData,               // Plan/team fields
    List<Map<String, Object>> historicalCommits,// Recent commits for pattern matching
    List<Map<String, Object>> rcdoTree,         // RCDO hierarchy
    Map<String, Object> additionalContext       // Free-form context
) {}
```

**Suggestion Type Constants**
- `TYPE_COMMIT_DRAFT = "COMMIT_DRAFT_ASSIST"`
- `TYPE_COMMIT_LINT = "COMMIT_LINT"`
- `TYPE_RCDO_SUGGEST = "RCDO_SUGGEST"`
- `TYPE_RISK_SIGNAL = "RISK_SIGNAL"`
- `TYPE_RECONCILE_ASSIST = "RECONCILE_ASSIST"`
- `TYPE_TEAM_SUMMARY = "TEAM_SUMMARY"`
- `TYPE_RAG_INTENT = "RAG_INTENT"`
- `TYPE_RAG_QUERY = "RAG_QUERY"`
- `TYPE_TEAM_INSIGHT = "TEAM_INSIGHT"`
- `TYPE_PERSONAL_INSIGHT = "PERSONAL_INSIGHT"`
- `TYPE_WHAT_IF = "WHAT_IF"`

---

### Capability 1: Commit Draft Assistance

**Service**: `CommitDraftAssistService`

**Input Data**
```java
record CommitDraftAssistRequest(
    UUID userId,
    UUID planId,
    UUID commitId,
    String currentTitle,
    String currentDescription,
    String currentSuccessCriteria,
    Integer currentEstimatePoints,
    ChessPiece chessPiece
) {}
```

**Data Collection**
- Current commit fields → `commitData`
- Plan: week start date, capacity budget → `planData`
- User's past commits (by `ownerUserId`) → `historicalCommits` (flatten to: title, estimatePoints, chessPiece, outcome)
- RCDO tree: all active nodes → `rcdoTree` (flatten to: id, title, nodeType, parentId)

**LLM Context**
- Prompt template: `commit-draft-assist.txt`
- System role: Improve drafts for clarity, outcome-focus, scoping
- Response schema: `{suggestedTitle, suggestedDescription, suggestedSuccessCriteria, suggestedEstimatePoints, suggestedChessPiece}`

**Response Parsing**
```java
private CommitDraftAssistResponse parseResponse(UUID suggestionId, AiSuggestionResult result) {
    JsonNode node = objectMapper.readTree(result.payload());
    return new CommitDraftAssistResponse(
        true,
        suggestionId,
        textOrNull(node, "suggestedTitle"),
        textOrNull(node, "suggestedDescription"),
        textOrNull(node, "suggestedSuccessCriteria"),
        node.has("suggestedEstimatePoints") ? node.get("suggestedEstimatePoints").asInt() : null,
        parseChessPiece(node, "suggestedChessPiece"),
        result.rationale()
    );
}
```

**Output DTO**
```java
record CommitDraftAssistResponse(
    boolean aiAvailable,
    UUID suggestionId,
    String suggestedTitle,
    String suggestedDescription,
    String suggestedSuccessCriteria,
    Integer suggestedEstimatePoints,
    ChessPiece suggestedChessPiece,
    String rationale
) {}
```

**Endpoint**: `POST /api/ai/commit-draft-assist`

---

### Capability 2: Commit Quality Lint

**Service**: `CommitLintService`

**Input Data**
```java
record CommitLintRequest(UUID userId, UUID planId) {}
```

**Data Collection**
- Collects ALL commits in the plan
- Hard validation: applied to all commits (duplicate titles, missing success criteria for KING/QUEEN, vague titles)
- Soft guidance: fragmentation check (>12 commits), estimate inconsistency, parent-level RCDO, etc.

**Rules-Based Checks (Non-LLM)**
```
HARD:
  - Missing success criteria for KING/QUEEN
  - Duplicate/near-duplicate title
  
SOFT:
  - Vague title (<10 chars or all generic words)
  - Parent-level RCDO node when leaf exists
  - Low estimate for KING/QUEEN (≤1 pt)
  - Plan fragmentation (>12 commits)
```

**Optional LLM Enrichment**
- Calls `TYPE_COMMIT_LINT` to log/learn (output not parsed)
- LLM context: `planData: {commitCount}`, `historicalCommits: all commits in plan`
- Failure is swallowed; lint never fails due to AI

**Output DTO**
```java
record CommitLintResponse(
    boolean aiAvailable,
    List<LintMessage> hardValidation,   // Blocks lock
    List<LintMessage> softGuidance      // Informational
) {}

record LintMessage(
    String code,                        // e.g., "DUPLICATE_TITLE"
    String message,
    UUID commitId
) {}
```

**Endpoint**: `POST /api/ai/commit-lint`

---

### Capability 3: RCDO Link Suggestion

**Service**: `RcdoSuggestService`

**Input Data**
```java
record RcdoSuggestRequest(
    UUID userId,
    UUID planId,
    String title,
    String description,
    ChessPiece chessPiece
) {}
```

**Confidence Threshold**: 0.7 (hardcoded in service)

**Data Collection**
- Commit: title, description, chessPiece → `commitData`
- RCDO tree: all active nodes → `rcdoTree`

**LLM Context**
- Prompt template: `rcdo-suggest.txt`
- System role: Match commit to RCDO Outcome leaf nodes
- Response schema: `{suggestedRcdoNodeId, confidence, rationale}`

**Response Parsing**
- Extracts UUID from `suggestedRcdoNodeId` field
- Returns `belowThreshold()` if confidence < 0.7
- Validates suggested node exists in DB
- Looks up node title for display

**Output DTO**
```java
record RcdoSuggestResponse(
    boolean aiAvailable,
    boolean hasAboveThresholdSuggestion,
    UUID suggestionId,
    UUID suggestedRcdoNodeId,
    String rcdoNodeTitle,
    double confidence,
    String rationale
) {}
```

**Endpoint**: `POST /api/ai/rcdo-suggest`

---

### Capability 4: Risk Signal Detection

**Service**: `RiskDetectionService`

**Key Components**

1. **Rules-Based Signals** (always computed, never skipped):
   ```
   OVERCOMMIT: totalPoints > capacityBudget
   UNDERCOMMIT: totalPoints / capacityBudget < 0.60
   REPEATED_CARRY_FORWARD: any commit with streak ≥ 2
   BLOCKED_CRITICAL: KING/QUEEN with BLOCKED ticket > 48 hours
   SCOPE_VOLATILITY: >3 post-lock scope change events
   ```

2. **LLM Augmentation** (supplementary, handles graceful degradation):
   ```
   Input: Full plan + commits + scope changes
   Output: {"signals": [{"signalType": "AI_RISK_DETECTED", "commitId": null, "severity": "MEDIUM", "description": "...", "suggestedAction": "..."}]}
   ```

**Invocation Points**
- On plan lock: `detectAndStoreRiskSignalsById(planId)`
- Daily sweep: `@Scheduled(cron = "0 0 7 * * *") runDailyRiskDetection()`

**Data Collection for LLM**
```java
AiContext context = new AiContext(
    AiContext.TYPE_RISK_SIGNAL,
    plan.getOwnerUserId(),
    plan.getId(),
    null,
    Map.of(),
    planData: {
        state, capacityBudgetPoints, weekStartDate, totalPlannedPoints
    },
    commitDataList: [{
        commitId, title, chessPiece, estimatePoints, 
        description, successCriteria, carryForwardStreak, outcome
    }],
    List.of(),
    additionalContext: {
        scopeChanges: [{category, reason}]
    }
);
```

**LLM Response Parsing**
```java
private List<RawSignal> parseAiRiskSignals(String payload) {
    JsonNode root = objectMapper.readTree(payload);
    JsonNode signalsNode = root.get("signals");
    // For each signal: extract signalType, commitId, severity, description, suggestedAction
    // Build RawSignal(type, commitId, rationale)
}
```

**Persistence**
- Existing risk signals for plan are deleted first
- New signals upserted to `ai_suggestion` table with:
  - `suggestionType = "RISK_SIGNAL"`
  - `suggestionPayload = {"signalType": "OVERCOMMIT"|"AI_RISK_DETECTED"|...}`
  - `rationale` populated with signal description + suggested action

**Query Endpoint**: `GET /api/plans/{id}/risk-signals`
- Returns list of active signals for plan
- Privacy: only plan owner + manager + admin can access

---

### Capability 5: Reconciliation Assistance

**Service**: `ReconcileAssistService`

**Input Data**
```java
record ReconcileAssistRequest(UUID userId, UUID planId) {}
```

**Data Collection**
```java
planData: {
    weekStartDate, state, scopeChanges (count), commitCount
}
commitList (historicalCommits): [{
    id, title, chessPiece, outcome, carryForwardStreak
}]
```

**LLM Context**
- Prompt template: `reconcile-assist.txt`
- System role: Suggest commit outcomes, draft summary, carry-forward recommendations
- Response schema:
  ```json
  {
    "draftSummary": "2-3 sentence narrative",
    "likelyOutcomes": [
      {"commitId": "...", "commitTitle": "...", "suggestedOutcome": "ACHIEVED|PARTIALLY_ACHIEVED|NOT_ACHIEVED|CANCELED", "rationale": "..."}
    ],
    "carryForwardRecommendations": [
      {"commitId": "...", "commitTitle": "...", "rationale": "..."}
    ]
  }
  ```

**Response Parsing**
```java
private ReconcileAssistResponse parseResponse(UUID suggestionId, AiSuggestionResult result, List<WeeklyCommit> commits) {
    // Parse draftSummary, likelyOutcomes[], carryForwardRecommendations[]
    // Build response DTO with parsed values
}
```

**Output DTO**
```java
record ReconcileAssistResponse(
    boolean aiAvailable,
    UUID suggestionId,
    List<CommitOutcomeSuggestion> outcomes,
    String draftSummary,
    List<CarryForwardRecommendation> carryForwards
) {}
```

**Endpoint**: `POST /api/ai/reconcile-assist`

---

### Capability 5b: What-If Plan Simulation

**Service**: `WhatIfService` (Pure computation + optional LLM narration)

**Input Data**
```java
record WhatIfRequest(
    UUID userId,
    UUID planId,
    List<WhatIfMutation> hypotheticalChanges  // [ADD_COMMIT, REMOVE_COMMIT, MODIFY_COMMIT]
) {}

record WhatIfMutation(
    WhatIfAction action,
    UUID commitId,
    String title,
    String chessPiece,
    Integer estimatePoints,
    UUID rcdoNodeId
) {}

enum WhatIfAction { ADD_COMMIT, REMOVE_COMMIT, MODIFY_COMMIT }
```

**Computation (No LLM)**
1. Load original plan + commits
2. Apply mutations in-memory (no persistence)
3. Build two snapshots: current state, projected state
4. Compute:
   - Capacity delta
   - Per-RCDO coverage changes
   - Risk signal delta (before/after, new, resolved)

**Risk Detection Replica** (Embedded in WhatIfService)
- Duplicates the 5 rules from `RiskDetectionService` inline
- No persistence; purely in-memory computation
- Thresholds match exactly (UNDERCOMMIT: 0.60, BLOCKED_CRITICAL_HOURS: 48, etc.)

**Optional LLM Narration** (Async, gracefully degraded)
```java
AiContext context = new AiContext(
    AiContext.TYPE_WHAT_IF,
    request.userId(),
    request.planId(),
    null,
    Map.of(),
    Map.of(),
    List.of(),
    List.of(),
    additionalContext: {
        currentTotalPoints, projectedTotalPoints, capacityBudget, capacityDelta,
        currentRiskSignals[], projectedRiskSignals[],
        newRisks[], resolvedRisks[],
        rcdoCoverageChanges[]
    }
);
```

**LLM Response Parsing**
- Response schema: `{narrative, recommendation}`
- Returns `[null, null]` on any failure; structured response always returned

**Output DTO**
```java
record WhatIfResponse(
    boolean aiAvailable,
    PlanSnapshot currentState,
    PlanSnapshot projectedState,
    int capacityDelta,
    List<RcdoCoverageChange> coverageChanges,
    RiskDelta riskDelta,
    String narrative,          // null if AI unavailable
    String recommendation      // null if AI unavailable
) {}

record PlanSnapshot(
    int totalPoints,
    int capacityBudget,
    List<String> riskSignals,   // ["OVERCOMMIT", "REPEATED_CARRY_FORWARD"]
    Map<UUID, Integer> rcdoCoverage  // rcdoNodeId → totalPoints
) {}
```

**Endpoint**: `POST /api/ai/what-if`

---

### Capability 6: Manager Team AI Summary

**Service**: `ManagerAiSummaryService`

**Access Control**: Managers + Admins only; throws `AccessDeniedException` for non-managers

**Input Data**
```java
public ManagerAiSummaryResponse getSummary(UUID teamId, LocalDate weekStart, UUID callerId)
```

**Data Collection**
```java
additionalContext: {
    unresolvedExceptions: count,
    carryForwardPatterns: ["X commits carried forward", "Y with streak ≥2"],
    criticalBlockedItems: count,
    memberCount,
    topRcdoBranches: ["Branch title (Xpts)", ...]
}
```

**LLM Context**
- Prompt template: `team-summary.txt`
- System role: Generate manager-focused narrative with risks, wins, and action items
- Response schema: `{summaryText}`

**Output DTO**
```java
record ManagerAiSummaryResponse(
    boolean aiAvailable,
    UUID suggestionId,
    UUID teamId,
    LocalDate weekStart,
    String summaryText,
    List<String> topRcdoBranches,
    List<UUID> unresolvedExceptionIds,
    List<String> carryForwardPatterns,
    List<UUID> criticalBlockedItemIds,
    String modelVersion
) {}
```

**Endpoint**: `GET /api/teams/{id}/week/{weekStart}/ai-summary`

---

### Capability 7: RAG Query (Semantic Search)

**Service**: `SemanticQueryService`

**Pipeline**
1. **Intent Classification** (LLM-based)
   - Prompt: `rag-intent.txt`
   - Extracts: entity types, time range, user filter, keywords
   - Handles relative date expressions using `currentDate`, `lastWeekFrom`, `thisWeekFrom`

2. **Embedding**
   - EmbeddingService: converts question → float[]
   - Model: `openai/text-embedding-3-small` (via OpenRouter)

3. **Pinecone Retrieval**
   - Metadata filters: teamId, userId (if applicable), entityType, weekStartEpochDay (for date ranges)
   - Top-K: 40 chunks

4. **Answer Generation** (LLM-based)
   - Prompt: `rag-query.txt`
   - Incorporates retrieved chunks into context
   - Response schema: `{answer, sources: [{entityType, entityId, weekStartDate, snippet}], confidence}`

**Data Flow**
```java
public RagQueryResult query(String question, UUID teamId, UUID userId) {
    // Step 1: Classify intent
    IntentResult intent = classifyIntent(question, teamId, userId);
    
    // Step 2: Embed question
    float[] vector = embeddingService.embed(question);
    
    // Step 3: Build Pinecone filter from intent
    Map<String, Object> filter = buildFilter(intent, teamId, userId);
    
    // Step 4: Query Pinecone
    String namespace = resolveNamespace(teamId);
    List<PineconeMatch> matches = pineconeClient.query(namespace, vector, TOP_K, filter);
    
    // Step 5: Build RAG context
    String contextString = buildContextString(question, matches);
    AiContext ragContext = new AiContext(
        AiContext.TYPE_RAG_QUERY,
        userId, null, null,
        Map.of(), Map.of(), List.of(), List.of(),
        additionalContext: buildRagAdditionalContext(question, matches)
    );
    
    // Step 6: Call LLM
    AiSuggestionResult llmResult = aiProviderRegistry.generateSuggestion(ragContext);
    
    // Step 7: Parse answer
    RagAnswer ragAnswer = parseRagAnswer(llmResult);
    
    // Step 8: Store audit record
    AiSuggestion stored = aiSuggestionService.storeSuggestion(AiContext.TYPE_RAG_QUERY, userId, null, null, contextString, llmResult);
    
    // Step 9: Return result
    return new RagQueryResult(true, ragAnswer.answer(), ragAnswer.sources(), ragAnswer.confidence(), stored.getId());
}
```

**Output DTO**
```java
record RagQueryResponse(
    boolean aiAvailable,
    String answer,
    List<RagSourceDto> sources,
    double confidence,
    UUID suggestionId
) {}

record RagSourceDto(
    String entityType,           // "commit", "plan_summary", "scope_change", etc.
    String entityId,             // UUID
    String weekStartDate,
    String snippet               // 30-80 char excerpt
) {}
```

**Endpoint**: `POST /api/ai/query`

---

### Supporting Capability: Proactive Team & Personal Insights

**Service**: `InsightGenerationService` (scheduled job, not covered in detail here)

**Team Insights** (`TYPE_TEAM_INSIGHT`)
- Prompt: `team-insight.txt`
- Triggered: weekly on team schedule
- Stored in `ai_suggestion` table with `teamId`, `weekStartDate`
- Retrieved via: `GET /api/teams/{id}/week/{weekStart}/ai-insights`

**Personal Insights** (`TYPE_PERSONAL_INSIGHT`)
- Prompt: `personal-insight.txt`
- Triggered: on plan lock + daily sweep
- Stored in `ai_suggestion` table with `planId`
- Retrieved via: `GET /api/plans/{id}/ai-insights`

**Endpoint**: `GET /api/plans/{id}/ai-insights`

---

## RAG System

### Overview

The RAG (Retrieval-Augmented Generation) system captures domain knowledge into Pinecone vectors and retrieves relevant context for LLM queries.

### Chunk Building

**Service**: `ChunkBuilder`

**Chunk Types & Structure**

| Entity Type | ID Format | Key Fields | Enrichment |
|---|---|---|---|
| **commit** | `commit:{uuid}` | title, chess, points, outcome, success criteria | RCDO path, owner name, team, carry-forward lineage, linked ticket, cross-team overlap |
| **plan_summary** | `plan_summary:{uuid}` | week date, state, point totals, achieved count, commit count | owner, team, RCDO distribution, inline commit listing |
| **scope_change** | `scope_change:{uuid}` | category, reason, commit title | week date, team, affected commit |
| **carry_forward** | `carry_forward:{uuid}` | commit title, streak, reason | RCDO path, team, source week |
| **manager_comment** | `manager_comment:{uuid}` | content, author | week date, team, associated plan/commit |
| **ticket** | `ticket:{uuid}` | key, title, status, priority | RCDO path, assignee name |

**Metadata Schema** (stored in Pinecone alongside vector)

```json
{
  "entityType": "commit|plan_summary|scope_change|carry_forward|manager_comment|ticket",
  "entityId": "UUID",
  "userId": "UUID of owner|author",
  "teamId": "UUID",
  "planId": "UUID (if applicable)",
  "weekStartDate": "YYYY-MM-DD (ISO)",
  "weekStartEpochDay": 19000 (long for $gte/$lte filtering),
  "text": "full chunk text"
}
```

**Enrichment Context** (resolved before chunking)

```java
record EnrichmentContext(
    String rcdoPath,                    // "Rally Cry: X > DO: Y > Outcome: Z"
    String teamName,
    String ownerDisplayName,
    String carryForwardLineage,         // "Carried forward 3 weeks (original: ...)"
    String linkedTicketSummary,         // "WC-42 title [IN_PROGRESS]"
    String crossTeamRcdoNote            // "Also targeted by: Backend, QA"
) {}
```

**Example: Commit Chunk Text**

```
"Implement OAuth provider integration" — ROOK, 5pts. 
Owner: Alice Chen. 
RCDO path: Rally Cry: Scale Infrastructure > DO: Improve Auth > Outcome: Multi-provider OAuth. 
Team: Backend. 
Also targeted by: Frontend, Platform. 
Carried forward 1 week (original: "Implement OAuth provider integration", week 2026-03-16). 
Linked ticket: AUTH-123 OAuth provider integration [IN_PROGRESS]. 
Description: Add support for GitHub and Google OAuth providers. 
Success criteria: OAuth login flow works for GitHub and Google, tokens refreshed correctly. 
Outcome: ACHIEVED. 
Notes: ...
```

### Embedding Service

**Service**: `EmbeddingService`

**Configuration**
```properties
ai.embedding.model=openai/text-embedding-3-small   # default
ai.openrouter.api-key=${OPENROUTER_API_KEY}
ai.openrouter.base-url=https://openrouter.ai/api/v1
```

**Public API**
```java
public boolean isAvailable();                   // API key configured
public float[] embed(String text);              // Returns float[0] on any error
```

**HTTP Call**
```
POST {baseUrl}/embeddings
{
  "model": "openai/text-embedding-3-small",
  "input": ["text to embed"]
}
Response:
{
  "data": [{"embedding": [0.123, -0.456, ...]}],
  "usage": {"tokens": 10}
}
```

**Error Handling**: Returns `float[0]` on any exception; never throws

### Semantic Index Service

**Service**: `SemanticIndexService`

**Async Indexing**
```java
@Async
public void indexEntity(String entityType, UUID entityId);

@Async
public void deleteEntity(String entityType, UUID entityId);
```

**Scheduled Sweep**
```java
@Scheduled(cron = "0 0 3 * * *")  // Daily 03:00 UTC
public void dailySweepReindex();   // Re-indexes all plans updated in last 48h
```

**Full Reindex** (Admin endpoint)
```java
public int fullReindex();  // Returns plan count queued
```

**Namespace Partitioning**
- Pinecone namespace = organization ID (string)
- All team entities use `team.getOrganizationId()` as namespace
- Enables multi-tenant isolation

**Indexing Flow for Each Entity Type**

1. **Commit**:
   - Resolve RCDO ancestry path
   - Resolve team name, owner name
   - Resolve carry-forward lineage (if streak > 0)
   - Resolve linked ticket summary
   - Resolve cross-team RCDO overlap
   - Build chunk with enrichment
   - Embed text
   - Upsert to Pinecone

2. **Plan Summary**:
   - Compute point totals, achievement ratio, achievement count
   - Resolve RCDO effort distribution (sum points per RCDO node)
   - Build chunk with inline commit listing
   - Embed and upsert

3. **Scope Change / Carry Forward / Manager Comment / Ticket**: Similar enrichment patterns

### Semantic Query Service

**Service**: `SemanticQueryService`

**Query Pipeline**

```java
public RagQueryResult query(String question, UUID teamId, UUID userId) {
    // 1. Intent classification
    IntentResult intent = classifyIntent(question, teamId, userId);
    
    // 2. Embed question
    float[] vector = embeddingService.embed(question);
    
    // 3. Build filter from intent
    Map<String, Object> filter = buildFilter(intent, teamId, userId);
    // - teamId (always applied)
    // - userId (if intent.userFilter == "self" or "me")
    // - entityType (single or {"$in": [...]})
    // - weekStartEpochDay (range: {"$gte": epochFrom, "$lte": epochTo})
    
    // 4. Query Pinecone
    String namespace = resolveNamespace(teamId);
    List<PineconeMatch> matches = pineconeClient.query(namespace, vector, TOP_K=40, filter);
    
    // 5. Build RAG context from matches
    Map<String, Object> ragContext = buildRagAdditionalContext(question, matches);
    // Includes: question, currentDate, retrievedChunks: [{id, score, metadata}]
    
    // 6. Call LLM with RAG prompt
    AiContext context = new AiContext(
        AiContext.TYPE_RAG_QUERY, userId, null, null,
        Map.of(), Map.of(), List.of(), List.of(), ragContext
    );
    AiSuggestionResult result = aiProviderRegistry.generateSuggestion(context);
    
    // 7. Parse response
    RagAnswer answer = parseRagAnswer(result);
    // Extracts: answer (string), sources ({entityType, entityId, weekStartDate, snippet}), confidence
    
    // 8. Store audit
    AiSuggestion stored = aiSuggestionService.storeSuggestion(...);
    
    // 9. Return result
    return new RagQueryResult(true, answer.answer(), answer.sources(), answer.confidence(), stored.getId());
}
```

**Intent Classification Prompt Output**
```json
{
  "intent": "status_query|risk_query|carry_forward_query|plan_summary_query|ticket_query|scope_change_query|general_query",
  "userFilter": "self|team|null",
  "entityTypes": ["commit", "plan_summary"],
  "timeRange": {"from": "YYYY-MM-DD", "to": "YYYY-MM-DD"},
  "keywords": ["keyword1", "keyword2"]
}
```

**Date Computation** (inside intent classifier)
- `currentDate`: provided as input (today's ISO date)
- `lastWeekFrom` / `lastWeekTo`: Monday–Sunday of previous week (provided as input)
- `thisWeekFrom`: Monday of current week (provided as input)

Examples:
- "last week" → use `lastWeekFrom` and `lastWeekTo`
- "this week" → from `thisWeekFrom` to `currentDate`
- "last month" → from 28 days before `thisWeekFrom` to `currentDate`
- No time reference → null (search all time)

---

## Evaluation & Metrics

### Faithfulness Evaluation

**Service**: `FaithfulnessEvaluator`

**Purpose**: Assess whether every factual claim in AI output is grounded in the provided context

**Sampling Strategy**
```java
// High-stakes types: ALWAYS evaluate
Set<String> HIGH_STAKES_TYPES = {
    AiContext.TYPE_RISK_SIGNAL,
    AiContext.TYPE_TEAM_INSIGHT,
    AiContext.TYPE_PERSONAL_INSIGHT,
    AiContext.TYPE_RAG_QUERY
};

// Lower-stakes types: sample at 10%
double LOW_STAKES_SAMPLE_RATE = 0.10;
```

**Async Scoring**
```java
@Async
public void maybeScopeAsync(UUID suggestionId) {
    AiSuggestion suggestion = suggestionRepo.findById(suggestionId).orElse(null);
    
    // Skip if already scored
    if (suggestion.getEvalFaithfulnessScore() != null) return;
    
    // Decide whether to score
    boolean highStakes = HIGH_STAKES_TYPES.contains(suggestion.getSuggestionType());
    if (!highStakes && Math.random() > LOW_STAKES_SAMPLE_RATE) return;
    
    // Score
    scoreFaithfulness(suggestion);
}
```

**Scoring Process**
```java
@Transactional
public void scoreFaithfulness(AiSuggestion suggestion) {
    String answer = suggestion.getSuggestionPayload();  // The LLM's output
    String context = suggestion.getPrompt();            // The input context
    
    AiContext judgeContext = new AiContext(
        "FAITHFULNESS_EVAL", null, null, null,
        Map.of(), Map.of(), List.of(), List.of(),
        additionalContext: {
            answer, retrievedContext: context, suggestionType
        }
    );
    
    AiSuggestionResult judgeResult = aiProviderRegistry.generateSuggestion(judgeContext);
    
    // Parse scores from judge response
    float faithfulness = parseScore(judgeResult.payload(), "faithfulnessScore");  // [0, 1]
    float relevancy = parseScore(judgeResult.payload(), "relevancyScore");        // [0, 1]
    
    // Write back to DB
    suggestion.setEvalFaithfulnessScore(faithfulness >= 0 ? faithfulness : null);
    suggestion.setEvalRelevancyScore(relevancy >= 0 ? relevancy : null);
    suggestion.setEvalScoredAt(Instant.now());
    suggestionRepo.save(suggestion);
    
    // Alert on low scores
    if (faithfulness >= 0 && faithfulness < 0.85) {
        log.warn("LOW FAITHFULNESS ({}) for {} suggestion {}", faithfulness, suggestion.getSuggestionType(), suggestion.getId());
    }
}
```

**Judge Prompt Output Schema**
```json
{
  "faithfulnessScore": 0.85,
  "relevancyScore": 0.90,
  "totalClaims": 6,
  "supportedClaims": 5,
  "unsupportedClaims": ["any claim not found in context"]
}
```

### Metrics & Observability

**Service**: `AiQualityMetrics`

**Prometheus Gauges**
```
weekly_commit_ai_faithfulness_score{suggestion_type="RISK_SIGNAL"}      # 7-day rolling avg
weekly_commit_ai_acceptance_rate{suggestion_type="COMMIT_DRAFT_ASSIST"} # Accepted / (Accepted + Dismissed)
weekly_commit_ai_provider_available                                      # 1 if up, 0 if down
weekly_commit_ai_tokens_total                                            # Total tokens consumed
weekly_commit_ai_requests_total                                          # Total requests made
```

**Refresh Cycle**
```java
@Scheduled(fixedRate = 300_000, initialDelay = 30_000)  // Every 5 minutes
public void refreshMetrics() {
    // Queries DB for avg faithfulness by type (last 7 days)
    // Queries DB for acceptance rate by type (last 7 days)
    // Polls provider.isAvailable()
    // Reads cumulative token/request counters from provider
}
```

**Database Queries**
```sql
-- Avg faithfulness by suggestion type (last 7 days)
SELECT suggestion_type, AVG(eval_faithfulness_score) 
FROM ai_suggestion 
WHERE created_at >= NOW() - INTERVAL 7 DAY
  AND eval_faithfulness_score IS NOT NULL
GROUP BY suggestion_type;

-- Acceptance rate by type (last 7 days)
SELECT suggestion_type, 
       SUM(CASE WHEN accepted = true THEN 1 ELSE 0 END) as accepted,
       COUNT(*) as total
FROM ai_suggestion 
WHERE created_at >= NOW() - INTERVAL 7 DAY
GROUP BY suggestion_type;
```

---

## Structured Evidence

**Service**: `StructuredEvidenceService`

**Purpose**: Gather the exact SQL facts, lineage, semantic retrieval results, and risk features that informed an AI decision

**Four Retrieval Strategies**

1. **SQL Facts**
   - Exact current state (plan state, total points, achievement count, etc.)
   - Distribution by chess piece
   - Carry-forward counts
   - Scope change counts
   - Lock/reconcile compliance flags

2. **Lineage**
   - Carry-forward chain (trace back to original commit)
   - RCDO ancestry path
   - Scope-change timeline

3. **Semantic Retrieval** (Pinecone)
   - Query: embedding of question or plan context
   - Top-K: 10 chunks
   - Returns: entity type, entity ID, week date, snippet text, score

4. **Risk Features**
   - Current-week completion ratio
   - Historical 4-week avg completion
   - Max carry-forward streak
   - Chess piece distribution
   - Active risk signal types

**Public API**
```java
public StructuredEvidence gatherForPlan(UUID planId, String question);
public StructuredEvidence gatherForCommit(UUID commitId, String question);
```

**Response DTO**
```java
record StructuredEvidence(
    SqlFacts sqlFacts,
    LineageChain lineage,
    List<SemanticMatch> semanticMatches,
    RiskFeatures riskFeatures
) {}

record SqlFacts(
    UUID userId, String userDisplayName,
    UUID teamId, String teamName,
    UUID planId, LocalDate weekStartDate,
    String planState,
    int capacityBudget, int totalPlanned, int totalAchieved,
    int commitCount, int carryForwardCount,
    int scopeChangeCount,
    boolean lockCompliance, boolean reconcileCompliance,
    Map<String, Integer> chessDistribution  // KING: 1, QUEEN: 2, ...
) {}

record LineageChain(
    UUID originalCommitId, String originalTitle, LocalDate originalWeek,
    List<UUID> intermediateCommitIds,
    UUID currentCommitId,
    int totalStreak
) {}

record SemanticMatch(
    String entityType,
    String entityId,
    float score,
    String weekStartDate,
    String snippet
) {}

record RiskFeatures(
    double completionRatio,           // Current week
    double avgCompletionRatio4w,      // 4-week avg
    int maxCarryForwardStreak,
    int scopeChangeCount,
    int kingCount,
    int queenCount,
    List<String> activeRiskTypes      // ["OVERCOMMIT", "BLOCKED_CRITICAL"]
) {}
```

**Endpoints**
- `GET /api/plans/{planId}/evidence?question=...`
- `GET /api/commits/{commitId}/evidence?question=...`

---

## Prompt Templates

### 1. `commit-draft-assist.txt`

**Role**: Improve commit drafts (title, description, success criteria, estimate, chess piece)

**Key Rules**
- Set field to null if already good; don't suggest for the sake of it
- Titles describe OUTCOMES not ACTIVITIES
- Titles ≤100 characters (hard max); suggest shorter version if exceeded
- Success criteria must be measurable
- Infer chess piece from context if null; never override explicit choice
- Check historical commits for estimate calibration

**Input Schema**
```json
{
  "suggestionType": "COMMIT_DRAFT_ASSIST",
  "commitData": {
    "title": "current title",
    "description": "current description",
    "chessPiece": "KING|QUEEN|ROOK|BISHOP|KNIGHT|PAWN|null",
    "estimatePoints": 3,
    "successCriteria": "current criteria"
  },
  "planData": {
    "weekStartDate": "2026-03-23",
    "capacityBudgetPoints": 10,
    "totalPlannedPoints": 8
  },
  "historicalCommits": [
    {
      "title": "past commit title",
      "chessPiece": "ROOK",
      "estimatePoints": 3,
      "outcome": "ACHIEVED"
    }
  ],
  "rcdoTree": [
    {"id": "uuid", "title": "node title", "nodeType": "RALLY_CRY|DEFINING_OBJECTIVE|OUTCOME", "parentId": "parent-uuid", "status": "ACTIVE"}
  ],
  "additionalContext": {}
}
```

**Output Schema**
```json
{
  "suggestedTitle": "improved title or null",
  "suggestedDescription": "improved description or null",
  "suggestedSuccessCriteria": "measurable criteria or null",
  "suggestedEstimatePoints": 3,
  "suggestedChessPiece": "BISHOP|null"
}
```

---

### 2. `commit-lint.txt`

**Role**: Flag quality issues (hard validation = blocks lock, soft guidance = informational)

**Hard Validation Rules**
- KING/QUEEN without success criteria
- Completely meaningless title
- More than 1 KING commit
- More than 2 QUEEN commits

**Soft Guidance**
- Vague/activity-focused title
- Unusual estimate
- Near-duplicate titles
- Fragmentation (>8 commits)
- High PAWN concentration (>40%)
- Parent-level RCDO when leaf exists

**Output Schema**
```json
{
  "hardValidation": [
    {"code": "MISSING_SUCCESS_CRITERIA", "message": "...", "commitId": "uuid"}
  ],
  "softGuidance": [
    {"code": "VAGUE_TITLE", "message": "...", "commitId": "uuid"}
  ]
}
```

---

### 3. `rcdo-suggest.txt`

**Role**: Match commit to RCDO Outcome leaf nodes

**Matching Rules**
- Read commit title + description
- Look for keyword overlap with RCDO node titles
- Consider semantic relationship (does this work move the outcome forward?)
- Prefer specific Outcomes over generic ones
- Link to Outcome, not DO or Rally Cry
- Only link to Active status nodes

**Output Schema**
```json
{
  "suggestedRcdoNodeId": "uuid-or-null",
  "confidence": 0.85,
  "rationale": "brief explanation of why this matches"
}
```

**Confidence Scale**: 0.0 (no match) ... 0.5 (weak) ... 0.7 (reasonable) ... 0.9+ (strong)
- Only suggestions with confidence ≥ 0.7 surfaced to user

---

### 4. `risk-signal.txt`

**Role**: Find risks that rules-based engine misses

**Rules Already Computed** (DO NOT DUPLICATE)
- OVERCOMMIT, UNDERCOMMIT, REPEATED_CARRY_FORWARD, BLOCKED_CRITICAL, SCOPE_VOLATILITY

**What to Look For**
- Unrealistic estimates given complexity
- Too many KING/QUEEN (even if within hard limits)
- Historical patterns suggesting failure
- Hidden dependencies ("waiting on", "after X ships")
- Concentration risk (all high-priority on same system)
- Mismatch between chess piece and estimate

**Output Schema**
```json
{
  "signals": [
    {
      "signalType": "AI_RISK_DETECTED",
      "commitId": "uuid-or-null",
      "severity": "HIGH|MEDIUM|LOW",
      "description": "specific risk referencing actual data",
      "suggestedAction": "specific recommendation"
    }
  ]
}
```

---

### 5. `reconcile-assist.txt`

**Role**: Suggest commit outcomes, draft summary, carry-forward recommendations

**Outcome Inference Rules**
- Linked ticket DONE → ACHIEVED (high confidence)
- Linked ticket IN_PROGRESS → PARTIALLY_ACHIEVED
- Linked ticket BLOCKED/BACKLOG → NOT_ACHIEVED
- Scope change COMMIT_REMOVED → CANCELED
- Unlinked: infer from title/description (lower confidence)
- Carry-forward recommendation for NOT_ACHIEVED/PARTIALLY_ACHIEVED ROOK+

**Output Schema**
```json
{
  "likelyOutcomes": [
    {"commitId": "uuid", "commitTitle": "...", "suggestedOutcome": "ACHIEVED|PARTIALLY_ACHIEVED|NOT_ACHIEVED|CANCELED", "confidence": 0.9, "reason": "evidence-based reason"}
  ],
  "draftSummary": "2-3 sentence narrative of week outcomes",
  "carryForwardRecommendations": [
    {"commitId": "uuid", "commitTitle": "...", "reason": "why carry forward"}
  ]
}
```

---

### 6. `team-summary.txt`

**Role**: Generate manager-focused weekly team health narrative

**Input Data**
- Plan aggregates (locked, reconciled counts, total points, achievement)
- RCDO effort distribution
- Carry-forward patterns
- Critical blocked items
- Unresolved exceptions

**Narrative Style**
- Direct, actionable; reference specific RCDO branches, point totals
- 2-3 focused paragraphs
- Highlight risks and wins; no generic praise

**Output Schema**
```json
{
  "summaryText": "2-3 paragraph narrative with specific data",
  "topRcdoBranches": ["Branch title (Xpts)", "Branch title (Ypts)"],
  "carryForwardPatterns": ["concerning patterns"],
  "criticalBlockedItemIds": ["uuid", "uuid"]
}
```

---

### 7. `rag-intent.txt`

**Role**: Classify user question and extract search filters

**Entity Types Available**
- commit, plan_summary, scope_change, carry_forward, manager_comment, ticket

**Key Rule**: For "what did team/someone commit to" questions, ALWAYS include BOTH "commit" AND "plan_summary" (plan summaries contain inline commit listings)

**Date Computation** (using inputs)
- `currentDate`, `lastWeekFrom`, `lastWeekTo`, `thisWeekFrom` provided
- "last week" → use `lastWeekFrom` to `lastWeekTo`
- "this week" → from `thisWeekFrom` to `currentDate`
- "last month" → from 28 days before `thisWeekFrom` to `currentDate`
- No time reference → null (all time search)

**Output Schema**
```json
{
  "intent": "status_query|risk_query|carry_forward_query|plan_summary_query|ticket_query|scope_change_query|general_query",
  "userFilter": "self|team|null",
  "entityTypes": ["commit", "plan_summary"],
  "timeRange": {"from": "YYYY-MM-DD", "to": "YYYY-MM-DD"},
  "keywords": ["keyword1", "keyword2"]
}
```

---

### 8. `rag-query.txt`

**Role**: Answer semantic questions using retrieved context chunks

**Chunk Prioritization**
- score > 0.7: highly relevant
- score 0.4–0.7: moderately relevant
- score < 0.4: likely noise
- plan_summary chunks are richest (include inline commit listings)

**Answer Rules**
1. Use ONLY facts from retrieved chunks
2. Be specific (titles, chess pieces, point values, week dates)
3. Synthesize across ALL relevant chunks for team questions
4. Say explicitly if chunks don't cover requested time period
5. Keep answer <200 words
6. Include 1–5 sources

**Output Schema**
```json
{
  "answer": "Direct answer under 200 words with specific data",
  "sources": [
    {
      "entityType": "commit|plan_summary|...",
      "entityId": "UUID",
      "weekStartDate": "YYYY-MM-DD",
      "snippet": "30-80 char excerpt from source text"
    }
  ],
  "confidence": 0.75
}
```

**Confidence Scale**
- 0.85–1.0: multiple high-score chunks directly answer
- 0.6–0.84: good coverage with gaps
- 0.35–0.59: partial or tangential data
- 0.0–0.34: insufficient data; explain gaps

---

### 9. `personal-insight.txt`

**Role**: Coach individual on estimation accuracy, carry-forward patterns, chess piece usage

**Data Sources**
- Current week commits
- Historical chunks from Pinecone (semantically similar past patterns)
- Historical performance

**Focus Areas**
- Estimation accuracy (over/under-estimation patterns)
- Carry-forward streaks and what they suggest
- Chess piece usage patterns
- Outcome trends

**Anti-Sycophancy Directive**
- Be honest coach, not validating friend
- If data shows underestimation, SAY SO even if recently improved
- Don't balance criticism with excessive praise
- Carry-forward streaks on important work = PROBLEM (not "ambitious scoping")
- Use second person ("You") for personalization

**Output Schema**
```json
{
  "insights": [
    {
      "insightText": "concise, personalized insight (1-2 sentences) with specific data",
      "severity": "HIGH|MEDIUM|LOW",
      "sourceEntityIds": ["uuid", "uuid"],
      "actionSuggestion": "specific, actionable coaching"
    }
  ]
}
```

**Count**: 1–3 insights (empty array if nothing to highlight)

---

### 10. `team-insight.txt`

**Role**: Generate management advisor insights for recurring patterns, trends

**Data Sources**
- All team commits for the week
- Historical chunks from Pinecone (past weeks' similar patterns)
- Team aggregates

**Focus Areas**
- Carry-forward patterns (recurring blockers, chronic overcommitment)
- Scope volatility (frequent post-lock changes)
- Capacity alignment across members
- KING/QUEEN outcomes
- Team-wide trends

**Anti-Sycophancy Directive**
- Manager audience needs unvarnished truth
- Say underperformance directly with numbers
- Don't manufacture positive insights to balance negatives
- "Delivered 38 of 48 points" not "showed great dedication by delivering"
- Trust > comfort

**Output Schema**
```json
{
  "insights": [
    {
      "insightText": "concise insight (1-2 sentences) with specific data references",
      "severity": "HIGH|MEDIUM|LOW",
      "sourceEntityIds": ["uuid"],
      "actionSuggestion": "specific, actionable recommendation for manager"
    }
  ]
}
```

**Count**: 1–5 insights (empty array if genuinely nothing noteworthy)

---

### 11. `what-if.txt`

**Role**: Narrate impact of hypothetical mutations in plain language

**Input**: Pre-computed impact data (capacity delta, risk delta, coverage changes)

**Narrative Rules**
1. 2–3 sentence summary referencing numbers
2. Mention new risk signals specifically
3. Highlight resolved risks
4. If delta=0 and signals unchanged, say neutral
5. Keep recommendation brief (1 sentence) or null

**Output Schema**
```json
{
  "narrative": "2-3 sentence plain-language impact explanation",
  "recommendation": "one sentence actionable recommendation, or null"
}
```

---

### 12. `faithfulness-eval.txt`

**Role**: Judge whether answer is grounded in provided context

**Scoring**
- Faithfulness: (supported claims) / (total claims)
- Relevancy: does output match what was asked?
- Scale: [0, 1]

**Process**
1. Identify every factual claim in answer
2. Check each against context
3. Score faithfulness
4. List unsupported claims

**Output Schema**
```json
{
  "faithfulnessScore": 0.85,
  "relevancyScore": 0.90,
  "totalClaims": 6,
  "supportedClaims": 5,
  "unsupportedClaims": ["any claim not in context"]
}
```

---

## Integration Examples

### Example 1: Commit Draft Assistance Flow

```
1. User POSTs /api/ai/commit-draft-assist with request
2. CommitDraftAssistService.assist():
   - Load plan + historical commits from DB
   - Populate AiContext
   - Call aiProviderRegistry.generateSuggestion(context)
3. OpenRouterAiProvider.generateSuggestion():
   - Load prompt template: commit-draft-assist.txt
   - Build user message: AiContext serialized to JSON
   - HTTP POST to OpenRouter with system + user message
   - Parse response, extract JSON from code fences
   - Return AiSuggestionResult(available=true, payload={...}, rationale="...", confidence=0.85, modelVersion="claude-sonnet-4-20250514", promptVersion="commit-draft-assist-v2")
4. CommitDraftAssistService.parseResponse():
   - Extract fields from payload: suggestedTitle, suggestedDescription, etc.
   - Build CommitDraftAssistResponse
5. AiSuggestionService.storeSuggestion():
   - Persist to ai_suggestion table with context hash
   - Trigger async FaithfulnessEvaluator.maybeScopeAsync()
   - Trigger async SemanticIndexService.indexEntity("commit", commitId) if applicable
6. HTTP 200 response with CommitDraftAssistResponse
```

### Example 2: Risk Signal Detection Flow

```
1. User locks plan, triggers planLockService.lock()
2. RiskDetectionService.detectAndStoreRiskSignalsById(planId):
   - Delete existing RISK_SIGNAL rows for plan
   - Apply rules: OVERCOMMIT, UNDERCOMMIT, REPEATED_CARRY_FORWARD, BLOCKED_CRITICAL, SCOPE_VOLATILITY
   - Call detectAiRiskSignals() to find additional risks
3. detectAiRiskSignals():
   - Check aiProviderRegistry.isAiEnabled() → false? return empty list
   - Build AiContext with full plan + commits
   - Call generateSuggestion(AiContext.TYPE_RISK_SIGNAL)
   - Parse response JSON: extract signals array
   - For each signal: create RawSignal(type, commitId, rationale)
4. For each signal (rule + AI):
   - Insert ai_suggestion row:
     - suggestionType = "RISK_SIGNAL"
     - suggestionPayload = {"signalType": "OVERCOMMIT"}
     - rationale = description + suggestedAction
     - modelVersion = "rules-v1" or provider.getVersion()
5. Async: FaithfulnessEvaluator scores HIGH_STAKES (always evaluate RISK_SIGNAL)
6. Async: SemanticIndexService indexes plan
7. Manager calls GET /api/plans/{id}/risk-signals → returns list of signals
```

### Example 3: RAG Query Flow

```
1. User POSTs /api/ai/query with {"question": "What did the team commit to last week?", "teamId": "...", "userId": "..."}
2. SemanticQueryService.query():
   - Check Pinecone + EmbeddingService available? → no? return unavailable()
   - Call classifyIntent(question, teamId, userId):
     - Create AiContext with TYPE_RAG_INTENT
     - Call LLM to classify intent
     - Parse response: {"intent": "status_query", "entityTypes": ["commit", "plan_summary"], "timeRange": {"from": "2026-03-16", "to": "2026-03-22"}, ...}
   - Embed question via EmbeddingService.embed()
   - Build Pinecone filter: {"teamId": "...", "entityType": {"$in": ["commit", "plan_summary"]}, "weekStartEpochDay": {"$gte": epochFrom, "$lte": epochTo}}
   - Query Pinecone: pineconeClient.query(namespace, vector, TOP_K=40, filter)
   - Build RAG context: additionalContext = {question, currentDate, retrievedChunks: [{id, score, metadata}]}
   - Create AiContext(TYPE_RAG_QUERY, ...) with RAG context
   - Call LLM with rag-query.txt prompt
   - Parse response: {"answer": "...", "sources": [...], "confidence": 0.85}
   - Store audit: AiSuggestionService.storeSuggestion(TYPE_RAG_QUERY, ...)
   - Return RagQueryResponse with answer + sources + suggestionId
3. Async: FaithfulnessEvaluator scores (HIGH_STAKES: always evaluate RAG_QUERY)
4. Frontend displays answer with source citations and confidence level
```

### Example 4: What-If Simulation Flow

```
1. User POSTs /api/ai/what-if with mutations:
   [
     {"action": "MODIFY_COMMIT", "commitId": "...", "estimatePoints": 5},
     {"action": "ADD_COMMIT", "title": "New task", "chessPiece": "ROOK", "estimatePoints": 3}
   ]
2. WhatIfService.simulate(request):
   - Load original plan + commits from DB
   - Apply mutations in-memory (no persistence):
     - Find commit, update estimatePoints
     - Create synthetic commit, add to list
   - Build two snapshots: current, projected
   - Compute structured impact:
     - Capacity delta: 8 → 16 (added 8 points)
     - Risk signals BEFORE: [] → AFTER: ["OVERCOMMIT"] (now 16 > 10 budget)
     - RCDO coverage changes
   - Optionally fetch LLM narration (gracefully degraded):
     - Build AiContext(TYPE_WHAT_IF) with impact data
     - Call LLM with what-if.txt prompt
     - Parse: {"narrative": "...", "recommendation": "..."}
   - Return WhatIfResponse with structured data + narrative/recommendation
3. Frontend shows before/after snapshots, coverage changes, risk deltas, narrative
```

---

## API Reference

### REST Endpoints

| Endpoint | Method | Service | Request | Response |
|---|---|---|---|---|
| `/api/ai/commit-draft-assist` | POST | CommitDraftAssistService | CommitDraftAssistRequest | CommitDraftAssistResponse |
| `/api/ai/commit-lint` | POST | CommitLintService | CommitLintRequest | CommitLintResponse |
| `/api/ai/rcdo-suggest` | POST | RcdoSuggestService | RcdoSuggestRequest | RcdoSuggestResponse |
| `/api/plans/{id}/risk-signals` | GET | RiskDetectionService | planId, callerId | PlanRiskSignals |
| `/api/ai/reconcile-assist` | POST | ReconcileAssistService | ReconcileAssistRequest | ReconcileAssistResponse |
| `/api/ai/what-if` | POST | WhatIfService | WhatIfRequest | WhatIfResponse |
| `/api/teams/{id}/week/{weekStart}/ai-summary` | GET | ManagerAiSummaryService | teamId, weekStart, callerId | ManagerAiSummaryResponse |
| `/api/ai/query` | POST | SemanticQueryService | RagQueryRequest | RagQueryResponse |
| `/api/teams/{id}/week/{weekStart}/ai-insights` | GET | AiController | teamId, weekStart | InsightListResponse |
| `/api/plans/{id}/ai-insights` | GET | AiController | planId | InsightListResponse |
| `/api/plans/{planId}/evidence` | GET | StructuredEvidenceService | planId, question? | StructuredEvidenceResponse |
| `/api/commits/{commitId}/evidence` | GET | StructuredEvidenceService | commitId, question? | StructuredEvidenceResponse |
| `/api/ai/status` | GET | AiController | — | {aiEnabled, providerName, providerVersion, available} |
| `/api/ai/feedback` | POST | AiSuggestionService | AiFeedbackRequest | 201 Created |
| `/api/admin/ai/reindex` | POST | SemanticIndexService | — | {plansQueued, status, message} |

---

## Summary

The AI pipeline integrates:

1. **7 core capabilities** (draft assist, lint, RCDO suggest, risk signals, reconciliation, team summary, RAG)
2. **2 supporting services** (team insights, personal insights)
3. **RAG system** (chunk building, embedding, Pinecone indexing/querying)
4. **Evaluation** (faithfulness scoring, metrics collection)
5. **Structured evidence** (SQL facts, lineage, semantic retrieval, risk features)

All services:
- Degrade gracefully (no blocking on AI unavailability)
- Persist suggestions for audit + feedback
- Track model/prompt versions for reproducibility
- Support async scoring + indexing to avoid latency

Prompt templates are versioned and loaded per suggestion type, enabling A/B testing and iterative improvement without code changes.
