# AI Services Quick Reference

## Service Summary Table

| Service | Purpose | Data Scope | Context | Response | Issues |
|---------|---------|-----------|---------|----------|--------|
| **CommitDraftAssist** | Draft title/desc/criteria/points/piece | User's ALL past commits (unbounded) | Commit draft + plan capacity + stripped history | JSON: suggestedTitle, suggestedDescription, suggestedEstimatePoints, suggestedChessPiece | Unbounded query; lost historical context in persistence |
| **CommitLint** | Quality rules: hard (must fix) + soft (guidance) | Plan's commits + RCDO hierarchy | Only plan commit count | Rules-based (no LLM output used) | LLM called but output discarded; wasteful |
| **RcdoSuggest** | Link commit to RCDO outcome node | ALL active RCDO nodes (full tree) | Commit (title/desc/piece) + entire RCDO tree | UUID (suggestedRcdoNodeId) | Entire tree in memory; no retrieval augmentation |
| **RiskDetection** | 5 rules + LLM augmentation for hidden risks | Plan commits + ticket history + scope changes | Detailed commits + plan + scope history | JSON: signals array (type, commitId, severity, description, action) | Context persisted as "{}" (lost); signal type extracted via regex; no persistence of AI context |
| **ReconcileAssist** | Outcome suggestions + summary + carry-forward recs | Plan commits + scope count | Basic commits (title/piece/outcome) + scope count | JSON: draftSummary, likelyOutcomes[], carryForwardRecommendations[] | Stub implementation; silent skips; no enum validation |
| **ManagerAiSummary** | Team-week summary for managers only | Team members + plans + all commits + exceptions + RCDO | Summary counts + pattern lists (top 3 RCDOs, CF patterns, blocked items) | summaryText (single string) | No pagination; only summary counts, not details; generic fallback |
| **WhatIfService** | Simulate plan mutations; compute impact deltas | Original commits + plan | Computed deltas (capacity, RCDO coverage, risks) | Before/after snapshots + deltas + optional LLM narrative | Rules duplicated from RiskDetection; narration only optional |
| **AiSuggestionService** | Persistence + audit + feedback recording | (All services' results) | Full context + response | Persisted AiSuggestion + AiFeedback records | Hash collision on SHA-256 failure; EDITED≠ACCEPTED |

---

## Data Query Patterns

### Unbounded (⚠️ Scaling Risk)
```
CommitDraftAssist:  commitRepo.findByOwnerUserId(userId)  
                    → Could be thousands; no LIMIT or date range

RcdoSuggest:        rcdoNodeRepo.findByStatus(ACTIVE)     
                    → Entire tree; scales with org size

ManagerAiSummary:   planRepo.findByTeamIdAndWeekStartDate(teamId, week)
                    → No LIMIT; could be 100+ plans
```

### Problematic Subqueries (⚠️ N+1 Risk)
```
RiskDetection:      For each King/Queen commit:
                      workItemRepo.findById(commitId)
                      statusHistoryRepo.findByWorkItemIdOrderByCreatedAtAsc(workItemId)
                      → Full history walk, every time
```

### Good (Bounded)
```
CommitLint:         commitRepo.findByPlanIdOrderByPriorityOrder(planId)
ReconcileAssist:    commitRepo.findByPlanIdOrderByPriorityOrder(planId)
WhatIfService:      commitRepo.findByPlanIdOrderByPriorityOrder(planId)
```

---

## Context Assembly Patterns

### Complete Context
```java
// RiskDetection (for LLM augmentation):
{
  "commits": [ { commitId, title, chessPiece, estimatePoints, description, 
                 successCriteria, carryForwardStreak, outcome } ],
  "plan": { state, capacityBudgetPoints, weekStartDate, totalPlannedPoints },
  "scopeChanges": [ { category, reason } ]
}
```

### Partial Context (Stripped)
```java
// CommitDraftAssist (audit trail):
{
  "commit": { title, description, successCriteria, estimatePoints, chessPiece },
  "plan": { weekStartDate, capacityPoints }
}
// ← Missing: historical outcomes, RCDO links, carry-forward streaks
```

### Sparse Context (Only Counts)
```java
// CommitLint (vestigial LLM call):
{
  "commitCount": <number>
}
// ← Too minimal; LLM can't add value
```

### Summary Context
```java
// ManagerAiSummary (for team insights):
{
  "unresolvedExceptions": <count>,
  "carryForwardPatterns": [ "X carried forward...", ... ],
  "criticalBlockedItems": <count>,
  "memberCount": <number>,
  "topRcdoBranches": [ "Branch 1 (50pts)", ... ]
}
// ← Aggregated; details lost; LLM can't be specific
```

---

## Response Parsing Strategy

### Lenient (Permissive, Silent Failures)
```java
// Pattern: used everywhere
JsonNode node = objectMapper.readTree(result.payload());
JsonNode child = node.get("field");
if (child == null || child.isNull() || child.asText().isBlank()) {
  return null;  // Missing field → null (no error)
}
return child.asText();
```

**Issues:**
- Malformed JSON? → JsonProcessingException caught, returns unavailable
- Missing field? → null (unclear if LLM omitted or schema changed)
- Invalid enum? → might throw or be passed as string

### Type-Safe Parsing (MISSING)
```java
// Recommended (not currently done):
private enum Outcome {
  COMPLETED, CANCELLED, CARRIED_FORWARD
}

// In parsing:
try {
  Outcome o = Outcome.valueOf(node.get("outcome").asText().toUpperCase());
} catch (IllegalArgumentException e) {
  log.error("Invalid outcome value: {}; signature mismatch?", node.get("outcome").asText());
  return belowThreshold();  // or throw
}
```

### Graceful Fallback (Good for User Experience)
```java
// ManagerAiSummary:
if (summaryText == null || summaryText.isBlank()) {
  summaryText = "Team has " + memberCount + " member(s) with " + planCount 
    + " plan(s) and " + commitCount + " total commit(s) this week.";
}
// ← Fallback generic summary if LLM fails
```

---

## Error Handling Patterns

### Swallow All (Most Services)
```java
try {
  // ... DB query + LLM call + parsing ...
} catch (Exception e) {
  log.warn("Failed to ...: {}", e.getMessage());
  return SomeResponse.unavailable();
}
// ↑ All errors → same "unavailable" response; user unaware of root cause
```

### Never Block Core Workflow (Good)
```java
// CommitLintService:
try {
  enrichSoftGuidanceFromAi(...);
} catch (Exception ex) {
  // Swallow — lint never fails due to AI errors
}
// ↑ Lint always returns; can't fail due to AI
```

### Batch Resilience (Good)
```java
// RiskDetectionService scheduled job:
for (WeeklyPlan plan : lockedPlans) {
  try {
    detectAndStoreRiskSignals(plan);
  } catch (Exception ex) {
    log.warn("Risk detection failed for plan {}: {}", plan.getId(), ex.getMessage());
  }
}
// ↑ Continue processing other plans; don't abort entire batch
```

---

## Sophistication Spectrum

### 🔴 Naive / MVP
```
CommitDraftAssist    - Unbounded history, incomplete persistence
CommitLint           - Vestigial LLM call
ReconcileAssist      - Stub implementation
```

### 🟡 Mixed (Some Good, Some Naive)
```
RcdoSuggest          - Good confidence threshold + DB validation
                       ✗ Entire tree in memory
ManagerAiSummary     - Good access control + aggregations
                       ✗ No pagination, sparse context
RiskDetection        - Good rules + batch resilience
                       ✗ No context persistence, fragile parsing
```

### 🟢 Sophisticated
```
WhatIfService        - In-memory safety, structured impact model
                       ✗ Rule duplication
AiSuggestionService  - Full context preservation, async evals
                       ✗ Hash collision on failure
```

---

## Critical Issues by Priority

### 🔴 P0: Data Loss / Audit Trail
1. **RiskDetectionService**: `prompt = "{}"` → loses all context
   - Impact: Future evals can't understand decisions
   - Fix: Persist full commitDataList + planData + scopeChanges

2. **CommitDraftAssist**: Persistence ≠ input context
   - Impact: Audit trail incomplete; diffs available vs. actual
   - Fix: Store the exact AiContext input, not re-serialized subset

### 🟡 P1: Scale / Performance
3. **CommitDraftAssist**: Unbounded `findByOwnerUserId(userId)`
   - Impact: Slow requests for users with 10+ years history
   - Fix: `findByOwnerUserIdAndWeekStartDateGreaterThanEqual(..., since)`

4. **RcdoSuggest**: Entire RCDO tree in memory every call
   - Impact: O(N) memory + tokens for every suggestion (N = tree size)
   - Fix: Use retrieval augmentation or chunked RAG

5. **RiskDetection**: Full status history per commit
   - Impact: O(N × M) where N = commits, M = history length
   - Fix: Add query like `findLatestStatusTransitionTo(workItemId, toStatus)`

### 🟡 P2: Code Quality / Maintainability
6. **Rule Duplication**: RiskDetection vs. WhatIfService
   - Impact: Risk of inconsistency; changes require dual updates
   - Fix: Extract `RiskRuleEngine.detectSignals(...)` utility

7. **Lenient Parsing**: Silent failures mask schema mismatches
   - Impact: Hard to debug when prompt template changes
   - Fix: Strict validation; explicit error types

8. **Vestigial Code**: CommitLintService calls LLM but ignores output
   - Impact: Wastes LLM quota; confusing to maintainers
   - Fix: Remove call or actually use output for soft-guidance

### 🟢 P3: Enhancement / Design Debt
9. **No Prompt Versioning**: Context shape not validated against schema
   - Fix: Implement PromptRegistryService
   - Validates context before LLM call
   - Supports A/B testing and prompt migrations

10. **Inconsistent Confidence Filtering**: Only RcdoSuggest uses it
    - Fix: Uniform confidence filtering across all services
    - High-confidence → surface, medium → warn, low → filter

---

## Test Coverage Gaps

### Missing Unit Tests
- **Context building**: Verify all fields included, no nulls where unexpected
- **Parsing logic**: Malformed JSON, missing fields, invalid enums
- **Boundary conditions**: Empty commits, null chessPiece, 0 capacity budget

### Missing Integration Tests
- **End-to-end**: Request → DB queries → context assembly → LLM mock → parsing → persistence → response
- **Error scenarios**: Provider unavailable, timeout, malformed response
- **Audit trail**: Verify persisted context matches input context

### Missing Performance Tests
- **Unbounded query**: Simulate 10k historical commits; measure latency
- **Tree size**: Simulate 1000-node RCDO tree; measure memory + parsing time
- **Batch risk detection**: Time daily job over 1000 LOCKED plans

---

## Recommendation Checklist

### This Sprint
- [ ] Fix RiskDetectionService: persist full context (not "{}")
- [ ] Extract RiskRuleEngine shared utility
- [ ] Add pagination to CommitDraftAssist historical commits
- [ ] Remove or fix CommitLintService vestigial LLM call

### Next Sprint
- [ ] Add pagination to ManagerAiSummary plan queries
- [ ] Implement PromptRegistryService with schema validation
- [ ] Implement proper error type distinction (transient vs. permanent)
- [ ] Add uniform confidence filtering

### Design Phase
- [ ] Implement RAG for RcdoSuggest (instead of full tree)
- [ ] Add cached aggregations for ManagerAiSummary
- [ ] Implement prompt versioning and A/B testing framework
- [ ] Design feedback semantics refinement (EDITED vs. ACCEPTED)
