# Goal

Build a What-If Planner and Historical Replay Benchmark for the Weekly Commit Module. These are beyond-scope features that demonstrate the system can reason about hypothetical future plans and prove its predictions work against historical data.

**What-If Planner:** Given a draft plan + hypothetical commit mutations (add/remove/modify), simulate the impact on capacity, RCDO coverage, and risk — then use the LLM to narrate the implications. All computation uses the existing rules engine and feature tables; the LLM only narrates.

**Historical Replay Benchmark:** Run risk prediction on historical seed data (weeks 1-8), compare predictions to actual reconcile outcomes (weeks 9-12), compute precision/recall/F1, and publish results as a markdown doc.

**Success criteria:**
- `POST /api/ai/what-if` endpoint works with hypothetical mutations and returns structured impact analysis + LLM narrative
- Frontend What-If panel accessible from MyWeek page shows before/after capacity, RCDO coverage delta, risk changes, and AI narrative
- Historical replay benchmark runs via `./gradlew evalTest` and produces `docs/eval-results.md`
- All existing tests continue to pass (`npm test`, `npm run lint`, `npm run typecheck`)

## Constraints

- **Languages:** Java 21 (backend), TypeScript strict (frontend), SQL — no Python
- **No new infrastructure:** use existing PostgreSQL, Pinecone, OpenRouter — no Neo4j, no Apache AGE, no new databases
- **Reuse existing services:** `RiskDetectionService` rules logic, `UserWeekFact`/`CarryForwardFact`/`RcdoWeekRollup` read models, `AiProviderRegistry` for LLM calls
- **Do NOT modify existing entity classes, migrations, or domain enums** — add new DTOs/services only
- **Do NOT modify existing frontend routes or page components** — add new components and integrate them via composition
- **Follow existing patterns:** DTOs as Java records, services annotated `@Service`, controllers in module-specific packages, frontend components in `frontend/src/components/ai/`

## Steps

### Step 1: What-If backend DTOs and service

Create the What-If Planner backend: request/response DTOs and the core `WhatIfService` that simulates plan mutations using existing rules and read models. No LLM calls in this step — pure computation.

**Scope:** Backend only. Do NOT modify any files in `frontend/src/`. Do NOT modify existing services (`RiskDetectionService`, `WeeklyPlanService`, etc.) — read from them, don't change them.

**Deliverables:**
- `backend/src/main/java/com/weeklycommit/ai/dto/WhatIfRequest.java` — record with `planId (UUID)`, `userId (UUID)`, and `hypotheticalChanges` (list of `WhatIfMutation` records with `action` enum ADD_COMMIT/REMOVE_COMMIT/MODIFY_COMMIT, `commitId` (nullable UUID), `title`, `chessPiece` (String, not ChessPiece enum — avoids coupling to domain enums in DTO), `estimatePoints` (Integer), `rcdoNodeId` (nullable UUID)). The `WhatIfMutation` record should be a nested type inside `WhatIfRequest` or a sibling file `WhatIfMutation.java`. Use `@Valid` and `@NotNull` annotations on `planId`/`userId` consistent with existing DTO records.
- `backend/src/main/java/com/weeklycommit/ai/dto/WhatIfResponse.java` — record with `available (boolean)`, `currentState` (nested record `PlanSnapshot` with `totalPoints`, `capacityBudget`, `riskSignals` (List<String>), `rcdoCoverage` (Map<UUID, Integer> nodeId→points)), `projectedState` (same type), `capacityDelta` (int), `rcdoCoverageChanges` (List of nested record `RcdoCoverageChange` with `rcdoNodeId`, `rcdoTitle` (nullable), `beforePoints`, `afterPoints`), `riskDelta` (nested record with `newRisks` (List<String>), `resolvedRisks` (List<String>)), `narrative` (String, null in this step), `recommendation` (String, null in this step). Add a `public static WhatIfResponse unavailable()` factory method following the pattern of `CommitDraftAssistResponse.unavailable()`.
- `backend/src/main/java/com/weeklycommit/ai/service/WhatIfService.java` — `@Service` class. Inject `WeeklyPlanRepository`, `WeeklyCommitRepository`, `RcdoWeekRollupRepository`, `ScopeChangeEventRepository`, `WorkItemRepository`, `WorkItemStatusHistoryRepository`. Method `simulate(WhatIfRequest): WhatIfResponse` that: (1) loads the plan + commits from DB via repository methods, (2) builds `currentState` snapshot (total points, budget, per-RCDO points from commits, current risk signals computed by re-implementing the 5 rules logic inline — DO NOT call `RiskDetectionService.detectAndStoreRiskSignals` since it persists to DB), (3) clones the commit list in-memory and applies each mutation (ADD inserts a synthetic commit, REMOVE filters out, MODIFY updates fields), (4) builds `projectedState` from the mutated list using the same rules, (5) computes deltas. Returns `WhatIfResponse` with `narrative = null`. The risk computation should replicate the OVERCOMMIT/UNDERCOMMIT/REPEATED_CARRY_FORWARD rules (the BLOCKED_CRITICAL and SCOPE_VOLATILITY rules are less relevant for hypothetical mutations but include them for completeness using existing data). Use the same threshold constants from `RiskDetectionService` (reference them or duplicate them as package-private constants).
- `backend/src/test/java/com/weeklycommit/ai/service/WhatIfServiceTest.java` — `@ExtendWith(MockitoExtension.class)` unit test matching the pattern in `RiskDetectionServiceTest.java`. Mock all repository interfaces. Test cases: (1) ADD_COMMIT that pushes total over budget → projectedState should have OVERCOMMIT in riskSignals, currentState should not, (2) REMOVE_COMMIT that brings total under budget → projectedState resolves OVERCOMMIT, riskDelta shows it resolved, (3) MODIFY_COMMIT changes rcdoNodeId → rcdoCoverageChanges shows before/after point shift, (4) empty mutations list → currentState equals projectedState, (5) plan not found → ResourceNotFoundException thrown.

**Checks:** lint, typecheck, unit, build

### Step 2: What-If LLM narration and API endpoint

Wire the `WhatIfService` to the LLM for narrative generation and expose the REST endpoint. The LLM receives the structured impact analysis and produces a 2-3 sentence explanation — it does NOT compute anything.

**Scope:** Backend only. Do NOT modify any files in `frontend/src/`.

**Depends on:** Step 1

**Deliverables:**
- `backend/src/main/resources/prompts/what-if.txt` — prompt template. Receives the `WhatIfResponse` data (current state, projected state, deltas) as structured JSON in `additionalContext`. Outputs a JSON object `{"narrative": "2-3 sentence explanation of the impact", "recommendation": "optional short recommendation"}`. Include 2 few-shot examples. Add `## Input Data Structure` section documenting all fields. Follow the style of existing prompts in `backend/src/main/resources/prompts/` (examine `risk-signal.txt` and `reconcile-assist.txt` for reference).
- Update `backend/src/main/java/com/weeklycommit/ai/provider/AiContext.java` — add `public static final String TYPE_WHAT_IF = "WHAT_IF";` constant following the existing pattern.
- Update `backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java` — add `case AiContext.TYPE_WHAT_IF -> "what-if.txt";` to the `loadPromptTemplate()` switch and `case AiContext.TYPE_WHAT_IF -> "what-if";` to the `resolvePromptVersion()` switch. The `resolvePromptVersion` should return `"what-if-v1"` (the base + `-v1` suffix, following the default pattern). Do NOT change any other logic in this file.
- Update `backend/src/main/java/com/weeklycommit/ai/provider/StubAiProvider.java` — add a `case AiContext.TYPE_WHAT_IF` block in `generateSuggestion()` that returns a canned JSON response `{"narrative": "This change would ...", "recommendation": "Consider ..."}`. Follow the pattern of existing stub cases.
- Update `backend/src/main/java/com/weeklycommit/ai/service/WhatIfService.java` — after computing the impact, build an `AiContext` with `suggestionType = AiContext.TYPE_WHAT_IF`, serialize the computed delta data into `additionalContext`, call `AiProviderRegistry.generateSuggestion()`, parse the `narrative` and `recommendation` fields from the JSON response, and populate them in the `WhatIfResponse`. If AI is unavailable (`!result.available()`), return the response with `narrative = null` and `recommendation = null` (graceful degradation). The `AiProviderRegistry` should be injected via constructor.
- Add endpoint in `backend/src/main/java/com/weeklycommit/ai/controller/AiController.java`: `@PostMapping("/api/ai/what-if")` method that accepts `@Valid @RequestBody WhatIfRequest`, calls `WhatIfService.simulate()`, and returns `ResponseEntity<WhatIfResponse>`. Add `WhatIfService` to the constructor parameter list and field. Add the necessary imports. Follow the pattern of the existing `commitDraftAssist` endpoint (one-liner delegating to the service).

**Checks:** lint, typecheck, unit, build

### Step 3: What-If frontend component

Build the frontend What-If panel and integrate it into the MyWeek page.

**Scope:** Frontend only. Do NOT modify any files in `backend/`. Do NOT modify existing page components except MyWeek.tsx where the integration line is added.

**Depends on:** Step 2

**Deliverables:**
- `frontend/src/api/whatIfApi.ts` — follow the `createXxxApi(client, actorUserId)` factory pattern used by all other API modules (see `aiApi.ts`, `ragApi.ts`). Export `createWhatIfApi(client: ApiClient, actorUserId: string)` factory function and the corresponding `WhatIfApi` type. Define TypeScript interfaces: `WhatIfMutationAction` (union type `"ADD_COMMIT" | "REMOVE_COMMIT" | "MODIFY_COMMIT"`), `WhatIfMutation` (action, commitId?, title?, chessPiece?, estimatePoints?, rcdoNodeId?), `WhatIfRequest` (planId, userId, hypotheticalChanges), `PlanSnapshot` (totalPoints, capacityBudget, riskSignals, rcdoCoverage), `RcdoCoverageChange` (rcdoNodeId, rcdoTitle?, beforePoints, afterPoints), `RiskDelta` (newRisks, resolvedRisks), `WhatIfResponse` (available, currentState, projectedState, capacityDelta, rcdoCoverageChanges, riskDelta, narrative?, recommendation?). The factory returns an object with `simulate: (req: WhatIfRequest) => Promise<WhatIfResponse>` that calls `client.post("/ai/what-if", req)`.
- `frontend/src/api/aiHooks.ts` — add a `useWhatIfApi()` hook that returns a stable `WhatIfApi` instance, following the exact pattern of `useAiApi()`. Import `createWhatIfApi` from `whatIfApi.ts`.
- `frontend/src/components/ai/WhatIfPanel.tsx` — the main component. Props: `planId: string`, `currentCommits: ReadonlyArray<CommitResponse>` (using the existing `CommitResponse` type from `planTypes.ts`). UI: a collapsible card (collapsed by default, expand button reads "🔮 What-If Planner"). When expanded shows: (a) mutation form — select action type (add/remove/modify), for ADD: title input + chess piece dropdown + points input, for REMOVE: dropdown of current commits to remove, for MODIFY: dropdown of current commit + field to modify + new value; (b) mutations list showing queued mutations with remove button; (c) "Simulate" button calling the API; (d) results section: capacity before→after with visual indicator (green if under budget, red if over), RCDO coverage changes as list items with +/- indicators, risk delta (new risks in danger color, resolved risks in success color), narrative in a highlighted box with sparkles icon. Include loading spinner on simulate, error message on failure, and graceful handling of `available: false`. Use `useAiStatus()` hook to hide the panel when AI is unavailable. Follow patterns from `CommitDraftAssistButton.tsx` and `ReconcileAssistPanel.tsx` for styling, state management, `cn()` utility usage, and lucide-react icons. Use `Card`/`CardHeader`/`CardTitle`/`CardContent` from `../ui/Card.js`, `Button` from `../ui/Button.js`, `Badge` from `../ui/Badge.js`.
- Integrate into `frontend/src/routes/MyWeek.tsx` — add import for `WhatIfPanel` and `AiErrorBoundary` (already defined in the file). Insert below the commit list (after the `CommitList` component render and before the "Scope change timeline" section). The integration is: `{aiAssistanceEnabled && plan && (isDraft || isLocked) && (<AiErrorBoundary><WhatIfPanel planId={plan.id} currentCommits={commits} /></AiErrorBoundary>)}`. This is a minimal insertion — no restructuring of existing layout. Note: this technically modifies MyWeek.tsx but the plan constraints say "add new components and integrate them via composition" — the 3-line insertion is the composition point.

**Checks:** lint, typecheck, unit

### Step 4: What-If eval fixtures

Add golden evaluation test cases for the What-If LLM narration capability and extend the eval runner.

**Scope:** Backend test resources and eval runner only. Do NOT modify any production source files.

**Depends on:** Step 2

**Deliverables:**
- `backend/src/test/resources/eval/what-if/cases.json` — 8 test cases. Each case has `id`, `description`, `edgeCaseType`, `input`, `expectedBehavior`, `critical` fields matching the `EvalCase` record schema. The `input` should contain the structured what-if impact data as the LLM would receive it (current state, projected state, deltas — matching what `WhatIfService` puts into `additionalContext`). The `expectedBehavior` should have: `shouldHaveNarrative` (boolean), `narrativeShouldMention` (string array of keywords), `maxNarrativeLength` (int), `shouldHaveRecommendation` (boolean, optional). Test cases: (1) add-overcommit: pushes over budget, (2) remove-resolves-risk: removing commit resolves OVERCOMMIT, (3) rcdo-shift: changes RCDO coverage, narrative should mention the affected branch, (4) no-impact: empty mutations, narrative should indicate minimal/no impact, (5) mixed-mutations: add + remove together, (6) budget-consumed: single large mutation consuming entire budget, (7) chess-piece-change: modify from PAWN to KING, narrative should mention priority/significance change, (8) undercommit-resolution: adding commit resolves UNDERCOMMIT risk.
- Extend `backend/src/test/java/com/weeklycommit/ai/eval/PromptEvalRunner.java` — add: (1) `whatIfCases()` method returning `Stream<EvalCase>` by calling `loadCases("eval/what-if/cases.json")`, (2) `@ParameterizedTest(name = "what-if: {0}") @MethodSource("whatIfCases") void evaluateWhatIf(EvalCase evalCase)` test method — follows the pattern of `evaluateRagQuery`: check provider available, build `AiContext` with `TYPE_WHAT_IF` putting input into `additionalContext`, call provider, validate JSON has `"narrative"` field, create `EvalResult`, call `evaluateWhatIfOutput()`, add to `allResults`, assert schema valid, (3) `private void evaluateWhatIfOutput(JsonNode output, JsonNode expected, EvalResult result)` helper — checks `shouldHaveNarrative` (narrative field present and non-empty), `narrativeShouldMention` (keyword matching in lowercase narrative, same pattern as `evaluateRagQueryOutput`'s `answerShouldMention`), `maxNarrativeLength` (narrative string length check), `shouldHaveRecommendation` (optional).

**Checks:** lint, typecheck, unit, build

### Step 5: Historical Replay Benchmark

Build a test-harness that replays risk predictions against historical seed data and computes accuracy metrics.

**Scope:** Backend test files and docs only. Do NOT modify any production source files or frontend.

**Depends on:** None (independent of steps 1-4)

**Deliverables:**
- `backend/src/test/java/com/weeklycommit/ai/eval/HistoricalReplayBenchmark.java` — `@Tag("eval")` test class. Since the V11 seed data uses PostgreSQL-specific SQL (ON CONFLICT, interval arithmetic) and tests run against H2 with Flyway disabled and `ddl-auto: create-drop`, the seed data will NOT be automatically available in the test DB. The benchmark must therefore use an embedded approach: either (a) provide a simplified H2-compatible SQL fixture file loaded via `@Sql` annotation, or (b) programmatically insert test data using JPA EntityManager/repository beans, or (c) use `@SpringBootTest` with a Testcontainers PostgreSQL instance. Recommended approach: use JPA entities directly — create a `@BeforeAll` setup method that programmatically constructs and persists the necessary `WeeklyPlan`, `WeeklyCommit`, and `UserWeekFact` entities for 10-12 weeks of historical data using the deterministic UUID scheme from V11. This avoids SQL compatibility issues. For each historical RECONCILED plan: (1) reconstruct the state at lock time (planned points, commit chess pieces, carry-forward streaks), (2) run the 5 rules-based risk signal detection logic in-memory against that state (replicate the rule thresholds from `RiskDetectionService`), (3) compare predictions to actual reconcile outcomes (did OVERCOMMIT predict a low completion ratio? did REPEATED_CARRY_FORWARD predict another carry-forward?), (4) aggregate into per-signal-type confusion matrix → precision/recall/F1. Output results to `build/eval-results/replay-benchmark.json` using Jackson `ObjectMapper`. If the test cannot set up properly, skip gracefully with `Assumptions.assumeTrue()`.
- `docs/eval-results.md` — markdown document summarizing the evaluation approach and placeholder results table. Structure: overview of eval approach (rules-based + LLM-augmented evaluation), per-capability eval summary table (commit-draft-assist: 12 cases, commit-lint: 6 cases, rcdo-suggest: 10 cases, risk-signal: 8 cases, reconcile-assist: 8 cases, rag-query: 8 cases, what-if: 8 cases), historical replay benchmark section with metrics table (signal type / # predicted / # actual / true positives / precision / recall / F1), and a "How to run" section explaining both `./gradlew test` (unit tests) and `./gradlew evalTest` (eval tests requiring OPENROUTER_API_KEY).

**Checks:** lint, typecheck, unit, build
