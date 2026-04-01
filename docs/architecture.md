# Architecture

This document describes the technical architecture of the Weekly Commit Module. For product requirements, see [prd.md](prd.md). For AI evaluation infrastructure, see [ai-eval-roadmap.md](ai-eval-roadmap.md).

---

## System Overview

The Weekly Commit Module is a route-level micro-frontend integrated into the PA host app, backed by a Java 21 modular monolith. AI capabilities are woven into the domain workflow — not a separate layer — with graceful degradation if any AI dependency is unavailable.

```
Browser → React MF Remote → REST/JSON → Spring Boot → PostgreSQL
                                              │
                                              ├──→ Pinecone (vector store)
                                              ├──→ OpenRouter (LLM — GPT-4.1-nano)
                                              └──→ OpenAI (embeddings)
```

### Design Decisions

| Decision | Rationale |
|---|---|
| **Modular monolith** over microservices | Complexity is in workflow and data integrity, not service decomposition. One DB transaction boundary keeps lock/reconcile snapshots consistent. |
| **Derived read models** over live joins | Manager dashboards and RCDO rollups query pre-computed `*_fact` / `*_rollup` tables refreshed on lifecycle events + 5-minute cron. Meets P95 < 1.5s target. |
| **AI behind provider abstraction** | `AiProvider` interface with `OpenRouterAiProvider` (production) and `StubAiProvider` (test/offline). Core workflow never depends on a single vendor. |
| **Immutable snapshots** | Lock and reconcile each capture a snapshot (normalized rows + denormalized JSON). History cannot be rewritten — only appended to via scope change events. |
| **Module Federation remote** | Frontend is a self-contained remote that receives host context (auth, design tokens, feature flags) via `HostProvider`. Runs standalone for dev with mock host bridge. |

---

## Backend Modules

The backend is organized as a package-per-bounded-context modular monolith:

```
com.weeklycommit
├── plan/           Weekly plan + commit CRUD, validation
├── lock/           Lock service, auto-lock job, baseline snapshots
├── reconcile/      Reconciliation, scope changes, outcome tracking
├── carryforward/   Explicit carry-forward with lineage
├── rcdo/           RCDO hierarchy management, linkage validation
├── team/           Team weekly view, manager review, authorization
├── ticket/         Native ticket/work item model, status workflow
├── notification/   Rules-based notifications, email digest
├── audit/          Audit logging for all lifecycle transitions
├── config/         Org + team cadence configuration
├── report/         Reporting service, read model refresh
├── ai/             AI pipeline (see below)
│   ├── controller/ Single REST controller for all AI endpoints
│   ├── provider/   AiProvider abstraction, OpenRouter, Stub
│   ├── service/    CommitDraftAssist, CommitLint, RcdoSuggest,
│   │               RiskDetection, ReconcileAssist, ManagerSummary
│   ├── rag/        Pinecone, Embedding, ChunkBuilder, SemanticQuery,
│   │               SemanticIndex, InsightGeneration
│   ├── evidence/   Structured evidence bundles for explainability
│   ├── eval/       FaithfulnessEvaluator (production scoring)
│   ├── metrics/    Prometheus AI quality gauges
│   └── dto/        Request/response records for all AI endpoints
└── domain/
    ├── entity/     32 JPA entities
    ├── enums/      13 enum types
    └── repository/ 32 Spring Data repositories
```

### API Surface

13 REST controllers expose 60+ endpoints:

| Controller | Prefix | Purpose |
|---|---|---|
| `PlanController` | `/api/plans` | Plan + commit CRUD, lock, reconcile |
| `ReconcileController` | `/api/plans/{id}` | Scope changes, outcomes, reconcile submit |
| `CarryForwardController` | `/api/plans/...` | Carry-forward with lineage |
| `TeamController` | `/api/teams` | Team week view, exceptions, comments, capacity |
| `TicketController` | `/api/tickets` | Ticket CRUD, status transitions, link to commits |
| `RcdoController` | `/api/rcdo` | RCDO hierarchy CRUD, move, archive |
| `AiController` | `/api/ai` | All AI endpoints (draft assist, lint, RAG, etc.) |
| `ReportController` | `/api/reports` | 8 report types from derived read models |
| `NotificationController` | `/api/notifications` | Notification list, mark read |
| `AuditLogController` | `/api/audit-log` | Audit trail queries |
| `ConfigController` | `/api/config` | Org + team cadence configuration |
| `PlanHistoryController` | `/api/users` | Historical plan browsing |
| `HealthController` | `/health` | Health check |

Full OpenAPI spec available at `/api-docs` (JSON) and `/swagger-ui.html` (interactive).

---

## Data Model

### Transactional Tables (source of truth)

```
organization ──< team ──< team_membership >── user_account
                  │
                  └──< weekly_plan ──< weekly_commit ──< carry_forward_link
                              │              │
                              │              ├── work_item (optional link)
                              │              └── rcdo_node (required at lock)
                              │
                              ├──< lock_snapshot_header ──< lock_snapshot_commit
                              ├──< reconcile_snapshot_header ──< reconcile_snapshot_commit
                              ├──< scope_change_event
                              └──< manager_comment

rcdo_node (self-referencing tree) ──< rcdo_change_log
work_item ──< work_item_status_history
              work_item_comment

notification ──< notification_delivery
ai_suggestion ──< ai_feedback
audit_log
capacity_override
manager_review_exception
```

### Derived Read Models (refreshed, not source of truth)

| Table | Grain | Purpose |
|---|---|---|
| `user_week_fact` | user × week | Points planned/achieved, compliance, carry-forward count, chess counts |
| `team_week_rollup` | team × week | Member count, lock/reconcile counts, chess distribution (JSONB) |
| `rcdo_week_rollup` | RCDO node × week | Points planned/achieved, team contribution breakdown (JSONB) |
| `compliance_fact` | user × week | Lock on-time/late/auto, reconcile on-time/late/missed |
| `carry_forward_fact` | commit | Source week, current week, streak length, RCDO, chess piece |

Read models are refreshed by `ReadModelRefreshService`:
- **Event-driven**: after lock, reconcile, scope change, carry-forward
- **Scheduled**: every 5 minutes for the current week (catch-up sweep)
- **Idempotent**: re-running with the same input produces the same result

---

## Weekly Lifecycle State Machine

```
                    ┌─────────────────────────────────────────────┐
                    │                                             │
  Draft opens       ▼                                             │
  ┌─────────┐   ┌──────┐   Lock    ┌────────┐   Reconcile   ┌────────────┐
  │ (start) │──>│ DRAFT│──────────>│ LOCKED │──────────────>│RECONCILING │
  └─────────┘   └──────┘           └────────┘   opens        └────────────┘
                    │                  │                           │
                    │ auto-lock        │ scope changes             │ submit
                    │ at cutoff        │ (append-only events)      ▼
                    └──────────────────┘                    ┌────────────┐
                                                           │ RECONCILED │ (terminal)
                                                           └────────────┘
                                                                │
                                                                │ carry-forward
                                                                ▼
                                                           New commit in
                                                           next week's plan
                                                           (with provenance)
```

**Key invariants:**
- Lock creates an **immutable baseline snapshot** — the contract for the week
- Post-lock edits create **scope change events** with required reason, not silent mutations
- Reconcile creates an **immutable actual snapshot** with outcomes
- Carry-forward creates a **new commit object** with `CarryForwardLink` — never mutates the original
- Auto-lock at cutoff marks the plan as `system_locked_with_errors` (non-compliant)

---

## AI Pipeline

### Provider Abstraction

```java
public interface AiProvider {
    AiSuggestionResult generateSuggestion(AiContext context);
    boolean isAvailable();
}
```

Two implementations:
- `OpenRouterAiProvider` — production: calls OpenRouter with per-type prompt templates, rate limiting, token tracking
- `StubAiProvider` — test/offline: returns realistic canned responses for each suggestion type

### Suggestion Types and Services

| Type | Service | Prompt | Trigger |
|---|---|---|---|
| `COMMIT_DRAFT_ASSIST` | `CommitDraftAssistService` | `commit-draft-assist.txt` | User clicks "AI Suggest" |
| `COMMIT_LINT` | `CommitLintService` | `commit-lint.txt` | Auto on save + pre-lock |
| `RCDO_SUGGEST` | `RcdoSuggestService` | `rcdo-suggest.txt` | During commit editing |
| `RISK_SIGNAL` | `RiskDetectionService` | `risk-signal.txt` | On lock + daily 07:00 UTC |
| `RECONCILE_ASSIST` | `ReconcileAssistService` | `reconcile-assist.txt` | Auto-prefill when the Reconcile page loads in `RECONCILING` |
| `TEAM_SUMMARY` | `ManagerAiSummaryService` | `team-summary.txt` | On-demand in Team Week |
| `RAG_INTENT` | `SemanticQueryService` | `rag-intent.txt` | First step of any RAG query |
| `RAG_QUERY` | `SemanticQueryService` | `rag-query.txt` | Second step — answer synthesis |
| `TEAM_INSIGHT` | `InsightGenerationService` | `team-insight.txt` | Daily 08:00 UTC + on-demand |
| `PERSONAL_INSIGHT` | `InsightGenerationService` | `personal-insight.txt` | On lock + daily sweep |

`AiController` also exposes structured evidence endpoints at `/api/plans/{planId}/evidence` and `/api/commits/{commitId}/evidence`, which assemble SQL facts, carry-forward lineage, semantic matches, and risk features for explainability.

### Risk Detection: Hybrid Rules + LLM

`RiskDetectionService` is the best example of the AI design philosophy — deterministic rules handle what they can, the LLM handles what they can't:

**Rules engine** (always runs, no AI dependency):
- `OVERCOMMIT` — total points > capacity budget
- `UNDERCOMMIT` — total points < 60% of budget
- `REPEATED_CARRY_FORWARD` — any commit with streak ≥ 2
- `BLOCKED_CRITICAL` — King/Queen linked to blocked ticket > 48 hours
- `SCOPE_VOLATILITY` — > 3 post-lock scope change events

**LLM augmentation** (runs after rules, degrades gracefully):
- The prompt explicitly lists the 5 rule-based signals and says "do NOT duplicate these"
- LLM looks for: hidden dependencies, unrealistic estimates, concentration risk, timing conflicts
- AI-detected signals are stored with separate model version for tracking

### RAG Pipeline Detail

**Indexing (write path):**

```
Entity event (commit create, lock, reconcile, etc.)
  → SemanticIndexService.indexEntity()
    → ChunkBuilder.build*Chunk() with EnrichmentContext
      (RCDO path, team name, owner, carry-forward lineage,
       linked ticket summary, cross-team RCDO overlap)
    → EmbeddingService.embed() (OpenAI text-embedding-3-small)
    → PineconeClient.upsert() (namespaced by org ID)
```

**Querying (read path):**

```
Question
  → Intent classification (LLM: entity types, time range, user scope)
  → Embed question
  → Pinecone query (top-40, filtered by team/user/date/entity type)
  → Context assembly
  → RAG answer generation (LLM)
  → Store audit record with source citations
  → Return answer + sources + confidence
```

**Chunk enrichment** is critical — raw entity data produces sparse embeddings. The `ChunkBuilder.EnrichmentContext` adds:
- Full RCDO ancestry path (e.g., "Rally Cry: Win Enterprise > DO: Improve Uptime > Outcome: 99.9% SLA")
- Carry-forward lineage narrative
- Linked ticket summary with status
- Cross-team RCDO overlap notes
- Owner and team context

### Evaluation Infrastructure

**Offline evaluation** (`PromptEvalRunner`, `@Tag("eval")`):
- Golden test datasets per capability in `backend/src/test/resources/eval/`
- Calls real LLM with actual prompt templates
- Automated schema validation + behavioral checks across 6 evaluated capabilities
- LLM-as-judge scoring currently for commit-draft title quality + success-criteria quality
- JSON report output to `build/eval-results/`

**Production evaluation** (`FaithfulnessEvaluator`):
- Async sampling after suggestion storage
- 100% of high-stakes types (RISK_SIGNAL, TEAM_INSIGHT, PERSONAL_INSIGHT, RAG_QUERY)
- 10% of lower-stakes types (`COMMIT_DRAFT_ASSIST`, `COMMIT_LINT`)
- RAGAS-style claim decomposition: faithfulness = supported claims / total claims
- Scores written back to `ai_suggestion` table

**Observability** (`AiQualityMetrics`):
- `weekly_commit_ai_faithfulness_score` — rolling 7-day average by suggestion type
- `weekly_commit_ai_acceptance_rate` — accepted / (accepted + dismissed) by type
- `weekly_commit_ai_provider_available` — 1/0 gauge
- `weekly_commit_ai_tokens_total` — cumulative token consumption
- Prometheus alerts at Manus-recommended thresholds (faithfulness < 0.88 → warning)

### Prompt Version Tracking and A/B Analysis

Experiment variant assignment is **deterministic per user** — `ExperimentService` hashes the experiment name and user ID to produce a stable bucket, ensuring the same user always receives the same variant for a given experiment. This avoids inconsistent UX from per-request randomisation and produces valid between-subjects A/B data. Environment-variable overrides (`AB_FORCE_<NAME>=control|treatment`) are available for testing and canary deploys.

Every `ai_suggestion` row stores `prompt_version` (e.g., `commit-draft-assist-v1`). Combined with `ai_feedback` (accept/dismiss), this enables:

```sql
SELECT prompt_version, suggestion_type,
       COUNT(*) as total,
       COUNT(CASE WHEN f.accepted THEN 1 END) as accepted,
       ROUND(COUNT(CASE WHEN f.accepted THEN 1 END)::decimal
             / NULLIF(COUNT(f.id), 0), 3) as acceptance_rate
FROM ai_suggestion s
LEFT JOIN ai_feedback f ON f.suggestion_id = s.id
WHERE prompt_version IS NOT NULL
GROUP BY prompt_version, suggestion_type;
```

---

## Frontend Architecture

### Micro-Frontend Integration

The frontend is a Vite-built Module Federation remote:

```typescript
// Host app loads the remote
const WeeklyCommitRoutes = React.lazy(() => import("weeklyCommit/Routes"));
<WeeklyCommitRoutes bridge={hostBridge} />
```

The `HostProvider` injects authenticated user identity, team context, feature flags, and design tokens. For local dev, `MockHostProvider` provides a complete mock bridge with a user-switcher banner.

### Routes

| Route | Page | Primary audience |
|---|---|---|
| `/weekly/my-week` | My Week — plan creation, commit list, AI lint, lock | IC |
| `/weekly/reconcile` | Reconcile — baseline vs actual, outcomes, carry-forward | IC |
| `/weekly/team` | Team Week — manager dashboard with 6 sections | Manager |
| `/weekly/tickets` | Tickets — native work item list + detail | All |
| `/weekly/rcdos` | RCDOs — hierarchy management, archive, move | Manager / Admin |
| `/weekly/reports` | Reports — compliance, planned vs achieved, RCDO coverage | All |
| `/weekly/admin` | Admin — org config, cadence settings | Admin |

### AI Components

| Component | Current status | Purpose |
|---|---|---|
| `CommitDraftAssistButton` | Mounted in commit form | "✨ AI Suggest" with inline diff-style suggestions |
| `AiLintPanel` | Mounted on My Week | Commit quality lint with hard vs soft distinction |
| `RcdoSuggestionInline` | Mounted in commit form | RCDO link suggestion with rationale |
| `InsightPanel` | Mounted on My Week / Team Week | Proactive personal + team insights |
| `ManagerAiSummaryCard` | Mounted on Team Week | AI-generated team summary |
| `SemanticSearchInput` | Mounted on Team Week | Natural-language RAG query input |
| `QueryAnswerCard` | Mounted via `SemanticSearchInput` | RAG answer with source citations |
| `AiFeedbackButtons` | Mounted across AI surfaces | Thumbs up/down on every AI output |
| `AiCommitComposer` | Mounted on My Week | AI-guided commit creation flow |
| `ProactiveRiskBanner` | Mounted on My Week | Banner for critical risk signals |
| `TeamRiskSummaryBanner` | Mounted on Team Week | Team-level risk overview |
| `AiSuggestedBadge` | Mounted on Reconcile | Indicates AI-prefilled outcomes |
| `WhatIfPanel` | Mounted on My Week | Interactive what-if planner for hypothetical commit mutations |
| `CalibrationCard` | Mounted on My Week | Displays user's rolling calibration profile and confidence tier |
| `PlanRecommendationCard` | Mounted on My Week | Personalized plan adjustment recommendations |
| `ConfidenceBadge` | Sub-component (CalibrationCard, PlanRecommendationCard, QueryAnswerCard) | Renders calibration/evidence confidence tier badges |
| `AnswerRenderer` | Sub-component (QueryAnswerCard) | Renders LLM answer text with lightweight markdown support |
| `RiskSignalsPanel` | Mounted on Team Week (By Person expanded rows) | Detailed risk signal display with evidence toggle |
| `ReconcileAssistPanel` | Reusable component, not currently mounted | Manual AI-assisted reconciliation surface |
| `EvidenceDrawer` | Sub-component (InsightPanel, RiskSignalsPanel) | Shows SQL facts, lineage, semantic matches, and risk features behind AI output |

The current Reconcile route uses `useAutoReconcileAssist()` to prefill outcomes and carry-forward recommendations automatically when a plan enters `RECONCILING`, rather than rendering `ReconcileAssistPanel` directly. In on-demand AI mode, a "Request AI Suggestions" button triggers the same prefill flow explicitly.

---

## Observability

### Prometheus Metrics
- Standard Spring Boot Actuator metrics (HTTP, JVM, HikariCP)
- Custom AI quality gauges (faithfulness, acceptance rate, provider availability, tokens)
- Lifecycle job success/failure counters (auto-lock, auto-reconcile)

### Alert Rules (`infra/prometheus/alerts.yml`)
- `HighErrorRate` — API 5xx > 5% for 2 minutes
- `SlowEndpoint` — p95 > 2s for 5 minutes
- `AutoLockJobFailure` / `AutoReconcileJobFailure` — lifecycle job errors
- `DatabaseConnectionPoolExhausted` — HikariCP > 90%
- `AiFaithfulnessLow` / `AiFaithfulnessCritical` — AI quality degradation
- `AiAcceptanceRateLow` — users dismissing > 80% of suggestions
- `AiProviderDown` — OpenRouter unavailable > 5 minutes

### Structured Logging
- JSON format via Logstash encoder (production profile)
- Request tracing across frontend and backend
- AI suggestion lifecycle logging (request → generate → store → eval → feedback)

---

## Testing Strategy

| Layer | Framework | Count | What it covers |
|---|---|---|---|
| Backend unit/integration | JUnit 5 + Mockito | 63 test files | Service logic, controller endpoints, AI services, domain validation |
| Frontend unit | Vitest + Testing Library | 49 test files | Components, hooks, API clients, user flows |
| E2E | Playwright | 4 spec files | Golden path, AI flows, manager flow, navigation |
| AI evaluation | Custom eval harness | Golden datasets | Prompt quality, schema validation, LLM-as-judge scoring |

### CI Pipeline

Both **GitHub Actions** and **GitLab CI** are configured with identical stages:

```
lint → test-frontend + test-backend → build → e2e → eval (manual, LLM)
                                                       ↑
                                                 api-contract (PR only)
```

The `eval:llm-judge` stage runs against real LLM APIs and is intentionally manual + `allow_failure` since it depends on external API availability.

---

## Deployment

### Docker Compose

```bash
docker compose up --build                           # Core: Postgres, Redis, Backend, Frontend
docker compose --profile observability up --build   # + Prometheus + Grafana
```

### Infrastructure

- `infra/prometheus/` — Prometheus config + alert rules
- `infra/grafana/provisioning/` — Datasource + dashboard provisioning
- `infra/terraform/main.tf` — Infrastructure-as-code skeleton

### Environment Variables

| Variable | Required | Purpose |
|---|---|---|
| `DB_USER` / `DB_PASSWORD` | Yes | PostgreSQL credentials |
| `OPENROUTER_API_KEY` | For AI | OpenRouter API key (production: GPT-4.1-nano) |
| `PINECONE_API_KEY` | For RAG | Pinecone vector database |
| `OPENAI_API_KEY` | For embeddings | OpenAI embedding model |
| `APP_SCHEDULING_ENABLED` | No | Enable/disable scheduled jobs (default: true) |
| `FEATURE_AI_ASSISTANCE` | No | Feature flag for AI (default: true) |
