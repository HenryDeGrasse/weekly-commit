# AI Evaluation & Quality Roadmap

**Created:** 2026-03-26
**Updated:** 2026-03-28
**Context:** Weekly Commit Module — RAG pipeline uses Pinecone (vector store) + OpenRouter/Claude (LLM) + OpenAI text-embedding-3-small (embeddings). 10 prompt templates, 7 AI service types, 6 frontend AI components. Evaluation gaps identified via code audit.

> **Status key:** ✅ Implemented · 🔨 In progress · 📋 Planned

---

## Phase 1: Eval Foundation ✅

**Goal:** Build the infrastructure to scientifically validate AI output quality, enable A/B testing of prompts, and measure the metrics that the Manus research identifies as critical (Faithfulness ≥ 0.90, Context Recall ≥ 0.85).

### 1a. Golden Test Dataset ✅ (partial — expanding)

**What:** 15-20 realistic test cases per AI capability, stored as JSON fixtures under `backend/src/test/resources/eval/`. Not synthetic — hand-crafted from realistic scenarios matching our seed data patterns.

**Status:** Implemented for `commit-draft-assist` (12 cases) and `commit-lint` (6 cases). Remaining capabilities (`rcdo-suggest`, `risk-signal`, `reconcile-assist`, `rag-query`) are in progress.

**Why:** Every AI test currently mocks the LLM response. We test "does the code handle JSON correctly" but never "does the prompt produce good suggestions." A golden dataset is the foundation for all eval work.

**Capabilities to cover:**
- `commit-draft-assist` — vague titles, missing success criteria, KING/QUEEN without criteria, estimate calibration
- `commit-lint` — plans with real quality issues vs clean plans, false positive detection
- `rcdo-suggest` — commits with obvious RCDO matches, ambiguous matches, no good match
- `risk-signal` — overcommit/undercommit edge cases, carry-forward patterns, blocked criticals
- `reconcile-assist` — plans with mixed outcomes, linked ticket evidence, carry-forward decisions
- `rag-query` — temporal questions, team-scoped vs personal, entity-specific queries

**Each case contains:**
```json
{
  "id": "draft-001",
  "description": "Vague KING commit, no success criteria",
  "input": { /* realistic AiContext fields */ },
  "expectedBehavior": {
    "shouldSuggestTitle": true,
    "titleShouldNotContain": ["stuff", "things", "work on"],
    "shouldSuggestSuccessCriteria": true,
    "estimateRange": [3, 8]
  },
  "edgeCaseType": "vague_input"
}
```

**File structure:**
```
backend/src/test/resources/eval/
├── commit-draft-assist/
│   └── cases.json
├── commit-lint/
│   └── cases.json
├── rcdo-suggest/
│   └── cases.json
├── risk-signal/
│   └── cases.json
├── reconcile-assist/
│   └── cases.json
└── rag-query/
    └── cases.json
```

### 1b. Eval Runner ✅

**What:** A JUnit test class that calls the real LLM (not mocked) with actual prompts, scores output against the golden dataset using automated + LLM-as-judge scoring, and writes results to a JSON report.

**Status:** Implemented as `PromptEvalRunner.java` with parameterized tests for `commit-draft-assist` and `commit-lint`. Tagged `@Tag("eval")`, excluded from normal test runs, runs via `./gradlew evalTest`.

**Scoring dimensions per capability:**

| Capability | Automated Judges | LLM-as-Judge |
|---|---|---|
| commit-draft-assist | schema_valid, estimate_in_range, null_handling | title_clarity, criteria_measurability |
| commit-lint | schema_valid, hard_vs_soft_classification | false_positive_rate, guidance_usefulness |
| rcdo-suggest | schema_valid, confidence_calibration | rationale_quality |
| risk-signal | schema_valid, signal_type_valid | risk_specificity, action_usefulness |
| reconcile-assist | schema_valid, outcome_enum_valid | summary_accuracy, carry_forward_reasoning |
| rag-query | schema_valid, source_count, confidence_range | faithfulness, answer_relevancy |

**Key design decisions:**
- Runs against real OpenRouter API — requires `OPENROUTER_API_KEY` env var, skips gracefully if absent
- Tagged with `@Tag("eval")` so normal `./gradlew test` doesn't trigger it
- Outputs results to `build/eval-results/` as JSON for analysis
- Each case runs 3 times to measure output variance (temperature stability)

**Judge prompts** stored in `backend/src/test/resources/eval/judge-prompts/`:
- `title-quality-judge.txt` — scores clarity, specificity, appropriate scope, improvement over original
- `criteria-quality-judge.txt` — scores measurability, completeness, achievability within one week
- `faithfulness-judge.txt` — scores whether every claim is attributable to retrieved context (per Manus §2.1)
- `relevancy-judge.txt` — scores whether output directly addresses the input query

### 1c. Prompt Version Tracking ✅

**What:** Add `prompt_version TEXT` column to `ai_suggestion` table. Populate it in `OpenRouterAiProvider` from the prompt template filename + a version suffix. Enables A/B analysis using existing `ai_feedback` data.

**Status:** Implemented. `V12__add_prompt_version_and_eval_scores.sql` adds the column + indexes. `OpenRouterAiProvider.resolvePromptVersion()` generates version identifiers (e.g., `commit-draft-assist-v1`). `AiSuggestionService.storeSuggestion()` persists the version.

**A/B analysis query (once data accumulates):**
```sql
SELECT prompt_version, suggestion_type,
       COUNT(*) as total,
       COUNT(CASE WHEN f.accepted THEN 1 END) as accepted,
       ROUND(COUNT(CASE WHEN f.accepted THEN 1 END)::decimal / NULLIF(COUNT(f.id), 0), 3) as acceptance_rate
FROM ai_suggestion s
LEFT JOIN ai_feedback f ON f.suggestion_id = s.id
WHERE prompt_version IS NOT NULL
GROUP BY prompt_version, suggestion_type;
```

### 1d. Faithfulness Scoring ✅

**What:** Implement the RAGAS faithfulness metric as an LLM-as-judge call specifically for RAG query answers and risk signals. Decompose the answer into atomic claims, verify each against retrieved context, score = supported/total.

**Status:** Implemented as `FaithfulnessEvaluator.java`. Runs asynchronously via `maybeScopeAsync()` after suggestion storage. High-stakes types (RISK_SIGNAL, TEAM_INSIGHT, PERSONAL_INSIGHT, RAG_QUERY) are always scored; lower-stakes types sampled at 10%. Scores written back to `ai_suggestion.eval_faithfulness_score` and `eval_relevancy_score`. Judge prompt at `prompts/faithfulness-eval.txt`.

---

## Phase 2: Prompt Quality ✅

**Goal:** Improve actual LLM output quality through prompt engineering. This is the highest-ROI code change — better prompts produce better outputs immediately, before any eval infrastructure is needed.

### 2a. Add Few-Shot Examples to All Prompts ✅

**What:** Add 1-2 good examples and 1 bad example to each of the 10 prompt templates in `backend/src/main/resources/prompts/`.

**Status:** Implemented. All 10 prompt templates now contain few-shot examples.

**Example improvement for `commit-draft-assist.txt`:**
```
## Example 1 (Good improvement):
Input: {"title": "Do API stuff", "chessPiece": "KING", "estimatePoints": null}
Output: {"suggestedTitle": "Deploy payment API v2 to staging with load test validation", "suggestedEstimatePoints": 5, "suggestedSuccessCriteria": "Payment API v2 deployed to staging, handles 100 req/s with <200ms p95 latency, zero error rate on smoke test suite", "suggestedDescription": null}

## Example 2 (Already good — return nulls):
Input: {"title": "Migrate user auth from JWT to session tokens", "chessPiece": "ROOK", "estimatePoints": 3}
Output: {"suggestedTitle": null, "suggestedDescription": null, "suggestedSuccessCriteria": null, "suggestedEstimatePoints": null}
```

**Prompts to update (all 10):**
- `commit-draft-assist.txt`
- `commit-lint.txt`
- `rcdo-suggest.txt`
- `risk-signal.txt`
- `reconcile-assist.txt`
- `team-summary.txt`
- `team-insight.txt`
- `personal-insight.txt`
- `rag-intent.txt`
- `rag-query.txt`

### 2b. Add Field-Level Context Descriptions ✅

**What:** The system prompts don't explain the data structure the model receives. The user message is a raw JSON blob from `buildUserMessage()` in `OpenRouterAiProvider`.

**Status:** Implemented. All prompts now document their input data structure.

```json
{"suggestionType":"COMMIT_DRAFT_ASSIST","commitData":{"title":"Do stuff"},"planData":{},"historicalCommits":[],"rcdoTree":[],"additionalContext":{}}
```

The model has no instructions about what `historicalCommits` contains, what `rcdoTree` looks like, or how to use `additionalContext`. Each prompt should document the fields it receives.

**Add to each prompt:**
```
## Input Data Structure:
- commitData: {title, description, chessPiece, estimatePoints, successCriteria} — the current commit being drafted
- planData: {state, capacityBudgetPoints, weekStartDate, totalPlannedPoints} — the weekly plan this commit belongs to
- historicalCommits: [{title, chessPiece, estimatePoints, outcome}] — this user's recent past commits for pattern matching
- rcdoTree: [{id, title, type, parentId, status}] — the active RCDO hierarchy nodes
```

### 2c. Deduplicate Risk Signal Prompt vs Rules Engine ✅

**What:** `risk-signal.txt` asks the LLM for risks but doesn't tell it what rules already exist. The `RiskDetectionService` already computes:

**Status:** Implemented. The prompt explicitly lists the 5 rules-based signals and instructs the LLM to find additional risks they miss.
- OVERCOMMIT: total points > capacity budget
- UNDERCOMMIT: total points < 60% of budget
- REPEATED_CARRY_FORWARD: any commit with carryForwardStreak ≥ 2
- BLOCKED_CRITICAL: KING/QUEEN linked to ticket in BLOCKED status for >48 hours
- SCOPE_VOLATILITY: >3 scope change events on the plan

The LLM should be told "these rules already run — look for risks they miss."

**Update `risk-signal.txt` to include:**
```
The following rules-based signals are already computed automatically and should NOT be duplicated:
- OVERCOMMIT: total planned points exceed capacity budget
- UNDERCOMMIT: total planned points below 60% of capacity budget
- REPEATED_CARRY_FORWARD: any commit with carry-forward streak ≥ 2 consecutive weeks
- BLOCKED_CRITICAL: KING or QUEEN commit linked to a ticket in BLOCKED status for >48 hours
- SCOPE_VOLATILITY: more than 3 post-lock scope change events on the plan

Your job is to find ADDITIONAL risks that these rules miss. Focus on:
...
```

### 2d. Enrich ChunkBuilder with RCDO Context ✅

**What:** Commit chunks in Pinecone are thin — a commit with no description embeds as:

**Status:** Implemented. `ChunkBuilder` now uses `EnrichmentContext` with RCDO path, team name, owner display name, carry-forward lineage, linked ticket summary, and cross-team RCDO overlap. `SemanticIndexService.indexCommitDirect()` resolves all enrichment context from DB lookups before building chunks.
```
"Deploy new API" — QUEEN, 5pts. Description: . Success criteria: . Outcome: . Notes:
```

This is a sparse embedding that hurts retrieval quality. Enrich with:
- RCDO node title (the linked Outcome/DO name)
- Team name
- Plan state at index time

**Update `ChunkBuilder.buildCommitChunk()`:**
```java
// Current:
String text = "\"" + commit.getTitle() + "\" — " + chess + ", " + pts + "pts. " + ...

// Enriched:
String text = "\"" + commit.getTitle() + "\" — " + chess + ", " + pts + "pts. "
    + "RCDO: " + rcdoNodeTitle + ". "
    + "Team: " + teamName + ". "
    + "Description: " + orEmpty(commit.getDescription()) + ". " + ...
```

This requires passing RCDO node and team data into the chunk builder, which means updating the `indexCommitDirect` method to look up the RCDO node title.

**Impact:** Directly improves Context Precision (Manus §7.2) — embeddings become more semantically rich, so retrieval returns more relevant chunks for RCDO-specific or team-specific queries.

---

## Phase 3: Wire Missing Frontend AI Features ✅

**Goal:** Three AI backend capabilities existed without frontend components. All three are now wired up.

### 3a. Commit Draft Assist Button in CommitForm ✅

**Status:** Implemented as `frontend/src/components/ai/CommitDraftAssistButton.tsx`. Shows "✨ AI Suggest" button in commit form, renders inline diff-style suggestions with Accept/Dismiss per field, uses `AiFeedbackButtons` for thumbs up/down, degrades when AI unavailable.

### 3b. Reconcile Assist in ReconcilePage ✅

**Status:** Implemented as `frontend/src/components/ai/ReconcileAssistPanel.tsx`. Shows "AI Assist Reconciliation" button on Reconcile page, suggests likely outcomes, draft week summary, and carry-forward recommendations. All suggestions are accept/dismiss-able.

### 3c. Suggested Questions in SemanticSearchInput ✅

**Status:** Implemented in `frontend/src/components/ai/SemanticSearchInput.tsx`. Shows clickable example question pills when the search input is empty. Clicking fills and auto-submits.

Previously planned implementation:
```tsx
const SUGGESTED_QUESTIONS = [
  "What did the team commit to last week?",
  "Which RCDOs received the most effort this month?",
  "What are the recurring carry-forward patterns?",
  "Which team members are overcommitting?",
];
```

Show these as clickable badges when `question` is empty and no result is displayed. Clicking one fills the input and auto-submits.

---

## Phase 4: Production Monitoring ✅ (mostly)

**Goal:** Continuously validate AI quality in production, detect degradation early, and provide observability into the AI layer's health.

### 4a. Async Eval Sampling ✅

**Status:** Implemented. `FaithfulnessEvaluator.maybeScopeAsync()` is called from `AiSuggestionService.storeSuggestion()`. High-stakes types (RISK_SIGNAL, TEAM_INSIGHT, PERSONAL_INSIGHT, RAG_QUERY) always scored; lower-stakes types sampled at 10%. Scores stored on `ai_suggestion` via V12 migration columns.

### 4b. Prometheus Metrics for AI Quality ✅

**Status:** Implemented as `AiQualityMetrics.java`. Exposes:
- `weekly_commit_ai_faithfulness_score` — rolling 7-day average by suggestion type
- `weekly_commit_ai_acceptance_rate` — accepted / (accepted + dismissed) by type
- `weekly_commit_ai_provider_available` — 1/0 gauge
- `weekly_commit_ai_tokens_total` — cumulative tokens
- `weekly_commit_ai_requests_total` — cumulative requests

Refreshed every 5 minutes from DB queries.

**Remaining:** Grafana dashboard JSON (`infra/grafana/provisioning/dashboards/ai-quality.json`) not yet created. Metrics are available at `/actuator/prometheus` but no pre-built Grafana panels.

### 4c. Drift Alerting ✅

**Status:** Implemented in `infra/prometheus/alerts.yml`:
- `AiFaithfulnessLow`: rolling 7-day faithfulness < 0.88 → warning
- `AiFaithfulnessCritical`: rolling 7-day faithfulness < 0.85 → critical
- `AiAcceptanceRateLow`: rolling 7-day acceptance rate < 0.20 → warning
- `AiProviderDown`: provider unavailable > 5 minutes → critical

---

## Reference: Current AI Architecture

```
Write Path (real-time):
  CommitService.create() → SemanticIndexService.indexEntity(COMMIT, id)    [async]
  ScopeChangeService.*() → SemanticIndexService.indexEntity(SCOPE_CHANGE)  [async]
  CarryForwardService.*() → SemanticIndexService.indexEntity(CARRY_FORWARD)[async]
  LockService.lock()     → SemanticIndexService.indexEntity(PLAN_SUMMARY)  [async]
                         → InsightGenerationService.generatePersonalInsightsAsync() [async]
  ReconciliationService  → SemanticIndexService.indexEntity(PLAN_SUMMARY)  [async]
  ManagerReviewService   → SemanticIndexService.indexEntity(MANAGER_COMMENT)[async]
  TicketService.*()      → SemanticIndexService.indexEntity(TICKET)        [async]

Indexing Pipeline:
  Entity → ChunkBuilder.build*Chunk() → EmbeddingService.embed() → PineconeClient.upsert()

Query Pipeline:
  Question → Intent Classification (LLM) → EmbeddingService.embed() → PineconeClient.query()
           → Context Assembly → RAG Answer Generation (LLM) → AiSuggestionService.store()

Scheduled Jobs:
  SemanticIndexService.dailySweepReindex()     — 03:00 UTC, reindexes plans updated in last 48h
  InsightGenerationService.generateDailyInsights() — 08:00 UTC, team + personal insights
  RiskDetectionService.runDailyRiskDetection() — runs on all LOCKED plans

AI Services (10 types):
  COMMIT_DRAFT_ASSIST  → CommitDraftAssistService  → prompt: commit-draft-assist.txt
  COMMIT_LINT          → CommitLintService          → prompt: commit-lint.txt
  RCDO_SUGGEST         → RcdoSuggestService         → prompt: rcdo-suggest.txt
  RISK_SIGNAL          → RiskDetectionService        → prompt: risk-signal.txt (+ rules engine)
  RECONCILE_ASSIST     → ReconcileAssistService      → prompt: reconcile-assist.txt
  TEAM_SUMMARY         → ManagerAiSummaryService     → prompt: team-summary.txt
  RAG_INTENT           → SemanticQueryService        → prompt: rag-intent.txt
  RAG_QUERY            → SemanticQueryService        → prompt: rag-query.txt
  TEAM_INSIGHT         → InsightGenerationService    → prompt: team-insight.txt
  PERSONAL_INSIGHT     → InsightGenerationService    → prompt: personal-insight.txt

Frontend AI Components (all wired):
  AiLintPanel            — commit quality lint (calls COMMIT_LINT)
  RiskSignalsPanel       — risk signal display (reads RISK_SIGNAL)
  InsightPanel           — team + personal insights (reads TEAM_INSIGHT, PERSONAL_INSIGHT)
  SemanticSearchInput    — RAG query interface (calls RAG_INTENT + RAG_QUERY) + suggested questions
  QueryAnswerCard        — RAG answer display with source citations
  AiFeedbackButtons      — thumbs up/down on every AI output
  CommitDraftAssistButton — ✨ AI Suggest in commit form (calls COMMIT_DRAFT_ASSIST)
  ReconcileAssistPanel   — AI reconciliation assistant (calls RECONCILE_ASSIST)
  AiCommitComposer       — AI-guided commit creation flow
  ProactiveRiskBanner    — critical risk signal banner
  TeamRiskSummaryBanner  — team-level risk overview
  ManagerAiSummaryCard   — AI-generated team summary (calls TEAM_SUMMARY)
  RcdoSuggestionInline   — RCDO link suggestion (calls RCDO_SUGGEST)
  AiSuggestedBadge       — visual indicator for AI-suggested content
```

## Reference: Key Manus Report Thresholds

| Metric | Dev Gate | Staging Gate | Prod Alert |
|---|---|---|---|
| Faithfulness | ≥ 0.85 | ≥ 0.90 | < 0.88 |
| Context Recall | ≥ 0.80 | ≥ 0.85 | < 0.82 |
| Answer Relevancy | ≥ 0.75 | ≥ 0.80 | < 0.78 |
| Context Precision | ≥ 0.65 | ≥ 0.70 | < 0.68 |
| P95 Latency | < 10s | < 7s | > 8s |
