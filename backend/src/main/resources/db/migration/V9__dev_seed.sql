-- ---------------------------------------------------------------------------
-- Dev seed data — matches MockHostProvider.tsx UUIDs exactly.
-- Safe to run repeatedly (all inserts use ON CONFLICT DO NOTHING).
-- ---------------------------------------------------------------------------

-- Organization
INSERT INTO organization (id, name)
VALUES ('00000000-0000-0000-0000-000000000100', 'Acme Corp')
ON CONFLICT DO NOTHING;

-- Org config: extend lock deadline to 120 h (Mon + 5 days = Sat noon) so plans
-- stay DRAFT all week during local development without being auto-locked.
INSERT INTO org_config (org_id, lock_due_offset_hours, reconcile_due_offset_hours)
VALUES ('00000000-0000-0000-0000-000000000100', 120, 240)
ON CONFLICT (org_id) DO UPDATE
  SET lock_due_offset_hours     = 120,
      reconcile_due_offset_hours = 240;

-- Teams
INSERT INTO team (id, organization_id, name)
VALUES ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000100', 'Engineering')
ON CONFLICT DO NOTHING;

-- Users — dev user is MANAGER so the Team Week dashboard shows memberViews
INSERT INTO user_account (id, organization_id, email, display_name, home_team_id, role)
VALUES
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100',
     'dev@example.com',     'Dev User',    '00000000-0000-0000-0000-000000000010', 'MANAGER'),
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000100',
     'manager@example.com', 'Manager One', '00000000-0000-0000-0000-000000000010', 'MANAGER')
ON CONFLICT DO NOTHING;

-- Team memberships
INSERT INTO team_membership (team_id, user_id, role)
VALUES
    ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', 'MANAGER'),
    ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000002', 'MANAGER')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Seed RCDO hierarchy so the commit form RCDO picker is not empty.
--
-- Structure:
--   Rally Cry: "Grow the Business"
--     Defining Objective: "Increase Revenue"
--       Outcome: "Close 10 Enterprise Deals"
--       Outcome: "Expand to EMEA Market"
--     Defining Objective: "Reduce Churn"
--       Outcome: "Improve Onboarding NPS to 60+"
--   Rally Cry: "Operational Excellence"
--     Defining Objective: "Ship Faster"
--       Outcome: "Deploy Daily by Q2"
-- ---------------------------------------------------------------------------

-- Rally Cries
INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-aaaa-000000000001', 'RALLY_CRY', 'Grow the Business',      'ACTIVE', NULL),
    ('00000000-0000-0000-aaaa-000000000002', 'RALLY_CRY', 'Operational Excellence', 'ACTIVE', NULL)
ON CONFLICT DO NOTHING;

-- Defining Objectives under "Grow the Business"
INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-bbbb-000000000001', 'DEFINING_OBJECTIVE', 'Increase Revenue', 'ACTIVE', '00000000-0000-0000-aaaa-000000000001'),
    ('00000000-0000-0000-bbbb-000000000002', 'DEFINING_OBJECTIVE', 'Reduce Churn',     'ACTIVE', '00000000-0000-0000-aaaa-000000000001')
ON CONFLICT DO NOTHING;

-- Defining Objective under "Operational Excellence"
INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-bbbb-000000000003', 'DEFINING_OBJECTIVE', 'Ship Faster', 'ACTIVE', '00000000-0000-0000-aaaa-000000000002')
ON CONFLICT DO NOTHING;

-- Outcomes under "Increase Revenue"
INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-cccc-000000000001', 'OUTCOME', 'Close 10 Enterprise Deals',    'ACTIVE', '00000000-0000-0000-bbbb-000000000001'),
    ('00000000-0000-0000-cccc-000000000002', 'OUTCOME', 'Expand to EMEA Market',         'ACTIVE', '00000000-0000-0000-bbbb-000000000001')
ON CONFLICT DO NOTHING;

-- Outcome under "Reduce Churn"
INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-cccc-000000000003', 'OUTCOME', 'Improve Onboarding NPS to 60+', 'ACTIVE', '00000000-0000-0000-bbbb-000000000002')
ON CONFLICT DO NOTHING;

-- Outcome under "Ship Faster"
INSERT INTO rcdo_node (id, node_type, title, status, parent_id)
VALUES
    ('00000000-0000-0000-cccc-000000000004', 'OUTCOME', 'Deploy Daily by Q2', 'ACTIVE', '00000000-0000-0000-bbbb-000000000003')
ON CONFLICT DO NOTHING;
