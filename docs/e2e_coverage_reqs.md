# E2E Test Coverage Requirements

> **Status: ✅ COMPLETED** — All 11 work units implemented and quality-fixed (2026-03-31).
>
> **Original purpose:** Self-contained reference for parallel pi instances to write comprehensive E2E tests.  
> Each section was an independent work unit assigned to parallel pi instances.

## Completion Summary

| Work Unit | File | Tests | Status |
|-----------|------|-------|--------|
| 1. Admin Page | `e2e/admin.spec.ts` | 20 | ✅ Done |
| 2. Reconcile Deep | `e2e/reconcile-deep.spec.ts` | — | Covered by existing `reconcile.spec.ts` + `integration-flows.spec.ts` |
| 3. Tickets CRUD | `e2e/tickets-deep.spec.ts` | 29 | ✅ Done |
| 4. RCDOs CRUD | `e2e/rcdos-deep.spec.ts` | 22 | ✅ Done |
| 5. Reports Deep | `e2e/reports-deep.spec.ts` | 20 | ✅ Done |
| 6. Commit CRUD Deep | `e2e/commit-deep.spec.ts` | 20 | ✅ Done |
| 7. Team Dashboard Deep | `e2e/team-deep.spec.ts` | 26 | ✅ Done |
| 8. AI Interactions Deep | `e2e/ai-deep.spec.ts` | 29 | ✅ Done |
| 9. Error & Edge Cases | `e2e/error-states.spec.ts` | 21 | ✅ Done |
| 10. Scope/Lock Deep | `e2e/scope-lock-deep.spec.ts` | 18 | ✅ Done |
| 11. Integration Flows | `e2e/integration-flows.spec.ts` | 5 | ✅ Done |

**Total: 282 tests across 24 spec files** (up from 72 tests / 14 files).

New page objects: `AdminPage`, `TicketsPage`, `RcdosPage`, `ReportsPage`.  
Shared helpers: `e2e/helpers/week-helpers.ts` (`goToCleanDraftWeek`, `mockDraftPlan`, `addCommitWithRcdo`, etc.).

---

## Architecture & Conventions

### Stack
- **Frontend:** React 18 + TypeScript + Vite (port 5173)
- **Backend:** Spring Boot (port 8080) with PostgreSQL + dev seed data
- **E2E Framework:** Playwright (chromium only, serial execution)
- **Config:** `e2e/playwright.config.ts` — baseURL `http://localhost:5173`, workers=1

### File Conventions
- Spec files: `e2e/<feature>.spec.ts`
- Page objects: `e2e/pages/<PageName>.ts`
- Fixtures: `e2e/fixtures/`
- Tests use `data-testid` selectors exclusively (see Appendix A)

### Existing Page Objects
```
e2e/pages/MyWeekPage.ts   — goto(), addCommit(), lockPlan(), expectDraftState()
e2e/pages/ReconcilePage.ts — goto(planId), openReconciliation(), setOutcome(), submitReconciliation()
e2e/pages/TeamPage.ts     — goto(), switchToTab()
```

### Mock Pattern for AI Endpoints
All AI tests use `page.route()` to mock API responses. See `e2e/ai-flows.spec.ts` for the canonical pattern with `mockAiEndpoints(page)`.

### Key Rules
1. **Never use `waitForTimeout()` for assertions** — use `waitFor()` or `expect().toBeVisible()`
2. **Don't silently pass** — avoid `if (await el.isVisible().catch(() => false))` as the sole assertion
3. **Each test must assert something concrete** — a visible element, text content, a URL change, or a count
4. **Use page objects** for repeated interactions — create new ones as needed
5. **Mock API routes** when testing error/edge states — use `page.route()` to return 500s, empty arrays, etc.
6. **Seed data assumption:** Backend runs with dev seed data providing a team, users, RCDO hierarchy, and some tickets

### Frontend Routes
| Route | Page Component | `data-testid` |
|-------|---------------|---------------|
| `/weekly/my-week` | MyWeek | `page-my-week` |
| `/weekly/reconcile` | Reconcile | `page-reconcile` |
| `/weekly/reconcile/:planId` | Reconcile | `page-reconcile` |
| `/weekly/team` | TeamWeek | `page-team-week` |
| `/weekly/team/:teamId` | TeamWeek | `page-team-week` |
| `/weekly/tickets` | Tickets | `page-tickets` |
| `/weekly/rcdos` | Rcdos | `page-rcdos` |
| `/weekly/reports` | Reports | `reports-page` |
| `/weekly/admin` | Admin | `admin-page` |

---

## Current Coverage (72 tests)

| File | Tests | Depth |
|------|-------|-------|
| `golden-path.spec.ts` | 8 | Page renders, basic nav |
| `navigation.spec.ts` | 4 | Header, sidebar collapse |
| `week-navigation.spec.ts` | 6 | Prev/next/today week |
| `commit-crud.spec.ts` | 5 | Create + delete only |
| `lifecycle.spec.ts` | 3 | DRAFT→LOCKED happy path |
| `validation.spec.ts` | 4 | Panel opens, no real validation |
| `scope-change.spec.ts` | 3 | Button visibility only |
| `ai-flows.spec.ts` | 9 | Mocked presence checks |
| `manager-flow.spec.ts` | 4 | Tab rendering |
| `team-dashboard.spec.ts` | 9 | Tab rendering |
| `reconcile.spec.ts` | 6 | Shallow structure checks |
| `rcdos.spec.ts` | 5 | Just "tree renders" |
| `tickets.spec.ts` | 4 | Just "page renders" |
| `reports.spec.ts` | 2 | Just "page loads" |

---

## WORK UNIT 1: Admin Page (NEW FILE: `e2e/admin.spec.ts`)

**Current coverage: 0 tests. Target: ~18 tests.**

The Admin page (`/weekly/admin`, testid `admin-page`) has two sections:

### 1A. Org Config Section (`org-config-section`)
Loads org-level cadence configuration from `GET /api/config/org`.

**Happy Path Tests:**
- [ ] Admin page renders with heading "Administration" and settings icon
- [ ] Org config section loads and displays current values
- [ ] Can change week start day via select (`org-week-start-day`) — verify dropdown has all 7 days
- [ ] Can change timezone via select (`org-timezone`) — verify dropdown lists major timezones
- [ ] Can change default weekly budget via number input (`org-budget`) — type new value, verify input reflects it
- [ ] Can change cadence deadlines via day+time pickers (`org-draft-offset-day`, `org-draft-offset-time`, `org-lock-offset-day`, `org-lock-offset-time`, `org-reconcile-open-offset-day`, `org-reconcile-open-offset-time`, `org-reconcile-due-offset-day`, `org-reconcile-due-offset-time`)
- [ ] Click "Save Configuration" (`org-save-btn`) → "Saved" indicator (`org-saved-indicator`) appears
- [ ] "Saved" indicator disappears after ~3 seconds

**Sad Path Tests:**
- [ ] Mock `GET /api/config/org` to return 500 → error message displays with AlertTriangle
- [ ] Mock `PUT /api/config/org` to return 500 → error message displays, form retains values

### 1B. Team Config Section (`team-config-section`)
Expandable panel for team-level overrides from `GET /api/config/teams/{teamId}`.

**Happy Path Tests:**
- [ ] Team config section is collapsed by default
- [ ] Click header expands the section (shows effective values)
- [ ] Effective values show in ConfigValueCards (week start, lock due, budget, timezone)
- [ ] Badge shows "Using org defaults" when no team override exists (`hasTeamOverride=false`)
- [ ] Can enter budget override (`team-budget-override`)
- [ ] Can enter lock due override (`team-lock-override`)
- [ ] Can select timezone override (`team-timezone-override`) — "Inherit from org" option resets
- [ ] Click "Save Overrides" (`team-save-btn`) → success

**Sad Path Tests:**
- [ ] Mock `GET /api/config/teams/{id}` to 500 → error in expanded section
- [ ] Mock `PUT /api/config/teams/{id}` to 500 → error message, form retains values

### Key `data-testid` Values
```
admin-page, org-config-section, team-config-section,
org-week-start-day, org-timezone, org-budget, org-save-btn, org-saved-indicator,
org-draft-offset-day, org-draft-offset-time, org-lock-offset-day, org-lock-offset-time,
org-reconcile-open-offset-day, org-reconcile-open-offset-time,
org-reconcile-due-offset-day, org-reconcile-due-offset-time,
team-budget-override, team-lock-override, team-timezone-override, team-save-btn
```

### API Endpoints to Mock (for error tests)
```
GET  /api/config/org
PUT  /api/config/org         (body: OrgConfigRequest)
GET  /api/config/teams/{id}
PUT  /api/config/teams/{id}  (body: TeamConfigOverrideRequest)
```

---

## WORK UNIT 2: Reconcile Deep Tests (NEW FILE: `e2e/reconcile-deep.spec.ts`)

**Current coverage: 6 shallow tests. Target: ~25 additional tests.**

The Reconcile page (`/weekly/reconcile`) is the most complex user flow. The existing `reconcile.spec.ts` only checks that the page renders. These tests cover the full reconciliation workflow.

**Prerequisites:** Each test that needs a RECONCILING plan should either:
- Navigate to My Week, add commits, lock, then go to reconcile and open reconciliation
- Or mock the reconcile API response via `page.route()`

### 2A. State Machine & Navigation
- [ ] When plan is DRAFT, reconcile page shows "No plan available" message with link to My Week
- [ ] When plan is LOCKED, shows locked prompt card (`reconcile-locked-prompt`) with "Open Reconciliation" button
- [ ] Click "Open Reconciliation" (`open-reconciliation-btn`) → state transitions to RECONCILING, commit list appears
- [ ] Reconcile page auto-discovers current week's plan when no planId in URL
- [ ] Direct URL `/weekly/reconcile/{planId}` loads the correct plan
- [ ] Loading state shows `reconcile-loading` while fetching

### 2B. Outcome Selection (per commit)
- [ ] Each commit renders as a ReconcileCommitRow (`reconcile-commit-row-{commitId}`)
- [ ] Outcome selector (`outcome-option-{commitId}-achieved`) — click ACHIEVED, verify it's selected
- [ ] Outcome selector — click PARTIALLY_ACHIEVED, verify notes textarea appears (`outcome-notes-{commitId}`)
- [ ] Outcome selector — click NOT_ACHIEVED, verify notes textarea appears and carry-forward checkbox appears
- [ ] Outcome selector — click CANCELED, verify notes textarea appears
- [ ] Notes validation: try to save with empty notes when required → error appears (`notes-error-{commitId}`)
- [ ] Save notes (`save-notes-{commitId}`) with valid text → saving indicator appears then clears

### 2C. Baseline vs Current Comparison
- [ ] Baseline column (`baseline-col-{commitId}`) shows title, chess piece, estimate at lock time
- [ ] Current column (`current-col-{commitId}`) shows current values
- [ ] When title changed post-lock, baseline title has strikethrough styling, current is bold
- [ ] Post-lock-added commits show "Added post-lock — no baseline" and badge (`added-post-lock-badge-{commitId}`)

### 2D. Carry-Forward Flow
- [ ] Carry-forward checkbox (`carry-forward-checkbox-{commitId}`) appears only for NOT_ACHIEVED/PARTIALLY_ACHIEVED
- [ ] Checking carry-forward opens CarryForwardDialog (`carry-forward-dialog`)
- [ ] Dialog shows commit preview (`carry-forward-commit-preview`)
- [ ] Dialog has week options (`carry-forward-week-options`) and reason select (`carry-forward-reason-select`)
- [ ] Selecting reason + confirm (`carry-forward-confirm`) → dialog closes
- [ ] Cancel (`carry-forward-cancel`) → checkbox unchecked

### 2E. Submit Reconciliation
- [ ] Submit button (`reconcile-submit-btn`) is disabled when not all outcomes are set
- [ ] Submit button becomes enabled when all outcomes are set (and required notes filled)
- [ ] Click submit → SubmitConfirmDialog (`reconcile-submit-dialog`) shows with summary counts (`reconcile-submit-summary`)
- [ ] Summary shows correct counts for ACHIEVED/PARTIAL/NOT_ACHIEVED/CANCELED
- [ ] Confirm (`reconcile-submit-confirm`) → plan transitions to RECONCILED
- [ ] Cancel (`reconcile-submit-cancel`) → dialog closes, still in RECONCILING
- [ ] After RECONCILED, read-only banner appears (`reconcile-readonly-banner`)
- [ ] After RECONCILED, outcome selectors are disabled

### 2F. Stats Bar
- [ ] Stats bar (`reconcile-stats-bar`) shows commit count, outcomes set count, baseline points, current points

### 2G. Error States
- [ ] Mock `GET /api/plans/{id}/reconcile` to return 500 → error message (`reconcile-error`)
- [ ] Mock submit to return 500 → error message (`reconcile-submit-error`), dialog stays open

### Key `data-testid` Values
```
page-reconcile, reconcile-plan-state, reconcile-commit-list, reconcile-stats-bar,
reconcile-locked-prompt, open-reconciliation-btn, reconcile-loading, reconcile-error,
reconcile-commit-row-{id}, outcome-option-{id}-{outcome}, outcome-notes-{id},
notes-error-{id}, save-notes-{id}, baseline-col-{id}, current-col-{id},
added-post-lock-badge-{id}, removed-post-lock-badge-{id},
carry-forward-checkbox-{id}, carry-forward-dialog, carry-forward-confirm,
carry-forward-cancel, carry-forward-reason-select, carry-forward-week-options,
reconcile-submit-btn, reconcile-submit-dialog, reconcile-submit-summary,
reconcile-submit-confirm, reconcile-submit-cancel, reconcile-readonly-banner,
reconcile-submit-error, ai-prefill-banner, ai-draft-summary,
ai-ghost-outcome-{id}, ai-ghost-accept-{id}
```

### Types Reference
```ts
type CommitOutcome = "ACHIEVED" | "PARTIALLY_ACHIEVED" | "NOT_ACHIEVED" | "CANCELED";
type CarryForwardReason = "BLOCKED_BY_DEPENDENCY" | "SCOPE_EXPANDED" | "REPRIORITIZED" |
  "RESOURCE_UNAVAILABLE" | "TECHNICAL_OBSTACLE" | "EXTERNAL_DELAY" | "UNDERESTIMATED" | "STILL_IN_PROGRESS";
```

---

## WORK UNIT 3: Tickets CRUD (NEW FILE: `e2e/tickets-deep.spec.ts`)

**Current coverage: 4 shallow tests. Target: ~28 additional tests.**

The Tickets page (`/weekly/tickets`, testid `page-tickets`) has a filter bar, paginated table, detail panel, and create/edit form.

### 3A. Create Ticket
- [ ] Click "Create Ticket" (`create-ticket-btn`) → ticket form dialog opens (`ticket-form-dialog`)
- [ ] Fill title (`ticket-form-title`), set priority (`ticket-form-priority` to "HIGH"), team (`ticket-form-team`) → submit (`ticket-form-submit`)
- [ ] New ticket appears in the list (`ticket-list-table`)
- [ ] Ticket count (`ticket-count`) increments
- [ ] Create with all fields: title, description (`ticket-form-description`), status (`ticket-form-status`), priority, assignee (`ticket-form-assignee`), estimate (`ticket-form-estimate`), RCDO link (`ticket-form-rcdo` / `ticket-rcdo-picker-toggle`), target week (`ticket-form-target-week`)

**Validation (Sad Path):**
- [ ] Submit with empty title → error appears on title field
- [ ] Submit with empty team → error appears on team field
- [ ] Submit with empty reporter → error appears on reporter field
- [ ] Mock `POST /api/tickets` to 500 → submit error (`ticket-form-submit-error`) shows in form

### 3B. Ticket Detail Panel
- [ ] Click a ticket row → detail panel (`ticket-detail-panel`) opens on the right
- [ ] Detail shows key (`ticket-detail-key`), title (`ticket-detail-title`), status badge (`ticket-detail-status-badge`)
- [ ] Detail shows priority (`ticket-detail-priority`), team (`ticket-detail-team`), reporter (`ticket-detail-reporter`)
- [ ] Detail shows estimate (`ticket-detail-estimate`), target week (`ticket-detail-target-week`), RCDO (`ticket-detail-rcdo`)
- [ ] Detail shows description (`ticket-detail-description`)
- [ ] Close button (`ticket-detail-close-btn`) closes the panel
- [ ] Click same ticket row again → panel closes (toggle behavior)

### 3C. Status Transitions
- [ ] Detail panel shows status transition buttons (`status-transition-buttons`) based on current status
- [ ] TODO ticket → shows "In Progress" and "Canceled" buttons
- [ ] Click "In Progress" → status updates to IN_PROGRESS, badge updates
- [ ] IN_PROGRESS → shows "Done", "Blocked", "Canceled", "To Do" buttons
- [ ] Click "Done" → status updates to DONE, no more transition buttons (terminal state)
- [ ] BLOCKED → shows "In Progress", "Canceled", "To Do" buttons
- [ ] CANCELED → shows "To Do" button (can reopen)
- [ ] Mock transition API to 500 → error message (`transition-error`)

### 3D. Edit Ticket
- [ ] Click edit button (`ticket-detail-edit-btn`) → edit form opens pre-populated
- [ ] Change title → submit → detail panel reflects new title
- [ ] Change priority → submit → detail panel reflects new priority

### 3E. Assignee Management
- [ ] Assignee section (`ticket-assignment-section`) shows current assignee or "Unassigned"
- [ ] Change assignee input (`ticket-assignee-input`) → save (`ticket-assign-save-btn`) → detail updates
- [ ] Mock assignee save to 500 → error (`assignee-error`)

### 3F. Filters
- [ ] Status filter (`filter-status`) — select "Done" → only DONE tickets shown
- [ ] Priority filter (`filter-priority`) — select "Critical" → only CRITICAL tickets shown
- [ ] Assignee filter (`filter-assignee`) — enter user ID → filtered results
- [ ] Team filter (`filter-team`) — enter team ID → filtered results
- [ ] Week filter (`filter-week`) — select a date → filtered results
- [ ] RCDO filter (`filter-rcdo`) — enter RCDO node ID → filtered results
- [ ] Clear all (`filter-clear-all`) → all filters reset, full list shown
- [ ] Combined filters — set status + priority → results match both

### 3G. Pagination & Sorting
- [ ] When >20 tickets, pagination appears (`ticket-list-pagination`)
- [ ] Next page (`ticket-page-next`) → shows next page, page indicator updates (`ticket-page-indicator`)
- [ ] Previous page (`ticket-page-prev`) → returns to first page
- [ ] Sort by column headers → list reorders (verify first item changes)

### 3H. Status History & Linked Commits
- [ ] In detail panel, status history timeline (`status-history-timeline`) shows transition entries
- [ ] Linked commits section (`linked-commits-list`) shows commit data or empty state (`linked-commits-empty`)

### Key `data-testid` Values
```
page-tickets, create-ticket-btn, ticket-form-dialog, ticket-form-title,
ticket-form-description, ticket-form-status, ticket-form-priority,
ticket-form-assignee, ticket-form-reporter, ticket-form-team,
ticket-form-estimate, ticket-form-target-week, ticket-form-submit,
ticket-form-submit-error, ticket-rcdo-picker-toggle, ticket-rcdo-selected,
ticket-list-table, ticket-list-empty, ticket-list-loading, ticket-count,
ticket-list-pagination, ticket-page-next, ticket-page-prev, ticket-page-indicator,
ticket-detail-panel, ticket-detail-view, ticket-detail-close-btn, ticket-detail-edit-btn,
ticket-detail-key, ticket-detail-title, ticket-detail-status-badge,
ticket-detail-priority, ticket-detail-team, ticket-detail-reporter,
ticket-detail-estimate, ticket-detail-target-week, ticket-detail-rcdo,
ticket-detail-description, ticket-detail-meta,
status-transition-buttons, transition-error,
ticket-assignment-section, ticket-assignee-input, ticket-assign-save-btn, assignee-error,
filter-status, filter-priority, filter-assignee, filter-team, filter-week,
filter-rcdo, filter-clear-all, ticket-filters,
status-history-timeline, status-history-empty,
linked-commits-list, linked-commits-empty
```

### Types Reference
```ts
type TicketStatus = "TODO" | "IN_PROGRESS" | "DONE" | "CANCELED" | "BLOCKED";
type TicketPriority = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
// Status transitions: TODO→[IN_PROGRESS,CANCELED], IN_PROGRESS→[DONE,BLOCKED,CANCELED,TODO],
// BLOCKED→[IN_PROGRESS,CANCELED,TODO], DONE→[], CANCELED→[TODO]
```

---

## WORK UNIT 4: RCDOs CRUD (NEW FILE: `e2e/rcdos-deep.spec.ts`)

**Current coverage: 5 shallow tests. Target: ~25 additional tests.**

The RCDOs page (`/weekly/rcdos`, testid `page-rcdos`) has a two-column layout: tree panel (left) and detail/form panel (right).

### 4A. Tree Rendering & Selection
- [ ] Tree view (`rcdo-tree-view`) renders Rally Cry nodes from seed data
- [ ] Click a node → detail panel shows node title, type badge, status badge
- [ ] Breadcrumb shows path (e.g., "Rally Cry > DO > Outcome") via `RcdoBreadcrumb`
- [ ] Click breadcrumb item → navigates to that ancestor node
- [ ] Empty state (`rcdo-tree-empty`) shows when no nodes exist (mock empty `GET /api/rcdo/tree`)

### 4B. Search & Filter
- [ ] Type in search input → tree filters to matching nodes
- [ ] Status filter radio buttons: "Active only" (default), "All", "Archived"
- [ ] Select "All" → archived nodes become visible
- [ ] Select "Archived only" → only archived nodes shown
- [ ] Search + status filter combined → both apply

### 4C. Create Rally Cry
- [ ] Click "+ Rally Cry" (`create-rally-cry-btn`) → form panel opens (`rcdo-node-form`)
- [ ] Fill title → submit → new node appears in tree
- [ ] Node is auto-activated (DRAFT → ACTIVE) after creation
- [ ] If activation fails, action error message shows

**Validation:**
- [ ] Submit with empty title → error message

### 4D. Create Defining Objective
- [ ] Select a Rally Cry node → click "+ Defining Objective" (`create-do-btn`)
- [ ] Form pre-fills parent to selected Rally Cry
- [ ] Fill title → submit → child appears under Rally Cry

### 4E. Create Outcome
- [ ] Select a Defining Objective → click "+ Outcome" (`create-outcome-btn`)
- [ ] Form pre-fills parent to selected DO
- [ ] Fill title → submit → child appears under DO

### 4F. Edit Node
- [ ] Select a node → click "Edit" (`edit-node-btn`) → form shows with current title
- [ ] Change title → submit → tree and detail reflect new title
- [ ] Cancel edit → returns to detail view

### 4G. Archive Node
- [ ] Select a leaf node (no active children) → click "Archive" (`archive-node-btn`)
- [ ] Archive confirm dialog shows node name
- [ ] Confirm → node archived, detail panel resets to empty
- [ ] Select a node with active children → archive dialog shows warning (`archive-blocked-message`)
- [ ] Mock archive API to 500 → error message shows

### 4H. Activate DRAFT Node
- [ ] Select a DRAFT status node → "Activate" button (`activate-node-btn`) visible
- [ ] Click Activate → status changes to ACTIVE, badge updates

### 4I. Move Node
- [ ] Select a non-Rally-Cry node → "Move" button (`move-node-btn`) visible
- [ ] Click Move → MoveNodeDialog opens with tree picker
- [ ] Select new parent → confirm → node re-parented in tree
- [ ] Rally Cry nodes don't show Move button (they're top-level)

### 4J. Permission-Based Visibility
- [ ] Read-only user sees `readonly-label`, no create/edit/archive/move buttons
- [ ] Admin user sees all CRUD buttons (`create-rally-cry-btn`, `create-do-btn`, `create-outcome-btn`, `edit-node-btn`, `archive-node-btn`, `move-node-btn`)

### Key `data-testid` Values
```
page-rcdos, rcdo-tree-view, rcdo-tree-empty, rcdo-node-form,
create-rally-cry-btn, create-do-btn, create-outcome-btn,
empty-create-rally-cry-btn, edit-node-btn, activate-node-btn,
archive-node-btn, move-node-btn, readonly-label,
node-action-buttons, archive-blocked-message, archive-confirm-button
```

### Types Reference
```ts
type RcdoNodeType = "RALLY_CRY" | "DEFINING_OBJECTIVE" | "OUTCOME";
type RcdoNodeStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
```

### API Endpoints
```
GET    /api/rcdo/tree
POST   /api/rcdo/nodes               (body: CreateRcdoNodePayload)
PUT    /api/rcdo/nodes/{id}           (body: UpdateRcdoNodePayload)
POST   /api/rcdo/nodes/{id}/activate
POST   /api/rcdo/nodes/{id}/archive
POST   /api/rcdo/nodes/{id}/move      (body: { newParentId })
```

---

## WORK UNIT 5: Reports Deep Tests (NEW FILE: `e2e/reports-deep.spec.ts`)

**Current coverage: 2 tests. Target: ~18 additional tests.**

The Reports page (`/weekly/reports`, testid `reports-page`) renders 8 chart/card types from report API data.

**Strategy:** Mock ALL report API endpoints via `page.route()` to provide controlled data, since reports depend on having multiple reconciled weeks.

### 5A. Page Structure & Controls
- [ ] Page renders with heading "Reports & Analytics" and BarChart3 icon
- [ ] Week range badge shows default "8 weeks"
- [ ] Click "More weeks" button → badge updates to "12 weeks", re-fetches data
- [ ] Click "Fewer" button → badge updates to "4 weeks" (minimum), re-fetches data
- [ ] Loading state: skeleton components (`report-chart-skeleton`) shown while fetching

### 5B. Empty State
- [ ] Mock all report APIs to return empty arrays → empty state (`reports-empty`) shown with "Not enough data yet" message

### 5C. Error State
- [ ] Mock report APIs to return 500 → error message with AlertTriangle icon

### 5D. Charts Render with Mock Data
For each chart, mock the corresponding API with realistic data and verify the chart container renders:

- [ ] Velocity trend chart (`velocity-chart`) renders with bars and trend line
- [ ] Planned vs Achieved chart (`pva-chart`) renders with grouped bars
- [ ] Achievement rate chart (`achievement-rate-chart`) renders with rate bars and 80% target line, shows avg badge
- [ ] Chess distribution chart (`chess-dist-chart`) renders with stacked bar and breakdown rows
- [ ] Scope change chart (`scope-change-chart`) renders with bars and threshold line
- [ ] Carry-forward chart (`cf-chart`) renders with CF Rate % bars
- [ ] Compliance chart (`compliance-chart`) renders with "Lock on time" / "Reconcile on time" / "Auto-locked" bars
- [ ] Exception aging table (`exception-aging-table`) renders with severity, type, week, age columns
- [ ] AI acceptance card (`ai-acceptance-card`) renders with total suggestions, accepted, dismissed, rate bar

### 5E. Chart Interactivity
- [ ] Velocity trend shows trend direction indicator (TrendingUp/TrendingDown) based on last two weeks
- [ ] Exception aging table: items with age ≥72h show "STALE", ≥24h show "AGING", <24h show "NEW"
- [ ] AI acceptance card: acceptance rate ≥25% shows "Above 25%" badge (reconciled variant), below shows "Below 25%" (locked variant)

### Mock Data Templates
```ts
// PlannedVsAchievedEntry
{ teamId: "team-1", weekStart: "2026-03-23", totalPlannedPoints: 15, totalAchievedPoints: 12, memberCount: 5, reconciledCount: 4 }

// ComplianceReportEntry
{ userId: "user-1", weekStart: "2026-03-23", lockOnTime: true, lockLate: false, autoLocked: false, reconcileOnTime: true, reconcileLate: false, reconcileMissed: false }

// CarryForwardReportEntry
{ userId: "user-1", weekStart: "2026-03-23", commitCount: 5, carryForwardCount: 1, carryForwardRate: 0.2 }

// ChessDistributionReportEntry
{ teamId: "team-1", weekStart: "2026-03-23", distribution: { KING: 1, QUEEN: 2, ROOK: 3, BISHOP: 2, KNIGHT: 1, PAWN: 4 } }

// ScopeChangeReportEntry
{ userId: "user-1", weekStart: "2026-03-23", scopeChangeCount: 3 }

// AiAcceptanceReportEntry
{ totalSuggestions: 100, totalFeedbackGiven: 60, acceptedCount: 30, dismissedCount: 30, acceptanceRate: 0.30 }

// ExceptionAgingEntry
{ exceptionId: "exc-1", teamId: "team-1", userId: "user-1", exceptionType: "MISSED_LOCK", severity: "HIGH", weekStartDate: "2026-03-23", createdAt: "2026-03-23T08:00:00Z", ageInHours: 96 }
```

### API Endpoints to Mock
```
GET /api/reports/planned-vs-achieved?teamId=...&weekStart=...&weekEnd=...
GET /api/reports/carry-forward?teamId=...&weekStart=...&weekEnd=...
GET /api/reports/compliance?teamId=...&weekStart=...&weekEnd=...
GET /api/reports/chess-distribution?teamId=...&weekStart=...
GET /api/reports/scope-changes?teamId=...&weekStart=...&weekEnd=...
GET /api/reports/ai-acceptance
GET /api/reports/exception-aging?teamId=...
```

---

## WORK UNIT 6: Commit CRUD Deep Tests (NEW FILE: `e2e/commit-deep.spec.ts`)

**Current coverage: 5 basic tests. Target: ~20 additional tests.**

Focuses on the CommitForm (`commit-form-modal`) interactions not covered by existing tests.

### 6A. Create Commit — Full Form
- [ ] Add commit with all fields: title, description (`commit-form-description`), chess piece, estimate, success criteria (`commit-form-success-criteria`), RCDO link, linked ticket (`commit-form-ticket`)
- [ ] Verify commit appears in list with correct title, chess piece icon, estimate badge

### 6B. Form Validation
- [ ] Submit with empty title → error "Title is required" appears
- [ ] Submit with no chess piece → error "Chess piece is required" appears
- [ ] Select KING when one already exists → KING option is disabled with limit message (`chess-piece-limit-king`)
- [ ] Select QUEEN when two already exist → QUEEN option is disabled with limit message (`chess-piece-limit-queen`)
- [ ] Select KING/QUEEN without success criteria → error "Success criteria is required for King / Queen"
- [ ] Mock `POST /api/plans/{id}/commits` to 500 → submit error shows in form

### 6C. Chess Piece Selection
- [ ] Each chess piece option (`chess-piece-option-king` through `chess-piece-option-pawn`) is clickable
- [ ] Selected piece gets highlighted border styling
- [ ] Piece descriptions are visible (e.g., "Mission-critical — must be done this week" for King)

### 6D. Estimate Points
- [ ] Point buttons (`estimate-btn-1` through `estimate-btn-8`) toggle on/off
- [ ] Click a selected point again → deselects (estimate becomes empty)
- [ ] Only valid Fibonacci values shown: 1, 2, 3, 5, 8

### 6E. RCDO Picker
- [ ] Click "Browse RCDO nodes" (`rcdo-picker-toggle`) → picker panel opens (`rcdo-picker-panel`)
- [ ] Select an Outcome node → selected node displays (`rcdo-selected-node`) with title
- [ ] Clear RCDO link via X button → selection cleared
- [ ] Selecting a Rally Cry (top-level) → validation error "Select an Outcome, or a Defining Objective with no active Outcomes"

### 6F. Edit Commit
- [ ] Click edit on an existing commit → CommitForm opens in edit mode with pre-populated fields
- [ ] Change title → save → list reflects new title
- [ ] Change chess piece → save → list reflects new piece icon
- [ ] Change estimate → save → capacity meter updates
- [ ] Carry-forward banner (`carry-forward-banner`) shows for carried-forward commits with streak count

### 6G. Delete Commit
- [ ] Delete button → confirm dialog (`delete-confirm-dialog`) → confirm (`delete-confirm-btn`) → commit removed from list
- [ ] Cancel delete dialog → commit still in list
- [ ] Capacity meter updates after deletion

### Key `data-testid` Values
```
commit-form-modal, commit-form, commit-form-title, commit-form-description,
commit-form-chess-piece-group, chess-piece-option-{piece}, chess-piece-limit-{piece},
commit-form-estimate-group, estimate-btn-{1|2|3|5|8},
commit-form-success-criteria, commit-form-ticket, commit-form-submit,
rcdo-picker-toggle, rcdo-picker-panel, rcdo-selected-node,
commit-list, commit-list-empty, capacity-meter, capacity-tally,
delete-confirm-dialog, delete-confirm-btn, carry-forward-banner
```

---

## WORK UNIT 7: Team Dashboard Deep Tests (NEW FILE: `e2e/team-deep.spec.ts`)

**Current coverage: 13 tests (tab rendering). Target: ~22 additional tests.**

Focuses on data interactions within each tab of the Team Week page.

### 7A. Overview Section Data
- [ ] Lock compliance card (`lock-compliance-card`) shows percentage/count
- [ ] Reconcile compliance card (`reconcile-compliance-card`) shows percentage/count
- [ ] Points summary card (`points-summary-card`) shows total planned/achieved
- [ ] Exceptions overview card (`exceptions-overview-card`) shows count by severity (`exceptions-high-count`, `exceptions-medium-count`, `exceptions-low-count`)

### 7B. By-Person Tab
- [ ] By-person table (`by-person-table`) shows team members with plan state, points, commit count
- [ ] Each row shows member name, plan state badge, capacity bar
- [ ] Members without plans show appropriate indicator

### 7C. By-RCDO Tab
- [ ] Switch to by-rcdo tab → `panel-by-rcdo` visible
- [ ] RCDO table (`by-rcdo-table`) shows RCDO nodes with commit count and point totals
- [ ] Section (`by-rcdo-section`) renders data from seed RCDO hierarchy

### 7D. Chess Distribution Tab
- [ ] Chess distribution section (`chess-distribution-section`) shows stacked bar (`chess-stacked-bar`)
- [ ] Chess table (`chess-distribution-table`) shows piece breakdown with counts and points
- [ ] Total commits (`chess-total-commits`) and total points (`chess-total-points`) displayed

### 7E. Uncommitted Work Tab
- [ ] Switch to uncommitted tab → `panel-uncommitted` visible
- [ ] Uncommitted work section shows assigned (`assigned-uncommitted-section`) and unassigned (`unassigned-section`) tickets
- [ ] Assigned table (`assigned-uncommitted-table`) shows ticket key, title, assignee
- [ ] Unassigned table (`unassigned-tickets-table`) shows unassigned tickets
- [ ] Unassigned count (`unassigned-count`) and assigned count (`assigned-uncommitted-count`) displayed

### 7F. Exception Queue Tab
- [ ] Exceptions tab shows exception list (`exception-list`) with severity badges
- [ ] Each exception shows type label, description, user, severity
- [ ] Resolve button → resolve dialog (`resolve-exception-dialog`) opens
- [ ] Enter resolution note (`resolution-note-input`) → confirm (`resolve-exception-confirm`) → exception marked resolved
- [ ] Add comment button → comment dialog (`add-comment-dialog`) opens
- [ ] Enter comment (`comment-text-input`) → confirm (`add-comment-confirm`) → comment posted
- [ ] Empty resolution note → confirm button stays disabled
- [ ] Mock resolve API to 500 → error shows in dialog

### 7G. History Tab
- [ ] History tab shows team history view (`team-history-view`)
- [ ] History table (`team-history-table`) shows weekly trend rows
- [ ] Empty state (`team-history-empty`) when no history data

### 7H. Error & Loading States
- [ ] Mock `GET /api/teams/{id}/week/{weekStart}` to 500 → error (`team-week-error`)
- [ ] Loading state shows skeleton (`team-week-skeleton`)

### Key `data-testid` Values
```
page-team-week, team-week-label, team-week-selector,
tab-overview, tab-by-person, tab-by-rcdo, tab-chess, tab-uncommitted, tab-exceptions, tab-history,
panel-overview, panel-by-person, panel-by-rcdo, panel-chess, panel-uncommitted, panel-exceptions, panel-history,
overview-section, lock-compliance-card, reconcile-compliance-card, points-summary-card, exceptions-overview-card,
by-person-section, by-person-table, by-rcdo-section, by-rcdo-table,
chess-distribution-section, chess-distribution-table, chess-stacked-bar, chess-total-commits, chess-total-points,
uncommitted-work-section, assigned-uncommitted-section, assigned-uncommitted-table,
unassigned-section, unassigned-tickets-table, unassigned-count, assigned-uncommitted-count,
exception-queue-section, exception-list, exception-queue-badge,
resolve-exception-dialog, resolution-note-input, resolve-exception-confirm,
add-comment-dialog, comment-text-input, add-comment-confirm,
team-history-view, team-history-table, team-history-empty, team-history-loading,
team-week-error, team-week-skeleton, team-action-error
```

---

## WORK UNIT 8: AI Interactions Deep Tests (NEW FILE: `e2e/ai-deep.spec.ts`)

**Current coverage: 9 presence-check tests. Target: ~25 additional tests.**

All tests use `page.route()` mocks. Uses the existing `mockAiEndpoints()` pattern from `ai-flows.spec.ts` as a base, with per-test overrides.

### 8A. AI Commit Composer Full Flow
- [ ] Click "Add Commit" with AI available → composer opens (`ai-commit-composer`)
- [ ] Type freeform text in input (`ai-composer-freeform-input`)
- [ ] Click "Generate" (`ai-composer-generate-btn`) → draft appears (`ai-composer-draft`) with structured fields
- [ ] Draft shows suggested title (`ai-composer-title-field`), description (`ai-composer-description-field`), criteria (`ai-composer-criteria-field`)
- [ ] Chess piece select (`ai-composer-chess-piece-select`) shows AI-suggested piece
- [ ] RCDO suggestion appears (`ai-composer-rcdo-title`) with confidence (`ai-composer-rcdo-confidence`)
- [ ] Click accept (`ai-composer-accept-btn`) → commit created, composer closes
- [ ] Click "Regenerate" (`ai-composer-regenerate-btn`) → new suggestion generated
- [ ] Click "Switch to manual" (`ai-composer-switch-manual-btn` or `ai-composer-switch-manual-link`) → CommitForm opens
- [ ] Close composer (`ai-composer-close-btn`) → returns to My Week

**Error states:**
- [ ] Mock draft-assist to return `aiAvailable: false` → error state (`ai-composer-error`)
- [ ] Mock draft-assist to 500 → error state with retry option

### 8B. AI Draft Assist Button (in CommitForm)
- [ ] In CommitForm, type a title → "AI Assist" button (`ai-draft-assist-btn`) appears
- [ ] Click assist → suggestions panel (`ai-draft-assist-suggestions`) shows improved title, description, criteria
- [ ] Rationale shown (`ai-draft-assist-rationale`)
- [ ] Accept individual suggestions → form fields update
- [ ] Mock draft-assist to 500 → error (`ai-draft-assist-error`)

### 8C. AI Lint Panel
- [ ] On DRAFT plan with commits, lint panel (`inline-ai-lint-panel`) auto-fires
- [ ] Lint results (`ai-lint-results`) show soft guidance items (`ai-lint-soft`)
- [ ] Hard validation items (`ai-lint-hard`) shown when present
- [ ] Clear state (`ai-lint-clear`) shown when no issues found
- [ ] Refresh button (`ai-lint-refresh` or `ai-lint-run-btn`) re-runs lint
- [ ] Mock lint to return empty → clear state shown
- [ ] Mock lint unavailable → unavailable message (`ai-lint-unavailable`)

### 8D. RCDO Suggestion Inline (in CommitForm)
- [ ] In CommitForm with title but no RCDO selected → suggestion appears (`rcdo-suggestion-inline`)
- [ ] Shows suggested RCDO title (`rcdo-suggestion-title`) and confidence (`rcdo-suggestion-confidence`)
- [ ] Shows rationale (`rcdo-suggestion-rationale`)
- [ ] Click accept (`rcdo-suggestion-accept`) → RCDO field populated
- [ ] Click dismiss (`rcdo-suggestion-dismiss`) → suggestion hidden

### 8E. Proactive Risk Banners
- [ ] On LOCKED plan, risk banners (`proactive-risk-banners`) render per signal
- [ ] Each banner shows signal type and rationale

### 8F. AI Feedback Buttons
- [ ] On any AI suggestion card, thumbs up (`ai-feedback-accept`) and thumbs down (`ai-feedback-dismiss`) visible
- [ ] Click accept → fires POST to `/api/ai/feedback` with action "ACCEPTED"
- [ ] Click dismiss → fires POST to `/api/ai/feedback` with action "DISMISSED"

### 8G. Reconcile AI Pre-fill
- [ ] On RECONCILING plan with AI, ghost outcomes appear (`ai-ghost-outcome-{commitId}`)
- [ ] AI suggested badge (`ai-suggested-badge-{commitId}`) shown
- [ ] Click ghost accept (`ai-ghost-accept-{commitId}`) → outcome selected, ghost removed
- [ ] AI draft summary card (`ai-draft-summary`) shows week summary text
- [ ] AI prefill banner (`ai-prefill-banner`) shows "AI has suggested likely outcomes"

### Mock Endpoints
```
GET  /api/ai/status
POST /api/ai/commit-draft-assist
POST /api/ai/commit-lint
POST /api/ai/rcdo-suggest
POST /api/ai/reconcile-assist
POST /api/ai/feedback
GET  /api/plans/{id}/risk-signals
GET  /api/teams/{id}/week/{weekStart}/ai-summary
GET  /api/teams/{id}/week/{weekStart}/ai-insights
```

---

## WORK UNIT 9: Error & Edge Cases (NEW FILE: `e2e/error-states.spec.ts`)

**Current coverage: 0 tests. Target: ~20 tests.**

Cross-cutting error states, loading states, and edge cases.

### 9A. Network Error Handling
- [ ] Mock `GET /api/plans` to 500 → My Week shows error state (not blank page)
- [ ] Mock `GET /api/rcdo/tree` to 500 → RCDOs page shows error message
- [ ] Mock `GET /api/tickets` to 500 → Tickets page shows error
- [ ] Mock `GET /api/teams/{id}/week/{weekStart}` to 500 → Team page shows error (`team-week-error`)
- [ ] Mock all report APIs to 500 → Reports shows error with AlertTriangle

### 9B. Loading States
- [ ] My Week: while plan loads, skeleton renders (`commit-list-skeleton`, PlanHeaderSkeleton)
- [ ] Team Week: while loading, skeleton renders (`team-week-skeleton`)
- [ ] Tickets: while loading, ticket list loading state (`ticket-list-loading`)
- [ ] RCDOs: while tree loads, skeleton renders
- [ ] Reconcile: loading spinner (`reconcile-loading`)
- [ ] Reports: chart skeletons (`report-chart-skeleton`) render during load

### 9C. Empty States
- [ ] My Week with no commits → empty state (`commit-list-empty`) with add button (`empty-add-commit-btn`)
- [ ] Tickets with no matching results → empty state (`ticket-list-empty`)
- [ ] RCDOs with no nodes → empty state (`rcdo-tree-empty`) with create button (`empty-create-rally-cry-btn`)
- [ ] Reports with no data → empty state (`reports-empty`) with "Not enough data yet"
- [ ] Team history with no data → empty state (`team-history-empty`)

### 9D. Loading Timeout
- [ ] Mock a slow endpoint (delay >5s via `page.route`) → loading timeout banner (`loading-timeout-banner`) appears
- [ ] Retry button (`loading-timeout-retry`) re-fetches

### 9E. Deep Linking
- [ ] Direct navigate to `/weekly/reconcile/{planId}` → loads correct plan
- [ ] Direct navigate to `/weekly/team/{teamId}` → loads correct team
- [ ] Navigate to non-existent route under `/weekly/` → redirected to my-week

### 9F. Browser Navigation
- [ ] Navigate My Week → Team → browser back → returns to My Week with state preserved
- [ ] Week selector state preserved across browser back/forward

### Implementation Pattern (Mocking Errors)
```ts
test("handles API error on My Week", async ({ page }) => {
  await page.route("**/api/plans", (route) =>
    route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ error: "Internal Server Error" }) })
  );
  await page.goto("/weekly/my-week");
  // Verify error state is shown, not a blank page
  await expect(page.getByRole("alert").or(page.getByText(/error|failed/i))).toBeVisible({ timeout: 5000 });
});
```

---

## WORK UNIT 10: Scope Changes & Lock Validation Deep (NEW FILE: `e2e/scope-lock-deep.spec.ts`)

**Current coverage: 7 shallow tests. Target: ~18 additional tests.**

### 10A. Pre-Lock Validation Details
- [ ] Empty plan → lock → validation section (`pre-lock-validation-section`) shows errors
- [ ] Validation errors listed as hard-error items (`hard-error-item`) — e.g., "At least one commit required"
- [ ] Plan with valid commits → lock → validation shows OK (`pre-lock-validation-ok`), continue button appears
- [ ] Auto-lock banner (`auto-lock-banner`) displays when plan was system-locked

### 10B. Lock Confirmation Dialog Details
- [ ] Lock confirm dialog (`lock-confirm-dialog`) shows:
  - Commit count (`lock-confirm-commit-count`)
  - Total points (`lock-confirm-total-points`)
  - Chess piece breakdown (`lock-confirm-piece-breakdown`)
  - RCDO coverage info (`lock-confirm-rcdo-coverage`)
- [ ] Summary section (`lock-confirm-summary`) reflects actual plan data
- [ ] Cancel (`lock-confirm-cancel`) → dialog closes, plan stays DRAFT
- [ ] Confirm (`lock-confirm-btn`) → plan transitions to LOCKED

### 10C. Post-Lock Scope Change Dialog
- [ ] On LOCKED plan, click post-lock add (`post-lock-add-commit-btn`)
- [ ] Scope change dialog (`scope-change-dialog`) opens with locked notice (`scope-change-locked-notice`)
- [ ] Reason select (`scope-change-reason-select`) has options
- [ ] Reason text field (`scope-change-reason-text`) for additional context
- [ ] Add preview (`scope-change-add-preview`) shows new commit fields
- [ ] Confirm (`scope-change-confirm`) → commit added, scope change event recorded
- [ ] Cancel (`scope-change-cancel`) → dialog closes

### 10D. Scope Change Timeline
- [ ] On LOCKED plan with scope changes, click timeline button (`load-scope-timeline-btn`)
- [ ] Timeline (`scope-change-timeline`) shows chronological events
- [ ] Each event shows category (COMMIT_ADDED, ESTIMATE_CHANGED, etc.), reason, timestamp
- [ ] Empty timeline (`scope-change-timeline-empty`) when no changes

### 10E. Soft Warnings Panel
- [ ] Soft warnings panel (`soft-warnings-panel`) shows non-blocking warnings
- [ ] "Too many commits" warning (`warning-too-many-commits`) when applicable
- [ ] "Pawn heavy" warning (`warning-pawn-heavy`) when too many pawns

### Key `data-testid` Values
```
pre-lock-validation-section, pre-lock-validation-panel, pre-lock-validation-ok,
pre-lock-validation-loading, pre-lock-continue-btn, hard-error-item,
lock-confirm-dialog, lock-confirm-btn, lock-confirm-cancel,
lock-confirm-commit-count, lock-confirm-total-points,
lock-confirm-piece-breakdown, lock-confirm-rcdo-coverage, lock-confirm-summary,
auto-lock-banner, post-lock-add-commit-btn,
scope-change-dialog, scope-change-locked-notice, scope-change-reason-select,
scope-change-reason-text, scope-change-confirm, scope-change-cancel,
scope-change-add-preview, scope-change-edit-preview, scope-change-remove-preview,
load-scope-timeline-btn, scope-change-timeline, scope-change-timeline-empty,
soft-warnings-panel, soft-warning-item, warning-too-many-commits, warning-pawn-heavy
```

---

## WORK UNIT 11: Cross-Page Integration Flows (NEW FILE: `e2e/integration-flows.spec.ts`)

**Current coverage: 0 tests. Target: ~8 tests.**

End-to-end flows that span multiple pages. These are slower but test the real user journeys.

### 11A. Full Weekly Lifecycle
- [ ] My Week → create 3 commits (ROOK 3pts, BISHOP 2pts, PAWN 1pt) → verify capacity meter shows 6/10
- [ ] Lock plan → verify LOCKED state
- [ ] Navigate to Reconcile → open reconciliation
- [ ] Set outcomes: ACHIEVED, PARTIALLY_ACHIEVED (with notes), NOT_ACHIEVED (with notes + carry-forward)
- [ ] Submit reconciliation → verify RECONCILED state
- [ ] Navigate back to My Week → verify plan shows RECONCILED badge

### 11B. Carry-Forward Verification
- [ ] Complete lifecycle as 11A with a carry-forward commit
- [ ] Navigate to next week → verify carried-forward commit exists in new week's plan
- [ ] Carry-forward banner shows streak count

### 11C. Team View Reflects Individual Plans
- [ ] My Week → create commits → lock
- [ ] Navigate to Team → by-person tab
- [ ] Current user's row shows LOCKED state with correct point total

### 11D. Ticket-Commit Link
- [ ] Tickets → create ticket
- [ ] My Week → add commit with linked ticket ID
- [ ] Lock plan → Reconcile → set outcome to ACHIEVED → submit
- [ ] Tickets → view ticket detail → linked commits section shows the commit with ACHIEVED outcome

### 11E. Week Navigation Consistency
- [ ] On My Week, go to previous week → verify different plan data
- [ ] Navigate to Team → verify same week shown in team view
- [ ] Navigate to Reconcile → verify same week context
- [ ] Click "Today" → all pages return to current week

---

## Appendix A: Complete `data-testid` Registry

All `data-testid` values in the frontend, grouped by area:

### App Shell & Navigation
```
app-shell, header, footer, user-name, theme-toggle,
week-selector, week-label, prev-week-btn, next-week-btn, current-week-btn
```

### My Week Page
```
page-my-week, plan-state-badge, plan-header, compliance-badge-ok, compliance-badge-warn,
add-commit-btn, add-commit-manually-btn, lock-plan-btn, post-lock-add-commit-btn,
commit-list, commit-list-empty, commit-list-add-btn, commit-list-skeleton,
capacity-meter, capacity-tally, capacity-bar, capacity-breakdown,
expand-all-btn, collapse-all-btn, toggle-plan-history-btn,
reconcile-hint, load-scope-timeline-btn, my-week-empty, empty-add-commit-btn
```

### Commit Form
```
commit-form-modal, commit-form, commit-form-title, commit-form-description,
commit-form-chess-piece-group, chess-piece-option-{king|queen|rook|bishop|knight|pawn},
chess-piece-limit-{king|queen}, commit-form-estimate-group, estimate-btn-{1|2|3|5|8},
commit-form-success-criteria, commit-form-ticket, commit-form-submit,
rcdo-picker-toggle, rcdo-picker-panel, rcdo-selected-node, carry-forward-banner
```

### Lock & Scope Change
```
pre-lock-validation-section, pre-lock-validation-panel, pre-lock-validation-ok,
pre-lock-validation-loading, pre-lock-continue-btn, hard-error-item,
lock-confirm-dialog, lock-confirm-btn, lock-confirm-cancel,
lock-confirm-commit-count, lock-confirm-total-points,
lock-confirm-piece-breakdown, lock-confirm-rcdo-coverage, lock-confirm-summary,
auto-lock-banner, delete-confirm-dialog, delete-confirm-btn,
scope-change-dialog, scope-change-locked-notice, scope-change-reason-select,
scope-change-reason-text, scope-change-confirm, scope-change-cancel,
scope-change-add-preview, scope-change-edit-preview, scope-change-remove-preview,
scope-change-timeline, scope-change-timeline-empty,
soft-warnings-panel, soft-warning-item, warning-too-many-commits, warning-pawn-heavy
```

### Reconcile Page
```
page-reconcile, reconcile-plan-state, reconcile-commit-list, reconcile-stats-bar,
reconcile-locked-prompt, open-reconciliation-btn, reconcile-loading, reconcile-error,
reconcile-commit-row-{id}, outcome-option-{commitId}-{outcome},
outcome-notes-{id}, notes-error-{id}, save-notes-{id},
baseline-col-{id}, baseline-title-{id}, baseline-piece-{id}, baseline-estimate-{id}, baseline-rcdo-{id},
current-col-{id}, current-title-{id}, current-piece-{id}, current-estimate-{id},
added-post-lock-badge-{id}, removed-post-lock-badge-{id}, auto-achieved-badge-{id},
carry-forward-checkbox-{id}, carry-forward-checkbox-label-{id},
carry-forward-dialog, carry-forward-confirm, carry-forward-cancel,
carry-forward-commit-preview, carry-forward-reason-select, carry-forward-week-options, carry-forward-notes,
reconcile-submit-btn, reconcile-submit-section, reconcile-submit-dialog,
reconcile-submit-summary, reconcile-submit-confirm, reconcile-submit-cancel,
reconcile-submit-error, reconcile-readonly-banner,
ai-prefill-banner, ai-draft-summary,
ai-ghost-outcome-{id}, ai-ghost-accept-{id}, ai-suggested-badge-{id},
saving-indicator-{id}
```

### Team Week Page
```
page-team-week, team-week-label, team-week-selector, team-name,
team-prev-week-btn, team-next-week-btn, team-current-week-btn,
tab-overview, tab-by-person, tab-by-rcdo, tab-chess, tab-uncommitted, tab-exceptions, tab-history,
panel-overview, panel-by-person, panel-by-rcdo, panel-chess, panel-uncommitted, panel-exceptions, panel-history,
overview-section, lock-compliance-card, reconcile-compliance-card, points-summary-card, exceptions-overview-card,
exceptions-high-count, exceptions-medium-count, exceptions-low-count,
by-person-section, by-person-table, by-rcdo-section, by-rcdo-table,
chess-distribution-section, chess-distribution-table, chess-stacked-bar, chess-total-commits, chess-total-points,
critical-work-summary, critical-work-pct,
uncommitted-work-section, assigned-uncommitted-section, assigned-uncommitted-table,
assigned-uncommitted-count, unassigned-section, unassigned-tickets-table, unassigned-count,
exception-queue-section, exception-list, exception-queue-badge,
resolve-exception-dialog, resolution-note-input, resolve-exception-confirm,
add-comment-dialog, comment-text-input, add-comment-confirm,
team-insights-section, team-insights-toggle, team-risk-summary-banners,
manager-ai-summary-card, manager-ai-summary-loading, manager-ai-summary-error,
manager-ai-summary-text, manager-ai-summary-rcdo-branches,
manager-ai-summary-carry-forward, manager-ai-summary-exception-count,
manager-ai-summary-blocked-count,
team-history-view, team-history-table, team-history-empty, team-history-loading,
team-week-error, team-week-loading, team-week-skeleton, team-action-error
```

### Tickets Page
```
page-tickets, create-ticket-btn, ticket-count,
ticket-form-dialog, ticket-form-title, ticket-form-description,
ticket-form-status, ticket-form-priority, ticket-form-assignee,
ticket-form-reporter, ticket-form-team, ticket-form-estimate,
ticket-form-target-week, ticket-form-submit, ticket-form-submit-error,
ticket-rcdo-picker-toggle, ticket-rcdo-picker-panel, ticket-rcdo-selected,
ticket-list-view, ticket-list-table, ticket-list-empty, ticket-list-loading,
ticket-list-pagination, ticket-page-next, ticket-page-prev, ticket-page-indicator,
ticket-detail-panel, ticket-detail-view, ticket-detail-loading,
ticket-detail-close-btn, ticket-detail-edit-btn,
ticket-detail-key, ticket-detail-title, ticket-detail-status-badge,
ticket-detail-priority, ticket-detail-team, ticket-detail-reporter,
ticket-detail-estimate, ticket-detail-target-week, ticket-detail-rcdo,
ticket-detail-description, ticket-detail-meta,
ticket-filters, filter-status, filter-priority, filter-assignee,
filter-team, filter-week, filter-rcdo, filter-clear-all,
status-transition-buttons, transition-error,
ticket-assignment-section, ticket-assignee-input, ticket-assign-save-btn, assignee-error,
status-history-timeline, status-history-empty,
linked-commits-list, linked-commits-empty
```

### RCDOs Page
```
page-rcdos, rcdo-tree-view, rcdo-tree-empty, rcdo-node-form,
create-rally-cry-btn, create-do-btn, create-outcome-btn,
empty-create-rally-cry-btn, edit-node-btn, activate-node-btn,
archive-node-btn, move-node-btn, readonly-label,
node-action-buttons, archive-blocked-message, archive-confirm-button
```

### Reports Page
```
reports-page, reports-empty, report-chart-skeleton,
velocity-chart, pva-chart, achievement-rate-chart,
chess-dist-chart, scope-change-chart, cf-chart,
compliance-chart, exception-aging-table, ai-acceptance-card
```

### Admin Page
```
admin-page, org-config-section, team-config-section,
org-week-start-day, org-timezone, org-budget,
org-draft-offset-day, org-draft-offset-time,
org-lock-offset-day, org-lock-offset-time,
org-reconcile-open-offset-day, org-reconcile-open-offset-time,
org-reconcile-due-offset-day, org-reconcile-due-offset-time,
org-save-btn, org-saved-indicator,
team-budget-override, team-lock-override, team-timezone-override, team-save-btn
```

### AI Components (cross-cutting)
```
ai-commit-composer, ai-composer-freeform-input, ai-composer-generate-btn,
ai-composer-draft, ai-composer-title-field, ai-composer-description-field,
ai-composer-criteria-field, ai-composer-chess-piece-select, ai-composer-chess-warning,
ai-composer-rcdo-title, ai-composer-rcdo-confidence, ai-composer-rcdo-picker-toggle,
ai-composer-rcdo-picker, ai-composer-rcdo-search, ai-composer-clear-rcdo,
ai-composer-accept-btn, ai-composer-regenerate-btn, ai-composer-close-btn,
ai-composer-switch-manual-btn, ai-composer-switch-manual-link,
ai-composer-error, ai-composer-submit-error,
ai-draft-assist-btn, ai-draft-assist-suggestions, ai-draft-assist-rationale,
ai-draft-assist-error, ai-draft-assist-good,
inline-ai-lint-panel, ai-lint-results, ai-lint-hard, ai-lint-soft,
ai-lint-clear, ai-lint-loading, ai-lint-error, ai-lint-unavailable,
ai-lint-refresh, ai-lint-run-btn, ai-lint-ready,
rcdo-suggestion-inline, rcdo-suggestion-title, rcdo-suggestion-confidence,
rcdo-suggestion-rationale, rcdo-suggestion-accept, rcdo-suggestion-dismiss, rcdo-suggestion-loading,
proactive-risk-banners, risk-signals-list, risk-signals-clear,
risk-signals-loading, risk-signals-error, risk-signals-unavailable, risk-signals-no-plan,
ai-feedback-accept, ai-feedback-dismiss, ai-flag, ai-suggested-badge, ai-mode-toggle,
confidence-badge, confidence-bar, confidence-indicator,
calibration-card, calibration-card-skeleton, calibration-weeks-label,
calibration-carry-forward, calibration-insufficient-msg,
insight-panel, insight-panel-empty, insight-panel-loading,
insight-panel-error, insight-panel-unavailable,
semantic-search-input, semantic-search-submit, semantic-search-loading,
semantic-search-answer, semantic-search-error, semantic-search-retry,
query-answer-card, rag-answer-text, rag-sources-section, rag-sources-toggle,
evidence-drawer, suggested-questions,
whatif-panel, whatif-expand-btn, whatif-collapse-btn,
whatif-add-title, whatif-add-chess, whatif-add-points,
whatif-remove-select, whatif-modify-commit-select,
whatif-modify-field-select, whatif-modify-value-input,
whatif-modify-value-chess, whatif-modify-value-points,
whatif-queue-btn, whatif-simulate-btn, whatif-clear-mutations-btn,
whatif-results, whatif-summary, whatif-narrative,
whatif-capacity, whatif-risk-delta, whatif-coverage-changes,
whatif-mutation-list, whatif-error, whatif-unavailable,
what-if-delta, what-if-new-risks, what-if-resolved-risks,
what-if-points-before, what-if-points-after,
recommendations-loading, refresh-recommendations-btn,
plan-recommendation-card, apply-suggestion-dialog, apply-suggestion-close-btn,
reconcile-assist-panel, reconcile-assist-btn, reconcile-assist-loading,
reconcile-assist-error, reconcile-assist-retry, reconcile-assist-refresh,
reconcile-assist-outcomes, reconcile-assist-summary,
reconcile-assist-carry-forwards, reconcile-assist-empty,
manager-ai-summary-card, manager-ai-summary-loading, manager-ai-summary-text,
manager-ai-summary-error, manager-ai-summary-rcdo-branches,
manager-ai-summary-carry-forward, manager-ai-summary-exception-count,
manager-ai-summary-blocked-count, streaming-typing-indicator
```

### Shared Components
```
loading, loading-timeout-banner, loading-timeout-retry,
empty-icon, cta-btn, filter-bar,
filter-preset-select, filter-preset-save-confirm, filter-preset-name-input, filter-save-preset-btn,
cf-lineage-section, cf-lineage-view, cf-lineage-chain, cf-lineage-length,
cf-lineage-empty, cf-lineage-loading
```
