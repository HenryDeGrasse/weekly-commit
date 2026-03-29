# Weekly Commit Module — Comprehensive Reference
## Documentation & Infrastructure Overview

**Generated:** 2026-03-29  
**Project:** Weekly Commit Module (Micro-frontend for weekly planning and reconciliation)  
**Status:** In Development (V1 scope active)

---

## Table of Contents

1. [Product & Requirements](#product--requirements)
2. [AI Capabilities & Evaluation](#ai-capabilities--evaluation)
3. [Technology Stack & Architecture](#technology-stack--architecture)
4. [Infrastructure & Operations](#infrastructure--operations)
5. [CI/CD Pipeline](#cicd-pipeline)
6. [Configuration & Environment](#configuration--environment)
7. [Key Metrics & Success Criteria](#key-metrics--success-criteria)
8. [Open Planning Items](#open-planning-items)

---

## Product & Requirements

### Documentation Files Retrieved

| File | Lines | Summary |
|---|---|---|
| `/Users/henrydegrasse/Development/GauntletAi/HPP/ST6V2/docs/prd.md` | 1-1386 | **Product Requirements Document** — Complete specification for Weekly Commit Module v1 (30 sections, 87KB). Core domain model: weekly plans, commits (chess-layered), capacity points, RCDO hierarchy, lifecycle state machine (DRAFT → LOCKED → RECONCILING → RECONCILED), reconciliation with carry-forward, manager dashboards, minimal native tickets, AI assistance (6 initial v1 capabilities). Non-goals explicitly exclude performance scoring, surveillance-style monitoring, and autonomous AI agents. |
| `/Users/henrydegrasse/Development/GauntletAi/HPP/ST6V2/docs/assignment.md` | Full | **Assignment Brief** — Defines the role: "ai-accelerated" category, all roles can access. Problem statement: replace 15-Five with system that connects weekly commitments to organizational strategy (Rally Cries, Defining Objectives, Outcomes). Requirements: TypeScript (strict mode), Java 21, SQL. Functional scope: weekly commit CRUD, RCDO linking, chess layer, full lifecycle state machine, reconciliation, manager dashboard, micro-frontend integration. |
| `/Users/henrydegrasse/Development/GauntletAi/HPP/ST6V2/docs/ai-eval-roadmap.md` | Full | **AI Evaluation & Quality Roadmap** — 4 phases: (1) Eval Foundation — golden test datasets (15-20 cases per capability), eval runner with LLM-as-judge scoring, prompt version tracking, faithfulness scoring; (2) Prompt Quality — few-shot examples for all 10 prompts, field-level context descriptions, deduplicate risk-signal prompt logic, enrich chunks with RCDO context; (3) Frontend AI Surfaces — 16 AI components (12 currently mounted); (4) Production Monitoring — async faithfulness sampling, Prometheus metrics (`weekly_commit_ai_*`), drift alerting (Manus thresholds: Faithfulness ≥0.90, Context Recall ≥0.85). **Missing:** Grafana dashboard for AI quality. |
| `/Users/henrydegrasse/Development/GauntletAi/HPP/ST6V2/docs/ai-thoughts.md` | Full | **Current AI Architecture Review** — Strong points: domain/architecture docs are concrete, backend AI stack is real (provider abstraction, Pinecone RAG, async eval, Prometheus metrics). Dormant components: `RiskSignalsPanel`, `ReconcileAssistPanel`, `EvidenceDrawer` (not mounted but implemented). Recommendations: (1) mount or remove dormant components; (2) finish Grafana dashboard; (3) expand judge-backed evals; (4) publish eval outputs as docs; (5) build higher-order planning intelligence (what-if planner, strategic advisor). |
| `/Users/henrydegrasse/Development/GauntletAi/HPP/ST6V2/docs/eval-results.md` | Full | **AI Eval Results** — Coverage of 7 AI capabilities: commit-draft-assist (12 cases), commit-lint (6), rcdo-suggest (10), risk-signal (8), reconcile-assist (8), rag-query (8), what-if (8) = 60 total. Two-level eval approach: (1) LLM prompt eval against golden cases (`PromptEvalRunner` via `./gradlew evalTest`); (2) Historical replay benchmark on 12 synthetic plans (signal precision/recall/F1). Run: `export OPENROUTER_API_KEY=...` then `./gradlew evalTest`. |
| `/Users/henrydegrasse/Development/GauntletAi/HPP/ST6V2/docs/ui-ux-decisions.md` | Full | **UI/UX Decision Guide** — 6 research-backed recommendations: (1) **Semantic colors** — add muted semantic tones (success #5F7A66, warning #8A7A5A, danger #8A5C5C) to monochrome base for status indicators; (2) **Progressive disclosure on My Week** — collapse non-critical sections (AI lint, insights, what-if, history), expand only when relevant or explicitly clicked; (3) **AI suggestion UX tiering** — graduated engagement (passive hints → contextual after action → explicit invocation), respect dismissal memory, rate limiting; (4) **Typography** — switch from Geist Mono as primary to Inter/Geist Sans (readable, technical feel via monospace only for IDs/commit titles/estimates); (5) **Charts library** — migrate from pure CSS to Tremor or Recharts (8 chart types, beyond CSS capability threshold); (6) **Loading & empty states** — 3-tier strategy (skeleton for <2s, progress for 2-5s, explicit retry for >5s) + illustrations for empty states. Priority order: colors (highest ROI), typography, progressive disclosure, loading/empty, AI tiering, charts. |
| `/Users/henrydegrasse/Development/GauntletAi/HPP/ST6V2/duet-plan-whatif.md` | Full | **What-If Planner & Historical Replay Plan** — Beyond-scope feature to demonstrate system can reason about hypothetical plans. **Deliverables:** (1) **Backend (5 steps):** WhatIfRequest/Response DTOs, `WhatIfService` (no LLM, pure compute using existing rules + read models), `what-if.txt` prompt template, API endpoint at `POST /api/ai/what-if`, LLM narration integration; (2) **Frontend (1 step):** `WhatIfPanel.tsx` component (mutation form, results display with capacity delta, RCDO coverage changes, risk delta, narrative), integrated into MyWeek.tsx; (3) **Evals (1 step):** 8 golden test cases for what-if narration, extended `PromptEvalRunner`; (4) **Benchmark (1 step):** Historical replay test against 10-12 synthetic plans, compute confusion matrices per risk signal, output to `build/eval-results/replay-benchmark.json` + markdown summary. Constraints: Java 21, TypeScript strict, SQL only; no new infrastructure; reuse RiskDetectionService, UserWeekFact, AiProviderRegistry. |

### PRD Key Concepts

**Core Domain Model:**
- **Weekly Plan** (owner, week, state, budget snapshot) — exactly one per user per week
- **Commit** (title, chess piece, priority, primary RCDO, estimate points, success criteria) — weekly promise linked to one RCDO
- **Chess Layer** (King/Queen/Rook/Bishop/Knight/Pawn) — required prioritization taxonomy; guards: max 1 King, max 2 Queens; affects risk signaling, rollup weight
- **RCDO Hierarchy** (Rally Cry → Defining Objective → Outcome) — strategic linkage; commits link to Outcomes by default; archived nodes visible in history but not selectable for new commits
- **Capacity** (coarse 1/2/3/5/8 points on 10-point budget) — represents weekly work allocation, not precise time
- **Lifecycle State Machine:**
  - DRAFT (editable, no lock snapshot)
  - LOCKED (baseline snapshot captured, post-lock changes create append-only events)
  - RECONCILING (auto-transition at week end, shows planned vs actual)
  - RECONCILED (immutable actual snapshot, terminal)
- **Carry-Forward** (explicit per incomplete commit) — creates new commit with provenance lineage, not in-place mutation
- **Scope Change Event** (append-only) — reason required for adds, removes, re-prioritization, material edits after lock
- **Team Weekly View** (derived read-model) — not a separate planning object; shows: all commits for team members + assigned/unassigned week-targeted tickets + uncommitted work
- **Native Ticket** (minimal model) — statuses: Backlog, Ready, In Progress, Blocked, Done, Canceled; can be linked to commit; completion auto-marks linked commit Achieved
- **Manager Exception Queue** — flags: missed locks, missed reconcile, over-budget, repeated carry-forward, King posts-lock changes, scope volatility

**Product Principles:**
1. Weekly plans are contracts, not notes — structured, lockable, reconcilable
2. Link strategy explicitly — RCDO linkage required field
3. Keep the ritual short — aim for <10 min planning, <7 min reconciliation
4. History is truthful — no silent edits after lock
5. Managers handle exceptions, not routine approvals
6. AI is visible, explainable, ignorable
7. Structure helps, doesn't police

**V1 Scope (In / Out):**

| In V1 | Post-V1 |
|---|---|
| Weekly plan + commit CRUD | Many-to-many commit-to-RCDO |
| Single primary RCDO per commit | Jira/GitHub/external integrations |
| Chess layer + capacity points | Dependency tracking, sprint boards |
| Manual lock + auto-lock | Calendar/PTO auto-adjust |
| Post-lock scope change events | Conversational AI coaching |
| Explicit carry-forward with lineage | Cross-team dependency summaries |
| Minimal native tickets (6 statuses) | Autonomous AI agent edits |
| Manager team dashboard + exceptions | Advanced dependency graphs |
| Rules-based notifications + AI assistance | Deep contextual retrieval |
| Conservative, explainable AI (6 v1 capabilities) |  |

---

## AI Capabilities & Evaluation

### V1 AI Capabilities

| # | Capability | Input | Output | Constraint |
|---|---|---|---|---|
| 1 | **COMMIT_DRAFT_ASSIST** | Partial commit + historical similar | Suggested title, description, success criteria, estimate points | Offer suggestions only; never auto-apply; disable if user dismisses 3x |
| 2 | **COMMIT_LINT** | Draft/locked commit | Flags: vague title, missing success criteria, parent-level RCDO, duplicate title, estimate inconsistency, over-fragmented plan | Runs on save + pre-lock; distinguish hard validation from soft guidance |
| 3 | **RCDO_SUGGEST** | Commit text + linked ticket data + similar commits | Node ID, confidence level, rationale (keyword match, ticket RCDO, historical analog) | Show only above confidence threshold; never auto-link |
| 4 | **RISK_SIGNAL** | Plan + commits + ticket status | Flags: OVERCOMMIT, UNDERCOMMIT, REPEATED_CARRY_FORWARD, BLOCKED_CRITICAL, SCOPE_VOLATILITY | Runs daily on LOCKED plans; LLM finds additional risks beyond 5 rules; show count badge, expand details on click |
| 5 | **RECONCILE_ASSIST** | Baseline plan + scope changes + linked ticket evidence | Likely outcome for unlinked commits, draft summary of changes, carry-forward recommendation | Suggestions only; user edits inline before submit |
| 6 | **TEAM_SUMMARY** | All team commits for week + RCDO rollup + exception queue | AI-generated team summary (strategic branches receiving effort, unresolved exceptions, carry-forward patterns, blocked items) | Cite underlying objects; not a black-box narrative |

**Additional capabilities (from eval roadmap):**
- **RAG_INTENT, RAG_QUERY** — semantic search over plans/tickets; context recall ≥0.85
- **TEAM_INSIGHT, PERSONAL_INSIGHT** — generated daily at 08:00 UTC
- **WHAT_IF** — hypothetical plan mutations + impact analysis + LLM narration (beyond-scope, documented in duet-plan-whatif.md)

### AI Architecture

**Provider Stack:**
- **LLM:** OpenRouter (abstracted via `AiProviderRegistry`) → Claude Sonnet 4 (from .env) — fallback to StubAiProvider if unavailable
- **Embeddings:** OpenAI text-embedding-3-small (from `EmbeddingService`)
- **Vector Store:** Pinecone (RAG index `weekly-commit`, us-east-1) — async indexing via `SemanticIndexService`
- **Prompt Templates:** 10 templates in `backend/src/main/resources/prompts/` with few-shot examples + field-level docs
- **Context Assembly:** `AiContext` object with suggestionType, commitData, planData, historicalCommits, rcdoTree, additionalContext
- **Faithfulness Scoring:** Async LLM-as-judge via `FaithfulnessEvaluator` (high-stakes types always scored, lower-stakes sampled at 10%)

**Indexing Pipeline:**
```
Entity (commit/scope-change/ticket/plan-summary/manager-comment)
  → ChunkBuilder.build*Chunk(enrichmentContext: RCDO path, team name, carry-forward lineage, linked ticket summary)
  → EmbeddingService.embed() [cached at Redis layer]
  → PineconeClient.upsert() [async, idempotent]
  → SemanticIndexService daily sweep (03:00 UTC) reindexes plans modified in last 48h
```

**Query Pipeline:**
```
User question
  → SemanticQueryService.classify intent (LLM) [RAG_INTENT type]
  → EmbeddingService.embed()
  → PineconeClient.query(topK=5, filters on team/user/date)
  → Context assembly [includes retrieved chunk metadata]
  → RAG answer generation (LLM) [RAG_QUERY type]
  → FaithfulnessEvaluator.score() [async, high-stakes]
  → AiSuggestionService.store() + return to frontend
```

**Scheduled Jobs:**
- `InsightGenerationService.generateDailyInsights()` — 08:00 UTC, team + personal insights
- `InsightGenerationService.generatePersonalInsightsAsync()` — triggered on plan lock
- `RiskDetectionService.runDailyRiskDetection()` — runs on all LOCKED plans
- `SemanticIndexService.dailySweepReindex()` — 03:00 UTC, reindex plans modified in last 48h

### Frontend AI Components (16 total, 12 mounted)

**Mounted in production routes:**
1. `AiLintPanel` — commit quality lint display (calls COMMIT_LINT)
2. `InsightPanel` — team + personal insights (reads TEAM_INSIGHT, PERSONAL_INSIGHT)
3. `SemanticSearchInput` — RAG query interface with suggested questions (calls RAG_INTENT + RAG_QUERY)
4. `QueryAnswerCard` — RAG answer with source citations
5. `AiFeedbackButtons` — thumbs up/down on all AI outputs
6. `CommitDraftAssistButton` — ✨ AI Suggest in commit form (calls COMMIT_DRAFT_ASSIST)
7. `AiCommitComposer` — AI-guided commit creation flow
8. `ProactiveRiskBanner` — critical risk signal banner
9. `TeamRiskSummaryBanner` — team-level risk overview
10. `ManagerAiSummaryCard` — AI-generated team summary (calls TEAM_SUMMARY)
11. `RcdoSuggestionInline` — RCDO link suggestion (calls RCDO_SUGGEST)
12. `AiSuggestedBadge` — visual indicator for AI-prefilled content

**Implemented but not currently mounted:**
- `RiskSignalsPanel` — detailed risk signal display (reads RISK_SIGNAL)
- `ReconcileAssistPanel` — manual AI reconciliation assistant (calls RECONCILE_ASSIST)
- `EvidenceDrawer` — explainability drawer for structured evidence bundles

### Evaluation Infrastructure

**Golden Datasets:** 60+ test cases across 7 capabilities stored as `backend/src/test/resources/eval/<capability>/cases.json`

**Eval Runner:** `PromptEvalRunner.java` (parameterized, tagged `@Tag("eval")`, excluded from normal `./gradlew test`)
- Calls real LLM (not mocked)
- Scores via automated checks (schema validity, behavioral rules) + LLM-as-judge (title quality, criteria measurability)
- Outputs JSON results to `build/eval-results/eval-<timestamp>.json`
- Integrates with `eval-thresholds.json` + `eval-baseline.json` for regression detection via `scripts/eval-threshold-check.js`

**Historical Replay Benchmark:** Tests 12 deterministic synthetic plans; computes per-signal confusion matrices (precision/recall/F1); outputs `build/eval-results/replay-benchmark.json`

**Manus Research Thresholds (from eval roadmap):**

| Metric | Dev Gate | Staging | Prod Alert |
|---|---|---|---|
| Faithfulness | ≥0.85 | ≥0.90 | < 0.88 |
| Context Recall | ≥0.80 | ≥0.85 | < 0.82 |
| Answer Relevancy | ≥0.75 | ≥0.80 | < 0.78 |
| Context Precision | ≥0.65 | ≥0.70 | < 0.68 |
| P95 Latency | < 10s | < 7s | > 8s |

---

## Technology Stack & Architecture

### Backend

| Layer | Technology | Details |
|---|---|---|
| **Language** | Java 21 | Strict typed, modular monolith for v1 |
| **Framework** | Spring Boot 3.x | REST/JSON APIs, auto-config, embedded Tomcat |
| **Build** | Gradle 8.x | Multi-module, Kotlin DSL, checkstyle via spotless |
| **Database** | PostgreSQL 16 | RDS ready (see terraform/main.tf); Flyway migrations |
| **ORM/Query** | JPA + Spring Data, JPQL | Type-safe queries via QueryDSL recommended |
| **API Docs** | OpenAPI 3.x | Contract verification in CI (`/api-docs` endpoint) |
| **Monitoring** | Micrometer + Prometheus | Metrics at `/actuator/prometheus` |
| **Testing** | JUnit 5 + Mockito + TestContainers | @Tag("eval") separates eval tests; 99% unit coverage target |
| **Logging** | Logback + structured JSON | ECS-compatible logging for log aggregation |

**Bounded Modules (recommended):**
- `weekly-planning` — plan CRUD, state machine, lifecycle transitions
- `reconciliation` — reconcile snapshots, carry-forward, outcome matching
- `rcdo` — hierarchy management, active/archived states, versioning
- `tickets` — native work items, status workflow, assignment, linking to commits
- `notifications` — rules-based + triggered rules
- `ai-suggestions` — provider abstraction, prompt templating, async scoring
- `reporting` — derived read models, manager dashboards, audit logs

### Frontend

| Layer | Technology | Details |
|---|---|---|
| **Language** | TypeScript (strict mode) | No implicit any; exported .d.ts from shared pkg |
| **Framework** | React 18 | Hook-based, Suspense-ready, strict mode enabled |
| **Build** | Vite 5.x | Fast HMR, optimized production builds |
| **Package Mgr** | npm workspaces (monorepo) | `@weekly-commit/shared` (types, utils), `frontend` (app), `backend` separate |
| **UI Library** | Custom design system (Tailwind) | `frontend/src/components/ui/` — Card, Button, Badge, Input, etc. |
| **State Management** | React Query (tanstack-query) | For server-state; useContext for UI state |
| **HTTP Client** | Fetch API (custom wrapper) | `frontend/src/api/` — factory pattern, response interceptors |
| **Routing** | React Router v6 | Nested routes, lazy code splitting |
| **Testing** | Vitest + Playwright (E2E) | Unit tests in `src/**/*.test.ts`, E2E in `e2e/tests/` |
| **Linting** | ESLint 8 + Prettier | Monorepo-aware, `npm run lint` for all workspaces |
| **Type Generation** | openapi-typescript | CI contract verification, API types from backend spec |

**Folder Structure:**
```
frontend/
├── src/
│   ├── api/           — service layer (planApi, aiApi, whatIfApi, etc.)
│   ├── components/
│   │   ├── ui/        — primitive UI components (Card, Button, etc.)
│   │   ├── ai/        — AI-specific UI (CommitDraftAssistButton, InsightPanel, etc.)
│   │   └── layout/    — page templates, headers, footers
│   ├── routes/        — page components (MyWeek.tsx, Reconcile.tsx, TeamWeek.tsx, etc.)
│   ├── hooks/         — custom hooks (useWeeklyPlan, useAiApi, etc.)
│   ├── types/         — shared TS interfaces (generated from OpenAPI + custom)
│   ├── styles/        — global CSS, Tailwind config
│   └── lib/           — utilities (date helpers, formatting, constants)
├── e2e/               — Playwright E2E tests
└── vite.config.ts     — build config (React plugin, resolve, ssr: false)
```

### Micro-Frontend Integration

**Assumption:** Route-level micro-frontend (Module Federation or similar) mounted under PA host app.

**Host Contract (what host provides):**
- Authenticated user identity + team/manager chain
- SSO via OAuth/OIDC
- Navigation context (breadcrumbs, deep linking)
- Design tokens + theme (light/dark, color palette)
- Feature flags (FEATURE_AI_ASSISTANCE, FEATURE_NOTIFICATIONS)
- Telemetry callback or SDK access
- Notification center bridge

**Remote Exposure (what module exports):**
- Full page routes under `/weekly-commit/*` prefix
- Optional summary widget for host dashboards
- Typed events for navigation + telemetry

---

## Infrastructure & Operations

### Prometheus & Alerting

**Prometheus Configuration** (`infra/prometheus/prometheus.yml`):
- **Scrape interval:** 15 seconds
- **Evaluation interval:** 15 seconds
- **Targets:** `backend:8080` (Spring Boot Actuator at `/actuator/prometheus`)
- **Rule file:** `alerts.yml`

**Alert Rules** (`infra/prometheus/alerts.yml`):

| Alert | Expression | Severity | Duration | Notes |
|---|---|---|---|---|
| **HighErrorRate** | 5xx rate > 5% | critical | 2m | Per-endpoint 5xx errors |
| **SlowEndpoint** | p95 latency > 2s | warning | 5m | Histogram quantile |
| **AutoLockJobFailure** | failures > 0 in 15m | warning | 1m | Lifecycle job metric |
| **AutoReconcileJobFailure** | failures > 0 in 15m | warning | 1m | Lifecycle job metric |
| **DatabaseConnectionPoolExhausted** | active/max > 0.9 | critical | 2m | HikariCP pool utilization |
| **HighJvmMemoryUsage** | heap > 85% | warning | 5m | GC pressure indicator |
| **AiFaithfulnessLow** | score < 0.88 | warning | 30m | Manus threshold (prod alert) |
| **AiFaithfulnessCritical** | score < 0.85 | critical | 30m | Manus threshold (dev gate) |
| **AiAcceptanceRateLow** | acceptance_rate < 0.20 | warning | 1h | User dismissal metric |
| **AiProviderDown** | available == 0 | critical | 5m | OpenRouter unreachable (non-blocking) |

**Metrics Exposed (via Spring Boot Actuator):**
- `http_server_requests_seconds_*` (count, sum, max histograms, by method/uri/status)
- `hikaricp_connections_*` (active, idle, max, pending)
- `jvm_memory_used_bytes`, `jvm_memory_max_bytes` (by area: heap, nonheap)
- `weekly_commit_ai_faithfulness_score` (gauge, by suggestion_type)
- `weekly_commit_ai_acceptance_rate` (gauge, by suggestion_type)
- `weekly_commit_ai_provider_available` (gauge, 1/0)
- `weekly_commit_ai_tokens_total` (counter)
- `weekly_commit_ai_requests_total` (counter)
- `auto_lock_failures_total`, `auto_reconcile_failures_total` (counters)

### Grafana Dashboards

**Datasource** (`infra/grafana/provisioning/datasources/prometheus.yml`):
- Name: `Prometheus`
- URL: `http://prometheus:9090` (internal Docker network)
- Default datasource: yes
- Editable: no (immutable via provisioning)

**Dashboard Provisioning** (`infra/grafana/provisioning/dashboards/dashboards.yml`):
- Folder: "Weekly Commit"
- Type: file-based
- Path: `/etc/grafana/provisioning/dashboards/json/`
- Auto-load `.json` files in that directory

**Service Health Dashboard** (`infra/grafana/provisioning/dashboards/json/service-health.json`):
- **Request Rate** (timeseries) — `rate(http_server_requests_seconds_count[1m])` by method/uri/status
- **Response Time p95** (timeseries) — `histogram_quantile(0.95, ...)` by uri
- **Error Rate (5xx)** (stat gauge) — % of 5xx errors, color thresholds: green 0%, yellow 1%, red 5%
- **JVM Heap Usage** (gauge) — heap/max ratio, thresholds: green 0%, yellow 70%, red 85%
- **HikariCP Active Connections** (timeseries) — active vs max pool
- **Plans Created (per minute)** (timeseries) — POST /api/plans rate

**Missing Dashboard (TO DO):** `infra/grafana/provisioning/dashboards/json/ai-quality.json` — should include:
- Faithfulness by suggestion type (7-day rolling avg, threshold lines at 0.85/0.88)
- Acceptance rate by suggestion type
- Provider availability gauge (1/0)
- Request volume + token consumption
- Latency percentiles for RAG queries
- Eval test pass rates trend

### Terraform Infrastructure

**File:** `infra/terraform/main.tf`

**Scope:** AWS stack for dev/staging/production deployments

**Key Resources:**
1. **VPC + Subnets:** 10.0.0.0/16 CIDR, 2 public subnets (10.0.1.0/24, 10.0.2.0/24) in AZs a/b
2. **Internet Gateway + Route Tables:** Public route via IGW
3. **Security Groups:**
   - **DB SG:** Inbound from app SG on port 5432
   - **App SG:** Inbound 8080 (app), 80/443 (HTTP/S) from 0.0.0.0/0
4. **RDS PostgreSQL:**
   - Engine version: 16.3
   - Instance class: configurable (default `db.t3.micro`)
   - Multi-AZ in production
   - Storage encrypted
   - Backup: 7 days in prod, 1 day in dev
   - Auto-scaling up to 100 GB

**Variables (via `.tfvars` or env):**
- `aws_region` — default us-east-1
- `environment` — dev/staging/production (affects multi-az, backup retention, snapshots)
- `db_instance_class` — default db.t3.micro
- `db_name`, `db_username`, `db_password` (db_password marked sensitive)

**Outputs:**
- `vpc_id` — VPC identifier
- `db_endpoint` — RDS connection string
- `db_name` — database name

**Remote State:** Commented-out S3 backend config (requires `weekly-commit-terraform-state` bucket + `terraform-locks` DynamoDB table)

---

## CI/CD Pipeline

**File:** `.github/workflows/ci.yml`

### Workflow Triggers
- **On push to `main`:** Full pipeline (lint → test → build → E2E → API contract check)
- **On PR to `main`:** Lint, unit tests, build, API contract check (no E2E unless merged)
- **Concurrency:** Group by workflow + ref, cancel in progress

### Jobs (in order)

| Job | Runs | Checks | Artifacts | Notes |
|---|---|---|---|---|
| **lint** | ubuntu-latest | ESLint (frontend), TypeScript typecheck (frontend + shared), spotless (backend Java formatting) | — | Node 20 + Java 21 cache |
| **test-frontend** | ubuntu-latest | Vitest with coverage (frontend only) | `frontend/coverage/` lcov report | Uploads coverage artifact |
| **test-backend** | ubuntu-latest | JUnit 5 tests via `./gradlew test` | `backend/build/reports/tests/` HTML report | Uploads report artifact |
| **build** | ubuntu-latest | `npm run build` (shared + frontend), `./gradlew bootJar` (backend) | — | Depends: lint + test jobs; validates entire monorepo builds |
| **e2e** | ubuntu-latest + postgres service | Playwright tests (requires OPENROUTER_API_KEY if AI tests present) | `e2e/test-results/` traces | On push to main only; starts backend bootRun + frontend vite preview |
| **api-contract** | ubuntu-latest + postgres service | Fetches `/api-docs`, generates TypeScript types, verifies types compile | — | PR only; ensures frontend types match backend OpenAPI spec |

### Key Features

**Postgres Service (E2E + API Contract):**
```yaml
postgres:
  image: postgres:16-alpine
  env:
    POSTGRES_DB: weekly_commit
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: postgres
  ports:
    - 5432:5432
  health-checks: pg_isready -U postgres (retry 10x every 5s)
```

**Backend Startup (E2E + API Contract):**
```bash
cd backend && ./gradlew bootRun &
for i in $(seq 1 30); do
  curl -sf http://localhost:8080/health && break
  sleep 2
done
```

**Frontend Dev Server (E2E only):**
```bash
cd frontend && npx vite preview --port 5173 &
```

**Playwright E2E:**
```bash
npx playwright install --with-deps chromium
npm run e2e
```

**OpenAPI Type Verification:**
```bash
curl -sf http://localhost:8080/api-docs > /tmp/openapi.json
npx openapi-typescript /tmp/openapi.json -o /tmp/api-types.ts
npx tsc --noEmit /tmp/api-types.ts --esModuleInterop --moduleResolution node
```

---

## Configuration & Environment

### .env File
**Location:** `.env` (checked in for dev; would be secrets in prod)

```env
# AI Provider
OPENROUTER_API_KEY=sk-or-v1-758b4c...  (Claude Sonnet 4)
OPENROUTER_MODEL=anthropic/claude-sonnet-4
OPENROUTER_MAX_TOKENS=1024

# Pinecone Vector DB
PINECONE_API_KEY=pcsk_...
PINECONE_INDEX_NAME=weekly-commit
PINECONE_ENVIRONMENT=us-east-1

# Database
DB_USER=postgres
DB_PASSWORD=postgres

# Feature Flags
FEATURE_AI_ASSISTANCE=true
FEATURE_NOTIFICATIONS=true
```

### .gitignore
Standard Node.js + Java + IDE ignores:
- `node_modules/`, `dist/`, `build/`, `.gradle/`
- `*.class`, `*.jar`, `*.war`
- `.idea/`, `.vscode/`, `.env`, `.env.local`
- `coverage/`, `*.log`, `hs_err_pid*`

### Docker Configuration

**Backend Dockerfile** (`backend/Dockerfile`):
- **Build stage:** Alpine JDK 21, Gradle bootJar (skip tests)
- **Runtime stage:** Alpine JRE 21, non-root appuser, entrypoint `java -jar app.jar`
- **Expose:** 8080
- **Base image:** eclipse-temurin (official Eclipse Foundation Java image)

**Frontend Dockerfile** (`frontend/Dockerfile`):
- **Stage 1 (shared-builder):** Node 20-alpine, install workspace deps, build shared package
- **Stage 2 (frontend-builder):** Copy shared output, build Vite frontend
- **Stage 3 (runtime):** nginx:1.27-alpine, serve `/app/frontend/dist` as static, custom nginx.conf
- **Expose:** 80

**Build Context:** Monorepo root (`.` context), Dockerfile can reference multi-stage via `FROM ... AS <name>`

---

## Key Metrics & Success Criteria

### Product Success Metrics (from PRD §21)

**Adoption & Workflow Health:**
- 85% of eligible users have locked plan weekly by week 8
- 70% manually lock before cutoff by week 12
- 80% reconciled by due time by week 12

**Alignment Quality:**
- 90% of locked commits linked to active Outcome or valid DO
- 95% of incomplete work in next week via explicit carry-forward (not copy/paste)

**Manager Usefulness:**
- 75% of managers weekly active in Team Week by week 8
- Manager survey score ≥4.0/5 on "I can see how my team's work maps to strategy"

**User Efficiency:**
- <10 min median IC planning time
- <7 min median reconciliation time
- <15 min median manager review time

**AI Usefulness:**
- ≥25% of active users accept ≥1 AI suggestion/month
- High-risk flags (overcommit/carry-forward) achieve ≥60% precision
- Negative feedback <10% of suggestion impressions

### Operational Metrics (from alerts.yml)

**API Health:**
- P95 latency < 2s per endpoint
- 5xx error rate < 5%
- Zero auto-lock/reconcile job failures

**Resource Health:**
- HikariCP connections < 90% utilized
- JVM heap < 85%

**AI Quality (Manus thresholds):**
- Faithfulness ≥0.90 (staging gate), ≥0.88 (prod alert threshold)
- Context Recall ≥0.85
- Provider available > 99.5% uptime
- Acceptance rate > 20% per suggestion type

---

## Open Planning Items

### From PRD §28 (Open Questions)

1. **Host Integration Detail** — Confirm exact micro-frontend remote-loading mechanism and shell contract with PA platform team
2. **Org Directory Source** — Home team and manager chain from host/identity layer or separate internal service?
3. **Historical Migration** — Migrate read-only 15-Five data, or start with fresh history?
4. **Nonstandard Workweeks** — Non-Monday/Friday defaults needed day one, or team-level overrides sufficient after pilot?

### From ai-thoughts.md (Recommendations)

1. **Dormant Components Decision** — Mount `RiskSignalsPanel`, `ReconcileAssistPanel`, `EvidenceDrawer` in product flow, OR explicitly mark as experimental, OR remove
2. **AI Quality Dashboard** — Build Grafana dashboard JSON for AI metrics (faithfulness, acceptance, provider availability, latency)
3. **Judge-Backed Eval Expansion** — Add judge prompts for RAG query, reconcile assist, RCDO suggest (beyond current commit-draft focus)
4. **Eval Documentation** — Create `docs/evals.md` explaining how to run evals, interpret thresholds, baseline provenance
5. **Strategic What-If Planning** — Higher-order planning capability (add this Queen, what should I de-scope? Which commits will carry forward? Which RCDOs are underfunded?) — Documented in duet-plan-whatif.md

### From ui-ux-decisions.md (To-Do by Priority)

1. ✅ **Semantic colors** — Add muted semantic tones to monochrome base (highest ROI, lowest effort)
2. ✅ **Typography** — Dual font stack (Inter/Geist Sans for prose, Geist Mono for IDs/codes)
3. 📋 **Progressive disclosure** — Collapse non-critical sections on My Week
4. 📋 **Loading & empty states** — 3-tier skeleton/progress/explicit + illustrations
5. 📋 **AI suggestion tiering** — Adjust default collapsed states, respect dismissal memory
6. 📋 **Charts library** — Migrate from CSS to Tremor or Recharts

### From duet-plan-whatif.md (5-Step Implementation Plan)

1. **WhatIfService + DTOs** — Backend simulation logic using existing rules + read models
2. **LLM narration + API** — `what-if.txt` prompt template, OpenRouter integration, `POST /api/ai/what-if` endpoint
3. **WhatIfPanel + MyWeek integration** — Frontend component with mutation form, results display, integration point
4. **Eval fixtures** — 8 golden test cases, extend `PromptEvalRunner`
5. **Historical replay benchmark** — Synthetic plan test harness, confusion matrices, output to JSON + markdown

---

## Summary

The Weekly Commit Module is a comprehensive weekly planning system with deep strategic alignment (RCDO linkage), conservative AI assistance, and strong operational visibility (manager dashboards, exception queues). The product is well-documented (87KB PRD, 6 supporting docs), the AI infrastructure is real (provider abstraction, Pinecone RAG, async faithfulness scoring), and the engineering foundation is solid (type-safe monorepo, comprehensive CI, infrastructure-as-code).

**Key strengths:**
- Concrete domain model and product principles
- Real AI stack with evaluation infrastructure
- Strong type safety and testing discipline
- Clear scope boundaries (v1 vs post-v1)

**Key gaps:**
- Grafana dashboard for AI quality metrics
- Dormant AI UI components (mount or remove)
- What-If planner (documented, ready for implementation)
- UI/UX decisions pending (colors, typography, progressive disclosure)

**Next 3 weeks (estimated):**
1. Implement UI/UX recommendations (colors, typography, progressive disclosure) — polish product feel
2. Build AI quality Grafana dashboard + evaluate against Manus thresholds
3. Implement What-If planner (backend + frontend + evals) — demonstrate planning intelligence
4. Clarify host integration contract + org directory source (unblock deployment readiness)

---

**Generated by:** Comprehensive Reference Scout  
**Scope:** All documentation + infrastructure files read and summarized  
**Total files processed:** 11 documents + 6 infra configs + 1 CI workflow + 2 Dockerfiles + 1 env file
