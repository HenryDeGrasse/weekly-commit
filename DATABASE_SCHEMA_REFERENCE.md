# Complete Database Schema Reference — Weekly Commit Module

**Last Updated:** V13 (Backfill Read Models)  
**Database:** PostgreSQL with Flyway migrations  
**Current Data:** 12 weeks of demo seed data (2026-01-05 → 2026-03-23)

---

## Table of Contents

1. [Organizational & User Management](#organizational--user-management)
2. [RCDO Hierarchy](#rcdo-hierarchy-rally-cry--defining-objectives--outcomes)
3. [Weekly Planning & Commits](#weekly-planning--commits)
4. [Work Items (Tickets)](#work-items-tickets)
5. [Snapshots & Immutable Records](#snapshots--immutable-records)
6. [Scope Changes & Carry-Forward](#scope-changes--carry-forward)
7. [Manager Review & Exceptions](#manager-review--exceptions)
8. [Notifications](#notifications)
9. [AI Suggestions & Feedback](#ai-suggestions--feedback)
10. [Audit Log](#audit-log)
11. [Configuration](#configuration)
12. [Derived Read Models (for Reporting)](#derived-read-models-for-reporting)
13. [Indexes](#indexes)
14. [Seed Data Overview](#seed-data-overview)

---

## Organizational & User Management

### `organization`
Central organization record. Supports multi-tenancy.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `name` | TEXT | NOT NULL | Organization display name |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- None (primary key index automatic)

**Seed Data:**
- 1 org: `Acme Corp` (ID: `00000000-0000-0000-0000-000000000100`)

---

### `team`
Team within an organization. Supports nested teams (parent-child hierarchy).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `organization_id` | UUID | NOT NULL, FK(organization.id) | Parent organization |
| `name` | TEXT | NOT NULL | Team display name |
| `parent_team_id` | UUID | FK(team.id), NULLABLE | For team hierarchies |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_team_org` ON `(organization_id)`

**Seed Data:**
- 1 team: `Engineering` (ID: `00000000-0000-0000-0000-000000000010`)

---

### `user_account`
User records. Each user belongs to an organization and has a home team.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `organization_id` | UUID | NOT NULL, FK(organization.id) | User's org |
| `email` | TEXT | NOT NULL | Email address |
| `display_name` | TEXT | NOT NULL | Full name |
| `home_team_id` | UUID | FK(team.id), NULLABLE | Default team |
| `role` | TEXT | NOT NULL, CHECK IN ('IC', 'MANAGER') | User role; default 'IC' |
| `weekly_capacity_points` | SMALLINT | NOT NULL | Default: 10 |
| `active` | BOOLEAN | NOT NULL | Default: TRUE |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Constraints:**
- `uq_user_org_email` UNIQUE `(organization_id, email)` — one email per org

**Indexes:**
- `idx_user_org` ON `(organization_id)`
- `idx_user_home_team` ON `(home_team_id)`

**Seed Data (V9 + V11):**
- `dev@example.com` — Dev User, MANAGER, 10 pts
- `manager@example.com` — Manager One, MANAGER, 10 pts
- `alice@example.com` — Alice Chen, IC, 10 pts
- `bob@example.com` — Bob Martinez, IC, 10 pts
- `carol@example.com` — Carol Nguyen, IC, 8 pts
- `dan@example.com` — Dan Okafor, IC, 10 pts

---

### `team_membership`
Join table: links users to teams with roles.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `team_id` | UUID | NOT NULL, FK(team.id) | Team |
| `user_id` | UUID | NOT NULL, FK(user_account.id) | User |
| `role` | TEXT | NOT NULL | Default: 'MEMBER'; e.g., 'MEMBER', 'LEAD' |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Constraints:**
- `uq_team_membership` UNIQUE `(team_id, user_id)`

**Indexes:**
- `idx_team_membership` ON `(user_id)`

**Seed Data:**
- 6 team memberships (all 6 users in Engineering team, various roles)

---

## RCDO Hierarchy (Rally Cry → Defining Objectives → Outcomes)

### `rcdo_node`
RCDO hierarchy: Rally Cry → Defining Objective → Outcome. Supports nested structure.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `node_type` | TEXT | NOT NULL, CHECK IN ('RALLY_CRY', 'DEFINING_OBJECTIVE', 'OUTCOME') | Hierarchy level |
| `status` | TEXT | NOT NULL, CHECK IN ('DRAFT', 'ACTIVE', 'ARCHIVED') | Default: 'DRAFT' |
| `parent_id` | UUID | FK(rcdo_node.id), NULLABLE | Parent node (null for Rally Cries) |
| `title` | TEXT | NOT NULL | Node display title |
| `description` | TEXT | NULLABLE | Optional details |
| `owner_team_id` | UUID | FK(team.id), NULLABLE | Owning team |
| `owner_user_id` | UUID | FK(user_account.id), NULLABLE | Owning user |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_rcdo_parent` ON `(parent_id)`
- `idx_rcdo_status` ON `(status)`

**Seed Data (V9):**

Rally Cries (2):
- `Grow the Business` (ACTIVE)
- `Operational Excellence` (ACTIVE)

Defining Objectives (3):
- `Increase Revenue` (under Grow the Business)
- `Reduce Churn` (under Grow the Business)
- `Ship Faster` (under Operational Excellence)

Outcomes (4):
- `Close 10 Enterprise Deals` (under Increase Revenue)
- `Expand to EMEA Market` (under Increase Revenue)
- `Improve Onboarding NPS to 60+` (under Reduce Churn)
- `Deploy Daily by Q2` (under Ship Faster)

---

### `rcdo_change_log`
Immutable change history for RCDO nodes.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `rcdo_node_id` | UUID | NOT NULL, FK(rcdo_node.id) | Node being changed |
| `changed_by_user_id` | UUID | NOT NULL, FK(user_account.id) | Who made the change |
| `change_summary` | TEXT | NOT NULL | Description of change |
| `previous_value` | JSONB | NULLABLE | Before state |
| `new_value` | JSONB | NULLABLE | After state |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_rcdo_change_node` ON `(rcdo_node_id)`

**Seed Data:**
- Empty (no changes logged in seed)

---

## Weekly Planning & Commits

### `weekly_plan`
One per user per week. Tracks plan state (DRAFT → LOCKED → RECONCILING → RECONCILED).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `owner_user_id` | UUID | NOT NULL, FK(user_account.id) | User owning the plan |
| `team_id` | UUID | NOT NULL, FK(team.id) | User's team context |
| `week_start_date` | DATE | NOT NULL | Monday of week (ISO calendar) |
| `state` | TEXT | NOT NULL, CHECK IN ('DRAFT', 'LOCKED', 'RECONCILING', 'RECONCILED') | Workflow state; default 'DRAFT' |
| `lock_deadline` | TIMESTAMPTZ | NOT NULL | When plan must be locked |
| `reconcile_deadline` | TIMESTAMPTZ | NOT NULL | When reconciliation must complete |
| `capacity_budget_points` | SMALLINT | NOT NULL | Available points for the week; default 10 |
| `is_compliant` | BOOLEAN | NOT NULL | Within capacity; default TRUE |
| `system_locked_with_errors` | BOOLEAN | NOT NULL | Auto-locked due to deadline miss; default FALSE |
| `lock_snapshot_id` | UUID | FK(lock_snapshot_header.id), NULLABLE | Back-reference to lock snapshot |
| `reconcile_snapshot_id` | UUID | FK(reconcile_snapshot_header.id), NULLABLE | Back-reference to reconcile snapshot |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Constraints:**
- `uq_plan_user_week` UNIQUE `(owner_user_id, week_start_date)` — one plan per user per week
- FK constraints to lock/reconcile snapshots added after those tables created

**Indexes:**
- `idx_plan_owner_week` ON `(owner_user_id, week_start_date)`
- `idx_plan_team_week` ON `(team_id, week_start_date)`
- `idx_plan_state` ON `(state)`

**Seed Data (V11):**
- **60 plans total** (12 weeks × 5 users)
- Weeks 1-10 (2026-01-05 → 2026-03-09): state = RECONCILED
- Week 11 (2026-03-16): state = LOCKED
- Week 12 (2026-03-23): state = DRAFT (current week)
- All with `is_compliant = true` except Week 3 user-005 which is false

---

### `weekly_commit`
Individual commit (chess-piece work item) within a plan.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `plan_id` | UUID | NOT NULL, FK(weekly_plan.id) | Parent plan |
| `owner_user_id` | UUID | NOT NULL, FK(user_account.id) | User who committed to this work |
| `title` | TEXT | NOT NULL | Commit title |
| `description` | TEXT | NULLABLE | Optional details |
| `chess_piece` | TEXT | NOT NULL, CHECK IN ('KING','QUEEN','ROOK','BISHOP','KNIGHT','PAWN') | Work level/importance |
| `priority_order` | INTEGER | NOT NULL | Sequencing within plan (1=highest) |
| `rcdo_node_id` | UUID | FK(rcdo_node.id), NULLABLE | Link to RCDO objective |
| `work_item_id` | UUID | FK(work_item.id), NULLABLE | Link to ticket system |
| `estimate_points` | SMALLINT | CHECK IN (1, 2, 3, 5, 8), NULLABLE | Fibonacci estimate |
| `success_criteria` | TEXT | NULLABLE | Definition of done |
| `outcome` | TEXT | CHECK IN ('ACHIEVED','PARTIALLY_ACHIEVED','NOT_ACHIEVED','CANCELED'), NULLABLE | Post-reconciliation status |
| `outcome_notes` | TEXT | NULLABLE | Reconciliation notes |
| `carry_forward_source_id` | UUID | FK(weekly_commit.id), NULLABLE | If carrying forward, source commit |
| `carry_forward_streak` | INTEGER | NOT NULL | How many weeks carried forward; default 0 |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Constraints:**
- `fk_weekly_commit_work_item` FK to `work_item(id)` (added in migration after work_item table created)

**Indexes:**
- `idx_commit_plan` ON `(plan_id)`
- `idx_commit_owner` ON `(owner_user_id)`
- `idx_commit_rcdo` ON `(rcdo_node_id)`
- `idx_commit_work_item` ON `(work_item_id)`

**Seed Data (V11):**
- **~250+ commits** across 60 plans
- Each user has 3-7 commits per week depending on week
- Chess pieces: varied distribution (KING = strategic, PAWN = operational)
- Outcomes: ACHIEVED (most), PARTIALLY_ACHIEVED, NOT_ACHIEVED (some), CANCELED (few)
- Carry-forward streak: max 1-2 weeks in demo data
- Week 1-10: all have outcomes; Week 11-12: no outcomes yet (LOCKED/DRAFT)

---

## Work Items (Tickets)

### `work_item`
Native ticket system. Tracks work across teams.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `team_id` | UUID | NOT NULL, FK(team.id) | Owning team |
| `key` | TEXT | NOT NULL | Unique ticket key (e.g., 'ENG-101') |
| `title` | TEXT | NOT NULL | Ticket title |
| `description` | TEXT | NULLABLE | Full description |
| `status` | TEXT | NOT NULL, CHECK IN ('TODO','IN_PROGRESS','BLOCKED','DONE','CANCELED') | Workflow state; default 'TODO' (changed from BACKLOG/READY in V3) |
| `assignee_user_id` | UUID | FK(user_account.id), NULLABLE | Assigned user |
| `reporter_user_id` | UUID | NOT NULL, FK(user_account.id) | Who created the ticket |
| `estimate_points` | SMALLINT | CHECK IN (1, 2, 3, 5, 8), NULLABLE | Fibonacci estimate |
| `rcdo_node_id` | UUID | FK(rcdo_node.id), NULLABLE | Link to strategic objective |
| `target_week_start_date` | DATE | NULLABLE | Target completion week |
| `priority` | TEXT | NOT NULL, CHECK IN ('CRITICAL','HIGH','MEDIUM','LOW') | Priority level; added V3, default 'MEDIUM' |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Constraints:**
- `uq_work_item_key` UNIQUE `(team_id, key)` — one key per team
- `chk_work_item_priority` CHECK (V3 migration)
- `chk_work_item_status` CHECK (V3 migration; updated status values)

**Indexes:**
- `idx_work_item_team` ON `(team_id)`
- `idx_work_item_assignee` ON `(assignee_user_id)`
- `idx_work_item_week` ON `(target_week_start_date)`
- `idx_work_item_status` ON `(status)`
- `idx_work_item_priority` ON `(priority)`

**Seed Data (V11):**
- **12 work items** (ENG-101 to ENG-112)
- Enterprise features: SSO, billing, EMEA compliance, onboarding, CI/CD
- Status mix: DONE (5), IN_PROGRESS (4), TODO (2), BLOCKED (1)
- Priority mix: CRITICAL (2), HIGH (4), MEDIUM (4), LOW (2)

**Example Tickets:**
- ENG-101: Build enterprise SSO integration (DONE, HIGH)
- ENG-103: EMEA data residency compliance (IN_PROGRESS, CRITICAL)
- ENG-105: CI/CD pipeline optimization (DONE, HIGH)

---

### `work_item_status_history`
Immutable history of status changes.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `work_item_id` | UUID | NOT NULL, FK(work_item.id) | Ticket being tracked |
| `from_status` | TEXT | NULLABLE | Previous status |
| `to_status` | TEXT | NOT NULL | New status |
| `changed_by_user_id` | UUID | NOT NULL, FK(user_account.id) | Who changed it |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:** None

**Seed Data:**
- Empty (no history logged in seed)

---

### `work_item_comment`
Comments on work items.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `work_item_id` | UUID | NOT NULL, FK(work_item.id) | Parent ticket |
| `author_user_id` | UUID | NOT NULL, FK(user_account.id) | Comment author |
| `content` | TEXT | NOT NULL | Comment text |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:** None

**Seed Data:**
- Empty (no comments in seed)

---

## Snapshots & Immutable Records

### `lock_snapshot_header`
Immutable snapshot of plan state at lock time. One per plan.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `plan_id` | UUID | NOT NULL, FK(weekly_plan.id) | Plan being locked |
| `locked_at` | TIMESTAMPTZ | NOT NULL | When locked; default `now()` |
| `locked_by_system` | BOOLEAN | NOT NULL | Was this an auto-lock?; default FALSE |
| `snapshot_payload` | JSONB | NOT NULL | Full plan state as JSON |

**Constraints:**
- `uq_lock_snapshot_plan` UNIQUE `(plan_id)` — one snapshot per plan

**Indexes:**
- `idx_lock_snap_commit` ON `(snapshot_id)` (on _commit table below)

**Seed Data (V11):**
- **55 lock snapshots** (weeks 1-11, all 5 users)
- Most locked manually by user; 2 auto-locked (week 1 & 3, user-005)

---

### `lock_snapshot_commit`
Individual commits within a lock snapshot (denormalized).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `snapshot_id` | UUID | NOT NULL, FK(lock_snapshot_header.id) | Parent snapshot |
| `commit_id` | UUID | NOT NULL, FK(weekly_commit.id) | Commit being captured |
| `snapshot_data` | JSONB | NOT NULL | Commit state as JSON |

**Indexes:**
- `idx_lock_snap_commit` ON `(snapshot_id)`

**Seed Data:**
- One row per commit in each lock snapshot (~3-7 per user per week)

---

### `reconcile_snapshot_header`
Immutable snapshot of plan state at reconciliation. One per plan that was reconciled.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `plan_id` | UUID | NOT NULL, FK(weekly_plan.id) | Plan being reconciled |
| `reconciled_at` | TIMESTAMPTZ | NOT NULL | When reconciled; default `now()` |
| `snapshot_payload` | JSONB | NOT NULL | Full plan state as JSON |

**Constraints:**
- `uq_reconcile_snapshot_plan` UNIQUE `(plan_id)` — one per plan

**Indexes:**
- `idx_recon_snap_commit` ON `(snapshot_id)` (on _commit table below)

**Seed Data (V11):**
- **50 reconcile snapshots** (weeks 1-10, all 5 users)
- Week 11-12 have no reconcile snapshots (not yet RECONCILED)

---

### `reconcile_snapshot_commit`
Individual commits within a reconcile snapshot (denormalized).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `snapshot_id` | UUID | NOT NULL, FK(reconcile_snapshot_header.id) | Parent snapshot |
| `commit_id` | UUID | NOT NULL, FK(weekly_commit.id) | Commit being captured |
| `outcome` | TEXT | CHECK IN ('ACHIEVED','PARTIALLY_ACHIEVED','NOT_ACHIEVED','CANCELED'), NULLABLE | Final outcome |
| `snapshot_data` | JSONB | NOT NULL | Commit state with outcome as JSON |

**Indexes:**
- `idx_recon_snap_commit` ON `(snapshot_id)`

**Seed Data:**
- One row per commit in each reconcile snapshot with outcome populated

---

## Scope Changes & Carry-Forward

### `scope_change_event`
Post-lock changes to commits. Append-only audit trail.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `plan_id` | UUID | NOT NULL, FK(weekly_plan.id) | Which plan |
| `commit_id` | UUID | FK(weekly_commit.id), NULLABLE | Which commit (NULL if plan-level) |
| `category` | TEXT | NOT NULL, CHECK IN ('COMMIT_ADDED','COMMIT_REMOVED','ESTIMATE_CHANGED','CHESS_PIECE_CHANGED','RCDO_CHANGED','PRIORITY_CHANGED') | Change type |
| `changed_by_user_id` | UUID | NOT NULL, FK(user_account.id) | Who changed it |
| `reason` | TEXT | NOT NULL | Why the change |
| `previous_value` | JSONB | NULLABLE | Old value |
| `new_value` | JSONB | NULLABLE | New value |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_scope_change_plan` ON `(plan_id)`
- `idx_scope_change_commit` ON `(commit_id)`

**Seed Data (V11):**
- **6 scope change events**
  - Week 3: Carol's estimate changed (EU database → 5 pts)
  - Week 4: Bob added a commit (billing unit tests)
  - Week 6: Dan prioritized health dashboard (→ priority 1)
  - Week 7: Bob removed rev rec commit
  - Week 9: Alice changed RCDO linkage
  - Week 10: Carol elevated chess piece (Rook → Queen)

---

### `carry_forward_link`
Explicit linkage between carry-forward commits and their sources.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `source_commit_id` | UUID | NOT NULL, FK(weekly_commit.id) | Original commit |
| `target_commit_id` | UUID | NOT NULL, FK(weekly_commit.id) | Carry-forward commit |
| `reason` | TEXT | NOT NULL, CHECK IN ('BLOCKED_BY_DEPENDENCY','SCOPE_EXPANDED','REPRIORITIZED','RESOURCE_UNAVAILABLE','TECHNICAL_OBSTACLE','EXTERNAL_DELAY','UNDERESTIMATED','STILL_IN_PROGRESS') | Why carried forward |
| `reason_notes` | TEXT | NULLABLE | Additional context |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Constraints:**
- `uq_carry_forward` UNIQUE `(source_commit_id, target_commit_id)` — one link per source-target pair

**Indexes:**
- `idx_carry_forward_src` ON `(source_commit_id)`
- `idx_carry_forward_tgt` ON `(target_commit_id)`

**Seed Data (V11):**
- **~10 carry-forward links**
  - Dan: wireframes (wk1 → wk2, blocked by design system)
  - Alice: Azure AD SSO (wk3 → wk4, underestimated)
  - Carol: EU database (wk3 → wk4, external delay — procurement)
  - Bob: proration fix (wk4 → wk5, underestimated)
  - Bob: webhook DLQ (wk5 → wk6, still in progress)
  - Carol: GDPR PDF (wk5 → wk6, still in progress)
  - Dan: health dashboard (wk5 → wk6, blocked by API)
  - Carol: EU migration (wk6 → wk7, still in progress)
  - Dev: E2E tests (wk7 → wk8, scope expanded)
  - Bob: rev rec (wk7 → wk8, underestimated)
  - Alice: SSO wave 2 (wk9 → wk10, still in progress)
  - Bob: tax engine (wk9 → wk10, external delay — vendor)
  - Dan: personalized onboarding (wk10 → wk11, still in progress)

---

## Manager Review & Exceptions

### `manager_review_exception`
Flags issues that need manager attention (auto-lock, late lock, carry-forward streaks, etc.).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `team_id` | UUID | NOT NULL, FK(team.id) | Which team |
| `plan_id` | UUID | FK(weekly_plan.id), NULLABLE | Which plan (may be null for team-level) |
| `user_id` | UUID | NOT NULL, FK(user_account.id) | User with the issue |
| `exception_type` | TEXT | NOT NULL | E.g., 'AUTO_LOCKED', 'REPEATED_CARRY_FORWARD' |
| `severity` | TEXT | NOT NULL | 'HIGH', 'MEDIUM', 'LOW' |
| `description` | TEXT | NOT NULL | What happened |
| `week_start_date` | DATE | NOT NULL | Which week |
| `resolved` | BOOLEAN | NOT NULL | Has it been addressed?; default FALSE |
| `resolution` | TEXT | NULLABLE | How was it resolved |
| `resolved_at` | TIMESTAMPTZ | NULLABLE | When resolved |
| `resolved_by_id` | UUID | FK(user_account.id), NULLABLE | Who resolved it |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_mgr_exc_team_week` ON `(team_id, week_start_date)`
- `idx_mgr_exc_plan_type` ON `(plan_id, exception_type)`
- `idx_mgr_exc_user` ON `(user_id)`
- `idx_mgr_exc_resolved` ON `(resolved)`

**Seed Data (V11):**
- **4 exceptions**
  - Week 1, Carol: AUTO_LOCKED (on PTO) — RESOLVED
  - Week 3, Carol: AUTO_LOCKED (recurring) — RESOLVED
  - Week 7, Bob: REPEATED_CARRY_FORWARD (rev rec) — RESOLVED
  - Week 11, Dan: REPEATED_CARRY_FORWARD (personalized onboarding) — UNRESOLVED

---

### `manager_comment`
Manager feedback on plans or individual commits.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `plan_id` | UUID | FK(weekly_plan.id), NULLABLE | Plan being commented on |
| `commit_id` | UUID | FK(weekly_commit.id), NULLABLE | Commit being commented on |
| `author_user_id` | UUID | NOT NULL, FK(user_account.id) | Comment author (usually manager) |
| `content` | TEXT | NOT NULL | Comment text |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_mgr_comment_plan` ON `(plan_id)`
- `idx_mgr_comment_commit` ON `(commit_id)`

**Seed Data (V11):**
- **9 manager comments** from Manager One (user-002)
  - Week 1: SSO research, wireframe blocker
  - Week 3: Procurement delay pivot
  - Week 5: Deploy frequency dashboard, webhook DLQ
  - Week 7: SSO production rollout, rev rec concern
  - Week 9: Zero-downtime deploy milestone
  - Week 10: SOC2 submission success

---

## Notifications

### `notification`
Notifications sent to users (draft windows, lock deadlines, exceptions, etc.).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `recipient_user_id` | UUID | NOT NULL, FK(user_account.id) | Who receives it |
| `notification_type` | TEXT | NOT NULL | E.g., 'DRAFT_WINDOW_OPENED', 'LOCK_DUE_REMINDER', 'EXCEPTION' |
| `title` | TEXT | NOT NULL | Short title |
| `body` | TEXT | NOT NULL | Notification text |
| `reference_id` | UUID | NULLABLE | Related entity ID (plan, commit, exception) |
| `reference_type` | TEXT | NULLABLE | Related entity type ('WEEKLY_PLAN', 'EXCEPTION', etc.) |
| `read` | BOOLEAN | NOT NULL | Has user read it?; default FALSE |
| `priority` | TEXT | NOT NULL, CHECK IN ('HIGH','MEDIUM','LOW') | Urgency; added V4, default 'MEDIUM' |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_notif_recipient` ON `(recipient_user_id, read)`
- `idx_notif_delivery` ON `(notification_id)` (on _delivery table below)
- `idx_notif_priority` ON `(recipient_user_id, priority, read)` (added V4)

**Seed Data (V11):**
- **13 notifications**
  - Most: DRAFT_WINDOW_OPENED, LOCK_DUE_REMINDER, RECONCILIATION_OPENED, RECONCILIATION_DUE_REMINDER
  - Some read (TRUE), some unread (FALSE)
  - Manager exception digest for Dan's carry-forward

---

### `notification_delivery`
Delivery tracking for notifications (email, push, SMS, etc.).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `notification_id` | UUID | NOT NULL, FK(notification.id) | Parent notification |
| `channel` | TEXT | NOT NULL | Delivery channel (e.g., 'EMAIL', 'PUSH', 'SMS') |
| `status` | TEXT | NOT NULL | 'PENDING', 'SENT', 'FAILED', 'OPENED'; default 'PENDING' |
| `sent_at` | TIMESTAMPTZ | NULLABLE | When sent |
| `opened_at` | TIMESTAMPTZ | NULLABLE | When opened (for tracking) |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_notif_delivery` ON `(notification_id)`

**Seed Data:**
- Empty (no delivery records in seed)

---

## AI Suggestions & Feedback

### `ai_suggestion`
AI-generated suggestions for commit titles, descriptions, estimates, etc.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `plan_id` | UUID | FK(weekly_plan.id), NULLABLE | Which plan (if plan-level) |
| `commit_id` | UUID | FK(weekly_commit.id), NULLABLE | Which commit (if commit-level) |
| `suggestion_type` | TEXT | NOT NULL | E.g., 'COMMIT_TITLE', 'ESTIMATE_ASSIST', 'TEAM_INSIGHT', 'PERSONAL_INSIGHT' |
| `prompt` | TEXT | NOT NULL | The prompt used |
| `rationale` | TEXT | NOT NULL | Why this suggestion |
| `suggestion_payload` | JSONB | NOT NULL | The suggestion details (JSON) |
| `model_version` | TEXT | NOT NULL | Which model generated this (e.g., 'claude-sonnet-4') |
| `accepted` | BOOLEAN | NULLABLE | User accepted suggestion? |
| `dismissed` | BOOLEAN | NULLABLE | User dismissed it? |
| `user_id` | UUID | FK(user_account.id), NULLABLE | Added V5: user context |
| `context_hash` | TEXT | NULLABLE | Added V5: hash of context for dedup |
| `team_id` | UUID | FK(team.id), NULLABLE | Added V10: for team-level insights |
| `week_start_date` | DATE | NULLABLE | Added V10: for team-week queries |
| `prompt_version` | TEXT | NULLABLE | Added V12: prompt template version |
| `eval_faithfulness_score` | REAL | NULLABLE | Added V12: LLM-as-judge faithfulness [0.0, 1.0] |
| `eval_relevancy_score` | REAL | NULLABLE | Added V12: LLM-as-judge relevancy [0.0, 1.0] |
| `eval_scored_at` | TIMESTAMPTZ | NULLABLE | Added V12: when eval scores were computed |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_ai_suggestion_plan` ON `(plan_id)`
- `idx_ai_suggestion_commit` ON `(commit_id)`
- `idx_ai_suggestion_user` ON `(user_id)` (added V5)
- `idx_ai_suggestion_type_plan` ON `(suggestion_type, plan_id)` (added V5)
- `idx_ai_suggestion_team_week` ON `(team_id, week_start_date, suggestion_type)` (added V10)
- `idx_ai_suggestion_prompt_version` ON `(prompt_version, suggestion_type)` (added V12)
- `idx_ai_suggestion_ab_analysis` ON `(suggestion_type, prompt_version, created_at)` (added V12)

**Seed Data:**
- Empty (no suggestions in seed)

---

### `ai_feedback`
User feedback on AI suggestions.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `suggestion_id` | UUID | NOT NULL, FK(ai_suggestion.id) | Parent suggestion |
| `user_id` | UUID | NOT NULL, FK(user_account.id) | User giving feedback |
| `accepted` | BOOLEAN | NOT NULL | Did user find it helpful? |
| `feedback_notes` | TEXT | NULLABLE | Optional feedback |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_ai_feedback_sugg` ON `(suggestion_id)`

**Seed Data:**
- Empty (no feedback in seed)

---

## Audit Log

### `audit_log`
Comprehensive audit trail of all entity changes.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `actor_user_id` | UUID | FK(user_account.id), NULLABLE | User who made the change (null if system) |
| `action` | TEXT | NOT NULL | E.g., 'CREATE', 'UPDATE', 'DELETE', 'LOCK', 'RECONCILE' |
| `entity_type` | TEXT | NOT NULL | E.g., 'weekly_plan', 'weekly_commit', 'work_item' |
| `entity_id` | UUID | NULLABLE | Which entity was affected |
| `old_value` | JSONB | NULLABLE | Previous state |
| `new_value` | JSONB | NULLABLE | New state |
| `ip_address` | TEXT | NULLABLE | Source IP |
| `user_agent` | TEXT | NULLABLE | Browser/client info |
| `actor_role` | TEXT | NULLABLE | Added V8: role of the actor at time of action |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Indexes:**
- `idx_audit_entity` ON `(entity_type, entity_id)`
- `idx_audit_actor` ON `(actor_user_id, created_at)`
- `idx_audit_action` ON `(action)` (added V8)
- `idx_audit_created` ON `(created_at)` (added V8)

**Seed Data:**
- Empty (no audit records in seed)

---

## Configuration

### `org_config`
Organization-level configuration (added V7). Cadence settings, timezones, default budgets.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `org_id` | UUID | NOT NULL, FK(organization.id) | Organization |
| `week_start_day` | TEXT | NOT NULL | Default: 'MONDAY' |
| `draft_open_offset_hours` | INT | NOT NULL | Default: -60 (Friday 12:00 before week start) |
| `lock_due_offset_hours` | INT | NOT NULL | Default: 12 (Monday 12:00) |
| `reconcile_open_offset_hours` | INT | NOT NULL | Default: 113 (Friday 17:00) |
| `reconcile_due_offset_hours` | INT | NOT NULL | Default: 178 (Monday 10:00 next week) |
| `default_weekly_budget` | SMALLINT | NOT NULL | Default: 10 |
| `timezone` | TEXT | NOT NULL | Default: 'UTC' |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Constraints:**
- `uq_org_config` UNIQUE `(org_id)` — one config per org

**Indexes:**
- `idx_org_config_org_id` ON `(org_id)`

**Seed Data (V9 + V11):**
- 1 config for Acme Corp
- Lock deadline extended to 120 hours (Sat noon) for dev to allow DRAFT all week
- Reconcile deadline extended to 240 hours

---

### `team_config_override`
Team-level config overrides (added V7). Nullable fields fall back to org_config.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `team_id` | UUID | NOT NULL, FK(team.id) | Team |
| `week_start_day` | TEXT | NULLABLE | Override (or null = use org) |
| `draft_open_offset_hours` | INT | NULLABLE | Override |
| `lock_due_offset_hours` | INT | NULLABLE | Override |
| `reconcile_open_offset_hours` | INT | NULLABLE | Override |
| `reconcile_due_offset_hours` | INT | NULLABLE | Override |
| `default_weekly_budget` | SMALLINT | NULLABLE | Override |
| `timezone` | TEXT | NULLABLE | Override |
| `created_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Default: `now()` |

**Constraints:**
- `uq_team_config_override` UNIQUE `(team_id)` — one override per team

**Indexes:**
- `idx_team_config_override_team_id` ON `(team_id)`

**Seed Data:**
- Empty (no team overrides in seed)

---

## Derived Read Models (for Reporting)

These tables are populated by the ReadModelRefreshService (event-driven) or by V13 backfill for seed data. They denormalize facts for fast reporting queries.

### `user_week_fact`
Per-user per-week aggregates (added V6). One row = metrics for one user in one week.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `user_id` | UUID | NOT NULL, FK(user_account.id) | User |
| `week_start` | DATE | NOT NULL | Week start date |
| `plan_state` | TEXT | NULLABLE | Final state ('DRAFT', 'LOCKED', 'RECONCILED') |
| `lock_compliance` | BOOLEAN | NOT NULL | Locked on time and compliant?; default FALSE |
| `reconcile_compliance` | BOOLEAN | NOT NULL | Reconciled on time?; default FALSE |
| `total_planned_points` | INT | NOT NULL | Sum of estimates; default 0 |
| `total_achieved_points` | INT | NOT NULL | Sum of ACHIEVED outcome points; default 0 |
| `commit_count` | INT | NOT NULL | Number of commits; default 0 |
| `carry_forward_count` | INT | NOT NULL | Commits with streak > 0; default 0 |
| `scope_change_count` | INT | NOT NULL | Post-lock changes; default 0 |
| `king_count` | INT | NOT NULL | # of KING pieces; default 0 |
| `queen_count` | INT | NOT NULL | # of QUEEN pieces; default 0 |
| `refreshed_at` | TIMESTAMPTZ | NOT NULL | Last update; default `now()` |

**Constraints:**
- `uq_user_week_fact` UNIQUE `(user_id, week_start)` — one row per user per week

**Indexes:**
- `idx_user_week_fact_user_week` ON `(user_id, week_start)`

**Seed Data (V13):**
- **60 rows** (12 weeks × 5 users)
- Calculated from transactional data

---

### `team_week_rollup`
Per-team per-week aggregates (added V6). Team-level rollup of user metrics.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `team_id` | UUID | NOT NULL, FK(team.id) | Team |
| `week_start` | DATE | NOT NULL | Week start date |
| `member_count` | INT | NOT NULL | # team members; default 0 |
| `locked_count` | INT | NOT NULL | # members who locked; default 0 |
| `reconciled_count` | INT | NOT NULL | # members who reconciled; default 0 |
| `total_planned_points` | INT | NOT NULL | Team total; default 0 |
| `total_achieved_points` | INT | NOT NULL | Team achieved; default 0 |
| `exception_count` | INT | NOT NULL | # exceptions; default 0 |
| `avg_carry_forward_rate` | DOUBLE PRECISION | NOT NULL | % of commits carried forward; default 0.0 |
| `chess_distribution` | JSONB | NULLABLE | `{"KING": N, "QUEEN": N, ...}` |
| `refreshed_at` | TIMESTAMPTZ | NOT NULL | Last update; default `now()` |

**Constraints:**
- `uq_team_week_rollup` UNIQUE `(team_id, week_start)` — one row per team per week

**Indexes:**
- `idx_team_week_rollup_team_week` ON `(team_id, week_start)`

**Seed Data (V13):**
- **12 rows** (12 weeks)
- Aggregates across all 5 users in Engineering team

**Example (Week 1 2026-01-05):**
```
member_count: 5
locked_count: 5
reconciled_count: 5
total_planned_points: 48
total_achieved_points: 38
exception_count: 1 (Carol auto-locked)
avg_carry_forward_rate: 0.0
chess_distribution: {"KING": 4, "QUEEN": 3, "ROOK": 5, "BISHOP": 1, "KNIGHT": 2, "PAWN": 3}
```

---

### `rcdo_week_rollup`
Per-RCDO-node per-week aggregates (added V6). Tracks progress toward strategic objectives.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `rcdo_node_id` | UUID | NOT NULL, FK(rcdo_node.id) | RCDO node (Outcome, etc.) |
| `week_start` | DATE | NOT NULL | Week start date |
| `planned_points` | INT | NOT NULL | Sum of estimates linked to this RCDO; default 0 |
| `achieved_points` | INT | NOT NULL | Sum of ACHIEVED linked commits; default 0 |
| `commit_count` | INT | NOT NULL | # commits linked; default 0 |
| `team_contribution_breakdown` | JSONB | NULLABLE | `{team_id: points, ...}` breakdown |
| `refreshed_at` | TIMESTAMPTZ | NOT NULL | Last update; default `now()` |

**Constraints:**
- `uq_rcdo_week_rollup` UNIQUE `(rcdo_node_id, week_start)` — one per RCDO per week

**Indexes:**
- `idx_rcdo_week_rollup_node_week` ON `(rcdo_node_id, week_start)`

**Seed Data (V13):**
- **~48 rows** (12 weeks × 4 outcome nodes)
- Tracks progress on each strategic outcome

---

### `compliance_fact`
Per-user per-week lock/reconcile compliance tracking (added V6). Supports compliance dashboards.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `user_id` | UUID | NOT NULL, FK(user_account.id) | User |
| `week_start` | DATE | NOT NULL | Week start date |
| `lock_on_time` | BOOLEAN | NOT NULL | Locked and compliant, not auto-locked?; default FALSE |
| `lock_late` | BOOLEAN | NOT NULL | Locked but not compliant?; default FALSE |
| `auto_locked` | BOOLEAN | NOT NULL | Was auto-locked?; default FALSE |
| `reconcile_on_time` | BOOLEAN | NOT NULL | Reconciled on time?; default FALSE |
| `reconcile_late` | BOOLEAN | NOT NULL | Reconciled past deadline?; default FALSE |
| `reconcile_missed` | BOOLEAN | NOT NULL | Didn't reconcile?; default FALSE |
| `refreshed_at` | TIMESTAMPTZ | NOT NULL | Last update; default `now()` |

**Constraints:**
- `uq_compliance_fact` UNIQUE `(user_id, week_start)` — one per user per week

**Indexes:**
- `idx_compliance_fact_user_week` ON `(user_id, week_start)`

**Seed Data (V13):**
- **60 rows** (12 weeks × 5 users)
- Week 1 & 3: Carol has `auto_locked = true`
- Weeks 1-10: reconcile_on_time = true
- Weeks 11-12: reconcile_on_time = false (not reconciled)

---

### `carry_forward_fact`
One row per active carry-forward commit (added V6). Tracks carry-forward streaks and progress.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY | Default: `gen_random_uuid()` |
| `commit_id` | UUID | NOT NULL, FK(weekly_commit.id) | The carry-forward commit |
| `source_week` | DATE | NOT NULL | When the commit originated |
| `current_week` | DATE | NOT NULL | Current week |
| `streak_length` | INT | NOT NULL | How many weeks carried forward; default 0 |
| `rcdo_node_id` | UUID | FK(rcdo_node.id), NULLABLE | Strategic objective (if linked) |
| `chess_piece` | TEXT | NULLABLE | KING, QUEEN, ROOK, etc. |
| `refreshed_at` | TIMESTAMPTZ | NOT NULL | Last update; default `now()` |

**Constraints:**
- `uq_carry_forward_fact` UNIQUE `(commit_id)` — one per commit (tracks current streak)

**Indexes:**
- `idx_carry_forward_fact_commit` ON `(commit_id)`
- `idx_carry_forward_fact_weeks` ON `(current_week, source_week)`

**Seed Data (V13):**
- **~13 rows** (one per carry-forward commit)
- Max streak in seed: 1 week

---

## Indexes

### Summary of All Indexes

| Index Name | Table | Columns | Purpose |
|------------|-------|---------|---------|
| `idx_team_org` | team | (organization_id) | Find teams by org |
| `idx_user_org` | user_account | (organization_id) | Find users by org |
| `idx_user_home_team` | user_account | (home_team_id) | Find users' home team |
| `idx_team_membership` | team_membership | (user_id) | Find user's teams |
| `idx_rcdo_parent` | rcdo_node | (parent_id) | RCDO hierarchy traversal |
| `idx_rcdo_status` | rcdo_node | (status) | Filter by RCDO status |
| `idx_rcdo_change_node` | rcdo_change_log | (rcdo_node_id) | Changes to an RCDO |
| `idx_plan_owner_week` | weekly_plan | (owner_user_id, week_start_date) | Find user's plan for week |
| `idx_plan_team_week` | weekly_plan | (team_id, week_start_date) | Find team's plans for week |
| `idx_plan_state` | weekly_plan | (state) | Filter by plan state |
| `idx_commit_plan` | weekly_commit | (plan_id) | Find commits in plan |
| `idx_commit_owner` | weekly_commit | (owner_user_id) | Find user's commits |
| `idx_commit_rcdo` | weekly_commit | (rcdo_node_id) | Find commits by RCDO |
| `idx_commit_work_item` | weekly_commit | (work_item_id) | Find commits linked to ticket |
| `idx_work_item_team` | work_item | (team_id) | Find team's tickets |
| `idx_work_item_assignee` | work_item | (assignee_user_id) | Find user's assigned tickets |
| `idx_work_item_week` | work_item | (target_week_start_date) | Find tickets for week |
| `idx_work_item_status` | work_item | (status) | Filter by ticket status |
| `idx_work_item_priority` | work_item | (priority) | Filter by priority (added V3) |
| `idx_scope_change_plan` | scope_change_event | (plan_id) | Find changes to plan |
| `idx_scope_change_commit` | scope_change_event | (commit_id) | Changes to a commit |
| `idx_lock_snap_commit` | lock_snapshot_commit | (snapshot_id) | Find commits in lock snapshot |
| `idx_recon_snap_commit` | reconcile_snapshot_commit | (snapshot_id) | Find commits in recon snapshot |
| `idx_carry_forward_src` | carry_forward_link | (source_commit_id) | Find what carried forward from a commit |
| `idx_carry_forward_tgt` | carry_forward_link | (target_commit_id) | Find source of a carry-forward |
| `idx_mgr_comment_plan` | manager_comment | (plan_id) | Find comments on plan |
| `idx_mgr_comment_commit` | manager_comment | (commit_id) | Find comments on commit |
| `idx_mgr_exc_team_week` | manager_review_exception | (team_id, week_start_date) | Find exceptions for team-week |
| `idx_mgr_exc_plan_type` | manager_review_exception | (plan_id, exception_type) | Find exceptions by type |
| `idx_mgr_exc_user` | manager_review_exception | (user_id) | Find user's exceptions |
| `idx_mgr_exc_resolved` | manager_review_exception | (resolved) | Filter by resolution status |
| `idx_notif_recipient` | notification | (recipient_user_id, read) | Find user's unread notifications |
| `idx_notif_priority` | notification | (recipient_user_id, priority, read) | Find high-priority notifications (added V4) |
| `idx_notif_delivery` | notification_delivery | (notification_id) | Find delivery records |
| `idx_ai_suggestion_plan` | ai_suggestion | (plan_id) | Find suggestions for plan |
| `idx_ai_suggestion_commit` | ai_suggestion | (commit_id) | Find suggestions for commit |
| `idx_ai_suggestion_user` | ai_suggestion | (user_id) | Find suggestions for user (added V5) |
| `idx_ai_suggestion_type_plan` | ai_suggestion | (suggestion_type, plan_id) | Find suggestion type for plan (added V5) |
| `idx_ai_suggestion_team_week` | ai_suggestion | (team_id, week_start_date, suggestion_type) | Team-week insights (added V10) |
| `idx_ai_suggestion_prompt_version` | ai_suggestion | (prompt_version, suggestion_type) | A/B testing (added V12) |
| `idx_ai_suggestion_ab_analysis` | ai_suggestion | (suggestion_type, prompt_version, created_at) | A/B analysis (added V12) |
| `idx_ai_feedback_sugg` | ai_feedback | (suggestion_id) | Find feedback on suggestion |
| `idx_audit_entity` | audit_log | (entity_type, entity_id) | Find audit trail for entity |
| `idx_audit_actor` | audit_log | (actor_user_id, created_at) | Find user's audit trail |
| `idx_audit_action` | audit_log | (action) | Filter by action type (added V8) |
| `idx_audit_created` | audit_log | (created_at) | Timeline queries (added V8) |
| `idx_org_config_org_id` | org_config | (org_id) | Find org's config (added V7) |
| `idx_team_config_override_team_id` | team_config_override | (team_id) | Find team's config (added V7) |
| `idx_user_week_fact_user_week` | user_week_fact | (user_id, week_start) | User week metrics (added V6) |
| `idx_team_week_rollup_team_week` | team_week_rollup | (team_id, week_start) | Team week metrics (added V6) |
| `idx_rcdo_week_rollup_node_week` | rcdo_week_rollup | (rcdo_node_id, week_start) | RCDO week metrics (added V6) |
| `idx_compliance_fact_user_week` | compliance_fact | (user_id, week_start) | User compliance metrics (added V6) |
| `idx_carry_forward_fact_commit` | carry_forward_fact | (commit_id) | Carry-forward streak (added V6) |
| `idx_carry_forward_fact_weeks` | carry_forward_fact | (current_week, source_week) | Carry-forward timeline (added V6) |

---

## Seed Data Overview

### Structure

The seed data is built in stages:

**V9 (dev_seed.sql):**
- 1 organization (Acme Corp)
- 1 team (Engineering)
- 2 users (Dev User, Manager One)
- RCDO hierarchy (2 Rally Cries, 3 Defining Objectives, 4 Outcomes)
- 1 org_config (extended lock deadline for dev)

**V11 (rich_demo_seed.sql):**
- 4 additional users (Alice, Bob, Carol, Dan)
- 12 weeks of data (2026-01-05 → 2026-03-23)
  - **60 weekly plans** (5 users × 12 weeks)
  - **250+ weekly commits** (varied chess pieces, outcomes)
  - **12 work items** (ENG-101 to ENG-112, enterprise features)
- **55 lock snapshots** (weeks 1-11)
- **50 reconcile snapshots** (weeks 1-10)
- **~13 carry-forward links** (with reasons)
- **6 scope change events** (post-lock modifications)
- **9 manager comments** from Manager One
- **4 manager review exceptions** (2 resolved, 2 unresolved)
- **13 notifications** (planning reminders, exceptions)

**V13 (backfill_read_models.sql):**
- Backfills all derived tables from transactional seed data
- **60 user_week_fact rows**
- **12 team_week_rollup rows**
- **~48 rcdo_week_rollup rows**
- **60 compliance_fact rows**
- **~13 carry_forward_fact rows**

### Realistic Patterns in Seed Data

**Carry-Forward Streaks:**
- Dan's "Personalized Onboarding" (wk10 → wk11): 1 week, unresolved exception
- Alice's Azure AD SSO (wk3 → wk4): Underestimated complexity
- Carol's EU region work (wk3 → wk4 → wk7): External procurement delays
- Bob's billing work (multiple): Complex edge cases

**Compliance Issues:**
- Carol auto-locked weeks 1 & 3 (on PTO)
- Most other users on-time, compliant

**Achievement Patterns:**
- Most commits: ACHIEVED (~60-70%)
- Some: PARTIALLY_ACHIEVED (~15-20%)
- Few: NOT_ACHIEVED (~5-10%)
- Rare: CANCELED

**Manager Engagement:**
- 9 feedback comments documenting progress & concerns
- Immediate follow-up on blockers

### Configuration

**application.yml:**
- Flyway enabled (V1 through V13)
- Hibernate DDL auto: `none` (Flyway owns schema)
- PostgreSQL at `localhost:5432/weekly_commit`
- Swagger UI at `/swagger-ui.html`
- Actuator metrics exposed
- Feature flags: AI assistance, notifications enabled
- AI provider: OpenRouter (Claude Sonnet 4)
- Pinecone embeddings for vector search

**Reseed Script (scripts/reseed.sql):**
- Wipes all transactional data (plans, commits, snapshots, AI suggestions, exceptions)
- Preserves: org, team, users, RCDO hierarchy, configurations
- Restores minimal seed data for fresh start
- Chain: `reseed.sql` → `V11__rich_demo_seed.sql` → `V13__backfill_read_models.sql`

---

## Quick Reference: Key Relationships

```
Organization
├── Team (1..*)
│   ├── User (team membership)
│   ├── Weekly Plan (user owns, team context)
│   │   ├── Weekly Commit (5-7 per plan)
│   │   │   ├── Work Item (optional link)
│   │   │   ├── RCDO Node (optional link)
│   │   │   └── Carry-forward Link (source → target)
│   │   ├── Lock Snapshot Header
│   │   │   └── Lock Snapshot Commit (denormalized)
│   │   ├── Reconcile Snapshot Header
│   │   │   └── Reconcile Snapshot Commit (with outcome)
│   │   ├── Scope Change Event (post-lock changes)
│   │   ├── Manager Comment
│   │   └── Manager Review Exception (flags)
│   ├── Work Item (tickets)
│   │   ├── Work Item Status History
│   │   └── Work Item Comment
│   └── Team Config Override (optional)
├── RCDO Node hierarchy
│   ├── Rally Cry
│   ├── Defining Objective
│   ├── Outcome
│   └── RCDO Change Log
├── Org Config
├── User Account
│   ├── Notification (unread inbox)
│   │   └── Notification Delivery (channel tracking)
│   ├── AI Suggestion
│   │   └── AI Feedback
│   ├── Capacity Override (for specific weeks)
│   └── Audit Log
└── Derived Read Models (user_week_fact, team_week_rollup, compliance_fact, rcdo_week_rollup, carry_forward_fact)
```

---

## Constraints Summary

**Unique Constraints:**
- `user_account`: (org_id, email)
- `team_membership`: (team_id, user_id)
- `weekly_plan`: (owner_user_id, week_start_date)
- `work_item`: (team_id, key)
- `lock_snapshot_header`: (plan_id)
- `reconcile_snapshot_header`: (plan_id)
- `carry_forward_link`: (source_commit_id, target_commit_id)
- `org_config`: (org_id)
- `team_config_override`: (team_id)
- `user_week_fact`: (user_id, week_start)
- `team_week_rollup`: (team_id, week_start)
- `rcdo_week_rollup`: (rcdo_node_id, week_start)
- `compliance_fact`: (user_id, week_start)
- `carry_forward_fact`: (commit_id)

**Check Constraints:**
- `rcdo_node.node_type` IN ('RALLY_CRY', 'DEFINING_OBJECTIVE', 'OUTCOME')
- `rcdo_node.status` IN ('DRAFT', 'ACTIVE', 'ARCHIVED')
- `user_account.role` IN ('IC', 'MANAGER')
- `weekly_plan.state` IN ('DRAFT', 'LOCKED', 'RECONCILING', 'RECONCILED')
- `weekly_commit.chess_piece` IN ('KING','QUEEN','ROOK','BISHOP','KNIGHT','PAWN')
- `weekly_commit.outcome` IN ('ACHIEVED','PARTIALLY_ACHIEVED','NOT_ACHIEVED','CANCELED')
- `work_item.status` IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELED')
- `work_item.priority` IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')
- `notification.priority` IN ('HIGH', 'MEDIUM', 'LOW')
- `scope_change_event.category` IN ('COMMIT_ADDED','COMMIT_REMOVED','ESTIMATE_CHANGED','CHESS_PIECE_CHANGED','RCDO_CHANGED','PRIORITY_CHANGED')
- `carry_forward_link.reason` IN ('BLOCKED_BY_DEPENDENCY','SCOPE_EXPANDED','REPRIORITIZED','RESOURCE_UNAVAILABLE','TECHNICAL_OBSTACLE','EXTERNAL_DELAY','UNDERESTIMATED','STILL_IN_PROGRESS')

---

**Generated:** 2026-03-29  
**Versions:** V1 (init) through V13 (backfill read models)  
**Database:** PostgreSQL  
**ORM:** Spring Boot JPA (Hibernate) with Flyway migrations
