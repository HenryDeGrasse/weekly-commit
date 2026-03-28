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
- `backend/src/main/java/com/weeklycommit/ai/dto/WhatIfRequest.java` — record with `planId (UUID)`, `userId (UUID)`, and `hypotheticalChanges` (list of `WhatIfMutation` records with `action` enum ADD_COMMIT/REMOVE_COMMIT/MODIFY_COMMIT, `commitId` (nullable UUID), `title`, `chessPiece`, `estimatePoints`, `rcdoNodeId`)
- `backend/src/main/java/com/weeklycommit/ai/dto/WhatIfResponse.java` — record with `available (boolean)`, `currentState` (capacity/points/risk summary), `projectedState` (same after mutations), `capacityDelta`, `rcdoCoverageChanges` (list of RCDO nodes gaining/losing points), `riskDelta` (new risks introduced / risks resolved), `narrative` (String, null in this step — populated in step 2)
- `backend/src/main/java/com/weeklycommit/ai/service/WhatIfService.java` — loads the plan + commits from DB, applies mutations in-memory (does NOT persist), computes: total points delta, capacity budget comparison, per-RCDO point changes using `RcdoWeekRollup` data, new risk signals by running the 5 rules from `RiskDetectionService` logic against the mutated commit list. Returns a `WhatIfResponse` with `narrative = null`.
- `backend/src/test/java/com/weeklycommit/ai/service/WhatIfServiceTest.java` — unit test: mock repos, test ADD_COMMIT pushes over budget, test REMOVE_COMMIT reduces risk, test MODIFY_COMMIT changes RCDO coverage

**Checks:** lint, typecheck, unit, build

### Step 2: What-If LLM narration and API endpoint

Wire the `WhatIfService` to the LLM for narrative generation and expose the REST endpoint. The LLM receives the structured impact analysis and produces a 2-3 sentence explanation — it does NOT compute anything.

**Scope:** Backend only. Do NOT modify any files in `frontend/src/`.

**Depends on:** Step 1

**Deliverables:**
- `backend/src/main/resources/prompts/what-if.txt` — prompt template. Receives the `WhatIfResponse` data (current state, projected state, deltas) as structured JSON. Outputs a JSON object `{"narrative": "2-3 sentence explanation of the impact", "recommendation": "optional short recommendation"}`. Include 2 few-shot examples. Add `## Input Data Structure` section documenting all fields. Follow the style of existing prompts in `backend/src/main/resources/prompts/`.
- Update `backend/src/main/java/com/weeklycommit/ai/service/WhatIfService.java` — after computing the impact, call `AiProviderRegistry.generateSuggestion()` with a new `AiContext` type `WHAT_IF` to get the narrative. If AI is unavailable, return the response with `narrative = null` (graceful degradation).
- Update `backend/src/main/java/com/weeklycommit/ai/provider/AiContext.java` — add `public static final String TYPE_WHAT_IF = "WHAT_IF";`
- Update `backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java` — add `case AiContext.TYPE_WHAT_IF -> "what-if.txt";` to `loadPromptTemplate()` and `case AiContext.TYPE_WHAT_IF -> "what-if";` to `resolvePromptVersion()`. Do NOT change any other logic in this file.
- Add endpoint in `backend/src/main/java/com/weeklycommit/ai/controller/AiController.java`: `@PostMapping("/api/ai/what-if")` that calls `WhatIfService` and returns `WhatIfResponse`. Add the import for `WhatIfRequest`/`WhatIfResponse`. Add `WhatIfService` to the constructor parameters. Follow the pattern of the existing `commitDraftAssist` endpoint.

**Checks:** lint, typecheck, unit, build

### Step 3: What-If frontend component

Build the frontend What-If panel and integrate it into the MyWeek page.

**Scope:** Frontend only. Do NOT modify any files in `backend/`. Do NOT modify existing page components — only add new components and import them.

**Depends on:** Step 2

**Deliverables:**
- `frontend/src/api/whatIfApi.ts` — API client function `whatIfSimulate(request: WhatIfRequest): Promise<WhatIfResponse>`. Define the TypeScript types for `WhatIfRequest`, `WhatIfMutation`, `WhatIfResponse`, `RcdoCoverageChange`, `RiskDelta` matching the backend DTOs. Use the existing `client.ts` `apiFetch` helper.
- `frontend/src/components/ai/WhatIfPanel.tsx` — the main component. Props: `planId: string`, `currentCommits` (array of current commit summaries for pre-populating the mutation form). UI: a card with a form to add hypothetical mutations (add commit with title/chess/points, remove existing commit, modify estimate), a "Simulate" button that calls the API, and a results section showing: capacity before→after bar, RCDO coverage changes as a diff list, risk delta (new risks in red, resolved in green), and the LLM narrative in a highlighted box. Include loading/error states. Follow the patterns in `CommitDraftAssistButton.tsx` and `ReconcileAssistPanel.tsx` for styling, state management, and AI availability checks.
- Integrate into `frontend/src/routes/MyWeek.tsx` — import `WhatIfPanel` and render it below the commit list when the plan is in DRAFT or LOCKED state. Pass `planId` and current commits as props. The integration should be a single `{plan && <WhatIfPanel planId={plan.id} currentCommits={commits} />}` insertion — do NOT restructure the existing MyWeek page layout.

**Checks:** lint, typecheck, unit

### Step 4: What-If eval fixtures

Add golden evaluation test cases for the What-If LLM narration capability and extend the eval runner.

**Scope:** Backend test resources only. Do NOT modify any production source files.

**Depends on:** Step 2

**Deliverables:**
- `backend/src/test/resources/eval/what-if/cases.json` — 8 test cases covering: add commit pushes over budget (narrative should mention overcommit), remove commit resolves risk (narrative should mention improvement), RCDO coverage shift (narrative should mention affected branches), no-impact mutation (narrative should say minimal impact), mixed mutations (add + remove), large single mutation consuming entire budget, modify chess piece from PAWN to KING, empty mutations list. Each case has `input` (structured what-if data as the LLM would receive) and `expectedBehavior` (shouldHaveNarrative: boolean, narrativeShouldMention: string[], maxNarrativeLength: number).
- Extend `backend/src/test/java/com/weeklycommit/ai/eval/PromptEvalRunner.java` — add `whatIfCases()` method source, `evaluateWhatIf()` parameterized test (pattern: load cases, build AiContext with TYPE_WHAT_IF, call provider, validate JSON has "narrative" field, run keyword checks from expectedBehavior). Add `evaluateWhatIfOutput()` helper. Follow the exact patterns established by `evaluateRcdoSuggest()` and `evaluateRagQuery()` already in the file.

**Checks:** lint, typecheck, unit, build

### Step 5: Historical Replay Benchmark

Build a test-harness that replays risk predictions against historical seed data and computes accuracy metrics.

**Scope:** Backend test files and docs only. Do NOT modify any production source files or frontend.

**Depends on:** None (independent of steps 1-4)

**Deliverables:**
- `backend/src/test/java/com/weeklycommit/ai/eval/HistoricalReplayBenchmark.java` — `@Tag("eval")` test class. Loads the V11 rich demo seed data by querying the actual `weekly_plan`, `weekly_commit`, `user_week_fact`, and `carry_forward_fact` tables (this requires an H2 or test DB with seed data loaded). For each historical RECONCILED plan: (1) reconstruct the state at lock time (planned points, commit chess pieces, carry-forward streaks, scope changes), (2) run the 5 rules-based risk signal detection logic in-memory against that state, (3) compare predictions to actual reconcile outcomes (did OVERCOMMIT predict a low completion ratio? did REPEATED_CARRY_FORWARD predict another carry-forward?), (4) aggregate into per-signal-type precision/recall/F1. Output results to `build/eval-results/replay-benchmark.json`. The class should be self-contained — use raw SQL or EntityManager queries, not service classes, since service classes have side effects. If no DB is available, skip gracefully.
- `docs/eval-results.md` — markdown document summarizing the evaluation approach and placeholder results table. Structure: overview of eval approach, per-capability eval summary (commit-draft-assist: 12 cases, commit-lint: 6 cases, rcdo-suggest: 10 cases, risk-signal: 8 cases, reconcile-assist: 8 cases, rag-query: 8 cases, what-if: 8 cases), historical replay benchmark section with metrics table (signal type / # predicted / # actual / precision / recall / F1), and a "How to run" section pointing to `./gradlew evalTest`.

**Checks:** lint, typecheck, unit, build
