## 1. Overview

Weekly Commit Module is a production internal application module that replaces 15Five for weekly planning and reconciliation. It creates a structured, auditable weekly workflow that ties each person’s weekly commitments to the organization’s RCDO hierarchy and gives managers timely visibility into alignment, scope change, and rollover risk.

The module will be delivered as a route-level micro-frontend integrated into the existing PA host app. It will own the following domains in v1:

* weekly plans and commits
* RCDO hierarchy management
* minimal native tickets/work items
* weekly lock and reconciliation lifecycle
* team and manager rollups
* conservative, explainable AI assistance

Key v1 product decisions in this PRD:

* One weekly plan per user per week.
* Team weekly plan is a derived aggregate view, not a separate planning object.
* Each commit maps to exactly one primary RCDO node in v1.
* Commits link to Outcomes by default; they may link to a Defining Objective only when that objective has no active Outcomes. Rally Cries are roll-up only and are not valid direct commit targets in v1.
* Capacity uses coarse commit-level points on a 1/2/3/5/8 scale, with a default weekly budget of 10 points per full-time user.
* No blanket manager approval gates for weekly plans. Managers review exceptions; they do not approve every plan.
* Carry-forward is always explicit and creates a new commit object with provenance.
* AI is assistive and explainable. It never makes hidden edits and never produces punitive scoring.

---

## 2. Problem Statement

The current 15Five-based weekly planning process has four structural failures:

1. **No first-class strategy linkage.** Users can describe weekly work, but the system does not require work to map to Rally Cries, Defining Objectives, or Outcomes. Managers cannot see whether a team’s weekly effort is moving the right strategic branches.

2. **No locked baseline.** Weekly plans can change without an auditable contract point. That makes it hard to distinguish planned work from in-week scope change.

3. **No structured reconciliation.** Managers and individuals cannot reliably compare planned vs. actual. Silent carry-forward hides repeated misses and reduces learning quality.

4. **No operational data foundation for AI.** Without structured work objects, lifecycle states, linkage, and historical lineage, AI cannot provide high-signal drafting, alignment, or risk assistance.

The result is late detection of misalignment, poor weekly discipline, weak managerial visibility, and no trustworthy data model for assistive intelligence.

---

## 3. Product Vision

Weekly Commit Module should function as the organization’s system of record for weekly work contracts and their connection to strategy.

The product vision is:

* lightweight enough that users complete weekly planning and reconciliation as a normal ritual
* structured enough that lock and reconciliation create durable historical records
* operational enough that managers can see risk and misalignment early
* AI-native enough that the system improves clarity, linkage quality, and exception detection without feeling invasive or authoritarian

In practice, this means a user should be able to answer three questions quickly every week:

* What am I committing to this week?
* Why does it matter strategically?
* What actually happened compared with the plan?

A manager should be able to answer three parallel questions:

* What is my team actually committing to this week?
* Which RCDOs are receiving effort, and which are not?
* Where do I need to intervene before the week is lost?

---

## 4. Goals

1. **Enforce strategy linkage.** Every locked weekly commit must have one valid primary RCDO link.
2. **Create a versioned weekly contract.** Lock creates an immutable baseline; reconciliation creates an immutable actual snapshot.
3. **Make scope change explicit.** Work added, changed, or dropped after lock must be visible as structured events.
4. **Make carry-forward explicit.** Incomplete work must not silently persist into the next week.
5. **Provide manager visibility without heavy process.** Managers should see team alignment, exception queues, and rollover trends without approving every user plan.
6. **Establish a credible AI foundation.** The data model must support conservative assistance for drafting, linkage, and risk detection in v1.

---

## 5. Non-Goals

1. Formal performance evaluation, compensation, or ranking of employees.
2. A composite “productivity score” or hidden employee scoring.
3. Detailed time tracking or surveillance-style monitoring.
4. A full Jira replacement for portfolio, dependency, or release management.
5. Many-to-many commit-to-RCDO mapping in v1.
6. A separate team planning object with its own lifecycle in v1.
7. Autonomous AI edits or automatic submission of plans/reconciliations.
8. Broad external integrations as a dependency for v1.

---

## 6. Personas

### Individual Contributor (IC)

Owns a weekly plan. Needs to commit to a focused set of work, link it to strategy, and reconcile quickly without writing a weekly essay.

### Manager

Owns a team and is accountable for weekly alignment, capacity realism, and exception handling. Needs a team roll-up, not a pile of individual check-ins.

### Admin / Strategy Owner

Owns org configuration, RCDO health, and rollout governance. Needs to manage hierarchy integrity, permissions, and cadence settings.

---

## 7. Jobs to Be Done

### IC

* When I start the week, I want to create a small, prioritized set of commitments tied to strategy so I can focus and my manager can see what matters.
* When priorities change mid-week, I want to record the change without rewriting history so my plan remains honest.
* When the week ends, I want to reconcile quickly and carry unfinished work forward explicitly.

### Manager

* When my team starts the week, I want to see what each person committed to and how those commitments roll up to RCDOs.
* When scope changes or people overcommit, I want to detect it early enough to intervene.
* When the week ends, I want to see which work moved strategy forward and where carry-forward is becoming a pattern.

### Admin / Strategy Owner

* When strategy changes, I want to update the RCDO tree without breaking historical reporting.
* When the organization adopts the module, I want one consistent cadence, permission model, and audit trail.

---

## 8. Product Principles

1. **Weekly plans are contracts, not notes.** They must be structured, lockable, and reconcilable.
2. **Link strategy explicitly.** RCDO linkage is a required field, not narrative text.
3. **Keep the ritual short.** Users should enter commitments, not every micro-task. Small operational work can be grouped into a single Pawn commit.
4. **History should be truthful.** After lock, work can change, but only through explicit change events.
5. **Managers handle exceptions, not routine approvals.** The workflow should not create a managerial bottleneck.
6. **AI must be visible, explainable, and ignorable.** Every suggestion needs a rationale and an audit trail.
7. **Use structure to help, not to police.** The system should improve focus and alignment without becoming a performance surveillance tool.

---

## 9. Core Concepts and Domain Model

### Weekly Plan

A weekly plan is a user-owned, week-scoped planning object. There is exactly one weekly plan per user per planning week in v1.

Fields include:

* owner user
* home team snapshot
* week start date
* state
* lock and reconcile deadlines
* capacity budget snapshot
* compliance flags
* lock snapshot reference
* reconcile snapshot reference

### Commit

A commit is the core weekly work object. It is a weekly promise owned by one user and attached to one weekly plan.

Commit fields in v1:

* title (required)
* description (optional)
* chess piece (required)
* priority order (required)
* primary RCDO link (required at lock)
* optional native ticket link
* estimate points (required at lock)
* success criteria (required for King and Queen; recommended otherwise)
* current status metadata
* provenance metadata if carried forward

### Baseline Snapshot

When a plan is locked, the system captures an immutable snapshot of the plan and all commits as the baseline contract for the week.

### Scope Change Event

Any commit add, remove, reprioritization, or material field edit after lock is stored as an append-only scope change event. No silent edits are allowed after lock.

### Reconciliation Snapshot

When a plan is reconciled, the system captures an immutable actual snapshot including outcomes, linked ticket status snapshots, change-event summary, and carry-forward decisions.

### Carry-Forward Link

Carry-forward creates a new commit object in a later plan and stores lineage to the source commit. It does not mutate the original commit into a new week.

### Ticket / Work Item

A ticket is a durable execution object that may or may not be represented in a weekly plan. It can exist before, during, or after a commit.

### RCDO Node

The hierarchy has three node types:

* Rally Cry
* Defining Objective
* Outcome

Commits link directly to Outcomes by default. If a Defining Objective has no active child Outcomes, commits may link directly to that objective. Commits do not link directly to Rally Cries in v1.

### Team Weekly View

The team weekly view is a derived read model over:

* all weekly commits for users on the team for the selected week
* week-targeted tickets with no linked commit, including unassigned tickets

This is not a separate persisted planning object.

### Home Team

Each user has one home team for weekly planning and capacity in v1. A user may work on tickets or RCDOs outside the home team, but weekly plan ownership and manager accountability follow the home team.

### AI Suggestion / Risk Signal

An AI output is a stored suggestion or risk signal tied to a user action or review surface. Every AI output must store rationale, model version, timestamp, and accept/dismiss feedback.

---

## 10. User Experience Overview

The module will ship with five primary surfaces.

### My Week

Default user landing page. Shows current week plan or upcoming draft, commit list, capacity meter, AI lint panel, and lock/reconcile actions.

### Reconcile

A comparison view that shows baseline vs. actual, linked ticket evidence, scope changes, outcome entry, and carry-forward actions.

### Team Week

Manager-focused roll-up showing:

* lock and reconcile compliance
* commits by person
* roll-up by RCDO
* chess distribution
* uncommitted assigned/unassigned work
* risk flags and exception queue

### Tickets

A minimal native work item experience: list view, detail view, status updates, assignment, filters, and create-from-commit. Full sprint/board functionality is not required in v1.

### RCDOs

Hierarchy management UI for admins and managers within their authorized branches.

The dominant interaction model should be list/table based rather than canvas-heavy. The module should feel operational and compact, not reflective or essay-based.

---

## 11. Primary User Flows

### Flow 1: Create and Lock Weekly Plan

1. Draft opens for next week.
2. User creates or edits commits.
3. System validates required fields and shows AI lint/suggestions.
4. User locks the plan manually.
5. Baseline snapshot is captured and the plan becomes LOCKED.

If the user does not lock by cutoff, the system auto-locks the draft as-is and flags the plan as non-compliant.

### Flow 2: Modify Scope After Lock

1. User edits a locked plan.
2. Instead of in-place silent mutation, the system creates a structured scope change event.
3. User must provide a reason for material changes.
4. Managers see the change in the exception feed if it crosses configured thresholds.

### Flow 3: Reconcile Weekly Plan

1. Plan enters RECONCILING at week end.
2. User sees baseline commits, post-lock changes, linked ticket statuses, and outcome inputs.
3. Done tickets auto-mark linked commits as Achieved.
4. User manually reconciles non-ticket work and linked-ticket work that is not done.
5. User submits reconciliation.
6. Immutable reconciliation snapshot is created.

### Flow 4: Carry Forward Incomplete Work

1. During reconciliation, the user selects incomplete commits to carry forward.
2. User provides a carry-forward reason.
3. System creates a new commit in the next week’s draft plan with provenance.
4. New commit is inserted at the bottom of the next week’s priority order and must be re-ranked before lock.

If the next week is already locked, the carry-forward is added as a post-lock scope change event in that week.

### Flow 5: Manager Weekly Review

1. Manager opens Team Week.
2. Sees compliance cards, risks, and RCDO coverage.
3. Reviews exception queue.
4. Comments, adjusts capacity overrides if needed, assigns unassigned tickets, or follows up on repeated carry-forward.
5. No per-user signoff is required for routine weekly plans.

### Flow 6: RCDO Maintenance

1. Admin or authorized manager creates or updates RCDO nodes.
2. Active nodes become selectable in commit creation.
3. Archived nodes remain in history but cannot be used for new commits.

---

## 12. Weekly Lifecycle State Machine

The weekly lifecycle applies to the **weekly plan**, not to the ticket.

| State       | Meaning                                            | Allowed Actions                                                                           | Entry Trigger                                                | Exit Trigger                                |
| ----------- | -------------------------------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ------------------------------------------- |
| DRAFT       | Editable plan for a specific upcoming/current week | Commit CRUD, reorder, set capacity note, link tickets, accept AI suggestions              | Draft window opens or plan is lazily created on first access | Manual lock or auto-lock at cutoff          |
| LOCKED      | Baseline contract exists                           | View plan, add structured scope changes, view manager comments, continue ticket execution | Lock action captures baseline snapshot                       | Automatic transition at reconcile open time |
| RECONCILING | User compares baseline vs. actual                  | View linked ticket evidence, set outcomes, add reconcile notes, choose carry-forward      | Automatic at reconcile open time                             | User submits reconciliation                 |
| RECONCILED  | Immutable actual snapshot exists                   | View history, manager review, analytics/reporting                                         | Reconcile submission captures actual snapshot                | Terminal for the source plan                |

**Carry Forward** is implemented as a post-reconciliation action, not as a durable state on the original plan. The original plan remains RECONCILED. Carry-forward creates a new commit object with lineage into a future plan.

### Default cadence in v1

The cadence is configurable at the org or team level. The recommended default is:

* planning week: Monday 00:00 to Friday 17:00, team local time
* next week draft opens: prior Friday 12:00
* reconciliation opens: Friday 17:00
* reconciliation due: Monday 10:00
* lock due: Monday 12:00

Rationale:

* Monday lock preserves a start-of-week planning ritual.
* Monday reconcile due allows users to reflect after the week closes.
* A two-hour gap between reconcile due and lock due allows explicit carry-forward before the new week is locked.
* Teams with weekend operations can configure alternative cutoffs.

### Compliance rules

* Manual lock by deadline counts as compliant.
* Auto-lock counts as locked but non-compliant.
* Reconciliation submitted by deadline counts as compliant.
* Late reconciliation remains allowed, but the plan is marked overdue.

---

## 13. Functional Requirements

### FR-1: Weekly Plan Creation

* The system must create a DRAFT plan for each active eligible user for each week when the draft window opens, or lazily on first access if pre-creation fails.
* A user must have exactly one plan per week in v1.
* The system must snapshot the user’s home team and configured capacity budget onto the plan.

### FR-2: Commit CRUD

* Users must be able to create, edit, delete, and reorder commits in DRAFT.
* Title, chess piece, priority order, primary RCDO link, and estimate points are required for manual lock.
* Success criteria are required for King and Queen commits.
* The UI must support drag/drop or explicit reorder controls for priority rank.

### FR-3: Validation and Lock

* Manual lock must enforce hard validation rules.
* Hard validation errors must block manual lock.
* The system must auto-lock at the configured cutoff even if the draft contains validation issues; such plans are marked `system_locked_with_errors`.
* Lock must create an immutable baseline snapshot.

### FR-4: Post-Lock Scope Change

* After lock, users may not silently edit or delete baseline commits.
* All changes after lock must be represented as structured change events.
* A reason category is required for:

  * adding a commit
  * removing/canceling a commit
  * changing estimate points
  * changing chess piece
  * changing primary RCDO
* The system must show an in-week change timeline in plan and manager views.

### FR-5: Reconciliation

* Reconciliation must show baseline values and current values side by side.
* Reconciliation must display linked ticket status and ticket history summary where available.
* The system must support actual outcomes:

  * Achieved
  * Partially Achieved
  * Not Achieved
  * Canceled / De-scoped
* Reconcile notes are required for Partially Achieved, Not Achieved, and Canceled outcomes.

### FR-6: Carry Forward

* Carry-forward must be explicit per commit.
* Carry-forward must create a new commit object, not mutate the existing one into the next week.
* The new commit must retain provenance to the source commit and increment carry-forward streak metadata.
* Carry-forward reason is required from a controlled list plus optional text.

### FR-7: Team Weekly View

* The system must provide a team aggregate view for a selected week.
* The view must include:

  * team members’ weekly commits
  * assigned tickets with no linked commit
  * unassigned tickets targeted to the selected week
* Linked tickets must not be double-counted as both committed work and separate uncommitted work.

### FR-8: Manager Review

* Managers must have an exception queue showing:

  * missed/auto locks
  * missed reconciliation
  * over-budget plans
  * repeated carry-forward
  * post-lock major scope increases
  * added/changed King commits after lock
* Managers must be able to add plan-level and commit-level comments for direct reports.

### FR-9: Search, Filters, and History

* Users and managers must be able to filter by week, user, team, RCDO, chess piece, state, and risk flag.
* Users must be able to view their plan history and carry-forward lineage.
* Managers must be able to view team history and trend summaries.

### FR-10: Configuration

* Org/admin must be able to configure default week cadence and deadlines.
* Teams must be able to override org defaults.
* Managers/admins must be able to set per-user weekly capacity overrides.

### FR-11: Auditability

* Lock, reconcile, carry-forward, RCDO edits, ticket status changes, capacity overrides, and AI suggestions must all be auditable.
* Historical snapshots must remain immutable.

### FR-12: Failure Handling

* If AI is unavailable, the core planning workflow must continue with no loss of functionality.
* If scheduled lifecycle jobs fail, retries must be idempotent and must not create duplicate transitions or snapshots.

---

## 14. Native Ticket / Work Item Model

### Purpose

Tickets are the durable execution objects inside the module. Commits are week-scoped promises. Tickets and commits are related but not interchangeable.

### Exact distinction

**Commit**

* scoped to one user and one week
* answers: “What am I committing to this week, in what order, and why?”
* participates in lock, reconciliation, and carry-forward
* required for weekly reporting

**Ticket**

* durable execution artifact
* answers: “What work item exists, who owns it, and what is its execution status?”
* may span multiple weeks
* does not participate in the weekly plan state machine

### Ticket fields in v1

* key / id
* title
* description
* status
* assignee
* reporter
* team
* priority
* optional estimate points
* optional primary RCDO link
* optional target week
* created/updated timestamps

### Ticket workflow in v1

Required statuses:

* Backlog
* Ready
* In Progress
* Blocked
* Done
* Canceled

Status transitions must be timestamped and retained in history to support duration-in-status reporting and future AI features.

### Relationship rules

* A commit may link to zero or one ticket.
* A ticket may be linked by many commits over time.
* In the same week, a ticket should not be linked to more than one active commit for the same assignee.
* A ticket may exist without a weekly commit.
* A weekly commit may exist without a ticket.

### Completion semantics

* If a linked ticket is `Done` at reconciliation snapshot time, the linked commit is auto-marked `Achieved`.
* If the linked ticket is not `Done`, the user reconciles manually against the commit’s success criteria.
* If users intend to commit to a partial milestone on a large ticket, they should either create a smaller ticket or use manual actuals. Ticket completion is treated as authoritative evidence of done when it exists.

### Ticket and RCDO linkage

Tickets may also have an optional primary RCDO link. When a ticket is linked to a commit:

* the commit RCDO defaults from the ticket if the commit is blank
* the commit still stores its own explicit RCDO
* if ticket and commit RCDO differ, the UI warns; weekly reporting uses the commit’s RCDO

### Unassigned work in team view

Week-targeted tickets without a linked commit appear as uncommitted work in the team weekly view. This is how v1 handles team-level assigned and unassigned work without creating a separate team plan object.

---

## 15. RCDO Management Model

### Hierarchy

The module is the system of record for the RCDO hierarchy:

* Rally Cry
* Defining Objective
* Outcome

Parent-child relationships are enforced in that order.

### Ownership

* **Admins** can create, update, archive, and move any RCDO node.
* **Managers** can create and update Defining Objectives and Outcomes within branches owned by their team.
* **Rally Cries** are admin-managed in v1.

Each node should have:

* title
* description
* owner team
* owner user (optional)
* status
* created/updated metadata

Outcomes may additionally store a short target metric or success statement, but v1 does not require full KPI tracking.

### Node statuses

* Draft
* Active
* Archived

Only Active nodes can be linked to new commits.

### Commit linkage rules

* Commits must link directly to an Outcome when one exists.
* A commit may link to a Defining Objective only if that objective has no active Outcomes.
* Commits may not link directly to Rally Cries in v1.

This keeps strategy linkage specific enough for real reporting without blocking teams whose hierarchy is not fully matured.

### History behavior

RCDO edits must not rewrite historical meaning. Lock and reconciliation snapshots must store the denormalized RCDO path and labels as they existed at that time.

### Archival rules

* Archived nodes remain visible in historical plans and reports.
* Archived nodes cannot be selected on new commits.
* A node with active child nodes cannot be archived until children are resolved or moved.

---

## 16. Chess Layer and Prioritization Rules

The chess layer is a required internal prioritization taxonomy on every commit. It affects attention, rollups, notification priority, and risk detection. It is not a score.

| Piece  | Meaning                                                                               | v1 Guardrails                                      | Operational Effect                                                       |
| ------ | ------------------------------------------------------------------------------------- | -------------------------------------------------- | ------------------------------------------------------------------------ |
| King   | Must not fail; critical compliance, production stability, or mission-critical blocker | Max 1 per user per week; success criteria required | Highest alerting; post-lock changes always enter manager exception queue |
| Queen  | Highest leverage strategic progress directly tied to RCDO movement                    | Max 2 per user per week; success criteria required | High rollup weight; elevated risk signaling                              |
| Rook   | Major deliverable with clear edges                                                    | No hard cap                                        | Standard deliverable rollup                                              |
| Bishop | Enabling/platform/process work supporting multiple outcomes                           | No hard cap                                        | Highlighted in enabling-work rollups                                     |
| Knight | Exploratory or high-uncertainty work                                                  | No hard cap; success criteria recommended          | Elevated uncertainty flag in risk model                                  |
| Pawn   | Small but necessary hygiene/ops/admin work                                            | No hard cap                                        | Lowest notification priority                                             |

### Additional rules

* Priority order is separate from chess piece and must be explicit.
* The UI should warn if a user creates more than 8 total active commits in a week.
* The UI should warn if more than 40% of total points are Pawns.
* The system should sort manager exceptions first by King/Queen, then by risk level.

### Why this model

The chess layer gives v1 a lightweight but meaningful taxonomy for reporting and AI without introducing numeric scoring or false precision.

---

## 17. AI Capabilities

### AI design constraints

All AI capabilities in v1 must follow these rules:

* assistive, not authoritarian
* no hidden edits
* no autonomous submission
* no punitive or composite scoring
* explainable rationale on every suggestion
* auditable storage of suggestion and response
* conservative thresholds with a bias toward silence over noise

### v1 AI capabilities

#### 1. Commit drafting assistance

Available on demand while writing a commit.

Capabilities:

* improve title clarity
* suggest concise description wording
* suggest success criteria for King/Queen commits
* suggest estimate points based on similar past commits and linked tickets

Output behavior:

* shown as editable suggestion
* never applied automatically

#### 2. Commit quality linting

Runs at save and pre-lock.

Checks include:

* vague titles
* missing success criteria for King/Queen
* parent-level RCDO linkage when leaf linkage is available
* duplicate or near-duplicate commit titles
* estimate inconsistency
* likely over-fragmented plan

Lint must clearly distinguish between hard validation and soft AI guidance.

#### 3. RCDO link suggestions

Uses commit text, linked ticket data, and prior similar commits to suggest a primary RCDO.

Requirements:

* only show when confidence clears a threshold
* always show rationale such as keyword match, linked ticket RCDO, or historical analog
* never auto-link

#### 4. Risk detection

Runs when a plan is locked and daily while LOCKED.

v1 risk signals:

* overcommit risk
* undercommit risk
* repeated carry-forward risk
* blocked critical work risk
* scope volatility risk

Rationale examples:

* current points exceed budget and exceed historical completed points
* two consecutive weeks of carry-forward on the same lineage
* King/Queen linked to blocked ticket for more than two business days

#### 5. Reconciliation assistance

At reconciliation start, AI may propose:

* likely outcome for unlinked commits based on notes and linked artifacts
* a draft summary of what changed after lock
* carry-forward recommendation with rationale

These are suggestions only and require user acceptance or editing.

#### 6. Manager summary

Managers may receive a short, generated team summary for the selected week:

* top strategic branches receiving effort
* unresolved exceptions
* repeated carry-forward patterns
* critical blocked items

This summary must cite the underlying objects in-product, not act as a black box narrative.

### Later-phase AI, not v1

The following are post-v1:

* personalized notification timing
* proactive decomposition of large commits into smaller tickets
* richer contextual retrieval across tickets, documents, and prior weeks
* cross-team dependency inference
* more advanced historical planning-pattern coaching
* conversational manager copilot

---

## 18. Notifications and Nudges

Notifications in v1 are primarily rules-based. AI may affect prioritization and wording, but not the existence of the core reminder schedule.

### Channels

* in-app notifications: required
* email digest: supported in v1
* host app notification center: supported if the host bridge exposes it

### Default events

* draft window opened
* lock due reminder
* auto-lock occurred
* reconciliation opened
* reconciliation due reminder
* repeated carry-forward reminder
* manager exception digest
* unassigned week-targeted ticket created
* critical linked ticket became blocked

### Priority behavior

* King-related exceptions: immediate in-app plus same-day email
* Queen/Rook exceptions: same-day digest
* Bishop/Knight/Pawn reminders: daily digest only

### Frequency controls

* no more than 2 direct reminders per user per task per day
* low-priority notifications must be batched
* notifications should respect team local time and business-hour defaults

### Missed-action data

Notification deliveries, opens, and outcomes should be stored because missed lock/reconcile patterns are relevant to manager reporting and future AI.

---

## 19. Permissions and Privacy Model

### Role model

#### IC

May:

* create/edit/lock/reconcile own plan
* create/edit own commits
* create tickets where they are reporter or assignee
* view basic current/next-week commit detail for peers on the same home team
* view full detail of own AI suggestions and historical patterns

May not:

* edit others’ plans
* view others’ private AI risk history
* edit RCDOs unless also a manager/admin

#### Manager

May:

* do everything an IC can do for their own plan
* view full detail for direct reports’ weekly plans and history
* view aggregate rollups for indirect reports
* comment on direct reports’ plans/commits
* manage capacity overrides for reports
* create/edit team-owned Defining Objectives and Outcomes
* create and assign team tickets
* view and resolve exception queues

May not:

* view detailed personal AI history for employees outside their direct reporting line
* see peer-level named data outside authorized team scope

#### Admin

May:

* manage org-wide settings and cadence
* manage all RCDOs
* access all team-level reporting
* perform data correction actions
* manage role assignments and feature flags

### Privacy defaults

The v1 privacy posture is intentionally conservative.

1. **Peer visibility within team**

   * visible: title, owner, priority, chess piece, primary RCDO, linked ticket, current state
   * hidden: AI risk flags, historical completion patterns, manager comments, detailed reconcile notes

2. **Historical visibility**

   * peers can see only current and next week basic detail
   * direct managers can see detailed historical plans for direct reports
   * skip-level leaders get aggregate summaries by default, not person-level detail

3. **Org-level reporting**

   * outside the management chain, reports default to aggregate team/RCDO views only
   * no individual leaderboards

4. **Time and duration data**

   * ticket status durations may be stored and used in risk logic
   * raw duration-in-status must not be presented as a person-level productivity metric

5. **Sensitive content**

   * no private-commit mode in v1
   * the module is not intended for HR-sensitive or medical notes; the UI should make that clear

### Recommended privacy default

This model optimizes for team coordination and manager usefulness while minimizing peer-level surveillance and cross-org personal visibility.

---

## 20. Manager Experience

The manager experience is centered on one weekly operating surface, not a stack of approvals.

### Team Week dashboard sections

1. **Overview**

   * lock compliance
   * reconcile compliance
   * total planned points
   * total achieved points
   * number of open exceptions

2. **By Person**

   * direct report plans
   * points vs budget
   * current risk flags
   * carry-forward streaks
   * major post-lock changes

3. **By RCDO**

   * points and commit counts by Rally Cry / DO / Outcome
   * planned vs achieved by branch
   * branch coverage gaps

4. **Chess Distribution**

   * team mix of King/Queen/Rook/Bishop/Knight/Pawn
   * concentration of critical work

5. **Uncommitted Work**

   * assigned tickets with no linked weekly commit
   * unassigned tickets targeted to the week

6. **Exceptions**

   * missed lock
   * missed reconcile
   * over-budget plan
   * added/changed King after lock
   * repeated carry-forward
   * high scope volatility

### Minimum manager actions required in v1

Managers are not required to approve every plan. The minimum expected actions are:

1. Review the team exception queue once per week by Tuesday 12:00 local time.
2. Resolve capacity override and critical scope-change exceptions within one business day.
3. Maintain team-owned RCDOs in an active, usable state.

Optional but encouraged:

* add comments on important plans
* rebalance unassigned week-targeted work
* follow up on repeated carry-forward

### Approval policy

V1 does **not** require manager approval for routine weekly plan lock or reconciliation.

V1 uses **exception review**, not blanket approval:

* initial weekly plans do not require approval
* over-budget plans at lock require user rationale and manager notification, not a blocking gate
* post-lock material changes generate manager review items, not silent edits
* added or materially changed King work always enters the manager exception queue

This is the recommended policy because true approval gates would create bottlenecks and damage adoption.

---

## 21. Reporting and Success Metrics

### Operational reporting in v1

The product must support the following reports:

* lock compliance by team and week
* reconcile compliance by team and week
* planned vs achieved points
* explicit carry-forward rate
* repeated carry-forward streaks
* points and commit counts by RCDO branch
* chess distribution by team and week
* post-lock scope change volume
* uncommitted assigned/unassigned weekly work
* AI suggestion acceptance rates
* manager exception aging

### v1 success metrics

#### Adoption and workflow health

* **85%** of eligible users have a locked plan each week by week 8 of rollout.
* **70%** of eligible users manually lock before cutoff by week 12.
* **80%** of locked plans are reconciled by the due time by week 12.

#### Alignment quality

* **90%** of locked commits are linked to an active Outcome or valid Defining Objective.
* **95%** of incomplete work appearing in the next week is created through explicit carry-forward lineage rather than copy/paste duplication.

#### Manager usefulness

* **75%** of managers with direct reports are weekly active in Team Week by week 8.
* Manager survey score on “I can see how my team’s weekly work maps to strategy” averages **4.0/5** or better in pilot.

#### User efficiency

* Median IC time to complete weekly planning is **under 10 minutes**.
* Median IC time to reconcile a week is **under 7 minutes**.
* Median manager time to review a team week is **under 15 minutes**.

#### AI usefulness

* At least **25%** of active users accept at least one AI suggestion per month.
* High-risk overcommit/carry-forward flags achieve **60% precision** or better, defined as the flagged condition materially appearing in reconciliation outcomes.
* Negative user feedback on AI suggestions remains below **10%** of suggestion impressions.

### What will not be reported

* no employee leaderboard
* no composite completion score
* no cross-team named ranking of individuals

---

## 22. Technical Architecture Considerations

### Micro-frontend integration assumption

The recommended assumption is a standard internal enterprise SPA host that can load route-level remotes. The Weekly Commit Module should follow a pragmatic remote pattern:

* route-level micro-frontend remote mounted under the PA host app
* TypeScript strict mode
* host provides SSO identity, navigation, design tokens, feature flags, telemetry hooks, and notification bridge
* remote owns domain routing, page composition, API client, and local state

A Module Federation-style remote is the most practical assumption for v1. If the host uses a different remote loading mechanism, the integration contract should remain the same.

### Backend shape

Recommended backend architecture:

* Java 21 service
* modular monolith for v1 rather than multiple distributed services
* REST/JSON APIs with OpenAPI contract
* background job workers for lifecycle transitions, rollups, notifications, and AI tasks
* relational SQL database, with PostgreSQL as the recommended implementation

### Why a modular monolith

This product’s complexity is in workflow, data integrity, and read models, not service decomposition. A modular monolith reduces coordination cost while still allowing clear bounded modules:

* weekly planning
* reconciliation
* RCDOs
* tickets
* notifications
* AI suggestions
* reporting/read models

### Host/remote contract

The host should pass:

* authenticated user identity
* team and manager chain context
* environment and feature flags
* telemetry callback or SDK access
* theme/design tokens
* notification center bridge

The remote should expose:

* full page routes
* optional summary widgets for host dashboards
* typed events for deep-link navigation and telemetry

### AI architecture boundary

AI must be behind a provider abstraction. The core workflow cannot depend on a single model vendor. If AI fails or is disabled, the module must remain fully usable.

### Read model strategy

Manager dashboards and RCDO rollups should not run large live joins against transactional tables on every request. Use derived SQL read models/materialized tables refreshed by events/jobs.

---

## 23. Data Model and Storage Strategy

### Transactional records

These should be stored as source-of-truth transactional tables:

* organization, team, user, membership, home team
* weekly_plan
* commit
* lock_snapshot_header
* lock_snapshot_commit
* scope_change_event
* reconcile_snapshot_header
* reconcile_snapshot_commit
* carry_forward_link
* work_item
* work_item_status_history
* work_item_comment
* rcdo_node
* rcdo_change_log
* manager_comment
* capacity_override
* notification
* notification_delivery
* ai_suggestion
* ai_feedback
* audit_log

### Derived / aggregated records

These should be stored as derived tables or materialized read models:

* user_week_fact
* team_week_rollup
* rcdo_week_rollup
* compliance_fact
* carry_forward_fact
* risk_feature tables
* dashboard summary tables

### Snapshot strategy

Lock and reconcile should each store:

* normalized snapshot rows for queryability
* denormalized JSON payload for exact historical replay and audit

This dual storage is justified because the product needs both reliable analytics and immutable historical truth.

### Storage decisions for AI and analytics

AI and reporting should rely primarily on derived feature tables, not direct request-path scans of raw historical events.

Examples of derived fields:

* points planned vs achieved
* repeated carry-forward count
* lock lateness
* reconcile lateness
* status-duration metrics for linked tickets
* unplanned added work points
* RCDO coverage distribution
* manager exception backlog

### History and retention

Recommended retention target:

* weekly plans, snapshots, change events, and carry-forward lineage: 24 months online minimum
* audit logs: per corporate audit policy, ideally 24 months online minimum
* AI suggestion and feedback logs: 12 months minimum

If corporate policy differs, policy wins.

### Consistency guarantees

* lifecycle jobs must be idempotent
* lock/reconcile snapshot writes must be transactional
* derived rollups may be eventually consistent, but should update within 5 minutes for current-week views

---

## 24. Non-Functional Requirements

### Performance

* P95 initial page load for My Week and Team Week: under 2.5 seconds on the corporate network
* P95 commit create/edit/save interaction: under 500 ms server time
* P95 manager dashboard query: under 1.5 seconds from derived read model

### Availability

* 99.9% monthly availability during business hours
* lifecycle jobs must retry safely after failure

### Scale

The system should support, at minimum:

* 10,000 active eligible users
* 1,000 concurrent users during Monday peak
* 250,000+ commits per year
* millions of ticket status history rows without dashboard degradation

### Security

* SSO-based authentication via host app
* backend-enforced RBAC
* encryption in transit and at rest
* audit logging for privileged actions

### Accessibility

* WCAG 2.1 AA target
* keyboard-accessible drag/reorder alternative required
* readable state and priority indicators without color dependence

### Observability

* structured logs
* metrics for job success/failure
* audit trail for lifecycle transitions
* tracing across remote and backend
* feature flag support for staged rollout

### Reliability

* no duplicate snapshots
* no duplicate carry-forward links from retry behavior
* no partial lock/reconcile writes

### Privacy

* row-level filtering enforced in backend, not only frontend
* AI requests must respect user/team authorization boundaries

---

## 25. Risks, Abuse Cases, and Safeguards

### Risk 1: The product is used as de facto performance surveillance

**Safeguards**

* no productivity score
* no employee ranking
* no public individual history outside management chain
* direct policy language in-product and in rollout training

### Risk 2: Users game chess pieces or estimates

**Safeguards**

* max 1 King, max 2 Queens
* explicit rationale for major post-lock changes
* manager rollups show unusual distributions
* AI can flag suspicious overuse patterns, but not score them

### Risk 3: Users dump every tiny task into the system

**Safeguards**

* soft warning above 8 commits
* guidance to group small work into Pawns
* capacity meter based on points, not count alone

### Risk 4: RCDO quality decays and linkage becomes meaningless

**Safeguards**

* hierarchy ownership rules
* archived nodes blocked for new work
* managers accountable for team branches
* parent-level linking restricted

### Risk 5: AI becomes noisy and loses trust

**Safeguards**

* show only above confidence threshold
* separate hard validation from AI suggestions
* store accept/dismiss feedback
* disable low-performing suggestion types independently

### Risk 6: Auto-lock creates bad historical baselines

**Safeguards**

* manual lock remains the compliance standard
* auto-lock records validation errors clearly
* manager exception queue highlights bad auto-locks
* post-lock change events preserve honest correction without rewriting history

### Risk 7: Unassigned work becomes a shadow backlog with no owner

**Safeguards**

* separate uncommitted-work section on team dashboard
* age and target-week filters
* manager reminders for aging unassigned weekly work

### Risk 8: Sensitive personal details are entered into reconcile notes

**Safeguards**

* UI warning not to store HR/medical/sensitive personal detail
* no private notes feature in v1
* admin correction process for improper content

---

## 26. v1 Scope

### In scope

* user weekly plan creation and commit CRUD
* one plan per user per week
* required primary RCDO linkage
* chess taxonomy and priority ordering
* capacity points and budget meter
* manual lock with baseline capture
* auto-lock at cutoff
* structured post-lock scope change events
* reconciliation view with planned vs actual
* explicit carry-forward with lineage
* minimal native tickets/work items with status workflow and history
* manager team dashboard and exception queue
* RCDO hierarchy management
* rules-based notifications
* AI v1:

  * drafting assistance
  * commit quality linting
  * RCDO suggestions
  * conservative risk flags
  * reconciliation assistance
* route-level micro-frontend integration into PA host app
* audit logs and derived rollups

### Explicit v1 limits

* no many-to-many commit-to-RCDO
* no separate team plan object
* no advanced dependency graph
* no mobile-first experience requirement
* no external Jira/OKR integration dependency
* no full sprint board or advanced backlog planning
* no autonomous AI agent behavior

---

## 27. Post-v1 / Future Scope

* Jira, GitHub, or other external work-system integrations
* many-to-many commit-to-RCDO support
* dependency tracking across users and teams
* richer ticket boards and sprint planning
* calendar and PTO integration for automatic capacity adjustment
* more advanced AI context retrieval
* personalized AI coaching on planning patterns
* cross-team risk and dependency summaries
* conversational search over plans, tickets, and RCDOs
* configurable custom chess policies by org

---

## 28. Open Questions

These are the remaining questions that should be validated, but they do not block writing or building v1.

1. **Host integration detail:** confirm the exact remote-loading mechanism and shell contract with the PA platform team.
2. **Org directory source of truth:** confirm whether home team and manager chain are provided by the host/identity layer or a separate internal service.
3. **Historical migration:** decide whether any 15Five historical data needs read-only migration, or whether rollout begins with fresh history.
4. **Nonstandard workweeks:** validate whether pilot teams need non-Monday/Friday defaults on day one, or whether team-level overrides are sufficient after pilot.

---

## 29. Acceptance Criteria

1. A DRAFT weekly plan exists or is created on first access for every active eligible user once the draft window opens.
2. A user can create, edit, delete, and reorder commits in DRAFT.
3. Manual lock is blocked if required fields are missing or chess-piece hard limits are violated.
4. Auto-lock occurs at the configured cutoff and creates a baseline snapshot even if the draft is incomplete.
5. A locked plan cannot be silently edited; all post-lock changes create append-only scope change events.
6. Reconciliation presents baseline values, scope changes, linked ticket evidence, and outcome inputs in one view.
7. A linked ticket in `Done` auto-marks the commit `Achieved` at reconciliation snapshot time.
8. Unlinked work and linked work with incomplete tickets can be reconciled manually.
9. Carry-forward creates a new commit object with provenance and does not mutate the original week’s commit.
10. If carry-forward occurs after the destination week is already locked, the new commit is added as a post-lock scope change event in that week.
11. Team Week shows committed work, assigned-but-uncommitted tickets, and unassigned week-targeted tickets without double counting.
12. Managers can view full detail for direct reports and only aggregate detail for indirect reports by default.
13. Peers on the same team can see current and next week basic commit detail, but not AI risk flags, manager comments, or detailed reconcile notes.
14. Admins and authorized managers can create, edit, activate, archive, and move RCDO nodes within their allowed scope.
15. Archived RCDO nodes remain visible historically but cannot be linked to new commits.
16. Every AI suggestion shows rationale, can be accepted or dismissed, and stores model version and response outcome.
17. AI downtime does not prevent planning, locking, reconciling, or carry-forward.
18. Manager dashboard loads from derived read models and meets the stated performance targets.
19. Lifecycle jobs are idempotent and do not create duplicate lock snapshots, reconcile snapshots, or carry-forward links.
20. All lifecycle transitions, RCDO edits, privileged actions, and AI suggestions are auditable.

---

## 30. Critical Review of the Proposed PRD

### The 5 biggest delivery risks

1. **This v1 is still broad.** Weekly planning, RCDO authoring, native tickets, manager dashboards, and AI assistance are each meaningful workstreams. Delivering all five together will pressure scope, QA, and rollout readiness.

2. **Historical snapshot integrity is easy to get wrong.** Lock snapshots, scope change events, reconciliation snapshots, and carry-forward lineage need careful transactional design. If that model is unstable, reporting and trust collapse.

3. **Permissions are subtle.** The distinction between peers, direct managers, skip-level leaders, and admins is simple on paper but easy to misimplement in APIs and derived rollups.

4. **Micro-frontend host uncertainty is real.** The PRD makes a sensible remote-pattern assumption, but if the PA host shell lacks stable auth, navigation, or notification contracts, the frontend team will absorb unexpected platform work.

5. **Dashboard performance depends on read models from the start.** If the team tries to build manager views directly off transactional joins first and optimize later, Monday peak performance will likely be poor and hard to recover.

### The 5 biggest product risks

1. **Users may resist required structure.** Mandatory RCDO linkage, chess piece selection, and estimate points add friction. If the UX is not very tight, adoption will lag.

2. **RCDO quality may be too weak for the feature to pay off.** If managers do not maintain Outcomes or if branches are too coarse, the product will technically enforce linkage while still producing weak alignment data.

3. **The native ticket stance may conflict with existing behavior.** Even though the PRD scopes ticketing modestly, teams already using Jira-like systems may not want to duplicate work item management internally.

4. **Exception review may still feel managerial.** The PRD avoids approval gates, but users can still interpret exception queues and carry-forward tracking as surveillance if rollout framing is poor.

5. **AI may underdeliver at launch.** Conservative AI is the right posture, but conservative AI can also feel underwhelming unless the first few suggestion types are very accurate and clearly useful.

### Areas likely over-scoped for v1

1. **Native ticketing could sprawl quickly.** Minimal CRUD and status workflow are reasonable; anything beyond that should be resisted in v1.
2. **Manager-generated AI summaries may be optional for first release.** They are useful but not essential to prove the core workflow.
3. **Unassigned-work handling is valuable, but it adds a second planning surface.** If schedule tightens, keep it simple and list-based.
4. **Flexible cadence configuration at both org and team level increases edge cases.** A fixed pilot cadence may be safer before broad configurability.
5. **Detailed derived analytics may outpace adoption needs.** Core compliance, alignment, and carry-forward reports matter more than a wide set of trend views in the first release.

### Assumptions that should be validated in discovery or pilot rollout

1. **Capacity model:** users will accept a required 1/2/3/5/8 points field and managers will find a default 10-point budget intuitive.
2. **Cadence:** Monday 10:00 reconcile due and Monday 12:00 lock due is acceptable for most teams.
3. **Privacy default:** same-team visibility of current and next week basic commits will feel useful rather than intrusive.
4. **RCDO target rule:** forcing direct linkage to Outcomes when available will be practical, not overly rigid.
5. **Single home-team plan:** one weekly plan per user, tied to a primary team, is sufficient for matrixed organizations during the pilot.

Overall, the PRD is coherent and buildable, but the highest-risk mistake would be trying to launch the full ticketing surface, full manager analytics set, and broad AI layer at the same time. The core weekly contract workflow should remain the center of gravity.

