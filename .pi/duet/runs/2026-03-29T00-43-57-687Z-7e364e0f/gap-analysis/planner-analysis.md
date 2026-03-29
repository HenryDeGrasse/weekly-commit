Now let me provide my analysis summary and verdict:

---

## Plan Review Analysis

I explored the entire codebase thoroughly, examining:
- All AI service patterns, DTOs, controller, provider classes
- Frontend API client factory patterns (`createXxxApi`), hooks, component patterns
- Test patterns (Mockito unit tests, `PromptEvalRunner` eval framework)
- Build configuration, check commands, seed data structure
- MyWeek.tsx layout and integration points

### Issues Found and Addressed

**1. Missing `StubAiProvider` update (BLOCKING)**
The original plan updates `OpenRouterAiProvider` and `AiContext` for the WHAT_IF type but omits updating `StubAiProvider.java`. Tests run with `ai.provider: stub`, so the stub would fall through to the `default -> "{}";` case. Added explicit deliverable in Step 2.

**2. Frontend API pattern mismatch (BLOCKING)**
The plan says to use "`apiFetch` helper" which doesn't exist. The actual pattern is `createXxxApi(client, actorUserId)` factory functions. Also the plan describes a standalone function `whatIfSimulate` instead of the factory pattern. Fixed to use `createWhatIfApi` factory matching `aiApi.ts`, `ragApi.ts`, etc.

**3. Missing hook for frontend integration (BLOCKING)**
The plan creates `whatIfApi.ts` but never adds a hook to `aiHooks.ts`. All other API modules have corresponding `useXxxApi()` hooks. Added `useWhatIfApi()` hook to Step 3.

**4. Historical Replay Benchmark H2 incompatibility (BLOCKING)**
The plan says to load V11 seed data by "querying the actual tables" but tests use H2 with `ddl-auto: create-drop` and Flyway disabled. The V11 SQL uses PostgreSQL-specific syntax (`ON CONFLICT DO NOTHING`, `interval`). Added guidance to use programmatic JPA entity setup instead of raw SQL.

**5. MyWeek.tsx modification contradicts constraints (CLARIFIED)**
The constraints say "Do NOT modify existing frontend routes or page components" but Step 3 needs to add the WhatIfPanel to MyWeek.tsx. This is unavoidable for composition — clarified that the 3-line insertion IS the composition point.

**6. Risk rule replication strategy unclear (CLARIFIED)**
The plan says WhatIfService should "run the 5 rules from RiskDetectionService logic" but `detectAndStoreRiskSignals` persists to DB. Added explicit guidance to reimplement rules inline (not call the service), referencing the same threshold constants.

**7. WhatIfRequest DTO chess piece type (CLARIFIED)**
The plan says `chessPiece` but doesn't specify whether to use the domain `ChessPiece` enum or String. Since the constraint says "Do NOT modify existing domain enums," clarified using String to avoid coupling.

**8. WhatIfResponse nested types not specified (CLARIFIED)**
The original plan described response fields loosely. Added explicit nested record types (`PlanSnapshot`, `RcdoCoverageChange`, `RiskDelta`) so implementers know the exact structure.

**9. `resolvePromptVersion` return value (CLARIFIED)**
The plan says to return `"what-if"` but the actual pattern appends `-v1` suffix. Fixed to return `"what-if-v1"`.

Verdict: approve

The revised plan addresses all blocking issues I identified. The remaining items are clarifications and additional detail that strengthen implementability without changing the fundamental approach.