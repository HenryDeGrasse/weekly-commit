# Test & Eval Infrastructure — Quick Index

## 📊 By the Numbers

| Category | Count | Coverage |
|----------|-------|----------|
| Java test files | 54 | All core services, domains, lifecycle |
| Eval golden cases | 60 | 7 AI prompt types + edge cases |
| Critical eval cases | 12 | Must pass, block regression |
| E2E test suites | 4 | Golden path, AI flows, manager, navigation |
| Judge-scored dimensions | 15+ | clarity, specificity, faithfulness, measurable, etc. |
| Page objects | 3 | MyWeekPage, ReconcilePage, TeamPage |

---

## 🎯 Test Files Quick Links

### Eval Framework
- **Test cases:** `backend/src/test/resources/eval/*/cases.json` (7 subdirs, 60 total cases)
- **Judge prompts:** `backend/src/test/resources/eval/judge-prompts/` (3 files)
- **Test executor:** `backend/src/test/java/com/weeklycommit/ai/eval/PromptEvalRunner.java`
- **Threshold config:** `eval-thresholds.json`
- **Baseline results:** `eval-baseline.json`
- **Threshold check script:** `scripts/eval-threshold-check.js`

### Java Tests by Category
| Category | Files | Key Tests |
|----------|-------|-----------|
| **AI Services** | 10 | CommitLintServiceTest, CommitDraftAssistServiceTest, RiskDetectionServiceTest, RcdoSuggestServiceTest, ReconcileAssistServiceTest, WhatIfServiceTest, ManagerAiSummaryServiceTest |
| **Plan Lifecycle** | 3 | WeeklyPlanServiceTest, CommitServiceTest, PlanHistoryServiceTest |
| **Locking & Audit** | 5 | LockServiceTest, LifecycleIdempotencyTest, AuditLogServiceTest, AutoLockJobTest, AutoReconcileJobTest |
| **Reconciliation** | 3 | ReconciliationServiceTest, ScopeChangeServiceTest, ReconcileControllerTest |
| **RAG & Search** | 4 | EmbeddingServiceTest, SemanticQueryServiceTest, InsightGenerationServiceTest, PineconeClientTest |
| **Domain & Validation** | 3 | EntityValidationTest, RcdoLinkageValidatorTest, RcdoServiceTest |
| **Authorization** | 2 | AuthorizationServiceTest, AuditLogPermissionTest |
| **Controllers** | 10+ | PlanControllerTest, CommitControllerTest, TeamControllerTest, NotificationControllerTest, etc. |
| **Config & Notifications** | 4 | ConfigurationServiceTest, NotificationServiceTest, EmailDigestServiceTest, HealthControllerTest |

### E2E Tests
- **Golden path:** `e2e/golden-path.spec.ts` (core workflow)
- **AI flows:** `e2e/ai-flows.spec.ts` (mocked AI endpoints)
- **Manager flow:** `e2e/manager-flow.spec.ts` (team dashboard)
- **Navigation:** `e2e/navigation.spec.ts` (layout & routing)
- **Page objects:** `e2e/pages/` (MyWeekPage.ts, ReconcilePage.ts, TeamPage.ts)

---

## 🚀 Running Tests

### All Java Unit Tests (fast, excludes eval)
```bash
./gradlew test
```

### Eval Tests (slow, real LLM calls)
```bash
OPENROUTER_API_KEY=sk-... ./gradlew test --tests "com.weeklycommit.ai.eval.*"
```

### Specific Test File
```bash
./gradlew test --tests "CommitLintServiceTest"
```

### Threshold Check (after eval run)
```bash
node scripts/eval-threshold-check.js
```

### E2E Tests (requires running backend + frontend)
```bash
# Start backend: ./gradlew bootRun
# Start frontend: npm run dev
npx playwright test

# Specific file:
npx playwright test e2e/golden-path.spec.ts

# Headed (watch browser):
npx playwright test --headed
```

---

## 📋 Eval Case Summary by Prompt Type

### 1. Commit Lint (6 cases, critical: 3)
**Tests:** Hard/soft validation rules for plan linting
- ✅ Missing criteria + vague title detection
- ✅ Chess piece limits (KING, QUEEN)
- ✅ Fragmentation warnings
- ✅ Duplicate title detection

### 2. Commit Draft Assist (12 cases, critical: 4)
**Tests:** Title/description/criteria/estimate suggestions
- ✅ Vague input → specific title + criteria
- ✅ Already good → all nulls
- ✅ Bad estimate → fix to range
- ✅ Special characters → clean title

### 3. RCDO Suggest (10 cases, critical: 0)
**Tests:** Semantic matching to RCDO tree
- ✅ Keyword match → correct Outcome
- ✅ No match → no suggestion
- ✅ Ambiguous → pick most specific
- ✅ Never suggest Rally Cry directly

### 4. Reconcile Assist (8 cases, critical: 0)
**Tests:** Outcome inference from evidence
- ✅ Ticket status → outcome mapping
- ✅ Carry-forward recommendations
- ✅ Scope change detection
- ✅ Unlinked commit inference

### 5. Risk Signal (8 cases, critical: 0)
**Tests:** Proactive risk detection
- ✅ Hidden dependencies
- ✅ Concentration risks
- ✅ Estimate mismatches
- ✅ Blocking language detection

### 6. RAG Query (8 cases, critical: 0)
**Tests:** Semantic understanding & answer synthesis
- ✅ Factual lookup from chunks
- ✅ Multi-chunk synthesis
- ✅ Temporal reasoning
- ✅ Hallucination avoidance

### 7. What-If (8 cases, critical: 2)
**Tests:** Scenario analysis narratives
- ✅ Overcommit scenarios
- ✅ Risk resolution
- ✅ RCDO coverage shifts
- ✅ Budget utilization messaging

---

## 🎲 Threshold Gates

### Hard Gates (Block CI/CD if fail)
- ✅ `schema_valid_rate = 1.00` (100% of outputs valid JSON)
- ✅ `overall_case_pass_rate ≥ 0.95` (95% of cases pass)
- ✅ `critical_case_pass_rate = 1.00` (100% of critical cases)

### Soft Gates (Report only)
- 🔔 `judge_high_stakes.*.mean_faithfulness_min ≥ 0.90` (risk/query features)
- 🔔 `judge_assistive.*.mean_clarity_min ≥ 0.70` (draft/lint/suggest)
- 🔔 `regression.faithfulness_drop_max ≤ 0.03` (max 3% drop)
- 🔔 `regression.pass_rate_drop_max ≤ 0.05` (max 5% drop)

**Exit code:** 0 if all hard gates pass (soft gates don't block)

---

## 🏗️ Architecture Patterns

### Unit Tests
```
Service → @Mock Repository → Mockito behavior verification
(Fast, deterministic, isolated)
```

### Eval Tests
```
Golden Case JSON → Real LLM (OpenRouter) → Output schema validation → 
Judge prompts (LLM scoring) → EvalResult JSON → Threshold check
(Slow ~10min, real quality metrics, regression detection)
```

### E2E Tests
```
Page Object → Playwright automation → Mocked AI endpoints → 
UI assertion
(Medium speed ~3min, integration testing, UI validation)
```

---

## 🔍 Most Important Files to Read

1. **Understanding the eval framework:**
   - `TEST_AND_EVAL_SUMMARY.md` (this repo) - Full technical spec
   - `eval-thresholds.json` - Gate definitions
   - `backend/src/test/resources/eval/*/cases.json` - Golden datasets

2. **Understanding the scoring:**
   - `scripts/eval-threshold-check.js` - How thresholds are computed
   - `backend/src/test/resources/eval/judge-prompts/*.txt` - Judge criteria

3. **Understanding the executor:**
   - `backend/src/test/java/com/weeklycommit/ai/eval/PromptEvalRunner.java` - Main eval logic

4. **Understanding E2E:**
   - `e2e/golden-path.spec.ts` - Happy path workflow
   - `e2e/pages/MyWeekPage.ts` - Page object pattern

5. **Understanding AI services:**
   - `backend/src/test/java/com/weeklycommit/ai/service/CommitLintServiceTest.java`
   - `backend/src/test/java/com/weeklycommit/ai/service/RiskDetectionServiceTest.java`

---

## 🐛 Common Issues & Debugging

| Issue | Debug Steps |
|-------|------------|
| Eval case fails | Check `rawOutput` schema, recheck case definition in `cases.json` |
| E2E test flakes | Increase timeout, mock API delays, run headed to watch |
| Judge score drops | Rerun eval 2-3 times (LLM variance), check prompt template |
| Regression detected | Compare latest results to `eval-baseline.json` |
| Schema invalid | Check JSON syntax in LLM output, check service response |

---

## 📈 Metrics & Tracking

**Latest eval run (from eval-baseline.json):**
- Total cases: 18 (partial set for example)
- Passed: 17 (94.4%)
- Failed: 1 (5.6%)
- Schema valid: 100%
- Mean faithfulness: 0.89
- Critical pass rate: 100%

**Status:** ⚠️ Soft warnings on RISK_SIGNAL faithfulness (0.87 < 0.90 threshold)

---

## 🚧 Quick Setup

```bash
# Backend
cd backend
./gradlew test                          # Unit tests only
OPENROUTER_API_KEY=sk-... \
  ./gradlew test --tests "*eval*"       # Full eval (slow)

# Frontend  
cd frontend
npm test                                 # Jest unit tests (if any)
npx playwright test                      # E2E tests (requires running backend)

# Check thresholds
cd ..
node scripts/eval-threshold-check.js     # After eval run
```

---

## 📚 References Within This Project

- AI Pipeline Reference: `AI_PIPELINE_REFERENCE.md`
- Database Schema: `DATABASE_SCHEMA_REFERENCE.md`
- Domain Model: `DOMAIN_MODEL_REFERENCE.md`
- Main README: `README.md`

---

**Last Updated:** 2026-03-29
**Test Count:** 54 Java files, 60 eval cases, 4 E2E suites (~30+ tests)
**Coverage:** Domain logic, AI services, lifecycle, RAG, authorization, notifications, controllers
