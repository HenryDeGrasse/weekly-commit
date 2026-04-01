# AI Eval Results

This document summarises the evaluation approach, per-capability test coverage,
and historical replay benchmark results for the Weekly Commit AI capabilities.

---

## Evaluation Approach

The eval suite operates at two complementary levels:

### 1. LLM Prompt Evaluation (Golden Cases)

Each AI capability has a `cases.json` fixture file under
`backend/src/test/resources/eval/<capability>/`. Test cases are executed by
`PromptEvalRunner` against the real OpenRouter LLM and scored on:

- **Schema validity** — does the response parse to the expected JSON shape?
- **Behavioral checks** — do keywords appear, are counts in range, etc.?
- **LLM judge scoring** (commit-draft-assist only) — a secondary LLM rates
  title quality, criteria measurability, and tone.

These tests run via `./gradlew evalTest` (requires `OPENROUTER_API_KEY`).
They are excluded from `./gradlew test` with `@Tag("eval")`.

### 2. Historical Replay Benchmark (Rules Engine)

`HistoricalReplayBenchmark` replays the 5 rules-based risk signals against 12
synthetic RECONCILED plans built entirely in memory (no Spring context, no DB).
It compares predicted signals to known outcomes and computes per-signal
confusion matrices → precision, recall, and F1.

Results are written to `build/eval-results/replay-benchmark.json`.
The placeholder metrics table below is populated by running `./gradlew evalTest`.

---

## Per-Capability Eval Coverage

| Capability | File | Cases | Notes |
|---|---|---|---|
| Commit Draft Assist | `eval/commit-draft-assist/cases.json` | 12 | LLM judge scoring on title + criteria |
| Commit Lint | `eval/commit-lint/cases.json` | 6 | Hard validation + soft guidance checks |
| RCDO Suggest | `eval/rcdo-suggest/cases.json` | 10 | Node ID correctness, confidence range |
| Risk Signal | `eval/risk-signal/cases.json` | 8 | AI-only signals (rules already computed) |
| Reconcile Assist | `eval/reconcile-assist/cases.json` | 8 | Outcome suggestions, carry-forwards |
| RAG Query | `eval/rag-query/cases.json` | 8 | Answer quality, source citations |
| What-If Narration | `eval/what-if/cases.json` | 8 | Narrative mentions, length, recommendation |
| **Total** | | **60** | |

---

## LLM Prompt Eval Results

Last run: 2026-03-31 (model: `openai/gpt-4.1-nano` via OpenRouter).
Machine-readable output: `backend/build/eval-results/eval-2026-04-01T03-33-19.159529Z.json`

**Overall: 57/60 passed (95.0%) — 100% schema validity across all 60 cases.**

| Capability | Cases | Passed | Rate | Notes |
|---|---|---|---|---|
| Commit Draft Assist | 12 | 11 | 92% | Occasional over-suggestion on already-good titles |
| Commit Lint | 6 | 5 | 83% | Rare misclassification of hard vs soft validation severity |
| RCDO Suggest | 10 | 10 | 100% | ✅ All node matches and confidence ranges correct |
| Risk Signal | 8 | 7 | 88% | Occasional empty signals for subtle concentration risks |
| Reconcile Assist | 8 | 8 | 100% | ✅ All outcomes, carry-forwards, and summaries correct |
| RAG Query | 8 | 8 | 100% | ✅ All answers, confidence calibration, and source citations correct |
| What-If Narration | 8 | 8 | 100% | ✅ All narrative, recommendation, and length checks pass |

**Remaining failures (3/60) are LLM stochastic variance** — different cases fail
on different runs due to GPT-4.1-nano's non-deterministic output. The ~5% noise
floor is expected for a nano-class model. Key prompt improvements from this round:

- **Draft Assist:** Added explicit per-field null-threshold rules and two new
  "already perfect" few-shot examples (KING + PAWN). Null-return compliance
  improved from 58% → 92%.

- **Commit Lint:** Added QUEEN_LIMIT_EXCEEDED and KING_LIMIT_EXCEEDED examples,
  fragmentation example, and stronger ROOK/BISHOP/KNIGHT/PAWN exclusion note.
  Improved from 67% → 83%.

- **RAG Query:** Added strict confidence calibration rules (≤0.30 when evidence
  is absent). Improved from 50% → 100%.

- **Risk Signal:** Added single-commit concentration risk pattern and example.
  Improved from 88% (missed concentration) to 88% (stochastic miss only).

All failures are schema-valid JSON with correct structure — the residual issues
are behavioral variance (non-deterministic LLM judgment), not structural.

---

## Historical Replay Benchmark

The benchmark uses 12 deterministic synthetic plans covering all 5 risk signal
types (2 per signal: one TP, one FP, plus cross-cutting FN/TN cases).

### Signal-to-Outcome Mappings

| Signal | Predicted when | True Positive when |
|---|---|---|
| OVERCOMMIT | `totalPoints > budget` | average completion rate < 70% |
| UNDERCOMMIT | `totalPoints / budget < 0.60` | spare capacity AND all commits ACHIEVED |
| REPEATED_CARRY_FORWARD | any commit with `carryForwardStreak ≥ 2` | flagged commit outcome = NOT_ACHIEVED or CANCELED |
| BLOCKED_CRITICAL | KING/QUEEN commit with BLOCKED ticket ≥ 48 h | flagged commit outcome = NOT_ACHIEVED |
| SCOPE_VOLATILITY | `scopeChangeCount > 3` | any commit outcome = NOT_ACHIEVED |

### Benchmark Results

Last run: 2026-03-31. Machine-readable output: `build/eval-results/replay-benchmark.json`

| Signal | Predicted | Actual | TP | FP | FN | TN | Precision | Recall | F1 |
|---|---|---|---|---|---|---|---|---|---|
| OVERCOMMIT | 2 | 2 | 1 | 1 | 1 | 9 | 0.500 | 0.500 | 0.500 |
| UNDERCOMMIT | 2 | 1 | 1 | 1 | 0 | 10 | 0.500 | 1.000 | 0.667 |
| REPEATED_CARRY_FORWARD | 2 | 1 | 1 | 1 | 0 | 10 | 0.500 | 1.000 | 0.667 |
| BLOCKED_CRITICAL | 2 | 1 | 1 | 1 | 0 | 10 | 0.500 | 1.000 | 0.667 |
| SCOPE_VOLATILITY | 2 | 5 | 1 | 1 | 4 | 6 | 0.500 | 0.200 | 0.286 |

*Counts across 12 synthetic RECONCILED plans. Precision = TP / (TP + FP),
Recall = TP / (TP + FN), F1 = harmonic mean of precision and recall.*

**Analysis:** All 5 signals achieve 50% precision — the benchmark intentionally
includes one TP and one FP scenario per signal to test both directions. Four of
five signals achieve perfect recall (1.0); SCOPE_VOLATILITY's low recall (0.20)
reflects that many plans have NOT_ACHIEVED commits from causes other than scope
churn (OVERCOMMIT, BLOCKED_CRITICAL), creating false negatives under the current
signal-to-outcome mapping. This is a known limitation of the mapping heuristic,
not the signal itself — the signal correctly fires when scope changes exceed the
threshold, but the outcome condition ("any NOT_ACHIEVED") is too broad.

---

## Model Comparison Leaderboard

We run a periodic N-way shootout to pick the best OpenRouter model for
production use. The runner (`MultiModelEvalRunner`, `@Tag("model-compare")`)
evaluates every model across all 7 eval datasets (60 cases total) and ranks
them by a composite score:

```
composite = schemaPassRate x 0.45
          + meanJudgeScore x 0.35   (Opus 4.6 judge, first 4 cases per dataset)
          + (1 - normLatency) x 0.20
```

### Run 3 — 2026-03-31 (full 8-model shootout)

**Models tested:** `gpt-4o`, `gpt-4.1`, `gpt-4.1-mini`, `gpt-4.1-nano`,
`gpt-5-mini`, `gpt-5-nano`, `gpt-5.4-nano`, `gemini-2.5-flash`

| Rank | Model | Composite | Schema | Latency | Opus Judge | Price/M in |
|------|-------|-----------|--------|---------|-----------|------------|
| 1 (winner) | **gpt-4.1-nano** | **0.9432** | 100% | 1,704ms | 0.846 | **$0.10** |
| 2 | gpt-5.4-nano | 0.9422 | 100% | 1,472ms | 0.835 | $0.20 |
| 3 | gpt-4.1 | 0.9384 | 100% | 2,392ms | **0.858** | $2.00 |
| 4 | gpt-4o | 0.9349 | 100% | 1,908ms | 0.830 | $2.50 |
| 5 | gemini-2.5-flash | 0.9274 | 100% | **1,474ms** | 0.793 | ~$0.15 |
| 6 | gpt-4.1-mini | 0.9010 | 100% | 2,118ms | 0.741 | $0.40 |
| disq. | gpt-5-mini | 0.6158 | 60% | 13,559ms | 0.861 | $0.25 |
| disq. | gpt-5-nano | 0.2133 | 27% | 17,009ms | n/a | $0.05 |

**Winner: `openai/gpt-4.1-nano`** — 100% schema, 0.846 judge quality, 1.7s
latency, $0.10/M input (25x cheaper than gpt-4o, 3x cheaper than Gemini Flash).
`gpt-5-mini` and `gpt-5-nano` disqualified: too slow and unreliable via
OpenRouter as of this date.

**Production model updated to `openai/gpt-4.1-nano`** across `.env`,
`application.yml` fallback, and `evalTest` Gradle task default.

### Run 2 — 2026-03-30 (quality scoring, 2 datasets)

Quick quality check on commit-draft + rag-query with Opus 4.6 judge:
`gpt-4o` led on quality (0.833), `gemini-2.5-flash` was fastest (1,248ms).

### Run 1 — 2026-03-30 (schema-only baseline, 7 models)

Schema-only run across all 7 datasets. Every model hit 100% schema pass rate.
`gemini-2.5-flash` fastest at 1,248ms; `claude-sonnet-4` slowest at 2,779ms.

### How to run the leaderboard

```bash
# Full run — all 7 datasets, Opus 4.6 judge on first 4 cases per dataset (~4 min)
cd backend && ./gradlew modelCompareTest

# Quick quality check — commit-draft + rag-query only (~2 min)
cd backend && QUICK=true ./gradlew modelCompareTest

# Custom model list
cd backend && COMPARE_MODELS="openai/gpt-4.1-nano,openai/gpt-4.1" ./gradlew modelCompareTest
```

Results land in `backend/build/eval-results/model-compare-<timestamp>.json`.

---

## How to Run

### Unit tests (no LLM, no API key required)

```bash
# Run all standard unit tests (excludes @Tag("eval"))
npm test
# Backend only
cd backend && ./gradlew test
```

### Eval tests (requires OPENROUTER_API_KEY)

```bash
# Run LLM prompt eval + historical replay benchmark
export OPENROUTER_API_KEY=<your-key>
cd backend && ./gradlew evalTest

# Or via npm alias
npm run eval:run
```

Eval reports are written to:
- `backend/build/eval-results/eval-<timestamp>.json` — LLM eval per-case results
- `backend/build/eval-results/replay-benchmark.json` — rules-engine benchmark

### Regression threshold check

After running evals, compare against the baseline:

```bash
npm run eval:check
```

This compares the latest eval run against `eval-baseline.json` and
`eval-thresholds.json` at the repo root and exits non-zero if pass rates drop.
