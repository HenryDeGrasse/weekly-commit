# Test & Evaluation Infrastructure Summary

## Overview

This project has a comprehensive, multi-layered testing strategy combining:
1. **Java Unit Tests (54 files)** - Backend domain logic, services, and AI integration
2. **Evaluation Framework** - Golden-dataset driven LLM prompt testing with automated scoring
3. **E2E Tests** - TypeScript/Playwright tests for critical user workflows
4. **Eval Threshold Checking** - CI/CD gate for regression detection and quality standards

---

## I. Java Backend Tests (54 Files)

### Test Categories

#### 1. **AI Service Tests** (core evaluation targets)
- `CommitLintServiceTest.java` - Tests hard/soft validation rules for plan linting
  - Hard validations: missing success criteria, chess piece limits, vague titles
  - Soft guidance: duplicate detection, fragmentation warnings
  - Mocks `AiProviderRegistry` to test without calling real LLM

- `CommitDraftAssistServiceTest.java` - Tests title/criteria/estimate suggestions
  - Validates schema compliance of AI suggestions
  - Checks title clarity, specificity, scope appropriateness
  - Verifies estimate ranges match chess piece

- `RiskDetectionServiceTest.java` - Tests plan risk signal detection
  - Signal types: OVERCOMMIT, CONCENTRATION, ESTIMATE_MISMATCH, BLOCKING_LANGUAGE, ATTENTION_SPLIT
  - Detects hidden dependencies between commits
  - Flags scope instability and single-large-commit risks

- `RcdoSuggestServiceTest.java` - Tests RCDO node recommendation
  - Semantic matching of commits to Outcomes/DOs
  - Confidence scoring and hierarchy preference (never suggest Rally Cry directly)
  - Handles no-match and ambiguous-match cases

- `ReconcileAssistServiceTest.java` - Tests auto-prefill of reconciliation outcomes
  - Suggests ACHIEVED/PARTIALLY_ACHIEVED/NOT_ACHIEVED based on ticket evidence
  - Recommends carry-forwards for incomplete priority work
  - Detects scope changes that occurred post-lock

- `WhatIfServiceTest.java` - Tests scenario analysis and risk narrative generation
  - Computes projected risk signals from hypothetical mutations (add/remove/modify commits)
  - Generates natural language narratives explaining impact
  - Tests budget saturation, risk resolution, RCDO coverage shifts

- `ManagerAiSummaryServiceTest.java` - Tests team-level AI insights
  - Synthesizes team status across all members' plans
  - Flags carry-forward patterns, blocked items, concentration risks
  - Tests temporal reasoning (this week vs. last week)

#### 2. **Plan Lifecycle Tests**
- `WeeklyPlanServiceTest.java` - Tests plan creation, state transitions, capacity computation
  - Tests `getOrCreatePlan()` for idempotency
  - Validates week start date handling, capacity inheritance
  - Tests plan state machine (DRAFT → LOCKED → RECONCILING → RECONCILED)

- `CommitServiceTest.java` - Tests commit CRUD with validation
  - Validates chess piece constraints (KING/QUEEN limits)
  - Enforces success criteria requirements by piece type
  - Tests RCDO linkage validation and audit logging

- `ReconciliationServiceTest.java` - Tests outcome reconciliation and carry-forward logic
  - Computes outcome states from ticket status + scope changes
  - Filters out archived/removed commits
  - Creates immutable reconciliation snapshots

#### 3. **Locking & Audit Tests**
- `LockServiceTest.java` / `LifecycleIdempotencyTest.java` - Tests plan locking with snapshot creation
  - Validates pre-lock conditions (all commits have criteria, no KINGs without criteria)
  - Creates immutable `LockSnapshotHeader` + `LockSnapshotCommit` records
  - **Tests idempotency**: running lock twice produces no duplicate snapshots

- `AuditLogServiceTest.java` - Tests event audit trail
  - Captures all lifecycle transitions (DRAFT → LOCKED → RECONCILED)
  - Records who made what change and when
  - Maintains immutable audit log

- `AutoLockJobTest.java` / `AutoReconcileJobTest.java` - Tests scheduled automation
  - Runs at deadline times to auto-lock incomplete plans
  - Creates system-generated snapshots and audit records

#### 4. **RAG & Embedding Tests**
- `EmbeddingServiceTest.java` - Tests plan/commit embedding and retrieval
  - Converts plans to dense vector embeddings via Pinecone
  - Tests semantic search query processing
  - Validates chunk scoring and relevance ranking

- `SemanticQueryServiceTest.java` - Tests RAG query interpretation
  - Parses questions about team performance, RCDO coverage, carry-forwards
  - Converts natural language to vector search queries
  - Validates answer synthesis from retrieved chunks

- `InsightGenerationServiceTest.java` - Tests narrative generation from RAG results
  - Builds human-readable summaries from retrieved chunks
  - Cites sources (commit IDs, plan summaries)
  - Tests confidence scoring and evidence aggregation

#### 5. **Domain & Validation Tests**
- `EntityValidationTest.java` - Tests Jakarta validation annotations
  - Email format, UUID uniqueness, date constraints
  - Commit title length, chess piece enum values
  - RCDO hierarchy type constraints

- `RcdoLinkageValidatorTest.java` - Tests RCDO graph navigation
  - Validates commit→outcome links are to active nodes
  - Prevents linking to archived outcomes
  - Tests hierarchy parent/child constraints

#### 6. **Authorization & Permissions Tests**
- `AuthorizationServiceTest.java` - Tests team membership and permission checks
  - Validates user belongs to team before accessing plans
  - Tests manager vs. individual contributor scopes
  - Audit log permission filtering

- `AuditLogPermissionTest.java` - Tests who can view audit logs
  - Only plan owner or team manager can see detailed audit
  - Organization admins can see full audit trail

#### 7. **Config & Feature Toggle Tests**
- `ConfigurationServiceTest.java` - Tests dynamic feature configuration
  - Capacity budget overrides per user/team
  - Lock/reconcile deadline calculations
  - Feature flags for AI availability

#### 8. **Notification Tests**
- `NotificationServiceTest.java` - Tests email/Slack notifications
  - Lock deadline approach notifications
  - Carry-forward alerts
  - Manager exception queue summaries

- `EmailDigestServiceTest.java` - Tests weekly email summaries
  - Aggregates team plan status
  - Includes carry-forward patterns and exceptions

#### 9. **Controller Tests**
Tests HTTP endpoints (request validation, response serialization):
- `PlanControllerTest.java`, `CommitControllerTest.java`
- `ReconcileControllerTest.java`, `LockControllerTest.java`
- `TeamControllerTest.java`, `TicketControllerTest.java`
- `AiControllerTest.java`, `RcdoControllerTest.java`

---

## II. Evaluation Framework (Golden Dataset Testing)

### Architecture

The evaluation system is **production-grade prompt testing** designed to:
1. **Isolate AI behavior** from integration logic
2. **Measure quality** against manually curated golden datasets
3. **Detect regressions** via baseline comparison
4. **Gate CI/CD** with pass/fail thresholds

### Location & Organization

```
backend/src/test/resources/eval/
├── commit-lint/
│   └── cases.json (6 test cases)
├── commit-draft-assist/
│   └── cases.json (12 test cases)
├── rcdo-suggest/
│   └── cases.json (10 test cases)
├── reconcile-assist/
│   └── cases.json (8 test cases)
├── risk-signal/
│   └── cases.json (8 test cases)
├── rag-query/
│   └── cases.json (8 test cases)
├── what-if/
│   └── cases.json (8 test cases)
└── judge-prompts/
    ├── title-quality-judge.txt
    ├── criteria-quality-judge.txt
    └── faithfulness-judge.txt
```

**Total: 60 golden test cases across 7 prompts**

### Evaluation Executor: `PromptEvalRunner.java`

```java
@Tag("eval")  // Excluded from ./gradlew test
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PromptEvalRunner {
  // Runs with @ParameterizedTest using each cases.json file
  // Calls real LLM (OpenRouter) with actual prompt templates
  // Scores output automatically + via LLM judges
  // Outputs JSON results to backend/build/eval-results/{timestamp}.json
}
```

**Run with:**
```bash
OPENROUTER_API_KEY=sk-... ./gradlew test --tests "com.weeklycommit.ai.eval.*"
```

### Golden Case Anatomy

Each case has:
```json
{
  "id": "commit-lint-001",
  "description": "Plan with KING missing success criteria",
  "critical": true,              // If true, must pass in regression check
  "edgeCaseType": "hard_validation_issues",
  "input": {
    "commits": [
      {"commitId": "c-001", "title": "...", "chessPiece": "KING", ...}
    ],
    "capacityBudgetPoints": 10
  },
  "expectedBehavior": {
    "minHardValidations": 2,
    "expectedHardCodes": ["MISSING_SUCCESS_CRITERIA", "VAGUE_TITLE"],
    "hardShouldReferenceCommitIds": ["c-001", "c-002"]
  }
}
```

### Case Details by Prompt

#### **Commit Lint (6 cases)**
Tests validation rule accuracy.

| Case | Edge Case | Expected Outcome |
|------|-----------|------------------|
| lint-001 | KING missing criteria + vague title | ≥2 hard validations (MISSING_CRITERIA, VAGUE_TITLE) |
| lint-002 | Clean plan | 0 hard, ≤2 soft |
| lint-003 | 2 KINGs | 1 hard: KING_LIMIT_EXCEEDED |
| lint-004 | 3 QUEENs | 1 hard: QUEEN_LIMIT_EXCEEDED |
| lint-005 | 10 PAWNs (fragmented) | 0 hard, ≥1 soft (TOO_MANY_COMMITS, EXCESSIVE_PAWNS) |
| lint-006 | Near-duplicate titles | 0 hard, ≥1 soft (NEAR_DUPLICATE_TITLES) |

**Critical cases:** lint-001, lint-003, lint-004

#### **Commit Draft Assist (12 cases)**
Tests title/description/criteria/estimate suggestions.

| Case | Edge Case | Expected Behavior |
|------|-----------|-------------------|
| draft-001 | Vague KING | Suggest title, criteria, estimate (3-8pts) |
| draft-002 | Good ROOK | Return all nulls (already good) |
| draft-003 | Activity-focused + bad estimate PAWN | Suggest outcome-focused title, fix estimate (1-3pts) |
| draft-004 | QUEEN missing criteria | Keep title, suggest measurable criteria, fix estimate |
| draft-005 | Single-word title ("TBD") | Suggest specific title (>10 chars) |
| draft-006 | KNIGHT exploratory | Suggest outcome, not title |
| draft-007 | BISHOP verbose title | Suggest shorter title (<100 chars) |
| draft-008 | KING fully specified | All nulls (complete) |
| draft-009 | PAWN estimate=1 reasonable | All nulls |
| draft-010 | Emoji/special chars | Suggest clean title |
| draft-011 | QUEEN underestimated at 1pt | Suggest higher estimate (5-8pts) + criteria |
| draft-012 | Duplicate-sounding PAWN | Suggest more specific title |

**Critical cases:** draft-001, draft-004, draft-008, draft-011

#### **RCDO Suggest (10 cases)**
Tests semantic matching to RCDO tree.

| Case | Edge Case | Expected Outcome |
|------|-----------|------------------|
| rcdo-001 | Clear keyword match | Suggest specific Outcome, conf ≥0.75 |
| rcdo-002 | No match | No suggestion, conf ≤0.5 |
| rcdo-003 | Ambiguous (2 outcomes) | Pick more specific one, conf ≥0.7 |
| rcdo-004 | DO without child Outcomes | Link to DO, not Rally Cry |
| rcdo-005 | Rally Cry trap | Never suggest Rally Cry directly |
| rcdo-006 | Archived node match | Skip archived, suggest active |
| rcdo-007 | Semantic match (no keywords) | Still match via embeddings, conf ≥0.7 |
| rcdo-008 | Empty RCDO tree | No suggestion, conf ≤0.3 |
| rcdo-009 | Multiple valid Outcomes | Pick best, moderate confidence (0.6) |
| rcdo-010 | Minimal title ("SSO") | Still attempt matching, conf ≥0.5 |

#### **Reconcile Assist (8 cases)**
Tests outcome inference from evidence.

| Case | Edge Case | Expected Outcome |
|------|-----------|------------------|
| recon-001 | All DONE | All ACHIEVED |
| recon-002 | Mixed (DONE, IN_PROGRESS, BLOCKED) | Map ticket status → outcome |
| recon-003 | Scope change removed commit | Mark CANCELED |
| recon-004 | Unlinked commits | Infer with low confidence |
| recon-005 | KING IN_PROGRESS at week end | Recommend carry-forward |
| recon-006 | Scope changes during week | Summary mentions them |
| recon-007 | Empty plan | Return empty outcomes, no errors |
| recon-008 | All NOT_ACHIEVED | Recommend highest-priority for carry-forward |

#### **Risk Signal (8 cases)**
Tests proactive risk detection.

| Case | Signal Type | Expected Behavior |
|------|-------------|-------------------|
| risk-001 | Hidden dependency | Detect sequential blocker (deploy then migrate) |
| risk-002 | No risk | 0-1 signals (baseline only) |
| risk-003 | Concentration | All points on payment system → CONCENTRATION risk |
| risk-004 | Estimate mismatch | KING with 1pt for migration → ESTIMATE_MISMATCH |
| risk-005 | Blocking language | "Waiting for vendor..." → BLOCKING_LANGUAGE signal |
| risk-006 | Attention split | 2 QUEENs at 100% capacity → warn |
| risk-007 | Scope instability | 4+ scope changes → INSTABILITY risk |
| risk-008 | Single large commit | 8pts in 10pt budget → SINGLE_LARGE_COMMIT |

#### **RAG Query (8 cases)**
Tests semantic understanding and answer synthesis.

| Case | Question Type | Expected Behavior |
|------|---------------|-------------------|
| rag-001 | Factual lookup | Find commits for specific person |
| rag-002 | Synthesis | Aggregate effort by RCDO across weeks |
| rag-003 | Pattern detection | Identify carry-forward trends |
| rag-004 | No data | Gracefully indicate insufficient context |
| rag-005 | Scope changes | Extract reasons for plan changes |
| rag-006 | Temporal reasoning | Distinguish "this week" vs. "last week" |
| rag-007 | Manager comments | Find feedback and cite sources |
| rag-008 | Person not found | Indicate no data for requested user |

**Scoring dimensions:**
- Faithfulness (0-1): Claims supported by retrieved chunks
- Relevancy (0-1): Retrieved chunks match question intent
- Confidence (0-1): Inferred from source count and recency

#### **What-If (8 cases)**
Tests scenario analysis narratives.

| Case | Mutation | Expected Narrative |
|------|----------|-------------------|
| whatif-001 | ADD_COMMIT overcommits | Mention 14pt exceeds 10pt budget |
| whatif-002 | REMOVE resolves overcommit | Highlight improvement |
| whatif-003 | MODIFY shifts RCDO coverage | Mention coverage change |
| whatif-004 | No change | Mention no impact (7pt stays 7pt) |
| whatif-005 | Mixed add/remove + coverage shift | Mention both |
| whatif-006 | ADD fills budget (0→10) | Highlight utilization |
| whatif-007 | PAWN→KING intro blocked risk | Warn about blocked critical |
| whatif-008 | ADD lifts undercommit | Celebrate improvement |

**Narratives tested for:**
- Max 600 chars
- Mentions specific numbers (points, budget)
- Explains risks and resolutions naturally
- Includes recommendations when critical

### Judge Prompts

#### Title Quality Judge
Evaluates suggested commit titles (0.0–1.0 scores):
- **clarity**: Outcome vs. activity? "Deploy auth service" vs. "Work on auth"
- **specificity**: Concrete deliverables, no vague words (stuff, things, work on)
- **appropriate_scope**: Single-week achievable? Matches chess piece?
- **improvement**: Is suggestion meaningfully better than original?
- **professional_tone**: No emoji, exclamation marks, slang?

#### Criteria Quality Judge
Evaluates success criteria (0.0–1.0 scores):
- **measurable**: Specific numbers, states, verifiable conditions?
- **completeness**: Covers key aspects? KING needs more than PAWN
- **achievable_in_one_week**: Realistic timeline?
- **testable**: Can teammate verify without subjective judgment?

#### Faithfulness Judge
Evaluates RAG answers (0.0–1.0 score):
- Decomposes answer into atomic claims
- Marks each SUPPORTED or UNSUPPORTED by retrieved chunks
- Score = (supported claims) / (total claims)
- Identifies hallucinations

---

## III. Evaluation Scoring & Results

### Output Format: `eval-baseline.json` & Latest Results

```json
{
  "timestamp": "2026-03-26T16:33:06Z",
  "totalCases": 18,
  "passed": 17,
  "failed": 1,
  "results": [
    {
      "caseId": "lint-001",
      "description": "...",
      "promptVersion": "commit-lint-v1",
      "schemaValid": true,
      "rawOutput": "{...json...}",
      "scores": {
        "hardValidationCount": 2.0,
        "faithfulness": 0.92,
        "titleJudge_clarity": 0.85,
        ...
      },
      "checks": {
        "minHardValidations": true,
        "hardCode_MISSING_SUCCESS_CRITERIA": true,
        ...
      },
      "notes": {}
    }
  ]
}
```

**Scoring dimensions per case:**
- `schemaValid`: Matches expected JSON schema ✓/✗
- `checks`: Automated assertions (all must be true for pass)
- `scores`: LLM judge ratings (averaged across cases)
  - Judge dimensions: `clarity`, `specificity`, `measurable`, `faithfulness`, etc.
  - Numeric dimensions: `hardValidationCount`, `softGuidanceCount`, `estimate`

---

## IV. Threshold Checking: `eval-threshold-check.js`

Located at `scripts/eval-threshold-check.js` (standalone Node.js, no deps)

### Config: `eval-thresholds.json`

```json
{
  "deterministic": {
    "schema_valid_rate": 1.00,        // All outputs must be valid JSON
    "overall_case_pass_rate": 0.95,   // 95% of all cases must pass
    "critical_case_pass_rate": 1.00   // 100% of critical cases
  },
  "judge_high_stakes": {
    "applies_to": ["RISK_SIGNAL", "TEAM_INSIGHT", "RAG_QUERY"],
    "mean_faithfulness_min": 0.90,
    "critical_faithfulness_min": 0.85,
    "mean_relevancy_min": 0.80
  },
  "judge_assistive": {
    "applies_to": ["COMMIT_DRAFT_ASSIST", "RCDO_SUGGEST", "RECONCILE_ASSIST"],
    "mean_faithfulness_min": 0.85,
    "mean_relevancy_min": 0.75,
    "title_judge_clarity_min": 0.70,
    "criteria_judge_measurable_min": 0.70
  },
  "regression": {
    "faithfulness_drop_max": 0.03,      // Max 3% drop from baseline
    "pass_rate_drop_max": 0.05,         // Max 5% drop
    "critical_failures_added_max": 0    // Zero new critical failures
  }
}
```

### Threshold Categories

#### Hard Gates (Block CI)
- `schema_valid_rate` - If even one output is invalid JSON, fail
- `overall_case_pass_rate` - If >5% of cases fail, fail
- `critical_case_pass_rate` - If any critical case fails, fail

#### Soft Gates (Report Only)
- `judge_high_stakes` - Faithfulness for risk/query features (≥0.90 mean)
- `judge_assistive` - Clarity for draft/lint/suggest (≥0.70–0.85)
- `regression_*` - Comparison to baseline run

### Execution

```bash
node scripts/eval-threshold-check.js
```

Output: Example from latest run showing 17/18 passing (94.4%)

```
Eval Threshold Check
════════════════════════════════════════════════════════
Results file : backend/build/eval-results/2026-03-26T16:33:06.json
Timestamp    : 2026-03-26T16:33:06.574447Z
Total cases  : 18 (17 passed, 1 failed)
Baseline     : 2026-03-26T16:33:06.574447Z (18 cases)
════════════════════════════════════════════════════════

── Deterministic Gates (hard) ──────────────────────────
  ✅ schema_valid_rate: 100.0% schema valid (required ≥ 100.0%)
  ✅ overall_case_pass_rate: 94.4% cases passed (required ≥ 95.0%)
  ✅ critical_case_pass_rate: 100.0% critical cases passed (required ≥ 100.0%)

── Judge Score Checks (soft) ───────────────────────────
  ⚠️  judge_high_stakes.RISK_SIGNAL.mean_faithfulness_min: 0.87 (required ≥ 0.90)
  ✅ judge_assistive.COMMIT_DRAFT_ASSIST.mean_clarity: 0.88 (required ≥ 0.70)

── Regression Checks (soft) ────────────────────────────
  ✅ pass_rate_regression: 94.4% (drop=0%, max=5%)
  ✅ faithfulness_regression: 0.890 (drop=0.010, max=0.03)

════════════════════════════════════════════════════════
  RESULT: ⚠️  2 soft check(s) flagged — NOT blocking CI
════════════════════════════════════════════════════════
```

**Exit code:** 0 (success, soft failures don't block)

---

## V. E2E Tests (TypeScript/Playwright)

Located at `e2e/` with 4 test files

### Test Files

#### 1. `golden-path.spec.ts`
**Core happy-path workflow** - Plan creation through reconciliation

Tests:
- Page loads with proper sidebar navigation (5 routes)
- Week selector navigates between weeks, shows "Today" button
- Plan auto-creates in DRAFT state on first visit
- Can add commit via form (title, chess piece, points)
- Capacity meter updates with committed points
- Navigation across all pages (My Week → Team → Tickets → RCDOs)
- Breadcrumb shows correct path

**Coverage:** Page shell, plan CRUD, basic navigation

#### 2. `ai-flows.spec.ts`
**AI features integration** - All AI-powered interactions

**Mocked AI Endpoints:**
- `/api/ai/status` - AI provider availability
- `/api/ai/commit-draft-assist` - Freeform → structure suggestions
- `/api/ai/commit-lint` - Auto-run plan linting
- `/api/ai/rcdo-suggest` - RCDO recommendation
- `/api/plans/*/risk-signals` - Proactive risk banners
- `/api/ai/reconcile-assist` - Reconcile AI pre-fill
- `/api/teams/*/week/*/ai-summary` - Manager insights
- `/api/ai/feedback` - Fire-and-forget feedback

Tests:
- **Commit Composer** - AI modal appears when adding commit (if available)
- **Manual bypass** - "Add manually" button skips AI
- **Auto-run lint** - Results appear inline after adding commits
- **Proactive risk banners** - Render for locked plans
- **Manager summary card** - Appears at top of Team Week
- **AI insights** - Team risk summary banners
- **Reconcile pre-fill** - Draft summary and carry-forward recommendations
- **AI feedback** - Accepts user feedback on suggestions

**Coverage:** AI UX integration, async loading, mocked provider

#### 3. `manager-flow.spec.ts`
**Team view & exception handling** - Manager dashboard

Tests:
- Team page loads with 4 tabs: Overview, By Person, Chess, Exceptions
- Tab switching works (panel transitions)
- Overview section shows 4 summary cards:
  - Lock compliance (% members locked)
  - Reconcile compliance (% reconciled)
  - Points summary (planned/used/overcommit)
  - Exceptions overview (count)
- Week selector works on team page

**Coverage:** Team dashboard, compliance tracking

#### 4. `navigation.spec.ts`
**Header & sidebar navigation** - Layout persistence

Tests:
- Header renders with brand, week selector, notifications, user name
- Sidebar can collapse/expand
- Collapse state **persists** across page navigation
- Clicking nav links navigate to correct URLs
- All routes accessible: /weekly/my-week, /weekly/team, /weekly/tickets, /weekly/rcdos

**Coverage:** Layout shell, state persistence

### Page Object Models

#### `pages/MyWeekPage.ts`
Locators & actions for My Week page:
- `goto()` - Navigate to /weekly/my-week
- `expectDraftState()` - Assert plan is in DRAFT with add/lock buttons
- `addCommit(title, chessPiece, points)` - Fill form & submit
- `lockPlan()` - Handle pre-lock validation → confirm dialog

#### `pages/ReconcilePage.ts`
Locators & actions for Reconcile page:
- `goto(planId)` - Navigate to /weekly/reconcile/{planId}
- `openReconciliation()` - Transition from locked to reconciling
- `setOutcome(commitId, outcome)` - Select ACHIEVED/PARTIALLY_ACHIEVED/NOT_ACHIEVED
- `submitReconciliation()` - Confirm & transition to RECONCILED

#### `pages/TeamPage.ts`
Locators & actions for Team Week page:
- `goto(teamId?)` - Navigate to /weekly/team or /weekly/team/{teamId}
- `switchToTab(tab)` - Click tab and wait for panel

#### `fixtures/` (empty)
No custom fixtures defined; uses Playwright defaults

### Test Execution

```bash
# Run all E2E tests
npx playwright test

# Run specific file
npx playwright test e2e/golden-path.spec.ts

# Run with headed browser
npx playwright test --headed

# Debug mode
npx playwright test --debug
```

**Config:** `e2e/playwright.config.ts`
- Base URL: http://localhost:5173 (frontend)
- Timeout: 30 seconds per test
- Retries: 2 (for flaky network)
- Parallel: 4 workers

---

## VI. Test Statistics Summary

### Java Tests
- **Total test files:** 54
- **Test methods:** ~400+ (estimated)
- **Coverage:**
  - AI services: 10+ service test files
  - Domain logic: 20+ entity/service tests
  - Lifecycle: 5+ tests (lock, reconcile, audit)
  - Authorization: 2 tests
  - Integration: Controller tests across all domains

### Evaluation Tests
- **Golden dataset size:** 60 cases across 7 prompt types
- **Judge-scored dimensions:** 15+ (clarity, specificity, faithfulness, measurable, etc.)
- **Critical cases:** 12 (must pass in regression)
- **LLM cost:** ~$10-20 per full eval run (OpenRouter, Sonnet 4)

### E2E Tests
- **Test suites:** 4 files
- **Test cases:** ~30+ individual tests
- **Page objects:** 3 classes
- **Estimated runtime:** 2-3 minutes per run (parallel)

---

## VII. CI/CD Integration

### Pipeline Flow

1. **Unit tests** - `./gradlew test` (fast, excludes @Tag("eval"))
2. **Eval tests** - `./gradlew test --tests "ai.eval.*"` (slow, ~10min)
   - Calls real LLM via OpenRouter
   - Outputs `backend/build/eval-results/{timestamp}.json`
3. **Threshold check** - `node scripts/eval-threshold-check.js`
   - Compares latest results against `eval-thresholds.json`
   - Compares against `eval-baseline.json` for regressions
   - **Exit 0:** All hard gates pass (soft gates report-only)
   - **Exit 1:** Hard gate failure blocks merge

4. **E2E tests** - `npx playwright test` (parallel, ~3min)
   - Requires frontend & backend running locally
   - Runs against http://localhost:5173 + http://localhost:8080

### Baseline Update

```bash
# After successful eval run, update baseline
cp backend/build/eval-results/latest.json eval-baseline.json
git add eval-baseline.json
git commit -m "chore: update eval baseline"
```

---

## VIII. Example: Commit Lint Eval Case

### Input
```json
{
  "id": "lint-001",
  "description": "Plan with KING missing success criteria and a vague title",
  "critical": true,
  "input": {
    "commits": [
      {
        "commitId": "c-001",
        "title": "King thing",           // ❌ Vague, too short
        "chessPiece": "KING",
        "successCriteria": "",          // ❌ Missing required
        "estimatePoints": 3
      },
      {
        "commitId": "c-002",
        "title": "stuff",               // ❌ Vague, too generic
        "chessPiece": "ROOK",
        "successCriteria": "",
        "estimatePoints": 5
      }
    ],
    "capacityBudgetPoints": 10
  },
  "expectedBehavior": {
    "minHardValidations": 2,
    "expectedHardCodes": ["MISSING_SUCCESS_CRITERIA", "VAGUE_TITLE"],
    "hardShouldReferenceCommitIds": ["c-001", "c-002"]
  }
}
```

### Execution
```bash
OPENROUTER_API_KEY=sk-... ./gradlew test --tests "PromptEvalRunner.*lint.*"
```

### Raw LLM Output
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

### Evaluation
```
✅ schemaValid: true (has hardValidation, softGuidance arrays)
✅ minHardValidations: 2 (actual: 2)
✅ hardCode_MISSING_SUCCESS_CRITERIA: true (found for c-001)
✅ hardCode_VAGUE_TITLE: true (found for c-002)
✅ Critical case: PASSED
```

### Judge Scoring
- AI then calls title-quality-judge and criteria-quality-judge LLM
- Scores: clarity: 0.88, specificity: 0.92, measurable: 0.85, etc.
- Average these across all draft-assist cases → used in soft thresholds

### Final Result in `eval-baseline.json`
```json
{
  "caseId": "lint-001",
  "schemaValid": true,
  "scores": {
    "hardValidationCount": 2.0
  },
  "checks": {
    "minHardValidations": true,
    "hardCode_MISSING_SUCCESS_CRITERIA": true,
    "hardCode_VAGUE_TITLE": true
  }
}
```

---

## IX. Key Design Insights

### Why Evaluation Over Mocks?

1. **Real LLM behavior** - Mocks can't catch prompt drift or model degradation
2. **Golden datasets** - Each case documents expected behavior as test case
3. **Judge scoring** - Uses LLM to evaluate LLM (meta-evaluation)
4. **Regression detection** - Baseline comparison catches silent failures
5. **Audit trail** - Every eval run is timestamped and archived

### Why Separate Hard vs. Soft Thresholds?

- **Hard:** Schema, pass rates, critical cases (deterministic)
  - These block CI/CD if they fail
  - Indicate actual bugs or prompt breakage

- **Soft:** Judge scores, faithfulness (stochastic)
  - Report but don't block
  - Catch quality degradation over time
  - Allow for normal LLM variance

### Why Page Objects for E2E?

- Decouples test logic from selector changes
- Single source of truth for page structure
- Easy to refactor selectors without breaking tests
- Improves test readability

### Why Mocked AI Endpoints in E2E?

- Tests **UI integration** not AI quality
- Avoids rate limits, API costs, flakiness
- Ensures deterministic test results
- AI quality tested separately via eval framework

---

## X. Common Test Patterns

### Unit Test Template (Mocking)
```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
  @Mock Repository repo;
  @InjectMocks Service service;
  
  @BeforeEach
  void setUp() {
    // Arrange: set up mocks
    when(repo.findById(id)).thenReturn(Optional.of(entity));
  }
  
  @Test
  void someCase_shouldBehaveSomeway() {
    // Act: call service
    Result result = service.doSomething(input);
    
    // Assert: verify behavior
    assertThat(result).isEqualTo(expected);
    verify(repo).save(any());
  }
}
```

### Eval Test Template
```java
@ParameterizedTest
@MethodSource("loadCases")
void evaluate(EvalCase evalCase) throws Exception {
  // Call real LLM
  AiSuggestionResult result = provider.generateSuggestion(context);
  
  // Assert schema
  assertThat(result.payload()).isValidJson();
  
  // Assert expected behavior
  assertThat(output.hardValidations()).contains("EXPECTED_CODE");
  
  // Score with judge
  JsonNode judgeScore = callJudge(result.payload());
  evalResult.addScore("clarity", judgeScore.get("clarity").asDouble());
}
```

### E2E Test Template
```typescript
test("user can perform action", async ({ page }) => {
  const myPage = new MyPage(page);
  
  // Navigate & wait for page load
  await myPage.goto();
  
  // Interact
  await myPage.button.click();
  
  // Assert UI state
  await expect(myPage.successMsg).toBeVisible();
});
```

---

## XI. Debugging Tips

### If an eval case fails:

1. **Check raw output** - Look at `rawOutput` in results JSON
   - Is schema valid? (Check JSON syntax)
   - Are expected keys present?

2. **Check case definition** - Re-read `cases.json` for edge case
   - What did we expect?
   - Does input reflect real scenario?

3. **Check prompt** - Review prompt template in `judge-prompts/` or service code
   - Did prompt change recently?
   - Is the instruction clear?

4. **Run with debugging** - Add logs to LLM calls:
   ```bash
   OPENROUTER_API_KEY=sk-... \
   ./gradlew test --tests "PromptEvalRunner" \
     -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
   ```

### If E2E test flakes:

1. **Check timeout** - Playwright defaults to 30s, AI features may be slow
   ```typescript
   await expect(element).toBeVisible({ timeout: 10000 }); // 10s
   ```

2. **Mock API delays** - Add latency to route mocks:
   ```typescript
   await page.route("**/api/**", async route => {
     await new Promise(r => setTimeout(r, 500)); // 500ms delay
     await route.continue();
   });
   ```

3. **Headless vs. headed** - Run headed to watch:
   ```bash
   npx playwright test --headed --test-match "navigation*"
   ```

---

## XII. Future Improvements

- [ ] Add visual regression tests for E2E (screenshot diffs)
- [ ] Expand eval dataset (currently 60 cases, target 100+)
- [ ] Add performance benchmarks (query latency, embedding speed)
- [ ] Integrate eval results into dashboard (trend tracking)
- [ ] Add fuzzing tests for prompt robustness
- [ ] Expand coverage of edge cases (unicode, very long inputs)
- [ ] Add A/B testing framework for prompt variants
