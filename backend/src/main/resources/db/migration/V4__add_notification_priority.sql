-- =============================================================================
-- Weekly Commit Module — V4: Add priority column to notification table
-- =============================================================================

ALTER TABLE notification
    ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM';

ALTER TABLE notification
    ADD CONSTRAINT chk_notification_priority
    CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'));

CREATE INDEX idx_notif_priority ON notification (recipient_user_id, priority, read);
