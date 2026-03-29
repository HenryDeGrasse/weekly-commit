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
 4. Step 8 CalibrationService — correctly notes UserWeekFact only has kingCount/queenCount aggregates, requiring WeeklyCommitRepository for per-chess-piece rates
 5. Step 12 GET/POST separation — the stable-ID design with getRecommendations() (read-only) vs generateAndPersistRecommendations() (refresh) is well thought out for frontend reliability
 6. Graceful degradation is consistently specified throughout
 7. Scope boundaries (backend-only / frontend-only per step) are clear

 ────────────────────────────────────────────────────────────────────────────────

 ### Summary

 ┌─────────────────────────────────────┬─────────────────────┬──────────────────────────────────────────────┐
 │ Issue                               │ Severity            │ Action                                       │
 ├─────────────────────────────────────┼─────────────────────┼──────────────────────────────────────────────┤
 │ Step 8 JPQL JOIN on non-mapped UUID │ 🔴 Won't compile    │ Fix to native query or implicit join         │
 ├─────────────────────────────────────┼─────────────────────┼──────────────────────────────────────────────┤
 │ Step 5 LLM reranking latency        │ 🟡 Performance risk │ Default enabled=false, document Cohere setup │
 ├─────────────────────────────────────┼─────────────────────┼──────────────────────────────────────────────┤
 │ Step 10 unbounded decomposition     │ 🟡 Edge case risk   │ Add max sub-query cap (e.g., 3)              │
 ├─────────────────────────────────────┼─────────────────────┼──────────────────────────────────────────────┤
 │ Step 4 null sparseValues in upsert  │ 🟢 Already handled  │ Looks fine as-is                             │
 └─────────────────────────────────────┴─────────────────────┴──────────────────────────────────────────────┘

 My recommendation: Fix the Step 8 query syntax, add the decomposition cap to Step 10, and consider flipping the reranking default — then this plan is ready to execute. The level of detail is excellent for
 agent-driven implementation.
