'use strict';

/**
 * eval-threshold-check.js
 *
 * Standalone Node.js 20 script (no external dependencies) that compares the
 * most recent eval run against eval-thresholds.json and eval-baseline.json.
 *
 * Exit codes:
 *   0 — all hard (deterministic) checks passed
 *   1 — at least one hard check failed
 *
 * Soft checks (judge scores, regression) are reported but do not affect the
 * exit code.
 *
 * Usage: node scripts/eval-threshold-check.js
 */

const fs = require('fs');
const path = require('path');

const REPO_ROOT = path.resolve(__dirname, '..');

// Maps promptVersion prefix → canonical type name used in thresholds config
const PROMPT_VERSION_TYPE_MAP = [
  { prefix: 'commit-draft-assist', type: 'COMMIT_DRAFT_ASSIST' },
  { prefix: 'commit-lint', type: 'COMMIT_LINT' },
  { prefix: 'rcdo-suggest', type: 'RCDO_SUGGEST' },
  { prefix: 'risk-signal', type: 'RISK_SIGNAL' },
  { prefix: 'reconcile-assist', type: 'RECONCILE_ASSIST' },
  { prefix: 'rag-query', type: 'RAG_QUERY' },
];

/** Infer canonical type name from promptVersion string. Returns null if unknown. */
function inferType(promptVersion) {
  if (!promptVersion) return null;
  for (const { prefix, type } of PROMPT_VERSION_TYPE_MAP) {
    if (promptVersion.startsWith(prefix)) return type;
  }
  return null;
}

/** True when a result has valid schema AND every check entry is true. */
function resultPassed(result) {
  if (!result.schemaValid) return false;
  const checks = result.checks || {};
  return Object.values(checks).every((v) => v === true);
}

/**
 * Map a threshold config key (e.g. "mean_faithfulness_min") to the actual
 * score dimension name stored in result.scores (e.g. "faithfulness").
 *
 * Mapping rules:
 *   mean_<dim>_min          → <dim>
 *   critical_<dim>_min      → <dim>
 *   title_judge_<sub>_min   → titleJudge_<sub>
 *   criteria_judge_<sub>_min → criteriaJudge_<sub>
 *
 * Returns null for unrecognised keys.
 */
function threshKeyToScoreDim(threshKey) {
  const m = threshKey.match(/^(?:mean_|critical_)?(.+)_min$/);
  if (!m) return null;
  const base = m[1];
  if (base.startsWith('title_judge_')) {
    return 'titleJudge_' + base.slice('title_judge_'.length);
  }
  if (base.startsWith('criteria_judge_')) {
    return 'criteriaJudge_' + base.slice('criteria_judge_'.length);
  }
  return base;
}

// ── Core logic (exported for unit tests) ─────────────────────────────────────

/**
 * Compute metrics from an array of EvalResult objects.
 *
 * Returns:
 *   schemaValidRate    — fraction with schemaValid === true (null if no results)
 *   overallPassRate    — fraction that fully passed (null if no results)
 *   criticalPassRate   — fraction of critical results that passed (null if none)
 *   judgeScoresByType  — { [type]: { [dim]: meanScore } }
 *   resultCountsByType — { [type]: number }
 *   totalResults       — total number of results
 *   criticalCount      — number of results with critical === true
 */
function computeMetrics(results) {
  if (!results || results.length === 0) {
    return {
      schemaValidRate: null,
      overallPassRate: null,
      criticalPassRate: null,
      judgeScoresByType: {},
      resultCountsByType: {},
      totalResults: 0,
      criticalCount: 0,
    };
  }

  const total = results.length;
  const schemaValidCount = results.filter((r) => r.schemaValid).length;
  const passedCount = results.filter((r) => resultPassed(r)).length;

  const criticalResults = results.filter((r) => r.critical === true);
  const criticalPassedCount = criticalResults.filter((r) => resultPassed(r)).length;

  // Accumulate judge scores grouped by inferred type, then by dimension
  const scoreAccum = {}; // { [type]: { [dim]: number[] } }
  const resultCountsByType = {}; // { [type]: number }
  for (const result of results) {
    const type = inferType(result.promptVersion);
    if (!type) continue;
    resultCountsByType[type] = (resultCountsByType[type] || 0) + 1;
    const scores = result.scores || {};
    for (const [dim, value] of Object.entries(scores)) {
      if (typeof value !== 'number') continue;
      if (!scoreAccum[type]) scoreAccum[type] = {};
      if (!scoreAccum[type][dim]) scoreAccum[type][dim] = [];
      scoreAccum[type][dim].push(value);
    }
  }

  // Convert to means
  const judgeScoresByType = {};
  for (const [type, dims] of Object.entries(scoreAccum)) {
    judgeScoresByType[type] = {};
    for (const [dim, values] of Object.entries(dims)) {
      judgeScoresByType[type][dim] = values.reduce((a, b) => a + b, 0) / values.length;
    }
  }

  return {
    schemaValidRate: schemaValidCount / total,
    overallPassRate: passedCount / total,
    criticalPassRate: criticalResults.length > 0 ? criticalPassedCount / criticalResults.length : null,
    judgeScoresByType,
    resultCountsByType,
    totalResults: total,
    criticalCount: criticalResults.length,
  };
}

/**
 * Check metrics against the thresholds config.
 *
 * Returns an array of check result objects:
 *   { name, passed, message, isSoft }
 *
 * Hard checks (isSoft=false): deterministic thresholds — block CI on failure.
 * Soft checks (isSoft=true): judge thresholds — report only.
 */
function checkThresholds(metrics, thresholds) {
  const checks = [];
  const det = thresholds.deterministic || {};

  // ── Hard gates ──────────────────────────────────────────────────────────

  if (det.schema_valid_rate !== undefined) {
    const actual = metrics.schemaValidRate;
    const required = det.schema_valid_rate;
    const passed = actual !== null && actual >= required;
    const pct = actual !== null ? (actual * 100).toFixed(1) + '%' : 'N/A';
    checks.push({
      name: 'schema_valid_rate',
      passed,
      message: `${pct} schema valid (required ≥ ${(required * 100).toFixed(1)}%)`,
      isSoft: false,
    });
  }

  if (det.overall_case_pass_rate !== undefined) {
    const actual = metrics.overallPassRate;
    const required = det.overall_case_pass_rate;
    const passed = actual !== null && actual >= required;
    const pct = actual !== null ? (actual * 100).toFixed(1) + '%' : 'N/A';
    checks.push({
      name: 'overall_case_pass_rate',
      passed,
      message: `${pct} cases passed (required ≥ ${(required * 100).toFixed(1)}%)`,
      isSoft: false,
    });
  }

  if (det.critical_case_pass_rate !== undefined) {
    if (metrics.criticalPassRate === null) {
      checks.push({
        name: 'critical_case_pass_rate',
        passed: true,
        message: 'No critical cases in results — check skipped',
        isSoft: false,
      });
    } else {
      const actual = metrics.criticalPassRate;
      const required = det.critical_case_pass_rate;
      const passed = actual >= required;
      checks.push({
        name: 'critical_case_pass_rate',
        passed,
        message: `${(actual * 100).toFixed(1)}% critical cases passed (required ≥ ${(required * 100).toFixed(1)}%)`,
        isSoft: false,
      });
    }
  }

  // ── Soft gates (judge thresholds) ───────────────────────────────────────

  for (const groupKey of ['judge_high_stakes', 'judge_assistive']) {
    const group = thresholds[groupKey];
    if (!group) continue;
    const appliesTo = group.applies_to || [];

    for (const type of appliesTo) {
      const typeScores = metrics.judgeScoresByType[type];
      const typeResultCount = metrics.resultCountsByType?.[type] || 0;
      let checkedCount = 0;
      let missingCount = 0;

      for (const [threshKey, minVal] of Object.entries(group)) {
        if (threshKey === 'applies_to') continue;
        const scoreDim = threshKeyToScoreDim(threshKey);
        if (!scoreDim) continue;

        if (!typeScores || typeScores[scoreDim] === undefined) {
          missingCount += 1;
          continue;
        }

        const actual = typeScores[scoreDim];
        const passed = actual >= minVal;
        checkedCount += 1;
        checks.push({
          name: `${groupKey}.${type}.${threshKey}`,
          passed,
          message: `[${type}] ${scoreDim}: ${actual.toFixed(3)} (required ≥ ${minVal})`,
          isSoft: true,
        });
      }

      if (typeResultCount > 0 && missingCount > 0) {
        checks.push({
          name: `${groupKey}.${type}.judge_scores_unavailable`,
          passed: true,
          message:
            checkedCount > 0
              ? `[${type}] some judge scores unavailable — ${missingCount} judge check(s) skipped`
              : `[${type}] judge scores unavailable — checks skipped`,
          isSoft: true,
        });
      }
    }
  }

  return checks;
}

/**
 * Check for regressions against the baseline eval run.
 *
 * Returns an array of check result objects (all isSoft=true — regressions are
 * initially soft-gated).
 *
 * When baselineData is null, returns a single skipped check.
 */
function checkRegression(currentResults, baselineData, thresholds) {
  const checks = [];
  const reg = thresholds.regression || {};

  if (!baselineData || !Array.isArray(baselineData.results) || baselineData.results.length === 0) {
    checks.push({
      name: 'regression_baseline',
      passed: true,
      message: 'No baseline available — regression checks skipped',
      isSoft: true,
    });
    return checks;
  }

  const baselineResults = baselineData.results;
  const baselinePassRate = baselineResults.filter((r) => resultPassed(r)).length / baselineResults.length;
  const currentPassRate = currentResults.filter((r) => resultPassed(r)).length / currentResults.length;

  // ── Pass-rate regression ─────────────────────────────────────────────────

  if (reg.pass_rate_drop_max !== undefined) {
    const drop = baselinePassRate - currentPassRate;
    const passed = drop <= reg.pass_rate_drop_max;
    checks.push({
      name: 'pass_rate_regression',
      passed,
      message:
        `Pass rate: baseline=${(baselinePassRate * 100).toFixed(1)}% → ` +
        `current=${(currentPassRate * 100).toFixed(1)}% ` +
        `(drop=${(drop * 100).toFixed(1)}%, max allowed=${(reg.pass_rate_drop_max * 100).toFixed(1)}%)`,
      isSoft: true,
    });
  }

  // ── Critical regressions (per-case ID) ──────────────────────────────────

  if (reg.critical_failures_added_max !== undefined) {
    // Build lookup of baseline results by caseId
    const baselineById = {};
    for (const r of baselineResults) {
      baselineById[r.caseId] = r;
    }

    // Find critical cases in the current run that newly regressed
    const newCriticalFailures = [];
    for (const curr of currentResults) {
      if (curr.critical !== true) continue; // only track critical cases
      const baseline = baselineById[curr.caseId];
      if (!baseline) continue; // not in baseline — can't be a regression
      if (resultPassed(baseline) && !resultPassed(curr)) {
        newCriticalFailures.push(curr.caseId);
      }
    }

    const count = newCriticalFailures.length;
    const passed = count <= reg.critical_failures_added_max;
    const detail = newCriticalFailures.length > 0 ? ` (${newCriticalFailures.join(', ')})` : '';
    checks.push({
      name: 'critical_failures_added',
      passed,
      message: `${count} new critical failure(s)${detail} (max allowed: ${reg.critical_failures_added_max})`,
      isSoft: true,
    });
  }

  // ── Faithfulness regression ──────────────────────────────────────────────

  if (reg.faithfulness_drop_max !== undefined) {
    const baselineFaith = baselineResults
      .map((r) => r.scores && r.scores.faithfulness)
      .filter((v) => typeof v === 'number');
    const currentFaith = currentResults
      .map((r) => r.scores && r.scores.faithfulness)
      .filter((v) => typeof v === 'number');

    if (baselineFaith.length > 0 && currentFaith.length > 0) {
      const baselineMean = baselineFaith.reduce((a, b) => a + b, 0) / baselineFaith.length;
      const currentMean = currentFaith.reduce((a, b) => a + b, 0) / currentFaith.length;
      const drop = baselineMean - currentMean;
      const passed = drop <= reg.faithfulness_drop_max;
      checks.push({
        name: 'faithfulness_regression',
        passed,
        message:
          `Faithfulness: baseline=${baselineMean.toFixed(3)} → current=${currentMean.toFixed(3)} ` +
          `(drop=${drop.toFixed(3)}, max allowed=${reg.faithfulness_drop_max})`,
        isSoft: true,
      });
    } else {
      checks.push({
        name: 'faithfulness_regression',
        passed: true,
        message: 'Faithfulness scores not available in baseline or current run — check skipped',
        isSoft: true,
      });
    }
  }

  return checks;
}

// ── File helpers ─────────────────────────────────────────────────────────────

/** Return the path of the most recently written eval result JSON file. */
function findLatestEvalResult(evalResultsDir) {
  if (!fs.existsSync(evalResultsDir)) {
    throw new Error(`Eval results directory not found: ${evalResultsDir}`);
  }
  const files = fs
    .readdirSync(evalResultsDir)
    .filter((f) => f.endsWith('.json'))
    .sort(); // ISO-timestamp filenames sort correctly lexicographically
  if (files.length === 0) {
    throw new Error(`No eval result files found in: ${evalResultsDir}`);
  }
  return path.join(evalResultsDir, files[files.length - 1]);
}

// ── Main ─────────────────────────────────────────────────────────────────────

function main() {
  const thresholdsPath = path.join(REPO_ROOT, 'eval-thresholds.json');
  const baselinePath = path.join(REPO_ROOT, 'eval-baseline.json');
  const evalResultsDir = path.join(REPO_ROOT, 'backend', 'build', 'eval-results');

  // Load thresholds (required)
  if (!fs.existsSync(thresholdsPath)) {
    console.error(`ERROR: eval-thresholds.json not found at ${thresholdsPath}`);
    process.exit(1);
  }
  const thresholds = JSON.parse(fs.readFileSync(thresholdsPath, 'utf8'));

  // Load latest eval results (required)
  let latestResultFile;
  try {
    latestResultFile = findLatestEvalResult(evalResultsDir);
  } catch (err) {
    console.error(`ERROR: ${err.message}`);
    process.exit(1);
  }

  const evalData = JSON.parse(fs.readFileSync(latestResultFile, 'utf8'));
  const results = evalData.results || [];

  if (results.length === 0) {
    console.error('ERROR: Eval results file contains no results');
    process.exit(1);
  }

  // Load baseline (optional)
  let baselineData = null;
  if (fs.existsSync(baselinePath)) {
    baselineData = JSON.parse(fs.readFileSync(baselinePath, 'utf8'));
  } else {
    console.warn('WARN: eval-baseline.json not found — regression checks will be skipped\n');
  }

  // Header
  console.log('\nEval Threshold Check');
  console.log('════════════════════════════════════════════════════════');
  console.log(`Results file : ${path.relative(REPO_ROOT, latestResultFile)}`);
  console.log(`Timestamp    : ${evalData.timestamp || 'unknown'}`);
  console.log(`Total cases  : ${results.length} (${evalData.passed || 0} passed, ${evalData.failed || 0} failed)`);
  if (baselineData) {
    console.log(`Baseline     : ${baselineData.timestamp || 'unknown'} (${baselineData.totalCases} cases)`);
  }
  console.log('════════════════════════════════════════════════════════\n');

  // Compute and check
  const metrics = computeMetrics(results);
  const thresholdChecks = checkThresholds(metrics, thresholds);
  const regressionChecks = checkRegression(results, baselineData, thresholds);

  const allChecks = [...thresholdChecks, ...regressionChecks];
  const hardChecks = allChecks.filter((c) => !c.isSoft);
  const softChecks = allChecks.filter((c) => c.isSoft);

  // Print deterministic section
  console.log('── Deterministic Gates (hard) ──────────────────────────');
  for (const c of hardChecks) {
    console.log(`  ${c.passed ? '✅' : '❌'} ${c.name}: ${c.message}`);
  }

  // Print soft section
  if (softChecks.length > 0) {
    const judgeChecks = softChecks.filter((c) => c.name.startsWith('judge_'));
    const regrChecks = softChecks.filter((c) => !c.name.startsWith('judge_'));

    if (judgeChecks.length > 0) {
      console.log('\n── Judge Score Checks (soft) ───────────────────────────');
      for (const c of judgeChecks) {
        console.log(`  ${c.passed ? '✅' : '⚠️ '} ${c.name}: ${c.message}`);
      }
    }

    if (regrChecks.length > 0) {
      console.log('\n── Regression Checks (soft) ────────────────────────────');
      for (const c of regrChecks) {
        console.log(`  ${c.passed ? '✅' : '⚠️ '} ${c.name}: ${c.message}`);
      }
    }
  }

  // Final verdict
  const hardFailures = hardChecks.filter((c) => !c.passed);
  const softFailures = softChecks.filter((c) => !c.passed);

  console.log('\n════════════════════════════════════════════════════════');
  if (hardFailures.length === 0 && softFailures.length === 0) {
    console.log('  RESULT: ✅ All checks passed');
  } else if (hardFailures.length === 0) {
    console.log(`  RESULT: ⚠️  ${softFailures.length} soft check(s) flagged — NOT blocking CI`);
    for (const f of softFailures) {
      console.log(`    ↳ ${f.name}`);
    }
  } else {
    console.log(`  RESULT: ❌ ${hardFailures.length} hard check(s) FAILED — blocking CI`);
    for (const f of hardFailures) {
      console.log(`    ↳ ${f.name}`);
    }
    if (softFailures.length > 0) {
      console.log(`         (also ${softFailures.length} soft check(s) flagged)`);
    }
  }
  console.log('════════════════════════════════════════════════════════\n');

  process.exit(hardFailures.length > 0 ? 1 : 0);
}

// ── Exports ───────────────────────────────────────────────────────────────────

module.exports = { computeMetrics, checkThresholds, checkRegression };

if (require.main === module) {
  main();
}
