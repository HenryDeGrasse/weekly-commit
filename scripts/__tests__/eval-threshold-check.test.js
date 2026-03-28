'use strict';

/**
 * Unit tests for scripts/eval-threshold-check.js
 *
 * Run with: node --test scripts/__tests__/eval-threshold-check.test.js
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { computeMetrics, checkThresholds, checkRegression } = require('../eval-threshold-check.js');

// ── Fixtures ──────────────────────────────────────────────────────────────────

/** Minimal thresholds config reused across most tests. */
const THRESHOLDS = {
  deterministic: {
    schema_valid_rate: 1.0,
    overall_case_pass_rate: 0.95,
    critical_case_pass_rate: 1.0,
  },
  judge_assistive: {
    applies_to: ['COMMIT_DRAFT_ASSIST'],
    title_judge_clarity_min: 0.70,
    criteria_judge_measurable_min: 0.70,
  },
  regression: {
    pass_rate_drop_max: 0.05,
    critical_failures_added_max: 0,
    faithfulness_drop_max: 0.03,
  },
};

/** Build a result that fully passes. */
function passingResult(id, opts = {}) {
  return {
    caseId: id,
    schemaValid: true,
    checks: { check_a: true },
    critical: opts.critical === true,
    promptVersion: opts.promptVersion || 'commit-draft-assist-v1',
    scores: opts.scores || {},
  };
}

/** Build a result with a failing check (schema valid, but check fails). */
function failingResult(id, opts = {}) {
  return {
    caseId: id,
    schemaValid: opts.schemaValid !== undefined ? opts.schemaValid : true,
    checks: opts.checks !== undefined ? opts.checks : { check_a: false },
    critical: opts.critical === true,
    promptVersion: opts.promptVersion || 'commit-draft-assist-v1',
    scores: opts.scores || {},
  };
}

/** Build an array of N passing results. */
function nPassing(n, promptVersion) {
  return Array.from({ length: n }, (_, i) =>
    passingResult(`r${i}`, { promptVersion: promptVersion || 'commit-draft-assist-v1' }),
  );
}

// ── computeMetrics ────────────────────────────────────────────────────────────

describe('computeMetrics', () => {
  it('returns null rates for empty results', () => {
    const m = computeMetrics([]);
    assert.equal(m.schemaValidRate, null);
    assert.equal(m.overallPassRate, null);
    assert.equal(m.criticalPassRate, null);
    assert.equal(m.totalResults, 0);
    assert.equal(m.criticalCount, 0);
  });

  it('computes 100% rates for all-passing results', () => {
    const m = computeMetrics(nPassing(10));
    assert.equal(m.schemaValidRate, 1.0);
    assert.equal(m.overallPassRate, 1.0);
    assert.equal(m.criticalPassRate, null); // no critical cases
    assert.equal(m.totalResults, 10);
  });

  it('computes correct pass rate when some results fail', () => {
    const results = [...nPassing(3), failingResult('f1'), failingResult('f2')];
    const m = computeMetrics(results);
    assert.ok(Math.abs(m.overallPassRate - 0.6) < 0.001);
  });

  it('computes criticalPassRate only from results with critical===true', () => {
    const results = [
      passingResult('crit-pass', { critical: true }),
      failingResult('crit-fail', { critical: true }),
      passingResult('non-critical'),
    ];
    const m = computeMetrics(results);
    assert.equal(m.criticalCount, 2);
    assert.equal(m.criticalPassRate, 0.5);
  });

  it('criticalPassRate is null when no critical cases are present', () => {
    const m = computeMetrics(nPassing(5));
    assert.equal(m.criticalPassRate, null);
  });

  it('computes mean judge scores grouped by inferred type', () => {
    const results = [
      passingResult('d1', { scores: { titleJudge_clarity: 0.8 }, promptVersion: 'commit-draft-assist-v1' }),
      passingResult('d2', { scores: { titleJudge_clarity: 0.6 }, promptVersion: 'commit-draft-assist-v1' }),
    ];
    const m = computeMetrics(results);
    const draftScores = m.judgeScoresByType['COMMIT_DRAFT_ASSIST'];
    assert.ok(draftScores, 'COMMIT_DRAFT_ASSIST scores must exist');
    assert.ok(Math.abs(draftScores['titleJudge_clarity'] - 0.7) < 0.001, 'mean clarity should be 0.7');
  });

  it('tracks resultCountsByType for each inferred type', () => {
    const results = [
      passingResult('a', { promptVersion: 'commit-draft-assist-v1' }),
      passingResult('b', { promptVersion: 'commit-draft-assist-v1' }),
      passingResult('c', { promptVersion: 'commit-lint-v1' }),
    ];
    const m = computeMetrics(results);
    assert.equal(m.resultCountsByType['COMMIT_DRAFT_ASSIST'], 2);
    assert.equal(m.resultCountsByType['COMMIT_LINT'], 1);
  });
});

// ── checkThresholds — all passing ─────────────────────────────────────────────

describe('checkThresholds — all passing', () => {
  it('emits no hard failures when all deterministic thresholds are met', () => {
    const m = computeMetrics(nPassing(20));
    const checks = checkThresholds(m, THRESHOLDS);
    const hardFailures = checks.filter((c) => !c.isSoft && !c.passed);
    assert.equal(hardFailures.length, 0);
  });

  it('returns check objects with name, passed, message, isSoft properties', () => {
    const m = computeMetrics(nPassing(20));
    const checks = checkThresholds(m, THRESHOLDS);
    for (const c of checks) {
      assert.ok('name' in c, 'check must have name');
      assert.ok('passed' in c, 'check must have passed');
      assert.ok('message' in c, 'check must have message');
      assert.ok('isSoft' in c, 'check must have isSoft');
    }
  });
});

// ── checkThresholds — schema validity failure ─────────────────────────────────

describe('checkThresholds — schema validity failure', () => {
  it('reports hard violation when schema_valid_rate falls below threshold', () => {
    const results = [
      { caseId: 'bad', schemaValid: false, checks: {}, critical: false, promptVersion: 'commit-draft-assist-v1', scores: {} },
      passingResult('ok'),
    ];
    const m = computeMetrics(results);
    const checks = checkThresholds(m, THRESHOLDS);
    const schemaCheck = checks.find((c) => c.name === 'schema_valid_rate');
    assert.ok(schemaCheck, 'schema_valid_rate check must be present');
    assert.equal(schemaCheck.passed, false, '50% < 100% should fail');
    assert.equal(schemaCheck.isSoft, false, 'schema check is a hard gate');
  });
});

// ── checkThresholds — critical case failure ───────────────────────────────────

describe('checkThresholds — critical case failure', () => {
  it('reports hard violation when critical_case_pass_rate falls below 100%', () => {
    // 19 passing non-critical + 1 failing critical → critical pass rate = 0%
    const results = [
      failingResult('crit-fail', { critical: true }),
      ...nPassing(19),
    ];
    const m = computeMetrics(results);
    const checks = checkThresholds(m, THRESHOLDS);
    const critCheck = checks.find((c) => c.name === 'critical_case_pass_rate');
    assert.ok(critCheck, 'critical_case_pass_rate check must be present');
    assert.equal(critCheck.passed, false, 'critical case failure should fail the check');
    assert.equal(critCheck.isSoft, false, 'critical check is a hard gate');
  });

  it('skips critical check (passes) when no critical cases are present', () => {
    const m = computeMetrics(nPassing(20));
    const checks = checkThresholds(m, THRESHOLDS);
    const critCheck = checks.find((c) => c.name === 'critical_case_pass_rate');
    assert.ok(critCheck, 'critical_case_pass_rate check must be present');
    assert.equal(critCheck.passed, true, 'no critical cases → check passes');
    assert.ok(critCheck.message.includes('skipped'), 'message should say skipped');
  });
});

// ── checkThresholds — missing judge scores ────────────────────────────────────

describe('checkThresholds — missing judge scores', () => {
  it('does not hard-fail when results exist but judge scores are absent', () => {
    // Results map to COMMIT_DRAFT_ASSIST but have no judge scores
    const m = computeMetrics(nPassing(20));
    const checks = checkThresholds(m, THRESHOLDS);
    const hardFailures = checks.filter((c) => !c.isSoft && !c.passed);
    assert.equal(hardFailures.length, 0, 'no hard failures when judge scores missing');
  });

  it('emits a soft informational note when judge scores are unavailable', () => {
    const m = computeMetrics(nPassing(20));
    const checks = checkThresholds(m, THRESHOLDS);
    const unavailableNote = checks.find(
      (c) => c.isSoft && c.name.includes('judge_scores_unavailable'),
    );
    assert.ok(unavailableNote, 'should emit soft note about unavailable judge scores');
    assert.equal(unavailableNote.passed, true, 'unavailability note is not a failure');
  });

  it('applies judge threshold checks when scores ARE present', () => {
    const results = nPassing(20).map((r) => ({
      ...r,
      scores: { titleJudge_clarity: 0.5 }, // below the 0.70 threshold
    }));
    const m = computeMetrics(results);
    const checks = checkThresholds(m, THRESHOLDS);
    const judgeFailures = checks.filter((c) => c.isSoft && !c.passed);
    assert.ok(judgeFailures.length > 0, 'judge threshold should flag low clarity score');
  });
});

// ── checkRegression — missing baseline ───────────────────────────────────────

describe('checkRegression — missing baseline', () => {
  it('returns a single passing note when baselineData is null', () => {
    const results = [passingResult('a')];
    const checks = checkRegression(results, null, THRESHOLDS);
    assert.equal(checks.length, 1);
    assert.equal(checks[0].passed, true);
    assert.ok(checks[0].message.toLowerCase().includes('skipped'));
  });

  it('returns a single passing note when baselineData has empty results', () => {
    const checks = checkRegression([passingResult('a')], { results: [] }, THRESHOLDS);
    assert.equal(checks.length, 1);
    assert.equal(checks[0].passed, true);
  });
});

// ── checkRegression — pass rate drop ─────────────────────────────────────────

describe('checkRegression — pass rate drop', () => {
  it('flags regression when pass rate drops beyond allowed threshold', () => {
    // Baseline: 20/20 (100%). Current: 1/20 (5%). Drop = 95% >> 5% max.
    const baselineData = { results: nPassing(20) };
    const currentResults = [
      passingResult('r0'),
      ...Array.from({ length: 19 }, (_, i) => failingResult(`r${i + 1}`)),
    ];
    const checks = checkRegression(currentResults, baselineData, THRESHOLDS);
    const rateCheck = checks.find((c) => c.name === 'pass_rate_regression');
    assert.ok(rateCheck, 'pass_rate_regression check must exist');
    assert.equal(rateCheck.passed, false, 'large drop should fail regression');
    assert.equal(rateCheck.isSoft, true, 'regression check is soft gate');
  });

  it('does not flag regression when pass rate drop is within threshold', () => {
    // Same results in baseline and current — zero drop
    const results = nPassing(20);
    const checks = checkRegression(results, { results }, THRESHOLDS);
    const rateCheck = checks.find((c) => c.name === 'pass_rate_regression');
    assert.ok(rateCheck, 'pass_rate_regression check must exist');
    assert.equal(rateCheck.passed, true, 'no drop should pass regression check');
  });
});

// ── checkRegression — critical failures added ─────────────────────────────────

describe('checkRegression — critical failures added', () => {
  it('flags regression when a critical case that passed in baseline now fails', () => {
    const baselineData = {
      results: [passingResult('crit-1')], // passed in baseline (not critical there)
    };
    const currentResults = [
      failingResult('crit-1', { critical: true }), // now critical AND failing
    ];
    const checks = checkRegression(currentResults, baselineData, THRESHOLDS);
    const critCheck = checks.find((c) => c.name === 'critical_failures_added');
    assert.ok(critCheck, 'critical_failures_added check must exist');
    assert.equal(critCheck.passed, false, 'should flag critical regression');
  });

  it('does not flag regression for a critical failure not present in baseline', () => {
    const baselineData = { results: [passingResult('other')] };
    const currentResults = [
      failingResult('new-case', { critical: true }), // not in baseline
    ];
    const checks = checkRegression(currentResults, baselineData, THRESHOLDS);
    const critCheck = checks.find((c) => c.name === 'critical_failures_added');
    assert.ok(critCheck, 'critical_failures_added check must exist');
    assert.equal(critCheck.passed, true, 'new critical case not in baseline is not a regression');
  });

  it('does not flag regression when critical case fails in both baseline and current', () => {
    const baselineData = {
      results: [failingResult('crit-always', { checks: { a: false } })],
    };
    const currentResults = [
      failingResult('crit-always', { critical: true }), // still failing
    ];
    const checks = checkRegression(currentResults, baselineData, THRESHOLDS);
    const critCheck = checks.find((c) => c.name === 'critical_failures_added');
    assert.ok(critCheck, 'critical_failures_added check must exist');
    assert.equal(critCheck.passed, true, 'already-failing case is not a new regression');
  });
});
