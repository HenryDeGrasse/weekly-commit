-- =============================================================================
-- Weekly Commit Module — V6: Derived read-model tables for reporting (PRD §21)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- user_week_fact — per-user per-week aggregate
-- ---------------------------------------------------------------------------

CREATE TABLE user_week_fact (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID        NOT NULL REFERENCES user_account (id),
    week_start              DATE        NOT NULL,
    plan_state              TEXT,
    lock_compliance         BOOLEAN     NOT NULL DEFAULT FALSE,
    reconcile_compliance    BOOLEAN     NOT NULL DEFAULT FALSE,
    total_planned_points    INT         NOT NULL DEFAULT 0,
    total_achieved_points   INT         NOT NULL DEFAULT 0,
    commit_count            INT         NOT NULL DEFAULT 0,
    carry_forward_count     INT         NOT NULL DEFAULT 0,
    scope_change_count      INT         NOT NULL DEFAULT 0,
    king_count              INT         NOT NULL DEFAULT 0,
    queen_count             INT         NOT NULL DEFAULT 0,
    refreshed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_week_fact UNIQUE (user_id, week_start)
);

-- ---------------------------------------------------------------------------
-- team_week_rollup — per-team per-week aggregate
-- ---------------------------------------------------------------------------

CREATE TABLE team_week_rollup (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id                 UUID        NOT NULL REFERENCES team (id),
    week_start              DATE        NOT NULL,
    member_count            INT         NOT NULL DEFAULT 0,
    locked_count            INT         NOT NULL DEFAULT 0,
    reconciled_count        INT         NOT NULL DEFAULT 0,
    total_planned_points    INT         NOT NULL DEFAULT 0,
    total_achieved_points   INT         NOT NULL DEFAULT 0,
    exception_count         INT         NOT NULL DEFAULT 0,
    avg_carry_forward_rate  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    chess_distribution      JSONB,
    refreshed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_team_week_rollup UNIQUE (team_id, week_start)
);

-- ---------------------------------------------------------------------------
-- rcdo_week_rollup — per-RCDO-node per-week aggregate
-- ---------------------------------------------------------------------------

CREATE TABLE rcdo_week_rollup (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rcdo_node_id                UUID        NOT NULL REFERENCES rcdo_node (id),
    week_start                  DATE        NOT NULL,
    planned_points              INT         NOT NULL DEFAULT 0,
    achieved_points             INT         NOT NULL DEFAULT 0,
    commit_count                INT         NOT NULL DEFAULT 0,
    team_contribution_breakdown JSONB,
    refreshed_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rcdo_week_rollup UNIQUE (rcdo_node_id, week_start)
);

-- ---------------------------------------------------------------------------
-- compliance_fact — per-user per-week lock/reconcile compliance detail
-- ---------------------------------------------------------------------------

CREATE TABLE compliance_fact (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES user_account (id),
    week_start          DATE        NOT NULL,
    lock_on_time        BOOLEAN     NOT NULL DEFAULT FALSE,
    lock_late           BOOLEAN     NOT NULL DEFAULT FALSE,
    auto_locked         BOOLEAN     NOT NULL DEFAULT FALSE,
    reconcile_on_time   BOOLEAN     NOT NULL DEFAULT FALSE,
    reconcile_late      BOOLEAN     NOT NULL DEFAULT FALSE,
    reconcile_missed    BOOLEAN     NOT NULL DEFAULT FALSE,
    refreshed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_compliance_fact UNIQUE (user_id, week_start)
);

-- ---------------------------------------------------------------------------
-- carry_forward_fact — one row per active carry-forward commit
-- ---------------------------------------------------------------------------

CREATE TABLE carry_forward_fact (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    commit_id       UUID        NOT NULL REFERENCES weekly_commit (id),
    source_week     DATE        NOT NULL,
    current_week    DATE        NOT NULL,
    streak_length   INT         NOT NULL DEFAULT 0,
    rcdo_node_id    UUID        REFERENCES rcdo_node (id),
    chess_piece     TEXT,
    refreshed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_carry_forward_fact UNIQUE (commit_id)
);

-- ---------------------------------------------------------------------------
-- Indexes for common report query patterns
-- ---------------------------------------------------------------------------

CREATE INDEX idx_user_week_fact_user_week  ON user_week_fact  (user_id, week_start);
CREATE INDEX idx_team_week_rollup_team_week ON team_week_rollup (team_id, week_start);
CREATE INDEX idx_rcdo_week_rollup_node_week ON rcdo_week_rollup (rcdo_node_id, week_start);
CREATE INDEX idx_compliance_fact_user_week  ON compliance_fact  (user_id, week_start);
CREATE INDEX idx_carry_forward_fact_commit  ON carry_forward_fact (commit_id);
CREATE INDEX idx_carry_forward_fact_weeks   ON carry_forward_fact (current_week, source_week);
