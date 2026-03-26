-- =============================================================================
-- Weekly Commit Module — V10: Add team_id and week_start_date to ai_suggestion
-- Enables TEAM_INSIGHT and PERSONAL_INSIGHT rows to be queried by team+week.
-- =============================================================================

ALTER TABLE ai_suggestion
    ADD COLUMN team_id         UUID REFERENCES team (id),
    ADD COLUMN week_start_date DATE;

CREATE INDEX idx_ai_suggestion_team_week
    ON ai_suggestion (team_id, week_start_date, suggestion_type);
