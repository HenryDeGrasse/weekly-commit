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

Add an optional `critical` boolean field to `backend/src/test/java/com/weeklycommit/ai/eval/EvalCase.java`. Jackson 2.18.3 defaults missing primitive record fields to `false`, so no extra annotation is needed beyond the existing `@JsonIgnoreProperties(ignoreUnknown = true)`.

Then update the two fixture files to mark critical cases:
- `backend/src/test/resources/eval/commit-draft-assist/cases.json` — mark `draft-001` (vague KING), `draft-004` (QUEEN missing criteria), `draft-008` (fully specified KING should return nulls) as `"critical": true`
- `backend/src/test/resources/eval/commit-lint/cases.json` — mark `lint-001` (KING missing criteria + vague title), `lint-003` (2 KING commits), `lint-004` (3 QUEEN commits) as `"critical": true`

Update `EvalResult.java` to track whether the case was critical (add a `boolean critical` field set at construction time). Update `PromptEvalRunner` to pass `evalCase.critical()` through to the `EvalResult` constructor, and include it in the JSON report output.

**Important:** `EvalResult` is a class (not a record) with a 5-arg constructor: `(String caseId, String description, String promptVersion, boolean schemaValid, String rawOutput)`. Add `boolean critical` as a 6th parameter. All 6 call sites in `PromptEvalRunner` (one per eval type: commitDraftAssist, commitLint, rcdoSuggest, riskSignal, reconcileAssist, ragQuery) must be updated.

**Scope:** Only modify files in `backend/src/test/`. Do NOT modify any file in `backend/src/main/` or `frontend/`.

**Deliverables:**
- `EvalCase.java` — record gains `boolean critical` field (defaults `false`)
- `EvalResult.java` — gains `boolean critical` field, getter `isCritical()`, included in `toString()` and JSON serialization
- `PromptEvalRunner.java` — all 6 `new EvalResult(...)` call sites updated to pass `evalCase.critical()`
- `commit-draft-assist/cases.json` — 3 cases marked critical
- `commit-lint/cases.json` — 3 cases marked critical

**Checks:** build

### Step 2: Wire title-quality and criteria-quality LLM judges into PromptEvalRunner for draft-assist cases

Extend `PromptEvalRunner.evaluateCommitDraftAssist()` to call the title-quality judge and criteria-quality judge when the provider is available and the draft-assist output includes a non-null `suggestedTitle` or `suggestedSuccessCriteria`.

**Critical implementation note:** `OpenRouterAiProvider.generateSuggestion()` cannot be used for judge calls — it hard-codes its own system prompt from `src/main/resources/prompts/` based on `suggestionType`, and builds its own user message from the `AiContext` fields. For judge calls we need a custom system prompt (the judge template) and a custom user message (the filled template). Since the constraint prohibits modifying `src/main/`, the judge calling logic must make direct HTTP calls to OpenRouter using `java.net.http.HttpClient` within the test code.

Create a private helper method in `PromptEvalRunner` (e.g., `callJudge(String systemPrompt, String userMessage)`) that:
1. Reads `OPENROUTER_API_KEY` and `OPENROUTER_MODEL` from env vars
2. Makes a direct HTTP POST to `https://openrouter.ai/api/v1/chat/completions`
3. Parses the response to extract the JSON content (reusing the same markdown-fence-stripping logic seen in `OpenRouterAiProvider.extractJson()`)
4. Returns the parsed `JsonNode` scores

Read the judge prompt templates from test classpath resources:
- `eval/judge-prompts/title-quality-judge.txt`
- `eval/judge-prompts/criteria-quality-judge.txt`

Substitute placeholders (`{originalTitle}`, `{suggestedTitle}`, `{chessPiece}`, `{commitTitle}`, `{suggestedCriteria}`) and use the filled template as a single user message (the template already contains both system-level instructions and the data).

Title judge dimensions: `titleJudge_clarity`, `titleJudge_specificity`, `titleJudge_appropriate_scope`, `titleJudge_improvement`, `titleJudge_professional_tone`.

Criteria judge dimensions: `criteriaJudge_measurable`, `criteriaJudge_completeness`, `criteriaJudge_achievable_in_one_week`, `criteriaJudge_testable`.

Guard all judge calls with the existing `providerAvailable` boolean. Wrap in try/catch so judge failures never fail the test.

**Scope:** Only modify `backend/src/test/java/com/weeklycommit/ai/eval/PromptEvalRunner.java`. Do NOT modify any file in `backend/src/main/` or `frontend/`.

**Depends on:** Step 1

**Deliverables:**
- `PromptEvalRunner.java` — `evaluateCommitDraftAssist()` calls title + criteria judges when provider is available
- Private `callJudge()` helper for direct OpenRouter HTTP calls
- Judge prompt template loading from classpath resources
- Score dimensions stored on `EvalResult` for each case

**Checks:** build

### Step 3: Commit the most recent eval run as baseline and create eval-thresholds.json

Create two new files at the repo root:

**`eval-baseline.json`** — Copy the content from `backend/build/eval-results/eval-2026-03-26T16-33-06.574104Z.json` (the most recent real eval run) as the initial baseline. This file is the regression anchor. Note: this baseline predates Step 1, so results will NOT have a `critical` field. The threshold enforcement script (Step 4) must handle missing `critical` gracefully.

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

**Checks:** build

### Step 4: Build the threshold enforcement script

Create `scripts/eval-threshold-check.js` — a standalone Node.js script (no external dependencies beyond Node 20 built-ins) that:

1. Reads `eval-thresholds.json` from the repo root (resolved relative to the script's location via `path.resolve(__dirname, '..')`)
2. Reads the latest `*.json` file from `backend/build/eval-results/` (sorted by filename timestamp, takes most recent)
3. Reads `eval-baseline.json` from the repo root (if it exists — skip regression checks when absent)
4. Computes:
   - `schema_valid_rate` = count of results where `schemaValid === true` / total
   - `overall_case_pass_rate` = count where `passed() equivalent` / total (a result passes when schemaValid and all checks are true)
   - `critical_case_pass_rate` = count of critical results that passed / count of critical results (skip if no results have `critical: true`)
   - Mean of each judge score dimension across all results that have that score
5. Compares against thresholds from `eval-thresholds.json`
6. Compares against `eval-baseline.json` for regression checks:
   - `pass_rate_drop` = baseline pass rate - current pass rate
   - `critical_failures_added` = count of critical cases that passed in baseline but failed now (handle missing `critical` field in baseline by treating those cases as non-critical)
7. Prints a clear summary table to stdout
8. Exits with code 0 if all checks pass, code 1 if any hard threshold is violated

The script must handle:
- Missing judge scores gracefully (when evals ran without API key, judge dimensions won't exist — skip those checks and note "judge scores unavailable" in output)
- Missing `eval-baseline.json` — skip regression checks and warn
- Missing `critical` field on baseline results — treat as non-critical
- Missing eval results directory or empty results — exit with error message

**Architecture note:** Extract the core logic into exported functions (e.g., `computeMetrics(results)`, `checkThresholds(metrics, thresholds)`, `checkRegression(metrics, baseline)`) so the script can be unit tested by `require()`-ing it. Use a guard pattern: `if (require.main === module) { main(); }` to allow importing without executing.

**Scope:** Only create `scripts/eval-threshold-check.js`. Do NOT modify any backend or frontend source files.

**Deliverables:**
- `scripts/eval-threshold-check.js` — self-contained Node.js script with exported functions for testability

**Checks:** build

### Step 5: Wire the enforcement script into the GitLab eval job and add an npm script

Update `.gitlab-ci.yml` to modify the `eval:llm-judge` job:
- The current image is `gradle:8.10.2-jdk21` which does NOT have Node.js installed. Either:
  - (a) Install Node.js in `before_script` (`apt-get update && apt-get install -y nodejs`), or
  - (b) Switch the image to one that has both JDK 21 and Node 20, or
  - (c) Add `curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && apt-get install -y nodejs` to `before_script`
  
  Option (c) is safest to get Node 20 specifically. Add `node scripts/eval-threshold-check.js` as the final command after `./gradlew evalTest`.

Add convenience npm scripts to `package.json`:
```json
"eval:check": "node scripts/eval-threshold-check.js",
"eval:run": "cd backend && ./gradlew evalTest"
```

**Scope:** Only modify `.gitlab-ci.yml` and `package.json`. Do NOT modify any backend or frontend source files.

**Deliverables:**
- `.gitlab-ci.yml` — `eval:llm-judge` job installs Node.js and runs threshold check after eval
- `package.json` — `eval:check` and `eval:run` scripts added

**Checks:** build

### Step 6: Add unit tests for the enforcement script and verify end-to-end

Create `scripts/__tests__/eval-threshold-check.test.js` that tests the threshold check logic with synthetic eval results.

**Important:** The test must `require()` the exported functions from `scripts/eval-threshold-check.js` (see Step 4 architecture note). The script must use CommonJS (`module.exports`) since the root `package.json` does not have `"type": "module"`. Tests use Node 20's built-in test runner (`node:test` module).

Test cases:
- Test: all passing → no violations
- Test: schema validity failure → violation reported
- Test: critical case failure → violation reported
- Test: regression detected → violation reported
- Test: missing judge scores → skip judge checks, pass on deterministic
- Test: missing baseline → skip regression checks, warn

The test should be runnable via `node --test scripts/__tests__/eval-threshold-check.test.js`.

**Note on `unit` check:** The `unit` check runs `npm test` which triggers `vitest` in frontend and shared workspaces + `./gradlew test` in backend. Node built-in test files in `scripts/__tests__/` are NOT automatically included. Either:
- (a) Add a separate npm script like `"test:scripts": "node --test scripts/__tests__/*.test.js"` and incorporate it into the root `test` script, or
- (b) Accept that `unit` check won't run these tests and rely on `build` check only

Option (a) is preferred — update `package.json`'s root `test` script in this step to also run `node --test scripts/__tests__/*.test.js`.

**Scope:** Create files in `scripts/__tests__/` and update `package.json` to include script tests in the root `test` command.

**Deliverables:**
- `scripts/__tests__/eval-threshold-check.test.js` — unit tests for the enforcement script
- `package.json` — root `test` script extended to include `node --test scripts/__tests__/*.test.js`

**Checks:** unit, build
