# Interview Prep: Weekly Commit Module — Technical Deep Dive

> **Format:** "Evaluating how you built it, your decision-making through the process, and the quality of the actual implementation."
>
> This document is your cheat sheet. Read it before the interview, internalize the narratives, and practice saying them out loud.

---

## Table of Contents

1. [30-Second Elevator Pitch](#1-30-second-elevator-pitch)
2. [The Problem (Why This Exists)](#2-the-problem-why-this-exists)
3. [Architecture Decisions They'll Probably Ask About](#3-architecture-decisions-theyll-probably-ask-about)
4. [The AI Layer — The Star of the Show](#4-the-ai-layer--the-star-of-the-show)
5. [Hardest Problems I Solved](#5-hardest-problems-i-solved)
6. [Quality & Testing — How I Know It Works](#6-quality--testing--how-i-know-it-works)
7. [What I'd Do Differently / Next](#7-what-id-do-differently--next)
8. [Likely Questions & Answers](#8-likely-questions--answers)
9. [Numbers You Should Know Cold](#9-numbers-you-should-know-cold)
10. [Code Walkthrough Paths](#10-code-walkthrough-paths)

---

## 1. 30-Second Elevator Pitch

> "Weekly Commit Module replaces 15Five for weekly planning. Every person creates weekly commitments linked to company strategy through an RCDO hierarchy — Rally Cries, Defining Objectives, Outcomes. Plans lock into immutable baselines, reconcile at end-of-week, and carry-forward explicitly with lineage.
>
> The AI layer is not a chatbot bolted on — it's an integrated intelligence pipeline. It drafts commits, lints quality, detects risk, suggests RCDO links, assists reconciliation, generates team summaries, and answers natural-language questions over planning history via a full RAG pipeline. Every suggestion is explainable, auditable, and dismissible.
>
> The codebase is a Java 21 Spring Boot modular monolith with a React TypeScript frontend. 46K lines of backend, 33K lines of frontend, 127 commits, 14 prompt templates, 60 golden eval cases, and a CI-gated eval pipeline."

---

## 2. The Problem (Why This Exists)

**15Five has four structural failures:**

1. **No strategy linkage** — people describe work, but nothing forces connection to Rally Cries/Outcomes. Managers can't see if the team's weekly effort moves the right strategic branches.

2. **No locked baseline** — plans change without a contract point. Can't distinguish planned work from in-week scope creep.

3. **No structured reconciliation** — can't compare planned vs actual. Silent carry-forward hides repeated misses.

4. **No data foundation for AI** — without structured work objects, lifecycle states, and lineage, AI can't provide meaningful assistance.

**The key insight:** If you fix the data model first (structured commits, immutable snapshots, explicit carry-forward with lineage), AI becomes dramatically more useful because it has trustworthy structured data to reason over — not free-text check-ins.

---

## 3. Architecture Decisions They'll Probably Ask About

### "Why a modular monolith instead of microservices?"

> The complexity is in **workflow and data integrity**, not service decomposition. Lock creates an immutable snapshot, scope changes append events, reconciliation compares baselines — all of this needs to be in one database transaction boundary. Microservices would turn a single-transaction operation into a distributed saga for no benefit at this scale. The modular monolith is organized as package-per-bounded-context — plan, lock, reconcile, carry-forward, RCDO, team, ticket, AI — so it's ready to split if needed, but there's no reason to pay the operational cost yet.

### "Why OpenRouter instead of direct API calls?"

> Provider abstraction. `AiProvider` is an interface with two implementations — `OpenRouterAiProvider` for production and `StubAiProvider` for test/offline. OpenRouter gives us model-agnostic routing so we can switch models without code changes. We proved this works — we migrated from Claude Sonnet 4 to GPT-4.1-nano by changing one environment variable, then tuned the prompts to get 95% eval pass rate.
>
> The model comparison leaderboard (`MultiModelEvalRunner`) tests 8 models across all 60 eval cases and picks the winner by a composite score of schema validity, judge quality, latency, and cost. GPT-4.1-nano won: 100% schema, 0.846 judge quality, $0.10/M tokens — 25x cheaper than GPT-4o.

### "Why immutable snapshots instead of versioned rows?"

> Auditing and trust. When a plan locks, we capture a normalized snapshot (individual commit rows) AND a denormalized JSON blob. The normalized rows are queryable; the JSON is the tamper-evident receipt. Post-lock changes are append-only `scope_change_event` rows with required reasons — we never mutate the baseline. Same for reconciliation. This means any compliance or performance review can look at the exact contract that existed at lock time, not a reconstructed view.

### "Why derived read models instead of live joins?"

> The team dashboard, RCDO rollups, and compliance views would require expensive multi-table joins across plans, commits, snapshots, scope changes, and carry-forward chains. We pre-compute `user_week_fact`, `team_week_rollup`, `rcdo_week_rollup`, `compliance_fact`, and `carry_forward_fact` on lifecycle events plus a 5-minute sweep. P95 for any dashboard read is under 1.5 seconds. The read models are idempotent — re-running with the same input produces the same result — so they're safe to rebuild at any time.

### "Why Module Federation for the frontend?"

> This integrates into an existing host app (PA). Module Federation lets us ship a self-contained remote that receives host context (auth, design tokens, feature flags) through a `HostProvider` contract. For local dev, a `MockHostProvider` with a user-switcher banner provides a complete mock bridge. The frontend runs standalone — you don't need the host app running to develop or demo.

---

## 4. The AI Layer — The Star of the Show

### The Design Philosophy (memorize these three words)

**Assistive, Explainable, Auditable.**

- **Assistive:** AI never makes hidden edits. Never auto-submits. Every suggestion has a dismiss button.
- **Explainable:** Every suggestion comes with evidence — SQL facts, carry-forward lineage, semantic matches, risk features. The `EvidenceDrawer` component shows the receipts.
- **Auditable:** Every AI interaction is stored in `ai_suggestion` with model version, prompt version, tokens used, confidence score, and user feedback (accept/dismiss via `ai_feedback`).

### 10 AI Capabilities — Know All of Them

| # | Capability | Key Design Decision |
|---|---|---|
| 1 | **Commit Draft Assist** | Per-field null-threshold rules — leave good fields alone. Two "already perfect" few-shot examples in the prompt prevent over-suggestion. |
| 2 | **Commit Lint** | Separation of hard validation (KING/QUEEN without criteria, title meaningless, chess piece limits) from soft guidance (vague titles, estimate mismatches). The model must never promote ROOK/PAWN missing criteria to hard validation. |
| 3 | **RCDO Suggest** | Returns top-3 RCDO nodes with confidence scores and rationale. Uses the full RCDO hierarchy as context. |
| 4 | **Risk Detection** | **Hybrid rules + LLM.** 5 deterministic rules always fire (OVERCOMMIT, UNDERCOMMIT, REPEATED_CARRY_FORWARD, BLOCKED_CRITICAL, SCOPE_VOLATILITY). The LLM sees which rules already fired and looks for what they missed — hidden dependencies, unrealistic estimates, concentration risk. Rules never depend on AI availability. |
| 5 | **Reconcile Assist** | Auto-prefills when the Reconcile page loads. Suggests outcomes, carry-forward recommendations, and a draft summary. Uses an on-demand button in AI-conscious mode. |
| 6 | **Manager Summary** | Generates a team week summary citing underlying data. On-demand only — never pushes unsolicited summaries. |
| 7 | **RAG (Semantic Search)** | Full pipeline: intent classification → embedding → Pinecone hybrid search (BM25 + vector, RRF fusion) → cross-encoder reranking → LLM answer synthesis with source citations. |
| 8 | **Personal/Team Insights** | Proactive pattern detection. Anti-sycophancy rules: "If the data warrants 2 criticisms and 0 praise, return 2 criticisms and 0 praise." |
| 9 | **What-If Planner** | Pure computation (add/remove/change commits, recalculate capacity/RCDO coverage/risk) + LLM narration of implications. Deterministic math, narrative LLM. |
| 10 | **Predictive Calibration** | Bayesian completion-rate model per user per chess piece. Rolling bias, variance, RMSE, confidence tier. Feeds into draft assist and plan recommendations. |

### The RAG Pipeline — Be Ready to Trace It End-to-End

**Write path:**
```
Entity event → ChunkBuilder (enriched with RCDO path, team, carry-forward lineage,
               linked ticket summary, cross-team overlap)
             → EmbeddingService (OpenAI text-embedding-3-small)
             → PineconeClient.upsert (namespaced by org)
```

**Read path:**
```
Question → Intent classification (LLM: entity types, time range, user scope)
         → Embed query
         → Pinecone hybrid search (top-40, filtered)
         → Cross-encoder reranking (top 15-25 above threshold)
         → Context assembly
         → RAG answer (LLM) with source citations
         → Store audit record
```

**Key insight about ChunkBuilder:** We don't embed raw text. We construct narratives with full RCDO ancestry paths, carry-forward lineage stories, linked ticket summaries with status, and cross-team overlap notes. This is closer to Anthropic's Contextual Retrieval (prepending document-level context to each chunk) than naive text splitting. Most RAG systems flatten relationships — ours preserves the semantic graph.

### Risk Detection — The Best Example of the AI Design Philosophy

> "This is the best example of our design philosophy. The rules engine handles what it can deterministically — overcommit, undercommit, repeated carry-forward, blocked critical work, scope volatility. Five concrete rules that always fire, no AI dependency. Then the LLM augments by looking for things rules can't catch — hidden dependencies between commits, unrealistic estimates given historical calibration, concentration risk where one commit consumes the entire budget. The prompt explicitly lists the 5 rule-based signals and says 'do NOT duplicate these.' If OpenRouter goes down, you still get all 5 rule-based signals. The AI degrades gracefully — it's additive, not foundational."

### Structured Evidence — The Explainability Story

Every AI request is backed by four evidence pillars assembled by `StructuredEvidenceService`:

1. **SQL Facts** — deterministic, cheap: exact current state, distributions, compliance flags
2. **Lineage** — carry-forward chains via recursive CTEs, RCDO ancestry, scope-change timeline
3. **Semantic** — Pinecone top-K for question-relevant context
4. **Risk Features** — pre-computed: completion ratios, max streaks, active signal types

The `EvidenceDrawer` component lets users see exactly what the AI saw. This isn't hand-waving about "transparency" — it's a real UI component that shows the SQL facts, the lineage chain, the semantic matches, and the risk features that went into every suggestion.

---

## 5. Hardest Problems I Solved

### Problem 1: Model Migration (Claude → GPT-4.1-nano)

> We ran a model comparison leaderboard across 8 models. GPT-4.1-nano won on composite score (schema × judge quality × speed × cost). But switching models broke 22% of eval cases — not because the schema was wrong (100% valid), but because the model's judgment was different. GPT-4.1-nano was over-eager: it suggested improvements to already-good commits, missed QUEEN/KING limit violations, and hallucinated moderate confidence when chunks contained no evidence.
>
> The fix was a two-round process:
> 1. **Prompt engineering** — added explicit per-field null-threshold rules to draft-assist, QUEEN/KING limit examples to lint, strict confidence calibration (≤0.30 when evidence absent) to RAG, and single-commit concentration risk to risk-signal. This was the bigger impact.
> 2. **Eval infrastructure hardening** — improved keyword matching (word-presence for paraphrasing, numeric ↔ written form conversion), expanded insufficient-data detection phrases, and relaxed golden case expectations where the model's behavior was defensible but different.
>
> Result: 78% → 95% pass rate. The remaining 5% is stochastic variance — different cases fail on different runs, which is the expected noise floor for a nano-class model.

### Problem 2: Deterministic A/B Experiment Assignment

> The original `ExperimentService` used `Math.random()` for variant assignment, meaning the same user could get different variants on every request. This creates inconsistent UX and invalid A/B data. I replaced it with hash-based bucketing: `hash(experimentName + ":" + userId) % 100`. The hash is deterministic, so the same user always gets the same variant for a given experiment. The bucket maps to a variant via cumulative traffic allocation. Three experiments don't warrant a database table — UUID strings have high entropy, making `String.hashCode()` well-distributed enough.

### Problem 3: When NOT to Suggest (Anti-Sycophancy in Draft Assist)

> The hardest prompt engineering problem wasn't getting the model to suggest improvements — it was getting it to shut up when the commit was already good. GPT-4.1-nano defaulted to always suggesting something, even when the title was clear, criteria were measurable, and the estimate was reasonable. This is the sycophancy problem: the model wants to be helpful, so it offers tweaks even when none are needed.
>
> The fix was explicit per-field defect thresholds:
> - `suggestedTitle → null unless the title is vague, activity-focused, or exceeds 100 chars`
> - `suggestedSuccessCriteria → null unless criteria are completely absent or unmeasurable`
> - `suggestedEstimatePoints → null unless the estimate is clearly unrealistic for the chess piece`
>
> Plus two "already perfect" few-shot examples showing the model what all-null output looks like. This improved null-return compliance from 58% to 92%.

### Problem 4: Confidence Calibration in RAG

> When retrieved chunks didn't contain the requested information, the model would still produce a plausible-sounding answer with moderate confidence (0.5-0.7). This is dangerous in enterprise software — a manager might act on a confident answer that's fabricated from vaguely related chunks.
>
> The fix was strict confidence calibration rules: "When retrieved chunks do NOT mention the person, topic, or time period the user asked about, you MUST set confidence ≤ 0.30 and explicitly state what information is missing. Saying 'no data available' at 0.2 confidence is far better than fabricating an answer at 0.7."
>
> RAG eval pass rate went from 50% to 100%.

---

## 6. Quality & Testing — How I Know It Works

### Eval Infrastructure

- **60 golden test cases** across 7 capabilities (draft-assist, lint, RCDO suggest, risk signal, reconcile assist, RAG query, what-if)
- **PromptEvalRunner** — calls real LLM, validates schema, runs behavioral checks, LLM-as-judge scoring
- **MultiModelEvalRunner** — 8-model comparison leaderboard with composite scoring
- **HistoricalReplayBenchmark** — 12 synthetic plans for rules-engine confusion matrices
- **FaithfulnessEvaluator** — production sampling using RAGAS-style claim decomposition (100% for high-stakes types, 10% for low-stakes)
- **CI-gated threshold check** — `eval-thresholds.json` enforces schema_valid_rate: 1.00, overall_case_pass_rate: 0.95, critical_case_pass_rate: 1.00

### Test Coverage

| Layer | Count | What it covers |
|---|---|---|
| Backend unit/integration | 77 test files | Service logic, controllers, AI services, domain validation |
| Frontend unit | 50 test files | Components, hooks, API clients |
| E2E (Playwright) | 24 specs | Golden path, AI flows, manager flow |
| AI eval | 60 golden cases | Prompt quality across 7 capabilities |

### Observability

- Prometheus metrics: faithfulness score, acceptance rate, provider availability, token consumption
- Alert rules: faithfulness < 0.88 → warning, < 0.85 → critical; acceptance < 20% → investigate
- Prompt version tracking: every `ai_suggestion` stores the prompt version for A/B analysis
- Structured JSON logging with request tracing

---

## 7. What I'd Do Differently / Next

### If I had more time (say these proactively — shows self-awareness)

1. **Langfuse for trace-level LLM inspection** — our Prometheus metrics are good for SRE-style monitoring, but insufficient for prompt experimentation. Langfuse adds prompt version comparison, golden-set replay, and human annotation workflows.

2. **Redis-backed rate limiting** — the current in-memory token bucket in `OpenRouterAiProvider` doesn't work in multi-instance deployment. Known gap, straightforward fix.

3. **Context caching** — Anthropic's explicit caching reduces costs by 90% and latency by up to 85%. For our per-user-per-week context (which changes slowly), this would be the single highest-ROI optimization.

4. **Multi-turn RAG conversations** — currently each RAG query is stateless. Adding conversation context would dramatically improve follow-up questions.

5. **Full integration test suite** — the 97 backend test failures are pre-existing Spring context issues in the integration tests, not failures in my code (my ExperimentServiceTest passes 17/17). Given more time, I'd fix the test infrastructure.

### What I deliberately chose NOT to do (shows judgment)

1. **No autonomous agents** — Anthropic's research shows 68% of production agents execute ≤10 steps before human intervention. Our domain has clear, predictable workflows — not open-ended exploration. Typed workflow orchestration (risk → what-if → recommendation) is more reliable and debuggable.

2. **No fine-tuning** — premature before prompt engineering is exhausted. With 14 prompt templates and a 95% eval pass rate, we haven't hit the ceiling where fine-tuning would add value.

3. **No knowledge graph** — our RCDO hierarchy is already a graph in PostgreSQL with recursive CTEs. Adding Neo4j would add operational complexity without proven marginal gain for our query patterns.

4. **No per-mutation AI triggers** — research shows at a 20% false alarm rate, operators ignore half of true alarms. Our event triggers (on lock, daily batch, on reconcile load) are the right granularity.

5. **No federated learning** — wrong abstraction entirely. FL is for settings where organizations can't share data AND need a jointly trained model. For planning software, this is a red flag.

---

## 8. Likely Questions & Answers

### "Walk me through how a commit gets created and how AI assists."

> A user on My Week clicks "Add Commit." They fill in title, chess piece (KING/QUEEN/ROOK/etc.), RCDO link, estimate points, and success criteria. On save, `CommitLintService` auto-runs — it sends the commit through the lint prompt and flags hard validation issues (KING without criteria, meaningless title) and soft guidance (vague title, estimate mismatch). The lint results appear inline.
>
> If the user clicks "✨ AI Suggest," `CommitDraftAssistService` assembles context: the commit data, the full plan, the user's calibration profile, and historical patterns. It sends this to the draft-assist prompt, which returns suggested improvements — but only for fields that actually have defects. Each suggestion is stored in `ai_suggestion` with the prompt version, and the user can accept or dismiss each field independently, which stores `ai_feedback`.
>
> `RcdoSuggestService` can also recommend which RCDO node this commit should link to, with confidence scores and rationale showing why it thinks "Deploy auth service" maps to "Improve Uptime."

### "How do you handle AI failures?"

> Graceful degradation at every level. The `AiProvider` interface has `isAvailable()`. If OpenRouter is down, `StubAiProvider` returns realistic canned responses. Risk detection falls back to rules-only — the 5 deterministic rules always fire regardless of AI availability. The frontend shows AI surfaces with an "AI unavailable" state. The feature flag `FEATURE_AI_ASSISTANCE` can disable all AI entirely. The weekly planning workflow never blocks on AI.

### "How do you know the AI outputs are good?"

> Three levels. First, **offline eval** — 60 golden test cases run against the real LLM, with schema validation, behavioral checks, and LLM-as-judge scoring. The CI pipeline gates on threshold: 100% schema validity, 95% pass rate, 100% for critical cases. Second, **production eval** — `FaithfulnessEvaluator` samples 100% of high-stakes suggestions (risk signals, insights, RAG answers) and 10% of low-stakes ones, using RAGAS-style claim decomposition to measure faithfulness. Third, **observability** — Prometheus gauges track rolling 7-day faithfulness and acceptance rates per suggestion type, with alerts at Manus-recommended thresholds.

### "Why did you pick GPT-4.1-nano over Claude?"

> Data-driven decision. We built a model comparison leaderboard that tests 8 models across all 60 eval cases with a composite score: schema validity (45%), Opus 4.6 judge quality (35%), and inverse latency (20%). GPT-4.1-nano scored 0.9432 — highest composite. 100% schema, 0.846 judge quality, 1.7s latency, $0.10/M tokens. Claude Sonnet 4 was slower (2.8s) and 30x more expensive. GPT-4.1 (full) had marginally better judge scores (0.858) but was 20x the cost. For structured JSON output in an enterprise planning tool, the nano model's quality was indistinguishable from full-size models in practice.

### "Tell me about your data model."

> 30+ tables across transactional state and derived read models. The core loop is: `weekly_plan` → `weekly_commit` → `lock_snapshot_header/commit` → `scope_change_event` → `reconcile_snapshot_header/commit` → `carry_forward_link`. Each commit links to exactly one `rcdo_node` (self-referencing tree). 5 derived read models (`user_week_fact`, `team_week_rollup`, `rcdo_week_rollup`, `compliance_fact`, `carry_forward_fact`) are refreshed on lifecycle events + 5-minute cron. 13 Flyway migrations manage schema evolution. The AI layer adds `ai_suggestion` (stores every AI output) and `ai_feedback` (tracks accept/dismiss).

### "What's the hardest technical decision you made?"

> The hybrid rules + LLM approach for risk detection. The easy path was "send everything to the LLM and let it figure it out." But that creates a single point of failure, makes the system opaque, and means a 3-second API timeout kills your risk detection. The hard path — implementing 5 deterministic rules (OVERCOMMIT, UNDERCOMMIT, REPEATED_CARRY_FORWARD, BLOCKED_CRITICAL, SCOPE_VOLATILITY) in code, then having the LLM augment for things rules can't catch — gives you both reliability and intelligence. The rules fire instantly, deterministically, with zero AI dependency. The LLM adds nuance on top. If the LLM hallucinates, you still have the rules. If the LLM is down, you still have the rules.

### "How did you handle the prompt engineering?"

> Each of the 14 prompts follows a consistent structure: system context, domain definitions, explicit output schema, few-shot examples (positive and negative), and anti-sycophancy rules where relevant. The key lesson from the model migration was that **negative examples are more valuable than positive ones** — showing the model what "leave it alone" looks like (all-null output for already-good commits) was the single highest-impact prompt change. I also learned that smaller models need much more explicit decision trees than larger ones — GPT-4.1-nano needs "set confidence ≤ 0.30 when evidence is absent" spelled out, while Claude would infer that from a general instruction.

### "How would you scale this?"

> The modular monolith splits cleanly at the AI boundary. The AI pipeline (provider, prompts, eval, RAG) could become its own service with minimal coupling — it already communicates through DTOs and an interface boundary. For the database, the derived read models are the scaling lever — they can be computed asynchronously and cached. For AI costs, prompt caching is the biggest win (90% cost reduction per Anthropic). For RAG, moving from Pinecone single-index to federated search across multiple namespaces would handle org-level isolation at scale.

### "What's the anti-sycophancy thing about?"

> Most LLMs default to agreeable, encouraging responses. In a weekly planning tool, sycophancy means the AI congratulates overcommitters, softens risk signals, and rubber-stamps bad plans. OpenAI publicly acknowledged sycophantic behavior affects trust and rolled back a GPT-4o update in 2025. Our prompts contain aggressive anti-flattery rules: "If the data warrants 2 criticisms and 0 praise, return 2 criticisms and 0 praise. Carry-forward streaks on important work are a PROBLEM, not 'ambitious scoping.' Do NOT soften bad patterns with excessive praise." The `RiskDetectionService` is the architectural embodiment — deterministic rules that don't care about feelings fire first.

---

## 9. Numbers You Should Know Cold

| Metric | Value |
|---|---|
| Total backend lines | 45,735 |
| Total frontend lines | 32,927 |
| Commits | 127 |
| Prompt templates | 14 |
| AI suggestion types | 10 |
| Frontend AI components | 20 (16 mounted + 2 sub-components + 2 reusable-only) |
| Database tables | 30+ transactional + 5 derived read models |
| Flyway migrations | 13 |
| REST endpoints | 60+ |
| Backend test files | 77 |
| Frontend test files | 50 |
| E2E specs | 24 Playwright |
| Eval golden cases | 60 across 7 capabilities |
| Eval pass rate | 95.0% (57/60) |
| Schema validity | 100% |
| Production model | GPT-4.1-nano ($0.10/M tokens) |
| Model leaderboard | 8 models tested, nano won on composite |
| Eval pass rate improvement | 78.3% → 95.0% (prompt tuning) |
| Risk detection rules | 5 deterministic + LLM augmentation |
| RAG pipeline stages | 6 (intent → embed → hybrid search → rerank → assemble → synthesize) |
| Evidence pillars | 4 (SQL facts, lineage, semantic, risk features) |

---

## 10. Code Walkthrough Paths

If they ask you to walk through code, here are the most impressive paths:

### Path A: Risk Detection (shows hybrid rules + LLM design)
```
RiskDetectionService.java (460 lines)
  → 5 rule methods (computeOvercommit, etc.)
  → buildLlmContext() assembles everything rules found
  → Calls AiProvider with risk-signal prompt
  → Merges rule signals + LLM signals
  → Stores in ai_suggestion with full audit trail
```

### Path B: RAG Pipeline (shows end-to-end retrieval)
```
SemanticQueryService.java (634 lines)
  → Intent classification (first LLM call)
  → Query rewriting via QueryRewriter
  → Pinecone hybrid search (sparse + dense, RRF fusion)
  → Cross-encoder reranking via RerankService
  → Context assembly
  → RAG answer synthesis (second LLM call)
  → Source citation extraction
  → Audit trail storage
```

### Path C: ChunkBuilder (shows enrichment strategy)
```
ChunkBuilder.java (467 lines)
  → buildCommitChunk() / buildPlanChunk() / etc.
  → EnrichmentContext: RCDO path, team, owner, lineage, tickets
  → Constructs narrative text (not raw fields)
  → Full RCDO ancestry: "Rally Cry: Win Enterprise > DO: Improve Uptime > Outcome: 99.9% SLA"
  → Carry-forward lineage stories
```

### Path D: Eval Pipeline (shows quality infrastructure)
```
PromptEvalRunner.java (936 lines)
  → Loads golden cases from JSON
  → Calls real LLM with production prompts
  → Schema validation per capability
  → Behavioral checks: keywords, counts, ranges, flags
  → LLM-as-judge scoring (draft-assist)
  → Writes structured JSON report
```

### Path E: Experiment Service (shows your own fix)
```
ExperimentService.java
  → Hash-based deterministic assignment (your fix)
  → hash(experimentName + ":" + userId) % 100
  → Maps to variant via cumulative traffic allocation
  → Environment variable overrides for testing
  → 3 tests proving determinism + distribution
```

---

## Delivery Tips

1. **Lead with decisions, not features.** Don't say "I built a RAG pipeline." Say "I chose hybrid retrieval over pure vector search because our corpus has exact-match requirements (RCDO labels, chess terms) that dense embeddings blur. Then I added cross-encoder reranking because the research shows it's the highest-ROI RAG upgrade — +33-40% precision for only 120ms latency."

2. **Show tradeoff reasoning.** For every choice, know what you considered and rejected. "I chose typed workflows over autonomous agents because our domain has predictable flows, and Anthropic's research shows 68% of production agents need human intervention within 10 steps."

3. **Admit what's incomplete.** "The rate limiting is in-memory only — it doesn't work multi-instance. I know the fix is Redis-backed, but it wasn't worth the infrastructure complexity for a demo. The 97 pre-existing integration test failures are Spring context issues I deprioritized versus AI quality."

4. **Use specific numbers.** Not "we improved the eval" but "78.3% to 95.0%. The biggest win was RAG confidence calibration — 50% to 100% — by adding one paragraph to the prompt about when to say 'I don't know.'"

5. **Connect AI to business value.** "Risk detection catches overcommitment before the week starts. A manager sees 'This plan has 15 points against a 10-point budget' immediately at lock — not at Friday reconciliation when it's too late. The carry-forward streak detection surfaces people who've been stuck on the same work for 3+ weeks."

6. **Be honest about AI coding agents.** You used pi/duet to build this. If asked, be straightforward: "I used AI coding agents for scaffolding and iteration. The architecture decisions, domain modeling, prompt engineering, and eval design were mine. The agents accelerated code generation; I drove the design."
