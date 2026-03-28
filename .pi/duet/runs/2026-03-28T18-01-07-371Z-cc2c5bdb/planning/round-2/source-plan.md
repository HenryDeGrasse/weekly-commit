# Goal

Implement enforceable AI quality thresholds by: (1) adding a `critical` field to eval cases, (2) wiring the existing title-quality and criteria-quality LLM judge prompts into `PromptEvalRunner` so draft-assist cases collect real judge scores, (3) committing the most recent eval output as a regression baseline, (4) creating an `eval-thresholds.json` config, (5) building a threshold enforcement script that reads eval results and compares against the config + baseline, and (6) wiring that script into the GitLab `eval:llm-judge` job. The result is a pipeline where deterministic checks hard-gate, judge scores soft-gate with clear pass/fail reporting, and regressions against baseline are detected.

## Constraints

- Backend only for steps 1-3 — do NOT modify any frontend files
- All Java code must pass `cd backend && ./gradlew spotlessCheck` (Eclipse JDT formatter)
- The existing `PromptEvalRunner` parameterized test structure must be preserved — extend it, don't replace it
- Judge scoring must be optional: if `OPENROUTER_API_KEY` is not set, judge dimensions are skipped (not failed)
- The enforcement script (step 5) must be a standalone script runnable outside Gradle — use Node.js (`scripts/eval-threshold-check.js`) since Node 20 is already a project dependency
- The eval runner already uses `@Tag("eval")` and is excluded from normal `./gradlew test` — keep that behavior
- Do NOT modify the existing `FaithfulnessEvaluator.java` (runtime judge) — it's separate from the offline eval pipeline
- Eval result artifacts write to `backend/build/eval-results/` — keep that path

## Steps

### Step 1: Add `critical` field to EvalCase and mark critical cases in fixture JSON

Add an optional `critical` boolean field to `backend/src/test/java/com/weeklycommit/ai/eval/EvalCase.java`. Default to `false` when absent in JSON (the `@JsonIgnoreProperties(ignoreUnknown = true)` annotation already handles missing fields, but the record needs the field).

Then update the two fixture files to mark critical cases:
- `backend/src/test/resources/eval/commit-draft-assist/cases.json` — mark `draft-001` (vague KING), `draft-004` (QUEEN missing criteria), `draft-008` (fully specified KING should return nulls) as `"critical": true`
- `backend/src/test/resources/eval/commit-lint/cases.json` — mark `lint-001` (KING missing criteria + vague title), `lint-003` (2 KING commits), `lint-004` (3 QUEEN commits) as `"critical": true`

Update `EvalResult.java` to track whether the case was critical (add a `boolean critical` field set at construction time). Update `PromptEvalRunner` to pass `evalCase.critical()` through to the `EvalResult` constructor, and include it in the JSON report output.

**Scope:** Only modify files in `backend/src/test/`. Do NOT modify any file in `backend/src/main/` or `frontend/`.

**Deliverables:**
- `EvalCase.java` — record gains `boolean critical` field (defaults `false`)
- `EvalResult.java` — gains `boolean critical` field, included in `toString()` and JSON serialization
- `PromptEvalRunner.java` — passes `evalCase.critical()` to `EvalResult` constructor
- `commit-draft-assist/cases.json` — 3 cases marked critical
- `commit-lint/cases.json` — 3 cases marked critical

**Checks:** build

### Step 2: Wire title-quality and criteria-quality LLM judges into PromptEvalRunner for draft-assist cases

Extend `PromptEvalRunner.evaluateCommitDraftAssist()` to call the title-quality judge (when `suggestedTitle` is non-null) and the criteria-quality judge (when `suggestedSuccessCriteria` is non-null) using the existing `OpenRouterAiProvider`.

Read the judge prompt templates from:
- `backend/src/test/resources/eval/judge-prompts/title-quality-judge.txt`
- `backend/src/test/resources/eval/judge-prompts/criteria-quality-judge.txt`

For each, substitute the placeholders (`{originalTitle}`, `{suggestedTitle}`, `{chessPiece}`, `{commitTitle}`, `{suggestedCriteria}`), send to the LLM via `provider.generateSuggestion()` using a new context type `"TITLE_QUALITY_JUDGE"` / `"CRITERIA_QUALITY_JUDGE"`, parse the JSON scores, and store them as dimensions on the `EvalResult` via `addScore()`.

Title judge dimensions: `titleJudge_clarity`, `titleJudge_specificity`, `titleJudge_appropriate_scope`, `titleJudge_improvement`, `titleJudge_professional_tone`.

Criteria judge dimensions: `criteriaJudge_measurable`, `criteriaJudge_completeness`, `criteriaJudge_achievable_in_one_week`, `criteriaJudge_testable`.

When the provider is unavailable (no API key), skip judge scoring silently — do not fail the test. Guard with the existing `providerAvailable` boolean.

The `writeReport()` method already serializes `scores` — no change needed there.

**Scope:** Only modify `backend/src/test/java/com/weeklycommit/ai/eval/PromptEvalRunner.java`. Do NOT modify any file in `backend/src/main/` or `frontend/`.

**Depends on:** Step 1

**Deliverables:**
- `PromptEvalRunner.java` — `evaluateCommitDraftAssist()` calls title + criteria judges when provider is available
- Judge prompt template loading from classpath resources
- Score dimensions stored on `EvalResult` for each case

**Checks:** build

### Step 3: Commit the most recent eval run as baseline and create eval-thresholds.json

Create two new files at the repo root:

**`eval-baseline.json`** — Copy the content from `backend/build/eval-results/eval-2026-03-26T16-33-06.574104Z.json` (the most recent real eval run) as the initial baseline. This file is the regression anchor.

**`eval-thresholds.json`** — A configuration file with this structure:

```json
{
  "deterministic": {
    "schema_valid_rate": 1.00,
    "overall_case_pass_rate": 0.95,
    "critical_case_pass_rate": 1.00
  },
  "judge_high_stakes": {
    "applies_to": ["RISK_SIGNAL", "TEAM_INSIGHT", "PERSONAL_INSIGHT", "RAG_QUERY"],
    "mean_faithfulness_min": 0.90,
    "critical_faithfulness_min": 0.85,
    "mean_relevancy_min": 0.80
  },
  "judge_assistive": {
    "applies_to": ["COMMIT_DRAFT_ASSIST", "COMMIT_LINT", "RCDO_SUGGEST", "RECONCILE_ASSIST"],
    "mean_faithfulness_min": 0.85,
    "mean_relevancy_min": 0.75,
    "title_judge_clarity_min": 0.70,
    "criteria_judge_measurable_min": 0.70
  },
  "regression": {
    "faithfulness_drop_max": 0.03,
    "pass_rate_drop_max": 0.05,
    "critical_failures_added_max": 0
  }
}
```

**Scope:** Only create files at the repo root. Do NOT modify any backend or frontend source files.

**Deliverables:**
- `eval-baseline.json` — snapshot of the most recent eval run
- `eval-thresholds.json` — threshold configuration

**Checks:** lint, typecheck

### Step 4: Build the threshold enforcement script

Create `scripts/eval-threshold-check.js` — a standalone Node.js script (no external dependencies beyond Node 20 built-ins) that:

1. Reads `eval-thresholds.json` from the repo root
2. Reads the latest `*.json` file from `backend/build/eval-results/` (sorted by filename timestamp, takes most recent)
3. Reads `eval-baseline.json` from the repo root
4. Computes:
   - `schema_valid_rate` = count of results where `schemaValid === true` / total
   - `overall_case_pass_rate` = count where `passed() equivalent` / total (a result passes when schemaValid and all checks are true)
   - `critical_case_pass_rate` = count of critical results that passed / count of critical results
   - Mean of each judge score dimension across all results that have that score
5. Compares against thresholds from `eval-thresholds.json`
6. Compares against `eval-baseline.json` for regression checks:
   - `pass_rate_drop` = baseline pass rate - current pass rate
   - `critical_failures_added` = count of critical cases that passed in baseline but failed now
7. Prints a clear summary table to stdout
8. Exits with code 0 if all checks pass, code 1 if any hard threshold is violated

Output format example:
```
═══════════════════════════════════════════════════════
  EVAL THRESHOLD CHECK
═══════════════════════════════════════════════════════
  Schema valid rate:         1.00  (threshold: >= 1.00)  ✅
  Overall pass rate:         0.94  (threshold: >= 0.95)  ❌ FAIL
  Critical pass rate:        1.00  (threshold: >= 1.00)  ✅
  
  Judge scores (draft-assist):
    title clarity mean:      0.82  (threshold: >= 0.70)  ✅
    criteria measurable mean:0.78  (threshold: >= 0.70)  ✅
  
  Regression vs baseline:
    Pass rate change:        -0.01 (max drop: 0.05)     ✅
    Critical failures added: 0     (max: 0)              ✅
═══════════════════════════════════════════════════════
  RESULT: FAIL (1 threshold violated)
═══════════════════════════════════════════════════════
```

The script must handle missing judge scores gracefully (when evals ran without API key, judge dimensions won't exist — skip those checks and note "judge scores unavailable" in output).

**Scope:** Only create `scripts/eval-threshold-check.js`. Do NOT modify any backend or frontend source files.

**Deliverables:**
- `scripts/eval-threshold-check.js` — self-contained Node.js script

**Checks:** lint, typecheck

### Step 5: Wire the enforcement script into the GitLab eval job and add an npm script

Update `.gitlab-ci.yml` to add `node scripts/eval-threshold-check.js` as the final command in the `eval:llm-judge` job, after `./gradlew evalTest` produces the results.

Add a convenience npm script to `package.json`:
```json
"eval:check": "node scripts/eval-threshold-check.js"
```

Also add the `evalTest` Gradle task invocation as:
```json
"eval:run": "cd backend && ./gradlew evalTest"
```

**Scope:** Only modify `.gitlab-ci.yml` and `package.json`. Do NOT modify any backend or frontend source files.

**Deliverables:**
- `.gitlab-ci.yml` — `eval:llm-judge` job runs threshold check after eval
- `package.json` — `eval:check` and `eval:run` scripts added

**Checks:** lint, typecheck

### Step 6: Add unit tests for the enforcement script and verify end-to-end

Create `scripts/__tests__/eval-threshold-check.test.js` (or `.mjs`) that tests the threshold check logic with synthetic eval results:
- Test: all passing → exit 0
- Test: schema validity failure → exit 1
- Test: critical case failure → exit 1  
- Test: regression detected → exit 1
- Test: missing judge scores → skip judge checks, pass on deterministic
- Test: missing baseline file → skip regression checks, warn

The test should be runnable via `node --test scripts/__tests__/eval-threshold-check.test.js` (Node 20 built-in test runner, no dependencies).

Also run the full build to verify nothing is broken end-to-end.

**Scope:** Only create files in `scripts/__tests__/`. Do NOT modify any backend or frontend source files.

**Deliverables:**
- `scripts/__tests__/eval-threshold-check.test.js` — unit tests for the enforcement script

**Checks:** lint, typecheck, unit, build
