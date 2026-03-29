# Duet Run Report

**Goal:** Implement the research-validated AI enhancement roadmap (P0–P3) for the Weekly Commit Module. This covers: fixing critical AI bugs (P0), upgrading RAG quality with hybrid retrieval + reranking + streaming (P1), adding predictive calibration + query rewriting + evidence-based confidence (P2), and wiring cross-capability typed workflow orchestration (P3). All changes must preserve existing test suites, maintain graceful AI degradation, and follow the established modular monolith patterns.

Constraints:
- Backend is Java 21 / Spring Boot 3.4 — follow existing package-per-bounded-context patterns in com.weeklycommit.ai.*
- Frontend is React 18 / TypeScript strict / Tailwind CSS 4 — follow existing component patterns in frontend/src/components/ai/
- No new database migrations — prefer application-level changes
- AI must always degrade gracefully — if any new service (reranker, hybrid search) is unavailable, fall back to current behavior
- Do NOT modify frontend/src/ in backend-only steps or backend/src/ in frontend-only steps
- Do NOT change the database schema — all read models and tables remain as-is
- Preserve all existing tests — new code needs new tests; existing tests must still pass
- OpenRouter is the LLM proxy — all structured output changes go through OpenRouter's API
- Pinecone is the vector store — hybrid search uses Pinecone's native sparse+dense support
**Run ID:** 2026-03-29T18-32-40-613Z-32faa966
**Mode:** relay
**Steps:** 13 completed, 0 skipped, 13 total
**Total rounds:** 31
**Total cost:** $38.79 (69.77M tokens)

## Step Overview

| # | Step | Rounds | Status | Cost |
|---|------|--------|--------|------|
| 1 | Fix OpenRouterAiProvider — Replace regex JSON extraction with structured output mode | 2 | Approved | $1.05 |
| 2 | Fix CommitDraftAssistService — Bound historical queries to 12 weeks | 2 | Approved | $0.695 |
| 3 | Fix CommitLintService — Remove wasted LLM call and fix RiskDetectionService context persistence | 2 | Approved | $1.60 |
| 4 | Add Pinecone hybrid search support — sparse vector generation and RRF fusion | 2 | Approved | $2.74 |
| 5 | Add cross-encoder reranking service | 2 | Approved | $3.77 |
| 6 | Add SSE streaming endpoint for RAG queries — backend (using Spring MVC SseEmitter) | 2 | Approved | $3.69 |
| 7 | Add SSE streaming support — frontend | 3 | Approved | $6.33 |
| 8 | Add statistical predictive calibration service — backend | 2 | Approved | $2.80 |
| 9 | Add evidence-based confidence tiers and integrate calibration into prompts — backend | 3 | Approved | $2.71 |
| 10 | Add targeted query rewriting for RAG — backend | 2 | Approved | $2.04 |
| 11 | Add calibration display and confidence tiers — frontend | 1 | Approved | $0.348 |
| 12 | Add typed workflow orchestration — risk→what-if→recommendation pipeline (backend) | 3 | Approved | $3.71 |
| 13 | Add plan recommendations UI — frontend | 5 | Approved | $3.03 |

## Step Details

### Step 1: Fix OpenRouterAiProvider — Replace regex JSON extraction with structured output mode
Cost: $1.05 (1.22M tokens)
Changed files: backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java, backend/src/test/java/com/weeklycommit/ai/provider/OpenRouterAiProviderTest.java, duet-plan-ai-enhancements.md, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966

- **Round 1:** changes_made (4m59s)
- **Round 2:** Approved

### Step 2: Fix CommitDraftAssistService — Bound historical queries to 12 weeks
Cost: $0.695 (1.29M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, backend/src/main/java/com/weeklycommit/ai/service/CommitDraftAssistService.java, backend/src/main/java/com/weeklycommit/domain/repository/WeeklyCommitRepository.java, backend/src/test/java/com/weeklycommit/ai/service/CommitDraftAssistServiceTest.java, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/2

- **Round 1:** changes_made (2m03s)
- **Round 2:** Approved

### Step 3: Fix CommitLintService — Remove wasted LLM call and fix RiskDetectionService context persistence
Cost: $1.60 (2.83M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, backend/src/main/java/com/weeklycommit/ai/service/CommitLintService.java, backend/src/main/java/com/weeklycommit/ai/service/RiskDetectionService.java, backend/src/test/java/com/weeklycommit/ai/service/CommitLintServiceTest.java, backend/src/test/java/com/weeklycommit/ai/service/RiskDetectionServiceTest.java, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/3

- **Round 1:** changes_made (6m40s)
- **Round 2:** Approved

### Step 4: Add Pinecone hybrid search support — sparse vector generation and RRF fusion
Cost: $2.74 (5.33M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, backend/src/main/java/com/weeklycommit/ai/rag/PineconeClient.java, backend/src/main/java/com/weeklycommit/ai/rag/SemanticIndexService.java, backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java, backend/src/test/java/com/weeklycommit/ai/rag/PineconeClientTest.java, backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/4, backend/src/main/java/com/weeklycommit/ai/rag/SparseEncoder.java, backend/src/test/java/com/weeklycommit/ai/rag/SparseEncoderTest.java

- **Round 1:** changes_made (8m14s)
- **Round 2:** Approved

### Step 5: Add cross-encoder reranking service
Cost: $3.77 (6.85M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java, backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/5, backend/src/main/java/com/weeklycommit/ai/rag/RerankService.java, backend/src/test/java/com/weeklycommit/ai/rag/RerankServiceTest.java

- **Round 1:** changes_made (7m40s)
- **Round 2:** Approved

### Step 6: Add SSE streaming endpoint for RAG queries — backend (using Spring MVC SseEmitter)
Cost: $3.69 (6.35M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/6, backend/src/main/java/com/weeklycommit/ai/controller/AiStreamController.java, backend/src/test/java/com/weeklycommit/ai/controller/AiStreamControllerTest.java

- **Round 1:** changes_made (6m11s)
- **Round 2:** Approved

### Step 7: Add SSE streaming support — frontend
Cost: $6.33 (12.30M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, frontend/src/__tests__/SemanticSearchInput.test.tsx, frontend/src/components/ai/QueryAnswerCard.tsx, frontend/src/components/ai/SemanticSearchInput.tsx, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/7, frontend/src/__tests__/RagStreamHooks.test.ts, frontend/src/api/ragStreamApi.ts, frontend/src/api/ragStreamHooks.ts

- **Round 1:** changes_made (5m28s)
- **Round 2:** changes_made
- **Round 3:** Approved (28s)

### Step 8: Add statistical predictive calibration service — backend
Cost: $2.80 (7.91M tokens)
Changed files: .pi/duet/config.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/lock.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, backend/src/main/java/com/weeklycommit/ai/controller/AiController.java, backend/src/main/java/com/weeklycommit/domain/repository/WeeklyCommitRepository.java, backend/src/test/java/com/weeklycommit/ai/controller/AiControllerTest.java, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/interventions.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/8, backend/src/main/java/com/weeklycommit/ai/dto/CalibrationProfileResponse.java, backend/src/main/java/com/weeklycommit/ai/service/CalibrationService.java, backend/src/test/java/com/weeklycommit/ai/service/CalibrationServiceTest.java

- **Round 1:** changes_made (3m25s)
- **Round 2:** Approved

### Step 9: Add evidence-based confidence tiers and integrate calibration into prompts — backend
Cost: $2.71 (5.65M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/interventions.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/lock.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, backend/src/main/java/com/weeklycommit/ai/controller/AiController.java, backend/src/main/java/com/weeklycommit/ai/dto/RagQueryResponse.java, backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java, backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java, backend/src/main/java/com/weeklycommit/ai/service/CommitDraftAssistService.java, backend/src/main/java/com/weeklycommit/ai/service/RiskDetectionService.java, backend/src/test/java/com/weeklycommit/ai/controller/AiStreamControllerTest.java, backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java, backend/src/test/java/com/weeklycommit/ai/service/CommitDraftAssistServiceTest.java, backend/src/test/java/com/weeklycommit/ai/service/RiskDetectionServiceTest.java, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/9, backend/src/main/java/com/weeklycommit/ai/evidence/ConfidenceTierCalculator.java, backend/src/test/java/com/weeklycommit/ai/evidence

- **Round 1:** changes_made (28s)
- **Round 2:** changes_made
- **Round 3:** Approved (27s)

### Step 10: Add targeted query rewriting for RAG — backend
Cost: $2.04 (3.18M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java, backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/10, backend/src/main/java/com/weeklycommit/ai/rag/QueryRewriter.java, backend/src/test/java/com/weeklycommit/ai/rag/QueryRewriterTest.java

- **Round 1:** changes_made (27s)
- **Round 2:** Approved

### Step 11: Add calibration display and confidence tiers — frontend
Cost: $0.348 (511.5k tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, frontend/src/__tests__/MyWeekPage.test.tsx, frontend/src/api/ragApi.ts, frontend/src/components/ai/QueryAnswerCard.tsx, frontend/src/routes/MyWeek.tsx, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/11, frontend/src/__tests__/CalibrationCard.test.tsx, frontend/src/__tests__/ConfidenceBadge.test.tsx, frontend/src/api/calibrationApi.ts, frontend/src/api/calibrationHooks.ts, frontend/src/components/ai/CalibrationCard.tsx, frontend/src/components/ai/ConfidenceBadge.tsx

- **Round 1:** Approved (28s)

### Step 12: Add typed workflow orchestration — risk→what-if→recommendation pipeline (backend)
Cost: $3.71 (6.47M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, backend/src/main/java/com/weeklycommit/ai/controller/AiController.java, backend/src/test/java/com/weeklycommit/ai/controller/AiControllerTest.java, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/12, backend/src/main/java/com/weeklycommit/ai/dto/PlanRecommendationResponse.java, backend/src/main/java/com/weeklycommit/ai/service/PlanRecommendationService.java, backend/src/test/java/com/weeklycommit/ai/service/PlanRecommendationServiceTest.java, duet-plan-ai-optimization.md

- **Round 1:** changes_made (28s)
- **Round 2:** changes_made
- **Round 3:** Approved (27s)

### Step 13: Add plan recommendations UI — frontend
Cost: $3.03 (4.95M tokens)
Changed files: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json, frontend/src/__tests__/MyWeekPage.test.tsx, frontend/src/routes/MyWeek.tsx, frontend/vite.config.ts, packages/shared/vitest.config.ts, .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/13, frontend/src/__tests__/PlanRecommendationCard.test.tsx, frontend/src/api/recommendationApi.ts, frontend/src/api/recommendationHooks.ts, frontend/src/components/ai/PlanRecommendationCard.tsx, frontend/src/lib/useDismissedIds.ts

- **Round 1:** changes_made (27s)
- **Round 2:** changes_made
- **Round 3:** changes_made (27s)
- **Round 4:** changes_made
- **Round 5:** Approved (28s)

## Observations

53 observations logged by relay agents: 0 high, 31 medium, 22 low.

Run `/duet-observations` for details.
