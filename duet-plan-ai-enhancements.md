# Goal

Implement the research-validated AI enhancement roadmap (P0–P3) for the Weekly Commit Module. This covers: fixing critical AI bugs (P0), upgrading RAG quality with hybrid retrieval + reranking + streaming (P1), adding predictive calibration + query rewriting + evidence-based confidence (P2), and wiring cross-capability typed workflow orchestration (P3). All changes must preserve existing test suites, maintain graceful AI degradation, and follow the established modular monolith patterns.

See `docs/approach_thoughts_decisions.md` for the full research-validated rationale behind every decision.

## Constraints

- **Backend is Java 21 / Spring Boot 3.4** — follow existing package-per-bounded-context patterns in `com.weeklycommit.ai.*`
- **Frontend is React 18 / TypeScript strict / Tailwind CSS 4** — follow existing component patterns in `frontend/src/components/ai/`
- **No new database migrations unless explicitly stated** — prefer application-level changes
- **AI must always degrade gracefully** — if any new service (reranker, hybrid search) is unavailable, fall back to current behavior
- **Do NOT modify `frontend/src/` in backend-only steps or `backend/src/` in frontend-only steps**
- **Do NOT change the database schema** — all read models and tables remain as-is
- **Preserve all existing tests** — new code needs new tests; existing tests must still pass
- **OpenRouter is the LLM proxy** — all structured output changes go through OpenRouter's API
- **Pinecone is the vector store** — hybrid search uses Pinecone's native sparse+dense support

## Steps

### Step 1: Fix OpenRouterAiProvider — Replace regex JSON extraction with structured output mode

Replace the fragile `extractJson()` markdown-fence regex parsing in `OpenRouterAiProvider.java` with OpenRouter's native structured output support (`response_format.type = "json_schema"`). Add proper Jackson deserialization with Jakarta Bean Validation as safety net.

**Scope:** Only `backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java` and its test `backend/src/test/java/com/weeklycommit/ai/provider/OpenRouterAiProviderTest.java`. Do NOT modify any other files.

**Deliverables:**
- Remove the `extractJson()` method and all regex-based fence parsing
- In `callOpenRouter()`, add `response_format: { type: "json_object" }` to the request body (OpenRouter's supported mode for Claude models). Note: full `json_schema` mode requires model-specific support checks, so use `json_object` as the safe baseline that ensures valid JSON without fences
- In `parseResponse()`, parse the response content directly with Jackson `objectMapper.readTree()` — no fence extraction needed
- Remove the `extractRationale()` method (rationale should come from within the JSON payload, not outside it)
- Keep the hard-coded `0.85` confidence for now (Step 9 addresses this properly)
- Update `OpenRouterAiProviderTest.java` to test the new parsing path, including: valid JSON response, malformed JSON fallback to `unavailable()`, empty response handling
- Add a one-retry fallback: if Jackson parsing fails, retry once with the same prompt. If second attempt fails, return `AiSuggestionResult.unavailable()`

**Checks:** lint, typecheck, unit, build

### Step 2: Fix CommitDraftAssistService — Bound historical queries to 12 weeks

Fix the unbounded `findByOwnerUserId()` call that loads ALL past commits for a user into the prompt context.

**Scope:** Only `backend/src/main/java/com/weeklycommit/ai/service/CommitDraftAssistService.java`, `backend/src/main/java/com/weeklycommit/domain/repository/WeeklyCommitRepository.java`, and `backend/src/test/java/com/weeklycommit/ai/service/CommitDraftAssistServiceTest.java`. Do NOT modify any other files.

**Deliverables:**
- Add a new repository method to `WeeklyCommitRepository.java`: `List<WeeklyCommit> findByOwnerUserIdAndCreatedAtAfter(UUID ownerUserId, java.time.Instant cutoff)` (or use a `@Query` with join on `weekly_plan.week_start_date` to filter by the last 12 weeks)
- In `CommitDraftAssistService.assist()`, replace `commitRepo.findByOwnerUserId(plan.getOwnerUserId())` at line 71 with the bounded query using `LocalDate.now().minusWeeks(12)` as cutoff
- Update `CommitDraftAssistServiceTest.java` to verify the bounded query is used (mock should expect the time-bounded call)

**Checks:** lint, typecheck, unit, build

### Step 3: Fix CommitLintService — Remove wasted LLM call and fix RiskDetectionService context persistence

Fix two bugs: (1) `CommitLintService.enrichSoftGuidanceFromAi()` calls the LLM but discards the output — either surface it or remove the call. (2) `RiskDetectionService` persists AI suggestions but the context passed to `storeSuggestion` may be incomplete.

**Scope:** Only `backend/src/main/java/com/weeklycommit/ai/service/CommitLintService.java`, `backend/src/main/java/com/weeklycommit/ai/service/RiskDetectionService.java`, and their corresponding test files. Do NOT modify any other backend or frontend files.

**Deliverables:**
- In `CommitLintService.java`: Remove the `enrichSoftGuidanceFromAi()` method entirely and its call in `lint()`. The comment says "We don't parse the AI output" — this wastes tokens. Remove it. The rules-based lint is sufficient.
- In `CommitLintService.java`: Remove the `AiProviderRegistry` dependency injection if no other method uses it after removing `enrichSoftGuidanceFromAi`
- In `RiskDetectionService.java`: In the `detectAiRiskSignals()` method (around line 324), after calling `aiProviderRegistry.generateSuggestion(context)`, ensure the full context is serialized and passed when storing the suggestion. Currently the AiContext is built correctly with `commitDataList`, `planData`, and `additionalContext` — verify the `AiSuggestionService.storeSuggestion()` call receives this full context as the prompt field (not an empty `{}`). If the store call is missing, add it. If it's using empty context, fix it to serialize the full AiContext.
- Update `CommitLintServiceTest.java` to remove any test expectations about AI provider calls
- Update `RiskDetectionServiceTest.java` to verify full context is stored with each risk suggestion

**Checks:** lint, typecheck, unit, build

### Step 4: Add Pinecone hybrid search support — sparse vector generation and RRF fusion

Add hybrid retrieval (BM25-style sparse + dense vectors) to the Pinecone integration using Pinecone's native sparse-dense support.

**Scope:** Only files in `backend/src/main/java/com/weeklycommit/ai/rag/` and their test counterparts. Do NOT modify any frontend files, controllers, or non-RAG backend files.

**Deliverables:**
- Create a new `backend/src/main/java/com/weeklycommit/ai/rag/SparseEncoder.java` that generates sparse vector representations from chunk text. Use a simple TF-IDF or BM25-style term-frequency approach: tokenize text, compute term frequencies, return a `Map<Integer, Float>` (token hash → weight). Include domain-specific token normalization for terms like "RCDO", "carry-forward", chess piece names (KING, QUEEN, etc.), and common planning terms. This does NOT call any external API — it's a local computation.
- Modify `PineconeClient.java`:
  - Update `PineconeVector` record to accept optional sparse values: `record PineconeVector(String id, float[] values, Map<String, Object> metadata, Map<Integer, Float> sparseValues)`
  - Keep backward compatibility: add a constructor that accepts `null` sparse values
  - In `upsert()`, include `sparse_values: { indices: [...], values: [...] }` in the Pinecone request body when sparse values are present
  - In `query()`, accept an optional `Map<Integer, Float> sparseVector` parameter. When present, include both `vector` (dense) and `sparse_vector` in the query body for hybrid search. When absent, fall back to dense-only (current behavior)
- Modify `SemanticIndexService.java`:
  - In `upsertChunk()`, generate sparse vectors via `SparseEncoder` and include them in the `PineconeVector`
- Modify `SemanticQueryService.java`:
  - In `query()`, generate a sparse vector for the question text and pass it to `pineconeClient.query()` alongside the dense vector
- Add test: `backend/src/test/java/com/weeklycommit/ai/rag/SparseEncoderTest.java` — test tokenization, domain term normalization, sparse vector generation
- Update `PineconeClientTest.java` — test that upsert/query include sparse vectors when present, and fall back gracefully when absent

**Checks:** lint, typecheck, unit, build

### Step 5: Add cross-encoder reranking service

Add a reranking layer between Pinecone retrieval and LLM context assembly.

**Scope:** Only files in `backend/src/main/java/com/weeklycommit/ai/rag/` and test counterparts. Do NOT modify frontend or non-RAG files.

**Deliverables:**
- Create `backend/src/main/java/com/weeklycommit/ai/rag/RerankService.java`:
  - Interface: `List<RankedChunk> rerank(String query, List<PineconeClient.PineconeMatch> candidates, int topN)`
  - `RankedChunk` record: `record RankedChunk(String id, double score, Map<String, Object> metadata, double rerankScore)`
  - Implementation uses the LLM (via `AiProviderRegistry`) as a reranker: send the query + each chunk's text to the LLM with a short relevance-scoring prompt, parse a 0.0–1.0 relevance score per chunk. This is a simulated cross-encoder using the existing LLM provider — no new external API dependency.
  - Alternatively, if Cohere Rerank API is available via `ai.rerank.api-key` config property, call that instead. But always fall back to LLM-based reranking if the Cohere key is absent.
  - Include `@Value("${ai.rerank.enabled:true}")` feature flag — when false, skip reranking entirely (pass-through)
  - Include `@Value("${ai.rerank.top-n:20}")` for configurable top-N after reranking
  - Graceful degradation: if reranking fails for any reason, return the original candidates unchanged with a log warning
- Modify `SemanticQueryService.java`:
  - After Pinecone retrieval (Step 5 in the query pipeline), call `rerankService.rerank(question, matches, topN)`
  - Increase `TOP_K` from 40 to 80 (retrieve more candidates for the reranker to filter)
  - Pass only the reranked top-N results to the RAG prompt context
- Add `backend/src/test/java/com/weeklycommit/ai/rag/RerankServiceTest.java`:
  - Test: reranking re-orders chunks by relevance score
  - Test: feature flag disabled → pass-through
  - Test: reranking failure → graceful fallback to original order
- Update `SemanticQueryServiceTest.java` to account for the reranking step

**Checks:** lint, typecheck, unit, build

### Step 6: Add SSE streaming endpoint for RAG queries — backend

Add a Server-Sent Events endpoint that streams RAG answers token by token using Spring WebFlux.

**Scope:** Only backend files. Do NOT modify any frontend files.

**Deliverables:**
- Create `backend/src/main/java/com/weeklycommit/ai/controller/AiStreamController.java`:
  - `@GetMapping(value = "/api/ai/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)`
  - Accept query params: `question`, `teamId`, `userId`
  - Return `Flux<ServerSentEvent<String>>` with event types:
    - `type: "delta"` — incremental answer text tokens
    - `type: "sources"` — JSON array of source citations (sent after answer completes)
    - `type: "confidence"` — confidence score (sent last)
    - `type: "done"` — empty data, signals stream end
    - `type: "error"` — error message if anything fails
  - For the initial implementation, use a "chunked flush" approach: call the existing `SemanticQueryService.query()` to get the full response, then split the answer text into word-level chunks and emit them as `delta` events with a small delay (simulated streaming). This preserves the existing pipeline while providing the streaming UX. True token-level streaming from the LLM can be added later.
  - Add response header `X-Accel-Buffering: no` for Nginx compatibility
  - Add response header `Cache-Control: no-cache`
- Update `application.yml`: no changes needed (Spring WebFlux SSE works with existing MVC setup via `Flux` return types)
- Add `backend/src/test/java/com/weeklycommit/ai/controller/AiStreamControllerTest.java`:
  - Test: SSE endpoint returns correct event types in order
  - Test: error handling returns error event, not exception
  - Test: missing params return 400

**Checks:** lint, typecheck, unit, build

### Step 7: Add SSE streaming support — frontend

Connect the frontend RAG components to the new streaming endpoint.

**Scope:** Only frontend files. Do NOT modify any backend files.

**Deliverables:**
- Create `frontend/src/api/ragStreamApi.ts`:
  - `streamRagQuery(question: string, teamId: string, userId: string, onDelta: (text: string) => void, onSources: (sources: RagSource[]) => void, onConfidence: (score: number) => void, onDone: () => void, onError: (error: string) => void): AbortController`
  - Uses `fetch()` with `ReadableStream` to parse SSE events (not `EventSource`, to support POST if needed later)
  - Returns an `AbortController` so the caller can cancel the stream
  - Handles reconnection gracefully — if the stream drops, don't auto-reconnect (user can retry)
- Create `frontend/src/api/ragStreamHooks.ts`:
  - `useStreamingRagQuery()` hook that wraps `streamRagQuery` with React state: `{ answer: string, sources: RagSource[], confidence: number, isStreaming: boolean, error: string | null, startStream: (question: string) => void, cancel: () => void }`
  - Appends delta text to answer state incrementally
  - Cleans up AbortController on unmount
- Modify `frontend/src/components/ai/QueryAnswerCard.tsx`:
  - Accept a `streaming` prop: when true, render the answer text as it arrives (character by character), show a typing indicator while `isStreaming`, and render sources + confidence after the `done` event
  - When `streaming` is false, keep current behavior (full answer appears at once)
- Modify `frontend/src/components/ai/SemanticSearchInput.tsx`:
  - Add a toggle or automatic detection: use the streaming hook instead of the batch hook when available
  - Pass `streaming={true}` to `QueryAnswerCard`
- Add `frontend/src/__tests__/RagStreamHooks.test.ts`:
  - Test: hook state transitions (idle → streaming → done)
  - Test: delta events accumulate into answer
  - Test: cancel aborts the stream
  - Test: error event sets error state

**Checks:** lint, typecheck, unit

### Step 8: Add statistical predictive calibration service — backend

Build a Bayesian completion-rate model from `user_week_fact` data for predictive planning intelligence.

**Scope:** Only backend files. Do NOT modify any frontend files.

**Deliverables:**
- Create `backend/src/main/java/com/weeklycommit/ai/service/CalibrationService.java`:
  - `CalibrationProfile getCalibration(UUID userId)` — returns rolling achievement stats
  - `CalibrationProfile` record contains:
    - `overallAchievementRate` (double) — planned vs achieved over last 12 weeks
    - `Map<ChessPiece, Double> chessPieceAchievementRates` — per-chess-piece calibration
    - `double carryForwardProbability` — probability of carry-forward based on historical rate
    - `int weeksOfData` — how many weeks of history are available (display as "Based on N weeks")
    - `Map<ChessPiece, Double> avgEstimateByPiece` — historical average estimate per chess piece
    - `confidenceTier` (enum: HIGH, MEDIUM, LOW, INSUFFICIENT) — based on `weeksOfData` (<8 = INSUFFICIENT, 8-15 = LOW, 15-30 = MEDIUM, 30+ = HIGH)
  - Queries `UserWeekFactRepository` for the user's last 12 weeks of `user_week_fact` rows
  - Computes rolling averages, per-chess-piece rates, carry-forward frequency
  - Returns `CalibrationProfile.insufficient()` when <8 weeks of data
- Create `backend/src/main/java/com/weeklycommit/ai/dto/CalibrationProfileResponse.java` — API response DTO
- Add endpoint to `AiController.java`: `GET /api/ai/calibration/{userId}` → returns `CalibrationProfileResponse`
- Add `backend/src/test/java/com/weeklycommit/ai/service/CalibrationServiceTest.java`:
  - Test: 12 weeks of data produces valid rates
  - Test: <8 weeks returns insufficient
  - Test: per-chess-piece rates computed correctly
  - Test: carry-forward probability matches historical frequency

**Checks:** lint, typecheck, unit, build

### Step 9: Add evidence-based confidence tiers and integrate calibration into prompts — backend

Replace the hard-coded 0.85 confidence with structured evidence-based tiers. Integrate calibration data into AI prompts.

**Scope:** Only backend files in `backend/src/main/java/com/weeklycommit/ai/`. Do NOT modify frontend files.

**Deliverables:**
- Create `backend/src/main/java/com/weeklycommit/ai/evidence/ConfidenceTierCalculator.java`:
  - `ConfidenceTier calculate(StructuredEvidence evidence, List<PineconeClient.PineconeMatch> matches)` 
  - Enum `ConfidenceTier { HIGH, MEDIUM, LOW, INSUFFICIENT }` with display labels
  - Logic:
    - HIGH: ≥3 high-score matches (>0.8) AND SQL facts present AND lineage data available
    - MEDIUM: ≥1 high-score match OR SQL facts present
    - LOW: only low-score matches (<0.5) or sparse evidence
    - INSUFFICIENT: no relevant matches, no SQL facts
  - Pure computation — no external calls
- Modify `OpenRouterAiProvider.java`:
  - In `parseResponse()`, attempt to read a `confidence` field from the LLM's JSON response. If present and between 0.0-1.0, use it. If absent or out of range, use 0.5 as default. This replaces the hard-coded 0.85.
  - Note: this LLM-reported confidence will be overridden by the evidence-based tier in services that have evidence context (RAG, risk, insights). For simple suggestion types (draft assist, lint), the LLM-reported value is a reasonable proxy.
- Modify `SemanticQueryService.java`:
  - After reranking, compute `ConfidenceTier` from the reranked matches
  - Include the tier in the `RagQueryResult` (add a `confidenceTier` field to the record)
- Modify `CommitDraftAssistService.java`:
  - Inject `CalibrationService` and include the user's `CalibrationProfile` in the prompt context (add to `additionalContext` map as `"calibration"`)
  - This lets the draft-assist prompt reference historical estimation patterns
- Modify `RiskDetectionService.java`:
  - Inject `CalibrationService` and include calibration data in the risk detection context
  - A user who historically achieves only 60% of QUEEN estimates should get higher risk signals for ambitious QUEEN commits
- Add `backend/src/test/java/com/weeklycommit/ai/evidence/ConfidenceTierCalculatorTest.java`:
  - Test all 4 tier conditions with mock evidence bundles

**Checks:** lint, typecheck, unit, build

### Step 10: Add targeted query rewriting for RAG — backend

Implement domain-specific query normalization before embedding, improving retrieval precision.

**Scope:** Only files in `backend/src/main/java/com/weeklycommit/ai/rag/`. Do NOT modify frontend or non-RAG files.

**Deliverables:**
- Create `backend/src/main/java/com/weeklycommit/ai/rag/QueryRewriter.java`:
  - `String rewrite(String originalQuery)` — pure string transformation, no LLM call
  - Domain-specific normalizations:
    - Expand acronyms: "CF" → "carry-forward", "RCDO" → "Rally Cry Defining Objective Outcome hierarchy"
    - Normalize chess terms: "king commit" → "KING chess piece commit", "queens" → "QUEEN chess piece"
    - Normalize time expressions: "last week" → leave as-is (intent classifier handles this), but normalize "prev week", "prior week" → "last week"
    - Expand shorthand: "pts" → "points", "est" → "estimate"
    - Strip filler words that hurt embedding quality: "can you tell me", "I want to know", "please show me"
  - `List<String> decompose(String query)` — for multi-hop questions
    - Detect compound questions (contains "and also", "as well as", "plus", or multiple question marks)
    - Split into sub-queries, each independently embeddable
    - Return single-element list for simple questions
- Modify `SemanticQueryService.java`:
  - Before embedding the question, call `queryRewriter.rewrite(question)`
  - If `decompose()` returns multiple sub-queries, embed each, query Pinecone for each, merge results (union + deduplicate by chunk ID, keep highest score), then rerank the merged set
- Add `backend/src/test/java/com/weeklycommit/ai/rag/QueryRewriterTest.java`:
  - Test acronym expansion
  - Test chess term normalization
  - Test filler word stripping
  - Test multi-hop decomposition
  - Test simple query passthrough (no unnecessary changes)

**Checks:** lint, typecheck, unit, build

### Step 11: Add calibration display and confidence tiers — frontend

Surface the predictive calibration data and evidence-based confidence tiers in the UI.

**Scope:** Only frontend files. Do NOT modify any backend files.

**Deliverables:**
- Create `frontend/src/api/calibrationApi.ts`:
  - `fetchCalibration(userId: string): Promise<CalibrationProfile>`
  - TypeScript types matching the backend DTO
- Create `frontend/src/api/calibrationHooks.ts`:
  - `useCalibration(userId: string)` — wraps the API call with loading/error state
- Create `frontend/src/components/ai/CalibrationCard.tsx`:
  - Displays the user's calibration profile: overall achievement rate, per-chess-piece rates as a small bar chart or progress bars, carry-forward probability, confidence tier badge
  - Shows "Based on N weeks of data" with appropriate messaging for INSUFFICIENT tier ("Not enough data yet — needs 8+ weeks")
  - Uses existing UI components (`Card`, `Badge`, `Skeleton` for loading)
- Create `frontend/src/components/ai/ConfidenceBadge.tsx`:
  - Renders a confidence tier as a colored badge: HIGH=green, MEDIUM=yellow, LOW=orange, INSUFFICIENT=gray
  - Tooltip explains what the tier means
- Modify `frontend/src/components/ai/QueryAnswerCard.tsx`:
  - Replace the numeric confidence display with `ConfidenceBadge` using the tier from the response
- Modify `frontend/src/routes/MyWeek.tsx`:
  - Mount `CalibrationCard` in the insights/AI section (near `InsightPanel`)
  - Only show when user has ≥8 weeks of data (INSUFFICIENT tier hides the card)
- Add `frontend/src/__tests__/CalibrationCard.test.tsx`:
  - Test: renders calibration data correctly
  - Test: insufficient tier shows appropriate message
  - Test: loading state shows skeleton
- Add `frontend/src/__tests__/ConfidenceBadge.test.tsx`:
  - Test: each tier renders correct color and label

**Checks:** lint, typecheck, unit

### Step 12: Add typed workflow orchestration — risk→what-if→recommendation pipeline (backend)

Wire `RiskDetectionService` and `WhatIfService` into a typed workflow that produces actionable recommendations when risks are detected.

**Scope:** Only backend files in `backend/src/main/java/com/weeklycommit/ai/`. Do NOT modify frontend files.

**Deliverables:**
- Create `backend/src/main/java/com/weeklycommit/ai/service/PlanRecommendationService.java`:
  - `List<PlanRecommendation> generateRecommendations(UUID planId)` — the main orchestration method
  - `PlanRecommendation` record: `record PlanRecommendation(String riskType, String description, String suggestedAction, WhatIfResponse whatIfResult, String narrative, ConfidenceTier confidence)`
  - Workflow:
    1. Call `RiskDetectionService.detectRiskSignals(planId)` to get current risk signals
    2. For each OVERCOMMIT or UNDERCOMMIT signal: identify the lowest-priority commit (PAWN first, then KNIGHT, etc.) and call `WhatIfService` to simulate removing/deferring it
    3. For each REPEATED_CARRY_FORWARD signal: call `CalibrationService` to get the user's carry-forward probability, then call `WhatIfService` to simulate splitting the commit into smaller pieces
    4. Assemble results into `PlanRecommendation` objects with the what-if narrative
    5. If no risks detected, return empty list (no forced recommendations)
  - Graceful degradation: if WhatIfService or CalibrationService fails, return the risk signal without the recommendation (partially degraded but still useful)
- Create `backend/src/main/java/com/weeklycommit/ai/dto/PlanRecommendationResponse.java` — API response DTO
- Add endpoint to `AiController.java`: `GET /api/ai/plans/{planId}/recommendations` → returns list of `PlanRecommendationResponse`
- Add `backend/src/test/java/com/weeklycommit/ai/service/PlanRecommendationServiceTest.java`:
  - Test: OVERCOMMIT risk produces recommendation with what-if result
  - Test: no risks → empty recommendations
  - Test: WhatIfService failure → returns risk without recommendation
  - Test: multiple risks produce multiple recommendations

**Checks:** lint, typecheck, unit, build

### Step 13: Add plan recommendations UI — frontend

Surface the typed workflow recommendations on the My Week page.

**Scope:** Only frontend files. Do NOT modify any backend files.

**Deliverables:**
- Create `frontend/src/api/recommendationApi.ts`:
  - `fetchPlanRecommendations(planId: string): Promise<PlanRecommendation[]>`
  - TypeScript types matching the backend DTO
- Create `frontend/src/api/recommendationHooks.ts`:
  - `usePlanRecommendations(planId: string)` hook
- Create `frontend/src/components/ai/PlanRecommendationCard.tsx`:
  - Renders a single recommendation: risk type badge, description, suggested action, what-if narrative with before/after numbers, confidence tier
  - "Apply suggestion" button that triggers the corresponding action (e.g., remove commit, split commit) — for now, just shows a confirmation dialog with the suggested change details; actual mutation is out of scope
  - `AiFeedbackButtons` attached for accept/dismiss tracking
  - Dismissible with `useDismissMemory` (dismissed recommendations don't reappear)
- Modify `frontend/src/routes/MyWeek.tsx`:
  - Mount `PlanRecommendationCard` list in the AI section, below `ProactiveRiskBanner`
  - Only fetch recommendations when plan is in DRAFT or LOCKED state
  - Show skeleton loader while loading, hide section when empty
- Add `frontend/src/__tests__/PlanRecommendationCard.test.tsx`:
  - Test: renders recommendation with all fields
  - Test: dismiss removes the card
  - Test: feedback buttons present

**Checks:** lint, typecheck, unit
