# Weekly Commit Module

A production-grade weekly planning intelligence system that replaces 15Five with structured, strategy-linked commitments. Every weekly plan is a contract — lockable, reconcilable, and auditable — with AI assistance that shows its evidence and never makes hidden edits.

**What makes this different:** the AI layer is not a chatbot bolted onto a CRUD app. It's an integrated intelligence pipeline that drafts, lints, links, predicts, and explains — grounded in structured evidence from SQL facts, carry-forward lineage, semantic retrieval, and historical features. Every suggestion is explainable, auditable, and dismissible.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         React Frontend                               │
│  Module Federation remote · TypeScript strict · Tailwind CSS         │
│                                                                      │
│  ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │ My Week  │ │ Reconcile │ │Team Week │ │ Tickets  │ │  RCDOs   │ │
│  └──────────┘ └───────────┘ └──────────┘ └──────────┘ └──────────┘ │
│  ┌──────────────────────────────────────────────────────────────────┐│
│  │ AI Components: DraftAssist · Lint · RiskSignals · Insights ·    ││
│  │ ReconcileAssist · SemanticSearch · QueryAnswer · Feedback        ││
│  └──────────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    Java 21 / Spring Boot 3.4                         │
│                    Modular Monolith Backend                          │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │ Domain Modules                                                  │ │
│  │ Plan · Commit · Lock · Reconcile · CarryForward · Ticket ·     │ │
│  │ RCDO · Team · Notification · Audit · Config · Reports          │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │ AI Pipeline                                                     │ │
│  │                                                                 │ │
│  │  10 Prompt Templates → OpenRouter/Claude → 7 AI Services       │ │
│  │                                                                 │ │
│  │  CommitDraftAssist · CommitLint · RcdoSuggest · RiskDetection  │ │
│  │  ReconcileAssist · ManagerSummary · InsightGeneration          │ │
│  │                                                                 │ │
│  │  RAG: ChunkBuilder → Embeddings → Pinecone → SemanticQuery    │ │
│  │  Eval: FaithfulnessEvaluator · PromptEvalRunner               │ │
│  │  Metrics: Prometheus gauges · Acceptance rates · Faithfulness  │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │ Read Models (derived, refreshed on lifecycle events + cron)     │ │
│  │ UserWeekFact · TeamWeekRollup · RcdoWeekRollup ·               │ │
│  │ ComplianceFact · CarryForwardFact                               │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
          │              │                │
          ▼              ▼                ▼
   ┌────────────┐ ┌────────────┐  ┌─────────────┐
   │ PostgreSQL │ │  Pinecone  │  │ OpenRouter   │
   │    16      │ │  Vector DB │  │ Claude LLM   │
   │            │ │            │  │ + OpenAI     │
   │ 13 Flyway  │ │ Enriched   │  │   Embeddings │
   │ migrations │ │ chunks w/  │  │              │
   │ 30+ tables │ │ lineage &  │  │ 10 prompt    │
   │ 5 read     │ │ RCDO paths │  │ templates    │
   │ models     │ │            │  │              │
   └────────────┘ └────────────┘  └─────────────┘
```

→ See [docs/architecture.md](docs/architecture.md) for full technical detail.

---

## Core Concepts

| Concept | What it is |
|---|---|
| **Weekly Plan** | One per user per week. Draft → Locked → Reconciling → Reconciled. |
| **Commit** | A weekly work promise with title, chess piece, RCDO link, estimate points, success criteria. |
| **RCDO Hierarchy** | Rally Cry → Defining Objective → Outcome. Every commit links to strategy. |
| **Chess Layer** | King (must not fail) · Queen (highest leverage) · Rook · Bishop · Knight · Pawn. Max 1 King, 2 Queens per week. |
| **Lock Snapshot** | Immutable baseline captured at lock. No silent edits — all post-lock changes are structured scope change events. |
| **Reconciliation** | End-of-week comparison: planned vs actual, with carry-forward decisions. |
| **Carry-Forward** | Explicit, with lineage. Creates a new commit with provenance — never silently mutates history. |

---

## AI Capabilities

The AI layer follows three principles from the PRD: **assistive** (never authoritarian), **explainable** (rationale on every suggestion), and **auditable** (every interaction stored with model version and user feedback).

| Capability | What it does | Trigger |
|---|---|---|
| **Commit Draft Assist** | Improves titles, suggests success criteria, estimates points | On-demand button in commit form |
| **Commit Quality Lint** | Flags vague titles, missing criteria, estimate inconsistencies | Auto on save, pre-lock |
| **RCDO Link Suggest** | Recommends primary RCDO with rationale | During commit editing |
| **Risk Detection** | Rules engine (5 types) + LLM augmentation for risks rules miss | On lock + daily scheduled job |
| **Reconcile Assist** | Suggests outcomes, draft summary, carry-forward recommendations | On-demand during reconciliation |
| **Manager Summary** | AI-generated team week summary citing underlying data | On-demand in Team Week |
| **Semantic Search (RAG)** | Natural-language queries over planning history | Search input across all pages |
| **Personal / Team Insights** | Proactive pattern detection from historical context | Generated on lock + daily sweep |

### RAG Pipeline

```
Write Path:
  Commit/Plan/Ticket/ScopeChange events
    → ChunkBuilder (enriched with RCDO path, team, lineage, ticket context)
    → OpenAI text-embedding-3-small
    → Pinecone (namespaced by org, filtered by team/user/week)

Query Path:
  Question → Intent Classification (entity types, time range, user scope)
    → Embedding → Pinecone retrieval (top-40)
    → Context assembly → Claude synthesis → Audited response with sources
```

### Evaluation Infrastructure

- **Golden test datasets** for AI capabilities with automated + LLM-as-judge scoring
- **FaithfulnessEvaluator** — async production sampling using RAGAS-style claim decomposition
- **Prometheus metrics** — rolling 7-day faithfulness, acceptance rates, provider availability
- **Alerting** — thresholds from Manus RAG Evaluation Research (faithfulness < 0.88 → warning, < 0.85 → critical)
- **Prompt version tracking** — every suggestion stores the prompt template version for A/B analysis

---

## Quick Start

### Prerequisites
- Java 21 (Homebrew: `brew install openjdk@21`)
- Node.js 20+ (`brew install node@20`)
- PostgreSQL 16 (`brew install postgresql@16`)

### Local Development
```bash
make dev          # install deps, create DB, start backend + frontend
```
- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- OpenAPI docs: http://localhost:8080/swagger-ui.html

### Docker
```bash
docker compose up --build               # core services
docker compose --profile observability up --build  # + Prometheus + Grafana
```

### Other Commands
```bash
make check        # lint + typecheck + tests
make reseed       # wipe runtime data, restore demo seed
make reset        # full clean slate (drop DB + rebuild)
make stop         # kill running services
```

### Run AI Eval Suite
```bash
OPENROUTER_API_KEY=sk-... ./backend/gradlew -p backend evalTest
# Results: backend/build/eval-results/
```

---

## Demo Walkthrough

The app ships with a rich demo seed (V11) — 6 users, 2 teams, 12 weeks of historical data including carry-forward chains, scope changes, manager exceptions, and RCDO hierarchy.

### Switch between personas
Use the dev user switcher (top banner) to experience different roles:

| Persona | Role | What to see |
|---|---|---|
| **Dev User** | Manager | Team Week dashboard, exception queue, AI summary, manager comments |
| **Manager One** | Manager | Full team rollup, RCDO coverage, compliance overview |
| **Alice Chen** | IC | My Week, commit creation with AI assist, lock/reconcile flow |
| **Bob Martinez** | IC | Active carry-forward chains, reconciliation with AI recommendations |
| **Carol Nguyen** | IC | Draft planning, RCDO linking, capacity meter |
| **Dan Okafor** | IC | Historical carry-forward lineage, scope change timeline |

### Key flows to demo
1. **Create & lock a plan** — add commits, get AI lint, lock to create baseline snapshot
2. **Post-lock scope change** — edit a locked commit, see structured change event with reason
3. **Reconcile** — compare planned vs actual, use AI reconcile assist, carry forward incomplete work
4. **Team Week** — switch to Manager, see team rollup, exception queue, RCDO coverage
5. **Semantic search** — ask "What did the team commit to last week?" or "Which RCDOs are under-invested?"
6. **Risk signals** — lock an over-budget plan, see rules-based + AI-augmented risk detection

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | React 18 · TypeScript strict · Tailwind CSS 4 · Vite · Module Federation |
| **Backend** | Java 21 · Spring Boot 3.4 · Spring Data JPA · Flyway · OpenAPI/Springdoc |
| **Database** | PostgreSQL 16 · 13 Flyway migrations · 30+ tables · 5 derived read models |
| **AI / LLM** | OpenRouter (Claude) · OpenAI embeddings · 10 prompt templates |
| **Vector Store** | Pinecone · Enriched chunks with RCDO paths, lineage, ticket context |
| **Observability** | Prometheus · Grafana · Structured JSON logging · Micrometer |
| **Testing** | JUnit 5 · Vitest · Playwright · LLM-as-judge eval harness |
| **CI/CD** | GitHub Actions · GitLab CI · Docker Compose |

---

## Project Structure

```
├── backend/                    Java 21 Spring Boot modular monolith
│   ├── src/main/java/          241 source files across 12 domain modules
│   ├── src/main/resources/
│   │   ├── db/migration/       13 Flyway migrations (V1–V13)
│   │   └── prompts/            10 AI prompt templates
│   └── src/test/
│       ├── java/               54 test files
│       └── resources/eval/     Golden eval datasets + judge prompts
├── frontend/                   React 18 TypeScript micro-frontend
│   └── src/
│       ├── components/         UI components (ai/, myweek/, teamweek/, etc.)
│       ├── routes/             7 page-level routes
│       └── api/                API clients + React hooks
├── packages/shared/            Shared TypeScript types
├── e2e/                        4 Playwright E2E test specs
├── infra/                      Prometheus alerts, Grafana provisioning, Terraform
├── docs/                       PRD, architecture, AI eval roadmap
└── docker-compose.yml          Full stack: Postgres, Redis, Backend, Frontend, Prometheus, Grafana
```

---

## Documentation

- [Product Requirements (PRD)](docs/prd.md) — full domain spec, 30 sections
- [Architecture](docs/architecture.md) — system design, AI pipeline, data model
- [AI Eval Roadmap](docs/ai-eval-roadmap.md) — evaluation infrastructure plan and status
- [Assignment](docs/assignment.md) — original project brief

---

## License

Internal project — not for public distribution.
