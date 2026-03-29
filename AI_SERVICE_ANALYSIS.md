# Complete Analysis of AI Service Layer

## Architecture Overview

**Core Infrastructure:**
- `AiProviderRegistry`: Single entry point; holds list of `AiProvider` implementations. Global `ai.enabled` flag (default: true).
- `AiContext`: Record with 9 fields (suggestionType, userId, planId, commitId, commitData, planData, historicalCommits, rcdoTree, additionalContext). Type discriminator determines prompt template.
- `AiSuggestionResult`: Immutable result with (available, payload, rationale, confidence, modelVersion, promptVersion). Graceful degradation when available=false.
- `AiSuggestionService`: Persists all suggestions to `ai_suggestion` table; computes SHA-256 hash of serialized context for dedup; samples async faithfulness evals.

**Design Pattern:** All 8 services follow identical flow:
1. Load entity from DB
2. Build map-based context
3. Create AiContext with type discriminator  
4. Call `registry.generateSuggestion(context)`
5. Parse JSON response (or handle unavailable gracefully)
6. Persist suggestion + return DTO

---

## Service-by-Service Analysis

### 1. CommitDraftAssistService

**Purpose:** Generate editable draft suggestions for commit title/description/success criteria/estimate points/chess piece (PRD §17 cap 1).

#### Data Queried
```java
// Exact DB access:
- planRepo.findById(request.planId()) — WeeklyPlan entity
- commitRepo.findByOwnerUserId(plan.getOwnerUserId()) — ALL past commits by user
```

**Naive Issue:** Loads ALL historical commits by user (unbounded query). No pagination, no date range filter. With 10 years of data, this could fetch thousands of rows. Should limit to last N weeks or add index on (userId, createdAt DESC) with LIMIT clause.

#### Prompt Context Assembly
```java
// commitData map:
Map {
  "title": request.currentTitle()          // user's draft title (string)
  "description": request.currentDescription() // draft description (string, nullable)
  "successCriteria": request.currentSuccessCriteria() // nullable
  "estimatePoints": request.currentEstimatePoints() // nullable Integer
  "chessPiece": request.chessPiece().name() // enum stringified (KING, QUEEN, etc.)
}

// planData map:
Map {
  "weekStartDate": plan.getWeekStartDate().toString() // ISO date
  "capacityPoints": plan.getCapacityBudgetPoints()     // int
}

// historical: List<Map> containing ONLY basic fields:
[
  {
    "title": c.getTitle()
    "estimatePoints": c.getEstimatePoints()
    "chessPiece": c.getChessPiece().name()
  }
  // ← no success criteria, no description, no outcomes
]

// AiContext fields:
- suggestionType: AiContext.TYPE_COMMIT_DRAFT
- userId, planId, commitId: all provided
- commitData, planData, historicalCommits: as above
- rcdoTree: empty List
- additionalContext: empty Map
```

**Naive Issue:** Historical commits stripped to 3 fields. No outcome data (COMPLETED, CANCELLED), no carry-forward streak, no linked RCDO. This discards pattern context that would help the LLM estimate accurately (e.g., "your KING commits usually cost 5pts").

#### LLM Call
```java
// Simple pass-through:
AiSuggestionResult result = registry.generateSuggestion(context);
// No retry logic, no input validation, no request size checks.
// Provider called once; if it fails, entire endpoint returns unavailable.
```

#### Response Parsing
```java
JsonNode node = objectMapper.readTree(result.payload());

// Expected JSON shape (from prompt template, not validated):
{
  "suggestedTitle": string,
  "suggestedDescription": string,
  "suggestedSuccessCriteria": string,
  "suggestedEstimatePoints": int,
  "suggestedChessPiece": "KING" | "QUEEN" | ...
}

// Parsing logic:
- textOrNull(node, field) → if node.get(field) is null/blank → null, else asText()
- Integer via node.get("suggestedEstimatePoints").asInt() (throws if non-numeric)
- Enum: try ChessPiece.valueOf(text.toUpperCase()); catch IllegalArgumentException → null

// Storage:
String contextJson = toJson(Map.of("commit": commitData, "plan": planData));
// ← NOTE: re-serializes commitData/planData; doesn't include historical!
// This means audit log loses the historical context that drove the suggestion.

AiSuggestion stored = suggestionService.storeSuggestion(
  TYPE_COMMIT_DRAFT, 
  userId, 
  planId, 
  commitId, 
  contextJson,  // ← re-serialized, incomplete
  result
);
```

**Parsing Issues:**
1. **No schema validation.** If LLM returns `{"foo": "bar"}`, all fields are null, response is empty but doesn't error.
2. **No bounds checking.** Estimate could be -999; no min/max validation.
3. **Incomplete context persistence.** The audit trail doesn't preserve the historical commits list, so future evals can't understand why the suggestion was made.
4. **Silent failures.** JsonProcessingException caught and logged as WARN; endpoint returns unavailable. User never sees what went wrong.

#### Error Handling
```java
try {
  JsonNode node = objectMapper.readTree(result.payload());
  // ... parsing ...
  return new CommitDraftAssistResponse(true, ...);
} catch (JsonProcessingException e) {
  log.warn("Failed to parse AI draft assist payload: {}", e.getMessage());
  return CommitDraftAssistResponse.unavailable();
}
```

**Naive:** Swallows all exceptions into a single "unavailable" response. No distinguish between:
- Malformed JSON (provider bug)
- Missing expected fields (schema mismatch, prompt version skew)
- Invalid enum values (data drift)

#### Sophistication vs. Naive
| Aspect | Level | Notes |
|--------|-------|-------|
| DB query design | **Naive** | Unbounded historical commits; no pagination |
| Context completeness | **Naive** | Strips 90% of commit fields; loses outcome/RCDO data |
| Response validation | **Naive** | No schema check; silently null-fills missing fields |
| Error handling | **Naive** | All failures → same unavailable response |
| Audit trail | **Naive** | Persisted context ≠ input context (re-serialized subset) |
| **Positive aspects** | **Good** | Graceful AI degradation; editable (non-auto-apply) |

---

### 2. CommitLintService

**Purpose:** Commit quality rules + optional AI enrichment (PRD §17 cap 2). Hard rules (must resolve before lock) vs. soft guidance (informational).

#### Data Queried
```java
// Exact DB access:
- planRepo.findById(request.planId()) — WeeklyPlan entity
- commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId()) — commits for this plan
- rcdoNodeRepo.findByParentIdAndStatus(commit.getRcdoNodeId(), ACTIVE) — for each commit
```

**Design:** Bounded query per plan (small N). No unbounded access.

#### Lint Rules (Deterministic, No AI)
```java
// Hard validations:
1. MISSING_SUCCESS_CRITERIA 
   → ChessPiece == KING or QUEEN AND (successCriteria == null or blank)

2. DUPLICATE_TITLE 
   → normalized(title.toLowerCase().replaceAll("\\s+", " ")) seen before

// Soft guidance:
1. VAGUE_TITLE 
   → title.length() < 10 OR all words in {fix, update, change, misc, ...}

2. PARENT_LEVEL_RCDO 
   → commit.rcdoNodeId != null AND that node has children

3. ESTIMATE_INCONSISTENCY 
   → ChessPiece in {KING, QUEEN} AND estimatePoints <= 1

4. Over-fragmentation 
   → Plan has > 12 commits
```

**Strength:** Deterministic, cacheable, auditable rules. No LLM involved initially.

#### AI Enrichment (Stub)
```java
// Called if AI enabled:
Map<String, Object> planData = Map.of("commitCount", commits.size());
AiContext ctx = new AiContext(
  AiContext.TYPE_COMMIT_LINT,
  request.userId(), 
  request.planId(), 
  null,  // ← no commitId
  Map.of(),  // empty commitData
  planData,  // only commit count
  List.of(),  // empty historicalCommits
  List.of(),  // empty rcdoTree
  Map.of()   // empty additionalContext
);
registry.generateSuggestion(ctx);
// ← Result is IGNORED. LLM called but output discarded.
// Comment: "We don't parse the AI output for lint — rules-based checks are authoritative."
```

**Naive Design:** LLM call is vestigial — made for "logging/learning" but output never used. Context so sparse (only commitCount) that LLM can't add value. Cost of calling LLM without using result.

#### Response Parsing
```java
// Not parsed. LLM output discarded.
// Only rules-based checks returned:
return new CommitLintResponse(true, hardMessages, softMessages);
```

#### Error Handling
```java
try {
  enrichSoftGuidanceFromAi(...);
} catch (Exception ex) {
  // Swallow — lint never fails due to AI errors
}
```

**Strength:** Lint always succeeds, never blocked by AI. **Weakness:** Silently swallows errors without logging reason.

#### Sophistication vs. Naive
| Aspect | Level | Notes |
|--------|-------|-------|
| Rules engine | **Sophisticated** | Clear, deterministic, auditable |
| Data query | **Good** | Bounded, efficient |
| AI integration | **Very Naive** | Call LLM but ignore output; vestigial |
| Error handling | **Good** | Never blocks core workflow |
| **Main flaw** | | Why call LLM if output discarded? Wastes quota. |

---

### 3. RcdoSuggestService

**Purpose:** Suggest primary RCDO node for commit draft (PRD §17 cap 3). Only surface if confidence ≥ 0.7.

#### Data Queried
```java
// Exact DB access:
- rcdoNodeRepo.findByStatus(RcdoNodeStatus.ACTIVE) — ALL active RCDO nodes
  → Builds full tree: id, title, nodeType, parentId for each
```

**Naive:** Entire RCDO tree passed to LLM every time. With 1000+ nodes, this is N×M cost (N suggestions × M LLM tokens per node). Should paginate or use retrieval-augmented generation (RAG).

#### Prompt Context Assembly
```java
// rcdoTree: List<Map> for each active node:
[
  {
    "id": node.getId().toString()
    "title": node.getTitle()
    "nodeType": node.getNodeType().name()  // e.g., "REQUIREMENT", "OUTCOME"
    "parentId": node.getParentId() != null ? toString() : null
  }
]

// commitData:
Map {
  "title": request.title()
  "description": request.description()
  "chessPiece": request.chessPiece() != null ? name() : null
}

// AiContext:
- suggestionType: TYPE_RCDO_SUGGEST
- userId, planId, commitId: all provided
- commitData, planData (empty), historicalCommits (empty): as above
- rcdoTree: full tree (unbounded)
- additionalContext: empty
```

#### LLM Call
```java
AiSuggestionResult result = registry.generateSuggestion(context);
if (!result.available()) {
  return RcdoSuggestResponse.unavailable();
}
```

#### Response Parsing
```java
// Expected JSON:
{
  "suggestedRcdoNodeId": "uuid-string"
}

private UUID parseSuggestedRcdoId(String payload) {
  JsonNode node = objectMapper.readTree(payload);
  JsonNode idNode = node.get("suggestedRcdoNodeId");
  if (idNode == null || idNode.isNull() || idNode.asText().isBlank()) {
    return null;
  }
  String raw = idNode.asText().trim();
  return raw.isEmpty() ? null : UUID.fromString(raw);
  // throws IllegalArgumentException if UUID.fromString fails
}
```

**Parsing Issues:**
1. No try-catch for UUID.fromString — caught at method level but logs as DEBUG.
2. No validation that returned UUID is in the provided rcdoTree.

#### Confidence Threshold
```java
if (result.confidence() < CONFIDENCE_THRESHOLD) {  // 0.7
  log.debug("RCDO suggestion below threshold: confidence={}", result.confidence());
  return RcdoSuggestResponse.belowThreshold();
}
```

**Strength:** Threshold prevents low-confidence hallucinations from surfacing. **Question:** Why not parameterize this threshold?

#### Double-Check: Verify RCDO Exists
```java
// After parsing ID:
String rcdoTitle = rcdoNodeRepo.findById(suggestedId)
  .map(RcdoNode::getTitle)
  .orElse(null);
if (rcdoTitle == null) {
  log.debug("Suggested RCDO node {} not found; returning below-threshold", suggestedId);
  return RcdoSuggestResponse.belowThreshold();
}
```

**Strength:** Validates that LLM-suggested ID exists in DB before returning. Prevents returning stale/hallucinated IDs.

#### Sophistication vs. Naive
| Aspect | Level | Notes |
|--------|-------|-------|
| Data query | **Naive** | Entire RCDO tree in every request; no pagination/RAG |
| Confidence threshold | **Good** | Blocks low-confidence suggestions |
| Response validation | **Sophisticated** | Verifies suggested ID exists in DB |
| Error handling | **Good** | Parse errors → below-threshold (graceful) |
| **Main issue** | | Tree size unbounded; should use retrieval or chunking |

---

### 4. RiskDetectionService

**Purpose:** Detect risk signals (PRD §17 cap 4). Rules-based (5 signals) + optional LLM augmentation. Scheduled daily + on-demand (lock). Stored in `ai_suggestion` table as RISK_SIGNAL type.

#### Data Queried

**On-demand (detectAndStoreRiskSignalsById):**
```java
- planRepo.findById(planId) — WeeklyPlan
- (then calls detectAndStoreRiskSignals(plan))

detectAndStoreRiskSignals(plan):
- commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId()) — commits
- For each commit with KING/QUEEN:
  - workItemRepo.findById(commit.getWorkItemId()) — linked ticket
  - statusHistoryRepo.findByWorkItemIdOrderByCreatedAtAsc(ticket.getId()) — full history
- scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId()) — all scope changes
- suggestionRepo.findByPlanIdAndSuggestionType(plan.getId(), "RISK_SIGNAL") — delete stale
```

**Naive Issue:** Full status history loaded for every King/Queen commit, then scanned linearly to find most recent BLOCKED transition. With N commits × M history entries, this is O(N×M). Should have a query like `findLatestStatusTransitionTo(workItemId, status)`.

**Scheduled Daily (runDailyRiskDetection):**
```java
planRepo.findByState(PlanState.LOCKED)  // ← ALL locked plans
// ↑ Scans entire PLANS table filtered by state.
```

**Design:** Depends on index (planState). If none exists, full table scan for every daily run. Should verify index exists on (state, id).

#### Rules-Based Signals (5 deterministic)

```java
1. OVERCOMMIT
   totalPoints > budget
   → "planned X pts exceeds capacity budget of Y pts"

2. UNDERCOMMIT
   (totalPoints / budget) < 0.60
   → "planned X pts is less than 60% of capacity budget (Y pts)"

3. REPEATED_CARRY_FORWARD
   For each commit: carryForwardStreak >= 2
   → "commit 'X' has been carried forward N times (streak >= 2)"

4. BLOCKED_CRITICAL
   For each King/Queen with linked ticket:
     ticket.status == BLOCKED
     Duration since BLOCKED >= 48 hours
   → "King/Queen 'X' linked ticket has been BLOCKED for > 48 hours"

5. SCOPE_VOLATILITY
   scopeChangeCount > 3
   → "plan has X post-lock scope changes (threshold: 3)"
```

All persisted with:
```java
s.setSuggestionType("RISK_SIGNAL");
s.setModelVersion(MODEL_VERSION);  // "rules-v1"
s.setSuggestionPayload("{\"signalType\":\"" + signal.type() + "\"}");
s.setRationale(rationale_string);
```

#### LLM Augmentation (Supplementary)

```java
// Called ONLY if aiProviderRegistry.isAiEnabled()
// NEVER blocks the rules engine — failures swallowed

detectAiRiskSignals(plan, commits):
  // Build detailed commit + plan context
  for each commit:
    commitDataList.add({
      commitId, title, chessPiece, estimatePoints, description,
      successCriteria, carryForwardStreak, outcome
    })
  
  planData = {
    state, capacityBudgetPoints, weekStartDate, totalPlannedPoints
  }
  
  // Scope changes:
  additionalContext = {
    "scopeChanges": [
      { category, reason },
      ...
    ]
  }
  
  AiContext context = new AiContext(
    TYPE_RISK_SIGNAL,
    plan.getOwnerUserId(),
    plan.getId(),
    null,
    Map.of(),  // empty commitData
    planData,
    commitDataList,  // detailed list, not historical
    List.of(),  // no rcdoTree
    additionalContext
  );
  
  AiSuggestionResult result = aiProviderRegistry.generateSuggestion(context);
  return parseAiRiskSignals(result.payload());
```

**Prompt:** Presumably asks LLM for hidden risks: "Given this plan, what risks might the rules miss?"

#### Response Parsing

```java
// Expected JSON format (documented in code):
{
  "signals": [
    {
      "signalType": string,
      "commitId": uuid | null,
      "severity": "LOW" | "MEDIUM" | "HIGH",
      "description": string,
      "suggestedAction": string
    },
    ...
  ]
}

// Parsing logic:
private List<RawSignal> parseAiRiskSignals(String payload) {
  JsonNode signalsNode = root.get("signals");  // null-safe
  if (!signalsNode.isArray()) return result;  // empty list
  
  for (JsonNode signal : signalsNode) {
    String signalType = signal.path("signalType").asText("AI_RISK_DETECTED");
    String description = signal.path("description").asText("");
    String severity = signal.path("severity").asText("MEDIUM");
    String suggestedAction = signal.path("suggestedAction").asText("");
    String commitIdStr = signal.path("commitId").asText(null);
    
    UUID commitId = null;
    if (commitIdStr != null && !"null".equals(commitIdStr) && !commitIdStr.isBlank()) {
      try {
        commitId = UUID.fromString(commitIdStr);
      } catch (IllegalArgumentException ignored) {}
    }
    
    String rationale = "[" + severity + "] " + description;
    if (!suggestedAction.isBlank()) {
      rationale += " → " + suggestedAction;
    }
    result.add(new RawSignal(signalType, commitId, rationale));
  }
}
```

**Parsing Issues:**
1. **Lenient null handling.** Field missing? Return default (e.g., "AI_RISK_DETECTED"). Malformed UUID? Ignore, commitId = null.
2. **No validation that signalType is expected.** LLM could return "FOO_RISK"; it's stored as-is.
3. **"null" string check.** `if (!"null".equals(commitIdStr))` suggests LLM sometimes returns the string "null" instead of actual null. Band-aid, not root fix.

#### Persistence
```java
for (RawSignal signal : signals) {
  AiSuggestion s = new AiSuggestion();
  s.setSuggestionType("RISK_SIGNAL");
  s.setPlanId(plan.getId());
  s.setCommitId(signal.commitId());
  s.setUserId(plan.getOwnerUserId());
  s.setPrompt("{}");  // ← EMPTY! No context persisted!
  s.setRationale(signal.rationale());
  s.setSuggestionPayload("{\"signalType\":\"" + signal.type() + "\"}");
  s.setModelVersion(signal.type().startsWith("AI_")
    ? aiProviderRegistry.getActiveProvider().map(p -> p.getVersion()).orElse(MODEL_VERSION)
    : MODEL_VERSION);
  suggestionRepo.save(s);
}
```

**Audit Trail Issue:** `prompt = "{}"`. The actual input context (all commitDataList, planData, scopeChanges) is not persisted. Future evals can't reconstruct why the LLM suggested a signal.

#### Query Interface
```java
@Transactional(readOnly = true)
public PlanRiskSignals getRiskSignals(UUID planId, UUID callerId) {
  authService.checkCanAccessUserFullDetail(callerId, plan.getOwnerUserId());
  // ← Privacy: only owner, manager, or admin may view
  
  List<AiSuggestion> suggestions = suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL");
  
  // Extract signal type from JSON or rationale fallback:
  signals = suggestions.stream()
    .map(s -> new RiskSignalResponse(
      s.getId(),
      extractSignalType(s.getRationale(), s.getSuggestionPayload()),
      s.getRationale(),
      s.getPlanId(),
      s.getCommitId(),
      s.getCreatedAt()
    )).toList();
}

private String extractSignalType(String rationale, String payload) {
  // Try JSON first
  try {
    JsonNode node = objectMapper.readTree(payload);
    JsonNode typeNode = node.get("signalType");
    if (typeNode != null && !typeNode.isNull()) {
      return typeNode.asText();
    }
  } catch (JsonProcessingException ignored) {}
  
  // Fallback: parse from rationale
  // Expects format: "OVERCOMMIT: ..." or "[MEDIUM] AI_RISK_DETECTED: ..."
  if (rationale != null && rationale.contains(": ")) {
    String after = rationale.substring(rationale.indexOf(": ") + 2);
    String[] tokens = after.trim().split("\\s+");
    if (tokens.length > 0 && tokens[0].matches("[A-Z_]+")) {
      return tokens[0];
    }
  }
  return "UNKNOWN";
}
```

**Fragile Parsing:** Extracts signal type from free-form text using regex `[A-Z_]+`. If rationale changes format, parsing breaks. Should store signalType directly as a field.

#### Error Handling
```java
public void runDailyRiskDetection() {
  for (WeeklyPlan plan : lockedPlans) {
    try {
      detectAndStoreRiskSignals(plan);
    } catch (Exception ex) {
      log.warn("Risk detection failed for plan {}: {}", plan.getId(), ex.getMessage());
      // ← Continue processing other plans; don't re-throw
    }
  }
}
```

**Strength:** Batch job resilient to individual plan failures.

#### Sophistication vs. Naive
| Aspect | Level | Notes |
|--------|-------|-------|
| Rules engine | **Sophisticated** | 5 clear signals, well-documented |
| DB queries | **Naive** | Full status history scan per commit; should have indexed query |
| LLM context | **Good** | Detailed commit + plan data passed |
| LLM result parsing | **Naive** | Lenient defaults; "null" string hack; no validation of signalType |
| Audit trail | **Very Naive** | prompt="{}"; context not persisted for eval |
| Privacy | **Sophisticated** | Access control enforced (owner/manager only) |
| Batch resilience | **Good** | Skips failed plans; logs but continues |
| **Main flaws** | | No context persistence; fragile signal type extraction |

---

### 5. ReconcileAssistService

**Purpose:** Reconciliation assistance (PRD §17 cap 5). Generates: outcome suggestions, draft summary, carry-forward recommendations.

#### Data Queried
```java
- planRepo.findById(request.planId()) — WeeklyPlan
- commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId()) — commits
- scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId()) — count only
```

**Design:** Bounded per-plan; efficient.

#### Prompt Context Assembly
```java
// planData:
Map {
  "weekStartDate": plan.getWeekStartDate().toString()
  "state": plan.getState().name()
  "scopeChanges": scopeChangeCount  // count only
  "commitCount": commits.size()
}

// commitList: List<Map> for each commit:
[
  {
    "id": c.getId().toString()
    "title": c.getTitle()
    "chessPiece": c.getChessPiece().name()
    "outcome": c.getOutcome().name()  // enum or null
    "carryForwardStreak": c.getCarryForwardStreak()
  }
]

// AiContext:
- suggestionType: TYPE_RECONCILE_ASSIST
- userId, planId, commitId: provided, null, null
- commitData, rcdoTree: empty
- planData, historicalCommits (as commitList above): as above
- additionalContext: empty
```

#### LLM Call
```java
AiSuggestionResult result = registry.generateSuggestion(context);
if (!result.available()) {
  return ReconcileAssistResponse.unavailable();
}
```

#### Response Parsing
```java
// Expected JSON:
{
  "draftSummary": string,
  "likelyOutcomes": [
    {
      "commitId": uuid,
      "commitTitle": string,
      "suggestedOutcome": string,  // enum name
      "rationale": string
    }
  ],
  "carryForwardRecommendations": [
    {
      "commitId": uuid,
      "commitTitle": string,
      "rationale": string
    }
  ]
}

// Parsing logic:
draftSummary = node.has("draftSummary") ? node.get("draftSummary").asText(null) : null;

if (node.has("likelyOutcomes") && node.get("likelyOutcomes").isArray()) {
  for (JsonNode entry : node.get("likelyOutcomes")) {
    String commitIdStr = entry.has("commitId") ? entry.get("commitId").asText(null) : null;
    if (commitIdStr == null) continue;  // skip if no id
    try {
      outcomes.add(new CommitOutcomeSuggestion(
        UUID.fromString(commitIdStr),
        entry.has("commitTitle") ? entry.get("commitTitle").asText(null) : null,
        entry.has("suggestedOutcome") ? entry.get("suggestedOutcome").asText(null) : null,
        entry.has("rationale") ? entry.get("rationale").asText(null) : null
      ));
    } catch (IllegalArgumentException ignored) {}
  }
}

// Same for carryForwardRecommendations
```

**Parsing Issues:**
1. **Silent skipping.** If array element lacks commitId, skip it. If UUID.fromString fails, ignore. No logging.
2. **No validation that suggested outcome is valid enum.** "FOOBAR" accepted and passed to client as-is.
3. **Stub behavior noted in comments.** "AI stub returns empty list" — implies LLM response is not actually used in production yet. This is a feature under development.

#### Sophistication vs. Naive
| Aspect | Level | Notes |
|--------|-------|-------|
| DB queries | **Good** | Bounded, efficient per-plan |
| Context building | **Naive** | Only basic commit data; no linked work items, no historical outcomes |
| Response parsing | **Naive** | Silent skips; no enum validation; stub implementation |
| Error handling | **Naive** | Catch-all JsonProcessingException → warn + unavailable |
| **Status** | | Feature under development; not yet fully functional |

---

### 6. ManagerAiSummaryService

**Purpose:** Weekly team AI summary (PRD §17 cap 6) for managers. Cites top RCDO branches, unresolved exceptions, carry-forward patterns, critical blocked items. **Access control:** Managers/admins only.

#### Data Queried
```java
// Access control first:
UserRole callerRole = authService.getCallerRole(callerId);
if (callerRole != UserRole.ADMIN && callerRole != UserRole.MANAGER) {
  throw new AccessDeniedException(...)
}

// Then fetch:
- teamRepo.findById(teamId) — Team
- membershipRepo.findByTeamId(teamId) — List<TeamMembership>
- planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart) — List<WeeklyPlan> for week
- For each plan:
  - commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId()) — commits
- exceptionRepo.findByTeamIdAndWeekStartDateAndResolved(teamId, weekStart, false) — unresolved MREs
- For each commit with KING/QUEEN:
  - workItemRepo.findById(commit.getWorkItemId()) — ticket, check if BLOCKED
- rcdoNodeRepo.findById(rcdo_id) — for 3 top branches
```

**Naive Issue:** No pagination on plan count. If team has 100+ plans, loads all with all commits. Should use batch queries or cached aggregations.

#### Derived Data (Pre-LLM)

```java
// 1. Top 3 RCDO branches by planned points:
deriveTopRcdoBranches(allCommits):
  Map<UUID, Integer> pointsByRcdo = new LinkedHashMap<>();
  for commit where rcdoNodeId != null:
    pointsByRcdo.merge(rcdoNodeId, estimatePoints, sum)
  
  return top 3 by value, resolved to titles:
    ["Requirement 1 (50 pts)", "Outcome 2 (45 pts)", ...]

// 2. Carry-forward patterns:
deriveCarryForwardPatterns(allCommits):
  carryForwardCount = count(c where carryForwardStreak > 0)
  if 0: return []
  
  patterns = [
    "X commit(s) carried forward this week",
    "Y commit(s) carried forward 2+ consecutive weeks"  // if Y > 0
  ]

// 3. Critical blocked items (King/Queen with BLOCKED ticket):
deriveCriticalBlockedItems(allCommits):
  for each commit where chessPiece in {KING, QUEEN}:
    if workItemId != null && workItem.status == BLOCKED:
      blocked.add(workItem.id)
  return blocked list (just IDs)
```

#### Prompt Context Assembly
```java
// additionalContext:
Map {
  "unresolvedExceptions": exceptionIds.size()  // count
  "carryForwardPatterns": [...strings...]
  "criticalBlockedItems": blockedItems.size()  // count
  "memberCount": memberships.size()
  "topRcdoBranches": [...3 strings...]
}

// AiContext:
- suggestionType: TYPE_TEAM_SUMMARY
- userId: callerId
- planId, commitId: null
- commitData, historicalCommits, rcdoTree: empty
- planData: {
    "weekStart": weekStart.toString(),
    "teamId": teamId.toString()
  }
- additionalContext: as above
```

**Naive:** Only summary counts/patterns passed, not details. LLM can't produce specific actionable insights (e.g., "Sarah's KING commit 'Database Migration' is blocked").

#### LLM Call
```java
AiSuggestionResult result = registry.generateSuggestion(context);
```

#### Response Parsing
```java
// Expected JSON:
{
  "summaryText": string  // 2-3 sentence narrative
}

private String parseSummaryText(String payload, int memberCount, int planCount, int commitCount) {
  try {
    JsonNode node = objectMapper.readTree(payload);
    JsonNode summaryNode = node.get("summaryText");
    if (summaryNode != null && !summaryNode.isNull() && !summaryNode.asText().isBlank()) {
      return summaryNode.asText();
    }
  } catch (JsonProcessingException e) {
    log.debug(...)
  }
  
  // Fallback: generated summary
  return "Team has " + memberCount + " member(s) with " + planCount + " plan(s) and " + commitCount 
    + " total commit(s) this week.";
}
```

**Strength:** Fallback generated summary if LLM fails or returns empty. **Weakness:** Fallback is generic and unhelpful.

#### Sophistication vs. Naive
| Aspect | Level | Notes |
|--------|-------|-------|
| Access control | **Sophisticated** | Enforced before data access |
| Data aggregation | **Good** | Derives top branches, patterns, blocked items |
| DB queries | **Naive** | No pagination; unbounded plan/commit fetch |
| LLM context | **Naive** | Only summary counts, not details; LLM can't be specific |
| Response | **Naive** | Only returns single summary string; no structured output |
| Fallback | **Good** | Generated summary if LLM fails |

---

### 7. WhatIfService

**Purpose:** Simulation service (PRD §17 cap 7). Applies hypothetical mutations to plan in-memory, computes before/after impact (capacity delta, RCDO coverage, risk signals). Optional LLM narration.

#### Data Queried
```java
// All read-only; no mutations persisted
- planRepo.findById(request.planId()) — WeeklyPlan
- commitRepo.findByPlanIdOrderByPriorityOrder(request.planId()) — original commits
- scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(request.planId()) — count only
```

#### Mutation Application
```java
// Three mutation types:
enum WhatIfAction { ADD_COMMIT, REMOVE_COMMIT, MODIFY_COMMIT }

for (WhatIfMutation mut : request.hypotheticalChanges()) {
  switch (mut.action()) {
    case ADD_COMMIT:
      // Create synthetic WeeklyCommit (no persistence; in-memory)
      synthetic.setId(UUID.randomUUID())
      synthetic.setTitle(mut.title() != null ? mut.title() : "(hypothetical)")
      synthetic.setChessPiece(parseChessPiece(mut.chessPiece()))
      synthetic.setEstimatePoints(mut.estimatePoints())
      synthetic.setRcdoNodeId(mut.rcdoNodeId())
      commits.add(synthetic)
    
    case REMOVE_COMMIT:
      commits.removeIf(c -> mut.commitId().equals(c.getId()))
    
    case MODIFY_COMMIT:
      for (WeeklyCommit c : commits) {
        if (mut.commitId().equals(c.getId())) {
          if (mut.title() != null) c.setTitle(mut.title())
          if (mut.chessPiece() != null) c.setChessPiece(parseChessPiece(mut.chessPiece()))
          if (mut.estimatePoints() != null) c.setEstimatePoints(mut.estimatePoints())
          if (mut.rcdoNodeId() != null) c.setRcdoNodeId(mut.rcdoNodeId())
          break
        }
      }
  }
}
```

**Strength:** Mutations are isolated in-memory copies; original DB never touched. Enables safe experimentation.

#### Snapshot Building
```java
private PlanSnapshot buildSnapshot(WeeklyPlan plan, List<WeeklyCommit> commits, long scopeChangeCount) {
  int totalPoints = commits.stream()
    .mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
    .sum()
  
  int budget = plan.getCapacityBudgetPoints()
  
  List<String> signals = detectSignals(commits, scopeChangeCount, totalPoints, budget)
  
  Map<UUID, Integer> rcdoCoverage = computeRcdoCoverage(commits)
  
  return new PlanSnapshot(totalPoints, budget, signals, rcdoCoverage)
}

// detectSignals: Duplicates 5 rules from RiskDetectionService inline:
// 1. OVERCOMMIT: totalPoints > budget
// 2. UNDERCOMMIT: totalPoints/budget < 0.60
// 3. REPEATED_CARRY_FORWARD: any commit.carryForwardStreak >= 2
// 4. BLOCKED_CRITICAL: King/Queen with blocked ticket >48hrs
// 5. SCOPE_VOLATILITY: scopeChangeCount > 3
```

**Naive Issue:** Risk rules duplicated verbatim. Changes to RiskDetectionService must be manually propagated here. Should share a utility class.

#### Delta Computation
```java
// Structured before/after:
int capacityDelta = projectedState.totalPoints() - currentState.totalPoints()

// Coverage changes: diff rcdoCoverage maps
List<RcdoCoverageChange> coverageChanges = computeCoverageChanges(
  currentState.rcdoCoverage(),
  projectedState.rcdoCoverage()
);

// Risk delta: signal set diff
RiskDelta riskDelta = computeRiskDelta(
  currentState.riskSignals(),
  projectedState.riskSignals()
);
```

**Strength:** Clear, composable impact model.

#### Optional LLM Narration
```java
// Called if aiProviderRegistry.isAiEnabled()
// Never blocks the structured response
String[] fetchNarration(...) {
  if (!aiProviderRegistry.isAiEnabled()) {
    return new String[]{null, null};
  }
  
  try {
    Map<String, Object> additionalContext = buildNarrationContext(
      currentState, projectedState, capacityDelta, riskDelta, coverageChanges
    );
    
    AiContext context = new AiContext(
      TYPE_WHAT_IF,
      request.userId(),
      request.planId(),
      null,
      Map.of(),  // empty
      Map.of(),  // empty
      List.of(), // empty
      List.of(), // empty
      additionalContext  // impact data
    );
    
    AiSuggestionResult result = aiProviderRegistry.generateSuggestion(context);
    if (!result.available() || result.payload() == null) {
      return new String[]{null, null};
    }
    
    return parseNarration(result.payload());
  } catch (Exception e) {
    log.debug("LLM narration failed for what-if plan {}: {}", request.planId(), e.getMessage());
    return new String[]{null, null};
  }
}

// Expected JSON:
{
  "narrative": "Reducing estimates by 20% would bring total to 40pts, within budget.",
  "recommendation": "Proceed; low risk of overcommit."
}

// Parsing:
private String[] parseNarration(String payload) {
  try {
    JsonNode node = objectMapper.readTree(payload);
    String narrative = textOrNull(node, "narrative");
    String recommendation = textOrNull(node, "recommendation");
    return new String[]{narrative, recommendation};
  } catch (Exception e) {
    log.debug(...)
    return new String[]{null, null};
  }
}
```

**Strength:** Narration is optional; full structured response returned even if LLM fails. Client always gets impact metrics.

#### Sophistication vs. Naive
| Aspect | Level | Notes |
|--------|-------|-------|
| In-memory simulation | **Sophisticated** | Safe, read-only, no DB mutations |
| Mutation application | **Good** | Clear, composable actions |
| Snapshot model | **Sophisticated** | Structured before/after capture |
| Delta computation | **Sophisticated** | Coverage + risk deltas computed cleanly |
| LLM integration | **Sophisticated** | Optional narration; never blocks response |
| Risk rule duplication | **Naive** | Duplicates RiskDetectionService logic; should share |
| **Strength** | | Complete impact visibility; optional narration |

---

### 8. AiSuggestionService

**Purpose:** Persistence and audit layer. Stores all suggestions to `ai_suggestion` table; computes SHA-256 hash of context; samples async faithfulness evals.

#### Persistence
```java
public AiSuggestion storeSuggestion(
  String suggestionType,  // type discriminator
  UUID userId,
  UUID planId,            // may be null
  UUID commitId,          // may be null
  String contextString,   // serialized context
  AiSuggestionResult result) {
  
  AiSuggestion s = new AiSuggestion();
  s.setSuggestionType(suggestionType);
  s.setUserId(userId);
  s.setPlanId(planId);
  s.setCommitId(commitId);
  s.setPrompt(contextString != null ? contextString : "{}");
  s.setContextHash(hash(contextString));  // SHA-256
  s.setRationale(result.rationale());
  s.setSuggestionPayload(result.payload());
  s.setModelVersion(result.modelVersion());
  s.setPromptVersion(result.promptVersion());
  
  AiSuggestion saved = suggestionRepo.save(s);
  
  // Async prod eval sampling (Phase 4a):
  if (faithfulnessEvaluator != null) {
    faithfulnessEvaluator.maybeScopeAsync(saved.getId());
  }
  
  return saved;
}
```

**Strength:** Context preserved for audit and future evals. Hash enables dedup detection.

#### SHA-256 Context Hash
```java
static String hash(String input) {
  if (input == null || input.isBlank()) {
    return "";
  }
  try {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(bytes);  // hex string
  } catch (NoSuchAlgorithmException e) {
    log.warn("SHA-256 not available; using empty hash");
    return "";
  }
}
```

**Use Case:** Detect duplicate suggestions (same context hash within time window) to avoid redundant LLM calls and user confusion.

**Issue:** Empty hash fallback (`return ""`) means all failures dedup to empty string. If SHA-256 unavailable, multiple different contexts become identical hashes. Should throw or use a different algorithm.

#### Feedback Recording
```java
public AiFeedback recordFeedback(AiFeedbackRequest request) {
  if (!suggestionRepo.existsById(request.suggestionId())) {
    throw new ResourceNotFoundException(...)
  }
  
  AiFeedback feedback = new AiFeedback();
  feedback.setSuggestionId(request.suggestionId());
  feedback.setUserId(request.userId());
  feedback.setAccepted(request.action() != AiFeedbackRequest.FeedbackAction.DISMISSED);
  feedback.setFeedbackNotes(request.notes());
  AiFeedback saved = feedbackRepo.save(feedback);
  
  // Also update suggestion's accepted/dismissed flags:
  suggestionRepo.findById(request.suggestionId()).ifPresent(s -> {
    switch (request.action()) {
      case ACCEPTED, EDITED -> s.setAccepted(true);
      case DISMISSED -> s.setDismissed(true);
    }
    suggestionRepo.save(s);
  });
  
  return saved;
}
```

**Strength:** Bidirectional feedback linkage (suggestion ↔ feedback). Enables training signal collection.

**Weakness:** Action EDITED treated same as ACCEPTED. Should distinguish between accepted-without-edit vs. accepted-after-edit for finer-grained signal.

#### Overloaded Variant (for Team/Personal Insights)
```java
public AiSuggestion storeSuggestion(
  String suggestionType,
  UUID userId,
  UUID planId,
  UUID commitId,
  String contextString,
  AiSuggestionResult result,
  UUID teamId,        // ← new
  LocalDate weekStartDate)  // ← new
{
  AiSuggestion s = storeSuggestion(...);  // call main variant
  s.setTeamId(teamId);
  s.setWeekStartDate(weekStartDate);
  return suggestionRepo.save(s);
}
```

**Use Case:** Insights scoped to team + week can be queried efficiently by (teamId, weekStartDate).

#### Sophistication vs. Naive
| Aspect | Level | Notes |
|--------|-------|-------|
| Persistence | **Sophisticated** | Full context preserved for audit |
| Context hashing | **Sophisticated** | SHA-256 enables dedup detection |
| Hash failure handling | **Naive** | Empty string fallback; should throw or use alt algorithm |
| Feedback recording | **Good** | Bidirectional linkage |
| Feedback action semantics | **Naive** | EDITED treated as ACCEPTED; should distinguish |
| Async eval sampling | **Sophisticated** | Faithfulness evaluator hooked up |

---

## Cross-Service Patterns and Issues

### 1. **Context Persistence Inconsistency**

| Service | Stored Prompt | Issue |
|---------|---------------|-------|
| CommitDraftAssist | Re-serialized subset | Missing historical commits |
| RiskDetection | "{}" (empty) | No context preserved; evals impossible |
| ReconcileAssist | Re-serialized planData | Missing commit details |
| WhatIfService | Not persisted (read-only) | Not an issue; simulation, not stored |
| **Pattern** | Mix of full/partial/none | Audit trail incomplete; inconsistent for evals |

**Recommendation:** Always persist the full input context. If sensitive, encrypt the field. This enables future debuggability and training signal quality.

### 2. **Rule Duplication**

**RiskDetectionService** vs. **WhatIfService**: Both implement identical 5 risk detection rules. Changes to one must be manually propagated to the other.

**Solution:** Extract rules to shared utility class:
```java
public class RiskRules {
  public static List<String> detectSignals(List<WeeklyCommit> commits, ...) { ... }
}
```

### 3. **Lenient JSON Parsing**

All services use `.path()`, `.asText()`, defaults when fields missing. This masks:
- Schema mismatches (prompt version skew, provider changes)
- Data corruption
- Hallucinations (LLM returning unexpected enum values)

**Recommendation:** Strict validation with explicit error types:
```java
public record LintMessage(String code, String message, UUID commitId) {}
// Currently uses free-form strings; should use enum codes
```

### 4. **Unbounded Queries**

| Service | Query | Issue |
|---------|-------|-------|
| CommitDraftAssist | `findByOwnerUserId(...)` | No limit; 10 years of data? |
| RcdoSuggest | `findByStatus(ACTIVE)` | Entire tree in memory |
| ManagerAiSummary | `findByTeamIdAndWeekStartDate(...)` | No pagination |
| RiskDetection | `findByWorkItemIdOrderByCreatedAtAsc(...)` | Full history per commit |

**Pattern:** Missing `LIMIT`, `OFFSET`, or indexed queries like `findLatestStatusTransitionTo(...)`.

### 5. **Confidence Threshold**

Only **RcdoSuggestService** uses confidence filtering (0.7). Other services accept all LLM responses regardless of stated confidence.

**Pattern:** Only `RcdoSuggestResponse` structure includes confidence; others don't. Should be uniform:
- High confidence → surface
- Medium confidence → warn (show rationale)
- Low confidence → filter or demote

### 6. **Prompt Template Management**

All services assume a prompt template exists for their suggestionType (e.g., "COMMIT_DRAFT_ASSIST" → some template). But:
- No version negotiation: old clients may call with outdated context shape
- No validation: if prompt template changes, context shape not validated against new schema
- `promptVersion` field available but rarely used

**Missing:** Prompt registry service that validates context against schema before calling provider.

### 7. **Error Visibility**

Most services catch exceptions at top level and return unavailable. Users never see:
- Malformed response from LLM
- Invalid enum values
- Missing required fields

**Pattern:** All exceptions swallowed. Should distinguish:
- Transient (provider down) → unavailable, retry
- Permanent (bad prompt, schema mismatch) → alert engineer, don't retry

---

## Summary Table

| Service | Complexity | Naive Aspects | Strength |
|---------|-----------|---------------|----------|
| **CommitDraftAssist** | Low | Unbounded history, incomplete context, silent parse failures | Graceful degradation |
| **CommitLint** | Low | Vestigial LLM call (output ignored) | Deterministic rules |
| **RcdoSuggest** | Medium | Entire tree in memory, UUID string parsing hack | Confidence threshold, DB validation |
| **RiskDetection** | High | No context persistence, rule duplication, fragile signal type extraction | Comprehensive rules, batch resilience |
| **ReconcileAssist** | Medium | Stub implementation, silent skips, no enum validation | Bounded queries |
| **ManagerAiSummary** | Medium | No pagination, only summary counts not details, generic fallback | Access control, derived aggregations |
| **WhatIfService** | High | Rule duplication, only optional narration | In-memory safety, structured impact model |
| **AiSuggestionService** | Low | Hash collision on failure, EDITED≠ACCEPTED in feedback | Full context preservation, async evals |

---

## Concrete Recommendations (Priority)

### 🔴 Critical (Fix Now)

1. **RiskDetectionService: Persist context**
   ```java
   s.setPrompt(toJson(Map.of("commits", commitDataList, "plan", planData, "scopeChanges", ...)));
   // Currently: s.setPrompt("{}");
   ```

2. **Extract shared risk rules**
   ```java
   // Create RiskRuleEngine.detectSignals(...)
   // Use in both RiskDetectionService and WhatIfService
   ```

3. **Validate UUID parsing**
   ```java
   // RcdoSuggestService and others should not silently catch UUID.fromString
   // Log error; return below-threshold with reason
   ```

### 🟡 High (Implement Next Sprint)

4. **Add pagination to unbounded queries**
   - CommitDraftAssist: limit historical commits to last 12 weeks
   - ManagerAiSummary: add `LIMIT N` to plan queries

5. **Remove vestigial LLM calls**
   - CommitLintService: stop calling LLM if output ignored
   - Or: actually use LLM output for soft-guidance enrichment

6. **Uniform confidence filtering**
   - Add confidence to all service responses
   - Surface high-confidence, warn/filter low-confidence consistently

7. **Robust context hashing**
   ```java
   // Change empty string fallback to throwing exception or using MD5 alt
   // Or validate SHA-256 available at startup
   ```

### 🟢 Medium (Design for Next Phase)

8. **Prompt registry service**
   - Validate context shape against prompt schema
   - Support prompt versioning and migration

9. **Distinguish exception types**
   - Transient (provider down) vs. permanent (schema mismatch)
   - Implement smart retry with backoff for transient

10. **Feedback semantics refinement**
    - Separate ACCEPTED from EDITED for finer training signals
    - Correlate outcome vs. AI suggestion for learned effectiveness

---

## Code Examples: Before/After

### Example 1: CommitDraftAssist History Query (Unbounded)

**Before:**
```java
List<WeeklyCommit> pastCommits = commitRepo.findByOwnerUserId(plan.getOwnerUserId());
// ↑ Could be thousands of rows
```

**After:**
```java
// Limit to last 12 weeks
LocalDate since = plan.getWeekStartDate().minusWeeks(12);
List<WeeklyCommit> pastCommits = commitRepo.findByOwnerUserIdAndWeekStartDateGreaterThanEqual(
  plan.getOwnerUserId(), 
  since
);
```

### Example 2: RiskDetectionService Context Persistence (Empty)

**Before:**
```java
s.setPrompt("{}");  // ← loses all context
```

**After:**
```java
Map<String, Object> fullContext = Map.of(
  "commits", commitDataList,
  "plan", planData,
  "scopeChanges", scopeChanges.stream().map(sc -> Map.of(
    "category", sc.getCategory().name(),
    "reason", sc.getReason()
  )).toList()
);
s.setPrompt(objectMapper.writeValueAsString(fullContext));
```

### Example 3: UUID Parsing (Silent Failure)

**Before:**
```java
UUID commitId = null;
if (commitIdStr != null && !"null".equals(commitIdStr) && !commitIdStr.isBlank()) {
  try {
    commitId = UUID.fromString(commitIdStr);
  } catch (IllegalArgumentException ignored) {}  // ← silently ignored
}
```

**After:**
```java
UUID commitId = null;
if (commitIdStr != null && !"null".equals(commitIdStr) && !commitIdStr.isBlank()) {
  try {
    commitId = UUID.fromString(commitIdStr);
  } catch (IllegalArgumentException e) {
    log.warn("Malformed UUID in LLM response: {}", commitIdStr);
    // Decide: skip this signal, or return below-threshold with error
  }
}
```

### Example 4: Shared Risk Rules

**Before:** Duplicated in RiskDetectionService and WhatIfService

**After:**
```java
// New utility class
public class RiskRuleEngine {
  public static final long BLOCKED_CRITICAL_HOURS = 48;
  public static final int CARRY_FORWARD_STREAK_THRESHOLD = 2;
  // ... other constants
  
  public static List<String> detectSignals(
    List<WeeklyCommit> commits,
    long scopeChangeCount,
    int totalPoints,
    int budget,
    Instant now,
    WorkItemRepository workItemRepo,
    WorkItemStatusHistoryRepository statusHistoryRepo) {
    // Implementation once; used everywhere
  }
}

// In both services:
List<String> signals = RiskRuleEngine.detectSignals(...);
```

---

## Data Flow Diagram

```
User Request
    ↓
Service (e.g., CommitDraftAssistService.assist())
    ├─→ Load from DB (limited scope)
    ├─→ Build AiContext (map-based)
    ├─→ registry.generateSuggestion(context)
    │   └─→ AiProviderRegistry (graceful degradation)
    │       └─→ Provider.generateSuggestion(context)
    │           └─→ LLM call (external)
    │               ↓ (JSON response)
    │           ← AiSuggestionResult
    │
    ├─→ Parse JSON response
    │   ├─→ textOrNull, parseEnum (lenient)
    │   └─→ [Catch JsonProcessingException → unavailable]
    │
    ├─→ AiSuggestionService.storeSuggestion()
    │   ├─→ Compute contextHash (SHA-256)
    │   ├─→ Persist to ai_suggestion table
    │   └─→ Sample FaithfulnessEvaluator.maybeScopeAsync()
    │
    └─→ Return DTO response
        ├─→ aiAvailable: true/false
        ├─→ Parsed fields (or defaults if parse failed)
        └─→ Rationale (why suggestion made)

User Feedback (Optional)
    ↓
AiSuggestionService.recordFeedback()
    ├─→ Create AiFeedback record
    ├─→ Update AiSuggestion flags (accepted/dismissed)
    └─→ (Used for training signal downstream)
```

---

## Conclusion

The AI service layer is **functionally complete** but **operationally naive**. It demonstrates:

✅ **Strengths:**
- Graceful degradation when AI unavailable
- Access control enforced (ManagerAiSummary)
- Comprehensive audit trail (most services)
- Mix of deterministic rules + LLM augmentation

❌ **Weaknesses:**
- Unbounded DB queries (no pagination)
- Incomplete context persistence (audit trail gaps)
- Rule duplication (RiskDetectionService ↔ WhatIfService)
- Lenient JSON parsing (masks schema mismatches)
- Vestigial code (CommitLintService LLM call)
- No prompt versioning or schema validation
- Inconsistent error visibility

**Maturity Level:** MVP → Production (needs hardening for scale and reliability).

**Recommended Focus Areas for Next Phase:**
1. Fix context persistence (auditability)
2. Add pagination (scalability)
3. Implement prompt registry (flexibility)
4. Distinguish transient vs. permanent errors (resilience)
5. Uniform confidence filtering (UX consistency)
