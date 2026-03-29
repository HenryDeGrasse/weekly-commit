# Duet Run Report

**Goal:** Build a What-If Planner and Historical Replay Benchmark for the Weekly Commit Module. The What-If Planner lets users simulate hypothetical commit mutations (add/remove/modify) against a draft plan and see the impact on capacity, RCDO coverage, and risk — with an optional LLM narrative. All computation uses the existing rules engine and feature tables; the LLM only narrates. The Historical Replay Benchmark replays risk predictions against historical seed data (weeks 1-8), compares to actual reconcile outcomes (weeks 9-12), computes precision/recall/F1, and publishes results as a markdown doc.

Success criteria:
- POST /api/ai/what-if endpoint accepts hypothetical mutations and returns structured impact analysis + optional LLM narrative
- Frontend What-If panel on MyWeek page shows before/after capacity, RCDO coverage delta, risk changes, and AI narrative
- Historical replay benchmark runs via ./gradlew evalTest and produces docs/eval-results.md
- All existing tests continue to pass (npm test, npm run lint, npm run typecheck)

Constraints:
- Java 21 backend, TypeScript strict frontend, SQL only — no Python or new infrastructure
- Reuse existing PostgreSQL, Pinecone, OpenRouter, RiskDetectionService rules, read models, AiProviderRegistry
- Do NOT modify existing entity classes, migrations, or domain enums — add new DTOs/services only
- Do NOT restructure existing frontend routes/pages — integrate via composition
- Follow existing patterns: DTOs as Java records, @Service annotation, controllers in module packages, frontend components in frontend/src/components/ai/
**Run ID:** 2026-03-29T00-43-57-687Z-7e364e0f
**Mode:** relay
**Steps:** 5 completed, 0 skipped, 5 total
**Total rounds:** 10
**Total cost:** $13.31 (22.72M tokens)

## Step Overview

| # | Step | Rounds | Status | Cost |
|---|------|--------|--------|------|
| 1 | What-If backend DTOs and pure-computation service | 2 | Approved | $1.49 |
| 2 | What-If LLM narration and REST API endpoint | 2 | Approved | $2.09 |
| 3 | What-If frontend API client and panel component | 2 | Approved | $2.48 |
| 4 | What-If eval fixtures and eval runner extension | 2 | Approved | $1.84 |
| 5 | Historical Replay Benchmark and eval results documentation | 2 | Approved | $2.77 |

## Step Details

### Step 1: What-If backend DTOs and pure-computation service
Cost: $1.49 (2.14M tokens)
Changed files: README.md, docs/ai-eval-roadmap.md, docs/ai-thoughts.md, docs/architecture.md, duet-plan-whatif.md, duet-plan.md, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f, backend/src/main/java/com/weeklycommit/ai/dto/WhatIfRequest.java, backend/src/main/java/com/weeklycommit/ai/dto/WhatIfResponse.java, backend/src/main/java/com/weeklycommit/ai/service/WhatIfService.java, backend/src/test/java/com/weeklycommit/ai/service/WhatIfServiceTest.java

- **Round 1:** changes_made (7m29s)
- **Round 2:** Approved

### Step 2: What-If LLM narration and REST API endpoint
Cost: $2.09 (3.88M tokens)
Changed files: .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/cost.json, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/observations.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/sessions/continuous/relay-a/2026-03-29T01-38-32-791Z_770ad88b-73f9-41bd-b173-003141923a9c.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/sessions/continuous/relay-b/2026-03-29T01-46-02-281Z_703119a5-a2fb-4f86-a6c1-58e5136412ff.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/state.json, backend/src/main/java/com/weeklycommit/ai/controller/AiController.java, backend/src/main/java/com/weeklycommit/ai/provider/AiContext.java, backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java, backend/src/main/java/com/weeklycommit/ai/provider/StubAiProvider.java, backend/src/main/java/com/weeklycommit/ai/service/WhatIfService.java, backend/src/test/java/com/weeklycommit/ai/controller/AiControllerTest.java, backend/src/test/java/com/weeklycommit/ai/service/WhatIfServiceTest.java, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/steps/2, backend/src/main/resources/prompts/what-if.txt

- **Round 1:** changes_made (5m57s)
- **Round 2:** Approved

### Step 3: What-If frontend API client and panel component
Cost: $2.48 (5.67M tokens)
Changed files: .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/cost.json, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/observations.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/sessions/continuous/relay-a/2026-03-29T01-38-32-791Z_770ad88b-73f9-41bd-b173-003141923a9c.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/sessions/continuous/relay-b/2026-03-29T01-46-02-281Z_703119a5-a2fb-4f86-a6c1-58e5136412ff.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/state.json, frontend/src/__tests__/MyWeekPage.test.tsx, frontend/src/api/aiHooks.ts, frontend/src/routes/MyWeek.tsx, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/steps/3, frontend/src/api/whatIfApi.ts, frontend/src/components/ai/WhatIfPanel.tsx

- **Round 1:** changes_made (6m24s)
- **Round 2:** Approved

### Step 4: What-If eval fixtures and eval runner extension
Cost: $1.84 (4.38M tokens)
Changed files: .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/cost.json, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/observations.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/sessions/continuous/relay-a/2026-03-29T01-38-32-791Z_770ad88b-73f9-41bd-b173-003141923a9c.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/sessions/continuous/relay-b/2026-03-29T01-46-02-281Z_703119a5-a2fb-4f86-a6c1-58e5136412ff.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/state.json, backend/src/test/java/com/weeklycommit/ai/eval/PromptEvalRunner.java, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/steps/4, backend/src/test/resources/eval/what-if

- **Round 1:** changes_made (4m14s)
- **Round 2:** Approved

### Step 5: Historical Replay Benchmark and eval results documentation
Cost: $2.77 (4.06M tokens)
Changed files: .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/cost.json, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/observations.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/sessions/continuous/relay-a/2026-03-29T01-38-32-791Z_770ad88b-73f9-41bd-b173-003141923a9c.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/sessions/continuous/relay-b/2026-03-29T01-46-02-281Z_703119a5-a2fb-4f86-a6c1-58e5136412ff.jsonl, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/state.json, .pi/duet/runs/2026-03-29T00-43-57-687Z-7e364e0f/steps/5, backend/src/test/java/com/weeklycommit/ai/eval/HistoricalReplayBenchmark.java, docs/eval-results.md

- **Round 1:** changes_made (7m38s)
- **Round 2:** Approved

## Observations

25 observations logged by relay agents: 0 high, 1 medium, 24 low.

Run `/duet-observations` for details.
