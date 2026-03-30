# Goal

Systematic bug sweep across the entire Weekly Commit codebase (frontend + backend). Fix all remaining `bg-neutral-*` dark-mode contrast violations, raw UUID displays, missing UX affordances, and minor logic gaps discovered after the initial polish pass. All changes must leave the project in a passing state (`static` + `unit` checks) after each step.

The app is a React/TypeScript frontend (Module Federation) + Spring Boot 3 / Java 21 backend with PostgreSQL. The design system uses Tailwind CSS with semantic color tokens (`bg-foreground/*`, `bg-danger/5`, `text-muted`, etc.) that work in both light and dark mode. Hard-coded `bg-neutral-*` and `text-neutral-*` classes are broken in dark mode — they render near-white, making text invisible against dark backgrounds.

## Constraints

- Do NOT modify `backend/src/main/resources/db/migration/V*.sql` files (except V11, which is the demo seed — see Step 1 note).
- Do NOT modify `.pi/` directory contents.
- Do NOT change any public API contracts (REST endpoints, DTO field names/types) — frontend and backend must stay in sync.
- All TypeScript must compile with `npx tsc --noEmit` (zero errors). All Java must compile with `./gradlew compileJava`.
- Checks: `static` = `npm run check:static` (runs from repo root, covers both TS typecheck and Java compile). `unit` = `npm test` (runs Vitest frontend suite + Gradle backend unit tests).
- After every step, run `static` + `unit` and fix any regressions before moving on.

---

## Steps

### Step 1: Sweep remaining `bg-neutral-*` dark-mode violations (frontend)

Fix all remaining hard-coded `bg-neutral-*` / `text-neutral-*` class usages in frontend components that render visible backgrounds or text — these appear white/invisible in dark mode.

Files to audit and fix (all in `frontend/src/`):
- `components/lock/ScopeChangeTimeline.tsx` — `bg-neutral-100` on `event.previousValue` span; `bg-neutral-*` category dot colors
- `components/lock/ScopeChangeDialog.tsx` — `bg-neutral-50` info banner, `bg-neutral-100` remove-preview box, `bg-neutral-100` diff table cell
- `components/lock/LockConfirmDialog.tsx` — `bg-neutral-200` chess piece count badge
- `components/myweek/CarryForwardLineageView.tsx` — outcome badges: `bg-neutral-200`, `bg-neutral-300`, `bg-neutral-100`
- `components/myweek/CommitList.tsx` — `bg-neutral-100` carry-forward streak row
- `components/reconcile/CarryForwardDialog.tsx` — `bg-neutral-50` commit preview box
- `components/tickets/TicketForm.tsx` — `bg-neutral-100` submit error alert
- `components/tickets/TicketDetailView.tsx` — `bg-neutral-100` BLOCKED/CANCELED status badges
- `components/ai/CommitDraftAssistButton.tsx` — `hover:bg-neutral-100` on ghost buttons
- `components/ai/RcdoSuggestionInline.tsx` — `hover:bg-neutral-100` on ghost buttons
- `components/ai/WhatIfPanel.tsx` — `bg-neutral-100` info box
- `components/ai/EvidenceDrawer.tsx` — `bg-neutral-200` outcome badge
- `components/ai/ReconcileAssistPanel.tsx` — `hover:bg-neutral-100` ghost button
- `components/notifications/NotificationPanel.tsx` — `bg-neutral-100` MEDIUM notification background

**Replacement rules:**
- `bg-neutral-50` / `bg-neutral-100` (light background surfaces) → `bg-foreground/5` or `bg-foreground/8`
- `bg-neutral-200` (medium tint badges) → `bg-foreground/12` or `bg-foreground/15`
- `bg-neutral-300` / `bg-neutral-400` (darker tint) → `bg-foreground/20` or `bg-foreground/25`
- `bg-neutral-500+` (dark dots, used as colored timeline markers) — LEAVE THESE AS-IS; they are intentionally dark and render correctly in both modes
- `text-neutral-600` / `text-neutral-700` (muted text on light bg) → `text-muted`
- `text-neutral-800` (near-black on light bg) → `text-foreground`
- `border-neutral-200` / `border-neutral-300` → `border-border` or `border-foreground/20`
- `hover:bg-neutral-100` on interactive elements → `hover:bg-foreground/8`

Do NOT touch `bg-neutral-800` / `bg-neutral-600` / `bg-neutral-700` / `bg-neutral-500` in ScopeChangeTimeline — these are colored category dots that render fine.

Do NOT modify any backend files in this step.
Do NOT modify any test files in this step (fix tests only if `unit` check fails after the component fixes).

**Checks:** `static`, `unit`

---

### Step 2: Fix raw UUID display in Tickets route and TicketDetailView (frontend)

Several places still show raw user UUIDs instead of human-readable names.

**2a — TicketListView / Tickets route (`frontend/src/routes/Tickets.tsx`, `frontend/src/components/tickets/TicketListView.tsx`):**
The Tickets page fetches tickets but displays `ticket.assigneeUserId` (UUID) in the assignee column. Fix by:
1. In `Tickets.tsx`, fetch the team members list using `useTeamMembers(teamId)` (already exists in `frontend/src/api/teamHooks.ts`).
2. Build a `userId → displayName` map and pass it as a `memberNames` prop to `TicketListView`.
3. In `TicketListView`, accept and use `memberNames?: Record<string, string>` to display the name (fall back to UUID if not found).

**2b — TicketDetailView assignee (`frontend/src/components/tickets/TicketDetailView.tsx`):**
The detail panel shows the assignee UUID. Accept the same `memberNames` prop and display the resolved name in the assignee field.

**2c — QuickAssign input in UncommittedWorkSection (`frontend/src/components/teamweek/UncommittedWorkSection.tsx`):**
The quick-assign input currently asks for a raw "User ID" string. Replace it with a `<select>` dropdown populated from the `memberNames` map (already plumbed into the component). If `memberNames` is empty/absent, fall back to the current text input.

Do NOT modify any backend files in this step.

**Checks:** `static`, `unit`

---

### Step 3: Fix remaining UUID displays in manager/comment surfaces (frontend)

**3a — ExceptionQueueSection resolved-by field:**
When an exception is resolved, the detail view shows `resolvedById` (UUID). The `ExceptionResponse` type already has `displayName` for the affected user. Add a `resolvedByName` field to `ExceptionResponse` Java record and populate it in `ManagerReviewService.resolveException()` and `getExceptionQueue()` by looking up `resolvedById` when present. Update the frontend `ExceptionResponse` TypeScript interface and display `resolvedByName ?? resolvedById` in the resolution block.

**3b — ManagerComment authorUserId:**
In `frontend/src/components/teamweek/ExceptionQueueSection.tsx` or wherever manager comments are displayed (check `CommentResponse` usage across the codebase), if `authorUserId` is rendered raw, resolve it using the member names map or a `displayName` field on the DTO.

**3c — Backend: add `resolvedByName` to `ExceptionResponse.java`:**
- Add `String resolvedByName` field (nullable) to the `ExceptionResponse` record.
- In `ManagerReviewService.getExceptionQueue()`, batch-fetch display names for all `resolvedById` values (similar to how `displayName` was added for `userId`).
- In `ManagerReviewService.resolveException()`, look up the resolver's display name and include it.
- Update `TeamControllerTest.java` test fixtures to include the new field.

**Scope:** Only `ExceptionResponse.java`, `ManagerReviewService.java`, `TeamControllerTest.java` on the backend. Only `teamTypes.ts` and `ExceptionQueueSection.tsx` on the frontend.
Do NOT modify other backend services or frontend components.

**Checks:** `static`, `unit`

---

### Step 4: Fix Reconcile route week sync and other route-level week isolation (frontend)

**4a — Reconcile route:**
Check `frontend/src/routes/Reconcile.tsx` — if it has its own local week offset state (similar to the bug fixed in TeamWeek), replace it with `useSelectedWeek()` from `frontend/src/lib/WeekContext.tsx` so it stays in sync with the header week selector.

**4b — Reports route:**
Check `frontend/src/routes/Reports.tsx` — it uses `weeksAgo(rangeWeeks - 1)` as a rolling window, which is correct. But verify the range selector controls are clearly labeled so users understand they're viewing a multi-week historical range (not a single selected week). If the range controls are confusing, add a brief clarifying label (e.g. "Showing last N weeks of history").

**4c — Tickets route week filter:**
Check `frontend/src/routes/Tickets.tsx` — if there is a week filter, ensure it reads from `useSelectedWeek()` as the default rather than computing its own "current week."

**Scope:** Only files in `frontend/src/routes/`. Do NOT modify backend files or non-route frontend files.

**Checks:** `static`, `unit`

---

### Step 5: RCDO node status UX improvements (frontend)

**5a — Show status transition hint in tree view:**
In `frontend/src/components/rcdo/RcdoTreeView.tsx`, DRAFT nodes are hard to identify at a glance. Add a subtle visual indicator (e.g. a small `DRAFT` label in muted text, or a different opacity) to DRAFT nodes in the tree list, so users know which nodes need activation.

**5b — Activate on create option:**
In `frontend/src/routes/Rcdos.tsx`, after a node is successfully created (in `handleCreate`), if the newly created node is a `DRAFT`, offer an inline toast/prompt or auto-activate flow. Specifically: after `handleCreate` resolves, check if the created node's status is `DRAFT` and if the user has permission to activate — if so, automatically call `api.activateNode(created.id)` before calling `refetch()`. This removes the need for a separate "Activate" click for the common case.

**5c — Status filter default:**
In `frontend/src/routes/Rcdos.tsx`, change the default `statusFilter` from `"all"` to `"active-only"` so the tree defaults to showing only active nodes (which is what's relevant for commit linking). Add a note in the filter labels to make `"all"` obvious.

**Scope:** Only `frontend/src/routes/Rcdos.tsx` and `frontend/src/components/rcdo/RcdoTreeView.tsx`. Do NOT modify backend files.

**Checks:** `static`, `unit`

---

### Step 6: Fix plan intelligence section and AI lint visibility edge cases (frontend)

**6a — Plan Intelligence section empty state:**
In `frontend/src/routes/MyWeek.tsx`, the "Plan Intelligence" `CollapsibleSection` is rendered whenever `aiAssistanceEnabled && plan` — even when the plan is RECONCILED (no lint, no insights needed). Add a condition to only show it for DRAFT and LOCKED plans.

**6b — WhatIfPanel visibility:**
Already gated on `isDraft || isLocked` — verify this is correct and the section title makes sense for LOCKED plans where commits can't be freely changed.

**6c — AiLintPanel in Plan Intelligence:**
The lint panel only shows for `isDraft && commits.length > 0`. For LOCKED plans, commits exist but drafting new ones is via scope-change. Verify the lint panel is not shown for LOCKED (it shouldn't be — scope-change audit is different from lint).

**6d — ProactiveRiskBanner visibility:**
Currently only shown for `isLocked`. Verify it is NOT shown when `riskSignalCount === 0` (there is a `riskSignalCount !== 0` guard but check it handles the initial `null` state — it should not flash before the count resolves).

**Scope:** Only `frontend/src/routes/MyWeek.tsx`. Do NOT touch any other files unless a TypeScript error forces it.

**Checks:** `static`, `unit`

---

### Step 7: Backend — fix ManagerAiSummaryService context completeness (backend)

The `ManagerAiSummaryService.getSummary()` was partially fixed to include `historicalCommits` and `planData`. Complete the fix:

**7a — Member-level breakdown in historicalCommits:**
Currently, `historicalCommits` includes `ownerUserId` as a UUID string. Replace the UUID with the user's `displayName` (look up via `userRepo.findAllById(ownerByPlanId.values())` in batch) so the AI prompt refers to people by name (e.g. "Alice Chen: KING commit...").

**7b — Chess distribution in additionalContext:**
The prompt template expects `additionalContext.chessDistribution` (e.g. `{KING: 4, QUEEN: 2, ...}`) but the service currently only sends `topRcdoBranches`, `carryForwardPatterns`, etc. Add the chess distribution map to `additionalCtx`.

**7c — missedLocks/missedReconciles accuracy:**
The current `missedLocks` calculation can go negative (if `plans.size() > memberships.size()`). Fix with `Math.max(0, ...)`.

**Scope:** Only `backend/src/main/java/com/weeklycommit/ai/service/ManagerAiSummaryService.java`. Do NOT modify any other backend files. Do NOT modify any frontend files.

**Checks:** `static`, `unit`

---

### Step 8: Frontend — fix notification panel and minor AI component polish (frontend)

**8a — NotificationPanel UUID display:**
In `frontend/src/components/notifications/NotificationPanel.tsx`, check if any notification fields render raw UUIDs (e.g. actor or target user IDs). If so, resolve them to names using the same pattern (accept a `memberNames` prop or read from context).

**8b — InsightPanel empty state:**
In `frontend/src/components/ai/InsightPanel.tsx`, when AI is unavailable or returns no insights, ensure the empty state is clearly labeled rather than rendering a blank collapsed section. Add a brief "No insights available" message within the section body when `insights.length === 0` and not loading.

**8c — SemanticSearchInput placeholder improvement:**
In `frontend/src/components/ai/SemanticSearchInput.tsx`, the placeholder text and example queries are hardcoded. Verify they make sense for the current context (team week vs personal). The component already accepts `mode` prop — ensure example queries differ between modes.

**8d — AiCommitComposer RCDO picker search:**
In `frontend/src/components/ai/AiCommitComposer.tsx`, the RCDO tree picker shown in Phase 2 uses `searchQuery=""` (hardcoded empty). Add a small search input above the tree picker so users can filter nodes by name when selecting manually.

**Scope:** Only files in `frontend/src/components/ai/` and `frontend/src/components/notifications/`. Do NOT modify route files or backend.

**Checks:** `static`, `unit`

---

### Step 9: Sweep and verify all test files compile and pass (frontend + backend)

After all prior steps, do a final pass:

1. Run `npx tsc --noEmit` in `frontend/` and fix any remaining TypeScript errors (there may be test files that reference old interfaces).
2. Run `npx vitest run` and fix any failing tests.
3. Run `./gradlew test` in `backend/` and fix any failing Java tests.
4. If any test fixtures still hard-code UUIDs where display names are now expected, update the fixtures.
5. Do NOT add new features or fix bugs beyond what's needed to make all checks pass.

**Scope:** Any file that fails `static` or `unit` checks. Prefer minimal targeted fixes over rewriting tests.

**Checks:** `static`, `unit`
