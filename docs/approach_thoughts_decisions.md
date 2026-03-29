# AI Enhancement: Approach, Thoughts & Decisions

> **Date:** 2026-03-29  
> **Context:** Deep analysis of the Weekly Commit Module's AI integration, followed by parallel research using Claude Research and ChatGPT (Pro, Extended Pro) to validate every recommendation against current production evidence, academic research, and real-world enterprise practice.

---

## Table of Contents

1. [How We Got Here](#how-we-got-here)
2. [What We Found: Current AI Strengths](#what-we-found-current-ai-strengths)
3. [What We Found: Current AI Weaknesses](#what-we-found-current-ai-weaknesses)
4. [Research Methodology](#research-methodology)
5. [Decision Log: What We're Doing and Why](#decision-log-what-were-doing-and-why)
6. [Decision Log: What We're NOT Doing and Why](#decision-log-what-were-not-doing-and-why)
7. [Priority Roadmap](#priority-roadmap)
8. [Key Research Findings That Changed Our Thinking](#key-research-findings-that-changed-our-thinking)
9. [Sources](#sources)

---

## How We Got Here

### Phase 1: Deep Code Audit

We read every line of the AI integration — all 248 backend Java files, 95+ frontend TypeScript files, 12 prompt templates, 13 database migrations, 54 backend tests, 41 frontend tests, and all infrastructure configs. This wasn't a surface scan; we read the actual `OpenRouterAiProvider.java` line by line, traced every data flow through `ChunkBuilder` → `EmbeddingService` → `PineconeClient`, and examined how each of the 10 AI services assembles prompts and parses responses.

### Phase 2: Identify Gaps

From the code audit, we produced an initial list of SOTA enhancements: reranking, HyDE, streaming, agentic reasoning, predictive intelligence, fine-tuning, federated learning, multi-modal visuals, real-time proactive AI, and cross-capability reasoning chains.

### Phase 3: Research Validation

We then ran **parallel research queries** through Claude Research (web search enabled) and ChatGPT (Extended Pro for deep reasoning, Pro for targeted questions) to validate every recommendation. We specifically asked the research tools to **debunk our own suggestions** — "which of these are overhyped?" "does reducing chunks actually hurt?" "is HyDE practical or academic?" "is fine-tuning worth it for a small team?"

### Phase 4: Corrected Roadmap

The research changed our thinking significantly. Several recommendations were downgraded or removed entirely. This document captures the full decision trail.

---

## What We Found: Current AI Strengths

These are genuinely impressive and conversation-worthy as-is:

### 1. Anti-Sycophancy Directives
The `team-insight.txt` and `personal-insight.txt` prompts contain aggressive anti-flattery rules that are rare in production AI:
- *"If the data warrants 2 criticisms and 0 praise, return 2 criticisms and 0 praise"*
- *"Carry-forward streaks on important work are a PROBLEM, not 'ambitious scoping'"*
- *"Do NOT soften bad patterns with excessive praise"*

**Why this matters:** OpenAI publicly acknowledged sycophancy as a trust problem after their 2025 GPT-4o rollback. Enterprise buyers in 2025-2026 evaluate AI on trust + reliability, not raw capability. Our anti-sycophancy stance is a genuine differentiator.

### 2. Hybrid Rules + LLM Risk Detection
`RiskDetectionService` runs 5 deterministic rules first (OVERCOMMIT, UNDERCOMMIT, REPEATED_CARRY_FORWARD, BLOCKED_CRITICAL, SCOPE_VOLATILITY), then asks the LLM to find what the rules missed. The prompt explicitly says "DO NOT DUPLICATE" the rule-based signals.

**Why this matters:** Most systems do rules OR LLM. Our layered approach means deterministic signals are always fast and reliable, while the LLM adds coverage for hidden dependencies, unrealistic estimates, and concentration risk. The scope discipline is architecturally mature.

### 3. Structured Evidence Pipeline
`StructuredEvidenceService` assembles 4 evidence pillars:
1. **SQL Facts** — exact current state, distributions, compliance flags
2. **Lineage** — carry-forward chains via recursive CTEs, RCDO ancestry, scope-change timeline
3. **Semantic** — Pinecone top-10 for question-relevant context
4. **Risk Features** — completion ratios, max streaks, active signal types

The `EvidenceDrawer` frontend component renders these as collapsible panes with full transparency.

**Why this matters:** Enterprise research confirms explainable AI is not a checkbox — it's a trust mechanism buyers actively evaluate. Our 4-pillar evidence system is more sophisticated than most production AI tools.

### 4. Rich Chunk Enrichment
`ChunkBuilder` doesn't embed raw text. It constructs narratives with:
- Full RCDO ancestry paths ("Rally Cry: Win Enterprise > DO: Improve Uptime > Outcome: 99.9% SLA")
- Carry-forward lineage stories
- Linked ticket summaries with status
- Cross-team RCDO overlap notes
- Owner and team context

**Why this matters:** Most RAG systems flatten relationships. Our chunks preserve the full semantic graph. This directly improves retrieval quality for questions about strategy alignment, team collaboration, and work lineage.

### 5. Faithfulness Evaluation in Production
`FaithfulnessEvaluator` runs RAGAS-style claim decomposition asynchronously:
- 100% of high-stakes types (RISK_SIGNAL, TEAM_INSIGHT, PERSONAL_INSIGHT, RAG_QUERY)
- 10% sampling of lower-stakes types
- Scores written back to `ai_suggestion` table
- Prometheus alerts at threshold (<0.85 → warning)

**Why this matters:** Research confirms this sampling strategy is exactly what production teams recommend. Most apps have zero production evaluation.

### 6. Full Audit Trail
Every AI suggestion stores: prompt version, model version, raw prompt, raw response, parsed payload, confidence, context hash, evaluation score, latency_ms. User feedback (accept/dismiss) tracked in `ai_feedback`. This enables real A/B analysis per prompt version.

---

## What We Found: Current AI Weaknesses

### Critical Bugs

| Issue | File | Impact |
|-------|------|--------|
| **Regex-based JSON extraction** | `OpenRouterAiProvider.extractJson()` | Fragile markdown fence parsing when structured outputs are now available via OpenRouter |
| **Hard-coded confidence 0.85** | `OpenRouterAiProvider.parseResponse()` | Every response gets the same confidence regardless of content; makes confidence meaningless |
| **CommitLintService calls LLM but discards output** | `CommitLintService.java` | Pays for tokens, stores the response, but only surfaces the rules-based validation |
| **RiskDetectionService persists empty context** | `RiskDetectionService.java` | Saves `prompt = "{}"` — breaks audit trail and future eval |
| **Unbounded historical queries** | `CommitDraftAssistService.java` | `findByOwnerUserId()` loads ALL past commits; could be thousands |

### RAG Pipeline Gaps

| Gap | Current State | Impact |
|-----|--------------|--------|
| **No hybrid retrieval** | Pure vector search via Pinecone | Misses exact-match queries for RCDO labels, team names, commit titles, chess piece terms |
| **No reranking** | Top-40 Pinecone results go directly to LLM | Most of 40 chunks are noise; LLM must sort signal from irrelevant context |
| **No embedding batching** | 1 HTTP call per chunk | Slow during indexing (not critical for query path) |
| **Fixed topK=40** | Hardcoded in `SemanticQueryService` | No adaptive retrieval based on query complexity |
| **Generic insight queries** | Embeds "patterns in this team's last 4 weeks" | Same 8 historical chunks return regardless of what's actually interesting |

### Infrastructure Gaps

| Gap | Current State | Impact |
|-----|--------------|--------|
| **Single-JVM rate limiting** | In-memory token bucket in `OpenRouterAiProvider` | Does nothing in multi-instance deployment |
| **No LLM observability beyond Prometheus** | Custom `AiQualityMetrics` + Prometheus gauges | Can't trace individual requests, compare prompt versions, or run experiments |
| **No structured output mode** | Regex extraction from markdown fences | Unnecessary fragility when OpenRouter supports `json_schema` |

---

## Research Methodology

We ran 12+ parallel research queries across Claude Research and ChatGPT (Extended Pro and Pro modes), specifically targeting:

1. **RAG best practices 2025-2026** — reranking, hybrid search, chunk count optimization, HyDE
2. **Debunking our own recommendations** — "which of these are overhyped?" 
3. **Long-context LLMs vs RAG** — does Claude's 1M context window change the reranking calculus?
4. **Confidence calibration** — LLM self-assessment reliability
5. **Enterprise AI differentiation** — what actually impresses buyers
6. **Predictive planning** — statistical baselines vs ML for 12 weeks of data
7. **Agentic patterns** — ReAct in production vs hype
8. **Proactive AI fatigue** — the "boy who cried wolf" problem
9. **Streaming SSE** — Spring Boot + React gotchas
10. **Structured outputs** — OpenRouter support, validation patterns
11. **Eval/observability tooling** — Langfuse vs RAGAS vs Braintrust vs custom
12. **Fine-tuning vs prompting** — practical decision framework

Each query included full context about our tech stack, domain, and current implementation. We cross-referenced findings between Claude and ChatGPT and flagged disagreements.

---

## Decision Log: What We're Doing and Why

### P0: Fix Critical Bugs

#### Decision: Replace regex JSON parsing with OpenRouter structured outputs
- **What:** Remove `extractJson()` regex fence parsing. Use OpenRouter's `response_format.type = "json_schema"` with `require_parameters: true`.
- **Why:** Anthropic structured outputs use constrained decoding — "always valid, no retries needed for schema violations." OpenRouter supports this natively. Our current regex parsing is a legacy pattern from 2023.
- **Fallback strategy:** Jackson deserialization → Jakarta Bean Validation → 1 retry on schema failure → fail closed. Zero regex on happy path.
- **Evidence:** Both Claude Research and ChatGPT confirmed this. OpenAI reports 100% schema adherence. Anthropic docs say "no retries needed."

#### Decision: Fix empty context persistence in RiskDetectionService
- **What:** Store full `commitDataList + planData + scopeChanges` instead of `"{}"`.
- **Why:** Breaks the audit trail. Makes future eval impossible. FaithfulnessEvaluator can't score a suggestion when context is empty.

#### Decision: Fix or remove wasted LLM call in CommitLintService
- **What:** Either surface the LLM lint output or stop calling the LLM for lint.
- **Why:** Currently pays for tokens and stores the response but only shows rules-based validation to users.

#### Decision: Bound historical queries in CommitDraftAssistService
- **What:** Limit to last 12 weeks of commits, not unbounded `findByOwnerUserId()`.
- **Why:** Over months, this could serialize thousands of commits into the prompt, wasting tokens and degrading quality.

---

### P1: Hybrid Retrieval on Pinecone

#### Decision: Add BM25/sparse vectors to existing Pinecone index with RRF fusion
- **What:** Enable Pinecone's native hybrid search. Generate sparse vectors alongside dense embeddings. Fuse with Reciprocal Rank Fusion.
- **Why this is high priority:** Our corpus has many "lexical landmines" — RCDO labels, team names, chess piece terms, commit titles, risk signal names. When a manager searches "What did Alice commit to the Win Enterprise rally cry?", pure vector search may blur the exact RCDO label match. Hybrid catches it.
- **Why Pinecone native:** No migration needed. Pinecone docs recommend a single hybrid index for most use cases. Lower operational overhead than adding Elasticsearch.
- **Why RRF first:** RRF is robust because it doesn't require score calibration across BM25 and vector spaces. Move to weighted score fusion only after we have a labeled eval set.
- **Research basis:** ChatGPT Pro (hybrid search query) confirmed "hybrid usually outperforms pure vector in practice" with "small-to-moderate average improvement overall, and large improvement on the hard lexical slice." Pinecone docs explicitly support this pattern.

---

### P1: Cross-Encoder Reranking

#### Decision: Add Cohere Rerank 4 Pro (or Voyage Rerank 2.5) after hybrid retrieval
- **What:** Retrieve top-80-100 from hybrid search → rerank with cross-encoder → keep top 15-25 above relevance threshold → send to LLM with scores annotated.
- **Why NOT "cut to 5":** Research showed this is wrong as a universal rule. Claude now has 1M context GA. The right approach is "optimize relevance density, not chunk count." Our enriched chunks are high-quality, so we'll likely keep more than 5.
- **Why this is the highest-ROI RAG upgrade:** Microsoft calls hybrid + reranking "table stakes." Cross-encoders jointly score query-document pairs, which is fundamentally better than embedding cosine similarity for determining relevance to a specific question.
- **Reranker choice:** Cohere Rerank 4 Pro is the safe managed default. Voyage Rerank 2.5 is a strong challenger, especially for long docs. Jina v2 or BGE v2-m3 for self-hosted if needed.
- **Research basis:** Multiple sources confirmed. Microsoft reports "large NDCG gains." Third-party benchmarks show 15-40% precision gains from rerankers over embeddings alone.

#### Important nuance — don't over-truncate
Our original recommendation said "reduce from 40 to 5-10 reranked chunks." Research corrected this:
- *Lost in the Middle* problem still exists but is less severe with current long-context models
- The right frame is **relevance density**, not chunk count
- Retrieve broadly (80-100), rerank to promote the best, keep top 15-25 that pass a threshold
- Annotate remaining chunks with scores so the LLM can weight them

---

### P1: Streaming SSE for Key AI Surfaces

#### Decision: Add SSE streaming for RAG answers, manager summary, and commit composer
- **What:** Spring WebFlux SSE endpoints for the 3 AI surfaces with highest perceived latency. Split-stream protocol: stream answer text first, append source citations at the end.
- **Why:** Users currently see a spinner for 3-5 seconds. Streaming shows first tokens in ~200ms. Table-stakes UX in 2025-2026.
- **Why NOT for everything:** Short lint/risk outputs (<50 words) don't benefit from streaming. Regular REST is fine.
- **Gotchas identified from research:**
  - Nginx buffers by default — need `X-Accel-Buffering: no`
  - HTTP/1.1 limits SSE to 6 connections per browser+domain — use one stream per active AI job, multiplex event types
  - Citation timing: sources may not be known when first tokens arrive. Use placeholder markers, fill in later.
  - Spring MVC `SseEmitter` works for lighter traffic; WebFlux for high concurrency
- **Research basis:** Claude Research confirmed split-stream protocol is "what I'd ship in production today." ChatGPT Extended Pro rated streaming SSE as "PROVEN — real, boring production infrastructure."

---

### P2: Statistical Predictive Calibration

#### Decision: Build Bayesian completion-rate model from `user_week_fact`, NOT ML
- **What:** Use 12 weeks of `user_week_fact` data (points planned vs achieved per user per week) to compute:
  - Rolling achievement rate per user
  - Per-chess-piece calibration ("Your QUEEN estimates historically achieve 72%")
  - Carry-forward probability based on streak + chess piece + blocked status
  - Team delivery ranges ("Expected: 35-42 points based on 12-week history")
- **Why statistical baselines, not ML:**
  - 12 weeks is too little for per-user ML models — they'll overfit
  - Bayesian completion-rate models and Monte Carlo throughput simulation are the proven approaches
  - Google's Rules of ML: "Don't be afraid to launch without ML"
  - Linear ships similar forecast-style estimates (closest competitor doing this)
- **Why this is high-impact:** Transforms the tool from retrospective ("what happened") to predictive ("what will likely happen"). Let the LLM narrate the stats (which it already does in personal insights), but compute them deterministically.
- **Display as ranges:** Never show point estimates. "Expected delivery: 35-42 points" not "Expected: 38.5 points."
- **Research basis:** ChatGPT Pro provided detailed analysis. Scrum.org recommends probabilistic forecasting. Monte Carlo simulation is "the approach emphasized by Scrum.org and widely used by forecasting add-ons."

#### Key constraint: Causal inference is much harder than prediction
Research was clear: "Determining WHY work was missed is much harder than predicting THAT it will be missed." Structured data alone rarely identifies the true cause. Our approach:
1. **Predict:** "This commit has a 78% probability of being carried forward"
2. **Attribute:** "Top contributing signals: carry-forward streak, scope increase, blocker time"
3. **Confirm:** Manager validates the reason (already have `ManagerComment` for this)

We will NOT claim the AI "knows the reason" — we'll present contributing factors and let humans decide.

---

### P2: Targeted Query Rewriting

#### Decision: Implement domain-specific query normalization, NOT HyDE
- **What:** Before embedding, normalize queries:
  - Expand acronyms: "CF" → "carry-forward", "RCDO" → "Rally Cry / Defining Objective / Outcome"
  - Normalize chess terms: "king commits" → "KING chess piece commits"
  - Decompose multi-hop: "What RCDOs are under-invested and who owns them?" → two subqueries
  - Conversational follow-ups → standalone questions (for future multi-turn)
- **Why NOT HyDE:** HyDE adds an extra LLM generation step before every retrieval. In systems with decent hybrid retrieval + reranking, HyDE "usually loses on operational efficiency." It's a rescue tool for specific failure modes, not a universal upgrade.
- **Why targeted rewriting IS high ROI:** Microsoft's Azure AI Search reported +4 NDCG@3 from query rewriting, with much larger gains when paired with reranking.
- **Research basis:** Both Claude and ChatGPT confirmed query rewriting is "one of the most proven levers." HyDE is "overhyped as a default toggle, but valid as a targeted fix."

---

### P2: Langfuse Integration for LLM Observability

#### Decision: Add Langfuse alongside existing Prometheus metrics
- **What:** Integrate Langfuse for trace-level LLM inspection, prompt version comparison, golden-set replay, and experiment workflows. Keep Prometheus for SRE-style metrics (latency, tokens, availability, faithfulness gauges).
- **Why Langfuse specifically:**
  - Winning the OSS default platform slot for small teams
  - Tracing, prompt management, datasets, experiments, model-based evals, self-hosting
  - Integrates with OpenTelemetry
  - Research ranked it above TruLens, and ahead of Braintrust for OSS/self-host preference
- **Why not just Prometheus:** Prometheus is weak at prompt version comparison, trace inspection, datasets/experiments, human annotation, and linking bad outputs back to exact prompts. These are exactly the things we need to safely iterate on prompts.
- **Relationship to existing eval:** Keep `FaithfulnessEvaluator` and RAGAS-style scoring. Langfuse adds the wrapper — trace every request, compare prompt versions, replay golden sets before deploying changes.
- **Research basis:** ChatGPT Pro provided detailed stack comparison. "Langfuse is winning the open-source default platform slot." "Do not try to build the whole LLM observability stack from raw Prometheus/Grafana unless observability is already a core competency."

---

### P2: Evidence-Based Confidence Tiers

#### Decision: Replace hard-coded 0.85 with structured evidence tiers, NOT LLM self-assessment
- **What:** Compute confidence from structured signals:
  - **High:** SQL facts + lineage + semantic matches all converge; multiple high-score Pinecone chunks (>0.8)
  - **Medium:** Some evidence sources agree; semantic matches moderate (0.5-0.8)
  - **Low:** Sparse evidence; few relevant chunks; conflicting signals
  - **Insufficient:** No relevant chunks found; no SQL facts available
- **Display as tiers, not decimals:** Show "High confidence" with evidence count, not "0.87."
- **Why NOT LLM self-reported confidence:** Research is clear — "LLMs are poorly calibrated at self-assessing confidence." The proven approach is "sample 3-5 times and measure semantic consistency" or use structural signals.
- **Why our evidence pipeline is the right foundation:** `StructuredEvidenceService` already computes 4 evidence pillars. We can derive confidence from evidence availability and agreement without asking the LLM.
- **Research basis:** Claude Research recommended "pipeline that samples consistency, calibrates with post-hoc layer, routes low-confidence before reaching users, displays as qualitative tiers + source attribution."

---

### P3: Typed Workflow Orchestration (Scoped Down)

#### Decision: Wire existing services into typed DAGs, NOT autonomous ReAct agents
- **What:** Connect existing services with explicit handoffs:
  - Risk detection finds OVERCOMMIT → WhatIfService computes impact of removing lowest-priority commit → format as specific recommendation: "Removing 'Update wiki' (PAWN, 3pts) resolves overcommit and barely changes RCDO coverage"
  - Reconcile assist sees carry-forward streak → query personal insight history → generate: "This is the 3rd week carrying forward 'Auth refactor'. Your historical QUEEN achievement rate is 72%. Consider splitting into two ROOKs."
- **Why NOT ReAct / autonomous agents:**
  - Anthropic: "workflows are predefined code paths; agents dynamically direct tool usage" — workflows are almost always right for enterprise SaaS
  - 68% of production agents execute ≤10 steps before human intervention
  - Agents bring higher cost and compounding error risk
  - Our domain has clear, predictable workflows — lock, reconcile, carry-forward — not open-ended exploration
- **Why this still matters:** Cross-capability recommendations are the difference between "10 AI features" and "one intelligent planning assistant." But they should be deterministic pipelines with typed inputs/outputs, not autonomous reasoning.
- **Research basis:** ChatGPT Extended Pro: "reliable enough for production only when clipped hard. Use bounded chains, typed inputs/outputs, deterministic gates."

---

## Decision Log: What We're NOT Doing and Why

### ❌ Removed: Federated Learning Across Teams

**Original idea:** Learn estimation patterns across teams without sharing individual data.

**Why we removed it:** Research unanimously says this is overengineering for a weekly planning tool.
- FL is designed for settings where organizations can't share data AND need a jointly trained model
- Gradient updates can leak training data even with averaging
- Our problems (permissions, ranking, summarization) are not distributed optimization problems
- If we want cross-team patterns, we compute anonymized aggregate statistics server-side

**ChatGPT Extended Pro:** "Unless you have a hard regulatory/contractual boundary that prevents raw-data sharing AND a genuinely important jointly trained model, FL is not the thing to build. For planning software, I would treat this as a red flag."

---

### ❌ Removed: Fine-Tuned Domain Model

**Original idea:** Fine-tune a smaller model on our domain vocabulary (chess pieces, RCDO, carry-forward).

**Why we removed it (for now):**
- Anthropic's API does not currently offer fine-tuning (only Claude 3 Haiku via Bedrock)
- "Fine-tuning is for consistent behavior/formatting, not domain knowledge"
- For 1-3 engineers, "usually resume-driven too early"
- Better prompts + better evals + better retrieval + structured outputs will outperform fine-tuning for our use case
- We should revisit only if acceptance rates plateau despite good prompting and retrieval

**ChatGPT Extended Pro:** "The biggest mistake I see teams make is treating fine-tuning as a solution to a prompting problem they haven't actually tried to solve yet."

---

### ❌ Removed: HyDE as Default

**Original idea:** Generate hypothetical ideal answers, embed them, use for retrieval.

**Why we removed it as a default:**
- Adds an extra LLM call before every retrieval (latency + cost)
- In systems with hybrid retrieval + reranking, HyDE usually doesn't produce user-visible gains
- It's a rescue tool for specific failure modes, not a universal upgrade

**What we're doing instead:** Targeted query rewriting (acronym expansion, term normalization, subquery decomposition). If we later identify specific query patterns where retrieval recall is poor, we can gate HyDE behind a failure detector for those cases only.

---

### ❌ Downgraded: Event-Driven AI on Every Mutation

**Original idea:** Recompute all AI capabilities on every data change, push updates via WebSocket.

**Why we downgraded it:**
- Pure "recompute on every data change" is too expensive and too noisy
- The "AI boy who cried wolf" problem is real — 20% false alarm rate → users ignore half of true alarms
- Users calibrate on precision and consequence, not recall
- Best products (Linear, Notion, Copilot) use proactive AI only in triage-like contexts

**What we're keeping:** Our current event triggers are already right:
- On lock → risk detection + lint + personal insights ✓
- On commit save → debounced lint ✓  
- Daily batch → team insights + index sweep ✓
- `useDismissMemory` hook for suppressing dismissed suggestions ✓

We will NOT add more event triggers. The heuristic from research: "interrupt only when `expected user value × confidence × urgency > interruption cost × current workload`."

---

### ❌ Downgraded: Multi-Modal Planning Intelligence

**Original idea:** AI-generated charts and visualizations with annotations.

**Why we downgraded it:**
- Chart QA benchmarks show large human-model gap: humans 93%, best model 63%
- AI-generated chart captions are frequently factually inaccurate
- The shippable version is "deterministic chart rendering from AI-drafted specs" — but that's just regular charting with an LLM writing the spec

**What we're doing instead:** We already have Tremor charts (added in recent commits). If we want AI annotations, we can have the LLM produce structured annotations (JSON) and render them deterministically on our existing charts. Not a priority.

---

## Priority Roadmap

### P0 — Fix Foundations (1-2 days)
| Task | Impact | Effort |
|------|--------|--------|
| Replace regex JSON parsing with OpenRouter structured outputs | Eliminates fragile parsing | Low |
| Fix empty context persistence in RiskDetectionService | Restores audit trail | Low |
| Fix/remove wasted LLM call in CommitLintService | Saves tokens + reduces confusion | Low |
| Bound historical queries (12-week limit) | Prevents prompt bloat | Low |

### P1 — RAG Quality + UX (1-2 weeks)
| Task | Impact | Effort |
|------|--------|--------|
| Hybrid retrieval on Pinecone (BM25 + vector, RRF) | Fixes exact-match search failures | Medium |
| Cross-encoder reranking (Cohere/Voyage) | Highest-ROI RAG quality improvement | Medium |
| Streaming SSE for RAG, manager summary, commit composer | Perceived latency 3-5s → 200ms | Medium |

### P2 — Intelligence Layer (2-3 weeks)
| Task | Impact | Effort |
|------|--------|--------|
| Statistical predictive calibration from `user_week_fact` | Transforms retrospective → predictive | Medium |
| Targeted query rewriting (acronym expansion, decomposition) | Improves retrieval precision | Low-Medium |
| Langfuse integration for LLM observability | Safe prompt iteration | Medium |
| Evidence-based confidence tiers | Meaningful trust signals | Medium |

### P3 — System Intelligence (2-4 weeks)
| Task | Impact | Effort |
|------|--------|--------|
| Typed workflow orchestration (risk→what-if→recommendation) | "One intelligent assistant" | High |
| Shared per-user-per-week context assembly | Eliminates duplicate queries | High |

### P4 — Future (When P2 calibration data is available)
| Task | Impact | Effort |
|------|--------|--------|
| AI-powered plan optimization (constraint-based using calibration) | "AI designs your week" | Very High |

---

## Key Research Findings That Changed Our Thinking

### 1. "Less is more" for RAG chunks is wrong as a universal rule
**Before:** "Cut from 40 chunks to 5-10 reranked."  
**After:** "Optimize relevance density, not chunk count." Claude has 1M context GA. Retrieve broadly, rerank to promote the best, keep top 15-25 above threshold. Don't arbitrarily truncate.

### 2. LLM self-reported confidence is unreliable
**Before:** "Use dynamic confidence scoring from the LLM."  
**After:** LLMs are poorly calibrated at self-assessment. Use structural signals (evidence agreement, chunk scores, SQL fact availability) and display as qualitative tiers.

### 3. HyDE is overhyped as a default
**Before:** "Add HyDE for better retrieval."  
**After:** HyDE adds latency without measurable gain in systems with hybrid retrieval + reranking. Use targeted query rewriting instead.

### 4. Autonomous agents are wrong for enterprise SaaS
**Before:** "Build agentic multi-step reasoning with ReAct."  
**After:** Anthropic says workflows beat agents for predictable enterprise tasks. Use typed DAGs with deterministic gates, not open-ended reasoning.

### 5. Fine-tuning is premature before prompt engineering is exhausted
**Before:** "Fine-tune a domain model as P5."  
**After:** Removed entirely. Anthropic doesn't offer general fine-tuning. Better prompts + structured outputs + retrieval improvements will outperform fine-tuning for our use case.

### 6. Federated learning is overengineering
**Before:** "Privacy-preserving cross-team learning as P5."  
**After:** Removed entirely. Our problems aren't distributed optimization problems. Compute anonymized aggregates server-side instead.

### 7. Proactive AI needs precision, not frequency
**Before:** "Push intelligence on every mutation."  
**After:** "Boy who cried wolf" problem is well-documented. 20% false alarm rate → users ignore half of true alarms. Our current event triggers (on lock, daily batch) are already the right granularity.

### 8. Hybrid retrieval is MORE important than we thought
**Before:** Listed as a nice-to-have.  
**After:** Elevated to P1. Our corpus has many lexical landmines (RCDO labels, chess piece terms, team names) where pure vector search fails. Pinecone supports hybrid natively.

### 9. OpenRouter now supports structured outputs
**Before:** Assumed we need regex fence parsing.  
**After:** OpenRouter documents `response_format.type = "json_schema"` with `require_parameters: true`. Our entire `extractJson()` method is legacy.

### 10. Langfuse is the right observability layer
**Before:** Focused on Prometheus/Grafana.  
**After:** Prometheus is fine for SRE metrics (we keep it). But Langfuse adds trace inspection, prompt experiments, golden-set replay, and comparison workflows that Prometheus can't do. Research calls it "the winning OSS default for small teams."

---

## Sources

### Research Tools Used
- **Claude Research** (Anthropic) — web search enabled, research mode. Used for: RAG best practices, confidence calibration, streaming patterns, enterprise differentiation, Pinecone hybrid search, embedding batching, prompt engineering SOTA.
- **ChatGPT Extended Pro** (OpenAI) — maximum reasoning depth. Used for: production RAG evaluation, agentic patterns debunking, proactive AI fatigue research, enterprise technique ratings.
- **ChatGPT Pro** (OpenAI) — research-grade analysis. Used for: predictive planning, hybrid retrieval, eval/observability tooling comparison, structured output reliability.

### Key External Sources Referenced by Research
- Anthropic: Building Effective Agents (2025), Context Engineering for AI Agents, Claude Structured Outputs docs
- OpenAI: Optimizing LLM Accuracy guide, Structured Outputs docs, Sycophancy in GPT-4o post-mortem
- Microsoft: Azure AI Search RAG guidance, Semantic Ranker benchmarks, Query Rewriting + Reranking blog
- Scrum.org: Probabilistic Forecasting, Monte Carlo in Scrum
- Pinecone: Hybrid Search docs, dense+sparse index guidance
- Cohere: Rerank 4.0 changelog, RAG Streaming/Citations docs
- Langfuse: LLM Observability docs, Evaluation overview
- Linear: Project Graph docs, Triage Intelligence docs
- ACL 2025: LLM-as-judge reliability study across 20 NLP tasks
- arXiv: Lost in the Middle, NoLiMa, LongBench v2, ChatQA 2, HyDE original paper
- BMJ: Sample size requirements for prediction models
- ScienceDirect: Alert fatigue and cry-wolf effect research

---

*This document is a living record. As we implement priorities and gather data, decisions may be revised based on measured outcomes, not assumptions.*
