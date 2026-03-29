# Weekly Commit - Domain Model Reference

Comprehensive documentation of all backend domain entities, enums, repositories, and their relationships.

---

## Table of Contents

1. [Core Organization Entities](#core-organization-entities)
2. [Weekly Planning Entities](#weekly-planning-entities)
3. [Work Item Management](#work-item-management)
4. [RCDO (Rally Cry / Defining Objective / Outcome)](#rcdo-rally-cry--defining-objective--outcome)
5. [Snapshot Entities](#snapshot-entities)
6. [Derived Read-Model Entities](#derived-read-model-entities)
7. [Compliance & Exception Entities](#compliance--exception-entities)
8. [AI Feedback Entities](#ai-feedback-entities)
9. [Notification Entities](#notification-entities)
10. [Configuration & Audit Entities](#configuration--audit-entities)
11. [Enums](#enums)
12. [Repository Methods](#repository-methods)

---

## Core Organization Entities

### Organization
**Table:** `organization`

**Purpose:** Top-level organizational container for all teams and users.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `name` | String | Not blank, Not null | `@Column` | Organization name |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `1:N` → Team (implicitly via Team.organizationId)
- `1:N` → UserAccount (implicitly via UserAccount.organizationId)
- `1:1` → OrgConfig (configuration)

---

### Team
**Table:** `team`

**Purpose:** Organizational unit within an organization; supports hierarchical team structures.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `organizationId` | UUID | Not null | `@Column` | FK to Organization |
| `name` | String | Not blank, Not null | `@Column` | Team name |
| `parentTeamId` | UUID | Nullable | `@Column` | FK to parent Team (self-referential) |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `N:1` → Organization
- `1:N` → TeamMembership (team members)
- `1:N` → WeeklyPlan (plans owned by team)
- `1:N` → WorkItem (tickets owned by team)
- `1:N` → RcdoNode (if ownerTeamId is set)
- `1:1` → TeamConfigOverride (optional configuration override)
- `1:N` → ManagerReviewException (team-level exceptions)

**Hierarchy:** Team can have `parentTeamId` pointing to another Team, allowing nested team structures.

---

### UserAccount
**Table:** `user_account`

**Purpose:** Individual user account with organization membership and role assignment.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `organizationId` | UUID | Not null | `@Column` | FK to Organization |
| `email` | String | Not blank, Not null, Email | `@Column` | Unique email address |
| `displayName` | String | Not blank, Not null | `@Column` | User display name |
| `homeTeamId` | UUID | Nullable | `@Column` | FK to primary Team |
| `role` | String | Not blank, Not null | `@Column` | Default: "IC" (maps to UserRole enum) |
| `weeklyCapacityPoints` | int | Positive, Not null | `@Column` | Default: 10 points per week |
| `active` | boolean | Not null | `@Column` | Default: true |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `N:1` → Organization
- `N:1` → Team (homeTeamId, optional)
- `1:N` → TeamMembership (team memberships)
- `1:N` → WeeklyPlan (plans owned)
- `1:N` → WeeklyCommit (commits owned)
- `1:N` → UserWeekFact (weekly aggregates)

**Notes:** `role` field should use UserRole enum values: IC, MANAGER, ADMIN

---

### TeamMembership
**Table:** `team_membership`

**Purpose:** Bridges users to teams; tracks membership role within each team.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `teamId` | UUID | Not null | `@Column` | FK to Team |
| `userId` | UUID | Not null | `@Column` | FK to UserAccount |
| `role` | String | Not null | `@Column` | Default: "MEMBER" |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → Team
- `N:1` → UserAccount

**Unique Constraint:** Implicitly (teamId, userId) should be unique.

---

## Weekly Planning Entities

### WeeklyPlan
**Table:** `weekly_plan`

**Purpose:** Owner-specific plan for a single week; contains weekly commits and tracks plan lifecycle.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `ownerUserId` | UUID | Not null | `@Column` | FK to UserAccount (plan owner) |
| `teamId` | UUID | Not null | `@Column` | FK to Team (team context) |
| `weekStartDate` | LocalDate | Not null | `@Column` | Monday of the planning week |
| `state` | PlanState | Not null | `@Enumerated(STRING)` | Default: DRAFT. Values: DRAFT, LOCKED, RECONCILING, RECONCILED |
| `lockDeadline` | Instant | Not null | `@Column` | Deadline to lock plan (prevent auto-lock) |
| `reconcileDeadline` | Instant | Not null | `@Column` | Deadline to reconcile (mark outcomes) |
| `capacityBudgetPoints` | int | Positive, Not null | `@Column` | Default: 10 points |
| `isCompliant` | boolean | Not null | `@Column` | Default: true. Tracks if all deadlines met |
| `systemLockedWithErrors` | boolean | Not null | `@Column` | Default: false. True if system auto-locked with validation errors |
| `lockSnapshotId` | UUID | Nullable | `@Column` | FK to LockSnapshotHeader |
| `reconcileSnapshotId` | UUID | Nullable | `@Column` | FK to ReconcileSnapshotHeader |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `N:1` → UserAccount (ownerUserId)
- `N:1` → Team (teamId)
- `1:N` → WeeklyCommit (commits in plan)
- `1:1` → LockSnapshotHeader (optional, state = LOCKED+)
- `1:1` → ReconcileSnapshotHeader (optional, state = RECONCILING+)
- `1:N` → ManagerReviewException (exceptions raised)
- `1:N` → ManagerComment (comments on plan)
- `1:N` → ScopeChangeEvent (scope changes after lock)

**State Lifecycle:**
- DRAFT → LOCKED (manual lock or auto-lock after deadline)
- LOCKED → RECONCILING (reconcile window opens after deadline)
- RECONCILING → RECONCILED (manual reconciliation or auto-reconcile)

**Unique Constraint:** (ownerUserId, weekStartDate)

---

### WeeklyCommit
**Table:** `weekly_commit`

**Purpose:** A single commitment (work item) for a plan owner in a specific week; tracks outcome and carry-forward status.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `planId` | UUID | Not null | `@Column` | FK to WeeklyPlan |
| `ownerUserId` | UUID | Not null | `@Column` | FK to UserAccount (commit owner) |
| `title` | String | Not blank, Not null | `@Column` | Commit title/description |
| `description` | String | Nullable | `@Column` | Detailed description |
| `chessPiece` | ChessPiece | Not null | `@Enumerated(STRING)` | Importance level: KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN |
| `priorityOrder` | int | Min(1), Not null | `@Column` | Priority within the plan (1 = highest) |
| `rcdoNodeId` | UUID | Nullable | `@Column` | FK to RcdoNode (if linked to RCDO) |
| `workItemId` | UUID | Nullable | `@Column` | FK to WorkItem (if linked to ticket) |
| `estimatePoints` | Integer | Nullable | `@Column` | Story points or effort estimate |
| `successCriteria` | String | Nullable | `@Column` | Definition of done/success criteria |
| `outcome` | CommitOutcome | Nullable | `@Enumerated(STRING)` | Reconciliation outcome: ACHIEVED, PARTIALLY_ACHIEVED, NOT_ACHIEVED, CANCELED |
| `outcomeNotes` | String | Nullable | `@Column` | Notes explaining the outcome |
| `carryForwardSourceId` | UUID | Nullable | `@Column` | FK to source commit if carried forward from previous week |
| `carryForwardStreak` | int | Min(0), Not null | `@Column` | Default: 0. Number of consecutive weeks carried forward |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `N:1` → WeeklyPlan (planId)
- `N:1` → UserAccount (ownerUserId)
- `N:1` → RcdoNode (optional, rcdoNodeId)
- `N:1` → WorkItem (optional, workItemId)
- `1:1` → CarryForwardFact (optional, if carried forward)
- `1:N` → CarryForwardLink (outbound carry-forward links)
- `1:N` → ManagerComment (comments on commit)

**Enums Used:**
- `ChessPiece`: KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN
- `CommitOutcome`: ACHIEVED, PARTIALLY_ACHIEVED, NOT_ACHIEVED, CANCELED

---

## Work Item Management

### WorkItem
**Table:** `work_item`

**Purpose:** Ticket or task that can be linked to commits and tracked across weeks.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `teamId` | UUID | Not null | `@Column` | FK to Team |
| `key` | String | Not blank, Not null | `@Column` | Unique ticket key (e.g., "PROJ-123") |
| `title` | String | Not blank, Not null | `@Column` | Ticket title |
| `description` | String | Nullable | `@Column` | Detailed description |
| `status` | TicketStatus | Not null | `@Enumerated(STRING)` | Default: TODO. Values: TODO, IN_PROGRESS, BLOCKED, DONE, CANCELED |
| `priority` | TicketPriority | Not null | `@Enumerated(STRING)` | Default: MEDIUM. Values: CRITICAL, HIGH, MEDIUM, LOW |
| `assigneeUserId` | UUID | Nullable | `@Column` | FK to UserAccount (assignee) |
| `reporterUserId` | UUID | Not null | `@Column` | FK to UserAccount (reporter) |
| `estimatePoints` | Integer | Nullable | `@Column` | Estimate in story points |
| `rcdoNodeId` | UUID | Nullable | `@Column` | FK to RcdoNode (if linked to RCDO) |
| `targetWeekStartDate` | LocalDate | Nullable | `@Column` | Target week for completion |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `N:1` → Team (teamId)
- `N:1` → UserAccount (assigneeUserId, optional)
- `N:1` → UserAccount (reporterUserId)
- `N:1` → RcdoNode (optional, rcdoNodeId)
- `1:N` → WeeklyCommit (commits linking this ticket)
- `1:N` → WorkItemComment (comments on ticket)
- `1:N` → WorkItemStatusHistory (status change history)

**JPA Features:**
- `JpaSpecificationExecutor` for dynamic filtering

**Enums Used:**
- `TicketStatus`: TODO, IN_PROGRESS, BLOCKED, DONE, CANCELED
- `TicketPriority`: CRITICAL, HIGH, MEDIUM, LOW

---

### WorkItemComment
**Table:** `work_item_comment`

**Purpose:** Comments on a work item.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `workItemId` | UUID | Not null | `@Column` | FK to WorkItem |
| `authorUserId` | UUID | Not null | `@Column` | FK to UserAccount (comment author) |
| `content` | String | Not blank, Not null | `@Column` | Comment text |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `N:1` → WorkItem
- `N:1` → UserAccount (authorUserId)

---

### WorkItemStatusHistory
**Table:** `work_item_status_history`

**Purpose:** Audit trail of status changes for a work item.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `workItemId` | UUID | Not null | `@Column` | FK to WorkItem |
| `fromStatus` | String | Nullable | `@Column` | Previous status |
| `toStatus` | String | Not blank, Not null | `@Column` | New status |
| `changedByUserId` | UUID | Not null | `@Column` | FK to UserAccount (who made the change) |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → WorkItem
- `N:1` → UserAccount (changedByUserId)

---

## RCDO (Rally Cry / Defining Objective / Outcome)

### RcdoNode
**Table:** `rcdo_node`

**Purpose:** Hierarchical objective structure (Rally Cry → Defining Objective → Outcome); can be linked from commits and work items.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `nodeType` | RcdoNodeType | Not null | `@Enumerated(STRING)` | Type: RALLY_CRY, DEFINING_OBJECTIVE, OUTCOME |
| `status` | RcdoNodeStatus | Not null | `@Enumerated(STRING)` | Default: DRAFT. Values: DRAFT, ACTIVE, ARCHIVED |
| `parentId` | UUID | Nullable | `@Column` | FK to parent RcdoNode (self-referential hierarchy) |
| `title` | String | Not blank, Not null | `@Column` | Node title |
| `description` | String | Nullable | `@Column` | Detailed description |
| `ownerTeamId` | UUID | Nullable | `@Column` | FK to Team (owning team) |
| `ownerUserId` | UUID | Nullable | `@Column` | FK to UserAccount (owning user) |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `1:N` → RcdoNode (children via parentId)
- `N:1` → RcdoNode (parent via parentId)
- `N:1` → Team (ownerTeamId, optional)
- `N:1` → UserAccount (ownerUserId, optional)
- `1:N` → WeeklyCommit (commits linked to this node)
- `1:N` → WorkItem (tickets linked to this node)
- `1:N` → RcdoWeekRollup (weekly rollups)
- `1:N` → RcdoChangeLog (change history)

**Hierarchy:**
- RALLY_CRY (top level)
  - DEFINING_OBJECTIVE (mid level)
    - OUTCOME (leaf level)

**Enums Used:**
- `RcdoNodeType`: RALLY_CRY, DEFINING_OBJECTIVE, OUTCOME
- `RcdoNodeStatus`: DRAFT, ACTIVE, ARCHIVED

---

### RcdoChangeLog
**Table:** `rcdo_change_log`

**Purpose:** Audit trail of changes made to an RCDO node.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `rcdoNodeId` | UUID | Not null | `@Column` | FK to RcdoNode |
| `changedByUserId` | UUID | Not null | `@Column` | FK to UserAccount (who made the change) |
| `changeSummary` | String | Not blank, Not null | `@Column` | Summary of change |
| `previousValue` | String | Nullable | `@ColumnTransformer` | JSON JSONB snapshot of old state |
| `newValue` | String | Nullable | `@ColumnTransformer` | JSON JSONB snapshot of new state |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → RcdoNode
- `N:1` → UserAccount (changedByUserId)

---

## Snapshot Entities

### LockSnapshotHeader
**Table:** `lock_snapshot_header`

**Purpose:** Immutable snapshot of a plan at the moment of locking; prevents historical changes.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `planId` | UUID | Not null, Unique | `@Column` | FK to WeeklyPlan (one-to-one) |
| `lockedAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Timestamp when plan was locked |
| `lockedBySystem` | boolean | Not null | `@Column` | Default: false. True if auto-locked by system |
| `snapshotPayload` | String | Not blank, Not null | `@ColumnTransformer` | JSON JSONB snapshot of entire plan state |

**Relationships:**
- `1:1` → WeeklyPlan (planId, unique)
- `1:N` → LockSnapshotCommit (snapshot of each commit)

**Notes:** Immutable after creation; used as audit trail.

---

### LockSnapshotCommit
**Table:** `lock_snapshot_commit`

**Purpose:** Snapshot of a single commit within a lock snapshot.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `snapshotId` | UUID | Not null | `@Column` | FK to LockSnapshotHeader |
| `commitId` | UUID | Not null | `@Column` | FK to WeeklyCommit (the commit being snapshotted) |
| `snapshotData` | String | Not blank, Not null | `@ColumnTransformer` | JSON JSONB snapshot of commit state |

**Relationships:**
- `N:1` → LockSnapshotHeader (snapshotId)
- Points to WeeklyCommit (commitId, implicit FK)

---

### ReconcileSnapshotHeader
**Table:** `reconcile_snapshot_header`

**Purpose:** Immutable snapshot of a plan at reconciliation; captures outcome states.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `planId` | UUID | Not null, Unique | `@Column` | FK to WeeklyPlan (one-to-one) |
| `reconciledAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Timestamp when plan was reconciled |
| `snapshotPayload` | String | Not blank, Not null | `@ColumnTransformer` | JSON JSONB snapshot of entire plan state |

**Relationships:**
- `1:1` → WeeklyPlan (planId, unique)
- `1:N` → ReconcileSnapshotCommit (snapshot of each commit with outcome)

---

### ReconcileSnapshotCommit
**Table:** `reconcile_snapshot_commit`

**Purpose:** Snapshot of a commit with its final outcome during reconciliation.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `snapshotId` | UUID | Not null | `@Column` | FK to ReconcileSnapshotHeader |
| `commitId` | UUID | Not null | `@Column` | FK to WeeklyCommit |
| `outcome` | CommitOutcome | Nullable | `@Enumerated(STRING)` | Final outcome: ACHIEVED, PARTIALLY_ACHIEVED, NOT_ACHIEVED, CANCELED |
| `snapshotData` | String | Not blank, Not null | `@ColumnTransformer` | JSON JSONB snapshot of commit state |

**Relationships:**
- `N:1` → ReconcileSnapshotHeader (snapshotId)
- Points to WeeklyCommit (commitId, implicit FK)

**Enums Used:**
- `CommitOutcome`: ACHIEVED, PARTIALLY_ACHIEVED, NOT_ACHIEVED, CANCELED

---

## Derived Read-Model Entities

These are materialized views (fact tables) that aggregate data from transactional entities and are refreshed on lifecycle events and scheduled cadence (typically every 5 minutes).

### UserWeekFact
**Table:** `user_week_fact`

**Purpose:** Per-user per-week aggregate: compliance status, planned vs. achieved points, carry-forward counts, chess piece counts.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `userId` | UUID | Not null | `@Column` | FK to UserAccount |
| `weekStart` | LocalDate | Not null | `@Column` | Week start date |
| `planState` | String | Nullable | `@Column` | Current state of user's plan for the week |
| `lockCompliance` | boolean | Not null | `@Column` | True if plan locked before deadline |
| `reconcileCompliance` | boolean | Not null | `@Column` | True if plan reconciled before deadline |
| `totalPlannedPoints` | int | Not null | `@Column` | Sum of estimate points across commits |
| `totalAchievedPoints` | int | Not null | `@Column` | Sum of achieved points (based on outcome) |
| `commitCount` | int | Not null | `@Column` | Number of commits in plan |
| `carryForwardCount` | int | Not null | `@Column` | Count of carried-forward commits |
| `scopeChangeCount` | int | Not null | `@Column` | Count of scope change events |
| `kingCount` | int | Not null | `@Column` | Count of KING chess pieces |
| `queenCount` | int | Not null | `@Column` | Count of QUEEN chess pieces |
| `refreshedAt` | Instant | Not null | `@Column` | Default: now(). When was this fact last refreshed |

**Relationships:**
- `N:1` → UserAccount (userId)

**Composite Key:** (userId, weekStart) - typically unique

---

### TeamWeekRollup
**Table:** `team_week_rollup`

**Purpose:** Per-team per-week aggregate: member engagement, lock/reconcile status, points, exception count, chess distribution.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `teamId` | UUID | Not null | `@Column` | FK to Team |
| `weekStart` | LocalDate | Not null | `@Column` | Week start date |
| `memberCount` | int | Not null | `@Column` | Number of active team members |
| `lockedCount` | int | Not null | `@Column` | Number of members with locked plans |
| `reconciledCount` | int | Not null | `@Column` | Number of members with reconciled plans |
| `totalPlannedPoints` | int | Not null | `@Column` | Sum of all planned points across team members |
| `totalAchievedPoints` | int | Not null | `@Column` | Sum of achieved points |
| `exceptionCount` | int | Not null | `@Column` | Count of unresolved exceptions |
| `avgCarryForwardRate` | double | Not null | `@Column` | Average carry-forward percentage |
| `chessDistribution` | String | Nullable | `@ColumnTransformer` | JSON JSONB map of chess piece name → count |
| `refreshedAt` | Instant | Not null | `@Column` | Default: now(). When was this fact last refreshed |

**Relationships:**
- `N:1` → Team (teamId)

**JSON Structure (chessDistribution):**
```json
{
  "KING": 5,
  "QUEEN": 10,
  "ROOK": 8,
  "BISHOP": 3,
  "KNIGHT": 2,
  "PAWN": 12
}
```

**Composite Key:** (teamId, weekStart) - typically unique

---

### RcdoWeekRollup
**Table:** `rcdo_week_rollup`

**Purpose:** Per-RCDO-node per-week aggregate: planned vs. achieved points, commit count, team contribution breakdown.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `rcdoNodeId` | UUID | Not null | `@Column` | FK to RcdoNode |
| `weekStart` | LocalDate | Not null | `@Column` | Week start date |
| `plannedPoints` | int | Not null | `@Column` | Sum of planned points for commits linking this node |
| `achievedPoints` | int | Not null | `@Column` | Sum of achieved points |
| `commitCount` | int | Not null | `@Column` | Number of commits linking this node |
| `teamContributionBreakdown` | String | Nullable | `@ColumnTransformer` | JSON JSONB map of team ID → contribution points |
| `refreshedAt` | Instant | Not null | `@Column` | Default: now(). When was this fact last refreshed |

**Relationships:**
- `N:1` → RcdoNode (rcdoNodeId)

**JSON Structure (teamContributionBreakdown):**
```json
{
  "<team-id-uuid-1>": 5,
  "<team-id-uuid-2>": 3,
  "<team-id-uuid-3>": 2
}
```

**Composite Key:** (rcdoNodeId, weekStart) - typically unique

---

### CarryForwardFact
**Table:** `carry_forward_fact`

**Purpose:** Per-active carry-forward commit: tracks provenance, source week, current week, and streak length.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `commitId` | UUID | Not null | `@Column` | FK to WeeklyCommit (the current/target commit) |
| `sourceWeek` | LocalDate | Not null | `@Column` | Week when the original commit was created |
| `currentWeek` | LocalDate | Not null | `@Column` | Week in which this commit currently lives |
| `streakLength` | int | Not null | `@Column` | Number of consecutive weeks carried forward |
| `rcdoNodeId` | UUID | Nullable | `@Column` | If the commit is linked to an RCDO node |
| `chessPiece` | String | Nullable | `@Column` | Chess piece importance level |
| `refreshedAt` | Instant | Not null | `@Column` | Default: now(). When was this fact last refreshed |

**Relationships:**
- Points to WeeklyCommit (commitId, implicit FK)

**Notes:** One entry per active carry-forward commit. When a commit is NOT carried forward, the row is deleted or marked as inactive.

---

## Compliance & Exception Entities

### ComplianceFact
**Table:** `compliance_fact`

**Purpose:** Per-user per-week lock and reconciliation compliance detail.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `userId` | UUID | Not null | `@Column` | FK to UserAccount |
| `weekStart` | LocalDate | Not null | `@Column` | Week start date |
| `lockOnTime` | boolean | Not null | `@Column` | Plan manually locked before deadline |
| `lockLate` | boolean | Not null | `@Column` | Plan manually locked after deadline |
| `autoLocked` | boolean | Not null | `@Column` | Plan auto-locked by system (deadline passed) |
| `reconcileOnTime` | boolean | Not null | `@Column` | Plan reconciled before deadline |
| `reconcileLate` | boolean | Not null | `@Column` | Plan reconciled after deadline (reserved for future use) |
| `reconcileMissed` | boolean | Not null | `@Column` | Reconcile deadline passed; plan not yet RECONCILED |
| `refreshedAt` | Instant | Not null | `@Column` | Default: now(). When was this fact last refreshed |

**Relationships:**
- `N:1` → UserAccount (userId)

**Composite Key:** (userId, weekStart) - typically unique

---

### ManagerReviewException
**Table:** `manager_review_exception`

**Purpose:** Persisted exception record requiring manager review and resolution; raised by business rules.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `teamId` | UUID | Not null | `@Column` | FK to Team |
| `planId` | UUID | Nullable | `@Column` | FK to WeeklyPlan (if exception is plan-specific) |
| `userId` | UUID | Not null | `@Column` | FK to UserAccount (the affected user) |
| `exceptionType` | ExceptionType | Not null | `@Enumerated(STRING)` | Type of exception (see enum) |
| `severity` | ExceptionSeverity | Not null | `@Enumerated(STRING)` | Severity: HIGH, MEDIUM, LOW |
| `description` | String | Not blank, Not null | `@Column` | Human-readable description |
| `weekStartDate` | LocalDate | Not null | `@Column` | Week in which exception occurred |
| `resolved` | boolean | Not null | `@Column` | Default: false. Resolution status |
| `resolution` | String | Nullable | `@Column` | Notes on how exception was resolved |
| `resolvedAt` | Instant | Nullable | `@Column` | When exception was resolved |
| `resolvedById` | UUID | Nullable | `@Column` | FK to UserAccount (who resolved it) |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → Team (teamId)
- `N:1` → WeeklyPlan (optional, planId)
- `N:1` → UserAccount (userId - affected user)
- Points to UserAccount (resolvedById - resolver, implicit FK)

**Enums Used:**
- `ExceptionType`: MISSED_LOCK, AUTO_LOCKED, MISSED_RECONCILE, OVER_BUDGET, REPEATED_CARRY_FORWARD, POST_LOCK_SCOPE_INCREASE, KING_CHANGED_POST_LOCK, HIGH_SCOPE_VOLATILITY
- `ExceptionSeverity`: HIGH, MEDIUM, LOW

---

## AI Feedback Entities

### AiSuggestion
**Table:** `ai_suggestion`

**Purpose:** AI-generated suggestion for a user/plan/commit context; tracks acceptance and evaluation scores.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `userId` | UUID | Nullable | `@Column` | FK to UserAccount (context user) |
| `planId` | UUID | Nullable | `@Column` | FK to WeeklyPlan (context plan) |
| `commitId` | UUID | Nullable | `@Column` | FK to WeeklyCommit (context commit) |
| `teamId` | UUID | Nullable | `@Column` | FK to Team (context team) |
| `weekStartDate` | LocalDate | Nullable | `@Column` | Context week |
| `contextHash` | String | Nullable | `@Column` | Hash of input context for dedup |
| `suggestionType` | String | Not blank, Not null | `@Column` | Type of suggestion (e.g., "ESTIMATE_ADJUSTMENT", "PRIORITY_REORDER") |
| `prompt` | String | Not blank, Not null | `@Column(columnDefinition="text")` | Full LLM prompt sent |
| `rationale` | String | Not blank, Not null | `@Column(columnDefinition="text")` | Explanation of why suggestion was made |
| `suggestionPayload` | String | Not blank, Not null | `@ColumnTransformer` | JSON JSONB structured suggestion data |
| `modelVersion` | String | Not blank, Not null | `@Column` | Which AI model generated this (e.g., "gpt-4", "claude-3") |
| `promptVersion` | String | Nullable | `@Column` | Version of prompt template used |
| `accepted` | Boolean | Nullable | `@Column` | Null = not yet reviewed; true/false = user feedback |
| `dismissed` | Boolean | Nullable | `@Column` | Null = not yet reviewed; true/false = user feedback |
| `evalFaithfulnessScore` | Float | Nullable | `@Column` | LLM evaluation: how faithful to context |
| `evalRelevancyScore` | Float | Nullable | `@Column` | LLM evaluation: how relevant to user's context |
| `evalScoredAt` | Instant | Nullable | `@Column` | When evaluation was performed |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- Points to UserAccount (userId, implicit FK)
- Points to WeeklyPlan (planId, implicit FK)
- Points to WeeklyCommit (commitId, implicit FK)
- Points to Team (teamId, implicit FK)
- `1:N` → AiFeedback (user feedback on this suggestion)

---

### AiFeedback
**Table:** `ai_feedback`

**Purpose:** User feedback on an AI suggestion (acceptance/rejection and notes).

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `suggestionId` | UUID | Not null | `@Column` | FK to AiSuggestion |
| `userId` | UUID | Not null | `@Column` | FK to UserAccount (who gave feedback) |
| `accepted` | boolean | Not null | `@Column` | User accepted (true) or rejected (false) suggestion |
| `feedbackNotes` | String | Nullable | `@Column` | Optional notes from user |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → AiSuggestion (suggestionId)
- `N:1` → UserAccount (userId)

---

## Notification Entities

### Notification
**Table:** `notification`

**Purpose:** In-app notification record; can be delivered via multiple channels.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `recipientUserId` | UUID | Not null | `@Column` | FK to UserAccount (notification recipient) |
| `notificationType` | String | Not blank, Not null | `@Column` | Type (maps to NotificationEvent enum) |
| `title` | String | Not blank, Not null | `@Column` | Notification title |
| `body` | String | Not blank, Not null | `@Column` | Notification body/message |
| `referenceId` | UUID | Nullable | `@Column` | FK to referenced entity (plan, commit, etc.) |
| `referenceType` | String | Nullable | `@Column` | Type of referenced entity |
| `read` | boolean | Not null | `@Column` | Default: false. User read status |
| `priority` | String | Not null | `@Column` | Default: "MEDIUM". Values: HIGH, MEDIUM, LOW |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → UserAccount (recipientUserId)
- `1:N` → NotificationDelivery (delivery attempts across channels)

---

### NotificationDelivery
**Table:** `notification_delivery`

**Purpose:** Tracks delivery status of a notification across a specific channel.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `notificationId` | UUID | Not null | `@Column` | FK to Notification |
| `channel` | String | Not blank, Not null | `@Column` | Channel name (e.g., "EMAIL", "PUSH", "IN_APP") |
| `status` | String | Not blank, Not null | `@Column` | Default: "PENDING". Values: PENDING, SENT, FAILED, BOUNCED |
| `sentAt` | Instant | Nullable | `@Column` | When message was sent to channel |
| `openedAt` | Instant | Nullable | `@Column` | When user opened/read (if trackable) |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → Notification (notificationId)

---

## Configuration & Audit Entities

### OrgConfig
**Table:** `org_config`

**Purpose:** Organization-level cadence configuration for planning windows, lock/reconcile deadlines, and default budget.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `orgId` | UUID | Not null | `@Column` | FK to Organization |
| `weekStartDay` | String | Not blank, Not null | `@Column` | Default: "MONDAY" |
| `draftOpenOffsetHours` | int | Not null | `@Column` | Default: -60 (Friday 12:00 for Monday start) |
| `lockDueOffsetHours` | int | Not null | `@Column` | Default: 12 (Monday 12:00) |
| `reconcileOpenOffsetHours` | int | Not null | `@Column` | Default: 113 (Friday 17:00) |
| `reconcileDueOffsetHours` | int | Not null | `@Column` | Default: 178 (next Monday 10:00) |
| `defaultWeeklyBudget` | int | Positive, Not null | `@Column` | Default: 10 points per week |
| `timezone` | String | Not blank, Not null | `@Column` | Default: "UTC" |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `1:1` → Organization (orgId, implicit)

**Notes:** One config row per organization. Nullable fields in TeamConfigOverride override these defaults at team level.

---

### TeamConfigOverride
**Table:** `team_config_override`

**Purpose:** Team-level cadence overrides; nullable fields fall back to OrgConfig.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `teamId` | UUID | Not null | `@Column` | FK to Team |
| `weekStartDay` | String | Nullable | `@Column` | If null, use OrgConfig.weekStartDay |
| `draftOpenOffsetHours` | Integer | Nullable | `@Column` | If null, use OrgConfig.draftOpenOffsetHours |
| `lockDueOffsetHours` | Integer | Nullable | `@Column` | If null, use OrgConfig.lockDueOffsetHours |
| `reconcileOpenOffsetHours` | Integer | Nullable | `@Column` | If null, use OrgConfig.reconcileOpenOffsetHours |
| `reconcileDueOffsetHours` | Integer | Nullable | `@Column` | If null, use OrgConfig.reconcileDueOffsetHours |
| `defaultWeeklyBudget` | Integer | Nullable | `@Column` | If null, use OrgConfig.defaultWeeklyBudget |
| `timezone` | String | Nullable | `@Column` | If null, use OrgConfig.timezone |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `1:1` → Team (teamId)

---

### CapacityOverride
**Table:** `capacity_override`

**Purpose:** Manager-set capacity override for a user in a specific week.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `userId` | UUID | Not null | `@Column` | FK to UserAccount |
| `weekStartDate` | LocalDate | Not null | `@Column` | Week affected by override |
| `budgetPoints` | int | Positive, Not null | `@Column` | Override capacity in points |
| `reason` | String | Nullable | `@Column` | Why override was set |
| `setByManagerId` | UUID | Not null | `@Column` | FK to UserAccount (manager who set it) |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → UserAccount (userId)
- Points to UserAccount (setByManagerId, implicit FK - the manager)

**Notes:** Overrides the default weeklyCapacityPoints for a specific week.

---

### ManagerComment
**Table:** `manager_comment`

**Purpose:** Comments from managers on plans or commits.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `planId` | UUID | Nullable | `@Column` | FK to WeeklyPlan (if commenting on plan) |
| `commitId` | UUID | Nullable | `@Column` | FK to WeeklyCommit (if commenting on commit) |
| `authorUserId` | UUID | Not null | `@Column` | FK to UserAccount (comment author) |
| `content` | String | Not blank, Not null | `@Column` | Comment text |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |
| `updatedAt` | Instant | Not null | `@UpdateTimestamp` | Auto-updated on each change |

**Relationships:**
- `N:1` → WeeklyPlan (optional, planId)
- `N:1` → WeeklyCommit (optional, commitId)
- `N:1` → UserAccount (authorUserId)

**Notes:** Either planId or commitId should be set, not both (or neither).

---

### ScopeChangeEvent
**Table:** `scope_change_event`

**Purpose:** Records significant changes to a plan after it was locked; used for exception detection.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `planId` | UUID | Not null | `@Column` | FK to WeeklyPlan |
| `commitId` | UUID | Nullable | `@Column` | FK to WeeklyCommit (if scope change is on a commit) |
| `category` | ScopeChangeCategory | Not null | `@Enumerated(STRING)` | Type of change (see enum) |
| `changedByUserId` | UUID | Not null | `@Column` | FK to UserAccount (who made the change) |
| `reason` | String | Not blank, Not null | `@Column` | Why the change was made |
| `previousValue` | String | Nullable | `@ColumnTransformer` | JSON JSONB snapshot of old value |
| `newValue` | String | Nullable | `@ColumnTransformer` | JSON JSONB snapshot of new value |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- `N:1` → WeeklyPlan (planId)
- Points to WeeklyCommit (commitId, optional, implicit FK)
- `N:1` → UserAccount (changedByUserId)

**Enums Used:**
- `ScopeChangeCategory`: COMMIT_ADDED, COMMIT_REMOVED, ESTIMATE_CHANGED, CHESS_PIECE_CHANGED, RCDO_CHANGED, PRIORITY_CHANGED

---

### AuditLog
**Table:** `audit_log`

**Purpose:** Append-only audit record of all entity actions (create, update, delete). Never updated or deleted after creation.

**Fields:**
| Field | Type | Constraints | JPA Annotation | Notes |
|-------|------|-----------|---|---|
| `id` | UUID | Not null, Immutable | `@GeneratedValue(UUID)` | Primary key |
| `actorUserId` | UUID | Nullable | `@Column` | FK to UserAccount (who performed action) |
| `actorRole` | String | Nullable | `@Column` | Role of actor at time of action (IC, MANAGER, ADMIN, SYSTEM) |
| `action` | String | Not blank, Not null | `@Column` | Action type (CREATE, UPDATE, DELETE, etc.) |
| `entityType` | String | Not blank, Not null | `@Column` | Type of entity affected |
| `entityId` | UUID | Nullable | `@Column` | ID of affected entity |
| `oldValue` | String | Nullable | `@ColumnTransformer` | JSON JSONB snapshot before change |
| `newValue` | String | Nullable | `@ColumnTransformer` | JSON JSONB snapshot after change |
| `ipAddress` | String | Nullable | `@Column` | IP address of request |
| `userAgent` | String | Nullable | `@Column` | User agent string |
| `createdAt` | Instant | Not null, Immutable | `@CreationTimestamp` | Auto-set on insert |

**Relationships:**
- Points to UserAccount (actorUserId, optional, implicit FK)

**Notes:** All columns except `id` are immutable after creation. Used for compliance and forensic analysis.

---

## Enums

### ChessPiece
**Purpose:** Importance/priority level of a commit, borrowing from chess piece values.

**Values:**
- `KING` — Highest priority; must achieve
- `QUEEN` — Very high priority; critical path
- `ROOK` — High priority; important
- `BISHOP` — Medium-high priority
- `KNIGHT` — Medium priority; flexible
- `PAWN` — Low priority; nice-to-have

---

### CommitOutcome
**Purpose:** Final outcome of a commit during reconciliation.

**Values:**
- `ACHIEVED` — Commit was fully completed
- `PARTIALLY_ACHIEVED` — Commit was partially completed
- `NOT_ACHIEVED` — Commit was not completed
- `CANCELED` — Commit was canceled

---

### PlanState
**Purpose:** Lifecycle state of a weekly plan.

**Values:**
- `DRAFT` — Plan is in draft/planning phase
- `LOCKED` — Plan has been locked; no edits allowed (except by admin)
- `RECONCILING` — Reconciliation window is open; outcomes can be recorded
- `RECONCILED` — Plan has been finalized with all outcomes recorded

---

### RcdoNodeType
**Purpose:** Type of node in the hierarchical RCDO structure.

**Values:**
- `RALLY_CRY` — Top-level strategic objective
- `DEFINING_OBJECTIVE` — Mid-level objective under a Rally Cry
- `OUTCOME` — Leaf-level outcome under a Defining Objective

---

### RcdoNodeStatus
**Purpose:** Lifecycle status of an RCDO node.

**Values:**
- `DRAFT` — Node is in draft state
- `ACTIVE` — Node is active and can be linked to
- `ARCHIVED` — Node is archived (read-only)

---

### TicketStatus
**Purpose:** Lifecycle status of a work item/ticket.

**Values:**
- `TODO` — Ticket is to-do (not started)
- `IN_PROGRESS` — Work is in progress
- `BLOCKED` — Work is blocked
- `DONE` — Ticket is complete
- `CANCELED` — Ticket was canceled

---

### TicketPriority
**Purpose:** Priority level of a work item.

**Values:**
- `CRITICAL` — Production impact; highest priority
- `HIGH` — Important; high priority
- `MEDIUM` — Normal priority (default)
- `LOW` — Nice-to-have; low priority

---

### ExceptionType
**Purpose:** Type of manager-review exception that can be raised.

**Values:**
- `MISSED_LOCK` — Plan remained in DRAFT past lock deadline
- `AUTO_LOCKED` — Plan was auto-locked by system
- `MISSED_RECONCILE` — Plan passed reconcile deadline without reaching RECONCILED
- `OVER_BUDGET` — Total estimate exceeds capacity budget
- `REPEATED_CARRY_FORWARD` — Commit has carry-forward streak ≥ 2 weeks
- `POST_LOCK_SCOPE_INCREASE` — Estimate increased >20% after lock
- `KING_CHANGED_POST_LOCK` — King-level commit changed after lock
- `HIGH_SCOPE_VOLATILITY` — More than 3 scope changes after lock

---

### ExceptionSeverity
**Purpose:** Urgency level of an exception.

**Values:**
- `HIGH` — Requires immediate manager attention
- `MEDIUM` — Should be reviewed before end of week
- `LOW` — Informational; worth monitoring but not urgent

---

### ScopeChangeCategory
**Purpose:** Type of scope change event recorded.

**Values:**
- `COMMIT_ADDED` — New commit was added to plan
- `COMMIT_REMOVED` — Commit was removed from plan
- `ESTIMATE_CHANGED` — Estimate points changed
- `CHESS_PIECE_CHANGED` — Chess piece importance level changed
- `RCDO_CHANGED` — RCDO link was added/removed/changed
- `PRIORITY_CHANGED` — Priority order changed

---

### CarryForwardReason
**Purpose:** Reason why a commit was carried forward to the next week.

**Values:**
- `BLOCKED_BY_DEPENDENCY` — Blocked on external dependency
- `SCOPE_EXPANDED` — Scope grew beyond initial estimate
- `REPRIORITIZED` — Work was reprioritized
- `RESOURCE_UNAVAILABLE` — Required resources were unavailable
- `TECHNICAL_OBSTACLE` — Hit unexpected technical issue
- `EXTERNAL_DELAY` — External partner delay
- `UNDERESTIMATED` — Original estimate was too low
- `STILL_IN_PROGRESS` — Work is still in progress

---

### NotificationEvent
**Purpose:** Type of notification event that can be sent.

**Values (with Priority):**
- `DRAFT_WINDOW_OPENED` — New draft planning window (LOW)
- `LOCK_DUE_REMINDER` — Lock deadline reminder (MEDIUM)
- `AUTO_LOCK_OCCURRED` — System auto-locked plan (HIGH)
- `RECONCILIATION_OPENED` — Reconcile window opened (MEDIUM)
- `RECONCILIATION_DUE_REMINDER` — Reconcile deadline reminder (MEDIUM)
- `REPEATED_CARRY_FORWARD_REMINDER` — Commit carried forward 2+ weeks (MEDIUM)
- `MANAGER_EXCEPTION_DIGEST` — Manager exception summary (HIGH)
- `UNASSIGNED_TICKET_CREATED` — Unassigned ticket created (MEDIUM)
- `CRITICAL_TICKET_BLOCKED` — King/Queen ticket blocked (HIGH)

---

### UserRole
**Purpose:** Organizational role of a user account.

**Values:**
- `IC` — Individual Contributor (can only access own plans/commits)
- `MANAGER` — Manager (full detail for direct reports, aggregates for indirect)
- `ADMIN` — Administrator (unrestricted access to all data)

---

## Repository Methods

### OrganizationRepository
**Extends:** `JpaRepository<Organization, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| (inherited CRUD) | - | findById, save, delete, etc. |

**Notes:** Minimal custom methods; mostly relies on Spring Data JPA defaults.

---

### TeamRepository
**Extends:** `JpaRepository<Team, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByOrganizationId(UUID)` | `List<Team>` | Find all teams in organization |
| `findByParentTeamId(UUID)` | `List<Team>` | Find child teams under parent |

---

### UserAccountRepository
**Extends:** `JpaRepository<UserAccount, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByOrganizationIdAndEmail(UUID, String)` | `Optional<UserAccount>` | Find user by org + email |
| `findByHomeTeamId(UUID)` | `List<UserAccount>` | Find users with home team |
| `findByOrganizationIdAndActive(UUID, boolean)` | `List<UserAccount>` | Find active users in org |

---

### TeamMembershipRepository
**Extends:** `JpaRepository<TeamMembership, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByTeamId(UUID)` | `List<TeamMembership>` | Find all members of team |
| `findByUserId(UUID)` | `List<TeamMembership>` | Find all teams user belongs to |
| `findByTeamIdAndUserId(UUID, UUID)` | `Optional<TeamMembership>` | Check if user is member of team |
| `findByUserIdAndRole(UUID, String)` | `List<TeamMembership>` | Find teams where user has role |

---

### WeeklyPlanRepository
**Extends:** `JpaRepository<WeeklyPlan, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByOwnerUserIdAndWeekStartDate(UUID, LocalDate)` | `Optional<WeeklyPlan>` | Find plan by user + week |
| `findByTeamIdAndWeekStartDate(UUID, LocalDate)` | `List<WeeklyPlan>` | Find plans for team + week |
| `findByTeamIdOrderByWeekStartDateDesc(UUID)` | `List<WeeklyPlan>` | Find recent plans for team |
| `findByOwnerUserIdOrderByWeekStartDateDesc(UUID)` | `List<WeeklyPlan>` | Find recent plans for user |
| `findByState(PlanState)` | `List<WeeklyPlan>` | Find plans in state |
| `findByStateAndLockDeadlineBefore(PlanState, Instant)` | `List<WeeklyPlan>` | Find expired DRAFT plans (auto-lock job) |
| `findByStateAndReconcileDeadlineBefore(PlanState, Instant)` | `List<WeeklyPlan>` | Find locked plans past reconcile-open |
| `findByStateAndLockDeadlineBetween(PlanState, Instant, Instant)` | `List<WeeklyPlan>` | Find plans with lock due soon (reminder job) |
| `findByStateAndReconcileDeadlineBetween(PlanState, Instant, Instant)` | `List<WeeklyPlan>` | Find plans with reconcile due soon |
| `existsByOwnerUserIdAndWeekStartDate(UUID, LocalDate)` | `boolean` | Check if plan exists |
| `findByWeekStartDate(LocalDate)` | `List<WeeklyPlan>` | Find all plans for week (read-model refresh) |
| `findUpdatedSince(@Param("since") Instant)` | `List<WeeklyPlan>` | Find recently updated plans (RAG sweep) |

---

### WeeklyCommitRepository
**Extends:** `JpaRepository<WeeklyCommit, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByPlanIdOrderByPriorityOrder(UUID)` | `List<WeeklyCommit>` | Find commits in plan, ordered by priority |
| `findByOwnerUserId(UUID)` | `List<WeeklyCommit>` | Find all commits for user |
| `findByRcdoNodeId(UUID)` | `List<WeeklyCommit>` | Find commits linked to RCDO node |
| `findByWorkItemId(UUID)` | `List<WeeklyCommit>` | Find commits linked to work item |
| `countByPlanId(UUID)` | `long` | Count commits in plan |
| `findActiveCommitsForTicketByOwnerExcluding(UUID, UUID, UUID)` | `List<WeeklyCommit>` | Find active (non-CANCELED) commits for ticket by owner, excluding specified commit (prevent duplicate link) |

---

### WorkItemRepository
**Extends:** `JpaRepository<WorkItem, UUID>, JpaSpecificationExecutor<WorkItem>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByTeamIdAndKey(UUID, String)` | `Optional<WorkItem>` | Find ticket by team + key |
| `findByAssigneeUserId(UUID)` | `List<WorkItem>` | Find tickets assigned to user |
| `findByTeamIdAndTargetWeekStartDate(UUID, LocalDate)` | `List<WorkItem>` | Find tickets targeted for week |
| `findByTeamIdAndStatus(UUID, TicketStatus)` | `List<WorkItem>` | Find tickets with status |
| `findByTeamId(UUID)` | `List<WorkItem>` | Find all tickets for team |
| `countByTeamId(UUID)` | `long` | Count team's tickets |

**JPA Features:**
- `JpaSpecificationExecutor` allows dynamic query building (Criteria API)

---

### WorkItemCommentRepository
**Extends:** `JpaRepository<WorkItemComment, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| (inherited CRUD) | - | findById, save, delete, etc. |

**Notes:** Minimal custom methods.

---

### WorkItemStatusHistoryRepository
**Extends:** `JpaRepository<WorkItemStatusHistory, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByWorkItemIdOrderByCreatedAtAsc(UUID)` | `List<WorkItemStatusHistory>` | Find status history for ticket (chronological) |

---

### RcdoNodeRepository
**Extends:** `JpaRepository<RcdoNode, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByStatus(RcdoNodeStatus)` | `List<RcdoNode>` | Find nodes in status |
| `findByParentId(UUID)` | `List<RcdoNode>` | Find child nodes |
| `findByParentIdAndStatus(UUID, RcdoNodeStatus)` | `List<RcdoNode>` | Find active child nodes |
| `findByNodeTypeAndStatus(RcdoNodeType, RcdoNodeStatus)` | `List<RcdoNode>` | Find nodes of type in status |
| `findByOwnerTeamId(UUID)` | `List<RcdoNode>` | Find nodes owned by team |

---

### RcdoChangeLogRepository
**Extends:** `JpaRepository<RcdoChangeLog, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByRcdoNodeIdOrderByCreatedAtAsc(UUID)` | `List<RcdoChangeLog>` | Find change history for node (chronological) |

---

### RcdoWeekRollupRepository
**Extends:** `JpaRepository<RcdoWeekRollup, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByRcdoNodeIdAndWeekStart(UUID, LocalDate)` | `Optional<RcdoWeekRollup>` | Find rollup for node + week |
| `findByRcdoNodeIdAndWeekStartBetween(UUID, LocalDate, LocalDate)` | `List<RcdoWeekRollup>` | Find rollups for node over date range |

---

### UserWeekFactRepository
**Extends:** `JpaRepository<UserWeekFact, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByUserIdAndWeekStart(UUID, LocalDate)` | `Optional<UserWeekFact>` | Find fact for user + week |
| `findByUserIdInAndWeekStartBetween(List<UUID>, LocalDate, LocalDate)` | `List<UserWeekFact>` | Find facts for multiple users over date range |
| `findByUserIdAndWeekStartBetween(UUID, LocalDate, LocalDate)` | `List<UserWeekFact>` | Find facts for user over date range |
| `findByUserIdIn(List<UUID>)` | `List<UserWeekFact>` | Find all facts for multiple users (current week) |

---

### TeamWeekRollupRepository
**Extends:** `JpaRepository<TeamWeekRollup, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByTeamIdAndWeekStart(UUID, LocalDate)` | `Optional<TeamWeekRollup>` | Find rollup for team + week |
| `findByTeamIdAndWeekStartBetween(UUID, LocalDate, LocalDate)` | `List<TeamWeekRollup>` | Find rollups for team over date range |

---

### CarryForwardFactRepository
**Extends:** `JpaRepository<CarryForwardFact, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByCommitId(UUID)` | `Optional<CarryForwardFact>` | Find carry-forward fact for commit |
| `findByCurrentWeekBetween(LocalDate, LocalDate)` | `List<CarryForwardFact>` | Find active carry-forwards for date range |

---

### CarryForwardLinkRepository
**Extends:** `JpaRepository<CarryForwardLink, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findBySourceCommitId(UUID)` | `List<CarryForwardLink>` | Find outbound carry-forward links from source |
| `findByTargetCommitId(UUID)` | `Optional<CarryForwardLink>` | Find inbound carry-forward link to target |

---

### ComplianceFactRepository
**Extends:** `JpaRepository<ComplianceFact, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByUserIdAndWeekStart(UUID, LocalDate)` | `Optional<ComplianceFact>` | Find compliance detail for user + week |
| `findByUserIdInAndWeekStartBetween(List<UUID>, LocalDate, LocalDate)` | `List<ComplianceFact>` | Find compliance facts for users over date range |

---

### ManagerReviewExceptionRepository
**Extends:** `JpaRepository<ManagerReviewException, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByTeamIdAndWeekStartDateAndResolved(UUID, LocalDate, boolean)` | `List<ManagerReviewException>` | Find unresolved exceptions for team + week |
| `findByTeamIdAndWeekStartDate(UUID, LocalDate)` | `List<ManagerReviewException>` | Find all exceptions for team + week |
| `countByTeamIdAndWeekStartDate(UUID, LocalDate)` | `long` | Count exceptions for team + week |
| `findByPlanIdAndExceptionTypeAndResolved(UUID, ExceptionType, boolean)` | `Optional<ManagerReviewException>` | Find existing unresolved exception (idempotent) |
| `findByUserIdAndWeekStartDateAndExceptionTypeAndResolved(UUID, LocalDate, ExceptionType, boolean)` | `Optional<ManagerReviewException>` | Find exception by user + week + type (for non-plan exceptions) |
| `findByTeamIdAndResolved(UUID, boolean)` | `List<ManagerReviewException>` | Find all unresolved exceptions for team |

---

### CapacityOverrideRepository
**Extends:** `JpaRepository<CapacityOverride, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByUserIdAndWeekStartDate(UUID, LocalDate)` | `Optional<CapacityOverride>` | Find capacity override for user + week |
| `findByUserId(UUID)` | `List<CapacityOverride>` | Find all overrides for user |

---

### OrgConfigRepository
**Extends:** `JpaRepository<OrgConfig, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByOrgId(UUID)` | `Optional<OrgConfig>` | Find org config by org ID |

---

### TeamConfigOverrideRepository
**Extends:** `JpaRepository<TeamConfigOverride, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByTeamId(UUID)` | `Optional<TeamConfigOverride>` | Find team config override by team ID |

---

### ManagerCommentRepository
**Extends:** `JpaRepository<ManagerComment, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByPlanIdOrderByCreatedAtAsc(UUID)` | `List<ManagerComment>` | Find comments on plan (chronological) |
| `findByCommitIdOrderByCreatedAtAsc(UUID)` | `List<ManagerComment>` | Find comments on commit (chronological) |
| `findByAuthorUserId(UUID)` | `List<ManagerComment>` | Find all comments by user |

---

### ScopeChangeEventRepository
**Extends:** `JpaRepository<ScopeChangeEvent, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByPlanIdOrderByCreatedAtAsc(UUID)` | `List<ScopeChangeEvent>` | Find scope changes for plan (chronological) |
| `findByCommitId(UUID)` | `List<ScopeChangeEvent>` | Find scope changes for commit |

---

### NotificationRepository
**Extends:** `JpaRepository<Notification, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByRecipientUserIdAndReadOrderByCreatedAtDesc(UUID, boolean)` | `List<Notification>` | Find notifications by read status |
| `findByRecipientUserIdOrderByCreatedAtDesc(UUID)` | `List<Notification>` | Find all notifications for user |
| `findByRecipientUserIdOrderByCreatedAtDesc(UUID, Pageable)` | `Page<Notification>` | Find notifications (paginated) |
| `findByRecipientUserIdAndReadOrderByCreatedAtDesc(UUID, boolean, Pageable)` | `Page<Notification>` | Find notifications by status (paginated) |
| `countByRecipientUserIdAndRead(UUID, boolean)` | `long` | Count unread notifications for user |
| `countTodayByUserTypeAndRef(@Param("userId") UUID, @Param("type") String, @Param("refId") UUID, @Param("startOfDay") Instant)` | `long` | Count notifications sent today (frequency check) |
| `countTodayByUserAndType(@Param("userId") UUID, @Param("type") String, @Param("startOfDay") Instant)` | `long` | Count notifications by type today (no specific ref) |
| `findByRecipientUserIdAndPriorityAndReadOrderByCreatedAtAsc(UUID, String, boolean)` | `List<Notification>` | Find unread notifications by priority (digest assembly) |
| `markAllReadForUser(@Param("userId") UUID)` | `int` | Bulk mark-all-read for user |

**JPA Features:**
- `@Modifying` annotation on update/delete operations

---

### NotificationDeliveryRepository
**Extends:** `JpaRepository<NotificationDelivery, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByNotificationId(UUID)` | `List<NotificationDelivery>` | Find delivery attempts for notification |

---

### AiSuggestionRepository
**Extends:** `JpaRepository<AiSuggestion, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByPlanId(UUID)` | `List<AiSuggestion>` | Find suggestions for plan |
| `findByCommitId(UUID)` | `List<AiSuggestion>` | Find suggestions for commit |
| `findByPlanIdAndSuggestionType(UUID, String)` | `List<AiSuggestion>` | Find suggestions by type for plan |
| `findByUserId(UUID)` | `List<AiSuggestion>` | Find suggestions for user |
| `findByUserIdAndSuggestionType(UUID, String)` | `List<AiSuggestion>` | Find suggestions by type for user |
| `findByTeamIdAndWeekStartDateAndSuggestionType(UUID, LocalDate, String)` | `List<AiSuggestion>` | Find suggestions for team + week + type |
| `avgFaithfulnessByType(@Param("since") Instant)` | `List<Object[]>` | Average faithfulness score by type since time |
| `acceptanceRateByType(@Param("since") Instant)` | `List<Object[]>` | Acceptance rate by type since time |

---

### AiFeedbackRepository
**Extends:** `JpaRepository<AiFeedback, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `countByAccepted(boolean)` | `long` | Count feedback by acceptance status |

---

### LockSnapshotHeaderRepository
**Extends:** `JpaRepository<LockSnapshotHeader, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByPlanId(UUID)` | `Optional<LockSnapshotHeader>` | Find snapshot for plan |

---

### LockSnapshotCommitRepository
**Extends:** `JpaRepository<LockSnapshotCommit, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findBySnapshotId(UUID)` | `List<LockSnapshotCommit>` | Find commits in snapshot |

---

### ReconcileSnapshotHeaderRepository
**Extends:** `JpaRepository<ReconcileSnapshotHeader, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByPlanId(UUID)` | `Optional<ReconcileSnapshotHeader>` | Find snapshot for plan |

---

### ReconcileSnapshotCommitRepository
**Extends:** `JpaRepository<ReconcileSnapshotCommit, UUID>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findBySnapshotId(UUID)` | `List<ReconcileSnapshotCommit>` | Find commits in snapshot |

---

### AuditLogRepository
**Extends:** `JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog>`

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String, UUID)` | `List<AuditLog>` | Find audit trail for entity |
| `findByActorUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID, Instant)` | `List<AuditLog>` | Find actions by user since time |

**JPA Features:**
- `JpaSpecificationExecutor` allows dynamic query building

---

## Entity Relationship Diagram (Text Summary)

```
Organization (1) ──┬─ (N) Team
                   ├─ (N) UserAccount
                   └─ (1) OrgConfig

Team (1) ──┬─ (N) TeamMembership ──┐
           ├─ (N) WeeklyPlan       │
           ├─ (N) WorkItem         │
           ├─ (N) RcdoNode         ├─ (N) UserAccount
           ├─ (1) TeamConfigOverride
           └─ (N) ManagerReviewException

WeeklyPlan (1) ──┬─ (N) WeeklyCommit ──┬─ (1) RcdoNode
                 ├─ (1) LockSnapshotHeader
                 ├─ (1) ReconcileSnapshotHeader
                 ├─ (N) ManagerComment
                 ├─ (N) ManagerReviewException
                 ├─ (N) ScopeChangeEvent
                 └─ (N) UserWeekFact

WeeklyCommit (1) ──┬─ (1) WorkItem
                   ├─ (1) CarryForwardFact
                   ├─ (N) CarryForwardLink (outbound)
                   ├─ (N) ManagerComment
                   └─ (N) ScopeChangeEvent

RcdoNode (1) ──┬─ (N) RcdoNode (children)
               ├─ (N) WeeklyCommit
               ├─ (N) WorkItem
               ├─ (N) RcdoWeekRollup
               └─ (N) RcdoChangeLog

WorkItem (1) ──┬─ (N) WeeklyCommit
               ├─ (N) WorkItemComment
               └─ (N) WorkItemStatusHistory

UserAccount (1) ──┬─ (N) TeamMembership
                  ├─ (N) WeeklyPlan (owner)
                  ├─ (N) WeeklyCommit (owner)
                  ├─ (N) UserWeekFact
                  ├─ (N) AiSuggestion
                  ├─ (N) AiFeedback
                  ├─ (N) Notification
                  └─ (N) AuditLog

Notification (1) ──── (N) NotificationDelivery

AiSuggestion (1) ──── (N) AiFeedback

LockSnapshotHeader (1) ──── (N) LockSnapshotCommit
ReconcileSnapshotHeader (1) ──── (N) ReconcileSnapshotCommit
```

---

## Key Design Patterns

### 1. **Immutable Snapshots**
- `LockSnapshotHeader/Commit` and `ReconcileSnapshotHeader/Commit` capture state at critical moments
- All snapshot fields are immutable after creation
- Used as audit trail and for historical comparison

### 2. **Denormalized Read Models**
- `UserWeekFact`, `TeamWeekRollup`, `RcdoWeekRollup` pre-aggregate data
- Refreshed on lifecycle events + scheduled cadence (5 min)
- Improves query performance for dashboards and reporting

### 3. **Fact Tables**
- `CarryForwardFact`, `ComplianceFact`: one-off computed facts
- Also refreshed on lifecycle events and scheduled cadence
- Enable fast compliance checking and carry-forward tracking

### 4. **Hierarchical Structures**
- `Team` supports parent-child hierarchy (parentTeamId)
- `RcdoNode` supports hierarchy (RALLY_CRY → DEFINING_OBJECTIVE → OUTCOME)

### 5. **Immutable Audit Log**
- `AuditLog` is append-only (no updates/deletes after creation)
- Captures who, what, when, where, and before/after state (JSON)

### 6. **Configuration Cascading**
- `OrgConfig` provides defaults
- `TeamConfigOverride` allows team-level overrides (nullable fields)
- Enables flexible cadence per team while maintaining org defaults

### 7. **Flexible Comments**
- `ManagerComment` can link to either Plan OR Commit (not both)
- Allows feedback at multiple levels

### 8. **JSON Columns**
- `ColumnTransformer` for JSONB serialization
- Used for flexible structures (snapshots, breakdowns, change logs)

---

## Key Constraints

| Constraint | Entity | Details |
|-----------|--------|---------|
| Unique | `WeeklyPlan` | (ownerUserId, weekStartDate) |
| Unique | `WorkItem` | (teamId, key) — implied |
| Unique | `LockSnapshotHeader` | (planId) |
| Unique | `ReconcileSnapshotHeader` | (planId) |
| Foreign Key | `Team` | organizationId → Organization.id |
| Foreign Key | `UserAccount` | organizationId → Organization.id |
| Immutable | All | `createdAt` and `updatedAt` columns never updated after insert |

---

## Common Query Patterns

### Weekly Planning Lifecycle
1. **DRAFT Phase:** Find plans by state + lock deadline range
2. **LOCK Phase:** Capture LockSnapshotHeader + child LockSnapshotCommits
3. **RECONCILING Phase:** Find plans with reconcile deadline, allow outcome entry
4. **RECONCILED Phase:** Capture ReconcileSnapshotHeader, finalize

### Exception Detection
1. Query `ManagerReviewException` by team + week for dashboard
2. Use idempotent checks (findByPlanIdAndExceptionTypeAndResolved) to avoid duplicates
3. Update `resolved` flag when manager takes action

### Carry-Forward Tracking
1. Query `CarryForwardFact` for active carry-forwards
2. Use `CarryForwardLink` to track source → target commit chain
3. Increment `carryForwardStreak` on commit entity

### Compliance Reporting
1. Query `ComplianceFact` for user + week compliance status
2. Query `UserWeekFact` for aggregate stats
3. Query `TeamWeekRollup` for team-level summaries

### AI Feedback Loop
1. Generate `AiSuggestion` with prompt, rationale, payload
2. Query `AiFeedback` to check user acceptance
3. Aggregate `avgFaithfulnessByType` and `acceptanceRateByType` for model evaluation

---

## Summary Statistics

| Category | Count |
|----------|-------|
| **Entities** | 30 |
| **Enums** | 11 |
| **Repositories** | 34 |
| **Core Domain Entities** | 4 (Org, Team, User, TeamMembership) |
| **Weekly Planning Entities** | 2 (WeeklyPlan, WeeklyCommit) |
| **Work Item Entities** | 3 (WorkItem, WorkItemComment, WorkItemStatusHistory) |
| **RCDO Entities** | 2 (RcdoNode, RcdoChangeLog) |
| **Snapshot Entities** | 4 (Lock/Reconcile Headers & Commits) |
| **Read-Model Entities** | 4 (UserWeekFact, TeamWeekRollup, RcdoWeekRollup, CarryForwardFact) |
| **Compliance Entities** | 2 (ComplianceFact, ManagerReviewException) |
| **AI Entities** | 2 (AiSuggestion, AiFeedback) |
| **Notification Entities** | 2 (Notification, NotificationDelivery) |
| **Config/Audit Entities** | 5 (OrgConfig, TeamConfigOverride, CapacityOverride, ManagerComment, AuditLog, ScopeChangeEvent) |

---

**Document Generated:** 2026-03-29
**Weekly Commit Module - Backend Domain Model Reference v1.0**
