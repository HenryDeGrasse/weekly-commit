-- =============================================================================
-- Weekly Commit Module — V2: Add manager_review_exception table
-- =============================================================================

CREATE TABLE manager_review_exception (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id             UUID        NOT NULL REFERENCES team (id),
    plan_id             UUID        REFERENCES weekly_plan (id),
    user_id             UUID        NOT NULL REFERENCES user_account (id),
    exception_type      TEXT        NOT NULL,
    severity            TEXT        NOT NULL,
    description         TEXT        NOT NULL,
    week_start_date     DATE        NOT NULL,
    resolved            BOOLEAN     NOT NULL DEFAULT FALSE,
    resolution          TEXT,
    resolved_at         TIMESTAMPTZ,
    resolved_by_id      UUID        REFERENCES user_account (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mgr_exc_team_week ON manager_review_exception (team_id, week_start_date);
CREATE INDEX idx_mgr_exc_plan_type ON manager_review_exception (plan_id, exception_type);
CREATE INDEX idx_mgr_exc_user      ON manager_review_exception (user_id);
CREATE INDEX idx_mgr_exc_resolved  ON manager_review_exception (resolved);
