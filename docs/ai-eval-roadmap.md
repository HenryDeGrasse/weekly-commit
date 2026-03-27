# AI Evaluation & Quality Roadmap

**Created:** 2026-03-26
**Context:** Weekly Commit Module — RAG pipeline uses Pinecone (vector store) + OpenRouter/Claude (LLM) + OpenAI text-embedding-3-small (embeddings). 10 prompt templates, 7 AI service types, 6 frontend AI components. Evaluation gaps identified via code audit and validated against Manus RAG evaluation research report (see `docs/rag-eval-research.md`).

---

## Phase 1: Eval Foundation

**Goal:** Build the infrastructure to scientifically validate AI output quality, enable A/B testing of prompts, and measure the metrics that the Manus research identifies as critical (Faithfulness ≥ 0.90, Context Recall ≥ 0.85).

### 1a. Golden Test Dataset

**What:** 15-20 realistic test cases per AI capability, stored as JSON fixtures under `backend/src/test/resources/eval/`. Not synthetic — hand-crafted from realistic scenarios matching our seed data patterns.

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

### 1b. Eval Runner

**What:** A JUnit test class that calls the real LLM (not mocked) with actual prompts, scores output against the golden dataset using automated + LLM-as-judge scoring, and writes results to a JSON report.

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

### 1c. Prompt Version Tracking

**What:** Add `prompt_version TEXT` column to `ai_suggestion` table. Populate it in `OpenRouterAiProvider` from the prompt template filename + a version suffix. Enables A/B analysis using existing `ai_feedback` data.

**Implementation:**
1. Flyway migration: `ALTER TABLE ai_suggestion ADD COLUMN prompt_version TEXT;`
2. `AiSuggestionResult` record: add `promptVersion` field
3. `OpenRouterAiProvider.generateSuggestion()`: read prompt version from template metadata or filename convention (`commit-draft-assist-v2.txt` → version `v2`)
4. `AiSuggestionService.storeSuggestion()`: persist prompt version

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

### 1d. Faithfulness Scoring

**What:** Implement the RAGAS faithfulness metric as an LLM-as-judge call specifically for RAG query answers and risk signals. Per Manus §2.1: decompose the answer into atomic claims, verify each against retrieved context, score = supported/total.

**Implementation:**
- New service: `FaithfulnessEvaluator` in `ai/eval/` package
- Judge prompt: given an answer + retrieved chunks, extract claims, mark each as SUPPORTED or UNSUPPORTED
- Store faithfulness score on `ai_suggestion` (new column `eval_faithfulness_score REAL`)
- Run asynchronously after suggestion storage (sample 10% of production suggestions)
- Target: ≥ 0.90 (Manus §8 staging gate)

**Context from Manus report:**
> "Faithfulness is the single most important metric for enterprise risk-signal generation, where a fabricated risk can trigger unnecessary escalation and erode stakeholder trust." — Manus §2.1
>
> The metric is computed by decomposing the answer into atomic statements, then verifying each against the retrieved context. Formula: Faithfulness = (claims supported by context) / (total claims). — Manus §2.1

---

## Phase 2: Prompt Quality

**Goal:** Improve actual LLM output quality through prompt engineering. This is the highest-ROI code change — better prompts produce better outputs immediately, before any eval infrastructure is needed.

### 2a. Add Few-Shot Examples to All Prompts

**What:** Add 1-2 good examples and 1 bad example to each of the 10 prompt templates in `backend/src/main/resources/prompts/`.

**Current problem:** Every prompt says "respond with ONLY a JSON object" but gives zero examples of good vs bad output. The model has no reference for what quality looks like in our domain.

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

### 2b. Add Field-Level Context Descriptions

**What:** The system prompts don't explain the data structure the model receives. The user message is a raw JSON blob from `buildUserMessage()` in `OpenRouterAiProvider`:

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

### 2c. Deduplicate Risk Signal Prompt vs Rules Engine

**What:** `risk-signal.txt` asks the LLM for risks but doesn't tell it what rules already exist. The `RiskDetectionService` already computes:
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

### 2d. Enrich ChunkBuilder with RCDO Context

**What:** Commit chunks in Pinecone are thin — a commit with no description embeds as:
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

## Phase 3: Wire Missing Frontend AI Features

**Goal:** Three AI backend capabilities exist but have no frontend component calling them. Wiring these up completes the user-facing AI story.

### 3a. Commit Draft Assist Button in CommitForm

**What:** `CommitDraftAssistService` (backend) and `aiApi.commitDraftAssist()` (frontend API client) exist and work. No UI component calls them.

**Build:** An "✨ AI Suggest" button in the commit creation/edit form that:
1. Calls `aiApi.commitDraftAssist()` with current form values
2. Shows suggestions inline (highlighted diff-style: "Current: X → Suggested: Y")
3. Each suggestion has Accept / Dismiss buttons (using existing `AiFeedbackButtons`)
4. Accepting a suggestion fills the form field
5. Disabled when AI is unavailable (check `useAiStatus()`)

**Location:** New component `frontend/src/components/ai/CommitDraftAssistButton.tsx`, used inside the commit form on the MyWeek page.

**UX notes:**
- Don't auto-run on every keystroke — trigger on explicit button click
- Show loading spinner while waiting
- If all suggestions are null (current values are already good), show "✓ Looks good!"
- Show rationale text in a tooltip or collapsed section

### 3b. Reconcile Assist in ReconcilePage

**What:** `ReconcileAssistService` (backend) exists. No frontend component calls it.

**Build:** An "AI Assist Reconciliation" button on the Reconcile page that:
1. Calls the reconcile assist endpoint with the plan's commits + scope changes
2. Pre-fills likely outcomes (ACHIEVED/PARTIALLY_ACHIEVED/NOT_ACHIEVED/CANCELED) in the `OutcomeSelector` components
3. Shows a draft week summary
4. Suggests which commits to carry forward with reasons
5. All suggestions are editable — user must explicitly accept

**Location:** New component `frontend/src/components/ai/ReconcileAssistPanel.tsx`, used in `frontend/src/routes/Reconcile.tsx`.

### 3c. Suggested Questions in SemanticSearchInput

**What:** When the search input is empty, show 3-4 clickable example questions as pills/chips below the input.

**Build:** Add to `SemanticSearchInput.tsx`:
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

## Phase 4: Production Monitoring

**Goal:** Continuously validate AI quality in production, detect degradation early, and provide observability into the AI layer's health.

### 4a. Async Eval Sampling

**What:** After each AI suggestion is stored, sample 5-10% and run faithfulness + relevancy scoring asynchronously. Store scores on the `ai_suggestion` row.

**Implementation:**
1. Add columns to `ai_suggestion`: `eval_faithfulness_score REAL`, `eval_relevancy_score REAL`, `eval_scored_at TIMESTAMPTZ`
2. New service: `ProductionEvalService` — after `AiSuggestionService.storeSuggestion()`, probabilistically enqueue an eval job
3. Eval job: call `FaithfulnessEvaluator` (from Phase 1d) + a relevancy judge on the stored suggestion
4. Results written back to the suggestion row

**Sampling strategy:** Score 100% of RISK_SIGNAL and TEAM_INSIGHT suggestions (high-stakes), 10% of COMMIT_DRAFT and COMMIT_LINT (high-volume, lower stakes).

### 4b. Prometheus Metrics for AI Quality

**What:** Expose Prometheus gauges for rolling AI quality metrics. Wire into the existing Grafana setup (provisioning already exists at `infra/grafana/provisioning/`).

**Metrics to expose:**
- `weekly_commit_ai_faithfulness_score` (gauge, labeled by suggestion_type) — rolling 7-day average
- `weekly_commit_ai_acceptance_rate` (gauge, labeled by suggestion_type) — accepted / (accepted + dismissed)
- `weekly_commit_ai_suggestion_latency_seconds` (histogram, labeled by suggestion_type)
- `weekly_commit_ai_provider_availability` (gauge) — 1 if available, 0 if not
- `weekly_commit_ai_total_tokens_used` (counter) — from `OpenRouterAiProvider.getTotalTokensUsed()`

**Grafana dashboard:** Create `infra/grafana/provisioning/dashboards/ai-quality.json` with panels for each metric.

### 4c. Drift Alerting

**What:** Alert when AI quality degrades below Manus-recommended production thresholds.

**Alert rules (add to `infra/prometheus/alerts.yml`):**
- `AiFaithfulnessLow`: rolling 7-day faithfulness < 0.88 → warning
- `AiFaithfulnessCritical`: rolling 7-day faithfulness < 0.85 → critical
- `AiAcceptanceRateLow`: rolling 7-day acceptance rate < 0.20 → warning
- `AiProviderDown`: provider unavailable for > 5 minutes → critical
- `AiLatencyHigh`: p95 suggestion latency > 8 seconds → warning

**Context from Manus report (§8):**
> Production alert thresholds: Faithfulness < 0.88, Context Recall < 0.82, Answer Relevancy < 0.78, P95 Latency > 8s

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

Frontend AI Components:
  AiLintPanel            — commit quality lint (calls COMMIT_LINT)
  RiskSignalsPanel       — risk signal display (reads RISK_SIGNAL)
  InsightPanel           — team + personal insights (reads TEAM_INSIGHT, PERSONAL_INSIGHT)
  SemanticSearchInput    — RAG query interface (calls RAG_INTENT + RAG_QUERY)
  QueryAnswerCard        — RAG answer display with source citations
  AiFeedbackButtons      — thumbs up/down on every AI output

  MISSING: CommitDraftAssist UI (backend exists, no frontend component)
  MISSING: ReconcileAssist UI (backend exists, no frontend component)
  MISSING: Suggested questions in SemanticSearchInput
```

## Reference: Key Manus Report Thresholds

| Metric | Dev Gate | Staging Gate | Prod Alert |
|---|---|---|---|
| Faithfulness | ≥ 0.85 | ≥ 0.90 | < 0.88 |
| Context Recall | ≥ 0.80 | ≥ 0.85 | < 0.82 |
| Answer Relevancy | ≥ 0.75 | ≥ 0.80 | < 0.78 |
| Context Precision | ≥ 0.65 | ≥ 0.70 | < 0.68 |
| P95 Latency | < 10s | < 7s | > 8s |
