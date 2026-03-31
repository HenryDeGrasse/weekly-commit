# Performance Benchmarks

Measured against the three PRD performance targets (Section 24: Non-Functional Requirements). All benchmarks run against the local dev stack with the V11 demo seed loaded (6 users, 2 teams, 1000+ weekly plans, 73 RCDO nodes, 12 weeks of historical data).

---

## PRD Targets vs Measured Results

| PRD Target | Specification | Measured P95 | Headroom | Status |
|---|---|---|---|---|
| **Page load** (My Week, Team Week) | < 2,500ms | **161ms / 177ms** | 93% under budget | ✅ Pass |
| **Dashboard query** (derived read models) | < 1,500ms | **11ms** (worst case) | 99% under budget | ✅ Pass |
| **Commit CRUD** (create/edit/save) | < 500ms server time | **3ms** (worst case) | 99% under budget | ✅ Pass |

---

## Backend API Benchmarks

100 sequential requests per endpoint via `curl` against `localhost:8080`. Measures server response time only (no browser rendering). Demo seed loaded with ~1,000 weekly plans and 30+ tables populated.

### Page-Level Queries (PRD target: P95 < 2,500ms)

| Endpoint | P50 | P95 | P99 | Max |
|---|---|---|---|---|
| `GET /api/plans` (list user plans) | 7.4ms | 8.0ms | 9.0ms | 9.0ms |
| `GET /api/plans/{id}` (plan + commits) | 1.0ms | 1.3ms | 1.6ms | 1.6ms |
| `GET /api/plans/{id}/lock-snapshot` | 0.9ms | 1.2ms | 1.3ms | 1.3ms |
| `GET /api/rcdo/nodes` (73-node hierarchy) | 1.0ms | 1.4ms | 1.9ms | 1.9ms |
| `GET /api/tickets` (filtered list) | 1.4ms | 1.7ms | 2.3ms | 2.3ms |

### Manager Dashboard Queries (PRD target: P95 < 1,500ms)

| Endpoint | P50 | P95 | P99 | Max |
|---|---|---|---|---|
| `GET /api/teams/{id}/week/{date}` (team week view) | 3.9ms | 4.8ms | 5.4ms | 5.4ms |
| `GET /api/teams/{id}/week/{date}/exceptions` | 3.6ms | 4.4ms | 5.6ms | 5.6ms |
| `GET /api/reports/planned-vs-achieved` | 1.0ms | 1.3ms | 1.4ms | 1.4ms |
| `GET /api/reports/compliance` | 1.1ms | 1.4ms | 1.7ms | 1.7ms |
| `GET /api/reports/carry-forward` | 1.5ms | 1.8ms | 2.5ms | 2.5ms |
| `GET /api/reports/chess-distribution` | 0.9ms | 1.2ms | 1.3ms | 1.3ms |
| `GET /api/reports/scope-changes` | 1.4ms | 1.8ms | 2.3ms | 2.3ms |
| `GET /api/reports/exception-aging` | 1.1ms | 1.5ms | 1.6ms | 1.6ms |
| `GET /api/teams/{id}/history` (team trends) | 9.9ms | 10.6ms | 13.1ms | 13.1ms |

**Slowest endpoint:** Team trends history at 11ms P95 — still 99.3% under the 1,500ms target.

### Commit CRUD Operations (PRD target: P95 < 500ms)

Each iteration runs a full create → update → delete cycle and cleans up after itself (no leftover data).

| Endpoint | P50 | P95 | P99 | Max |
|---|---|---|---|---|
| `POST /api/plans/{id}/commits` (create) | 2.7ms | 3.3ms | 5.0ms | 5.0ms |
| `PUT /api/plans/{id}/commits/{id}` (update) | 1.3ms | 1.8ms | 3.4ms | 3.4ms |
| `DELETE /api/plans/{id}/commits/{id}` (delete) | 3.0ms | 4.2ms | 4.2ms | 4.2ms |

---

## Frontend Page Load Benchmarks

15 iterations per page in Chromium via Playwright. Each iteration uses a fresh browser context (no cache). Measures Navigation Timing API (`loadEventEnd`) and Largest Contentful Paint (LCP) via `PerformanceObserver`.

### Page Load Times (PRD target: P95 < 2,500ms for My Week and Team Week)

| Page | Load P50 | Load P95 | Load P99 | LCP P95 | TTFB P95 | Status |
|---|---|---|---|---|---|---|
| **My Week** | 138ms | **161ms** | 161ms | 132ms | 2ms | ✅ Pass |
| **Team Week** | 134ms | **177ms** | 177ms | 80ms | 2ms | ✅ Pass |
| Reconcile | 137ms | 157ms | 157ms | 108ms | 2ms | ✅ Pass |
| Tickets | 137ms | 160ms | 160ms | 112ms | 2ms | ✅ Pass |
| RCDOs | 135ms | 192ms | 192ms | 96ms | 2ms | ✅ Pass |
| Reports | 136ms | 153ms | 153ms | 116ms | 2ms | ✅ Pass |

**Slowest page:** RCDOs at 192ms P95 — still 92% under the 2,500ms target.

---

## Why the Numbers Are This Low

The architecture was designed with these targets in mind from the start:

1. **Derived read models** — Manager dashboards and reports query pre-computed `*_fact` and `*_rollup` tables, not live joins across transactional tables. Refreshed on lifecycle events + 5-minute cron.

2. **Single DB transaction boundary** — Modular monolith means no inter-service latency for plan queries that touch commits, RCDO links, and snapshots.

3. **Vite + React 18 + code splitting** — The frontend bundle is served by Vite with route-level code splitting. No heavy framework overhead on initial paint.

4. **Efficient JPA queries** — `@EntityGraph` annotations on critical paths (plan + commits, team week view) eliminate N+1 query patterns.

---

## Caveats

- **Local measurement** — These benchmarks run against `localhost` with no network latency. The PRD specifies "corporate network" targets. Real-world measurements would add 10-50ms of network RTT depending on office/VPN topology, which still leaves substantial headroom.

- **Demo seed scale** — The demo seed has ~1,000 plans across 6 users. At production scale (10,000 users, 250K+ commits/year per PRD Section 24), the derived read models and indexes would be the critical factor. The current index strategy is designed for this, but production load testing would validate it.

- **No concurrent load** — These are sequential single-user benchmarks. The PRD specifies 1,000 concurrent users during Monday peak. Connection pooling (HikariCP defaults) and the read-model strategy should handle this, but concurrent load testing (k6/Gatling) would be a valuable follow-up.

- **Cold start excluded** — JVM warm-up and Vite's first-request compilation are excluded. First-ever page load after `make dev` may be 2-3x slower due to JIT compilation.

---

## How to Reproduce

### Backend API benchmark
```bash
# Requires: backend running on :8080 with demo seed
./scripts/perf-benchmark-api.sh [ITERATIONS]   # default: 100
# Results: scripts/perf-results-api.json
```

### Frontend page load benchmark
```bash
# Requires: full stack running (backend :8080 + frontend :5173)
PERF_ITERATIONS=20 npx playwright test --config=e2e/playwright.config.ts e2e/perf-benchmark.spec.ts
# Results: scripts/perf-results-frontend.json
```

### Quick check (both)
```bash
make perf   # runs both benchmarks
```

---

## Machine-Readable Results

- `scripts/perf-results-api.json` — per-endpoint P50/P90/P95/P99/max with timestamp
- `scripts/perf-results-frontend.json` — per-page TTFB/DCL/load/LCP with timestamp

Both files are gitignored (results vary by machine). Regenerate with the commands above.
