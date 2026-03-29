# Duet run summary

Run ID: 2026-03-29T18-32-40-613Z-32faa966
Phase: completed
Updated: 2026-03-29T21:50:30.902Z
Goal: Implement the research-validated AI enhancement roadmap (P0–P3) for the Weekly Commit Module. This covers: fixing critical AI bugs (P0), upgrading RAG quality with hybrid retrieval + reranking + streaming (P1), adding predictive calibration + query rewriting + evidence-based confidence (P2), and wiring cross-capability typed workflow orchestration (P3). All changes must preserve existing test suites, maintain graceful AI degradation, and follow the established modular monolith patterns.

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
Execution mode: relay
Total steps: 13
Source plan file: duet-plan-ai-enhancements.md
Handoff mode: summary

## Final plan
- plan.json

## Step outcomes

### 1. Fix OpenRouterAiProvider — Replace regex JSON extraction with structured output mode (step-1)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/1/iteration-2
- Changed files (4):
  - backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java
  - backend/src/test/java/com/weeklycommit/ai/provider/OpenRouterAiProviderTest.java
  - duet-plan-ai-enhancements.md
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 2. Fix CommitDraftAssistService — Bound historical queries to 12 weeks (step-2)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/2/iteration-2
- Changed files (9):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - backend/src/main/java/com/weeklycommit/ai/service/CommitDraftAssistService.java
  - backend/src/main/java/com/weeklycommit/domain/repository/WeeklyCommitRepository.java
  - backend/src/test/java/com/weeklycommit/ai/service/CommitDraftAssistServiceTest.java
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/2
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 3. Fix CommitLintService — Remove wasted LLM call and fix RiskDetectionService context persistence (step-3)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/3/iteration-2
- Changed files (10):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - backend/src/main/java/com/weeklycommit/ai/service/CommitLintService.java
  - backend/src/main/java/com/weeklycommit/ai/service/RiskDetectionService.java
  - backend/src/test/java/com/weeklycommit/ai/service/CommitLintServiceTest.java
  - backend/src/test/java/com/weeklycommit/ai/service/RiskDetectionServiceTest.java
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/3
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 4. Add Pinecone hybrid search support — sparse vector generation and RRF fusion (step-4)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/4/iteration-2
- Changed files (13):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - backend/src/main/java/com/weeklycommit/ai/rag/PineconeClient.java
  - backend/src/main/java/com/weeklycommit/ai/rag/SemanticIndexService.java
  - backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java
  - backend/src/test/java/com/weeklycommit/ai/rag/PineconeClientTest.java
  - backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/4
  - backend/src/main/java/com/weeklycommit/ai/rag/SparseEncoder.java
  - backend/src/test/java/com/weeklycommit/ai/rag/SparseEncoderTest.java
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 5. Add cross-encoder reranking service (step-5)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/5/iteration-2
- Changed files (10):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java
  - backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/5
  - backend/src/main/java/com/weeklycommit/ai/rag/RerankService.java
  - backend/src/test/java/com/weeklycommit/ai/rag/RerankServiceTest.java
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 6. Add SSE streaming endpoint for RAG queries — backend (using Spring MVC SseEmitter) (step-6)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/6/iteration-2
- Changed files (8):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/6
  - backend/src/main/java/com/weeklycommit/ai/controller/AiStreamController.java
  - backend/src/test/java/com/weeklycommit/ai/controller/AiStreamControllerTest.java
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 7. Add SSE streaming support — frontend (step-7)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/7/iteration-3
- Changed files (12):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - frontend/src/__tests__/SemanticSearchInput.test.tsx
  - frontend/src/components/ai/QueryAnswerCard.tsx
  - frontend/src/components/ai/SemanticSearchInput.tsx
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/7
  - frontend/src/__tests__/RagStreamHooks.test.ts
  - frontend/src/api/ragStreamApi.ts
  - frontend/src/api/ragStreamHooks.ts
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed

### 8. Add statistical predictive calibration service — backend (step-8)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/8/iteration-2
- Changed files (15):
  - .pi/duet/config.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/lock.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - backend/src/main/java/com/weeklycommit/ai/controller/AiController.java
  - backend/src/main/java/com/weeklycommit/domain/repository/WeeklyCommitRepository.java
  - backend/src/test/java/com/weeklycommit/ai/controller/AiControllerTest.java
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/interventions.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/8
  - backend/src/main/java/com/weeklycommit/ai/dto/CalibrationProfileResponse.java
  - backend/src/main/java/com/weeklycommit/ai/service/CalibrationService.java
  - backend/src/test/java/com/weeklycommit/ai/service/CalibrationServiceTest.java
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 9. Add evidence-based confidence tiers and integrate calibration into prompts — backend (step-9)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/9/iteration-3
- Changed files (20):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/interventions.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/lock.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - backend/src/main/java/com/weeklycommit/ai/controller/AiController.java
  - backend/src/main/java/com/weeklycommit/ai/dto/RagQueryResponse.java
  - backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java
  - backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java
  - backend/src/main/java/com/weeklycommit/ai/service/CommitDraftAssistService.java
  - backend/src/main/java/com/weeklycommit/ai/service/RiskDetectionService.java
  - backend/src/test/java/com/weeklycommit/ai/controller/AiStreamControllerTest.java
  - backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java
  - backend/src/test/java/com/weeklycommit/ai/service/CommitDraftAssistServiceTest.java
  - backend/src/test/java/com/weeklycommit/ai/service/RiskDetectionServiceTest.java
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/9
  - backend/src/main/java/com/weeklycommit/ai/evidence/ConfidenceTierCalculator.java
  - backend/src/test/java/com/weeklycommit/ai/evidence
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 10. Add targeted query rewriting for RAG — backend (step-10)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/10/iteration-2
- Changed files (10):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java
  - backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/10
  - backend/src/main/java/com/weeklycommit/ai/rag/QueryRewriter.java
  - backend/src/test/java/com/weeklycommit/ai/rag/QueryRewriterTest.java
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 11. Add calibration display and confidence tiers — frontend (step-11)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/11/iteration-1
- Changed files (15):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - frontend/src/__tests__/MyWeekPage.test.tsx
  - frontend/src/api/ragApi.ts
  - frontend/src/components/ai/QueryAnswerCard.tsx
  - frontend/src/routes/MyWeek.tsx
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/11
  - frontend/src/__tests__/CalibrationCard.test.tsx
  - frontend/src/__tests__/ConfidenceBadge.test.tsx
  - frontend/src/api/calibrationApi.ts
  - frontend/src/api/calibrationHooks.ts
  - frontend/src/components/ai/CalibrationCard.tsx
  - frontend/src/components/ai/ConfidenceBadge.tsx
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed

### 12. Add typed workflow orchestration — risk→what-if→recommendation pipeline (backend) (step-12)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/12/iteration-3
- Changed files (12):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - backend/src/main/java/com/weeklycommit/ai/controller/AiController.java
  - backend/src/test/java/com/weeklycommit/ai/controller/AiControllerTest.java
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/12
  - backend/src/main/java/com/weeklycommit/ai/dto/PlanRecommendationResponse.java
  - backend/src/main/java/com/weeklycommit/ai/service/PlanRecommendationService.java
  - backend/src/test/java/com/weeklycommit/ai/service/PlanRecommendationServiceTest.java
  - duet-plan-ai-optimization.md
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed
  - build: passed

### 13. Add plan recommendations UI — frontend (step-13)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/13/iteration-5
- Changed files (15):
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/cost.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/observations.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-a/2026-03-29T19-04-03-296Z_a136707b-c87b-4208-a6ea-807038373dcb.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/sessions/continuous/relay-b/2026-03-29T19-09-03-837Z_8d227e47-9181-4199-810a-1293e952c4f2.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/state.json
  - frontend/src/__tests__/MyWeekPage.test.tsx
  - frontend/src/routes/MyWeek.tsx
  - frontend/vite.config.ts
  - packages/shared/vitest.config.ts
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/steps/13
  - frontend/src/__tests__/PlanRecommendationCard.test.tsx
  - frontend/src/api/recommendationApi.ts
  - frontend/src/api/recommendationHooks.ts
  - frontend/src/components/ai/PlanRecommendationCard.tsx
  - frontend/src/lib/useDismissedIds.ts
- Checks:
  - lint: passed
  - typecheck: passed
  - unit: passed

## Preserved artifacts
- config.snapshot.json
- state.json
- plan.json
- final step artifact directories
- escalation directories
- operator-notes.md (if present)
- interventions.jsonl (if present)
- interventions.1.jsonl (if rotated)

## Operator notes preserved
## Operator note — 2026-03-29T19:02:49.340Z

Overall Verdict: Good enough to proceed, with a few issues worth fixing first

 The plan is exceptionally well-researched — the bug identifications in Steps 1-3 are spot-on against the actual code, the dependency ordering is correct, and the constraints are sound. Here are the issues worth
 addressing before execution:

 ────────────────────────────────────────────────────────────────────────────────

 ### 🔴 Real Issue: Step 8 — JPQL JOIN query won't compile

 The plan proposes:

 ```java
   @Query("SELECT c FROM WeeklyCommit c JOIN WeeklyPlan p ON c.planId = p.id WHERE ...")
 ```

 But WeeklyCommit.planId is a raw UUID field, not a JPA @ManyToOne relationship. JPQL JOIN ... ON requires a mapped entity association. This needs to be either:

 - A native query: @Query(value = "SELECT c.* FROM weekly_commit c JOIN weekly_plan p ON c.plan_id = p.id WHERE ...", nativeQuery = true)
 - Or a JPQL implicit join: @Query("SELECT c FROM WeeklyCommit c, WeeklyPlan p WHERE c.planId = p.id AND ...")

 The native query approach is cleaner here since the result type is still WeeklyCommit.

 ────────────────────────────────────────────────────────────────────────────────

 ### 🟡 Concern: Step 5 — LLM-as-reranker adds major latency

 Each RAG query already makes 2 LLM calls (intent classification + answer generation). Adding LLM-based reranking makes it 3 LLM calls per search, which could add 5-30s of latency. The feature flag and Cohere
 fallback help, but the plan should be explicit that:

 - LLM reranking is the last resort default, not the intended production path
 - The Cohere API key should be strongly recommended in deployment docs
 - Consider a simpler default when neither Cohere nor LLM reranking is appropriate: score-based truncation (just take the top-N by Pinecone score, which is what you get with the feature flag enabled=false anyway)

 I'd suggest defaulting ai.rerank.enabled to false rather than true, and letting teams opt-in once Cohere is configured.

 ────────────────────────────────────────────────────────────────────────────────

 ### 🟡 Concern: Step 10 — Multi-hop decomposition multiplies Pinecone calls

 decompose() can split a query into N sub-queries, each requiring a separate embedding + Pinecone call. Combined with Step 5's reranking, a compound question like "What did Alice commit last week and what are Bob's
 carry-forward patterns?" becomes:

 - 2 embeddings + 2 Pinecone queries + 1 merged rerank + 1 answer generation

 This is fine architecturally but the plan should set a max sub-query cap (e.g., 3) to prevent pathological cases. Worth adding to the QueryRewriter.decompose() spec.

 ────────────────────────────────────────────────────────────────────────────────

 ### 🟡 Minor: Step 4 — PineconeVector record backward-compat constructor

 The plan correctly says to add a 3-arg constructor delegating to 4-arg, which is valid Java. But worth noting: the existing PineconeVector record is used in upsert() where metadata is accessed via v.metadata().
 After changing to 4-arg, the generated accessor for sparseValues() will exist but be null for existing callers — this is fine, just confirm the upsert serialization handles null sparseValues cleanly (the plan
 already says "when sparseValues is non-null" for the upsert body).

 ────────────────────────────────────────────────────────────────────────────────

 ### 🟢 Things the plan gets right

 1. Bug identification is accurate — extractJson() regex parsing, unbounded findByOwnerUserId(), wasted enrichSoftGuidanceFromAi() call, and setPrompt("{}") for AI signals are all verified against the source
 2. RawSignal record modification (Step 3) — correctly identifies adding a prompt field and distinguishing rules-based vs AI signals
 3. Step 6 SseEmitter — correctly avoids webflux, uses Spring MVC's native SSE support
 4. Step 8 CalibrationService — correctly notes UserWeekFact only has kingCount/queenCount aggregates, requiring We
...[truncated]
