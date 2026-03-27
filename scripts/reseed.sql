-- =============================================================================
-- reseed.sql — step 1: wipe all mutable runtime data.
--
-- Preserves: organization, team, user_account, team_membership, rcdo_node,
--            org_config, flyway_schema_history.
-- Wipes:     everything users create at runtime (plans, commits, tickets,
--            notifications, AI suggestions, exceptions, snapshots, …).
--
-- Run via:  make reseed   (chains this file then the V11 demo seed)
-- =============================================================================

BEGIN;

-- ── Snapshots ────────────────────────────────────────────────────────────────
-- Clear back-references on plans first (weekly_plan FK → snapshot headers)
UPDATE weekly_plan SET lock_snapshot_id = NULL, reconcile_snapshot_id = NULL;
DELETE FROM reconcile_snapshot_commit;
DELETE FROM reconcile_snapshot_header;
DELETE FROM lock_snapshot_commit;
DELETE FROM lock_snapshot_header;

-- ── AI ───────────────────────────────────────────────────────────────────────
DELETE FROM ai_feedback;
DELETE FROM ai_suggestion;

-- ── Carry-forward ────────────────────────────────────────────────────────────
DELETE FROM carry_forward_link;
DELETE FROM carry_forward_fact;

-- ── Plan details ─────────────────────────────────────────────────────────────
DELETE FROM scope_change_event;
DELETE FROM manager_comment;
DELETE FROM manager_review_exception;

-- ── Commits then plans ───────────────────────────────────────────────────────
DELETE FROM weekly_commit;
DELETE FROM weekly_plan;

-- ── Tickets ──────────────────────────────────────────────────────────────────
DELETE FROM work_item_status_history;
DELETE FROM work_item_comment;
DELETE FROM work_item;

-- ── Notifications ─────────────────────────────────────────────────────────────
DELETE FROM notification_delivery;
DELETE FROM notification;

-- ── Capacity overrides ────────────────────────────────────────────────────────
DELETE FROM capacity_override;

-- ── RCDO change log ───────────────────────────────────────────────────────────
DELETE FROM rcdo_change_log;

-- ── Rollup read-models ────────────────────────────────────────────────────────
DELETE FROM compliance_fact;
DELETE FROM team_week_rollup;
DELETE FROM rcdo_week_rollup;

-- ── Restore static reference data (idempotent) ───────────────────────────────

INSERT INTO organization (id, name)
VALUES ('00000000-0000-0000-0000-000000000100', 'Acme Corp')
ON CONFLICT DO NOTHING;

INSERT INTO org_config (org_id, lock_due_offset_hours, reconcile_due_offset_hours)
VALUES ('00000000-0000-0000-0000-000000000100', 120, 240)
ON CONFLICT (org_id) DO UPDATE
  SET lock_due_offset_hours     = 120,
      reconcile_due_offset_hours = 240;

INSERT INTO team (id, organization_id, name)
VALUES ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000100', 'Engineering')
ON CONFLICT DO NOTHING;

INSERT INTO user_account (id, organization_id, email, display_name, home_team_id, role)
VALUES
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100',
     'dev@example.com',     'Dev User',    '00000000-0000-0000-0000-000000000010', 'MANAGER'),
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000100',
     'manager@example.com', 'Manager One', '00000000-0000-0000-0000-000000000010', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO team_membership (team_id, user_id, role)
VALUES
    ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', 'MANAGER'),
    ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000002', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-aaaa-000000000001', 'RALLY_CRY', 'Grow the Business',      'ACTIVE', NULL),
    ('00000000-0000-0000-aaaa-000000000002', 'RALLY_CRY', 'Operational Excellence', 'ACTIVE', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-bbbb-000000000001', 'DEFINING_OBJECTIVE', 'Increase Revenue', 'ACTIVE', '00000000-0000-0000-aaaa-000000000001'),
    ('00000000-0000-0000-bbbb-000000000002', 'DEFINING_OBJECTIVE', 'Reduce Churn',     'ACTIVE', '00000000-0000-0000-aaaa-000000000001'),
    ('00000000-0000-0000-bbbb-000000000003', 'DEFINING_OBJECTIVE', 'Ship Faster',      'ACTIVE', '00000000-0000-0000-aaaa-000000000002')
ON CONFLICT DO NOTHING;

INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-cccc-000000000001', 'OUTCOME', 'Close 10 Enterprise Deals',     'ACTIVE', '00000000-0000-0000-bbbb-000000000001'),
    ('00000000-0000-0000-cccc-000000000002', 'OUTCOME', 'Expand to EMEA Market',          'ACTIVE', '00000000-0000-0000-bbbb-000000000001'),
    ('00000000-0000-0000-cccc-000000000003', 'OUTCOME', 'Improve Onboarding NPS to 60+',  'ACTIVE', '00000000-0000-0000-bbbb-000000000002'),
    ('00000000-0000-0000-cccc-000000000004', 'OUTCOME', 'Deploy Daily by Q2',             'ACTIVE', '00000000-0000-0000-bbbb-000000000003')
ON CONFLICT DO NOTHING;

COMMIT;
