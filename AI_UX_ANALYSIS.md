# AI UX Surface Analysis: GauntletAI Frontend

**Thoroughness**: Complete review of all 24 AI-related files (17 components + 5 API layers + 2 utilities)

---

## Executive Summary

The AI UX is **enterprise-grade but unevenly polished**. There's exceptional sophistication in **trust/transparency patterns** (evidence drawers, feedback loops, rationale displays), **state management** (debouncing, auto-run logic, graceful degradation), and **composition** (reusable feedback buttons, specialized renderers). However, the user experience varies widely: some surfaces are minimal/utilitarian, others are refined. The codebase prioritizes **assistive over authoritarian** design (per PRD §17) — AI suggestions are always optional, auditable, and never auto-execute.

### Overall Sophistication: **7/10**
- **Exceptional** (8-9/10): Commit composer, lint panel, evidence drawer, risk banners
- **Good** (7/10): Insight cards, semantic search, what-if planner
- **Basic** (5-6/10): Risk signals panel, suggestion inline renderings

---

## File-by-File Analysis

### **Components**

#### 1. **AiCommitComposer.tsx**
- **UX Pattern**: Two-phase modal wizard
  - Phase 1: Freeform text input + generate button
  - Phase 2: Structured draft with editable fields + accept/dismiss model
- **Loading State**: Loader2 spinner + "Generating…" button text
- **Error State**: Inline error message; manual fallback to form
- **Interaction Model**: 
  - User → text → generate → review → accept (or switch to manual)
  - Every field labeled "AI suggested" with badge
  - Chess piece constraint warnings (max 1 KING/week)
  - Can re-generate ("Try again" button) or switch to manual at any point
- **Polish Level**: **9/10** — Sophisticated state machine, graceful fallbacks, clear user intent flows
  - Parallel API calls (draft + RCDO) with non-fatal failure handling
  - Constraint validation prevents invalid submissions
  - Pre-filled form switch preserves user context
- **Key Insight**: Exemplary "never auto-submit" pattern — user must explicitly click "Accept & Create"

#### 2. **AiFeedbackButtons.tsx**
- **UX Pattern**: Side-by-side 👍/👎 micro-buttons (6×6px)
- **Loading State**: None (fire-and-forget)
- **Error State**: Silently ignore failures (non-critical)
- **Interaction Model**: Click once → show "Thanks! 👍" or "Noted 👎" → disabled
- **Polish Level**: **8/10** — Minimal, reusable, non-blocking
- **Ubiquitous**: Used by 10+ other components as a feedback collection layer

#### 3. **AiLintPanel.tsx**
- **UX Pattern**: Collapsible code quality hints (hard + soft)
  - Hard validations: red/neutral styling
  - Soft guidance: lighter gray styling
- **Loading State**: Shimmer skeleton (3 placeholder bars)
- **Error State**: Inline error + manual "Retry" button
- **Interaction Model**:
  - Manual mode: show button until user clicks, then run
  - Auto-run mode: fire on mount + debounce on `refreshKey` changes (800ms)
  - Refresh button always visible in results
- **Polish Level**: **8/10**
  - Smart auto-run with debounce guards against commit CRUD thrashing
  - Callback to parent for hint count badge (collapsed summary)
  - Graceful "unavailable" state when AI is down
- **Key Insight**: Teaches best practice for expensive operations in React (debounce + refresh key)

#### 4. **AiSuggestedBadge.tsx**
- **UX Pattern**: Inline micro-badge (✨ + "AI suggested" text)
- **Polish Level**: **9/10** — Atomic, reusable, properly accessible (`aria-label`)
- **Usage**: Paired with every AI-influenced field to signal non-finality

#### 5. **AnswerRenderer.tsx**
- **UX Pattern**: Lightweight markdown → JSX parser (no external deps)
  - Bold: `**text**` → `<strong>`
  - Chess pieces: `KING/QUEEN/ROOK/BISHOP/KNIGHT/PAWN` → styled badges with glyphs
  - Metrics: `5pts`, `85%` → highlighted
  - Quoted titles: `"commit title"` → monospace highlights
  - Lists: `-` prefix → `<li>`
  - Paragraphs: `\n\n` separator
- **Polish Level**: **8/10** — Smart pattern matching, no dependencies, proper list rendering
- **Key Insight**: Solves the "LLM text looks janky" problem without a heavy markdown lib

#### 6. **CommitDraftAssistButton.tsx**
- **UX Pattern**: Single-click "AI Suggest" button → inline suggestion cards
  - Each suggestion shows current vs. suggested side-by-side
  - "✓ Applied" badge after accept
  - "All good! No suggestions" state when optimized
- **Loading State**: Spinning Sparkles icon + "Thinking…" text
- **Error State**: Inline error + silent failure (non-blocking)
- **Interaction Model**: 
  - Click button → request suggestions
  - For each field with a diff: show badge + accept button
  - Accept → callback to parent + visual confirmation
  - Multiple fields can be independently accepted
- **Polish Level**: **7/10**
  - Good field-by-field acceptance UX
  - Could benefit from "accept all" shortcut
  - Parent must provide individual `onAccept*` callbacks (slightly verbose API)
- **Key Insight**: Teaches diff-style suggestion UX (current crossed-out, new bold)

#### 7. **EvidenceDrawer.tsx**
- **UX Pattern**: Four collapsible panes showing exactly what the AI saw
  - **Facts** (SQL): owner, team, week, points, commits, compliance, chess distribution
  - **Lineage** (DB): commit history chain with outcomes, streak badges
  - **Evidence** (Vector retrieval): top 5 semantic matches with scores
  - **Risk** (Feature store): completion ratio, carry-forward streak, active signal types
- **Loading State**: None (passed pre-computed evidence object)
- **Error State**: None (passed as null)
- **Interaction Model**: Expandable panes (chevron toggle), collapsible by default except Facts
- **Polish Level**: **9/10** — Exemplary transparency pattern
  - Every number is traceable to a data source
  - Proper badge indicators (success/danger for outcomes)
  - Clean grid layout for SQL facts
  - Responsive badge wrapping for chess distribution
- **Key Insight**: Directly implements "no black box AI" principle; every suggestion is auditable

#### 8. **InsightPanel.tsx**
- **UX Pattern**: Card list with severity badges (HIGH/MEDIUM/LOW in danger/warning/primary)
  - Balanced text layout (shrinkwrapped max-width)
  - Action suggestion as italic hint
  - Supports team-level (manager) or personal (IC) modes
- **Loading State**: 3 skeleton cards with shimmer
- **Error State**: Inline error message
- **Interaction Model**: 
  - Read-only insights with feedback buttons
  - Two modes: `mode="team"` for team insights, `mode="personal"` for plan insights
  - Balanced text uses canvas measurement to shrinkwrap text width
- **Polish Level**: **8/10**
  - Balanced text is thoughtful (reduces orphan lines)
  - Severity color scheme is consistent
  - Skeletal loading is clean
- **Key Insight**: Uses custom `useBalancedText` hook for typographic refinement

#### 9. **ManagerAiSummaryCard.tsx**
- **UX Pattern**: Prose summary card for team week
  - Top strategic branches (RCDO badges)
  - Carry-forward patterns (bullet list)
  - Exception/blocked item counts (warning badges)
- **Loading State**: Card skeleton with shimmer bars
- **Error State**: Inline error + muted unavailable state
- **Interaction Model**: 
  - Read-only; callback to parent with first-sentence preview for badge use
  - Supports manager-specific alerts (critical blocked items, unresolved exceptions)
- **Polish Level**: **8/10**
  - Useful preview-text callback for collapsed headers
  - Clear visual hierarchy (prose → branches → patterns → warnings)
  - Icon + count combos are quick to scan
- **Key Insight**: Shows how to surface AI narratives to managers without overwhelming them

#### 10. **ProactiveRiskBanner.tsx**
- **UX Pattern**: Alert banners for critical IC risks (OVERCOMMIT, BLOCKED_CRITICAL, REPEATED_CARRY_FORWARD)
  - Color-coded by risk type (warning/danger)
  - Rationale + action hint (→ "resolve blocked ticket" etc.)
  - Feedback buttons on each banner
- **Loading State**: None (returns `null` during load to avoid flash)
- **Error State**: Silently degrade (return `null` when unavailable)
- **Interaction Model**:
  - Persistent, non-dismissible (intentional for risk)
  - Callback to parent with critical signal count
  - Parent controls visibility ("only render when plan is LOCKED")
- **Polish Level**: **8/10**
  - Smart signal filtering (3 critical types only; non-critical types surfaced elsewhere)
  - Rich per-signal config (icons, colors, action hints)
  - Silent degradation is appropriate for non-critical UI
- **Key Insight**: Teaches proper risk alerting UX — persistent, actionable, specific hints

#### 11. **QueryAnswerCard.tsx**
- **UX Pattern**: RAG answer + collapsible sources + confidence bar
  - Confidence indicator: label + visual bar (color-coded by confidence)
  - Sources collapsible (chevron toggle)
  - Each source shows entity type badge, date, snippet, link (when applicable)
- **Loading State**: None (passed pre-fetched answer)
- **Error State**: None (validation in parent)
- **Interaction Model**: 
  - Expand sources on demand (compact by default)
  - Source links route to relevant tickets page
  - Feedback buttons on the card footer
- **Polish Level**: **8/10**
  - Confidence visualization is clear (high = bold full bar, low = underlined 20% bar)
  - Source citations are compact but scannable
  - "verify before acting" disclaimer is appropriately prominent
- **Key Insight**: Shows RAG confidence is visual, not just numeric

#### 12. **RcdoSuggestionInline.tsx**
- **UX Pattern**: Inline suggestion below RCDO picker
  - Shows title, confidence %, rationale, accept/dismiss buttons
  - Debounces fetch (1200ms) to avoid request spam
  - Auto-dismiss if user manually picks an RCDO
- **Loading State**: Pulsing text "Finding best RCDO match…" + spinner
- **Error State**: Silent (returns `null` on fetch failure)
- **Interaction Model**:
  - Fires when title length ≥ 8 chars
  - Debounced; resets if user changes title meaningfully
  - Accept → callback to parent + set `accepted` flag (hide suggestion)
  - Dismiss → hide suggestion until title changes
- **Polish Level**: **8/10**
  - Smart debounce logic respects user intent (won't re-fetch same title)
  - Auto-dismiss logic is intuitive (user already picked? hide suggestion)
  - Non-blocking failure (silent return)
- **Key Insight**: Teaches debouncing + state reset patterns for inline suggestions

#### 13. **ReconcileAssistPanel.tsx**
- **UX Pattern**: Reconciliation helper with outcome suggestions + carry-forward recs
  - Outcome badges color-coded (ACHIEVED=green, PARTIAL=yellow, NOT_ACHIEVED=red, CANCELED=gray)
  - Carry-forward recommendations with rationale
  - Draft summary text box
  - Applied suggestions show "✓ Queued"
- **Loading State**: Spinner + "Analyzing your week…"
- **Error State**: Border + error message + "Retry" button
- **Interaction Model**:
  - Manual trigger button
  - Each outcome/carry-forward has independent accept button
  - Parent receives callbacks for accepted changes
  - Refresh button always visible
- **Polish Level**: **8/10**
  - Color-coded outcomes are intuitive
  - Applied badges provide feedback
  - Outcome rationales are concise
- **Key Insight**: Teaches multi-step suggestion acceptance (outcomes + carry-forwards in one panel)

#### 14. **RiskSignalsPanel.tsx**
- **UX Pattern**: Grid of risk signal cards (simple, dense)
  - Signal type badge + date + rationale
  - Color-coded by signal type
- **Loading State**: Skeleton cards
- **Error State**: Error message
- **Interaction Model**: Read-only; called from Team Week risk tab
- **Polish Level**: **6/10**
  - Minimal styling (borders + background colors)
  - No feedback buttons (unlike ProactiveRiskBanner)
  - Less polished than other panels; feels utilitarian
- **Key Insight**: Baseline risk display; less refined than ProactiveRiskBanner variant

#### 15. **SemanticSearchInput.tsx**
- **UX Pattern**: AI Q&A search interface
  - Input + button in header row
  - Suggested questions shown when input empty
  - Answer card rendered below (via QueryAnswerCard)
  - Loading + error states inline
- **Loading State**: Spinner in button + text "Searching…" + pulsing header text
- **Error State**: Banner with error + retry button
- **Interaction Model**:
  - Type → submit → wait → see answer + sources
  - Suggested question buttons pre-fill + auto-submit
  - Graceful degradation when vector index unavailable
- **Polish Level**: **7/10**
  - Suggested questions provide good UX onboarding
  - Error messaging includes setup hints (PINECONE_API_KEY)
  - Could use more feedback during search (vs. just "Searching…")
- **Key Insight**: Shows how to make AI search discoverable (suggested Q's break writer's block)

#### 16. **TeamRiskSummaryBanner.tsx**
- **UX Pattern**: Aggregated risk signals across all team members
  - Per-plan `useRiskSignals` calls (no hooks-in-loop violation)
  - Renders `<PlanRiskRow>` for each team member
  - Shows member name + signal type + rationale
- **Loading State**: Returns `null` per row during load (no flash)
- **Error State**: Silently degrade (return `null`)
- **Interaction Model**: Read-only; called from Team Week top banner
- **Polish Level**: **7/10**
  - Architecture solves the "hooks in loop" problem elegantly
  - Clean filtering for critical signals only
  - Per-member organization is helpful
- **Key Insight**: Teaches pattern for parallelizing hook calls without violating rules-of-hooks

#### 17. **WhatIfPanel.tsx**
- **UX Pattern**: Interactive mutation queuer → simulation → impact analysis
  - Expandable/collapsible panel
  - Three mutation types: ADD_COMMIT, REMOVE_COMMIT, MODIFY_COMMIT
  - Shows queued mutations as badges
  - Simulation results: capacity delta, risk delta, RCDO coverage changes, AI narrative
- **Loading State**: "Simulating…" button text + spinner
- **Error State**: Inline error banner
- **Interaction Model**:
  - Action tabs (+ Add, − Remove, ✎ Modify)
  - Context-sensitive input fields per action type
  - Queue button builds mutation list
  - Simulate button submits to backend
  - Results show before/after + AI insight (when available)
- **Polish Level**: **8/10**
  - Multi-step mutation UI is well-organized
  - Simulation always works (rule-based logic doesn't need LLM)
  - AI narrative gracefully optional (shown only if non-null)
  - Visual indicators (colors) for risk changes (danger, success)
- **Key Insight**: Shows how to do complex "what-if" without requiring AI (simpler, faster, deterministic)

---

### **API Layer** (aiApi.ts, aiHooks.ts, ragApi.ts, ragHooks.ts, whatIfApi.ts)

#### **aiApi.ts** — AI Endpoint Definitions
- **Architecture**: Factory function `createAiApi(client, userId)` → object of methods
- **Endpoints**:
  - `getStatus()` — provider check
  - `commitFromFreeform()` — Phase 1 of composer
  - `rcdoSuggest()` — smart RCDO linking
  - `commitDraftAssist()` — field-level suggestions
  - `commitLint()` — code quality hints (hard + soft)
  - `getRiskSignals()` — signal retrieval
  - `getTeamAiSummary()` — manager prose
  - `recordFeedback()` — thumbs up/down logging
  - `reconcileAssist()` — outcome + carry-forward suggestions
- **Polish Level**: **9/10**
  - Comprehensive type safety (TypeScript interfaces for every request/response)
  - Consistent error handling (all return `aiAvailable` boolean)
  - Non-fatal failures (e.g., RCDO suggest doesn't block draft creation)
- **Key Insight**: Every response includes `aiAvailable` boolean — components can gracefully degrade

#### **aiHooks.ts** — React Query Wrappers
- **Architecture**: Stable API instances + `useQuery` wrappers + mutation hooks
- **Notable Hooks**:
  - `useAiStatus()` — polls provider status
  - `useRiskSignals()` — fetch with enabled guard
  - `useManagerAiSummary()` — team-level summary
  - `useAutoReconcileAssist()` — fires once when plan enters RECONCILING state
  - `useWhatIfApi()` — mutation hook for simulations
- **Polish Level**: **9/10**
  - Proper memoization to prevent infinite re-renders
  - Guards on `enabled` flag (don't fetch if planId is null)
  - Auto-reconcile uses ref-based tracking to prevent duplicate calls
- **Key Insight**: `useAutoReconcileAssist` is sophisticated — fires once on RECONCILING state, tracks via ref

#### **ragApi.ts** — RAG Endpoints
- **Endpoints**:
  - `submitQuery()` — semantic search
  - `getTeamInsights()` — team insight cards
  - `getPlanInsights()` — personal insight cards
- **Polish Level**: **8/10** — Clean, minimal, proper URL encoding

#### **ragHooks.ts** — RAG Query Hooks
- **Hooks**:
  - `useTeamInsights()` / `usePlanInsights()` — query wrappers
  - `useSemanticQuery()` — mutation hook for free-form searches
- **Polish Level**: **8/10** — Consistent with aiHooks patterns

#### **whatIfApi.ts** — Simulation Endpoints
- **Architecture**: Single `simulate()` endpoint (deterministic, rule-based)
- **Response**: Before/after snapshots + capacity/risk deltas + AI narrative (optional)
- **Polish Level**: **8/10** — Clean; narrative is optional (doesn't block if AI is down)

---

### **Utilities**

#### **useAiMode.ts**
- **UX Pattern**: Global preference for AI suggestion intensity
  - `'full'` (default): sections auto-run
  - `'on-demand'`: sections require explicit trigger
  - Persists to localStorage
- **Polish Level**: **7/10** — Useful but undiscovered (no UI to toggle visible in codebase)
- **Key Insight**: Supports different user preferences without code branching

#### **useDismissMemory.ts**
- **UX Pattern**: Tracks dismissals; auto-collapse after 3 dismisses
  - Stores count in localStorage
  - Returns `shouldAutoCollapse` + `recordDismiss` callback
- **Polish Level**: **6/10** — Functional but not integrated (no components use it yet)
- **Key Insight**: Teaches "respect user attention" pattern (don't keep forcing dismissed UI)

---

## UX Pattern Summary

### **Polished Patterns** (8-9/10)
1. **Two-phase wizards** (Commit Composer) — separate discovery from confirmation
2. **Evidence transparency** (Evidence Drawer) — every datum traceable to source
3. **Diffed suggestions** (Draft Assist) — show current vs. new side-by-side
4. **Proactive alerts** (Risk Banners) — persistent, colored, actionable hints
5. **Confidence visualization** (Query Card) — bar + label + percentage
6. **Feedback loops** (AiFeedbackButtons) — ubiquitous 👍/👎 on every suggestion
7. **Graceful degradation** — all surfaces handle AI unavailable elegantly
8. **Debounced auto-fetch** (RCDO Suggestion, Lint) — respects user pace

### **Basic/Utilitarian Patterns** (5-6/10)
1. **Risk signal cards** — minimal styling, dense information
2. **Inline error messages** — text-only, no visual hierarchy
3. **Skeleton loaders** — basic shimmer bars (not contextual)

### **State Management Excellence**
- **All components properly handle**: loading, error, AI unavailable, empty state
- **Non-blocking failures** — lint failures don't block composer
- **Debounce guards** — auto-run respects user pace (800ms+ delays)
- **Disable states** — buttons disable when invalid (e.g., empty title)
- **Feedback state** — suggestions track acceptance with badges

---

## Loading/Error States Taxonomy

### **Loading States**
| Component | Pattern | Quality |
|-----------|---------|---------|
| Composer | Spinner + button text change | ✓ Clear |
| Lint | Shimmer skeleton (3 bars) | ✓ Contextual |
| Insights | 3 skeleton cards | ✓ Contextual |
| Manager Summary | Card skeleton | ✓ Contextual |
| Query Answer | None (pre-fetched) | ✓ N/A |
| Risk Signals | Skeleton cards | ✓ Contextual |
| Risk Banners | Returns `null` (no flash) | ✓ Smart |
| What-If | Spinner in button | ✓ Clear |
| RCDO Suggestion | Pulsing text + spinner | ~ Could be more refined |

### **Error States**
| Component | Pattern | Blocks User? |
|-----------|---------|------------|
| Composer | Inline message + manual fallback | ✗ No |
| Lint | Inline error + retry button | ✗ No |
| Insights | Inline error message | ✗ No |
| Query Answer | Error banner + retry | ✗ No |
| What-If | Banner error + retry | ✗ No |
| Feedback | Silent failure (non-critical) | ✗ No |

**Pattern**: All errors are non-blocking; user can always fallback to manual entry or retry.

---

## Interaction Model: User Intent Flows

### **Suggestion Acceptance Flow**
```
User Action          → Component State     → Result
─────────────────────────────────────────────────────
Click "Generate"     → Loading            → Show spinner
                     → Result            → Show draft
Click "Accept"       → Accepting          → Show loader
                     → Submitted          → Callback fires
[User dismisses]     → Dismissed          → Hide suggestion
```

### **Auto-Run Flow** (Lint, RCDO Suggestion)
```
Component mount      → Check isAvailable
                     → Check planId exists
                     → Auto-fetch (debounced)
Data arrives         → Show results
User edits           → Reset debounce
                     → Auto-fetch after 800ms
[User dismisses]     → Skip future auto-fetch (reset on title change)
```

### **Proactive Alert Flow** (Risk Banners)
```
Plan locks           → Parent calls ProactiveRiskBanner
                     → Component fetches signals
                     → Filters to 3 critical types
                     → Returns null if none
                     → Renders banners if any
User sees alert      → Can click feedback buttons
                     → Alert persists (non-dismissible)
```

---

## What's Polished vs. What's Basic

### **Polished** (Investment Made)
✅ **Commit Composer** — Two-phase UX, parallel API calls, chess piece limits, pre-fill on fallback  
✅ **AI Lint Panel** — Auto-run, debounce, refresh button, hint count callback  
✅ **Evidence Drawer** — Collapsible panes, SQL facts grid, lineage chain, semantic matches  
✅ **Feedback Buttons** — Atomic, reusable, non-blocking, visual confirmation  
✅ **Risk Banners** — Color-coded by type, action hints, smart filtering  
✅ **What-If Planner** — Multi-step mutation UI, before/after snapshots  
✅ **Answer Renderer** — Custom markdown parser, chess badges, metrics highlighting  

### **Basic/Minimal** (Shipped but Utilitarian)
⚠️ **Risk Signals Panel** — Dense grid, minimal styling, no feedback buttons  
⚠️ **RCDO Suggestion Inline** — Just badge + rationale + buttons (could show more context)  
⚠️ **Semantic Search** — Works well but limited affordance discovery (only "suggested questions")  
⚠️ **Dismiss Memory** — Code exists but no UI toggle for `useAiMode`  

### **Not Yet Polished** (Needs Work)
❌ **Skeleton loaders** — Generic bars; could be more contextual (e.g., insight card shapes)  
❌ **Error messages** — Text-only; could have icons + retry affordances on all  
❌ **Confirmation modals** — Some actions lack "are you sure?" for high-risk changes  
❌ **Aria labels** — Some buttons lack descriptive aria-labels  

---

## Overall AI UX Sophistication Assessment

### **Strengths** (Why it feels enterprise)
1. **Trust through transparency** — Evidence drawer, feedback loops, rationale displays
2. **Assistive not authoritarian** — No auto-execution; every suggestion requires explicit accept
3. **Graceful degradation** — Components work without AI; partial failures don't block
4. **Smart state handling** — Debounces, guards, memoization prevent thrashing
5. **Reusable primitives** — AiFeedbackButtons used by 10+ components
6. **Proper error boundaries** — No crashes; silent failures on non-critical operations
7. **Confidence signals** — Visual bars, percentages, badges indicate AI certainty
8. **Accessibility** — Proper ARIA labels, semantic HTML, color + icon combos

### **Weaknesses** (What needs polish)
1. **Inconsistent loading states** — Some use skeletons, some use spinners, some hide during load
2. **Visual hierarchy** — Risk signals panel is dense; insights are spaced; no consistent pattern
3. **Mobile-first design** — No evidence of responsive breakpoints; assumes desktop widths
4. **Undo/redo missing** — No way to revert AI suggestions after acceptance
5. **Batch operations** — Suggesting one field at a time; could batch multiple suggestions
6. **Discoverability** — `useAiMode` toggle, `useDismissMemory` not surfaced in UI
7. **Onboarding** — No tooltips explaining AI sections on first visit
8. **Comparative views** — No side-by-side before/after for complex suggestions

### **Rating Rubric**
- **9-10/10**: Commit Composer, Evidence Drawer, Risk Banners, Lint Panel
- **7-8/10**: What-If Planner, Insights, Manager Summary, Semantic Search, RCDO Suggestion
- **5-6/10**: Risk Signals Panel, Query Card (solid but minimal)
- **Overall**: **7/10** — Sophisticated core, uneven polish at edges

---

## Recommendations

### **Quick Wins** (1-2 weeks)
1. **Consistent skeleton loaders** — Use context-aware shapes (card outlines, not generic bars)
2. **UI toggle for `useAiMode`** — Add settings panel to expose "Full" vs. "On-Demand" preferences
3. **Feedback button improvements** — Add loading state + error toast on feedback failure
4. **ARIA labels** — Audit all buttons for proper `aria-label` coverage

### **Medium-Effort** (2-4 weeks)
1. **Undo stack** — Track accepted suggestions with dismissible undo for 5 seconds
2. **Comparative layouts** — Show before/after diffs for multi-field suggestions
3. **Responsive breakpoints** — Test on tablet/mobile; adjust Evidence Drawer grid
4. **Error toast library** — Replace inline errors with toast notifications (better UX for transient errors)
5. **Onboarding tooltips** — First-time users see "What is this AI section?" on hover

### **Strategic** (4+ weeks)
1. **AI mode switch per component** — Some users want full auto-run, others want on-demand per section
2. **Suggestion confidence ranking** — Show highest-confidence suggestions first (not random order)3. **Batch suggestion UI** — Group related suggestions (e.g., all title/description changes together)
4. **Narrative generation for risk banners** — Extend ProactiveRiskBanner with LLM explanation
5. **A/B testing framework** — Test "auto-run" vs. "on-demand" cohorts to measure engagement

---

## Code Quality Observations

### **Excellent Patterns**
```tsx
// ✅ Proper error boundary with fallback
if (!response.aiAvailable) {
  setError("AI is currently unavailable. Use the manual form.");
  return;
}

// ✅ Debounce with timer ref
useEffect(() => {
  if (timerRef.current) clearTimeout(timerRef.current);
  timerRef.current = setTimeout(() => {
    // fetch
  }, DEBOUNCE_MS);
}, [dep]);

// ✅ Non-blocking failure
const [rcdoResponse] = await Promise.all([
  aiApi.commitFromFreeform(...),
  aiApi.rcdoSuggest(...).catch(() => ({ aiAvailable: false })),
]);

// ✅ Graceful degradation
if (loading) return null; // No flash for proactive banners
if (error || !data?.aiAvailable) return null;
if (criticalSignals.length === 0) return null;
```

### **Minor Issues**
- Some components use `void mutate()` instead of `.then()` for promise side effects
- A few error messages could be more specific ("Failed to generate commit" → which part failed?)
- No request deduplication (same request made twice can both fire)

---

## Conclusion

**This is a well-engineered AI UX surface.** The architecture prioritizes trust, transparency, and graceful degradation. Components are reusable, state is carefully managed, and the "never auto-submit" principle is consistently applied. 

The main gap is **uneven polish** — some surfaces (Composer, Lint, Evidence) are refined; others (Risk Signals, basic inline suggestions) are utilitarian. With targeted investment in visual consistency, loading states, error messaging, and discoverability, this could easily reach **8.5-9/10 sophistication**.

The codebase is also **production-ready** — error handling is comprehensive, accessibility is considered, and there are no obvious crashes or security issues. The team clearly understands AI UX best practices.

**Most impressive**: The Evidence Drawer and Feedback Button patterns. These should be industry examples for "trustworthy AI UI."
