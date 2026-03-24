-- =============================================================================
-- Weekly Commit Module — Initial Schema (V1)
-- All timestamps are stored as TIMESTAMPTZ (UTC).
-- Primary keys use gen_random_uuid() (PostgreSQL 13+).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Organisation and team hierarchy
-- ---------------------------------------------------------------------------

CREATE TABLE organization (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE team (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID        NOT NULL REFERENCES organization (id),
    name            TEXT        NOT NULL,
    parent_team_id  UUID        REFERENCES team (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_account (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID        NOT NULL REFERENCES organization (id),
    email                 TEXT        NOT NULL,
    display_name          TEXT        NOT NULL,
    home_team_id          UUID        REFERENCES team (id),
    role                  TEXT        NOT NULL DEFAULT 'IC',
    weekly_capacity_points SMALLINT   NOT NULL DEFAULT 10,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_org_email UNIQUE (organization_id, email)
);

CREATE TABLE team_membership (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id    UUID        NOT NULL REFERENCES team (id),
    user_id    UUID        NOT NULL REFERENCES user_account (id),
    role       TEXT        NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_team_membership UNIQUE (team_id, user_id)
);

-- ---------------------------------------------------------------------------
-- RCDO hierarchy
-- ---------------------------------------------------------------------------

CREATE TABLE rcdo_node (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    node_type     TEXT        NOT NULL
                              CHECK (node_type IN ('RALLY_CRY', 'DEFINING_OBJECTIVE', 'OUTCOME')),
    status        TEXT        NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    parent_id     UUID        REFERENCES rcdo_node (id),
    title         TEXT        NOT NULL,
    description   TEXT,
    owner_team_id UUID        REFERENCES team (id),
    owner_user_id UUID        REFERENCES user_account (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rcdo_change_log (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rcdo_node_id      UUID        NOT NULL REFERENCES rcdo_node (id),
    changed_by_user_id UUID       NOT NULL REFERENCES user_account (id),
    change_summary    TEXT        NOT NULL,
    previous_value    JSONB,
    new_value         JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Weekly plan (one per user per week)
-- ---------------------------------------------------------------------------

CREATE TABLE weekly_plan (
    id                       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id            UUID        NOT NULL REFERENCES user_account (id),
    team_id                  UUID        NOT NULL REFERENCES team (id),
    week_start_date          DATE        NOT NULL,
    state                    TEXT        NOT NULL DEFAULT 'DRAFT'
                                         CHECK (state IN ('DRAFT', 'LOCKED', 'RECONCILING', 'RECONCILED')),
    lock_deadline            TIMESTAMPTZ NOT NULL,
    reconcile_deadline       TIMESTAMPTZ NOT NULL,
    capacity_budget_points   SMALLINT    NOT NULL DEFAULT 10,
    is_compliant             BOOLEAN     NOT NULL DEFAULT TRUE,
    system_locked_with_errors BOOLEAN    NOT NULL DEFAULT FALSE,
    -- Back-references populated after snapshots are created
    lock_snapshot_id         UUID,
    reconcile_snapshot_id    UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_plan_user_week UNIQUE (owner_user_id, week_start_date)
);

-- ---------------------------------------------------------------------------
-- Weekly commit  (table name avoids reserved word 'commit')
-- ---------------------------------------------------------------------------

CREATE TABLE weekly_commit (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id                UUID        NOT NULL REFERENCES weekly_plan (id),
    owner_user_id          UUID        NOT NULL REFERENCES user_account (id),
    title                  TEXT        NOT NULL,
    description            TEXT,
    chess_piece            TEXT        NOT NULL
                                       CHECK (chess_piece IN ('KING','QUEEN','ROOK','BISHOP','KNIGHT','PAWN')),
    priority_order         INTEGER     NOT NULL,
    rcdo_node_id           UUID        REFERENCES rcdo_node (id),
    -- work_item FK added below after work_item table is created
    work_item_id           UUID,
    estimate_points        SMALLINT    CHECK (estimate_points IN (1, 2, 3, 5, 8)),
    success_criteria       TEXT,
    outcome                TEXT        CHECK (outcome IN ('ACHIEVED','PARTIALLY_ACHIEVED','NOT_ACHIEVED','CANCELED')),
    outcome_notes          TEXT,
    carry_forward_source_id UUID       REFERENCES weekly_commit (id),
    carry_forward_streak   INTEGER     NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Work item (native ticket)
-- ---------------------------------------------------------------------------

CREATE TABLE work_item (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id               UUID        NOT NULL REFERENCES team (id),
    key                   TEXT        NOT NULL,
    title                 TEXT        NOT NULL,
    description           TEXT,
    status                TEXT        NOT NULL DEFAULT 'BACKLOG'
                                      CHECK (status IN ('BACKLOG','READY','IN_PROGRESS','BLOCKED','DONE','CANCELED')),
    assignee_user_id      UUID        REFERENCES user_account (id),
    reporter_user_id      UUID        NOT NULL REFERENCES user_account (id),
    estimate_points       SMALLINT    CHECK (estimate_points IN (1, 2, 3, 5, 8)),
    rcdo_node_id          UUID        REFERENCES rcdo_node (id),
    target_week_start_date DATE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_work_item_key UNIQUE (team_id, key)
);

-- Now that work_item exists, add the FK from weekly_commit
ALTER TABLE weekly_commit
    ADD CONSTRAINT fk_weekly_commit_work_item
    FOREIGN KEY (work_item_id) REFERENCES work_item (id);

CREATE TABLE work_item_status_history (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    work_item_id       UUID        NOT NULL REFERENCES work_item (id),
    from_status        TEXT,
    to_status          TEXT        NOT NULL,
    changed_by_user_id UUID        NOT NULL REFERENCES user_account (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE work_item_comment (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    work_item_id   UUID        NOT NULL REFERENCES work_item (id),
    author_user_id UUID        NOT NULL REFERENCES user_account (id),
    content        TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Lock snapshot (immutable baseline)
-- ---------------------------------------------------------------------------

CREATE TABLE lock_snapshot_header (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID        NOT NULL REFERENCES weekly_plan (id),
    locked_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_by_system BOOLEAN     NOT NULL DEFAULT FALSE,
    snapshot_payload JSONB       NOT NULL,
    CONSTRAINT uq_lock_snapshot_plan UNIQUE (plan_id)
);

CREATE TABLE lock_snapshot_commit (
    id            UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id   UUID  NOT NULL REFERENCES lock_snapshot_header (id),
    commit_id     UUID  NOT NULL REFERENCES weekly_commit (id),
    snapshot_data JSONB NOT NULL
);

-- Back-fill FK on weekly_plan once lock_snapshot_header exists
ALTER TABLE weekly_plan
    ADD CONSTRAINT fk_weekly_plan_lock_snapshot
    FOREIGN KEY (lock_snapshot_id) REFERENCES lock_snapshot_header (id);

-- ---------------------------------------------------------------------------
-- Scope change events (append-only post-lock changes)
-- ---------------------------------------------------------------------------

CREATE TABLE scope_change_event (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id            UUID        NOT NULL REFERENCES weekly_plan (id),
    commit_id          UUID        REFERENCES weekly_commit (id),
    category           TEXT        NOT NULL
                                   CHECK (category IN (
                                       'COMMIT_ADDED','COMMIT_REMOVED','ESTIMATE_CHANGED',
                                       'CHESS_PIECE_CHANGED','RCDO_CHANGED','PRIORITY_CHANGED')),
    changed_by_user_id UUID        NOT NULL REFERENCES user_account (id),
    reason             TEXT        NOT NULL,
    previous_value     JSONB,
    new_value          JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Reconciliation snapshot (immutable actual record)
-- ---------------------------------------------------------------------------

CREATE TABLE reconcile_snapshot_header (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID        NOT NULL REFERENCES weekly_plan (id),
    reconciled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    snapshot_payload JSONB       NOT NULL,
    CONSTRAINT uq_reconcile_snapshot_plan UNIQUE (plan_id)
);

CREATE TABLE reconcile_snapshot_commit (
    id            UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id   UUID  NOT NULL REFERENCES reconcile_snapshot_header (id),
    commit_id     UUID  NOT NULL REFERENCES weekly_commit (id),
    outcome       TEXT  CHECK (outcome IN ('ACHIEVED','PARTIALLY_ACHIEVED','NOT_ACHIEVED','CANCELED')),
    snapshot_data JSONB NOT NULL
);

ALTER TABLE weekly_plan
    ADD CONSTRAINT fk_weekly_plan_reconcile_snapshot
    FOREIGN KEY (reconcile_snapshot_id) REFERENCES reconcile_snapshot_header (id);

-- ---------------------------------------------------------------------------
-- Carry-forward lineage
-- ---------------------------------------------------------------------------

CREATE TABLE carry_forward_link (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    source_commit_id UUID        NOT NULL REFERENCES weekly_commit (id),
    target_commit_id UUID        NOT NULL REFERENCES weekly_commit (id),
    reason           TEXT        NOT NULL
                                 CHECK (reason IN (
                                     'BLOCKED_BY_DEPENDENCY','SCOPE_EXPANDED','REPRIORITIZED',
                                     'RESOURCE_UNAVAILABLE','TECHNICAL_OBSTACLE','EXTERNAL_DELAY',
                                     'UNDERESTIMATED','STILL_IN_PROGRESS')),
    reason_notes     TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_carry_forward UNIQUE (source_commit_id, target_commit_id)
);

-- ---------------------------------------------------------------------------
-- Manager comments
-- ---------------------------------------------------------------------------

CREATE TABLE manager_comment (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id        UUID        REFERENCES weekly_plan (id),
    commit_id      UUID        REFERENCES weekly_commit (id),
    author_user_id UUID        NOT NULL REFERENCES user_account (id),
    content        TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Capacity overrides
-- ---------------------------------------------------------------------------

CREATE TABLE capacity_override (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES user_account (id),
    week_start_date     DATE        NOT NULL,
    budget_points       SMALLINT    NOT NULL,
    reason              TEXT,
    set_by_manager_id   UUID        NOT NULL REFERENCES user_account (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_capacity_override UNIQUE (user_id, week_start_date)
);

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------

CREATE TABLE notification (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id  UUID        NOT NULL REFERENCES user_account (id),
    notification_type  TEXT        NOT NULL,
    title              TEXT        NOT NULL,
    body               TEXT        NOT NULL,
    reference_id       UUID,
    reference_type     TEXT,
    read               BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification_delivery (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID        NOT NULL REFERENCES notification (id),
    channel         TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'PENDING',
    sent_at         TIMESTAMPTZ,
    opened_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- AI suggestions and feedback
-- ---------------------------------------------------------------------------

CREATE TABLE ai_suggestion (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id            UUID        REFERENCES weekly_plan (id),
    commit_id          UUID        REFERENCES weekly_commit (id),
    suggestion_type    TEXT        NOT NULL,
    prompt             TEXT        NOT NULL,
    rationale          TEXT        NOT NULL,
    suggestion_payload JSONB       NOT NULL,
    model_version      TEXT        NOT NULL,
    accepted           BOOLEAN,
    dismissed          BOOLEAN,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_feedback (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    suggestion_id   UUID        NOT NULL REFERENCES ai_suggestion (id),
    user_id         UUID        NOT NULL REFERENCES user_account (id),
    accepted        BOOLEAN     NOT NULL,
    feedback_notes  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Audit log
-- ---------------------------------------------------------------------------

CREATE TABLE audit_log (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID       REFERENCES user_account (id),
    action       TEXT        NOT NULL,
    entity_type  TEXT        NOT NULL,
    entity_id    UUID,
    old_value    JSONB,
    new_value    JSONB,
    ip_address   TEXT,
    user_agent   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- Indexes for common query patterns
-- =============================================================================

CREATE INDEX idx_team_org          ON team (organization_id);
CREATE INDEX idx_user_org          ON user_account (organization_id);
CREATE INDEX idx_user_home_team    ON user_account (home_team_id);
CREATE INDEX idx_team_membership   ON team_membership (user_id);

CREATE INDEX idx_rcdo_parent       ON rcdo_node (parent_id);
CREATE INDEX idx_rcdo_status       ON rcdo_node (status);
CREATE INDEX idx_rcdo_change_node  ON rcdo_change_log (rcdo_node_id);

CREATE INDEX idx_plan_owner_week   ON weekly_plan (owner_user_id, week_start_date);
CREATE INDEX idx_plan_team_week    ON weekly_plan (team_id, week_start_date);
CREATE INDEX idx_plan_state        ON weekly_plan (state);

CREATE INDEX idx_commit_plan       ON weekly_commit (plan_id);
CREATE INDEX idx_commit_owner      ON weekly_commit (owner_user_id);
CREATE INDEX idx_commit_rcdo       ON weekly_commit (rcdo_node_id);
CREATE INDEX idx_commit_work_item  ON weekly_commit (work_item_id);

CREATE INDEX idx_work_item_team    ON work_item (team_id);
CREATE INDEX idx_work_item_assignee ON work_item (assignee_user_id);
CREATE INDEX idx_work_item_week    ON work_item (target_week_start_date);
CREATE INDEX idx_work_item_status  ON work_item (status);

CREATE INDEX idx_scope_change_plan ON scope_change_event (plan_id);
CREATE INDEX idx_scope_change_commit ON scope_change_event (commit_id);

CREATE INDEX idx_lock_snap_commit  ON lock_snapshot_commit (snapshot_id);
CREATE INDEX idx_recon_snap_commit ON reconcile_snapshot_commit (snapshot_id);

CREATE INDEX idx_carry_forward_src ON carry_forward_link (source_commit_id);
CREATE INDEX idx_carry_forward_tgt ON carry_forward_link (target_commit_id);

CREATE INDEX idx_mgr_comment_plan  ON manager_comment (plan_id);
CREATE INDEX idx_mgr_comment_commit ON manager_comment (commit_id);

CREATE INDEX idx_notif_recipient   ON notification (recipient_user_id, read);
CREATE INDEX idx_notif_delivery    ON notification_delivery (notification_id);

CREATE INDEX idx_ai_suggestion_plan   ON ai_suggestion (plan_id);
CREATE INDEX idx_ai_suggestion_commit ON ai_suggestion (commit_id);
CREATE INDEX idx_ai_feedback_sugg     ON ai_feedback (suggestion_id);

CREATE INDEX idx_audit_entity      ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_actor       ON audit_log (actor_user_id, created_at);
