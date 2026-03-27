-- =============================================================================
-- V13 — Backfill derived read-model tables from transactional seed data
--
-- The V11 seed inserted rich transactional data (plans, commits, snapshots)
-- but the derived tables (user_week_fact, team_week_rollup, compliance_fact,
-- rcdo_week_rollup, carry_forward_fact) are normally populated by the
-- ReadModelRefreshService on lifecycle events. Since seed data bypasses the
-- service layer, we must backfill directly.
-- =============================================================================

-- =========================================================================
-- 1. user_week_fact — per-user per-week aggregates
-- =========================================================================

DELETE FROM user_week_fact WHERE user_id IN (
  SELECT user_id FROM team_membership
  WHERE team_id = '00000000-0000-0000-0000-000000000010'
);

INSERT INTO user_week_fact (
  user_id, week_start, plan_state, lock_compliance, reconcile_compliance,
  total_planned_points, total_achieved_points, commit_count,
  carry_forward_count, scope_change_count, king_count, queen_count, refreshed_at
)
SELECT
  p.owner_user_id,
  p.week_start_date,
  p.state,
  -- lock_compliance: locked (not DRAFT) and plan is_compliant
  (p.state != 'DRAFT' AND p.is_compliant),
  -- reconcile_compliance: plan is RECONCILED
  (p.state = 'RECONCILED'),
  -- total_planned_points
  COALESCE(SUM(c.estimate_points), 0),
  -- total_achieved_points (only ACHIEVED outcome counts)
  COALESCE(SUM(CASE WHEN c.outcome = 'ACHIEVED' THEN c.estimate_points ELSE 0 END), 0),
  -- commit_count
  COUNT(c.id),
  -- carry_forward_count
  COUNT(CASE WHEN c.carry_forward_streak > 0 THEN 1 END),
  -- scope_change_count
  (SELECT COUNT(*) FROM scope_change_event sc WHERE sc.plan_id = p.id),
  -- king_count
  COUNT(CASE WHEN c.chess_piece = 'KING' THEN 1 END),
  -- queen_count
  COUNT(CASE WHEN c.chess_piece = 'QUEEN' THEN 1 END),
  now()
FROM weekly_plan p
JOIN weekly_commit c ON c.plan_id = p.id
WHERE p.team_id = '00000000-0000-0000-0000-000000000010'
GROUP BY p.id, p.owner_user_id, p.week_start_date, p.state, p.is_compliant
ON CONFLICT (user_id, week_start)
DO UPDATE SET
  plan_state             = EXCLUDED.plan_state,
  lock_compliance        = EXCLUDED.lock_compliance,
  reconcile_compliance   = EXCLUDED.reconcile_compliance,
  total_planned_points   = EXCLUDED.total_planned_points,
  total_achieved_points  = EXCLUDED.total_achieved_points,
  commit_count           = EXCLUDED.commit_count,
  carry_forward_count    = EXCLUDED.carry_forward_count,
  scope_change_count     = EXCLUDED.scope_change_count,
  king_count             = EXCLUDED.king_count,
  queen_count            = EXCLUDED.queen_count,
  refreshed_at           = now();


-- =========================================================================
-- 2. compliance_fact — per-user per-week lock/reconcile compliance
-- =========================================================================

DELETE FROM compliance_fact WHERE user_id IN (
  SELECT user_id FROM team_membership
  WHERE team_id = '00000000-0000-0000-0000-000000000010'
);

INSERT INTO compliance_fact (
  user_id, week_start,
  lock_on_time, lock_late, auto_locked,
  reconcile_on_time, reconcile_late, reconcile_missed,
  refreshed_at
)
SELECT
  p.owner_user_id,
  p.week_start_date,
  -- lock_on_time: not DRAFT, compliant, and NOT auto-locked
  (p.state != 'DRAFT' AND p.is_compliant AND COALESCE(lsh.locked_by_system, false) = false),
  -- lock_late: not DRAFT but not compliant
  (p.state != 'DRAFT' AND NOT p.is_compliant),
  -- auto_locked: lock snapshot says locked_by_system
  COALESCE(lsh.locked_by_system, false),
  -- reconcile_on_time: state is RECONCILED
  (p.state = 'RECONCILED'),
  -- reconcile_late: false (no reconciled_at tracking yet)
  false,
  -- reconcile_missed: not reconciled and past reconcile deadline
  (p.state != 'RECONCILED' AND p.reconcile_deadline < now()),
  now()
FROM weekly_plan p
LEFT JOIN lock_snapshot_header lsh ON lsh.id = p.lock_snapshot_id
WHERE p.team_id = '00000000-0000-0000-0000-000000000010'
ON CONFLICT (user_id, week_start)
DO UPDATE SET
  lock_on_time      = EXCLUDED.lock_on_time,
  lock_late         = EXCLUDED.lock_late,
  auto_locked       = EXCLUDED.auto_locked,
  reconcile_on_time = EXCLUDED.reconcile_on_time,
  reconcile_late    = EXCLUDED.reconcile_late,
  reconcile_missed  = EXCLUDED.reconcile_missed,
  refreshed_at      = now();


-- =========================================================================
-- 3. team_week_rollup — per-team per-week aggregates
-- =========================================================================

DELETE FROM team_week_rollup
WHERE team_id = '00000000-0000-0000-0000-000000000010';

INSERT INTO team_week_rollup (
  team_id, week_start, member_count,
  locked_count, reconciled_count,
  total_planned_points, total_achieved_points,
  exception_count, avg_carry_forward_rate,
  chess_distribution, refreshed_at
)
SELECT
  p.team_id,
  p.week_start_date,
  -- member_count (constant for this team)
  (SELECT COUNT(*) FROM team_membership WHERE team_id = '00000000-0000-0000-0000-000000000010'),
  -- locked_count
  COUNT(DISTINCT CASE WHEN p.state IN ('LOCKED','RECONCILING','RECONCILED') THEN p.id END),
  -- reconciled_count
  COUNT(DISTINCT CASE WHEN p.state = 'RECONCILED' THEN p.id END),
  -- total_planned_points
  COALESCE(SUM(c.estimate_points), 0),
  -- total_achieved_points
  COALESCE(SUM(CASE WHEN c.outcome = 'ACHIEVED' THEN c.estimate_points ELSE 0 END), 0),
  -- exception_count
  (SELECT COUNT(*) FROM manager_review_exception mre
   WHERE mre.team_id = p.team_id AND mre.week_start_date = p.week_start_date),
  -- avg_carry_forward_rate
  CASE WHEN COUNT(c.id) > 0
    THEN COUNT(CASE WHEN c.carry_forward_streak > 0 THEN 1 END)::double precision / COUNT(c.id)
    ELSE 0.0
  END,
  -- chess_distribution as JSONB
  jsonb_build_object(
    'KING',   COUNT(CASE WHEN c.chess_piece = 'KING' THEN 1 END),
    'QUEEN',  COUNT(CASE WHEN c.chess_piece = 'QUEEN' THEN 1 END),
    'ROOK',   COUNT(CASE WHEN c.chess_piece = 'ROOK' THEN 1 END),
    'BISHOP', COUNT(CASE WHEN c.chess_piece = 'BISHOP' THEN 1 END),
    'KNIGHT', COUNT(CASE WHEN c.chess_piece = 'KNIGHT' THEN 1 END),
    'PAWN',   COUNT(CASE WHEN c.chess_piece = 'PAWN' THEN 1 END)
  ),
  now()
FROM weekly_plan p
JOIN weekly_commit c ON c.plan_id = p.id
WHERE p.team_id = '00000000-0000-0000-0000-000000000010'
GROUP BY p.team_id, p.week_start_date
ON CONFLICT (team_id, week_start)
DO UPDATE SET
  member_count           = EXCLUDED.member_count,
  locked_count           = EXCLUDED.locked_count,
  reconciled_count       = EXCLUDED.reconciled_count,
  total_planned_points   = EXCLUDED.total_planned_points,
  total_achieved_points  = EXCLUDED.total_achieved_points,
  exception_count        = EXCLUDED.exception_count,
  avg_carry_forward_rate = EXCLUDED.avg_carry_forward_rate,
  chess_distribution     = EXCLUDED.chess_distribution,
  refreshed_at           = now();


-- =========================================================================
-- 4. rcdo_week_rollup — per-RCDO-node per-week aggregates
-- =========================================================================

DELETE FROM rcdo_week_rollup WHERE rcdo_node_id IN (
  SELECT DISTINCT c.rcdo_node_id FROM weekly_commit c
  JOIN weekly_plan p ON p.id = c.plan_id
  WHERE p.team_id = '00000000-0000-0000-0000-000000000010'
    AND c.rcdo_node_id IS NOT NULL
);

INSERT INTO rcdo_week_rollup (
  rcdo_node_id, week_start,
  planned_points, achieved_points, commit_count,
  team_contribution_breakdown, refreshed_at
)
SELECT
  c.rcdo_node_id,
  p.week_start_date,
  COALESCE(SUM(c.estimate_points), 0),
  COALESCE(SUM(CASE WHEN c.outcome = 'ACHIEVED' THEN c.estimate_points ELSE 0 END), 0),
  COUNT(c.id),
  jsonb_build_object(p.team_id::text, COALESCE(SUM(c.estimate_points), 0)),
  now()
FROM weekly_commit c
JOIN weekly_plan p ON p.id = c.plan_id
WHERE c.rcdo_node_id IS NOT NULL
  AND p.team_id = '00000000-0000-0000-0000-000000000010'
GROUP BY c.rcdo_node_id, p.week_start_date, p.team_id
ON CONFLICT (rcdo_node_id, week_start)
DO UPDATE SET
  planned_points              = EXCLUDED.planned_points,
  achieved_points             = EXCLUDED.achieved_points,
  commit_count                = EXCLUDED.commit_count,
  team_contribution_breakdown = EXCLUDED.team_contribution_breakdown,
  refreshed_at                = now();


-- =========================================================================
-- 5. carry_forward_fact — one row per carry-forward commit
-- =========================================================================

DELETE FROM carry_forward_fact WHERE commit_id IN (
  SELECT c.id FROM weekly_commit c
  JOIN weekly_plan p ON p.id = c.plan_id
  WHERE p.team_id = '00000000-0000-0000-0000-000000000010'
);

INSERT INTO carry_forward_fact (
  commit_id, source_week, current_week, streak_length,
  rcdo_node_id, chess_piece, refreshed_at
)
SELECT
  c.id,
  p.week_start_date - (c.carry_forward_streak * 7),
  p.week_start_date,
  c.carry_forward_streak,
  c.rcdo_node_id,
  c.chess_piece,
  now()
FROM weekly_commit c
JOIN weekly_plan p ON p.id = c.plan_id
WHERE c.carry_forward_streak > 0
  AND p.team_id = '00000000-0000-0000-0000-000000000010'
ON CONFLICT (commit_id)
DO UPDATE SET
  source_week   = EXCLUDED.source_week,
  current_week  = EXCLUDED.current_week,
  streak_length = EXCLUDED.streak_length,
  rcdo_node_id  = EXCLUDED.rcdo_node_id,
  chess_piece   = EXCLUDED.chess_piece,
  refreshed_at  = now();
