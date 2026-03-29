# Architecture Decisions: Approaches, Thoughts & Tradeoffs

> **Date:** 2026-03-29  
> **Context:** Comprehensive record of every major architecture decision in the Weekly Commit Module — what we considered, what we chose, why we chose it, and what we'd do differently with more time, more people, or a bigger budget. Every recommendation was validated against current production evidence, academic research, and real-world enterprise practice using parallel research through Claude Research (web-search-enabled) and ChatGPT (Extended Pro for deep reasoning, Pro for targeted questions, Deep Research for surveys).

---

## Table of Contents

1. [How We Made Decisions](#how-we-made-decisions)
2. [Retrieval Architecture: How We Handle Knowledge at Scale](#retrieval-architecture-how-we-handle-knowledge-at-scale)
3. [Hybrid Retrieval: Why Pure Vector Search Isn't Enough](#hybrid-retrieval-why-pure-vector-search-isnt-enough)
4. [Reranking: The Highest-ROI RAG Upgrade](#reranking-the-highest-roi-rag-upgrade)
5. [Query Understanding: Rewriting, HyDE, and What We Actually Ship](#query-understanding-rewriting-hyde-and-what-we-actually-ship)
6. [Structured Outputs & JSON Validation: No More Regex](#structured-outputs--json-validation-no-more-regex)
7. [Streaming Architecture: SSE, WebSockets, and Real-Time UX](#streaming-architecture-sse-websockets-and-real-time-ux)
8. [Agent Architecture: Workflows vs Autonomous Agents](#agent-architecture-workflows-vs-autonomous-agents)
9. [Proactive AI: When to Speak and When to Stay Quiet](#proactive-ai-when-to-speak-and-when-to-stay-quiet)
10. [Anti-Sycophancy: Honest AI in Enterprise Software](#anti-sycophancy-honest-ai-in-enterprise-software)
11. [Predictive Intelligence: Statistical Baselines Before ML](#predictive-intelligence-statistical-baselines-before-ml)
12. [Evaluation & Observability: Measuring What Matters](#evaluation--observability-measuring-what-matters)
13. [Context Engineering: What Goes Into the Window](#context-engineering-what-goes-into-the-window)
14. [Shared Intelligence Context vs Feature Silos](#shared-intelligence-context-vs-feature-silos)
15. [Fine-Tuning, Federated Learning, and Other Things We're Not Doing](#fine-tuning-federated-learning-and-other-things-were-not-doing)
16. [Multi-Modal Intelligence: Charts, Visuals, and What's Actually Ready](#multi-modal-intelligence-charts-visuals-and-whats-actually-ready)
17. [Cross-Cutting: Infrastructure, Deployment & Resilience](#cross-cutting-infrastructure-deployment--resilience)
18. [The "Go Big" Roadmap: What We'd Build With More Time](#the-go-big-roadmap-what-wed-build-with-more-time)
19. [Sources & Research Methodology](#sources--research-methodology)

---

## How We Made Decisions

### Phase 1: Deep Code Audit
We read every line of the AI integration — all 263 backend Java files, 95+ frontend TypeScript files, 14 prompt templates, 13 database migrations, 63 backend tests, 49 frontend tests, and all infrastructure configs. This wasn't a surface scan; we traced every data flow through `ChunkBuilder` → `EmbeddingService` → `PineconeClient`, and examined how each of the 10 AI services assembles prompts and parses responses.

### Phase 2: Initial Enhancement List
From the audit, we produced a full list of possible enhancements: reranking, HyDE, streaming, agentic reasoning, predictive intelligence, fine-tuning, federated learning, multi-modal visuals, real-time proactive AI, cross-capability reasoning chains, knowledge graphs, multi-agent systems, and more.

### Phase 3: Research Validation — And Deliberate Self-Debunking
We ran **12+ parallel research queries** across Claude Research and ChatGPT, specifically targeting both validation and falsification. We asked: *"Which of these are overhyped?" "Does reducing chunks actually hurt?" "Is HyDE practical or academic?" "Is fine-tuning worth it for a 1–3 engineer team?"*

### Phase 4: Corrected Roadmap
The research changed our thinking significantly. Several recommendations were downgraded, removed, or fundamentally reframed. This document captures the full decision trail for every major architectural topic.

---

## Retrieval Architecture: How We Handle Knowledge at Scale

### The Problem

Our corpus contains weekly plans, commits, RCDO hierarchies, carry-forward lineage, risk signals, team insights, and personal insights — all accumulating over time. Even a moderately-sized organization will produce a corpus that dwarfs any single context window within months. How do we let users and AI features find the right information?

### Approaches We Considered

| Approach | Summary | Verdict for Us |
|----------|---------|----------------|
| **Naive long context** | Stuff everything into Claude's 1M window | Too expensive, latency >2min at scale, lost-in-the-middle degradation |
| **Basic RAG (embed & retrieve)** | Embed chunks, cosine similarity, top-K to LLM | Our starting point — works, but misses exact-match queries |
| **Hybrid RAG** | BM25 + vector search + fusion | **Our choice** — catches both semantic and lexical queries |
| **Agentic RAG** | LLM orchestrates its own retrieval iteratively | Overkill for our query patterns; saves for future complex surfaces |
| **GraphRAG** | Knowledge graph + community detection + hierarchical summaries | RCDO hierarchy is already a graph in PostgreSQL; adding Neo4j adds complexity without proven marginal gain for our query mix |
| **Cache-Augmented Generation** | Pre-load all knowledge into pre-computed KV cache | Only works for static, bounded knowledge — our data changes weekly |
| **Multi-agent retrieval** | Multiple agents explore different aspects in parallel | Token cost ~15x; debugging is hard; not justified for our scale |

### What We Chose: Layered Hybrid RAG Pipeline

```
Question → Query Normalization → Embed → Hybrid Search (BM25 + Vector, RRF)
  → Cross-Encoder Rerank → Top 15-25 above threshold → LLM Answer + Citations
```

**Why this specific stack:**

1. **The 2025-2026 production playbook has converged.** Microsoft calls hybrid retrieval plus reranking "table stakes" based on billions of daily queries. The stack that most reliably improves quality is: good extraction/chunking/metadata, hybrid retrieval, selective query rewriting, and a second-stage reranker.

2. **Our corpus has lexical landmines.** RCDO labels ("Win Enterprise > Improve Uptime > 99.9% SLA"), chess piece terms (KING, QUEEN, ROOK, PAWN), team names, commit titles, risk signal names — pure vector search blurs these exact matches. Hybrid catches them.

3. **Our chunks are already unusually rich.** `ChunkBuilder` constructs narratives with full RCDO ancestry paths, carry-forward lineage stories, linked ticket summaries, and cross-team overlap notes. This is closer to "parent-child chunking" than naive text splitting.

4. **We didn't need to migrate.** Pinecone supports hybrid search natively. Adding sparse vectors to our existing index was lower-risk than migrating to Elasticsearch, Weaviate, or Qdrant.

### What We'd Do at Scale

- **Add a query router** that classifies queries and routes simple factual lookups to BM25-only (fast, cheap), relationship queries to our SQL evidence pipeline, and only complex semantic queries to full hybrid RAG. Research shows this reduces costs by ~40% and latency by ~35%.
- **Consider GraphRAG for cross-RCDO analysis.** If users frequently ask questions like "Which teams are contributing to the same Rally Cry across different Defining Objectives?" — that's a graph traversal question. Microsoft's GraphRAG shows up to 35% accuracy improvement on relationship-heavy queries. LightRAG achieves 70-90% of full GraphRAG quality at 1/100th the cost.
- **Add Contextual Retrieval headers.** Anthropic's technique of prepending document-level context to each chunk cuts retrieval failures by 49%. For us, this would mean adding "This chunk is from [user]'s week [N] plan, linked to RCDO [path]" to every chunk at index time.
- **Evaluate multi-agent retrieval** for the manager summary use case, where an orchestrator agent could spawn parallel sub-agents to investigate each team member's week. Anthropic's production research system reports 90.2% improvement over single-agent on breadth-first queries.

### Key Research That Informed This

- Recursive 512-token splitting achieved 69% accuracy vs 54% for semantic chunking (Feb 2026 benchmark)
- Chroma identified a "context cliff around 2,500 tokens" where response quality drops
- Voyage AI's MoE-architecture Voyage 4 outperforms OpenAI embeddings by 14% on NDCG@10
- IBM found three-way retrieval (BM25 + dense vectors + SPLADE sparse vectors) is optimal
- Teams adding hybrid search report double-digit gains in relevance without sacrificing latency

---

## Hybrid Retrieval: Why Pure Vector Search Isn't Enough

### The Problem

Our v1 RAG pipeline uses pure vector search via Pinecone (top-40 cosine similarity). When a manager searches "What did Alice commit to the Win Enterprise rally cry?", vector search understands the intent but may blur the exact RCDO label match. When someone searches for a specific commit SHA, team name, or chess piece term, dense embeddings are fundamentally the wrong retrieval primitive.

### Approaches We Considered

| Approach | Pros | Cons | Verdict |
|----------|------|------|---------|
| **Pinecone hybrid (single index)** | No migration, native sparse+dense, simple ops | Pinecone's BM25 is less tunable than Elasticsearch | **Our choice** |
| **Separate BM25 service + Pinecone** | Full control over both | Operational overhead, manual merge/dedup | Overengineering for now |
| **Elasticsearch migration** | Rich lexical tuning, filters, aggregations, mature ecosystem | Migration risk, new ops surface, overkill for our scale | Future option if search becomes a first-class product feature |
| **Weaviate** | Native hybrid with `alpha` tuning, Relative Score Fusion default | Migration from Pinecone, new vendor relationship | Viable but not worth the switch cost |
| **Qdrant** | Rust-based speed, native hybrid, good metadata filtering | Same migration concern | Same as Weaviate |

### What We Chose: Pinecone-Native Hybrid with RRF

We added sparse vectors alongside dense embeddings in our existing Pinecone index, fused with Reciprocal Rank Fusion (RRF): `score = Σ 1/(k + rank_i)` with k=60.

**Why RRF first:** RRF is robust because it doesn't require score calibration across BM25 and vector spaces. It works as a plug-in late-fusion baseline. Weighted linear fusion can beat RRF — Elasticsearch added a linear retriever specifically because it's easier to optimize — but it requires a labeled eval set and decent score normalization. We'll move to weighted score fusion only after we have that eval set.

**Why not migrate:** Pinecone docs explicitly recommend a single hybrid index for most use cases because it reduces operational overhead. Pinecone only recommends separate dense and sparse indexes when you need sparse-only queries, independent reranking, or independent lifecycle management — none of which apply to us.

### What We'd Do at Scale

- **Migrate to Elasticsearch** if search relevance engineering, lexical control, and filters/aggregations became first-class product requirements. Elastic's new linear retriever and RRF retriever both support sophisticated hybrid fusion.
- **Move to weighted score fusion** once we have a labeled eval set of 200+ judged queries. Score-based fusion preserves magnitude and lets us tune how much lexical vs semantic evidence matters per query type.
- **Add SPLADE sparse vectors** for the best of both worlds — learned sparse representations that capture semantic intent while remaining interpretable and exact-match-aware. IBM research found three-way retrieval (BM25 + dense + SPLADE) is optimal.

---

## Reranking: The Highest-ROI RAG Upgrade

### The Problem

Our v1 retrieves top-40 from Pinecone and sends them all to the LLM. Most of those 40 chunks are noise. The LLM has to sort signal from irrelevant context, which wastes tokens and degrades answer quality.

### Approaches We Considered

| Approach | Accuracy Gain | Latency Cost | Operational Cost | Verdict |
|----------|---------------|--------------|------------------|---------|
| **No reranking (current)** | Baseline | None | None | Insufficient for production quality |
| **Cohere Rerank 4 Pro** | 15-40% precision gains | ~120ms | Managed API, per-query pricing | **Our choice for default** |
| **Voyage Rerank 2.5** | Comparable, possibly better on long docs | ~120ms | Strong challenger to Cohere | Alternative we'd test |
| **Jina Reranker v2** | Good, multilingual | Self-hostable | Best for privacy-sensitive deployments | Future option |
| **BGE-reranker-v2-m3** | Strong open-source | Self-hostable | Best budget option | Future option |
| **FlashRank** | Moderate | Very low latency | Good for latency-sensitive workloads | Not needed at our scale |

### What We Chose: Cross-Encoder Reranking with Score-Based Filtering

Retrieve top-80-100 from hybrid search → rerank with cross-encoder → keep top 15-25 above relevance threshold → send to LLM with scores annotated.

**Why NOT "cut to 5-10":** This is one of the most important findings from our research. The common advice to "reduce RAG context from 40 chunks to 5-10 reranked chunks" is **wrong as a universal rule.**

- Claude Sonnet 4.6 and Opus 4.6 have 1M context GA as of March 2026.
- Databricks found that retrieving more documents *can* improve performance, but most models eventually peak and then get worse as context grows.
- *ChatQA 2* found that RAG with top-20/top-30 chunks can beat full long-context ingestion.
- The *Lost in the Middle* problem still exists but is less severe with current long-context models.

The right frame is **relevance density**, not chunk count. Less irrelevant context is more; more relevant context can still be better. We optimize to a relevance threshold, not an arbitrary K.

**Why cross-encoder specifically:** Cross-encoders jointly score query-document pairs, which is fundamentally more accurate than embedding cosine similarity. An MIT study measured +33-40% precision improvement for only +120ms latency. Microsoft calls this "the highest-ROI optimization once basic retrieval works."

**Critical caveat from practitioners:** "Don't rerank bad retrieval — fix retrieval first." A reranker can only reorder what was already found. That's why we fixed hybrid retrieval (P1) before adding reranking.

### What We'd Do at Scale

- **A/B test Cohere vs Voyage** on our actual query distribution. Voyage reports improvement over Cohere 3.5 on their 93-dataset suite, but vendor benchmarks should be treated as directional, not ground truth.
- **Self-host a reranker** (Jina v2 or BGE v2-m3) if API costs become significant or if we need to run reranking in a VPC for data residency.
- **Add ColBERT as a late-interaction reranker** for maximum quality. IBM research found the optimal pipeline is BM25 + dense vectors + SPLADE, with ColBERT as the reranker on top.

---

## Query Understanding: Rewriting, HyDE, and What We Actually Ship

### The Problem

User queries are messy. They use acronyms ("CF" for carry-forward), domain jargon ("king commits"), conversational follow-ups ("what about last week?"), and multi-hop questions ("What RCDOs are under-invested and who owns them?"). The retriever needs clean, specific queries to find the right chunks.

### Approaches We Considered

| Approach | Latency | Cost | Quality Gain | Verdict |
|----------|---------|------|--------------|---------|
| **No rewriting** | None | None | Baseline | Insufficient for domain-specific vocabulary |
| **Domain-specific normalization** | ~5ms (rule-based) | Free | High for our domain | **Our choice** |
| **LLM-based query rewriting** | +200-500ms | Per-query LLM call | Moderate | Future for hard cases |
| **HyDE** | +500-2000ms | Full generation + embedding | Variable | **Rejected as default** |
| **Multi-query expansion** | +200ms per variant | Multiple retrievals | Helps recall, adds noise | **Rejected as default** |
| **Subquery decomposition** | +300ms | Per-query LLM call | High for multi-hop | **Selective use only** |

### What We Chose: Domain-Specific Normalization + Selective Decomposition

Before embedding, we normalize queries:
- Expand acronyms: "CF" → "carry-forward", "RCDO" → "Rally Cry / Defining Objective / Outcome"
- Normalize chess terms: "king commits" → "KING chess piece commits"
- Detect multi-hop questions and decompose into subqueries
- Rewrite conversational follow-ups into standalone questions (for future multi-turn)

### Why NOT HyDE

HyDE was one of our most-researched topics because it appears in so many RAG recommendation lists. Our conclusion: **HyDE is overhyped as a general-purpose upgrade, but valid as a targeted rescue tool.**

The case against HyDE as a default:
- Adds an extra LLM generation step before every retrieval (latency + cost)
- The original paper showed strong gains over zero-shot dense retrieval — but that's a 2022 baseline, not a 2026 system with hybrid retrieval + reranking
- In systems where hybrid retrieval + reranking already find the right candidates, HyDE usually loses on operational efficiency
- Retrieval gains don't automatically become end-to-end answer gains — some RAG evaluations show generation can wash out retrieval improvements

The case for HyDE in specific cases:
- When recall is genuinely poor on a specific query class
- When queries are very short or underspecified
- When you're doing zero-shot dense retrieval on a specialized domain

**Our approach:** Targeted query rewriting first. If we later identify specific query patterns where retrieval recall is poor despite hybrid + reranking, we'll gate HyDE behind a failure detector for those cases only.

Microsoft's Azure AI Search reported +4 NDCG@3 from query rewriting, with much larger gains when paired with reranking. NVIDIA's RAG Blueprint enables query decomposition only as an advanced option and explicitly says not to use it for simple factual queries. That's exactly the production pattern: rewrite aggressively only when the query type needs it.

### What We'd Do at Scale

- **Add intent classification** to route queries to the cheapest appropriate method. Simple factual queries go to BM25-only; relationship queries go to SQL evidence; complex semantic queries go to full hybrid RAG. Adaline Labs reports this reduces costs by ~40%.
- **Gate HyDE behind a recall monitor.** If a query class consistently retrieves low-score chunks, automatically switch that class to HyDE and measure whether end-to-end answer quality improves.
- **Add step-back reformulation** for queries that are too specific. Microsoft's guidance includes this as an advanced technique: abstract the query to a higher level, retrieve broader context, then narrow down.

---

## Structured Outputs & JSON Validation: No More Regex

### The Problem

Our `OpenRouterAiProvider.extractJson()` uses regex to parse JSON from markdown code fences. This is a 2023-era pattern that's now unnecessary, fragile, and a maintenance liability.

### The State of Structured Outputs in 2026

We researched this exhaustively across all three providers we interact with:

**Anthropic Claude:** Structured Outputs use constrained decoding, described as "always valid" with "no retries needed for schema violations." For syntax-level JSON and schema-shape adherence, this is treated as guaranteed on supported models/modes. This eliminates regex parsing of markdown fences as the normal path.

**OpenAI:** Official claim is strongest here — 100% schema adherence on their internal evals for `gpt-4o-2024-08-06` with Structured Outputs. That's a vendor eval, not a universal production SLA, but it's reliable enough that regex parsing should not be your default path.

**OpenRouter (our provider):** Now documents `response_format.type = "json_schema"` directly. Support is model-dependent — we need `require_parameters: true` to only route to providers that support it. OpenRouter also offers a Response Healing plugin for non-streaming requests, but that exists precisely because not every path is clean.

### What We Chose: Provider-Native Structured Outputs + Layered Validation

**Primary path:**
1. Request provider-native structured output via OpenRouter's `json_schema` mode with `require_parameters: true`
2. Zero regex on the happy path

**On failure (layered):**
1. Jackson deserialization into Java `record` or DTO
2. Jakarta Bean Validation (`@NotNull`, `@Size`, `@Pattern`, enums, custom validators)
3. If schema/syntax failure: one targeted retry with validation error summary
4. If still invalid: fail closed for high-stakes actions

**Why we still validate even with structured outputs:**
- Structured outputs eliminate *syntax* problems, not *semantic* problems. A field can be the wrong value while remaining perfectly schema-valid.
- Provider/proxy mismatches can occur depending on model routing
- Domain rules beyond JSON Schema (e.g., "chess piece must be valid for this estimate range") need application-level validation
- Insulates against regressions or model swaps

**Spring Boot validation stack:**
- **Jackson** for JSON binding to records/DTOs
- **Jakarta Bean Validation / Hibernate Validator** for domain constraints
- **`networknt/json-schema-validator`** when we need strict schema validation independent of the provider (supports Draft V4 through 2020-12)
- Optional: **Spring AI `BeanOutputConverter`** if we adopt Spring AI abstractions later

### What We'd Do at Scale

- **Add JSON Schema as a cross-service contract.** If other services consume our AI outputs, validate against a shared JSON Schema using `networknt/json-schema-validator` at service boundaries.
- **Per-model routing policies.** Not all models support structured outputs equally. Build a model capability registry that gates structured output requests to known-supported models only.
- **Semantic validation via LLM-as-judge.** For high-stakes outputs (risk signals, team insights), run a cheap model as a validator to check whether the content makes sense given the evidence, not just whether the JSON is valid.

---

## Streaming Architecture: SSE, WebSockets, and Real-Time UX

### The Problem

Users currently see a spinner for 3-5 seconds on AI-heavy surfaces. For RAG answers, manager summaries, and commit composition, this feels slow. Streaming shows first tokens in ~200ms, transforming the perceived experience.

### Approaches We Considered

| Transport | Best When | Our Verdict |
|-----------|-----------|-------------|
| **SSE (Server-Sent Events)** | Server → client streaming, browser-native, simple | **Our default** |
| **WebSockets** | Bidirectional, many tool-call round trips, sub-100ms | Overkill for our patterns |
| **Long polling** | Legacy environments, coarse job status | Too weak for AI UX |
| **WebRTC** | Voice/video, ultra-low latency | Not applicable |

### What We Chose: Spring WebFlux SSE for Three Key Surfaces

We add SSE streaming for RAG answers, manager summary, and commit composer — the three surfaces with highest perceived latency. Short outputs (<50 words) like lint results and risk flags stay on regular REST.

**Why SSE, not WebSockets:**
- OpenAI's streaming guide uses SSE as the default transport. Vercel AI SDK 5 made SSE its standard.
- SSE is browser-native via `EventSource`, requires no library, and is easier to debug.
- Our interactions are server-push (model generates tokens → client renders). We don't need bidirectional.
- OpenAI's WebSocket mode is aimed at 20+ tool-call workflows — we're nowhere near that.

**Split-stream protocol:** Stream answer text tokens first via `delta` events. Append source citations as a `citations` event at the end. The client renders text progressively and fills in citation markers when they arrive. This matches how Cohere handles streaming RAG — their `accurate` mode aligns citations after full generation.

**Production gotchas we identified from research:**

| Gotcha | Solution |
|--------|----------|
| Nginx buffers upstream responses by default | `X-Accel-Buffering: no` header or `proxy_buffering off` |
| HTTP/1.1 limits SSE to 6 connections per browser+domain | One stream per active AI job; multiplex event types (`delta`, `citation`, `tool`, `done`, `error`) |
| Spring MVC `SseEmitter` blocks individual writes | Use WebFlux for high concurrency; `SseEmitter` is acceptable for lighter traffic |
| Browsers auto-reconnect SSE by default | Handle `id` and `retry` fields; prevent duplicate output on reconnect |
| Streaming makes moderation harder | OpenAI warns partial completions are harder to evaluate; we run faithfulness eval on the completed response, not the stream |
| Disconnect detection is hard on Servlet stack | Write periodic heartbeat comments as keep-alive |

### What We'd Do at Scale

- **WebSocket mode for complex multi-tool workflows.** If we build the what-if planner that iteratively explores scenarios (retrieve → compute → retrieve more → recompute), WebSockets would reduce round-trip overhead. OpenAI reports ~40% faster end-to-end with 20+ tool calls.
- **Semantic SSE event types.** Beyond `delta`/`citation`/`done`/`error`, add typed events like `evidence_found`, `risk_detected`, `tool_called` so the UI can progressively render structured investigation traces.
- **Prompt caching for streaming.** Anthropic's explicit caching reduces costs by 90% and latency by up to 85%. A 100K-token book drops from 11.5s to 2.4s. For repeated patterns (same user asking about same team context), this is the single highest-ROI optimization.

### Architecture Maturity Assessment

Our research rated streaming SSE as **PROVEN** — "real, boring production infrastructure now." The hard part is not SSE itself; it's buffering, auth, reconnect semantics, and cleanup discipline.

---

## Agent Architecture: Workflows vs Autonomous Agents

### The Problem

We have 10 AI services that currently operate independently. The product would be more intelligent if they could chain together: risk detection finds OVERCOMMIT → what-if service computes impact of removing lowest-priority commit → format as a specific recommendation.

### The Fundamental Distinction

Anthropic draws a critical line: **workflows** are predefined code paths with deterministic gates. **Agents** dynamically direct tool usage with open-ended autonomy. Anthropic explicitly says the most successful enterprise implementations use simple, composable patterns rather than complex frameworks.

### Approaches We Considered

| Approach | Best When | Risk | Our Verdict |
|----------|-----------|------|-------------|
| **Independent services (current)** | Simple, predictable, debuggable | No cross-capability intelligence | Starting point |
| **Typed workflow DAGs** | Predictable enterprise tasks, clear handoffs | More code, more testing | **Our choice** |
| **ReAct agents** | Open-ended exploration, unpredictable step count | Higher cost, compounding errors, hard to debug | **Rejected for core product** |
| **Multi-agent orchestration** | Truly massive, parallel workstreams | ~15x token cost, very high complexity | **Rejected — not justified at our scale** |
| **LangGraph / CrewAI** | Complex stateful workflows with checkpointing | Framework lock-in, harder to debug | Considered, decided raw code is simpler for our use case |

### What We Chose: Typed Workflow Orchestration

We wire existing services into explicit, bounded pipelines with typed inputs and outputs:

**Example chain: Risk → What-If → Recommendation**
```
RiskDetectionService finds OVERCOMMIT
  → WhatIfService computes impact of removing lowest-priority commit
  → Format as recommendation: "Removing 'Update wiki' (PAWN, 3pts) resolves 
    overcommit and barely changes RCDO coverage"
```

**Example chain: Carry-Forward → History → Calibrated Suggestion**
```
ReconcileAssist sees carry-forward streak ≥ 3
  → Query personal insight history for this RCDO
  → Compute historical achievement rate for this chess piece
  → Generate: "This is the 3rd week carrying forward 'Auth refactor'. 
    Your historical QUEEN achievement rate is 72%. Consider splitting 
    into two ROOKs."
```

**Why NOT ReAct or autonomous agents:**

The research was unambiguous:
- A 2025 study found 68% of production agents execute ≤10 steps before human intervention
- Agents bring higher cost and compounding-error risk
- Anthropic describes internal incidents: deleting Git branches, uploading auth tokens, attempting production database migrations
- Our domain has clear, predictable workflows (lock, reconcile, carry-forward) — not open-ended exploration
- Identical prompts yield different execution paths, making testing unreliable

**Why NOT framework-based orchestration (LangGraph, CrewAI):**

Anthropic advocates starting with raw LLM API calls and only adding framework complexity when it demonstrably improves outcomes. Our workflows are straightforward enough that a typed Java service with explicit handoffs is cleaner than introducing framework dependencies. We get compile-time type safety, standard debugging, and no abstraction leakage.

### Error Cascade Prevention

Multi-step reasoning chains are where errors compound. Our controls:

1. **Validate every step.** Each service returns a typed result with explicit success/failure. Schema validation via Jakarta Bean Validation at every boundary.
2. **Persist state after every step.** If step 2 fails, we can retry from step 2, not start over.
3. **Retry only the failing step.** Transient tool failures get automatic retries; schema violations get re-asks; business-rule failures stop deterministically.
4. **Store artifacts outside the prompt.** Large intermediate results go to the database, not the context window. Anthropic warns about "context rot" in long-horizon runs.
5. **Trace everything.** Every step writes to `ai_suggestion` with full audit trail.
6. **Never auto-execute irreversible actions.** The LLM recommends; the user decides.

### What We'd Do at Scale

- **Add ReAct for the RAG investigator surface.** If a manager asks a complex, multi-hop question ("Which teams have consistently missed RCDO coverage for the last month, and what's the common pattern?"), an agentic loop that retrieves, evaluates, and refines its search would produce better answers. But this would be a bounded investigator with max 5 iterations, read-only tools, and explicit "inconclusive" outcomes.
- **Durable workflow execution via Temporal.** For chains that matter to revenue or compliance, run them on crash-proof infrastructure that resumes exactly where it left off after failures. Anthropic built the same capability internally for their multi-agent Research system.
- **Parallel sub-agents for manager summaries.** Spawn 3-5 sub-agents (one per team member) to investigate each person's week in parallel, then synthesize. Anthropic reports this approach outperformed single-agent by 90.2% on breadth-first queries.
- **Programmatic Tool Calling.** Anthropic's approach (November 2025) adds code execution steps to process tool results before they enter context, reducing context pollution.

---

## Proactive AI: When to Speak and When to Stay Quiet

### The Problem

We want AI to proactively surface risk, suggest improvements, and flag patterns — but not at the cost of notification fatigue. How often can you push before users tune out?

### The Research Is Clear: Precision Over Frequency

There is no credible universal threshold like "3 pushes/day." The research points to **fit-to-context**: interruption cost depends on timing, workload, task boundary, relevance, and perceived actionability.

**The fatigue pattern is well-documented:**
- In hospital monitoring, higher exposure to nonactionable alarms was associated with slower response to subsequent alarms
- Reuters Institute reported 43% of people who didn't receive alerts had actively disabled them, citing excess frequency
- At a 20% false alarm rate with PPV of 0.3, operators ignored about half of true alarms on difficult targets

**The production products converge on the same pattern:**
- **GitHub Copilot:** Keeps proactive help inline in the editor — ghost text suggestions appear as you type, not as notifications
- **Linear:** Applies proactivity in Triage mode, where interruption cost is naturally lower; surfaces suggestions with "hover to see why"
- **Notion AI:** Object-centric automation (autofill on database properties), not constant pinging
- **Datadog Watchdog:** Proactive, but heavily gated — builds baselines, detects anomalies, attaches impact/root-cause context

### What We Chose: Event-Triggered, Not Mutation-Driven

Our current event triggers are already right:
- **On lock** → risk detection + lint + personal insights ✓
- **On commit save** → debounced lint ✓
- **Daily batch** → team insights + index sweep ✓
- **On reconcile page load** → reconcile assist auto-prefill ✓
- `useDismissMemory` hook for suppressing dismissed suggestions ✓

We will **NOT** add "AI on every mutation." This was one of our most strongly downgraded recommendations.

**Our notification priority stack:**

| Surface | When | UX Pattern |
|---------|------|------------|
| Inline suggestions (lint, RCDO suggest) | User is already on the object | Ghost text / inline annotation |
| Risk banners | On lock, high confidence only | Banner at top of relevant page |
| Insight panels | On page load, debounced | Side panel, non-interruptive |
| Team summary | On-demand only | Manager explicitly requests |
| Proactive push (future) | Only if: high confidence + time-sensitive + user-relevant | Would degrade to inbox/digest otherwise |

**The design heuristic from research:**
> Interrupt only when `expected user value × confidence × urgency > interruption cost × current workload`

### What We'd Do at Scale

- **Build an AI notification budget.** Track "seen → acted on → accepted/dismissed → disabled" per user per channel. Cap push volume when acceptance ratios deteriorate.
- **Add triage mode (Linear-style).** A dedicated view where proactive AI suggestions are expected and welcome — not injected into task-focused views.
- **Event-driven architecture with intelligent gating.** Event bus → orchestration/decision worker → persistent state → notification fan-out. Trigger on derived business events ("risk crossed threshold," "plan looks similar to last week's missed plan"), not raw database mutations.
- **Buffered, not real-time.** Notion AI Autofill explicitly buffers — auto-updates five minutes after page edits, not on every keystroke. That's the right pattern for most enterprise AI.

---

## Anti-Sycophancy: Honest AI in Enterprise Software

### The Problem

Most LLMs default to agreeable, encouraging responses. In a weekly planning tool, sycophancy means the AI congratulates overcommitters, softens risk signals, and rubber-stamps bad plans. This is actively harmful.

### Why This Is Not Just a Developer Concern

- **OpenAI** publicly acknowledged sycophantic behavior affects trust and rolled back a GPT-4o update in 2025. Their early offline evals and A/B tests looked positive before the rollback — sycophancy can look good in short-term metrics.
- **Anthropic** found sycophancy across five state-of-the-art assistants and showed that preference optimization can sometimes favor sycophancy over correctness. Users often rate potentially disempowering interactions favorably in the moment.
- **KPMG's 2025 survey** found lower AI acceptance in the U.S. than the global average and highlighted trust as the key barrier.

In enterprise products, the customer-facing translation of anti-sycophancy is: **challenge weak assumptions, avoid fake certainty, and disagree politely when needed.**

### What We Built

Our `team-insight.txt` and `personal-insight.txt` prompts contain aggressive anti-flattery rules:
- *"If the data warrants 2 criticisms and 0 praise, return 2 criticisms and 0 praise"*
- *"Carry-forward streaks on important work are a PROBLEM, not 'ambitious scoping'"*
- *"Do NOT soften bad patterns with excessive praise"*

Our `RiskDetectionService` is the architectural embodiment of this: deterministic rules that don't care about feelings fire first (OVERCOMMIT, UNDERCOMMIT, REPEATED_CARRY_FORWARD), then the LLM adds what the rules missed.

### Our Maturity Assessment

We rated anti-sycophancy as **EMERGING** — not yet a standard line item in procurement language, but absolutely a real product-quality differentiator. It's our most defensible AI feature because most competitors optimize for user satisfaction metrics that reward sycophancy.

### What We'd Do at Scale

- **Add explicit anti-sycophancy evals.** Before any prompt change ships, run a "sycophancy check" eval that presents the prompt with data that warrants criticism and measures whether the output is appropriately critical.
- **Disagree-with-user test cases.** Golden set cases where the user's plan is objectively bad, and the AI must say so. Measure the "polite disagreement" rate.
- **Track sycophancy alongside acceptance.** If acceptance rate is very high and the AI rarely criticizes, that might be a sycophancy signal, not a quality signal.

---

## Predictive Intelligence: Statistical Baselines Before ML

### The Problem

We have `user_week_fact` data: points planned vs achieved per user per week. Can we predict sprint/task completion probability?

### Approaches We Considered

| Method | Data Requirements | Accuracy | Complexity | Our Verdict |
|--------|-------------------|----------|------------|-------------|
| **Rolling average** | Any | Low — throws away variation | Trivial | Too simple |
| **Bayesian completion-rate model** | 8+ weeks | Good for broad ranges | Low | **Our primary choice** |
| **Monte Carlo throughput simulation** | 12+ weeks | Good probability ranges | Low-Medium | **Our secondary choice** |
| **Logistic regression** | Hundreds of task-level observations | Better per-task predictions | Medium | Future, when we have the data |
| **Gradient boosted trees** | Thousands of observations + rich features | Best for complex patterns | High | Far future |
| **Per-user time-series forecasting** | 30+ comparable periods per user | Potentially high | High | **Rejected — 12 weeks is not enough** |

### What We Chose: Bayesian Rates + Monte Carlo

**Bayesian completion-rate model:**
- Rolling historical distribution over "planned vs completed" rate per user
- Per-chess-piece calibration ("Your QUEEN estimates historically achieve 72%")
- Carry-forward probability based on streak + chess piece + blocked status

**Monte Carlo simulation:**
- Sample from historical throughput/cycle-time distributions
- Return probability ranges, not point estimates: "Expected delivery: 35-42 points"
- This is the approach emphasized by Scrum.org and widely used by forecasting add-ons

**Why NOT ML:**
- 12 weeks is too little history for per-user ML models — they'll overfit
- Google's Rules of ML: "Don't be afraid to launch without ML"
- With 12 weekly aggregates, black-box ML will look smarter than it is
- ML becomes worth it only when you can pool data across many users with covariates (role, tenure, product usage, calendar effects)

**Display as ranges, never point estimates.** "Expected: 35-42 points" not "38.5 points." Scrum.org's forecasting guidance explicitly argues that simple average velocity is weak because it throws away variation.

**Data sufficiency thresholds:**

| History | Reliability |
|---------|------------|
| < 8 comparable periods | Mostly noise — don't show predictions |
| 8-15 periods | Usable for broad planning bands |
| 20-30 periods | Forecasting starts to stabilize |
| 50+ periods | Serious modeling becomes worthwhile |

### Causal Inference: Much Harder Than Prediction

Research was unambiguous: "Determining WHY work was missed is much harder than predicting THAT it will be missed." Structured planning data alone rarely identifies the true cause — external blockers look like low productivity, estimation bias looks like underperformance, scope creep looks like missed commitment.

**Our tiered approach:**
1. **Predict:** "This commit has a 78% probability of being carried forward"
2. **Attribute:** "Top contributing signals: carry-forward streak, scope increase, blocker time"
3. **Confirm:** Manager validates the reason (we already have `ManagerComment` for this)

We will NOT claim the AI "knows the reason." We present contributing factors and let humans decide.

### What We'd Do at Scale

- **Pool data across users with covariates.** Once we have thousands of task-level completions across many users, build a segment-aware logistic model with features: estimate size, chess piece, carry-forward count, blocked flag, owner load, scope changes, team, and calendar effects.
- **Causal forest / uplift models** for estimating the effect of specific interventions ("Does breaking a QUEEN into two ROOKs actually improve delivery?"). This requires treatment/control groups and careful confounder handling.
- **Linear-style project graph.** Linear ships forecast-style completion estimates with optimistic/pessimistic ranges. Our `user_week_fact` + Monte Carlo can produce something similar.

### Production Landscape

- **Linear:** Yes, native forecast-style completion estimates
- **Jira:** AI planning assistance, but probabilistic forecasting is mostly in ecosystem apps like ActionableAgile
- **monday.com:** AI risk insights and capacity support, not native sprint-probability
- **Asana:** AI status/risk/workflow assistance, not much evidence of native probabilistic forecasting
- **15Five:** Not really for sprint/week planning

We're ahead of most competitors on this dimension by even attempting principled forecasting.

---

## Evaluation & Observability: Measuring What Matters

### The Problem

We need to know when AI quality degrades, safely iterate on prompts, and measure whether changes help. Our current stack is Prometheus gauges + async `FaithfulnessEvaluator`. That's good for SRE-style monitoring but insufficient for prompt experimentation.

### The Landscape

| Tool | Best For | Our Verdict |
|------|----------|-------------|
| **Langfuse** | OSS LLM platform — tracing, prompts, datasets, experiments | **Our choice** |
| **Braintrust** | Eval-centric commercial workflow — experiments, scoring, comparison | Strong alternative if eval workflow is the center of gravity |
| **RAGAS** | Metric library (faithfulness, context recall, etc.) | **Keep as metric library**, not end-to-end platform |
| **TruLens** | Detailed instrumentation, feedback functions | Solid but behind Langfuse on OSS adoption |
| **Prometheus/Grafana** | SRE metrics (latency, tokens, error rates) | **Keep for infra**, not for LLM-specific concerns |
| **Custom eval pipeline** | Domain-specific scorers, business KPIs, safety checks | **Always needed on top** |

### What We Chose: Langfuse + Prometheus + RAGAS Metrics

**Langfuse** for trace-level LLM inspection, prompt version comparison, golden-set replay, and experiment workflows. **Prometheus** stays for SRE-style metrics. **RAGAS-style metrics** (faithfulness via claim decomposition) continue via our existing `FaithfulnessEvaluator`.

**Why Langfuse specifically:**
- Winning the OSS default platform slot for small teams
- Tracing, prompt management, datasets, experiments, model-based evals, self-hosting
- Integrates with OpenTelemetry
- Prometheus can't do: prompt version comparison, trace inspection, datasets/experiments, human annotation, linking bad outputs back to exact prompts

### LLM-as-Judge: Useful But Not Trustworthy Alone

We use LLM-as-judge in our eval harness. Research shows:
- In some QA settings, LLM-vs-human agreement can reach Cohen's κ up to 0.93
- But JudgeBench found strong judges can perform only slightly better than random on harder correctness-sensitive comparisons
- A large ACL 2025 study found "substantial variance across models and datasets"

**Our practical rule:**
- Good enough for: ranking candidates, monitoring drift, triaging failures, screening outputs
- Not good enough alone for: high-stakes launches, rare edge cases, compliance decisions
- **Always:** constrained rubrics, specific dimensions judged separately, panel judging for important decisions, human escalation for disagreements

### Prompt Testing Without Classical A/B

We can't do 50/50 user-randomized A/B tests — our traffic is too low and stakes too high. Instead:

1. **Golden set replay.** Maintain 100-500 critical test cases; replay on every prompt change.
2. **Shadow evaluation.** Run new prompts behind the scenes on live inputs; compare outputs via LLM judges + business rules.
3. **Pairwise comparison.** "Which response is better on criterion X?" is more stable than absolute scoring.
4. **Sequential decision rules.** Don't wait for large classical samples; use Bayesian updating and stop when evidence is strong enough.
5. **Canary by risk class.** Route only low-risk intents to new prompts first.

### Cost of Evaluation

At ~1,500 input tokens + 150 output per eval call:
- GPT-4.1 mini: ~$0.84 per 1,000 evals
- Claude Haiku 4.5: ~$2.25 per 1,000 evals
- Gemini Flash-Lite: ~$0.60 per 1,000 evals

**Our sampling strategy:**
- **100% eval** for: risk signals, team insights, personal insights, RAG answers
- **10% sampling** for: commit draft assist, commit lint
- **100% during canaries** and immediately after prompt/model changes
- **Stratified sampling:** oversample long answers, tool-using traces, low-confidence outputs

### What We'd Do at Scale

- **Braintrust for experiment workflow** if we outgrow Langfuse's experiment capabilities
- **Human review queue** for disagreements, high-value flows, and release gating
- **Custom business KPI scoring** — e.g., "Did the risk signal the AI surfaced actually predict a carry-forward?"
- **Interleaving for RAG ranking changes** — more sample-efficient than A/B for ranked retrieval

---

## Context Engineering: What Goes Into the Window

### The Problem

Managing what goes into (and stays in) the context window is not an afterthought. Google's ADK team coined the term "context engineering" in December 2025, treating context as "a compiled view over a richer stateful system."

### Our Context Assembly Strategy

Our `StructuredEvidenceService` already assembles 4 evidence pillars per AI request:
1. **SQL Facts** — exact current state, distributions, compliance flags
2. **Lineage** — carry-forward chains via recursive CTEs, RCDO ancestry, scope-change timeline
3. **Semantic** — Pinecone top-K for question-relevant context
4. **Risk Features** — completion ratios, max streaks, active signal types

This is our version of context engineering. Each pillar serves a different purpose:
- SQL facts are deterministic and cheap — always include them
- Lineage provides causal structure that embeddings can't capture
- Semantic retrieval fills in similar historical patterns
- Risk features are pre-computed signals that prevent the LLM from having to derive them

### Chunk Enrichment: Our Competitive Advantage

`ChunkBuilder` doesn't embed raw text. It constructs narratives with:
- Full RCDO ancestry paths ("Rally Cry: Win Enterprise > DO: Improve Uptime > Outcome: 99.9% SLA")
- Carry-forward lineage stories
- Linked ticket summaries with status
- Cross-team RCDO overlap notes
- Owner and team context

Most RAG systems flatten relationships. Our chunks preserve the full semantic graph. This is closer to Anthropic's Contextual Retrieval (prepending document-level context headers to each chunk) than naive text splitting.

### Confidence Tiers: Evidence-Based, Not LLM Self-Assessment

We replaced hard-coded `confidence = 0.85` with structured evidence tiers:

| Tier | Signal | Display |
|------|--------|---------|
| **High** | SQL facts + lineage + semantic all converge; multiple high-score chunks (>0.8) | "High confidence" + evidence count |
| **Medium** | Some evidence sources agree; semantic matches moderate (0.5-0.8) | "Medium confidence" + primary source |
| **Low** | Sparse evidence; few relevant chunks; conflicting signals | "Low confidence — limited evidence" |
| **Insufficient** | No relevant chunks; no SQL facts available | "Insufficient data" — don't show AI output |

**Why NOT LLM self-reported confidence:** Research is clear — LLMs are poorly calibrated at self-assessment. The proven approach is structural signals (evidence agreement, chunk scores, SQL fact availability) displayed as qualitative tiers.

### What We'd Do at Scale

- **Prompt caching.** Anthropic's explicit caching reduces costs by 90% and latency by up to 85%. For our per-user-per-week context (which changes slowly), this is enormous.
- **Context compression with LLMLingua.** Microsoft's LLMLingua achieves up to 20x compression with only 1.5% performance loss. For large team summaries where we need to fit all member contexts, this could be transformative.
- **Memory systems (MemGPT/Letta pattern).** Three-tier architecture: in-context core memory (editable, ~2K chars), archival memory (vector DB, unbounded), recall memory (conversation history with search). The agent decides what to page in and out.
- **Hierarchical summarization (RAPTOR).** Organize documents where leaves are text chunks and internal nodes store summaries, enabling multi-resolution retrieval. Perfect for our RCDO hierarchy where queries range from "how is the whole Rally Cry doing?" to "what happened with this specific Outcome?"

---

## Shared Intelligence Context vs Feature Silos

### The Problem

We have 10 AI services. Should they share context, or operate independently?

### The Production Pattern

Research showed this is an **actual production pattern**, but only in its boring form:
- **Microsoft 365 Copilot** grounds prompts against Microsoft Graph, scoped to the signed-in user's permissions
- **Atlassian** says Teamwork Graph unifies data across Atlassian and 100+ apps
- **Salesforce** positions Data Cloud as a shared business-data layer

Where this becomes "architecture astronautics" is when teams try to build a **universal enterprise brain** — one mega-context object, one cross-product memory system. That usually collapses under permissions, latency, freshness, and debugging complexity.

### What We Chose: Shared Evidence Layer, Feature-Specific Assembly

**Shared:** Identity/ACL enforcement, `StructuredEvidenceService` (SQL facts, lineage, semantic matches, risk features), `ChunkBuilder` enrichment, `AiProvider` abstraction, audit trail, evaluation infrastructure.

**Feature-specific:** Each AI service assembles its own prompt from the shared evidence layer. `RiskDetectionService` emphasizes risk features and capacity. `InsightGenerationService` emphasizes trends and lineage. `SemanticQueryService` emphasizes semantic matches and citations.

This is the proven version: shared retrieval/indexing/provenance, then feature-specific context assembly on top.

### What We'd Do at Scale

- **Per-user-per-week context cache.** Assemble the full evidence bundle for a user's week once, cache it, and let all AI services draw from it. This eliminates duplicate SQL queries and Pinecone calls across services.
- **Shared memory across AI sessions.** If the user asks the RAG system a question and then triggers commit assist, the second call should know what was just asked. This is the MemGPT/Letta pattern.
- **Mixed-mode context assembly.** Microsoft's guidance: indexed content for broad knowledge + live API calls for up-to-date facts. We'd index historical plans/insights but live-query current week data.

---

## Fine-Tuning, Federated Learning, and Other Things We're Not Doing

### Fine-Tuning: Premature Before Prompt Engineering Is Exhausted

**Why we removed it:**
- Anthropic's API does not currently offer general fine-tuning (only Claude 3 Haiku via Bedrock)
- Fine-tuning is for consistent behavior/formatting, not domain knowledge
- For 1-3 engineers, "usually resume-driven too early"
- Better prompts + structured outputs + retrieval improvements outperform fine-tuning for our use case

**When it WOULD make sense:**
- High-volume, repetitive task with a good supervised dataset
- Shrinking long prompts to save tokens on every call
- Distilling a larger model's behavior into a smaller, cheaper one
- Our acceptance rates plateau despite good prompting and retrieval

**Research quote:** "The biggest mistake I see teams make is treating fine-tuning as a solution to a prompting problem they haven't actually tried to solve yet."

### Federated Learning: Wrong Abstraction for Planning Software

**Why we removed it entirely:**
- FL is designed for settings where organizations can't share data AND need a jointly trained model
- Gradient updates can leak training data even with averaging
- Our problems (permissions, ranking, summarization) are not distributed optimization problems
- If we want cross-team patterns, we compute anonymized aggregate statistics server-side

**Research quote:** "Unless you have a hard regulatory/contractual boundary that prevents raw-data sharing AND a genuinely important jointly trained model, FL is not the thing to build. For planning software, I would treat this as a red flag."

### Per-Mutation Event-Driven AI: The "Boy Who Cried Wolf"

**Why we downgraded it:**
- Pure "recompute on every data change" is too expensive and too noisy
- At 20% false alarm rate → users ignore half of true alarms
- PagerDuty explicitly says not every alert should be an incident
- Users calibrate on precision and consequence, not recall

**What we're keeping:** Our current event triggers (on lock, daily batch, on reconcile page load) are already the right granularity. The working pattern is proactive AI on **high-signal semantic events**, not every row update.

### Knowledge Graphs (GraphRAG): Not Yet, But We're Watching

**Why we deferred:**
- RCDO hierarchy is already a graph in PostgreSQL — we get graph traversal via recursive CTEs
- Adding Neo4j would add significant operational complexity
- Entity resolution remains the critical bottleneck — accuracy below 85% makes the system unreliable
- For our query mix, hybrid retrieval + reranking covers most needs

**When it WOULD make sense:**
- If users frequently ask cross-team, cross-RCDO relationship questions
- If we need multi-hop reasoning that spans more than 2-3 hops
- LightRAG achieves 70-90% of full GraphRAG quality at 1/100th the cost — that's our likely entry point

### Long-Context as a Silver Bullet: It's Not

**Gemini 2.5 Pro has 2M tokens. Claude has 1M. Problem solved, right?**

No:
- Lost-in-the-middle problem persists with 40% context degradation at scale
- Prefill latency exceeds 2 minutes at maximum context lengths
- KV cache for 1M tokens requires ~15GB per user
- "Having the option of long context windows is critical, but it may not make sense to use the entire window by default"

Long context is a safety net, not a replacement for retrieval. We use it to be less brittle about truncation and whole-document reasoning, but we still retrieve selectively.

---

## Multi-Modal Intelligence: Charts, Visuals, and What's Actually Ready

### The Problem

Could we generate AI-annotated charts, visual planning summaries, or diagram-based what-if scenarios?

### The Honest Assessment

**What's shippable:** Anthropic ships inline chart/diagram generation in Claude. Turning structured data into a chart spec and letting a deterministic renderer draw it is practical.

**What's NOT ready:**
- ChartMuseum benchmark: humans 93%, best model 63%, best open model 38.5% on chart QA
- AI-generated chart captions are frequently factually inaccurate
- Models cannot reliably invent the right chart, right numbers, and right narrative without strong validation

### What We Chose

We already have Tremor charts rendered from structured data. The AI is not involved in chart rendering. If we add AI annotations, the LLM produces structured JSON annotations (e.g., "this week's completion rate is an outlier") and we render them deterministically on existing charts. The chart remains the source of truth; the AI adds narration.

### What We'd Do at Scale

- **AI-drafted chart specs.** LLM suggests what chart to show based on the user's question, produces a spec, deterministic renderer draws it.
- **Visual what-if scenarios.** "If I remove this QUEEN commit, here's what the capacity chart looks like" — computed deterministically, rendered by Tremor, narrated by LLM.
- **Multi-modal input processing.** If users paste screenshots of Jira boards or spreadsheets, use vision models to extract structured data. But validate everything before acting on it.

---

## Cross-Cutting: Infrastructure, Deployment & Resilience

### Modular Monolith over Microservices

| Consideration | Decision | Why |
|---------------|----------|-----|
| Service decomposition | Modular monolith | Complexity is in workflow and data integrity, not service boundaries. One DB transaction boundary keeps lock/reconcile snapshots consistent. |
| AI provider abstraction | `AiProvider` interface | `OpenRouterAiProvider` (production) and `StubAiProvider` (test/offline). Core workflow never depends on a single vendor. |
| Read model pattern | Derived `*_fact` / `*_rollup` tables | Pre-computed on lifecycle events + 5-minute cron. Meets P95 < 1.5s without complex joins. |
| Snapshot strategy | Immutable lock/reconcile snapshots | History cannot be rewritten — only appended via scope change events. |
| Frontend architecture | Module Federation remote | Self-contained, receives host context via `HostProvider`, runs standalone for dev with mock host bridge. |

### Rate Limiting Gap

Current in-memory token bucket in `OpenRouterAiProvider` does nothing in multi-instance deployment. This is a known gap. The fix is Redis-backed rate limiting with `spring-boot-starter-data-redis` — a straightforward infrastructure task, not an architecture decision.

### Graceful Degradation

Every AI feature degrades gracefully if the AI provider is unavailable:
- `StubAiProvider` returns realistic canned responses
- Risk detection falls back to rules-only (5 deterministic rules always fire)
- Frontend shows AI surfaces with "AI unavailable" state
- Feature flag `FEATURE_AI_ASSISTANCE` can disable all AI

This is a deliberate design choice. The weekly planning workflow must never be blocked by AI provider outages.

---

## The "Go Big" Roadmap: What We'd Build With More Time

### With 2 More Engineers (6-month horizon)

1. **Full what-if planner.** "If I add this 5-point Queen, what should I de-scope?" Uses constraint-based optimization (not LLM reasoning) with LLM narration of the result.
2. **Cross-team RCDO intelligence.** "Which Rally Cries are underfunded across all teams?" Requires pooled data access with proper authorization gates.
3. **Predictive carry-forward model.** Pool task-level data across users, add logistic regression with features: estimate size, chess piece, carry-forward count, blocked flag, owner load, scope changes.
4. **Multi-turn RAG conversations.** Context window management, conversation summarization, follow-up question understanding.
5. **Real-time collaborative planning.** WebSocket-based collaborative editing during draft phase, with AI suggestions reflecting the team-wide context as it evolves.

### With a Dedicated ML Engineer

1. **Calibrated estimation model.** "Your QUEEN estimates average 3.2x actual effort. A ROOK estimate of 3 would cost you 4.8 points of actual capacity." Requires careful sample size and calibration.
2. **Anomaly detection on team metrics.** Datadog Watchdog-style baseline detection. Detect when a team's delivery pattern changes significantly before anyone notices.
3. **Causal forest for intervention effects.** "Does breaking a QUEEN into two ROOKs actually improve delivery?" Requires treatment/control design and confounder handling.
4. **Fine-tuned domain model via Bedrock.** If acceptance rates plateau, fine-tune Claude 3 Haiku on our best-accepted suggestions. Distill into a cheaper model for high-volume, low-stakes tasks.

### With an Enterprise Budget

1. **Knowledge graph layer.** LightRAG or LazyGraphRAG for cross-RCDO, cross-team relationship reasoning. Query router sends relationship queries to graph, factual queries to hybrid retrieval.
2. **Multi-agent manager intelligence.** Orchestrator spawns sub-agents per team member, each with its own context window and tools. Synthesizes into a comprehensive team health report.
3. **Temporal-backed durable workflows.** Crash-proof execution for multi-step AI chains. Resume-from-failure. Full audit trail per step.
4. **Custom evaluation infrastructure.** Business-KPI-linked scoring ("Did the risk signal predict a carry-forward?"), human annotation workflows, Bayesian A/B testing, continuous calibration.
5. **Prompt caching at scale.** Multi-layer cache: semantic cache → prefix cache → full inference. Could reduce total AI costs by 80%+.

---

## Sources & Research Methodology

### Research Tools Used
- **Claude Research** (Anthropic) — web search enabled, research mode. 6+ queries covering RAG best practices, context engineering, streaming patterns, enterprise differentiation, Pinecone hybrid search, structured output validation.
- **ChatGPT Extended Pro** (OpenAI) — maximum reasoning depth. 4+ queries covering production RAG evaluation, agentic patterns debunking, proactive AI fatigue, enterprise technique maturity ratings.
- **ChatGPT Pro** (OpenAI) — research-grade analysis. 4+ queries covering predictive planning, hybrid retrieval, eval/observability tooling comparison, structured output reliability across providers.
- **ChatGPT Deep Research** (OpenAI) — comprehensive multi-source investigation. 2+ queries covering large-corpus handling strategies and current state of AI coding tools.

Each query included full context about our tech stack (Spring Boot + React + Pinecone + OpenRouter/Claude + OpenAI embeddings + PostgreSQL), domain (weekly planning with RCDO hierarchy), and current implementation. We cross-referenced findings between Claude and ChatGPT and flagged disagreements.

### Key External Sources Referenced

**Retrieval & RAG:**
- Microsoft: Azure AI Search RAG guidance, Semantic Ranker benchmarks, Query Rewriting blog ([techcommunity.microsoft.com][1])
- Pinecone: Hybrid Search docs, dense+sparse index guidance ([docs.pinecone.io][2])
- Cohere: Rerank 4.0, RAG Streaming/Citations ([docs.cohere.com][3])
- Voyage AI: Rerank 2.5, embedding benchmarks ([docs.voyageai.com][4])
- Weaviate: Hybrid search, Relative Score Fusion ([docs.weaviate.io][5])
- Elasticsearch: RRF retriever, linear retriever ([elastic.co/docs][6])
- arXiv: BEIR benchmark, HyDE original paper, Lost in the Middle, NoLiMa, LongBench v2, ChatQA 2

**Agents & Architecture:**
- Anthropic: Building Effective Agents, Context Engineering for AI Agents, Multi-Agent Research System, Claude Code Auto Mode ([anthropic.com/engineering][7])
- OpenAI: Function Calling, Streaming, WebSocket Mode, Background Mode, Structured Outputs ([developers.openai.com][8])
- Microsoft: Multi-Agent Patterns, Azure Cloud Adoption AI Agents ([learn.microsoft.com][9])
- Temporal: Durable Execution docs ([docs.temporal.io][10])

**Structured Outputs & Validation:**
- Anthropic: Structured Outputs docs ([platform.claude.com][11])
- OpenAI: Introducing Structured Outputs, Schema adherence benchmarks ([openai.com][12])
- OpenRouter: Structured Outputs, Response Healing ([openrouter.ai/docs][13])
- Spring Boot: Validation reference, Spring AI BeanOutputConverter ([docs.spring.io][14])
- networknt: json-schema-validator ([github.com/networknt][15])

**Evaluation & Observability:**
- Langfuse: LLM Observability, Evaluation overview ([langfuse.com][16])
- Braintrust: Experiments, Online evaluation ([braintrust.dev][17])
- RAGAS: Faithfulness metric ([docs.ragas.io][18])
- TruLens: Evals and Tracing ([trulens.org][19])
- OpenTelemetry: GenAI semantic conventions ([opentelemetry.io][20])
- ACL 2025: LLM-as-judge reliability study across 20 NLP tasks ([aclanthology.org][21])

**Proactive AI & Notification Design:**
- Linear: Triage Intelligence, Project Graph ([linear.app/docs][22])
- GitHub: Copilot code suggestions, NES ([docs.github.com][23])
- Notion: AI Autofill, Enterprise Search, Database Automations ([notion.com/help][24])
- Datadog: Watchdog, Bits AI SRE ([docs.datadoghq.com][25])
- Reuters Institute: Notification fatigue research 2025 ([reutersinstitute.politics.ox.ac.uk][26])
- ACM: User-Centered Investigation of Attention Management ([dl.acm.org][27])
- Europe PMC: Nonactionable alarm exposure and response times ([europepmc.org][28])

**Predictive Intelligence:**
- Scrum.org: Probabilistic Forecasting, Monte Carlo in Scrum ([scrum.org][29])
- Google: Rules of ML ([developers.google.com][30])
- BMJ: Sample size for prediction models ([bmj.com][31])
- Springer Link: Causal inference and observational data ([link.springer.com][32])

**Anti-Sycophancy:**
- OpenAI: Sycophancy in GPT-4o, Expanding on Sycophancy ([openai.com][33])
- KPMG: Trust and AI survey 2025 ([kpmg.com][34])

**Context Engineering & Long Context:**
- Anthropic: 1M Context GA, Effective Context Engineering ([claude.com/blog][35])
- arXiv: LLM×MapReduce, EM-LLM episodic memory, KV cache compression surveys
- Microsoft: LLMLingua series (up to 20x compression with 1.5% performance loss)
- Google: Infini-attention, Ring Attention

[1]: https://techcommunity.microsoft.com/blog/azure-ai-foundry-blog/raising-the-bar-for-rag-excellence-query-rewriting-and-new-semantic-ranker/4302729
[2]: https://docs.pinecone.io/guides/search/hybrid-search
[3]: https://docs.cohere.com/changelog/rerank-v4.0
[4]: https://docs.voyageai.com/docs/reranker
[5]: https://docs.weaviate.io/weaviate/concepts/search/hybrid-search
[6]: https://www.elastic.co/docs/reference/elasticsearch/rest-apis/retrievers/rrf-retriever
[7]: https://www.anthropic.com/engineering/building-effective-agents
[8]: https://developers.openai.com/api/docs/guides/function-calling
[9]: https://learn.microsoft.com/en-us/microsoft-copilot-studio/guidance/multi-agent-patterns
[10]: https://docs.temporal.io/
[11]: https://platform.claude.com/docs/en/build-with-claude/structured-outputs
[12]: https://openai.com/index/introducing-structured-outputs-in-the-api/
[13]: https://openrouter.ai/docs/guides/features/structured-outputs
[14]: https://docs.spring.io/spring-boot/reference/io/validation.html
[15]: https://github.com/networknt/json-schema-validator
[16]: https://langfuse.com/docs/observability/overview
[17]: https://www.braintrust.dev/docs
[18]: https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/faithfulness/
[19]: https://www.trulens.org/
[20]: https://opentelemetry.io/docs/specs/semconv/gen-ai/
[21]: https://aclanthology.org/2025.acl-short.20/
[22]: https://linear.app/docs/triage-intelligence
[23]: https://docs.github.com/en/copilot/concepts/completions/code-suggestions
[24]: https://www.notion.com/help/autofill
[25]: https://docs.datadoghq.com/watchdog/
[26]: https://reutersinstitute.politics.ox.ac.uk/digital-news-report/2025/walking-notification-tightrope-how-engage-audiences-while-avoiding
[27]: https://dl.acm.org/doi/fullHtml/10.1145/3626705.3627766
[28]: https://europepmc.org/articles/PMC4456276
[29]: https://www.scrum.org/resources/probabilistic-forecasting-and-flow-scrum
[30]: https://developers.google.com/machine-learning/guides/rules-of-ml/
[31]: https://www.bmj.com/content/368/bmj.m441
[32]: https://link.springer.com/article/10.1186/s12874-023-02058-5
[33]: https://openai.com/index/sycophancy-in-gpt-4o/
[34]: https://kpmg.com/us/en/articles/2025/trust-attitudes-and-use-of-artificial-intelligence.html
[35]: https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents

---

*This document is a living record. As we implement priorities and gather data, decisions will be revised based on measured outcomes, not assumptions. Every "What We'd Do at Scale" section is a genuine future path, not aspirational padding — each one has a concrete trigger condition that would justify the investment.*
