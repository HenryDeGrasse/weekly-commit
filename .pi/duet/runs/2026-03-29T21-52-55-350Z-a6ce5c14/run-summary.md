# Duet run summary

Run ID: 2026-03-29T21-52-55-350Z-a6ce5c14
Phase: completed
Updated: 2026-03-29T23:08:28.502Z
Goal: Add seven optimization capabilities to the AI pipeline — prompt caching, A/B testing infrastructure, embedding model override (offline eval only), LLM model A/B, HyDE, SQL query routing, and chunk size analysis — all backward-compatible and default-off. The embedding-model A/B experiment is scoped to offline evaluation only (via AbComparisonRunner) because the Pinecone index is fixed at 1536 dimensions matching text-embedding-3-small; routing live queries through text-embedding-3-large (3072-d) would cause dimension mismatches. Backend-only; no frontend, e2e, or schema changes.
Execution mode: relay
Total steps: 7
Source plan file: duet-plan-ai-optimization.md
Handoff mode: summary

## Final plan
- plan.json

## Step outcomes

### 1. Prompt Caching in OpenRouterAiProvider (step-1)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/1/iteration-2
- Changed files (238):
  - .pi/duet/config.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/draft-plan.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/handoff-source.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/handoff-summary/assistant.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/handoff-summary/events.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/handoff-summary/parsed.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/handoff-summary/stderr.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/handoff-summary/system-prompt.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/handoff.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/handoff.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/lock.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/plan.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/review.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/side-a/assistant.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/side-a/events.jsonl
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/side-a/parsed.json
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/side-a/stderr.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/side-a/system-prompt.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/side-b/assistant.txt
  - .pi/duet/runs/2026-03-29T18-32-40-613Z-32faa966/planning/round-1/side-b/events.jsonl
  - ...and 218 more
- Checks:
  - static: passed
  - unit: passed

### 2. A/B Testing Infrastructure (step-2)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/2/iteration-2
- Changed files (9):
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/cost.json
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/observations.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-a/2026-03-29T22-15-56-016Z_4d8a934a-459e-41a2-bc00-87beceb1d233.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-b/2026-03-29T22-18-26-552Z_5dd146d3-1137-4d36-824a-14b1aae37343.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/state.json
  - backend/src/main/resources/application.yml
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/2
  - backend/src/main/java/com/weeklycommit/ai/experiment
  - backend/src/test/java/com/weeklycommit/ai/experiment
- Checks:
  - static: passed
  - unit: passed

### 3. Embedding Model Override (Offline Eval) + Chunk Size Analysis (step-3)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/3/iteration-2
- Changed files (11):
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/cost.json
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/observations.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-a/2026-03-29T22-15-56-016Z_4d8a934a-459e-41a2-bc00-87beceb1d233.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-b/2026-03-29T22-18-26-552Z_5dd146d3-1137-4d36-824a-14b1aae37343.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/state.json
  - backend/src/main/java/com/weeklycommit/ai/rag/EmbeddingService.java
  - backend/src/main/java/com/weeklycommit/ai/rag/SemanticIndexService.java
  - backend/src/test/java/com/weeklycommit/ai/rag/EmbeddingServiceTest.java
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/3
  - backend/src/main/java/com/weeklycommit/ai/rag/ChunkSizeAnalyzer.java
  - backend/src/test/java/com/weeklycommit/ai/rag/ChunkSizeAnalyzerTest.java
- Checks:
  - static: passed
  - unit: passed

### 4. LLM Model A/B + Experiment-Aware Provider (step-4)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/4/iteration-3
- Changed files (12):
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/cost.json
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/observations.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-a/2026-03-29T22-15-56-016Z_4d8a934a-459e-41a2-bc00-87beceb1d233.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-b/2026-03-29T22-18-26-552Z_5dd146d3-1137-4d36-824a-14b1aae37343.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/state.json
  - backend/src/main/java/com/weeklycommit/ai/provider/AiProvider.java
  - backend/src/main/java/com/weeklycommit/ai/provider/AiProviderRegistry.java
  - backend/src/main/java/com/weeklycommit/ai/provider/AiSuggestionResult.java
  - backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java
  - backend/src/test/java/com/weeklycommit/ai/provider/AiProviderRegistryTest.java
  - backend/src/test/java/com/weeklycommit/ai/provider/OpenRouterAiProviderTest.java
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/4
- Checks:
  - static: passed
  - unit: passed

### 5. HyDE Service + SQL Query Router (step-5)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/5/iteration-3
- Changed files (16):
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/cost.json
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/observations.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-a/2026-03-29T22-15-56-016Z_4d8a934a-459e-41a2-bc00-87beceb1d233.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-b/2026-03-29T22-18-26-552Z_5dd146d3-1137-4d36-824a-14b1aae37343.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/state.json
  - backend/src/main/java/com/weeklycommit/ai/provider/AiContext.java
  - backend/src/main/java/com/weeklycommit/ai/provider/OpenRouterAiProvider.java
  - backend/src/main/java/com/weeklycommit/ai/provider/StubAiProvider.java
  - backend/src/main/resources/prompts/rag-intent.txt
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/5
  - backend/src/main/java/com/weeklycommit/ai/rag/HydeService.java
  - backend/src/main/java/com/weeklycommit/ai/rag/SqlQueryRouter.java
  - backend/src/main/resources/prompts/hyde.txt
  - backend/src/main/resources/prompts/sql-synthesis.txt
  - backend/src/test/java/com/weeklycommit/ai/rag/HydeServiceTest.java
  - backend/src/test/java/com/weeklycommit/ai/rag/SqlQueryRouterTest.java
- Checks:
  - static: passed
  - unit: passed

### 6. Wire HyDE, SQL Routing, and LLM A/B into SemanticQueryService (step-6)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/6/iteration-2
- Changed files (8):
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/cost.json
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/observations.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-a/2026-03-29T22-15-56-016Z_4d8a934a-459e-41a2-bc00-87beceb1d233.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-b/2026-03-29T22-18-26-552Z_5dd146d3-1137-4d36-824a-14b1aae37343.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/state.json
  - backend/src/main/java/com/weeklycommit/ai/rag/SemanticQueryService.java
  - backend/src/test/java/com/weeklycommit/ai/rag/SemanticQueryServiceTest.java
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/6
- Checks:
  - static: passed
  - unit: passed

### 7. A/B Comparison Eval Runner (step-7)
- Verdict: approve
- Final artifacts: .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/7/iteration-3
- Changed files (8):
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/cost.json
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/observations.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-a/2026-03-29T22-15-56-016Z_4d8a934a-459e-41a2-bc00-87beceb1d233.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/sessions/continuous/relay-b/2026-03-29T22-18-26-552Z_5dd146d3-1137-4d36-824a-14b1aae37343.jsonl
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/state.json
  - backend/build.gradle.kts
  - .pi/duet/runs/2026-03-29T21-52-55-350Z-a6ce5c14/steps/7
  - backend/src/test/java/com/weeklycommit/ai/eval/AbComparisonRunner.java
- Checks:
  - static: passed

## Preserved artifacts
- config.snapshot.json
- state.json
- plan.json
- final step artifact directories
- escalation directories
- operator-notes.md (if present)
- interventions.jsonl (if present)
- interventions.1.jsonl (if rotated)
