# AI Notes (current state)

This file used to contain a rough repo review. It has been refreshed to match the current codebase.

## What is already strong

- The product/domain docs are unusually concrete (`docs/prd.md`, `docs/architecture.md`).
- The backend AI stack is real infrastructure, not placeholder code:
  - provider abstraction
  - Pinecone-backed semantic retrieval
  - prompt version tracking
  - async faithfulness scoring
  - Prometheus metrics + alert rules
- The frontend already ships multiple AI surfaces in production routes.
- CI covers lint, typecheck, backend/frontend tests, build, OpenAPI verification, and E2E.

## Current recommendations

### 1. Decide whether dormant AI components should ship or be removed

The repo now contains a few AI UI components that are implemented but not mounted in the current routes:

- `frontend/src/components/ai/RiskSignalsPanel.tsx`
- `frontend/src/components/ai/ReconcileAssistPanel.tsx`
- `frontend/src/components/ai/EvidenceDrawer.tsx`

That is not necessarily bad, but it should be intentional.

Good next step:
- either mount them in the product flow,
- or mark them as experimental / future surfaces in docs,
- or remove them to reduce maintenance drag.

### 2. Finish the observability story with an AI-quality Grafana dashboard

Metrics and Prometheus alerts exist, but there is still no dedicated AI quality dashboard JSON under `infra/grafana/provisioning/dashboards/json/`.

The most valuable panels would be:

- faithfulness by suggestion type
- acceptance rate by suggestion type
- provider availability
- request volume + token consumption
- latency percentiles for RAG queries

### 3. Expand judge-backed evals beyond draft assist

The eval runner now covers six capability datasets, which is a big improvement. The remaining gap is that judge-based scoring is still concentrated on commit-draft title/criteria quality.

High-value next expansions:

- judge prompts for `rag-query` faithfulness / answer relevance
- judge prompts for `reconcile-assist` summary usefulness
- judge prompts for `rcdo-suggest` rationale quality

### 4. Publish eval outputs as a product artifact

The repo already has:

- `backend/build/eval-results/`
- `eval-thresholds.json`
- `eval-baseline.json`
- `scripts/eval-threshold-check.js`

What would make this easier to understand for new contributors is a short companion doc such as `docs/evals.md` that explains:

- how to run evals locally
- how thresholds are enforced
- how to interpret regressions vs soft judge warnings
- where baseline files come from

### 5. The biggest future differentiator is still a real planning intelligence feature

The current AI is strongest in assistive flows. The most compelling next leap would be a higher-order planning capability such as a strategic what-if planner:

- “If I add this 5-point Queen, what should I de-scope?”
- “Which commits are most likely to carry forward, and why?”
- “Which RCDO branches are underfunded next week?”

If implemented, it should be built as a hybrid system:

1. structured feature extraction
2. exact SQL facts
3. lineage / graph-style traversal
4. predictive scoring
5. LLM explanation only at the final layer

That would feel like a true planning intelligence system rather than a set of isolated AI helpers.
