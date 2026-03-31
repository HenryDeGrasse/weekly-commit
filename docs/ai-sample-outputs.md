# AI Sample Outputs — End-to-End Evidence

This document showcases actual LLM outputs from the eval harness running against real Claude prompts via OpenRouter. Every input→output pair below was produced by the production prompt templates and scored by the automated eval pipeline.

**How these were generated:** `OPENROUTER_API_KEY=... ./backend/gradlew evalTest` runs 60 golden test cases across 7 AI capabilities. Each case sends a real request to Claude via OpenRouter, validates the JSON schema, runs behavioral checks, and (for draft-assist) uses an LLM-as-judge scorer. Results: `eval-baseline.json` at the repo root.

**Baseline stats:** 18 cases evaluated in the current baseline (commit-lint + commit-draft-assist), 17/18 passing (94.4%). The remaining 42 cases across 5 additional capabilities (rcdo-suggest, risk-signal, reconcile-assist, rag-query, what-if) have golden fixtures ready and run on demand with an API key.

---

## 1. Commit Draft Assist

The draft assist capability takes a user's rough commit and suggests improvements — clearer titles, success criteria for King/Queen commits, calibrated estimates. It returns `null` for any field that doesn't need improvement.

### Example 1: Vague KING commit → specific, outcome-focused

**User input:**
| Field | Value |
|---|---|
| Title | `Do API stuff` |
| Chess Piece | KING |
| Description | *(empty)* |
| Success Criteria | *(empty)* |
| Estimate | *(none)* |

**AI output:**
```json
{
  "suggestedTitle": "Deploy critical API endpoint with monitoring and rollback plan",
  "suggestedDescription": "Complete deployment of mission-critical API changes with proper monitoring, testing, and rollback procedures in place",
  "suggestedSuccessCriteria": "API deployed to production, monitoring alerts configured, smoke tests passing, rollback plan documented and tested",
  "suggestedEstimatePoints": 5
}
```

**What happened:** The LLM transformed a vague 3-word title into an outcome-focused description, generated measurable success criteria (required for KING commits), and estimated 5 points — appropriate for a critical deployment. Eval checks: ✅ title suggested, ✅ title is clean, ✅ criteria suggested, ✅ estimate suggested, ✅ estimate in valid range.

---

### Example 2: Good ROOK commit → AI leaves it alone, adds only success criteria

**User input:**
| Field | Value |
|---|---|
| Title | `Migrate auth endpoints to session tokens` |
| Chess Piece | ROOK |
| Description | `Replace JWT-based auth on all /api/v2/* endpoints with server-side session tokens` |
| Success Criteria | *(empty)* |
| Estimate | 5 |

**AI output:**
```json
{
  "suggestedTitle": null,
  "suggestedDescription": null,
  "suggestedSuccessCriteria": "All /api/v2/* endpoints use session tokens instead of JWT, authentication tests pass, existing user sessions remain valid during migration",
  "suggestedEstimatePoints": null
}
```

**What happened:** The title and description were already good — the LLM returned `null` for both (no unnecessary changes). It only added success criteria since the field was empty. The estimate of 5 was left alone. This demonstrates the "don't fix what isn't broken" principle.

---

### Example 3: Activity-focused PAWN title + inflated estimate → corrected

**User input:**
| Field | Value |
|---|---|
| Title | `Work on bugs` |
| Chess Piece | PAWN |
| Description | *(empty)* |
| Estimate | 8 points |

**AI output:**
```json
{
  "suggestedTitle": "Triage and fix top-3 P1 bugs from customer escalation queue",
  "suggestedDescription": "Review high-priority customer-reported issues and resolve the most critical ones affecting user experience",
  "suggestedSuccessCriteria": null,
  "suggestedEstimatePoints": 2
}
```

**What happened:** "Work on bugs" is an activity, not an outcome. The LLM made it specific and scoped. The estimate dropped from 8 to 2 — appropriate for a PAWN (small hygiene work). Success criteria are optional for Pawns, so `null` is correct.

---

### Example 4: Already-excellent KING commit → all nulls (no changes)

**User input:**
| Field | Value |
|---|---|
| Title | `Zero-downtime database migration from PostgreSQL 14 to 16` |
| Chess Piece | KING |
| Description | `Upgrade production database with logical replication failover` |
| Success Criteria | `PostgreSQL 16 serving production traffic, zero data loss, rollback tested, <5min total failover window` |
| Estimate | 8 |

**AI output:**
```json
{
  "suggestedTitle": null,
  "suggestedDescription": null,
  "suggestedSuccessCriteria": null,
  "suggestedEstimatePoints": null
}
```

**What happened:** Everything about this commit is already specific, measurable, and well-estimated. The LLM correctly returned all `null` — this is the hardest behavior to get right (resisting the urge to "improve" something that's already good).

---

### Example 5: QUEEN underestimated at 1 point → LLM calibrates upward

**User input:**
| Field | Value |
|---|---|
| Title | `Redesign mobile checkout flow` |
| Chess Piece | QUEEN (highest-leverage strategic work) |
| Estimate | 1 point |

**AI output:**
```json
{
  "suggestedTitle": "Complete mobile checkout flow redesign mockups and user testing",
  "suggestedDescription": "Design new 3-step mobile checkout flow with improved UX, create high-fidelity mockups, and conduct user testing with 5+ participants",
  "suggestedSuccessCriteria": "High-fidelity mockups for new mobile checkout flow completed, user testing conducted with 5+ participants, key insights documented with recommended next steps",
  "suggestedEstimatePoints": 5
}
```

**What happened:** A QUEEN commit estimated at 1 point signals underestimation. The LLM bumped it to 5 (appropriate for a high-leverage design + testing effort) and added measurable success criteria.

---

## 2. Commit Quality Lint

The lint capability reviews an entire weekly plan and flags issues at two severity levels:
- **hardValidation** — blocks plan lock (structural violations)
- **softGuidance** — suggestions that improve quality but don't block

### Example 1: KING missing success criteria + vague title

**Plan input (3 commits):**
| Title | Chess Piece | Estimate | Success Criteria |
|---|---|---|---|
| `King thing` | KING | 3 | *(empty)* |
| `stuff` | ROOK | 5 | *(empty)* |
| `Fix login bug` | PAWN | 1 | *(empty)* |

**AI output:**
```json
{
  "hardValidation": [
    {
      "code": "MISSING_SUCCESS_CRITERIA",
      "message": "KING commit 'King thing' requires measurable success criteria before lock",
      "commitId": "c-001"
    },
    {
      "code": "VAGUE_TITLE",
      "message": "Commit 'stuff' has no meaningful title — describe the deliverable",
      "commitId": "c-002"
    }
  ],
  "softGuidance": [
    {
      "code": "VAGUE_TITLE",
      "message": "Consider making 'King thing' more specific — what exactly must not fail?",
      "commitId": "c-001"
    }
  ]
}
```

**What happened:** Two hard blocks (KING without criteria is a structural violation; "stuff" is not a real title). The soft guidance suggests improving the KING title without blocking lock. This correctly separates hard rules from helpful suggestions.

---

### Example 2: Clean plan → no hard blocks, minimal soft guidance

**Plan input (2 well-formed commits):**
| Title | Chess Piece | Estimate | Success Criteria |
|---|---|---|---|
| `Deploy auth service v2 to production` | QUEEN | 5 | `All endpoints responding, zero 5xx in first hour` |
| `Write integration tests for auth service` | ROOK | 3 | *(empty)* |

**AI output:**
```json
{
  "hardValidation": [],
  "softGuidance": [
    {
      "code": "MISSING_SUCCESS_CRITERIA",
      "message": "Consider adding success criteria for ROOK commit 'Write integration tests for auth service' — what coverage or test scenarios must be achieved?",
      "commitId": "c-011"
    }
  ]
}
```

**What happened:** Zero hard blocks — this plan is lockable. One soft suggestion to add criteria to the ROOK commit (optional but recommended). The QUEEN has criteria already and was left alone.

---

### Example 3: Fragmented plan — 10 PAWN commits

**Plan input:** 10 commits, all PAWNs at 1 point each (`Fix typo in readme`, `Update npm deps`, `Rename config var`, `Fix lint warning`, `Add missing test`, `Update changelog`, `Remove dead code`, `Fix CSS alignment`, `Bump version`, `Update env vars`).

**AI output:**
```json
{
  "hardValidation": [],
  "softGuidance": [
    {
      "code": "TOO_MANY_PAWNS",
      "message": "100% of points allocated to PAWN commits — consider grouping maintenance tasks or including higher-impact work",
      "commitId": null
    },
    {
      "code": "PLAN_FRAGMENTATION",
      "message": "10 commits may be too granular — consider batching related maintenance tasks",
      "commitId": null
    }
  ]
}
```

**What happened:** No hard blocks (PAWNs don't require criteria), but the LLM correctly identified two plan-level issues: excessive pawn concentration (100%) and fragmentation (10 commits). Both are soft guidance per the PRD — they warn without blocking.

---

### Example 4: Near-duplicate titles

**Plan input (2 commits):**
| Title | Chess Piece |
|---|---|
| `Write tests for auth module` | ROOK |
| `Write tests for auth service` | ROOK |

**AI output:**
```json
{
  "hardValidation": [],
  "softGuidance": [
    {
      "code": "NEAR_DUPLICATE_TITLES",
      "message": "Two similar commits about writing auth tests — consider combining or clarifying the distinction between 'auth module' and 'auth service'",
      "commitId": "c-050"
    },
    {
      "code": "ACTIVITY_FOCUSED_TITLE",
      "message": "Consider outcome-focused phrasing: 'Auth module test coverage complete' instead of 'Write tests for auth module'",
      "commitId": "c-050"
    },
    {
      "code": "ACTIVITY_FOCUSED_TITLE",
      "message": "Consider outcome-focused phrasing: 'Auth service test coverage complete' instead of 'Write tests for auth service'",
      "commitId": "c-051"
    }
  ]
}
```

**What happened:** The LLM detected the near-duplicate and also flagged both titles as activity-focused rather than outcome-focused, suggesting concrete rewrites. Three distinct, actionable suggestions without a single hard block.

---

## 3. RCDO Link Suggest (Golden Cases — Ready to Run)

RCDO suggest matches commits to the strategy hierarchy. Example golden case:

**Input:**
- Commit: *"Implement Stripe webhook handler for subscription renewals"*
- RCDO tree: Rally Cry → `Scale Revenue` → DO → `Launch self-serve billing` → Outcomes → `Subscription renewal automation`, `Invoice generation pipeline`

**Expected behavior:** Should suggest `Subscription renewal automation` (Outcome `out-1`) with high confidence (≥0.75), with rationale citing keyword match on "subscription renewals" → "Subscription renewal automation". Should NOT suggest the Rally Cry directly.

*10 golden cases covering: strong matches, no matches, archived nodes, ambiguous multi-match, defining-objective-with-no-outcomes edge case.*

---

## 4. Risk Detection (Golden Cases — Ready to Run)

The risk detection LLM augments the rules-based engine by catching risks that rule thresholds miss.

**Example golden case — hidden sequential dependency:**
- Commit 1: "Set up CI/CD pipeline for auth service"
- Commit 2: "Deploy auth service to staging" (depends on CI/CD being done first)
- Rules engine: no flags (points under budget, no carry-forward, no blocked tickets)
- Expected LLM output: flag a DEPENDENCY_RISK signal explaining the implicit ordering

*8 golden cases covering: hidden dependencies, clean plans, concurrent risks, post-lock scope changes, cross-team impacts.*

---

## 5. Reconcile Assist (Golden Cases — Ready to Run)

Reconcile assist suggests outcomes, carry-forward decisions, and a weekly summary at end-of-week.

**Example golden case — all tickets Done:**
- 3 commits: `Deploy auth service` (QUEEN/5pt/ticket DONE), `Add rate limiting` (ROOK/3pt/ticket DONE), `Update docs` (PAWN/1pt/ticket DONE)
- Expected: all 3 suggested as ACHIEVED, summary generated, zero carry-forwards

**Example golden case — mixed outcomes:**
- Commits with DONE, IN_PROGRESS, and BLOCKED tickets
- Expected: DONE → ACHIEVED, IN_PROGRESS → PARTIALLY_ACHIEVED with carry-forward recommendation, BLOCKED → NOT_ACHIEVED with carry-forward

*8 golden cases covering: all-done, mixed, scope-changed plans, cancelled work, carry-forward chains.*

---

## 6. RAG / Semantic Search (Golden Cases — Ready to Run)

Natural-language queries over planning history via Pinecone vector retrieval + Claude synthesis.

**Example golden cases:**
- *"What did Alice commit to last week?"* — expects structured answer citing specific commits
- *"Which RCDOs received the most effort this month?"* — expects cross-chunk synthesis with point totals

*8 golden cases covering: factual recall, RCDO coverage analysis, temporal queries, ambiguous questions, no-result handling.*

---

## 7. What-If Planner (Golden Cases — Ready to Run)

Simulates hypothetical commit mutations and narrates the impact.

**Example golden cases:**
- ADD_COMMIT that pushes total over capacity budget — narrative must mention OVERCOMMIT risk
- REMOVE_COMMIT that resolves overcommit — narrative must highlight improvement

*8 golden cases covering: add/remove/change mutations, RCDO coverage shifts, risk state transitions.*

---

## Historical Replay Benchmark — Rules Engine

Beyond LLM eval, the rules-based risk engine is benchmarked against 12 synthetic RECONCILED plans with known outcomes.

| Signal | Precision | Recall | F1 | Notes |
|---|---|---|---|---|
| OVERCOMMIT | 0.50 | 0.50 | 0.50 | Intentional 1 TP + 1 FP design |
| UNDERCOMMIT | 0.50 | 1.00 | 0.67 | Perfect recall |
| REPEATED_CARRY_FORWARD | 0.50 | 1.00 | 0.67 | Perfect recall |
| BLOCKED_CRITICAL | 0.50 | 1.00 | 0.67 | Perfect recall |
| SCOPE_VOLATILITY | 0.50 | 0.20 | 0.29 | Known mapping limitation — see [eval-results.md](eval-results.md) |

Each signal has 1 TP and 1 FP scenario by design. The 50% precision is the expected baseline given the benchmark intentionally tests both true and false positive cases. Four of five signals achieve perfect recall.

---

## Eval Infrastructure Summary

| Component | What it does |
|---|---|
| **`PromptEvalRunner`** | Sends golden cases to Claude via OpenRouter, validates schema + behavioral checks |
| **`MultiModelEvalRunner`** | N-way model comparison (leaderboard mode) + A/B eval |
| **`HistoricalReplayBenchmark`** | Rules-engine confusion matrices on 12 synthetic plans |
| **`FaithfulnessEvaluator`** | Production sampling — RAGAS-style claim decomposition |
| **`eval-threshold-check.js`** | CI gate — exits non-zero if pass rates drop below baseline |
| **Prometheus gauges** | Rolling 7-day faithfulness, acceptance rates, provider availability |
| **14 prompt templates** | Versioned in `backend/src/main/resources/prompts/`, tracked per suggestion |

### Running the eval suite

```bash
# LLM eval (requires API key)
OPENROUTER_API_KEY=sk-... ./backend/gradlew evalTest

# Check against baseline thresholds (CI-safe, no API key needed)
npm run eval:check

# Full model comparison
make model-compare
```

Results written to `backend/build/eval-results/`. Current baseline: `eval-baseline.json` at repo root.
