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

> Run `./gradlew evalTest` to populate this table with actual values.
> Machine-readable output: `build/eval-results/replay-benchmark.json`

| Signal | Predicted | Actual | TP | FP | FN | TN | Precision | Recall | F1 |
|---|---|---|---|---|---|---|---|---|---|
| OVERCOMMIT | — | — | — | — | — | — | — | — | — |
| UNDERCOMMIT | — | — | — | — | — | — | — | — | — |
| REPEATED_CARRY_FORWARD | — | — | — | — | — | — | — | — | — |
| BLOCKED_CRITICAL | — | — | — | — | — | — | — | — | — |
| SCOPE_VOLATILITY | — | — | — | — | — | — | — | — | — |

*Table cells show counts across 12 synthetic plans. Precision = TP / (TP + FP),
Recall = TP / (TP + FN), F1 = harmonic mean of precision and recall.*

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
