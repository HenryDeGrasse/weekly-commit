-- =============================================================================
-- Configuration management: org-level cadence config + team-level overrides
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Org-level configuration (one row per organisation, with sensible defaults)
-- Default cadence per PRD §12:
--   draft opens Fri 12:00, lock due Mon 12:00, reconcile opens Fri 17:00,
--   reconcile due next Mon 10:00  (all offsets in hours from week-start Monday)
-- ---------------------------------------------------------------------------

CREATE TABLE org_config (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID        NOT NULL REFERENCES organization (id),
    week_start_day              TEXT        NOT NULL DEFAULT 'MONDAY',
    draft_open_offset_hours     INT         NOT NULL DEFAULT -60,
    lock_due_offset_hours       INT         NOT NULL DEFAULT 12,
    reconcile_open_offset_hours INT         NOT NULL DEFAULT 113,
    reconcile_due_offset_hours  INT         NOT NULL DEFAULT 178,
    default_weekly_budget       SMALLINT    NOT NULL DEFAULT 10,
    timezone                    TEXT        NOT NULL DEFAULT 'UTC',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_config UNIQUE (org_id)
);

-- ---------------------------------------------------------------------------
-- Team-level configuration overrides (nullable fields = fall back to org)
-- ---------------------------------------------------------------------------

CREATE TABLE team_config_override (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id                     UUID        NOT NULL REFERENCES team (id),
    week_start_day              TEXT,
    draft_open_offset_hours     INT,
    lock_due_offset_hours       INT,
    reconcile_open_offset_hours INT,
    reconcile_due_offset_hours  INT,
    default_weekly_budget       SMALLINT,
    timezone                    TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_team_config_override UNIQUE (team_id)
);

CREATE INDEX idx_org_config_org_id            ON org_config (org_id);
CREATE INDEX idx_team_config_override_team_id ON team_config_override (team_id);
