-- =============================================================================
-- Weekly Commit Module — V8: Add actor_role to audit_log
-- =============================================================================

ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS actor_role TEXT;

CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log (created_at);
