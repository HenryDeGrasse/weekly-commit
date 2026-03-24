-- =============================================================================
-- Weekly Commit Module — V3: Add ticket priority and align ticket status values
-- =============================================================================

ALTER TABLE work_item
    ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM';

ALTER TABLE work_item
    ADD CONSTRAINT chk_work_item_priority
    CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'));

CREATE INDEX idx_work_item_priority ON work_item (priority);

UPDATE work_item
SET status = 'TODO'
WHERE status IN ('BACKLOG', 'READY');

DO $$
DECLARE
    status_constraint_name TEXT;
BEGIN
    SELECT c.conname
    INTO status_constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    WHERE t.relname = 'work_item'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) LIKE '%status IN (%';

    IF status_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE work_item DROP CONSTRAINT %I', status_constraint_name);
    END IF;
END $$;

ALTER TABLE work_item
    ADD CONSTRAINT chk_work_item_status
    CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELED'));

ALTER TABLE work_item
    ALTER COLUMN status SET DEFAULT 'TODO';
