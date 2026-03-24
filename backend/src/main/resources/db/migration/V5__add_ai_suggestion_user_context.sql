-- =============================================================================
-- Weekly Commit Module — V5: Add user_id and context_hash to ai_suggestion
-- =============================================================================

ALTER TABLE ai_suggestion
    ADD COLUMN user_id     UUID REFERENCES user_account (id),
    ADD COLUMN context_hash TEXT;

CREATE INDEX idx_ai_suggestion_user ON ai_suggestion (user_id);
CREATE INDEX idx_ai_suggestion_type_plan ON ai_suggestion (suggestion_type, plan_id);
