-- =============================================================================
-- V11 — Rich demo seed data
--
-- Provides a realistic 12-week history for the Weekly Commit application.
-- All IDs are deterministic UUIDs for reproducibility.
-- Safe to run repeatedly (ON CONFLICT DO NOTHING on all inserts).
--
-- Contents:
--   • 6 users (1 manager, 5 ICs) across 1 team
--   • 12 weeks of weekly plans (weeks -11 to 0, Mon 2026-01-05 → 2026-03-23)
--   • 5-8 commits per plan with varied chess pieces, outcomes, RCDO links
--   • Lock + reconcile snapshots for completed weeks
--   • Scope change events, carry-forward chains
--   • Manager comments
--   • Work items (tickets)
--   • Notifications (mix of read/unread)
--   • Manager review exceptions
-- =============================================================================

-- =========================================================================
-- 0a. Clean up existing data that might conflict with deterministic seed
--     (e.g., plans created via the UI during development)
-- =========================================================================

-- Delete in dependency order: children → parents
DELETE FROM reconcile_snapshot_commit WHERE snapshot_id IN (SELECT id FROM reconcile_snapshot_header WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010'));
DELETE FROM reconcile_snapshot_header WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010');
DELETE FROM lock_snapshot_commit WHERE snapshot_id IN (SELECT id FROM lock_snapshot_header WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010'));
DELETE FROM lock_snapshot_header WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010');
DELETE FROM ai_feedback WHERE suggestion_id IN (SELECT id FROM ai_suggestion WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010'));
DELETE FROM ai_suggestion WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010');
DELETE FROM carry_forward_link WHERE source_commit_id IN (SELECT id FROM weekly_commit WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010'));
DELETE FROM carry_forward_link WHERE target_commit_id IN (SELECT id FROM weekly_commit WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010'));
DELETE FROM scope_change_event WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010');
DELETE FROM manager_comment WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010');
DELETE FROM manager_review_exception WHERE team_id = '00000000-0000-0000-0000-000000000010';
DELETE FROM carry_forward_fact WHERE commit_id IN (SELECT id FROM weekly_commit WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010'));
DELETE FROM weekly_commit WHERE plan_id IN (SELECT id FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010');
UPDATE weekly_plan SET lock_snapshot_id = NULL, reconcile_snapshot_id = NULL WHERE team_id = '00000000-0000-0000-0000-000000000010';
DELETE FROM weekly_plan WHERE team_id = '00000000-0000-0000-0000-000000000010';
DELETE FROM notification WHERE recipient_user_id IN (SELECT user_id FROM team_membership WHERE team_id = '00000000-0000-0000-0000-000000000010');
DELETE FROM notification_delivery WHERE notification_id NOT IN (SELECT id FROM notification);

-- =========================================================================
-- 0b. Additional users (V9 already created user-001 and user-002)
-- =========================================================================

INSERT INTO user_account (id, organization_id, email, display_name, home_team_id, role, weekly_capacity_points)
VALUES
  ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000100',
   'alice@example.com',  'Alice Chen',    '00000000-0000-0000-0000-000000000010', 'IC', 10),
  ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000100',
   'bob@example.com',    'Bob Martinez',  '00000000-0000-0000-0000-000000000010', 'IC', 10),
  ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000100',
   'carol@example.com',  'Carol Nguyen',  '00000000-0000-0000-0000-000000000010', 'IC', 8),
  ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000100',
   'dan@example.com',    'Dan Okafor',    '00000000-0000-0000-0000-000000000010', 'IC', 10)
ON CONFLICT DO NOTHING;

INSERT INTO team_membership (team_id, user_id, role) VALUES
  ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000003', 'MEMBER'),
  ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000004', 'MEMBER'),
  ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000005', 'MEMBER'),
  ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000006', 'MEMBER')
ON CONFLICT DO NOTHING;


-- =========================================================================
-- 1. Work items (tickets) — used across multiple weeks
-- =========================================================================

-- Drop stale V1 status constraint if V3 migration's dynamic drop didn't work
ALTER TABLE work_item DROP CONSTRAINT IF EXISTS work_item_status_check;

INSERT INTO work_item (id, team_id, key, title, description, status, assignee_user_id, reporter_user_id, estimate_points, rcdo_node_id, priority)
VALUES
  -- Enterprise deal tickets
  ('00000000-0000-0000-dddd-000000000001', '00000000-0000-0000-0000-000000000010',
   'ENG-101', 'Build enterprise SSO integration', 'SAML/OIDC integration for enterprise customers', 'DONE',
   '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 8,
   '00000000-0000-0000-cccc-000000000001', 'HIGH'),

  ('00000000-0000-0000-dddd-000000000002', '00000000-0000-0000-0000-000000000010',
   'ENG-102', 'Enterprise billing API', 'Implement usage-based billing for enterprise tier', 'DONE',
   '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 5,
   '00000000-0000-0000-cccc-000000000001', 'HIGH'),

  ('00000000-0000-0000-dddd-000000000003', '00000000-0000-0000-0000-000000000010',
   'ENG-103', 'EMEA data residency compliance', 'Implement EU data residency for EMEA expansion', 'IN_PROGRESS',
   '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', 8,
   '00000000-0000-0000-cccc-000000000002', 'CRITICAL'),

  ('00000000-0000-0000-dddd-000000000004', '00000000-0000-0000-0000-000000000010',
   'ENG-104', 'Onboarding wizard v2', 'Redesign onboarding flow to improve NPS', 'IN_PROGRESS',
   '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000002', 5,
   '00000000-0000-0000-cccc-000000000003', 'MEDIUM'),

  ('00000000-0000-0000-dddd-000000000005', '00000000-0000-0000-0000-000000000010',
   'ENG-105', 'CI/CD pipeline optimization', 'Reduce build time from 20min to <5min', 'DONE',
   '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 5,
   '00000000-0000-0000-cccc-000000000004', 'HIGH'),

  ('00000000-0000-0000-dddd-000000000006', '00000000-0000-0000-0000-000000000010',
   'ENG-106', 'Flaky test quarantine system', 'Auto-quarantine flaky tests to unblock deploys', 'DONE',
   '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 3,
   '00000000-0000-0000-cccc-000000000004', 'MEDIUM'),

  ('00000000-0000-0000-dddd-000000000007', '00000000-0000-0000-0000-000000000010',
   'ENG-107', 'Multi-region failover setup', 'Active-passive failover for EMEA region', 'TODO',
   '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', 8,
   '00000000-0000-0000-cccc-000000000002', 'CRITICAL'),

  ('00000000-0000-0000-dddd-000000000008', '00000000-0000-0000-0000-000000000010',
   'ENG-108', 'Customer health dashboard', 'Real-time dashboard showing churn risk signals', 'IN_PROGRESS',
   '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000002', 5,
   '00000000-0000-0000-cccc-000000000003', 'MEDIUM'),

  ('00000000-0000-0000-dddd-000000000009', '00000000-0000-0000-0000-000000000010',
   'ENG-109', 'API rate limiting per tenant', 'Implement per-tenant rate limits for enterprise', 'DONE',
   '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 3,
   '00000000-0000-0000-cccc-000000000001', 'MEDIUM'),

  ('00000000-0000-0000-dddd-000000000010', '00000000-0000-0000-0000-000000000010',
   'ENG-110', 'Automated regression suite', 'End-to-end regression suite for deploy confidence', 'IN_PROGRESS',
   '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 5,
   '00000000-0000-0000-cccc-000000000004', 'HIGH'),

  ('00000000-0000-0000-dddd-000000000011', '00000000-0000-0000-0000-000000000010',
   'ENG-111', 'Webhook delivery system', 'Reliable webhook delivery with retry for enterprise integrations', 'BLOCKED',
   '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 5,
   '00000000-0000-0000-cccc-000000000001', 'HIGH'),

  ('00000000-0000-0000-dddd-000000000012', '00000000-0000-0000-0000-000000000010',
   'ENG-112', 'GDPR data export endpoint', 'User data export for GDPR compliance in EMEA', 'TODO',
   '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', 3,
   '00000000-0000-0000-cccc-000000000002', 'HIGH')
ON CONFLICT DO NOTHING;


-- =========================================================================
-- Helper: generate plan + commit + snapshot data for 12 weeks × 5 users
--
-- UUID scheme:
--   Plans:   10000000-WWWW-0000-UUUU-000000000000  (WWWW=week#, UUUU=user#)
--   Commits: 20000000-WWWW-CCCC-UUUU-000000000000  (CCCC=commit#)
--   Lock snapshots:    30000000-WWWW-0000-UUUU-000000000000
--   Recon snapshots:   31000000-WWWW-0000-UUUU-000000000000
--   Lock snap commits: 32000000-WWWW-CCCC-UUUU-000000000000
--   Recon snap commits:33000000-WWWW-CCCC-UUUU-000000000000
--   Scope changes:     40000000-WWWW-CCCC-UUUU-000000000000
--   Carry-fwd links:   50000000-WWWW-CCCC-UUUU-000000000000
--   Manager comments:  60000000-WWWW-0000-UUUU-000000000000
--   Notifications:     70000000-WWWW-NNNN-UUUU-000000000000
--   Exceptions:        80000000-WWWW-EEEE-UUUU-000000000000
-- =========================================================================

-- =========================================================================
-- 2. Weekly plans — 12 weeks × 5 users = 60 plans
--    Weeks 1-10 (2026-01-05 → 2026-03-09): RECONCILED
--    Week 11 (2026-03-16): LOCKED
--    Week 12 (2026-03-23): DRAFT (current week)
--
--    User mapping:
--      user-001 (Dev User)      user#=0001
--      user-003 (Alice Chen)    user#=0003
--      user-004 (Bob Martinez)  user#=0004
--      user-005 (Carol Nguyen)  user#=0005
--      user-006 (Dan Okafor)    user#=0006
--    (user-002 = Manager One, does not have own plans)
-- =========================================================================

-- Week 1: 2026-01-05 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0001-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-01-05', 'RECONCILED', '2026-01-10 12:00:00+00', '2026-01-17 10:00:00+00', 10, true),
  ('10000000-0001-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-01-05', 'RECONCILED', '2026-01-10 12:00:00+00', '2026-01-17 10:00:00+00', 10, true),
  ('10000000-0001-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-01-05', 'RECONCILED', '2026-01-10 12:00:00+00', '2026-01-17 10:00:00+00', 10, true),
  ('10000000-0001-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-01-05', 'RECONCILED', '2026-01-10 12:00:00+00', '2026-01-17 10:00:00+00', 8, true),
  ('10000000-0001-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-01-05', 'RECONCILED', '2026-01-10 12:00:00+00', '2026-01-17 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 2: 2026-01-12 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0002-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-01-12', 'RECONCILED', '2026-01-17 12:00:00+00', '2026-01-24 10:00:00+00', 10, true),
  ('10000000-0002-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-01-12', 'RECONCILED', '2026-01-17 12:00:00+00', '2026-01-24 10:00:00+00', 10, true),
  ('10000000-0002-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-01-12', 'RECONCILED', '2026-01-17 12:00:00+00', '2026-01-24 10:00:00+00', 10, true),
  ('10000000-0002-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-01-12', 'RECONCILED', '2026-01-17 12:00:00+00', '2026-01-24 10:00:00+00', 8, true),
  ('10000000-0002-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-01-12', 'RECONCILED', '2026-01-17 12:00:00+00', '2026-01-24 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 3: 2026-01-19 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0003-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-01-19', 'RECONCILED', '2026-01-24 12:00:00+00', '2026-01-31 10:00:00+00', 10, true),
  ('10000000-0003-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-01-19', 'RECONCILED', '2026-01-24 12:00:00+00', '2026-01-31 10:00:00+00', 10, true),
  ('10000000-0003-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-01-19', 'RECONCILED', '2026-01-24 12:00:00+00', '2026-01-31 10:00:00+00', 10, true),
  ('10000000-0003-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-01-19', 'RECONCILED', '2026-01-24 12:00:00+00', '2026-01-31 10:00:00+00', 8, false),
  ('10000000-0003-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-01-19', 'RECONCILED', '2026-01-24 12:00:00+00', '2026-01-31 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 4: 2026-01-26 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0004-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-01-26', 'RECONCILED', '2026-01-31 12:00:00+00', '2026-02-07 10:00:00+00', 10, true),
  ('10000000-0004-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-01-26', 'RECONCILED', '2026-01-31 12:00:00+00', '2026-02-07 10:00:00+00', 10, true),
  ('10000000-0004-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-01-26', 'RECONCILED', '2026-01-31 12:00:00+00', '2026-02-07 10:00:00+00', 10, true),
  ('10000000-0004-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-01-26', 'RECONCILED', '2026-01-31 12:00:00+00', '2026-02-07 10:00:00+00', 8, true),
  ('10000000-0004-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-01-26', 'RECONCILED', '2026-01-31 12:00:00+00', '2026-02-07 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 5: 2026-02-02 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0005-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-02-02', 'RECONCILED', '2026-02-07 12:00:00+00', '2026-02-14 10:00:00+00', 10, true),
  ('10000000-0005-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-02-02', 'RECONCILED', '2026-02-07 12:00:00+00', '2026-02-14 10:00:00+00', 10, true),
  ('10000000-0005-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-02-02', 'RECONCILED', '2026-02-07 12:00:00+00', '2026-02-14 10:00:00+00', 10, true),
  ('10000000-0005-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-02-02', 'RECONCILED', '2026-02-07 12:00:00+00', '2026-02-14 10:00:00+00', 8, true),
  ('10000000-0005-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-02-02', 'RECONCILED', '2026-02-07 12:00:00+00', '2026-02-14 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 6: 2026-02-09 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0006-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-02-09', 'RECONCILED', '2026-02-14 12:00:00+00', '2026-02-21 10:00:00+00', 10, true),
  ('10000000-0006-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-02-09', 'RECONCILED', '2026-02-14 12:00:00+00', '2026-02-21 10:00:00+00', 10, true),
  ('10000000-0006-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-02-09', 'RECONCILED', '2026-02-14 12:00:00+00', '2026-02-21 10:00:00+00', 10, true),
  ('10000000-0006-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-02-09', 'RECONCILED', '2026-02-14 12:00:00+00', '2026-02-21 10:00:00+00', 8, true),
  ('10000000-0006-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-02-09', 'RECONCILED', '2026-02-14 12:00:00+00', '2026-02-21 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 7: 2026-02-16 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0007-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-02-16', 'RECONCILED', '2026-02-21 12:00:00+00', '2026-02-28 10:00:00+00', 10, true),
  ('10000000-0007-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-02-16', 'RECONCILED', '2026-02-21 12:00:00+00', '2026-02-28 10:00:00+00', 10, true),
  ('10000000-0007-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-02-16', 'RECONCILED', '2026-02-21 12:00:00+00', '2026-02-28 10:00:00+00', 10, true),
  ('10000000-0007-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-02-16', 'RECONCILED', '2026-02-21 12:00:00+00', '2026-02-28 10:00:00+00', 8, true),
  ('10000000-0007-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-02-16', 'RECONCILED', '2026-02-21 12:00:00+00', '2026-02-28 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 8: 2026-02-23 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0008-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-02-23', 'RECONCILED', '2026-02-28 12:00:00+00', '2026-03-07 10:00:00+00', 10, true),
  ('10000000-0008-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-02-23', 'RECONCILED', '2026-02-28 12:00:00+00', '2026-03-07 10:00:00+00', 10, true),
  ('10000000-0008-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-02-23', 'RECONCILED', '2026-02-28 12:00:00+00', '2026-03-07 10:00:00+00', 10, true),
  ('10000000-0008-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-02-23', 'RECONCILED', '2026-02-28 12:00:00+00', '2026-03-07 10:00:00+00', 8, true),
  ('10000000-0008-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-02-23', 'RECONCILED', '2026-02-28 12:00:00+00', '2026-03-07 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 9: 2026-03-02 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0009-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-03-02', 'RECONCILED', '2026-03-07 12:00:00+00', '2026-03-14 10:00:00+00', 10, true),
  ('10000000-0009-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-03-02', 'RECONCILED', '2026-03-07 12:00:00+00', '2026-03-14 10:00:00+00', 10, true),
  ('10000000-0009-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-03-02', 'RECONCILED', '2026-03-07 12:00:00+00', '2026-03-14 10:00:00+00', 10, true),
  ('10000000-0009-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-03-02', 'RECONCILED', '2026-03-07 12:00:00+00', '2026-03-14 10:00:00+00', 8, true),
  ('10000000-0009-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-03-02', 'RECONCILED', '2026-03-07 12:00:00+00', '2026-03-14 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 10: 2026-03-09 (RECONCILED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0010-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-03-09', 'RECONCILED', '2026-03-14 12:00:00+00', '2026-03-21 10:00:00+00', 10, true),
  ('10000000-0010-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-03-09', 'RECONCILED', '2026-03-14 12:00:00+00', '2026-03-21 10:00:00+00', 10, true),
  ('10000000-0010-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-03-09', 'RECONCILED', '2026-03-14 12:00:00+00', '2026-03-21 10:00:00+00', 10, true),
  ('10000000-0010-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-03-09', 'RECONCILED', '2026-03-14 12:00:00+00', '2026-03-21 10:00:00+00', 8, true),
  ('10000000-0010-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-03-09', 'RECONCILED', '2026-03-14 12:00:00+00', '2026-03-21 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 11: 2026-03-16 (LOCKED)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0011-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-03-16', 'LOCKED', '2026-03-21 12:00:00+00', '2026-03-28 10:00:00+00', 10, true),
  ('10000000-0011-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-03-16', 'LOCKED', '2026-03-21 12:00:00+00', '2026-03-28 10:00:00+00', 10, true),
  ('10000000-0011-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-03-16', 'LOCKED', '2026-03-21 12:00:00+00', '2026-03-28 10:00:00+00', 10, true),
  ('10000000-0011-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-03-16', 'LOCKED', '2026-03-21 12:00:00+00', '2026-03-28 10:00:00+00', 8, true),
  ('10000000-0011-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-03-16', 'LOCKED', '2026-03-21 12:00:00+00', '2026-03-28 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;

-- Week 12: 2026-03-23 (DRAFT — current week)
INSERT INTO weekly_plan (id, owner_user_id, team_id, week_start_date, state, lock_deadline, reconcile_deadline, capacity_budget_points, is_compliant) VALUES
  ('10000000-0012-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-03-23', 'DRAFT', '2026-03-28 12:00:00+00', '2026-04-04 10:00:00+00', 10, true),
  ('10000000-0012-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', '2026-03-23', 'DRAFT', '2026-03-28 12:00:00+00', '2026-04-04 10:00:00+00', 10, true),
  ('10000000-0012-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', '2026-03-23', 'DRAFT', '2026-03-28 12:00:00+00', '2026-04-04 10:00:00+00', 10, true),
  ('10000000-0012-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000010', '2026-03-23', 'DRAFT', '2026-03-28 12:00:00+00', '2026-04-04 10:00:00+00', 8, true),
  ('10000000-0012-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '2026-03-23', 'DRAFT', '2026-03-28 12:00:00+00', '2026-04-04 10:00:00+00', 10, true)
ON CONFLICT DO NOTHING;


-- =========================================================================
-- 3. Weekly commits — representative sample across all weeks/users
--    Each user gets 5-7 commits per week with varied chess pieces & outcomes
-- =========================================================================

-- ----- Week 1 (2026-01-05) — all RECONCILED -----

-- Dev User (user-001): CI/CD focused
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, work_item_id, estimate_points, success_criteria, outcome, outcome_notes) VALUES
  ('20000000-0001-0001-0001-000000000000', '10000000-0001-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Set up CI/CD pipeline foundation', 'Establish GitHub Actions pipelines for all services', 'KING', 1, '00000000-0000-0000-cccc-000000000004', '00000000-0000-0000-dddd-000000000005', 5, 'All services have green CI pipelines', 'ACHIEVED', 'Completed ahead of schedule'),
  ('20000000-0001-0002-0001-000000000000', '10000000-0001-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Docker build caching', 'Implement layer caching for Docker builds', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', NULL, 3, 'Build time reduced by 50%', 'ACHIEVED', 'Reduced from 12min to 5min'),
  ('20000000-0001-0003-0001-000000000000', '10000000-0001-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Sprint retro prep', 'Prepare sprint retrospective materials', 'PAWN', 3, NULL, NULL, 1, 'Retro deck ready', 'ACHIEVED', NULL),
  ('20000000-0001-0004-0001-000000000000', '10000000-0001-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Code review backlog', 'Clear 8 pending PRs', 'KNIGHT', 4, NULL, NULL, 1, 'All PRs reviewed', 'PARTIALLY_ACHIEVED', 'Reviewed 6 of 8')
ON CONFLICT DO NOTHING;

-- Alice Chen (user-003): SSO work
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, work_item_id, estimate_points, success_criteria, outcome, outcome_notes) VALUES
  ('20000000-0001-0001-0003-000000000000', '10000000-0001-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO research and design doc', 'Evaluate SAML vs OIDC for enterprise SSO', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', '00000000-0000-0000-dddd-000000000001', 5, 'Design doc approved by team lead', 'ACHIEVED', 'Team chose OIDC-first approach'),
  ('20000000-0001-0002-0003-000000000000', '10000000-0001-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'OIDC provider integration spike', 'Prototype OIDC flow with Okta sandbox', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', '00000000-0000-0000-dddd-000000000001', 3, 'Working prototype with Okta', 'ACHIEVED', NULL),
  ('20000000-0001-0003-0003-000000000000', '10000000-0001-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Weekly 1:1 with manager', 'Discuss Q1 goals alignment', 'PAWN', 3, NULL, NULL, 1, 'Action items documented', 'ACHIEVED', NULL)
ON CONFLICT DO NOTHING;

-- Bob Martinez (user-004): Billing work
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, work_item_id, estimate_points, success_criteria, outcome, outcome_notes) VALUES
  ('20000000-0001-0001-0004-000000000000', '10000000-0001-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Billing data model design', 'Design usage-based billing schema', 'KING', 1, '00000000-0000-0000-cccc-000000000001', '00000000-0000-0000-dddd-000000000002', 5, 'Schema supports per-seat and usage billing', 'ACHIEVED', NULL),
  ('20000000-0001-0002-0004-000000000000', '10000000-0001-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Stripe integration research', 'Evaluate Stripe billing APIs', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', NULL, 3, 'Recommendation doc written', 'ACHIEVED', NULL),
  ('20000000-0001-0003-0004-000000000000', '10000000-0001-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Fix API pagination bug', 'Cursor pagination returning duplicates', 'KNIGHT', 3, NULL, NULL, 2, 'Bug fixed, test added', 'ACHIEVED', 'Root cause was missing ORDER BY')
ON CONFLICT DO NOTHING;

-- Carol Nguyen (user-005): EMEA compliance
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, work_item_id, estimate_points, success_criteria, outcome, outcome_notes) VALUES
  ('20000000-0001-0001-0005-000000000000', '10000000-0001-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'GDPR requirements analysis', 'Map data flows and identify compliance gaps', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000002', '00000000-0000-0000-dddd-000000000003', 5, 'Gap analysis document complete', 'ACHIEVED', 'Found 12 gaps to address'),
  ('20000000-0001-0002-0005-000000000000', '10000000-0001-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Data classification tagging', 'Add PII tags to user data columns', 'BISHOP', 2, '00000000-0000-0000-cccc-000000000002', NULL, 2, 'All PII columns tagged', 'PARTIALLY_ACHIEVED', 'Tagged 80% of columns, 3 services remain'),
  ('20000000-0001-0003-0005-000000000000', '10000000-0001-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Team standup notes', 'Daily standup facilitation', 'PAWN', 3, NULL, NULL, 1, 'Notes published daily', 'ACHIEVED', NULL)
ON CONFLICT DO NOTHING;

-- Dan Okafor (user-006): Onboarding
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, work_item_id, estimate_points, success_criteria, outcome, outcome_notes) VALUES
  ('20000000-0001-0001-0006-000000000000', '10000000-0001-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding UX audit', 'Audit current onboarding flow and identify drop-off points', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000003', '00000000-0000-0000-dddd-000000000004', 5, 'Audit report with recommendations', 'ACHIEVED', 'Identified 3 major drop-off points'),
  ('20000000-0001-0002-0006-000000000000', '10000000-0001-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Wireframe new onboarding wizard', 'Design wireframes for improved flow', 'ROOK', 2, '00000000-0000-0000-cccc-000000000003', '00000000-0000-0000-dddd-000000000004', 3, 'Wireframes reviewed by design', 'NOT_ACHIEVED', 'Blocked waiting for design system update'),
  ('20000000-0001-0003-0006-000000000000', '10000000-0001-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Fix tooltip accessibility', 'Screen reader not reading tooltip content', 'PAWN', 3, NULL, NULL, 1, 'Tooltip passes WCAG 2.1', 'ACHIEVED', NULL)
ON CONFLICT DO NOTHING;

-- ----- Week 2 (2026-01-12) — RECONCILED -----

INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, work_item_id, estimate_points, success_criteria, outcome, outcome_notes) VALUES
  -- Dev User: continuing CI/CD
  ('20000000-0002-0001-0001-000000000000', '10000000-0002-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Parallel test execution', 'Enable parallel test runs in CI', 'KING', 1, '00000000-0000-0000-cccc-000000000004', '00000000-0000-0000-dddd-000000000005', 5, 'Test suite runs in <5min', 'ACHIEVED', 'Down to 4min 30sec'),
  ('20000000-0002-0002-0001-000000000000', '10000000-0002-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Flaky test identification', 'Identify and tag flaky tests', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', '00000000-0000-0000-dddd-000000000006', 3, 'All flaky tests identified and tagged', 'ACHIEVED', 'Found 23 flaky tests'),
  ('20000000-0002-0003-0001-000000000000', '10000000-0002-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Remaining PR reviews', 'Clear remaining 2 PRs from last week', 'PAWN', 3, NULL, NULL, 1, 'All PRs reviewed', 'ACHIEVED', NULL),
  -- Alice: SSO implementation
  ('20000000-0002-0001-0003-000000000000', '10000000-0002-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'OIDC token validation', 'Implement JWT validation middleware', 'KING', 1, '00000000-0000-0000-cccc-000000000001', '00000000-0000-0000-dddd-000000000001', 5, 'All token claims validated correctly', 'ACHIEVED', NULL),
  ('20000000-0002-0002-0003-000000000000', '10000000-0002-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO session management', 'Handle SSO session lifecycle', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', NULL, 3, 'Sessions persist across page reloads', 'ACHIEVED', NULL),
  ('20000000-0002-0003-0003-000000000000', '10000000-0002-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'On-call rotation', 'Primary on-call this week', 'KNIGHT', 3, NULL, NULL, 2, 'All incidents responded to <15min', 'ACHIEVED', 'Zero P1 incidents'),
  -- Bob: Billing implementation
  ('20000000-0002-0001-0004-000000000000', '10000000-0002-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Stripe billing integration', 'Implement Stripe subscription creation', 'KING', 1, '00000000-0000-0000-cccc-000000000001', '00000000-0000-0000-dddd-000000000002', 5, 'Subscriptions can be created/cancelled', 'PARTIALLY_ACHIEVED', 'Creation works, cancellation needs webhook handling'),
  ('20000000-0002-0002-0004-000000000000', '10000000-0002-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Usage metering collector', 'Build event collector for API usage tracking', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', NULL, 3, 'Events collected and stored reliably', 'ACHIEVED', NULL),
  ('20000000-0002-0003-0004-000000000000', '10000000-0002-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'API rate limiter design', 'Design per-tenant rate limiting approach', 'BISHOP', 3, '00000000-0000-0000-cccc-000000000001', '00000000-0000-0000-dddd-000000000009', 2, 'Design doc approved', 'ACHIEVED', NULL),
  -- Carol: EMEA compliance continued
  ('20000000-0002-0001-0005-000000000000', '10000000-0002-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Finish data classification tagging', 'Complete remaining 3 services', 'ROOK', 1, '00000000-0000-0000-cccc-000000000002', NULL, 3, 'All services tagged', 'ACHIEVED', 'All 15 services now tagged'),
  ('20000000-0002-0002-0005-000000000000', '10000000-0002-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Data residency architecture', 'Design multi-region data routing', 'QUEEN', 2, '00000000-0000-0000-cccc-000000000002', '00000000-0000-0000-dddd-000000000003', 5, 'Architecture doc reviewed', 'ACHIEVED', 'Approved by security team'),
  -- Dan: Onboarding continued (carry forward wireframes from wk1)
  ('20000000-0002-0001-0006-000000000000', '10000000-0002-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Wireframe new onboarding wizard (carry-forward)', 'Carry forward from last week', 'ROOK', 1, '00000000-0000-0000-cccc-000000000003', '00000000-0000-0000-dddd-000000000004', 3, 'Wireframes approved', 'ACHIEVED', 'Design system was updated mid-week'),
  ('20000000-0002-0002-0006-000000000000', '10000000-0002-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Prototype onboarding step 1', 'Build interactive prototype of first onboarding step', 'QUEEN', 2, '00000000-0000-0000-cccc-000000000003', '00000000-0000-0000-dddd-000000000004', 5, 'Clickable prototype in Figma', 'ACHIEVED', NULL),
  ('20000000-0002-0003-0006-000000000000', '10000000-0002-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Customer interview notes synthesis', 'Synthesize 5 customer interview notes', 'BISHOP', 3, '00000000-0000-0000-cccc-000000000003', NULL, 2, 'Key themes documented', 'ACHIEVED', 'Found 4 recurring pain points')
ON CONFLICT DO NOTHING;

-- Set carry_forward_streak on Dan's wk2 wireframe carry-forward
UPDATE weekly_commit SET carry_forward_streak = 1 WHERE id = '20000000-0002-0001-0006-000000000000';

-- ----- Weeks 3-10: generating varied commits for each user -----

-- Week 3 (2026-01-19)
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, success_criteria, outcome, outcome_notes, carry_forward_streak) VALUES
  ('20000000-0003-0001-0001-000000000000', '10000000-0003-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Deploy staging environment', 'Set up staging with production parity', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 'Staging env deployed and verified', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0002-0001-000000000000', '10000000-0003-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Automated rollback mechanism', 'Build automated rollback on deploy failure', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', 3, 'Rollback triggers on health check failure', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0003-0001-000000000000', '10000000-0003-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Onboard new team member', 'Pair program and code walkthrough', 'KNIGHT', 3, NULL, 2, 'New member can submit PR independently', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0001-0003-000000000000', '10000000-0003-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO multi-provider support', 'Support Okta, Azure AD, Google Workspace', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'All 3 providers working in staging', 'PARTIALLY_ACHIEVED', 'Okta and Google done, Azure AD pending', 0),
  ('20000000-0003-0002-0003-000000000000', '10000000-0003-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO admin configuration UI', 'Build admin panel for SSO config', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'Admin can enable/configure SSO', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0003-0003-000000000000', '10000000-0003-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Security review prep', 'Prepare materials for SSO security review', 'PAWN', 3, NULL, 1, 'Review materials submitted', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0001-0004-000000000000', '10000000-0003-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Stripe webhook handler', 'Handle subscription lifecycle webhooks', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'All webhook events handled idempotently', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0002-0004-000000000000', '10000000-0003-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Invoice generation', 'Generate monthly invoices from usage data', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'Invoices match usage records', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0003-0004-000000000000', '10000000-0003-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Rate limiter implementation', 'Implement Redis-backed rate limiting', 'ROOK', 3, '00000000-0000-0000-cccc-000000000001', 2, 'Rate limits enforced per-tenant', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0001-0005-000000000000', '10000000-0003-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'EU region database setup', 'Provision and configure EU PostgreSQL cluster', 'KING', 1, '00000000-0000-0000-cccc-000000000002', 5, 'EU database cluster operational', 'NOT_ACHIEVED', 'Blocked by procurement — AWS marketplace approval pending', 0),
  ('20000000-0003-0002-0005-000000000000', '10000000-0003-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Data routing middleware', 'Route requests to correct region based on tenant', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 'Routing logic tested', 'ACHIEVED', 'Unit tests passing', 0),
  ('20000000-0003-0001-0006-000000000000', '10000000-0003-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding wizard step 1 implementation', 'Build account setup step', 'KING', 1, '00000000-0000-0000-cccc-000000000003', 5, 'Step 1 deployed to staging', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0002-0006-000000000000', '10000000-0003-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Progress indicator component', 'Build reusable multi-step progress bar', 'BISHOP', 2, '00000000-0000-0000-cccc-000000000003', 3, 'Component works for 2-8 steps', 'ACHIEVED', NULL, 0),
  ('20000000-0003-0003-0006-000000000000', '10000000-0003-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Customer health metrics API', 'API to expose churn risk signals', 'ROOK', 3, '00000000-0000-0000-cccc-000000000003', 2, 'API returns health score', 'PARTIALLY_ACHIEVED', 'API works but needs caching', 0)
ON CONFLICT DO NOTHING;

-- Week 4-10: Abbreviated but representative commits
-- (Using a more compact format for the remaining weeks)

-- Week 4 (2026-01-26)
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, success_criteria, outcome, outcome_notes, carry_forward_streak) VALUES
  ('20000000-0004-0001-0001-000000000000', '10000000-0004-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Feature flag infrastructure', 'Set up LaunchDarkly integration', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 'Feature flags operational', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0002-0001-000000000000', '10000000-0004-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Canary deployment pipeline', 'Implement canary releases', 'QUEEN', 2, '00000000-0000-0000-cccc-000000000004', 3, 'Canary deploys to 10% traffic', 'PARTIALLY_ACHIEVED', 'Works but needs monitoring', 0),
  ('20000000-0004-0003-0001-000000000000', '10000000-0004-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Tech debt: upgrade Spring Boot', 'Upgrade to latest Spring Boot', 'KNIGHT', 3, NULL, 2, 'All tests pass on new version', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0001-0003-000000000000', '10000000-0004-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Azure AD SSO integration', 'Complete Azure AD support (carry-forward)', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'Azure AD SSO working', 'ACHIEVED', 'SCIM provisioning also added', 1),
  ('20000000-0004-0002-0003-000000000000', '10000000-0004-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO error handling', 'Graceful error handling for SSO failures', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'User-friendly error messages', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0003-0003-000000000000', '10000000-0004-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Document SSO setup guide', 'Write customer-facing SSO docs', 'PAWN', 3, NULL, 1, 'Docs reviewed and published', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0001-0004-000000000000', '10000000-0004-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Billing dashboard frontend', 'Build usage dashboard for customers', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 5, 'Dashboard shows usage and cost', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0002-0004-000000000000', '10000000-0004-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Proration logic', 'Handle mid-cycle plan changes', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'Proration calculated correctly', 'NOT_ACHIEVED', 'Edge cases with timezone boundaries', 0),
  ('20000000-0004-0003-0004-000000000000', '10000000-0004-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Unit tests for billing module', 'Increase coverage to 90%', 'PAWN', 3, NULL, 1, '90% coverage achieved', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0001-0005-000000000000', '10000000-0004-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'EU region database setup (carry-forward)', 'Procurement approved, provision cluster', 'KING', 1, '00000000-0000-0000-cccc-000000000002', 5, 'EU database operational', 'ACHIEVED', 'Finally provisioned after procurement delay', 1),
  ('20000000-0004-0002-0005-000000000000', '10000000-0004-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Data migration tooling', 'Build tenant data migration scripts', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 'Migration scripts tested', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0001-0006-000000000000', '10000000-0004-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding wizard step 2', 'Build team setup step', 'KING', 1, '00000000-0000-0000-cccc-000000000003', 5, 'Step 2 deployed', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0002-0006-000000000000', '10000000-0004-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Health dashboard wireframes', 'Design customer health dashboard', 'ROOK', 2, '00000000-0000-0000-cccc-000000000003', 3, 'Wireframes approved', 'ACHIEVED', NULL, 0),
  ('20000000-0004-0003-0006-000000000000', '10000000-0004-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Health metrics caching', 'Add Redis caching for health API', 'KNIGHT', 3, '00000000-0000-0000-cccc-000000000003', 2, 'API response <100ms', 'ACHIEVED', NULL, 0)
ON CONFLICT DO NOTHING;

-- Weeks 5-10: More concise — 3 commits per user per week to keep migration manageable
-- Week 5 (2026-02-02)
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, outcome, outcome_notes, carry_forward_streak) VALUES
  ('20000000-0005-0001-0001-000000000000', '10000000-0005-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Deploy frequency dashboard', 'Track deploy frequency metrics', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000004', 5, 'ACHIEVED', NULL, 0),
  ('20000000-0005-0002-0001-000000000000', '10000000-0005-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Quarantine flaky tests', 'Auto-quarantine system', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', 3, 'ACHIEVED', 'System auto-quarantines after 3 failures', 0),
  ('20000000-0005-0003-0001-000000000000', '10000000-0005-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Incident runbook updates', 'Update runbooks for new services', 'PAWN', 3, NULL, 1, 'ACHIEVED', NULL, 0),
  ('20000000-0005-0001-0003-000000000000', '10000000-0005-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO load testing', 'Load test SSO with 10K concurrent users', 'ROOK', 1, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', 'Handles 15K concurrent', 0),
  ('20000000-0005-0002-0003-000000000000', '10000000-0005-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO audit logging', 'Log all SSO auth events', 'BISHOP', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', NULL, 0),
  ('20000000-0005-0003-0003-000000000000', '10000000-0005-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Cross-team SSO demo', 'Demo SSO to product and sales', 'PAWN', 3, NULL, 1, 'ACHIEVED', 'Great reception from sales', 0),
  ('20000000-0005-0001-0004-000000000000', '10000000-0005-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Fix proration edge cases', 'Carry forward proration fix', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', 'All timezone edge cases handled', 1),
  ('20000000-0005-0002-0004-000000000000', '10000000-0005-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Billing email notifications', 'Send invoice and payment emails', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', NULL, 0),
  ('20000000-0005-0003-0004-000000000000', '10000000-0005-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Webhook retry mechanism', 'Build exponential backoff for webhooks', 'BISHOP', 3, '00000000-0000-0000-cccc-000000000001', 2, 'PARTIALLY_ACHIEVED', 'Retry works, dead letter queue pending', 0),
  ('20000000-0005-0001-0005-000000000000', '10000000-0005-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Tenant data migration dry run', 'Test migration with production snapshot', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000002', 5, 'ACHIEVED', 'Migration completed in 4 hours', 0),
  ('20000000-0005-0002-0005-000000000000', '10000000-0005-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'GDPR data export endpoint', 'Build user data export API', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 'PARTIALLY_ACHIEVED', 'API works, PDF format pending', 0),
  ('20000000-0005-0001-0006-000000000000', '10000000-0005-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding wizard step 3', 'Build integration setup step', 'KING', 1, '00000000-0000-0000-cccc-000000000003', 5, 'ACHIEVED', NULL, 0),
  ('20000000-0005-0002-0006-000000000000', '10000000-0005-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Health dashboard implementation', 'Build customer health dashboard', 'QUEEN', 2, '00000000-0000-0000-cccc-000000000003', 3, 'NOT_ACHIEVED', 'Blocked by missing API endpoints', 0),
  ('20000000-0005-0003-0006-000000000000', '10000000-0005-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding A/B test setup', 'Configure A/B testing for onboarding', 'BISHOP', 3, '00000000-0000-0000-cccc-000000000003', 2, 'ACHIEVED', NULL, 0)
ON CONFLICT DO NOTHING;

-- Week 6 (2026-02-09)
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, outcome, outcome_notes, carry_forward_streak) VALUES
  ('20000000-0006-0001-0001-000000000000', '10000000-0006-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Observability: distributed tracing', 'Set up OpenTelemetry tracing', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 'ACHIEVED', NULL, 0),
  ('20000000-0006-0002-0001-000000000000', '10000000-0006-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Alert rules for deploy failures', 'PagerDuty alerts on failed deploys', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', 3, 'ACHIEVED', NULL, 0),
  ('20000000-0006-0003-0001-000000000000', '10000000-0006-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Tech talk: CI/CD best practices', 'Present at engineering all-hands', 'PAWN', 3, NULL, 1, 'ACHIEVED', 'Great feedback from team', 0),
  ('20000000-0006-0001-0003-000000000000', '10000000-0006-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO SCIM provisioning', 'Auto-provision users from IdP', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', NULL, 0),
  ('20000000-0006-0002-0003-000000000000', '10000000-0006-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO migration tooling', 'Help existing customers migrate to SSO', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', NULL, 0),
  ('20000000-0006-0001-0004-000000000000', '10000000-0006-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Webhook dead letter queue', 'Complete DLQ for failed webhooks', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', NULL, 1),
  ('20000000-0006-0002-0004-000000000000', '10000000-0006-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Enterprise plan pricing page', 'Build enterprise pricing comparison', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', NULL, 0),
  ('20000000-0006-0001-0005-000000000000', '10000000-0006-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'EU data migration execution', 'Migrate first 5 EU tenants', 'KING', 1, '00000000-0000-0000-cccc-000000000002', 5, 'PARTIALLY_ACHIEVED', 'Migrated 3 of 5 — latency issues on 2', 0),
  ('20000000-0006-0002-0005-000000000000', '10000000-0006-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'GDPR data export PDF format', 'Add PDF export option', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 'ACHIEVED', NULL, 1),
  ('20000000-0006-0001-0006-000000000000', '10000000-0006-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Health dashboard (carry-forward)', 'Complete blocked dashboard', 'KING', 1, '00000000-0000-0000-cccc-000000000003', 5, 'ACHIEVED', 'API endpoints now available', 1),
  ('20000000-0006-0002-0006-000000000000', '10000000-0006-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding completion analytics', 'Track funnel completion rates', 'ROOK', 2, '00000000-0000-0000-cccc-000000000003', 3, 'ACHIEVED', NULL, 0),
  ('20000000-0006-0003-0006-000000000000', '10000000-0006-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Fix mobile responsive issues', 'Onboarding wizard not rendering on mobile', 'KNIGHT', 3, NULL, 2, 'ACHIEVED', NULL, 0)
ON CONFLICT DO NOTHING;

-- Week 7 (2026-02-16)
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, outcome, carry_forward_streak) VALUES
  ('20000000-0007-0001-0001-000000000000', '10000000-0007-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Automated E2E test suite', 'Build Playwright E2E tests for critical paths', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 'PARTIALLY_ACHIEVED', 0),
  ('20000000-0007-0002-0001-000000000000', '10000000-0007-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Database query optimization', 'Optimize slow queries (>500ms)', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', 3, 'ACHIEVED', 0),
  ('20000000-0007-0003-0001-000000000000', '10000000-0007-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Mentoring junior dev', 'Pair programming sessions', 'PAWN', 3, NULL, 1, 'ACHIEVED', 0),
  ('20000000-0007-0001-0003-000000000000', '10000000-0007-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO production rollout', 'Roll out SSO to first 10 enterprise customers', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', 0),
  ('20000000-0007-0002-0003-000000000000', '10000000-0007-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO monitoring dashboard', 'Build Grafana dashboard for SSO health', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', 0),
  ('20000000-0007-0001-0004-000000000000', '10000000-0007-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Revenue recognition system', 'Build ASC 606 compliant rev rec', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 5, 'NOT_ACHIEVED', 0),
  ('20000000-0007-0002-0004-000000000000', '10000000-0007-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Billing API documentation', 'Write API docs for partners', 'BISHOP', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', 0),
  ('20000000-0007-0001-0005-000000000000', '10000000-0007-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Complete EU tenant migration', 'Migrate remaining 2 EU tenants', 'KING', 1, '00000000-0000-0000-cccc-000000000002', 5, 'ACHIEVED', 1),
  ('20000000-0007-0002-0005-000000000000', '10000000-0007-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Multi-region health checks', 'Cross-region health monitoring', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 'ACHIEVED', 0),
  ('20000000-0007-0001-0006-000000000000', '10000000-0007-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding NPS survey integration', 'Trigger NPS survey at onboarding end', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000003', 5, 'ACHIEVED', 0),
  ('20000000-0007-0002-0006-000000000000', '10000000-0007-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'In-app guided tours', 'Build feature discovery tooltips', 'ROOK', 2, '00000000-0000-0000-cccc-000000000003', 3, 'ACHIEVED', 0),
  ('20000000-0007-0003-0006-000000000000', '10000000-0007-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Accessibility audit fixes', 'Fix remaining WCAG issues', 'KNIGHT', 3, NULL, 2, 'ACHIEVED', 0)
ON CONFLICT DO NOTHING;

-- Week 8 (2026-02-23)
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, outcome, carry_forward_streak) VALUES
  ('20000000-0008-0001-0001-000000000000', '10000000-0008-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Complete E2E test suite', 'Finish Playwright tests (carry-forward)', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 'ACHIEVED', 1),
  ('20000000-0008-0002-0001-000000000000', '10000000-0008-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Infrastructure cost optimization', 'Right-size staging resources', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', 3, 'ACHIEVED', 0),
  ('20000000-0008-0003-0001-000000000000', '10000000-0008-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'On-call documentation update', 'Update runbooks', 'PAWN', 3, NULL, 1, 'ACHIEVED', 0),
  ('20000000-0008-0001-0003-000000000000', '10000000-0008-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Enterprise customer onboarding', 'Onboard Acme Corp to SSO', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', 0),
  ('20000000-0008-0002-0003-000000000000', '10000000-0008-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO session analytics', 'Track SSO usage patterns', 'BISHOP', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', 0),
  ('20000000-0008-0001-0004-000000000000', '10000000-0008-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Revenue recognition (carry-forward)', 'Complete rev rec implementation', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', 1),
  ('20000000-0008-0002-0004-000000000000', '10000000-0008-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Billing admin panel', 'Internal admin for billing support', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', 0),
  ('20000000-0008-0001-0005-000000000000', '10000000-0008-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'EU region performance tuning', 'Optimize query latency for EU', 'KING', 1, '00000000-0000-0000-cccc-000000000002', 5, 'ACHIEVED', 0),
  ('20000000-0008-0002-0005-000000000000', '10000000-0008-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Compliance audit prep', 'Prepare SOC2 evidence collection', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 'ACHIEVED', 0),
  ('20000000-0008-0001-0006-000000000000', '10000000-0008-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'A/B test analysis', 'Analyze onboarding A/B test results', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000003', 5, 'ACHIEVED', 0),
  ('20000000-0008-0002-0006-000000000000', '10000000-0008-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding wizard polish', 'UI polish based on A/B results', 'ROOK', 2, '00000000-0000-0000-cccc-000000000003', 3, 'ACHIEVED', 0),
  ('20000000-0008-0003-0006-000000000000', '10000000-0008-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Health dashboard alerts', 'Add alerting for churn risk spikes', 'KNIGHT', 3, '00000000-0000-0000-cccc-000000000003', 2, 'PARTIALLY_ACHIEVED', 0)
ON CONFLICT DO NOTHING;

-- Weeks 9-10: Final two reconciled weeks
-- Week 9 (2026-03-02)
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, outcome, carry_forward_streak) VALUES
  ('20000000-0009-0001-0001-000000000000', '10000000-0009-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Zero-downtime deploy process', 'Blue-green deployment for all services', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 'ACHIEVED', 0),
  ('20000000-0009-0002-0001-000000000000', '10000000-0009-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'SLO dashboard', 'Track SLI/SLO across services', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', 3, 'ACHIEVED', 0),
  ('20000000-0009-0003-0001-000000000000', '10000000-0009-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Interview candidate (senior)', 'Technical phone screen', 'PAWN', 3, NULL, 1, 'ACHIEVED', 0),
  ('20000000-0009-0001-0003-000000000000', '10000000-0009-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Enterprise SSO wave 2', 'Onboard 5 more enterprise accounts', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'PARTIALLY_ACHIEVED', 0),
  ('20000000-0009-0002-0003-000000000000', '10000000-0009-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Role-based access for SSO', 'Map IdP groups to app roles', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', 0),
  ('20000000-0009-0001-0004-000000000000', '10000000-0009-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Multi-currency support', 'Support EUR, GBP billing', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', 0),
  ('20000000-0009-0002-0004-000000000000', '10000000-0009-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Tax calculation engine', 'Integrate Avalara for tax', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'NOT_ACHIEVED', 0),
  ('20000000-0009-0001-0005-000000000000', '10000000-0009-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Failover testing', 'Test EU region failover procedure', 'KING', 1, '00000000-0000-0000-cccc-000000000002', 5, 'ACHIEVED', 0),
  ('20000000-0009-0002-0005-000000000000', '10000000-0009-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Data retention policies', 'Implement automated data purge', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 'ACHIEVED', 0),
  ('20000000-0009-0001-0006-000000000000', '10000000-0009-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding NPS report', 'First month NPS analysis', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000003', 5, 'ACHIEVED', 0),
  ('20000000-0009-0002-0006-000000000000', '10000000-0009-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Help center content', 'Write 10 help articles', 'BISHOP', 2, '00000000-0000-0000-cccc-000000000003', 3, 'ACHIEVED', 0)
ON CONFLICT DO NOTHING;

-- Week 10 (2026-03-09)
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, outcome, carry_forward_streak) VALUES
  ('20000000-0010-0001-0001-000000000000', '10000000-0010-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Regression test suite v1', 'Comprehensive regression tests', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 'ACHIEVED', 0),
  ('20000000-0010-0002-0001-000000000000', '10000000-0010-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Dependency vulnerability scan', 'Audit and fix CVEs', 'ROOK', 2, '00000000-0000-0000-cccc-000000000004', 3, 'ACHIEVED', 0),
  ('20000000-0010-0003-0001-000000000000', '10000000-0010-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Sprint planning facilitation', 'Lead Q1 sprint planning', 'PAWN', 3, NULL, 1, 'ACHIEVED', 0),
  ('20000000-0010-0001-0003-000000000000', '10000000-0010-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Complete SSO wave 2', 'Finish remaining accounts', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', 1),
  ('20000000-0010-0002-0003-000000000000', '10000000-0010-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO self-service portal', 'Let admins manage SSO config', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', 0),
  ('20000000-0010-0001-0004-000000000000', '10000000-0010-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Tax engine integration', 'Complete Avalara integration', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 'ACHIEVED', 1),
  ('20000000-0010-0002-0004-000000000000', '10000000-0010-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Annual billing option', 'Add yearly subscription plans', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 'ACHIEVED', 0),
  ('20000000-0010-0001-0005-000000000000', '10000000-0010-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'SOC2 evidence submission', 'Submit audit evidence package', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000002', 5, 'ACHIEVED', 0),
  ('20000000-0010-0002-0005-000000000000', '10000000-0010-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'EMEA launch prep checklist', 'Final go/no-go checklist', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 'ACHIEVED', 0),
  ('20000000-0010-0001-0006-000000000000', '10000000-0010-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Personalized onboarding paths', 'Different flows per customer segment', 'KING', 1, '00000000-0000-0000-cccc-000000000003', 5, 'PARTIALLY_ACHIEVED', 0),
  ('20000000-0010-0002-0006-000000000000', '10000000-0010-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Customer success integration', 'Sync health data with CS tools', 'ROOK', 2, '00000000-0000-0000-cccc-000000000003', 3, 'ACHIEVED', 0)
ON CONFLICT DO NOTHING;

-- Week 11 (2026-03-16) — LOCKED, no outcomes yet
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, carry_forward_streak) VALUES
  ('20000000-0011-0001-0001-000000000000', '10000000-0011-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Deploy daily goal validation', 'Verify we hit deploy daily target', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 0),
  ('20000000-0011-0002-0001-000000000000', '10000000-0011-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Chaos engineering experiment', 'Run failure injection in staging', 'QUEEN', 2, '00000000-0000-0000-cccc-000000000004', 3, 0),
  ('20000000-0011-0003-0001-000000000000', '10000000-0011-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Quarterly OKR review', 'Prep Q1 review materials', 'PAWN', 3, NULL, 1, 0),
  ('20000000-0011-0001-0003-000000000000', '10000000-0011-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO wave 3 enterprise rollout', 'Next batch of enterprise SSO', 'KING', 1, '00000000-0000-0000-cccc-000000000001', 5, 0),
  ('20000000-0011-0002-0003-000000000000', '10000000-0011-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'Identity governance features', 'Build user lifecycle management', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 0),
  ('20000000-0011-0001-0004-000000000000', '10000000-0011-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Billing reconciliation report', 'Monthly billing reconciliation', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 5, 0),
  ('20000000-0011-0002-0004-000000000000', '10000000-0011-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Payment retry logic', 'Handle failed card payments', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 3, 0),
  ('20000000-0011-0001-0005-000000000000', '10000000-0011-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'EMEA region launch', 'Go-live for EU data residency', 'KING', 1, '00000000-0000-0000-cccc-000000000002', 5, 0),
  ('20000000-0011-0002-0005-000000000000', '10000000-0011-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'Compliance monitoring dashboard', 'Real-time compliance status', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 0),
  ('20000000-0011-0001-0006-000000000000', '10000000-0011-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Personalized onboarding (carry-forward)', 'Complete segmented flows', 'KING', 1, '00000000-0000-0000-cccc-000000000003', 5, 1),
  ('20000000-0011-0002-0006-000000000000', '10000000-0011-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding benchmark report', 'Q1 onboarding metrics report', 'ROOK', 2, '00000000-0000-0000-cccc-000000000003', 3, 0)
ON CONFLICT DO NOTHING;

-- Week 12 (2026-03-23) — DRAFT (current week), partial commits
INSERT INTO weekly_commit (id, plan_id, owner_user_id, title, description, chess_piece, priority_order, rcdo_node_id, estimate_points, carry_forward_streak) VALUES
  ('20000000-0012-0001-0001-000000000000', '10000000-0012-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Production daily deploy verification', 'Confirm daily deploy cadence in production', 'KING', 1, '00000000-0000-0000-cccc-000000000004', 5, 0),
  ('20000000-0012-0002-0001-000000000000', '10000000-0012-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Security scanning in CI', 'Add SAST/DAST to pipeline', 'QUEEN', 2, '00000000-0000-0000-cccc-000000000004', 3, 0),
  ('20000000-0012-0003-0001-000000000000', '10000000-0012-0000-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'Team retrospective', 'Facilitate Q1 retro', 'PAWN', 3, NULL, 1, 0),
  ('20000000-0012-0001-0003-000000000000', '10000000-0012-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO metrics quarterly review', 'Present SSO adoption metrics', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 3, 0),
  ('20000000-0012-0002-0003-000000000000', '10000000-0012-0000-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'SSO SDK for mobile apps', 'Build mobile SSO SDK', 'KING', 2, '00000000-0000-0000-cccc-000000000001', 5, 0),
  ('20000000-0012-0001-0004-000000000000', '10000000-0012-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Q1 billing metrics report', 'Revenue and churn analysis', 'QUEEN', 1, '00000000-0000-0000-cccc-000000000001', 3, 0),
  ('20000000-0012-0002-0004-000000000000', '10000000-0012-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'Dunning email automation', 'Auto-send payment failure emails', 'ROOK', 2, '00000000-0000-0000-cccc-000000000001', 5, 0),
  ('20000000-0012-0001-0005-000000000000', '10000000-0012-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'EMEA post-launch monitoring', 'Monitor EU region first week', 'KING', 1, '00000000-0000-0000-cccc-000000000002', 5, 0),
  ('20000000-0012-0002-0005-000000000000', '10000000-0012-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'SOC2 Type II prep', 'Begin Type II evidence collection', 'ROOK', 2, '00000000-0000-0000-cccc-000000000002', 2, 0),
  ('20000000-0012-0001-0006-000000000000', '10000000-0012-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Onboarding v2 GA release', 'General availability launch', 'KING', 1, '00000000-0000-0000-cccc-000000000003', 5, 0),
  ('20000000-0012-0002-0006-000000000000', '10000000-0012-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'Customer health v2 planning', 'Plan next iteration of health dashboard', 'ROOK', 2, '00000000-0000-0000-cccc-000000000003', 3, 0)
ON CONFLICT DO NOTHING;


-- =========================================================================
-- 4. Lock snapshots — weeks 1-11 (all plans that are LOCKED or RECONCILED)
-- =========================================================================

-- Generate a lock snapshot for every plan in weeks 1-11
INSERT INTO lock_snapshot_header (id, plan_id, locked_at, locked_by_system, snapshot_payload) VALUES
  -- Week 1
  ('30000000-0001-0000-0001-000000000000', '10000000-0001-0000-0001-000000000000', '2026-01-05 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0001-0000-0003-000000000000', '10000000-0001-0000-0003-000000000000', '2026-01-05 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0001-0000-0004-000000000000', '10000000-0001-0000-0004-000000000000', '2026-01-05 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0001-0000-0005-000000000000', '10000000-0001-0000-0005-000000000000', '2026-01-05 12:10:00+00', true, '{"planState":"LOCKED"}'),
  ('30000000-0001-0000-0006-000000000000', '10000000-0001-0000-0006-000000000000', '2026-01-05 11:55:00+00', false, '{"planState":"LOCKED"}'),
  -- Week 2
  ('30000000-0002-0000-0001-000000000000', '10000000-0002-0000-0001-000000000000', '2026-01-12 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0002-0000-0003-000000000000', '10000000-0002-0000-0003-000000000000', '2026-01-12 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0002-0000-0004-000000000000', '10000000-0002-0000-0004-000000000000', '2026-01-12 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0002-0000-0005-000000000000', '10000000-0002-0000-0005-000000000000', '2026-01-12 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0002-0000-0006-000000000000', '10000000-0002-0000-0006-000000000000', '2026-01-12 11:55:00+00', false, '{"planState":"LOCKED"}'),
  -- Week 3
  ('30000000-0003-0000-0001-000000000000', '10000000-0003-0000-0001-000000000000', '2026-01-19 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0003-0000-0003-000000000000', '10000000-0003-0000-0003-000000000000', '2026-01-19 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0003-0000-0004-000000000000', '10000000-0003-0000-0004-000000000000', '2026-01-19 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0003-0000-0005-000000000000', '10000000-0003-0000-0005-000000000000', '2026-01-19 12:15:00+00', true, '{"planState":"LOCKED"}'),
  ('30000000-0003-0000-0006-000000000000', '10000000-0003-0000-0006-000000000000', '2026-01-19 11:50:00+00', false, '{"planState":"LOCKED"}'),
  -- Weeks 4-10 (all on time)
  ('30000000-0004-0000-0001-000000000000', '10000000-0004-0000-0001-000000000000', '2026-01-26 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0004-0000-0003-000000000000', '10000000-0004-0000-0003-000000000000', '2026-01-26 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0004-0000-0004-000000000000', '10000000-0004-0000-0004-000000000000', '2026-01-26 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0004-0000-0005-000000000000', '10000000-0004-0000-0005-000000000000', '2026-01-26 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0004-0000-0006-000000000000', '10000000-0004-0000-0006-000000000000', '2026-01-26 11:55:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0005-0000-0001-000000000000', '10000000-0005-0000-0001-000000000000', '2026-02-02 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0005-0000-0003-000000000000', '10000000-0005-0000-0003-000000000000', '2026-02-02 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0005-0000-0004-000000000000', '10000000-0005-0000-0004-000000000000', '2026-02-02 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0005-0000-0005-000000000000', '10000000-0005-0000-0005-000000000000', '2026-02-02 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0005-0000-0006-000000000000', '10000000-0005-0000-0006-000000000000', '2026-02-02 11:55:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0006-0000-0001-000000000000', '10000000-0006-0000-0001-000000000000', '2026-02-09 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0006-0000-0003-000000000000', '10000000-0006-0000-0003-000000000000', '2026-02-09 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0006-0000-0004-000000000000', '10000000-0006-0000-0004-000000000000', '2026-02-09 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0006-0000-0005-000000000000', '10000000-0006-0000-0005-000000000000', '2026-02-09 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0006-0000-0006-000000000000', '10000000-0006-0000-0006-000000000000', '2026-02-09 11:55:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0007-0000-0001-000000000000', '10000000-0007-0000-0001-000000000000', '2026-02-16 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0007-0000-0003-000000000000', '10000000-0007-0000-0003-000000000000', '2026-02-16 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0007-0000-0004-000000000000', '10000000-0007-0000-0004-000000000000', '2026-02-16 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0007-0000-0005-000000000000', '10000000-0007-0000-0005-000000000000', '2026-02-16 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0007-0000-0006-000000000000', '10000000-0007-0000-0006-000000000000', '2026-02-16 11:55:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0008-0000-0001-000000000000', '10000000-0008-0000-0001-000000000000', '2026-02-23 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0008-0000-0003-000000000000', '10000000-0008-0000-0003-000000000000', '2026-02-23 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0008-0000-0004-000000000000', '10000000-0008-0000-0004-000000000000', '2026-02-23 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0008-0000-0005-000000000000', '10000000-0008-0000-0005-000000000000', '2026-02-23 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0008-0000-0006-000000000000', '10000000-0008-0000-0006-000000000000', '2026-02-23 11:55:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0009-0000-0001-000000000000', '10000000-0009-0000-0001-000000000000', '2026-03-02 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0009-0000-0003-000000000000', '10000000-0009-0000-0003-000000000000', '2026-03-02 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0009-0000-0004-000000000000', '10000000-0009-0000-0004-000000000000', '2026-03-02 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0009-0000-0005-000000000000', '10000000-0009-0000-0005-000000000000', '2026-03-02 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0009-0000-0006-000000000000', '10000000-0009-0000-0006-000000000000', '2026-03-02 11:55:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0010-0000-0001-000000000000', '10000000-0010-0000-0001-000000000000', '2026-03-09 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0010-0000-0003-000000000000', '10000000-0010-0000-0003-000000000000', '2026-03-09 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0010-0000-0004-000000000000', '10000000-0010-0000-0004-000000000000', '2026-03-09 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0010-0000-0005-000000000000', '10000000-0010-0000-0005-000000000000', '2026-03-09 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0010-0000-0006-000000000000', '10000000-0010-0000-0006-000000000000', '2026-03-09 11:55:00+00', false, '{"planState":"LOCKED"}'),
  -- Week 11 (LOCKED only)
  ('30000000-0011-0000-0001-000000000000', '10000000-0011-0000-0001-000000000000', '2026-03-16 11:30:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0011-0000-0003-000000000000', '10000000-0011-0000-0003-000000000000', '2026-03-16 11:40:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0011-0000-0004-000000000000', '10000000-0011-0000-0004-000000000000', '2026-03-16 11:45:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0011-0000-0005-000000000000', '10000000-0011-0000-0005-000000000000', '2026-03-16 11:50:00+00', false, '{"planState":"LOCKED"}'),
  ('30000000-0011-0000-0006-000000000000', '10000000-0011-0000-0006-000000000000', '2026-03-16 11:55:00+00', false, '{"planState":"LOCKED"}')
ON CONFLICT DO NOTHING;

-- Back-fill lock_snapshot_id on plans
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0001-0000-0001-000000000000' WHERE id = '10000000-0001-0000-0001-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0001-0000-0003-000000000000' WHERE id = '10000000-0001-0000-0003-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0001-0000-0004-000000000000' WHERE id = '10000000-0001-0000-0004-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0001-0000-0005-000000000000' WHERE id = '10000000-0001-0000-0005-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0001-0000-0006-000000000000' WHERE id = '10000000-0001-0000-0006-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0002-0000-0001-000000000000' WHERE id = '10000000-0002-0000-0001-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0002-0000-0003-000000000000' WHERE id = '10000000-0002-0000-0003-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0002-0000-0004-000000000000' WHERE id = '10000000-0002-0000-0004-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0002-0000-0005-000000000000' WHERE id = '10000000-0002-0000-0005-000000000000';
UPDATE weekly_plan SET lock_snapshot_id = '30000000-0002-0000-0006-000000000000' WHERE id = '10000000-0002-0000-0006-000000000000';
-- (weeks 3-11 similarly)
UPDATE weekly_plan SET lock_snapshot_id = concat('30000000-', lpad(split_part(id::text, '-', 2), 4, '0'), '-0000-', split_part(id::text, '-', 4), '-000000000000')::uuid
WHERE state IN ('LOCKED', 'RECONCILED', 'RECONCILING') AND lock_snapshot_id IS NULL;


-- =========================================================================
-- 5. Reconcile snapshots — weeks 1-10 (RECONCILED plans only)
-- =========================================================================

INSERT INTO reconcile_snapshot_header (id, plan_id, reconciled_at, snapshot_payload)
SELECT
  concat('31000000-', lpad(split_part(p.id::text, '-', 2), 4, '0'), '-0000-', split_part(p.id::text, '-', 4), '-000000000000')::uuid,
  p.id,
  p.reconcile_deadline - interval '2 hours',
  '{"planState":"RECONCILED"}'
FROM weekly_plan p
WHERE p.state = 'RECONCILED'
ON CONFLICT DO NOTHING;

-- Back-fill reconcile_snapshot_id on plans
UPDATE weekly_plan SET reconcile_snapshot_id = concat('31000000-', lpad(split_part(id::text, '-', 2), 4, '0'), '-0000-', split_part(id::text, '-', 4), '-000000000000')::uuid
WHERE state = 'RECONCILED' AND reconcile_snapshot_id IS NULL;


-- =========================================================================
-- 6. Carry-forward links — connect carry-forward commits to their sources
-- =========================================================================

-- Dan wk1→wk2: wireframes
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0002-0001-0006-000000000000', '20000000-0001-0002-0006-000000000000', '20000000-0002-0001-0006-000000000000', 'BLOCKED_BY_DEPENDENCY', 'Blocked by design system update')
ON CONFLICT DO NOTHING;

-- Alice wk3→wk4: Azure AD SSO
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0004-0001-0003-000000000000', '20000000-0003-0001-0003-000000000000', '20000000-0004-0001-0003-000000000000', 'STILL_IN_PROGRESS', 'Azure AD integration more complex than estimated')
ON CONFLICT DO NOTHING;

-- Carol wk3→wk4: EU database
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0004-0001-0005-000000000000', '20000000-0003-0001-0005-000000000000', '20000000-0004-0001-0005-000000000000', 'EXTERNAL_DELAY', 'AWS marketplace procurement delay')
ON CONFLICT DO NOTHING;

-- Bob wk4→wk5: proration fix
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0005-0001-0004-000000000000', '20000000-0004-0002-0004-000000000000', '20000000-0005-0001-0004-000000000000', 'UNDERESTIMATED', 'Timezone edge cases not accounted for')
ON CONFLICT DO NOTHING;

-- Bob wk5→wk6: webhook DLQ
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0006-0001-0004-000000000000', '20000000-0005-0003-0004-000000000000', '20000000-0006-0001-0004-000000000000', 'STILL_IN_PROGRESS', 'Dead letter queue implementation continued')
ON CONFLICT DO NOTHING;

-- Carol wk5→wk6: GDPR PDF
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0006-0002-0005-000000000000', '20000000-0005-0002-0005-000000000000', '20000000-0006-0002-0005-000000000000', 'STILL_IN_PROGRESS', 'PDF generation needed more work')
ON CONFLICT DO NOTHING;

-- Dan wk5→wk6: health dashboard
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0006-0001-0006-000000000000', '20000000-0005-0002-0006-000000000000', '20000000-0006-0001-0006-000000000000', 'BLOCKED_BY_DEPENDENCY', 'Missing API endpoints from backend team')
ON CONFLICT DO NOTHING;

-- Carol wk6→wk7: EU migration
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0007-0001-0005-000000000000', '20000000-0006-0001-0005-000000000000', '20000000-0007-0001-0005-000000000000', 'STILL_IN_PROGRESS', 'Remaining 2 tenants had latency issues')
ON CONFLICT DO NOTHING;

-- Dev User wk7→wk8: E2E tests
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0008-0001-0001-000000000000', '20000000-0007-0001-0001-000000000000', '20000000-0008-0001-0001-000000000000', 'SCOPE_EXPANDED', 'Added more critical paths to test')
ON CONFLICT DO NOTHING;

-- Bob wk7→wk8: rev rec
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0008-0001-0004-000000000000', '20000000-0007-0001-0004-000000000000', '20000000-0008-0001-0004-000000000000', 'UNDERESTIMATED', 'ASC 606 requirements more complex than expected')
ON CONFLICT DO NOTHING;

-- Alice wk9→wk10: SSO wave 2
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0010-0001-0003-000000000000', '20000000-0009-0001-0003-000000000000', '20000000-0010-0001-0003-000000000000', 'STILL_IN_PROGRESS', '2 of 5 accounts pending IT approval')
ON CONFLICT DO NOTHING;

-- Bob wk9→wk10: tax engine
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0010-0001-0004-000000000000', '20000000-0009-0002-0004-000000000000', '20000000-0010-0001-0004-000000000000', 'EXTERNAL_DELAY', 'Avalara API access delayed by vendor')
ON CONFLICT DO NOTHING;

-- Dan wk10→wk11: personalized onboarding
INSERT INTO carry_forward_link (id, source_commit_id, target_commit_id, reason, reason_notes) VALUES
  ('50000000-0011-0001-0006-000000000000', '20000000-0010-0001-0006-000000000000', '20000000-0011-0001-0006-000000000000', 'STILL_IN_PROGRESS', 'Segmented flows need more iteration')
ON CONFLICT DO NOTHING;


-- =========================================================================
-- 7. Scope change events — post-lock changes for select weeks
-- =========================================================================

INSERT INTO scope_change_event (id, plan_id, commit_id, category, changed_by_user_id, reason, previous_value, new_value, created_at) VALUES
  -- Week 3: Carol's plan had estimate change
  ('40000000-0003-0001-0005-000000000000', '10000000-0003-0000-0005-000000000000', '20000000-0003-0001-0005-000000000000', 'ESTIMATE_CHANGED', '00000000-0000-0000-0000-000000000005', 'EU database provisioning more complex than estimated', '"3"', '"5"', '2026-01-20 14:00:00+00'),
  -- Week 4: Bob added a commit mid-week
  ('40000000-0004-0001-0004-000000000000', '10000000-0004-0000-0004-000000000000', '20000000-0004-0003-0004-000000000000', 'COMMIT_ADDED', '00000000-0000-0000-0000-000000000004', 'Critical billing unit test coverage needed', NULL, NULL, '2026-01-28 10:00:00+00'),
  -- Week 6: Dan changed priority
  ('40000000-0006-0001-0006-000000000000', '10000000-0006-0000-0006-000000000000', '20000000-0006-0001-0006-000000000000', 'PRIORITY_CHANGED', '00000000-0000-0000-0000-000000000006', 'Health dashboard became urgent — customer escalation', '"2"', '"1"', '2026-02-10 09:00:00+00'),
  -- Week 7: Bob removed a commit
  ('40000000-0007-0001-0004-000000000000', '10000000-0007-0000-0004-000000000000', NULL, 'COMMIT_REMOVED', '00000000-0000-0000-0000-000000000004', 'Rev rec deprioritized pending accounting review', NULL, NULL, '2026-02-18 11:00:00+00'),
  -- Week 9: Alice changed RCDO linkage
  ('40000000-0009-0001-0003-000000000000', '10000000-0009-0000-0003-000000000000', '20000000-0009-0002-0003-000000000000', 'RCDO_CHANGED', '00000000-0000-0000-0000-000000000003', 'Role-based access better fits Reduce Churn objective', '"00000000-0000-0000-cccc-000000000001"', '"00000000-0000-0000-cccc-000000000003"', '2026-03-03 15:00:00+00'),
  -- Week 10: Carol changed chess piece
  ('40000000-0010-0001-0005-000000000000', '10000000-0010-0000-0005-000000000000', '20000000-0010-0001-0005-000000000000', 'CHESS_PIECE_CHANGED', '00000000-0000-0000-0000-000000000005', 'SOC2 submission elevated to strategic priority', '"ROOK"', '"QUEEN"', '2026-03-10 10:00:00+00')
ON CONFLICT DO NOTHING;


-- =========================================================================
-- 8. Manager comments — Manager One (user-002) reviewing plans
-- =========================================================================

INSERT INTO manager_comment (id, plan_id, commit_id, author_user_id, content, created_at) VALUES
  -- Week 1 feedback
  ('60000000-0001-0000-0003-000000000000', '10000000-0001-0000-0003-000000000000', NULL, '00000000-0000-0000-0000-000000000002', 'Great progress on SSO research. The OIDC-first approach looks solid — make sure we document the rationale for future reference.', '2026-01-09 16:00:00+00'),
  ('60000000-0001-0000-0006-000000000000', '10000000-0001-0000-0006-000000000000', '20000000-0001-0002-0006-000000000000', '00000000-0000-0000-0000-000000000002', 'The wireframe blocker on design system is concerning. Lets make sure this doesnt slip another week.', '2026-01-09 16:30:00+00'),
  -- Week 3 feedback
  ('60000000-0003-0000-0005-000000000000', '10000000-0003-0000-0005-000000000000', NULL, '00000000-0000-0000-0000-000000000002', 'Carol — the procurement delay is frustrating but out of your control. Good pivot to focus on data routing middleware in the meantime.', '2026-01-23 14:00:00+00'),
  -- Week 5 feedback
  ('60000000-0005-0000-0001-000000000000', '10000000-0005-0000-0001-000000000000', NULL, '00000000-0000-0000-0000-000000000002', 'Deploy frequency dashboard is exactly what we need. Can you share access with the leadership team?', '2026-02-06 15:00:00+00'),
  ('60000000-0005-0000-0004-000000000000', '10000000-0005-0000-0004-000000000000', NULL, '00000000-0000-0000-0000-000000000002', 'Bob — nice work clearing the proration backlog. The webhook DLQ should be a high priority next week.', '2026-02-06 15:30:00+00'),
  -- Week 7 feedback
  ('60000000-0007-0000-0003-000000000000', '10000000-0007-0000-0003-000000000000', NULL, '00000000-0000-0000-0000-000000000002', 'SSO production rollout was smooth — excellent coordination with the customer success team.', '2026-02-20 14:00:00+00'),
  ('60000000-0007-0000-0004-000000000000', '10000000-0007-0000-0004-000000000000', NULL, '00000000-0000-0000-0000-000000000002', 'Revenue recognition not achieved is a red flag for Q1. Lets discuss timeline in our 1:1.', '2026-02-20 14:30:00+00'),
  -- Week 9 feedback
  ('60000000-0009-0000-0001-000000000000', '10000000-0009-0000-0001-000000000000', NULL, '00000000-0000-0000-0000-000000000002', 'Zero-downtime deploy is a major milestone for the team. Well done driving this end to end.', '2026-03-06 14:00:00+00'),
  -- Week 10 feedback
  ('60000000-0010-0000-0005-000000000000', '10000000-0010-0000-0005-000000000000', NULL, '00000000-0000-0000-0000-000000000002', 'SOC2 evidence submission on time — this unblocks the EMEA launch. Great work under pressure, Carol.', '2026-03-13 14:00:00+00')
ON CONFLICT DO NOTHING;


-- =========================================================================
-- 9. Manager review exceptions
-- =========================================================================

INSERT INTO manager_review_exception (id, team_id, plan_id, user_id, exception_type, severity, description, week_start_date, resolved, resolution, resolved_at, resolved_by_id, created_at) VALUES
  -- Week 1: Carol auto-locked (late lock)
  ('80000000-0001-0001-0005-000000000000', '00000000-0000-0000-0000-000000000010', '10000000-0001-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'AUTO_LOCKED', 'MEDIUM', 'Plan auto-locked at deadline — Carol did not manually lock', '2026-01-05', true, 'Discussed in 1:1 — was on PTO, will set reminder', '2026-01-06 10:00:00+00', '00000000-0000-0000-0000-000000000002', '2026-01-05 12:10:00+00'),
  -- Week 3: Carol again (late + over budget after estimate change)
  ('80000000-0003-0001-0005-000000000000', '00000000-0000-0000-0000-000000000010', '10000000-0003-0000-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'AUTO_LOCKED', 'MEDIUM', 'Plan auto-locked again', '2026-01-19', true, 'Recurring issue — established lock checklist', '2026-01-20 10:00:00+00', '00000000-0000-0000-0000-000000000002', '2026-01-19 12:15:00+00'),
  -- Week 7: Bob revenue rec not achieved
  ('80000000-0007-0001-0004-000000000000', '00000000-0000-0000-0000-000000000010', '10000000-0007-0000-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'REPEATED_CARRY_FORWARD', 'LOW', 'Revenue recognition carried forward — dependency on accounting team', '2026-02-16', true, 'Accounting review completed, work can proceed', '2026-02-23 09:00:00+00', '00000000-0000-0000-0000-000000000002', '2026-02-20 14:30:00+00'),
  -- Week 11: Unresolved exception for Dan (carry forward streak 1 for personalized onboarding)
  ('80000000-0011-0001-0006-000000000000', '00000000-0000-0000-0000-000000000010', '10000000-0011-0000-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'REPEATED_CARRY_FORWARD', 'MEDIUM', 'Personalized onboarding has been carried forward — needs attention', '2026-03-16', false, NULL, NULL, NULL, '2026-03-16 12:00:00+00')
ON CONFLICT DO NOTHING;


-- =========================================================================
-- 10. Notifications — mix of read/unread for the dev user and others
-- =========================================================================

INSERT INTO notification (id, recipient_user_id, notification_type, title, body, reference_id, reference_type, read, priority, created_at) VALUES
  -- Dev User notifications
  ('70000000-0012-0001-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'DRAFT_WINDOW_OPENED', 'New week planning open', 'The draft window for week of 2026-03-23 is now open. Start planning your commitments.', '10000000-0012-0000-0001-000000000000', 'WEEKLY_PLAN', false, 'LOW', '2026-03-20 12:00:00+00'),
  ('70000000-0011-0001-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'LOCK_DUE_REMINDER', 'Lock due tomorrow', 'Your plan for week of 2026-03-16 should be locked by tomorrow noon.', '10000000-0011-0000-0001-000000000000', 'WEEKLY_PLAN', true, 'MEDIUM', '2026-03-15 12:00:00+00'),
  ('70000000-0011-0002-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'RECONCILIATION_OPENED', 'Reconciliation open for week of 2026-03-09', 'The reconciliation window is now open for your plan.', '10000000-0010-0000-0001-000000000000', 'WEEKLY_PLAN', true, 'MEDIUM', '2026-03-14 17:00:00+00'),
  ('70000000-0010-0001-0001-000000000000', '00000000-0000-0000-0000-000000000001', 'RECONCILIATION_DUE_REMINDER', 'Reconciliation due soon', 'Your reconciliation for week of 2026-03-09 is due by 2026-03-21 10:00 UTC.', '10000000-0010-0000-0001-000000000000', 'WEEKLY_PLAN', true, 'MEDIUM', '2026-03-20 10:00:00+00'),
  -- Manager notifications
  ('70000000-0011-0001-0002-000000000000', '00000000-0000-0000-0000-000000000002', 'MANAGER_EXCEPTION_DIGEST', 'Exception: Dan Okafor carry-forward', 'Dan Okafor has a repeated carry-forward on "Personalized onboarding" — 1 week streak.', '80000000-0011-0001-0006-000000000000', 'EXCEPTION', false, 'HIGH', '2026-03-16 12:00:00+00'),
  ('70000000-0012-0001-0002-000000000000', '00000000-0000-0000-0000-000000000002', 'DRAFT_WINDOW_OPENED', 'New week: 2026-03-23', 'Draft window is open for the week of 2026-03-23.', NULL, NULL, false, 'LOW', '2026-03-20 12:00:00+00'),
  -- Alice notifications
  ('70000000-0012-0001-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'DRAFT_WINDOW_OPENED', 'New week planning open', 'The draft window for week of 2026-03-23 is now open.', '10000000-0012-0000-0003-000000000000', 'WEEKLY_PLAN', false, 'LOW', '2026-03-20 12:00:00+00'),
  ('70000000-0011-0001-0003-000000000000', '00000000-0000-0000-0000-000000000003', 'LOCK_DUE_REMINDER', 'Lock due tomorrow', 'Your plan for week of 2026-03-16 should be locked by tomorrow noon.', '10000000-0011-0000-0003-000000000000', 'WEEKLY_PLAN', true, 'MEDIUM', '2026-03-15 12:00:00+00'),
  -- Bob notifications
  ('70000000-0012-0001-0004-000000000000', '00000000-0000-0000-0000-000000000004', 'DRAFT_WINDOW_OPENED', 'New week planning open', 'The draft window for week of 2026-03-23 is now open.', '10000000-0012-0000-0004-000000000000', 'WEEKLY_PLAN', false, 'LOW', '2026-03-20 12:00:00+00'),
  -- Carol notifications
  ('70000000-0012-0001-0005-000000000000', '00000000-0000-0000-0000-000000000005', 'DRAFT_WINDOW_OPENED', 'New week planning open', 'The draft window for week of 2026-03-23 is now open.', '10000000-0012-0000-0005-000000000000', 'WEEKLY_PLAN', false, 'LOW', '2026-03-20 12:00:00+00'),
  -- Dan notifications
  ('70000000-0012-0001-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'DRAFT_WINDOW_OPENED', 'New week planning open', 'The draft window for week of 2026-03-23 is now open.', '10000000-0012-0000-0006-000000000000', 'WEEKLY_PLAN', false, 'LOW', '2026-03-20 12:00:00+00'),
  ('70000000-0011-0001-0006-000000000000', '00000000-0000-0000-0000-000000000006', 'REPEATED_CARRY_FORWARD_REMINDER', 'Carry-forward alert', 'Your commit "Personalized onboarding" has been carried forward. Consider breaking it down or re-estimating.', '20000000-0011-0001-0006-000000000000', 'WEEKLY_COMMIT', false, 'MEDIUM', '2026-03-16 12:00:00+00')
ON CONFLICT DO NOTHING;